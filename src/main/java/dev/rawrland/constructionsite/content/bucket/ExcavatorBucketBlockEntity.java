package dev.rawrland.constructionsite.content.bucket;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.rawrland.constructionsite.content.material.FallingMaterialEntity;
import dev.rawrland.constructionsite.registry.ModBlockEntities;
import dev.rawrland.constructionsite.registry.ModTags;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Holds the content of one bucket block, does the digging, and lets the
 * content go again when the bucket is tipped.
 *
 * Every bucket block holds at most one dug block. A joined bucket is simply
 * several of these working together: its capacity is the number of its blocks.
 *
 * Sable calls {@link #sable$tick} once per server tick, but only while this
 * block is part of a simulated contraption ("sub-level"). That is why a bucket
 * standing in the normal world never digs and never empties.
 */
public class ExcavatorBucketBlockEntity extends BlockEntity implements BlockEntitySubLevelActor, IHaveGoggleInformation {

    /**
     * Distance from the block centre to the dig point, along the open side.
     * 0.5 reaches the open face, plus 2 pixels (2/16 = 0.125). Used to tell how the bucket moves.
     */
    private static final double PROBE_DISTANCE = 0.5 + 2.0 / 16.0;

    // The dig area: where the bucket looks for blocks to dig. It covers the whole
    // open face of each opening block and the teeth of the bottom row.
    // All in blocks; the distances in front grow with the bucket's height.

    /** Sideways and up/down positions of the sample points on the open face, from the block centre. */
    private static final double[] FACE_SAMPLES = {-0.4, 0.0, 0.4};
    /** How far in front of the open face the face samples lie, for a 1-high bucket (2 pixels). */
    private static final double FACE_REACH = 2.0 / 16.0;
    /** How far in front of the open face the teeth samples lie, for a 1-high bucket (3.5 pixels, just inside the tips). */
    private static final double TEETH_REACH = 3.5 / 16.0;
    /** Height of the teeth samples, from the block centre: just above the bottom of the block. */
    private static final double TEETH_HEIGHT = -0.45;

    /**
     * How the bucket moves is judged over this many ticks (a quarter of a second),
     * not from one tick to the next. That way small bumps and shaking cancel out. Set by the spec.
     */
    private static final int PUSH_WINDOW_TICKS = 5;

    /**
     * How far (in blocks) the dig point must have moved over that time to count as moving at all.
     * Low enough for a slowly turning bearing. To be tuned in game.
     */
    private static final double MIN_PUSH_DISTANCE = 0.015;

    /** Pushing: at least this share of the movement must point out of the open side. */
    private static final double PUSH_SHARE = 0.3;
    /** Scooping: at least this share of the movement must point toward the teeth edge. */
    private static final double SCOOP_SHARE = 0.5;
    /** Scooping while backing away does not count: at most this share of the movement may point backward. */
    private static final double SCOOP_MAX_BACKWARD_SHARE = 0.2;

    // Emptying: a bucket whose open side points downward lets its content go.

    /** A full bucket starts to spill when its open side points this many degrees below level. Set by the spec. */
    private static final double TIP_ANGLE_FULL = 20.0;
    /** The last block only leaves when the open side points this many degrees below level. Set by the spec. */
    private static final double TIP_ANGLE_LAST = 45.0;
    /**
     * The bucket counts as tipped when the downward part of its open side's
     * direction is above this: the sine of the angle for a full bucket.
     */
    private static final double TIP_LIMIT = Math.sin(Math.toRadians(TIP_ANGLE_FULL));
    /** A tipped bucket releases blocks once every this many ticks (a quarter of a second). Set by the spec. */
    private static final int RELEASE_INTERVAL_TICKS = 5;
    /** Released blocks appear this far in front of the block centre: in the middle of the next block. */
    private static final double RELEASE_DISTANCE = 1.0;

    private static final String GOGGLES_TITLE = "gui.goggles.create_construction_site.excavator_bucket.title";
    private static final String GOGGLES_FILL = "gui.goggles.create_construction_site.excavator_bucket.fill";
    private static final String GOGGLES_EMPTY = "gui.goggles.create_construction_site.excavator_bucket.empty";

    private static final String TAG_STORED_BLOCK = "StoredBlock";
    private static final String TAG_STORED_BLOCK_ENTITY = "StoredBlockEntity";
    private static final String TAG_HAS_CONTENT = "HasContent";
    private static final String TAG_PICKED_UP_AT = "PickedUpAt";
    private static final String TAG_ANCHOR_X = "AnchorX";
    private static final String TAG_ANCHOR_Y = "AnchorY";
    private static final String TAG_ANCHOR_Z = "AnchorZ";
    private static final String TAG_GROUP_HEIGHT = "GroupHeight";
    private static final String TAG_GROUP_WIDTH = "GroupWidth";
    private static final String TAG_MOUNT = "Mount";
    private static final String TAG_HOLD_UNTIL_LEVEL = "HoldUntilLevel";

    // ----------------------------------------------------------------- content

    /** The dug block, exactly as it was in the world. Null while this block is empty. */
    @Nullable
    private BlockState storedState;

    /** Extra data of the dug block (for example the items in a chest). Null if it had none. */
    @Nullable
    private CompoundTag storedBlockEntityData;

    /** Game time at which the content was picked up. Decides which block was "picked up last". */
    private long pickedUpAt;

    // ------------------------------------------------------------------- group

    /** Where the bucket's anchor (bottom front left block) is, counted from this block. Zero for a single bucket. */
    private BlockPos anchorOffset = BlockPos.ZERO;

    /** Height (and depth) of the bucket this block belongs to, in blocks. */
    private int groupHeight = 1;

    /** Width of the bucket this block belongs to, in blocks. */
    private int groupWidth = 1;

    /** Where the mounting ears are shown. All blocks of a bucket hold the same value; the anchor's is the one drawn. */
    private BucketMount mount = BucketMount.TOP;

    /**
     * Set when the bucket digs while tipped downward. It then keeps its content
     * until it has been brought back to level. Only the anchor block's value counts.
     */
    private boolean holdUntilLevel;

    // ------------------------------------------------------- not saved, per tick

    /** Where this block's dig point was in the world over the last ticks. Newest first; null until known. */
    private final Vec3[] recentWorldDigPoints = new Vec3[PUSH_WINDOW_TICKS + 1];

    /** Set after the first tick on a contraption, where joining is checked once more. */
    private boolean regroupedOnContraption;

    /** Ticks left until a tipped bucket releases its next blocks. Only used by the anchor block. */
    private int releaseCooldown;

    public ExcavatorBucketBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.EXCAVATOR_BUCKET.get(), pos, state);
    }

    // ------------------------------------------------------------ this block

    public boolean isEmpty() {
        return storedState == null;
    }

    /** The stored block, or null while this block is empty. */
    @Nullable
    public BlockState getStoredState() {
        return storedState;
    }

    public long getPickedUpAt() {
        return pickedUpAt;
    }

    private void setContent(BlockState state, @Nullable CompoundTag blockEntityData, long gameTime) {
        storedState = state;
        storedBlockEntityData = blockEntityData;
        pickedUpAt = gameTime;
        changed();
    }

    /** Marks this block for saving and tells nearby players' games about the change. */
    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // -------------------------------------------------------- the whole bucket

    /** Called by {@link BucketGroup} when the bucket this block belongs to changes. */
    public void setGroup(BlockPos anchorOffset, int height, int width) {
        if (this.anchorOffset.equals(anchorOffset) && groupHeight == height && groupWidth == width) {
            return;
        }
        this.anchorOffset = anchorOffset.immutable();
        this.groupHeight = height;
        this.groupWidth = width;
        changed();
    }

    public boolean isJoined() {
        return getBlockState().getValue(ExcavatorBucketBlock.JOINED);
    }

    /** True for the one block of a joined bucket that draws it. Always true for a single bucket. */
    public boolean isAnchor() {
        return !isJoined() || anchorOffset.equals(BlockPos.ZERO);
    }

    public int getGroupHeight() {
        return isJoined() ? groupHeight : 1;
    }

    public int getGroupWidth() {
        return isJoined() ? groupWidth : 1;
    }

    /** Where the bucket's anchor block is, counted from this block. Zero for a single bucket. */
    public BlockPos getAnchorOffset() {
        return isJoined() ? anchorOffset : BlockPos.ZERO;
    }

    /** The positions of this block's slice: every block at the same place along the bucket's width. */
    public List<BlockPos> getSlicePositions() {
        return BucketGroup.slice(getBlockPos(), getBlockState(), anchorOffset, groupHeight);
    }

    public BucketMount getMount() {
        return mount;
    }

    /** Switches the whole bucket to the next mount position: top, back, none, top, ... */
    public BucketMount cycleGroupMount() {
        BucketMount next = mount.next();
        for (ExcavatorBucketBlockEntity member : getGroupMembers()) {
            member.mount = next;
            member.changed();
        }
        return next;
    }

    /** The anchor block's block entity: the one block that acts for the whole bucket. This one for a single bucket. */
    @Nullable
    private ExcavatorBucketBlockEntity getAnchor() {
        if (isAnchor() || level == null) {
            return this;
        }
        BlockPos anchorPos = getBlockPos().offset(anchorOffset);
        if (level.isLoaded(anchorPos) && level.getBlockEntity(anchorPos) instanceof ExcavatorBucketBlockEntity anchor) {
            return anchor;
        }
        return null;
    }

    /** How many blocks the whole bucket can hold: one per bucket block. */
    public int getGroupCapacity() {
        int height = getGroupHeight();
        return height * height * getGroupWidth();
    }

    /** The block entities of all blocks of this bucket, this one included. Works on server and client. */
    public List<ExcavatorBucketBlockEntity> getGroupMembers() {
        List<ExcavatorBucketBlockEntity> members = new ArrayList<>();
        if (level == null) {
            members.add(this);
            return members;
        }
        for (BlockPos pos : BucketGroup.members(getBlockPos(), getBlockState(), anchorOffset, groupHeight, groupWidth)) {
            if (pos.equals(getBlockPos())) {
                members.add(this);
            } else if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ExcavatorBucketBlockEntity other) {
                members.add(other);
            }
        }
        return members;
    }

    /** What the whole bucket holds: each kind of block with its count, most first. */
    public Map<Block, Integer> countGroupContent() {
        Map<Block, Integer> counts = new LinkedHashMap<>();
        for (ExcavatorBucketBlockEntity member : getGroupMembers()) {
            if (member.storedState != null) {
                counts.merge(member.storedState.getBlock(), 1, Integer::sum);
            }
        }
        List<Map.Entry<Block, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());
        Map<Block, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<Block, Integer> entry : sorted) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /** This block if it is empty, otherwise the nearest empty block of the same bucket. Null if the bucket is full. */
    @Nullable
    private ExcavatorBucketBlockEntity findFreeMember() {
        if (isEmpty()) {
            return this;
        }
        ExcavatorBucketBlockEntity best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (ExcavatorBucketBlockEntity member : getGroupMembers()) {
            if (member.isEmpty()) {
                int distance = member.getBlockPos().distManhattan(getBlockPos());
                if (distance < bestDistance) {
                    best = member;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    // ---------------------------------------------------------- Create's goggles

    /**
     * Shown when a player wearing Create's goggles looks at any block of the bucket:
     * how full the whole bucket is, and every kind of block in it with its count.
     * Unlike the short message above the hotbar, this panel has room for the full list.
     */
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Map<Block, Integer> content = countGroupContent();
        int total = 0;
        for (int count : content.values()) {
            total += count;
        }

        CreateLang.builder()
            .add(Component.translatable(GOGGLES_TITLE))
            .style(ChatFormatting.WHITE)
            .forGoggles(tooltip);
        CreateLang.builder()
            .add(Component.translatable(GOGGLES_FILL, total, getGroupCapacity()))
            .style(ChatFormatting.GRAY)
            .forGoggles(tooltip);

        if (content.isEmpty()) {
            CreateLang.builder()
                .add(Component.translatable(GOGGLES_EMPTY))
                .style(ChatFormatting.DARK_GRAY)
                .forGoggles(tooltip);
            return true;
        }
        for (Map.Entry<Block, Integer> entry : content.entrySet()) {
            CreateLang.builder()
                .add(Component.literal(entry.getValue() + "x ").withStyle(ChatFormatting.GOLD))
                .add(entry.getKey().getName().withStyle(ChatFormatting.GRAY))
                .forGoggles(tooltip);
        }
        return true;
    }

    // ---------------------------------------------------------------- digging

    @Override
    public void sable$tick(ServerSubLevel subLevel) {
        if (!(level instanceof ServerLevel serverLevel) || Sable.HELPER.getContaining(this) != subLevel) {
            return;
        }

        // It is not known whether assembling a contraption runs the normal
        // "block placed" logic. So joining is checked once more here.
        // One block per bucket is enough to do this: the anchor, or a block that is on its own.
        if (!regroupedOnContraption) {
            regroupedOnContraption = true;
            if (isAnchor()) {
                BucketGroup.regroup(serverLevel, getBlockPos());
            }
        }

        // Only blocks with an open side dig. The back layer of a 2-deep bucket just stores.
        if (!BucketGroup.isFrontLayer(getBlockState(), anchorOffset)) {
            return;
        }

        // Positions of blocks on a contraption are stored far away from where
        // the player sees them. Sable converts ("projects") them to the world.
        Direction facing = getBlockState().getValue(ExcavatorBucketBlock.FACING);
        Vec3 localCentre = getBlockPos().getCenter();
        Vec3 localDigPoint = localCentre.add(Vec3.atLowerCornerOf(facing.getNormal()).scale(PROBE_DISTANCE));

        // A point one block toward the edge with the teeth, to learn which way that edge points in the world.
        Direction towardTeeth = BucketGroup.axes(getBlockState()).up().getOpposite();
        Vec3 localTeethPoint = localCentre.add(Vec3.atLowerCornerOf(towardTeeth.getNormal()));

        Vec3 worldCentre = Sable.HELPER.projectOutOfSubLevel(serverLevel, localCentre);
        Vec3 worldDigPoint = Sable.HELPER.projectOutOfSubLevel(serverLevel, localDigPoint);
        Vec3 worldTeethPoint = Sable.HELPER.projectOutOfSubLevel(serverLevel, localTeethPoint);

        // Which way the open side points in the world. Pointing clearly downward means the bucket is tipped.
        Vec3 openSideDirection = worldDigPoint.subtract(worldCentre).normalize();
        // How strongly it points downward: 0 when level, 1 when straight down (the sine of the angle).
        double downward = -openSideDirection.y;
        boolean tipped = downward > TIP_LIMIT;

        // One block empties the whole bucket: the anchor.
        if (isAnchor()) {
            tickEmptying(serverLevel, tipped, downward);
        }

        boolean pushed = updatePush(worldDigPoint, openSideDirection,
            worldTeethPoint.subtract(worldCentre).normalize());
        if (!pushed) {
            return;
        }

        // Dig every diggable block the dig area reaches, as long as the bucket has room.
        for (BlockPos target : digAreaTargets(serverLevel, localCentre, facing)) {
            tryDig(serverLevel, target, tipped);
        }
    }

    // --------------------------------------------------------------- emptying

    /**
     * Runs on the anchor block every tick. While the bucket is tipped it lets
     * blocks go: one per block of its width every quarter second, the block
     * picked up last first. The emptier the bucket, the steeper it has to be tipped.
     */
    private void tickEmptying(ServerLevel serverLevel, boolean tipped, double downward) {
        if (!tipped) {
            // Back to level: a bucket that was holding its load may empty the next time it is tipped.
            releaseCooldown = 0;
            if (holdUntilLevel) {
                holdUntilLevel = false;
                setChanged();
            }
            return;
        }
        if (holdUntilLevel) {
            return;
        }
        if (releaseCooldown > 0) {
            releaseCooldown--;
            return;
        }
        releaseCooldown = RELEASE_INTERVAL_TICKS - 1;

        List<ExcavatorBucketBlockEntity> members = getGroupMembers();

        // What the bucket holds, the block picked up last first.
        List<ExcavatorBucketBlockEntity> filled = new ArrayList<>();
        for (ExcavatorBucketBlockEntity member : members) {
            if (!member.isEmpty()) {
                filled.add(member);
            }
        }
        if (filled.isEmpty()) {
            return;
        }
        filled.sort(Comparator.comparingLong(ExcavatorBucketBlockEntity::getPickedUpAt).reversed());

        // The places in the world where blocks can leave, sorted into the columns of the bucket's width.
        Direction facing = getBlockState().getValue(ExcavatorBucketBlock.FACING);
        Direction right = BucketGroup.axes(getBlockState()).right();
        Vec3 forward = Vec3.atLowerCornerOf(facing.getNormal()).scale(RELEASE_DISTANCE);
        Map<Integer, List<Vec3>> columns = new TreeMap<>();
        for (ExcavatorBucketBlockEntity member : members) {
            if (!BucketGroup.isFrontLayer(member.getBlockState(), member.anchorOffset)) {
                continue;
            }
            int column = BucketGroup.along(member.getBlockPos().subtract(getBlockPos()), right);
            Vec3 worldPoint = Sable.HELPER.projectOutOfSubLevel(serverLevel, member.getBlockPos().getCenter().add(forward));
            columns.computeIfAbsent(column, key -> new ArrayList<>()).add(worldPoint);
        }

        // Each column lets one block go, from its lowest place that is free.
        int capacity = getGroupCapacity();
        int next = 0;
        for (List<Vec3> points : columns.values()) {
            // Stop when the bucket is empty, or no longer tipped steeply enough for what is left in it.
            if (next >= filled.size() || !isSteepEnough(downward, filled.size() - next, capacity)) {
                break;
            }
            points.sort(Comparator.comparingDouble((Vec3 point) -> point.y));
            for (Vec3 point : points) {
                BlockPos place = BlockPos.containing(point);
                if (isFreeForRelease(serverLevel, place)) {
                    filled.get(next).releaseContent(serverLevel, place);
                    next++;
                    break;
                }
            }
        }
    }

    /**
     * True if a bucket holding this many blocks lets one go when tipped this far.
     * A full bucket needs the smallest angle; the needed angle grows evenly as it empties.
     */
    private static boolean isSteepEnough(double downward, int held, int capacity) {
        double emptyShare = 1.0 - (double) held / capacity;
        double neededAngle = TIP_ANGLE_FULL + (TIP_ANGLE_LAST - TIP_ANGLE_FULL) * emptyShare;
        return downward >= Math.sin(Math.toRadians(neededAngle));
    }

    /** True if a released block may appear here: the place is loaded and holds air or something replaceable. */
    private static boolean isFreeForRelease(ServerLevel serverLevel, BlockPos place) {
        if (serverLevel.isOutsideBuildHeight(place) || !serverLevel.isLoaded(place)) {
            return false;
        }
        return serverLevel.getBlockState(place).canBeReplaced();
    }

    /** Takes the content out of this block and lets it fall from the given place in the world. */
    private void releaseContent(ServerLevel serverLevel, BlockPos place) {
        if (storedState == null) {
            return;
        }
        FallingMaterialEntity.spawn(serverLevel, place, storedState, storedBlockEntityData);
        storedState = null;
        storedBlockEntityData = null;
        changed();
    }

    /** The world blocks that this block's part of the dig area currently reaches. */
    private Set<BlockPos> digAreaTargets(ServerLevel serverLevel, Vec3 localCentre, Direction facing) {
        BucketGroup.Axes axes = BucketGroup.axes(getBlockState());
        Vec3 right = Vec3.atLowerCornerOf(axes.right().getNormal());
        Vec3 up = Vec3.atLowerCornerOf(axes.up().getNormal());
        Vec3 forward = Vec3.atLowerCornerOf(facing.getNormal());
        int scale = getGroupHeight();

        Set<BlockPos> targets = new LinkedHashSet<>();

        // The open face: a 3 x 3 grid of points just in front of it.
        double faceDistance = 0.5 + FACE_REACH * scale;
        for (double r : FACE_SAMPLES) {
            for (double u : FACE_SAMPLES) {
                Vec3 local = localCentre.add(right.scale(r)).add(up.scale(u)).add(forward.scale(faceDistance));
                targets.add(BlockPos.containing(Sable.HELPER.projectOutOfSubLevel(serverLevel, local)));
            }
        }

        // The teeth: only the bottom row of the bucket has them.
        if (BucketGroup.isBottomRow(getBlockState(), anchorOffset)) {
            double teethDistance = 0.5 + TEETH_REACH * scale;
            for (double r : FACE_SAMPLES) {
                Vec3 local = localCentre.add(right.scale(r)).add(up.scale(TEETH_HEIGHT))
                    .add(forward.scale(teethDistance));
                targets.add(BlockPos.containing(Sable.HELPER.projectOutOfSubLevel(serverLevel, local)));
            }
        }
        return targets;
    }

    /**
     * Records where the dig point is and tells whether the bucket is moving into
     * material: open side first (pushing) or teeth first (scooping, as when the
     * bucket turns). Moving sideways along the teeth, roof first or backward does
     * not count, and neither does shaking on the spot.
     *
     * The dig point is watched, not the block's centre, because a bucket that
     * turns on the spot moves its mouth while its centre stays where it is.
     * Must run every tick, full or empty, so the record has no gaps.
     */
    private boolean updatePush(Vec3 worldDigPoint, Vec3 openSideDirection, Vec3 teethDirection) {
        // Shift the record by one tick and put the newest position in front.
        System.arraycopy(recentWorldDigPoints, 0, recentWorldDigPoints, 1, PUSH_WINDOW_TICKS);
        recentWorldDigPoints[0] = worldDigPoint;

        Vec3 oldest = recentWorldDigPoints[PUSH_WINDOW_TICKS];
        if (oldest == null) {
            return false;
        }
        // Where the dig point went over the whole window. Back-and-forth shaking cancels out here.
        Vec3 movement = worldDigPoint.subtract(oldest);
        double distance = movement.length();
        if (distance < MIN_PUSH_DISTANCE) {
            return false;
        }
        double forward = movement.dot(openSideDirection);
        double towardTeeth = movement.dot(teethDirection);

        boolean pushing = forward > PUSH_SHARE * distance;
        boolean scooping = towardTeeth > SCOOP_SHARE * distance && forward > -SCOOP_MAX_BACKWARD_SHARE * distance;
        return pushing || scooping;
    }

    private void tryDig(ServerLevel serverLevel, BlockPos target, boolean tipped) {
        if (serverLevel.isOutsideBuildHeight(target) || !serverLevel.isLoaded(target)) {
            return;
        }
        // Only dig the normal world, never blocks that belong to a contraption.
        if (Sable.HELPER.getContaining(serverLevel, target) != null) {
            return;
        }

        BlockState state = serverLevel.getBlockState(target);
        if (state.isAir() || !state.is(ModTags.BUCKET_DIGGABLE)) {
            return;
        }
        // Negative destroy speed means unbreakable (bedrock and the like), whatever the tag says.
        if (state.getDestroySpeed(serverLevel, target) < 0) {
            return;
        }

        // Find room before touching the world: this block, or another block of the same bucket.
        ExcavatorBucketBlockEntity receiver = findFreeMember();
        if (receiver == null) {
            return;
        }

        // Keep the block's extra data, then empty it so nothing spills when it is removed.
        CompoundTag blockEntityData = null;
        BlockEntity blockEntity = serverLevel.getBlockEntity(target);
        if (blockEntity != null) {
            blockEntityData = blockEntity.saveWithId(serverLevel.registryAccess());
            Clearable.tryClear(blockEntity);
        }

        // false = no item drops. Break particles and sound still play.
        if (!serverLevel.destroyBlock(target, false)) {
            return;
        }

        receiver.setContent(state, blockEntityData, serverLevel.getGameTime());

        // Dug while pointing downward: keep the load until the bucket has been levelled,
        // or it would fall straight back into the hole it came from.
        if (tipped) {
            ExcavatorBucketBlockEntity anchor = getAnchor();
            if (anchor != null && !anchor.holdUntilLevel) {
                anchor.holdUntilLevel = true;
                anchor.setChanged();
            }
        }
    }

    // --------------------------------------------------------------- breaking

    /**
     * Empties this block and returns what its content would drop if it were
     * broken in the world with an unenchanted shovel tool.
     */
    public List<ItemStack> removeContentAsBroken(ServerLevel serverLevel) {
        List<ItemStack> drops = new ArrayList<>();
        if (storedState == null) {
            return drops;
        }
        BlockPos pos = getBlockPos();

        BlockEntity storedBlockEntity = storedBlockEntityData == null ? null
            : BlockEntity.loadStatic(pos, storedState, storedBlockEntityData, serverLevel.registryAccess());

        drops.addAll(
            Block.getDrops(storedState, serverLevel, pos, storedBlockEntity, null, new ItemStack(Items.IRON_SHOVEL))
        );

        // Containers such as chests spill their items when broken. Shulker boxes
        // keep their items inside the dropped box instead, so they are skipped here.
        if (storedBlockEntity instanceof Container container && !(storedState.getBlock() instanceof ShulkerBoxBlock)) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (!stack.isEmpty()) {
                    drops.add(stack);
                }
            }
        }

        storedState = null;
        storedBlockEntityData = null;
        setChanged();
        return drops;
    }

    /** Empties this block and drops its content into the world, as when the bucket block is broken. */
    public void dropContentAsBroken(ServerLevel serverLevel) {
        BlockPos pos = getBlockPos();
        for (ItemStack stack : removeContentAsBroken(serverLevel)) {
            Containers.dropItemStack(serverLevel, pos.getX(), pos.getY(), pos.getZ(), stack);
        }
    }

    // ---------------------------------------------------- sending to the client

    // Digging and joining happen on the server. These two methods send the
    // result to the players' games, so the renderer can show it.

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ----------------------------------------------------------------- saving

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // Always written, so that "now empty" also reaches the client (empty updates are ignored).
        tag.putBoolean(TAG_HAS_CONTENT, storedState != null);
        if (storedState != null) {
            tag.put(TAG_STORED_BLOCK, NbtUtils.writeBlockState(storedState));
            tag.putLong(TAG_PICKED_UP_AT, pickedUpAt);
            if (storedBlockEntityData != null) {
                tag.put(TAG_STORED_BLOCK_ENTITY, storedBlockEntityData.copy());
            }
        }
        tag.putInt(TAG_ANCHOR_X, anchorOffset.getX());
        tag.putInt(TAG_ANCHOR_Y, anchorOffset.getY());
        tag.putInt(TAG_ANCHOR_Z, anchorOffset.getZ());
        tag.putInt(TAG_GROUP_HEIGHT, groupHeight);
        tag.putInt(TAG_GROUP_WIDTH, groupWidth);
        tag.putInt(TAG_MOUNT, mount.ordinal());
        tag.putBoolean(TAG_HOLD_UNTIL_LEVEL, holdUntilLevel);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        storedState = null;
        storedBlockEntityData = null;
        pickedUpAt = 0;
        if (tag.contains(TAG_STORED_BLOCK, Tag.TAG_COMPOUND)) {
            BlockState state = NbtUtils.readBlockState(
                registries.lookupOrThrow(Registries.BLOCK), tag.getCompound(TAG_STORED_BLOCK)
            );
            if (!state.isAir()) {
                storedState = state;
                pickedUpAt = tag.getLong(TAG_PICKED_UP_AT);
                if (tag.contains(TAG_STORED_BLOCK_ENTITY, Tag.TAG_COMPOUND)) {
                    storedBlockEntityData = tag.getCompound(TAG_STORED_BLOCK_ENTITY).copy();
                }
            }
        }
        anchorOffset = new BlockPos(tag.getInt(TAG_ANCHOR_X), tag.getInt(TAG_ANCHOR_Y), tag.getInt(TAG_ANCHOR_Z));
        groupHeight = Math.max(1, tag.getInt(TAG_GROUP_HEIGHT));
        groupWidth = Math.max(1, tag.getInt(TAG_GROUP_WIDTH));
        mount = BucketMount.byIndex(tag.getInt(TAG_MOUNT));
        holdUntilLevel = tag.getBoolean(TAG_HOLD_UNTIL_LEVEL);
    }
}
