package dev.rawrland.constructionsite.content.blade;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.rawrland.constructionsite.Config;
import dev.rawrland.constructionsite.content.bucket.BucketGroup;
import dev.rawrland.constructionsite.content.material.FallingMaterialEntity;
import dev.rawrland.constructionsite.content.tool.PushTracker;
import dev.rawrland.constructionsite.content.tool.WorldDig;
import dev.rawrland.constructionsite.registry.ModBlockEntities;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Holds part of a blade's load and does the blade's work: taking the blocks in
 * front of it into a pile, letting the pile run into gaps, and setting the pile
 * down when the blade backs away.
 *
 * The blade thinks in columns: one column per block of its width. Each column
 * pushes its own pile, whose shape and size are described in {@link BladePile}.
 * The blocks of a pile are stored in the blade blocks of that column, the
 * lowest blade block first.
 *
 * The pile is not put into the world. It is only drawn in front of the blade
 * (see the renderer), because real blocks there would stop the blade.
 *
 * When the pile is full, a block the blade meets is moved off the nearer end
 * of the blade and put down there, so a ridge forms beside the blade's path.
 * The blade never lets blocks fall as entities: loose falling blocks around a
 * moving machine end up under it and jam it.
 *
 * Sable calls {@link #sable$tick} once per server tick, but only while this
 * block is part of a simulated contraption. That is why a blade standing in
 * the normal world never does anything.
 */
public class BulldozerBladeBlockEntity extends SmartBlockEntity implements BlockEntitySubLevelActor, IHaveGoggleInformation {

    /**
     * Distance from the block centre to the probe points, along the working face's direction.
     * 0.5 reaches the face, plus 4 pixels (4/16 = 0.25) into the block in front.
     *
     * This reach decides how early the blade takes a block. With 2 pixels (the
     * bucket's value) a slow vehicle was stopped by the block before the probe
     * got into it, and a blade driven in at an angle only touched with a corner.
     * Tuned in game on 10 October 2026.
     */
    private static final double PROBE_DISTANCE = 0.5 + 4.0 / 16.0;
    /** Sideways positions of the probe points on the working face, from the block centre. */
    private static final double[] PROBE_ACROSS = {-0.4, 0.0, 0.4};
    /**
     * Up/down positions of the probe points. Kept away from the bottom edge, so a
     * vehicle that nods forward a little does not scrape the ground below the blade.
     */
    private static final double[] PROBE_UP = {-0.3, 0.0, 0.3};

    /** Pushing: at least this share of the movement must point out of the working face. The same as the bucket. */
    private static final double PUSH_SHARE = 0.3;
    /**
     * After the blade was last seen pushing, it goes on taking blocks for this
     * many ticks (half a second). A slow vehicle comes to a stop against the
     * block it is about to take, and a blade that stands still is not "pushing".
     */
    private static final int PUSH_MEMORY_TICKS = 10;
    /** Backing away: at least this share of the movement must point away from the working face. To be tuned in game. */
    private static final double BACK_SHARE = 0.3;

    /**
     * A column lets a block go when the ground under its pile is at least this far
     * below the cutting edge, in blocks. Just under one block, so a blade that
     * bounces a little above a hole still fills it, and a blade that merely rides
     * a bit high does not drop anything.
     */
    private static final double GAP_DEPTH = 0.9;

    /**
     * Size of the zone in front of each blade block in which falling material is
     * caught, in blocks. It covers the front of the blade block and the place in front of it.
     */
    private static final double CATCH_ZONE_SIZE = 1.4;
    /** How far in front of the block centre the middle of that zone lies. */
    private static final double CATCH_ZONE_DISTANCE = 0.75;

    /** How many places upward are tried when a block is to be put down where something already is. */
    private static final int MAX_STEPS_UP = 4;

    /** A block that was just taken is drawn sliding into the pile from at most this far away, in blocks. */
    private static final double MAX_SLIDE_DISTANCE = 3.0;

    /** The blade works while its face points at most this many degrees up or down. Set by the spec. */
    private static final double MAX_TILT_ANGLE = 45.0;
    private static final double TILT_LIMIT = Math.sin(Math.toRadians(MAX_TILT_ANGLE));

    private static final String GOGGLES_TITLE = "gui.goggles.create_construction_site.bulldozer_blade.title";
    private static final String GOGGLES_FILL = "gui.goggles.create_construction_site.bulldozer_blade.fill";
    private static final String GOGGLES_EMPTY = "gui.goggles.create_construction_site.bulldozer_blade.empty";

    private static final String TAG_STORED = "Stored";
    private static final String TAG_BLOCK = "Block";
    private static final String TAG_BLOCK_ENTITY = "BlockEntity";
    private static final String TAG_FROM_RIGHT = "FromRight";
    private static final String TAG_FROM_UP = "FromUp";
    private static final String TAG_FROM_FORWARD = "FromForward";
    private static final String TAG_TAKEN_AT = "TakenAt";
    /** Older names: the first version of the blade stored a single block per blade block. */
    private static final String TAG_OLD_STORED_BLOCK = "StoredBlock";
    private static final String TAG_OLD_STORED_BLOCK_ENTITY = "StoredBlockEntity";
    private static final String TAG_ANCHOR_X = "AnchorX";
    private static final String TAG_ANCHOR_Y = "AnchorY";
    private static final String TAG_ANCHOR_Z = "AnchorZ";
    private static final String TAG_GROUP_HEIGHT = "GroupHeight";
    private static final String TAG_GROUP_WIDTH = "GroupWidth";
    private static final String TAG_MOUNT = "Mount";
    /** Older name: blades saved before the mount had three settings only stored shown or hidden. */
    private static final String TAG_MOUNT_SHOWN = "MountShown";

    /**
     * One block of the pile.
     *
     * @param state           the block, exactly as it was in the world
     * @param blockEntityData its extra data (for example the items in a chest), or null
     * @param fromRight       where it came from, measured from its place in the pile, along the blade's "right"
     * @param fromUp          the same along the blade's "up"
     * @param fromForward     the same along the direction the working face points
     * @param takenAt         game time at which it was taken; the renderer draws it sliding in from "from" for a moment
     */
    public record Stored(BlockState state, @Nullable CompoundTag blockEntityData,
                         float fromRight, float fromUp, float fromForward, long takenAt) {
    }

    // ----------------------------------------------------------------- content

    /** The blocks of the pile that this blade block stores, oldest first. */
    private final List<Stored> stored = new ArrayList<>();

    // ------------------------------------------------------------------- group

    /** Where the blade's anchor (bottom left block) is, counted from this block. Zero for a single blade. */
    private BlockPos anchorOffset = BlockPos.ZERO;

    /** Height of the blade this block belongs to, in blocks. */
    private int groupHeight = 1;

    /** Width of the blade this block belongs to, in blocks. */
    private int groupWidth = 1;

    /**
     * How the mount on the back is shown. A new blade shows it in the middle; the wrench changes it.
     * All blocks of a blade hold the same value; the anchor's is the one drawn.
     */
    private BladeMount mount = BladeMount.CENTRE;

    // ------------------------------------------------------- not saved, per tick

    /** Follows a point on this block's working face in the world and tells how it moved over the last ticks. */
    private final PushTracker pushTracker = new PushTracker();

    /** Ticks left in which the blade still counts as pushing, after it was last seen moving face first. */
    private int pushingTicksLeft;

    /** Set after the first tick on a contraption, where joining is checked once more. */
    private boolean regroupedOnContraption;

    public BulldozerBladeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BULLDOZER_BLADE.get(), pos, state);
    }

    /** The blade uses none of Create's plug-in parts. */
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    // ------------------------------------------------------------ this block

    /** The blocks of the pile that this blade block stores, oldest first. Not to be changed by the caller. */
    public List<Stored> getStored() {
        return stored;
    }

    private boolean hasRoom() {
        return stored.size() < BladePile.capacityPerBlock(getGroupHeight());
    }

    // --------------------------------------------------------- the whole blade

    /** Called by {@link BladeGroup} when the blade this block belongs to changes. */
    public void setGroup(BlockPos anchorOffset, int height, int width) {
        if (this.anchorOffset.equals(anchorOffset) && groupHeight == height && groupWidth == width) {
            return;
        }
        this.anchorOffset = anchorOffset.immutable();
        this.groupHeight = height;
        this.groupWidth = width;
        notifyUpdate();
    }

    public boolean isJoined() {
        return getBlockState().getValue(BulldozerBladeBlock.JOINED);
    }

    /** True for the one block of a joined blade that draws it and acts for it. Always true for a single blade. */
    public boolean isAnchor() {
        return !isJoined() || anchorOffset.equals(BlockPos.ZERO);
    }

    public int getGroupHeight() {
        return isJoined() ? groupHeight : 1;
    }

    public int getGroupWidth() {
        return isJoined() ? groupWidth : 1;
    }

    /** Where the blade's anchor block is, counted from this block. Zero for a single blade. */
    public BlockPos getAnchorOffset() {
        return isJoined() ? anchorOffset : BlockPos.ZERO;
    }

    public BladeMount getMount() {
        return mount;
    }

    /** Switches the whole blade to the next mount: middle, ends, none, middle, ... */
    public BladeMount cycleGroupMount() {
        BladeMount next = mount.next();
        for (BulldozerBladeBlockEntity member : getGroupMembers()) {
            member.mount = next;
            member.notifyUpdate();
        }
        return next;
    }

    /** How many blocks the whole blade can carry: the size of one column's pile times the blade's width. */
    public int getGroupCapacity() {
        return BladePile.capacityPerColumn(getGroupHeight()) * getGroupWidth();
    }

    /** This block's place along the blade's width: 0 for the leftmost block. */
    public int getColumn() {
        return -BucketGroup.along(getAnchorOffset(), BladeGroup.axes(getBlockState()).right());
    }

    /** This block's place along the blade's height: 0 for the bottom row. */
    public int getRow() {
        return -BucketGroup.along(getAnchorOffset(), BladeGroup.axes(getBlockState()).up());
    }

    /** The block entities of all blocks of this blade, column by column, bottom first. Works on server and client. */
    public List<BulldozerBladeBlockEntity> getGroupMembers() {
        List<BulldozerBladeBlockEntity> members = new ArrayList<>();
        if (level == null) {
            members.add(this);
            return members;
        }
        for (BlockPos pos : BladeGroup.members(getBlockPos(), getBlockState(), anchorOffset, groupHeight, groupWidth)) {
            if (pos.equals(getBlockPos())) {
                members.add(this);
            } else if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof BulldozerBladeBlockEntity other) {
                members.add(other);
            }
        }
        return members;
    }

    /**
     * What each column's pile holds, oldest block first. The list has one entry
     * per block of the blade's width; an empty column has an empty entry.
     * The position of a block in its column's list is its number in {@link BladePile#slot}.
     */
    public List<List<Stored>> getColumnContents() {
        int width = getGroupWidth();
        List<List<Stored>> columns = new ArrayList<>(width);
        for (int column = 0; column < width; column++) {
            columns.add(new ArrayList<>());
        }
        // Members come column by column, bottom first, and a column fills its lowest blade block first.
        for (BulldozerBladeBlockEntity member : getGroupMembers()) {
            int column = member.getColumn();
            if (column >= 0 && column < width) {
                columns.get(column).addAll(member.stored);
            }
        }
        return columns;
    }

    /** How many blocks the pile of one column holds. */
    private int countColumn(int column) {
        int count = 0;
        for (BulldozerBladeBlockEntity member : getGroupMembers()) {
            if (member.getColumn() == column) {
                count += member.stored.size();
            }
        }
        return count;
    }

    /** What the whole blade carries: each kind of block with its count, most first. */
    public Map<Block, Integer> countGroupContent() {
        Map<Block, Integer> counts = new LinkedHashMap<>();
        for (BulldozerBladeBlockEntity member : getGroupMembers()) {
            for (Stored block : member.stored) {
                counts.merge(block.state().getBlock(), 1, Integer::sum);
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

    /**
     * The blade block that should store a block met by this block: one of this
     * block's own column if its pile has room, otherwise of the nearest column
     * whose pile has room. Within a column the lowest blade block with room,
     * so that the order of the pile stays the order of taking. Null if the blade is full.
     */
    @Nullable
    private BulldozerBladeBlockEntity findReceiver() {
        int ownColumn = getColumn();
        BulldozerBladeBlockEntity best = null;
        int bestColumnDistance = Integer.MAX_VALUE;
        int bestRow = Integer.MAX_VALUE;
        for (BulldozerBladeBlockEntity member : getGroupMembers()) {
            if (!member.hasRoom()) {
                continue;
            }
            int columnDistance = Math.abs(member.getColumn() - ownColumn);
            int row = member.getRow();
            if (columnDistance < bestColumnDistance || (columnDistance == bestColumnDistance && row < bestRow)) {
                best = member;
                bestColumnDistance = columnDistance;
                bestRow = row;
            }
        }
        return best;
    }

    /** The blade block that stores the newest block of a column's pile: the highest one that stores anything. Null if the pile is empty. */
    @Nullable
    private BulldozerBladeBlockEntity findTopOfColumn(int column) {
        BulldozerBladeBlockEntity top = null;
        for (BulldozerBladeBlockEntity member : getGroupMembers()) {
            if (member.getColumn() == column && !member.stored.isEmpty()) {
                // Members come bottom first, so the last one found is the highest.
                top = member;
            }
        }
        return top;
    }

    // ---------------------------------------------------------- Create's goggles

    /** Shown when a player wearing Create's goggles looks at any block of the blade: how full it is and what it carries. */
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

    // ---------------------------------------------------------------- working

    @Override
    public void sable$tick(ServerSubLevel subLevel) {
        if (!(level instanceof ServerLevel serverLevel) || Sable.HELPER.getContaining(this) != subLevel) {
            return;
        }

        // It is not known whether assembling a contraption runs the normal
        // "block placed" logic. So joining is checked once more here, by one block per blade.
        if (!regroupedOnContraption) {
            regroupedOnContraption = true;
            if (isAnchor()) {
                BladeGroup.regroup(serverLevel, getBlockPos());
            }
        }

        // Positions of blocks on a contraption are stored far away from where
        // the player sees them. Sable converts ("projects") them to the world.
        Direction facing = getBlockState().getValue(BulldozerBladeBlock.FACING);
        Vec3 forward = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 localCentre = getBlockPos().getCenter();
        Vec3 worldCentre = Sable.HELPER.projectOutOfSubLevel(serverLevel, localCentre);
        Vec3 worldProbe = Sable.HELPER.projectOutOfSubLevel(serverLevel, localCentre.add(forward.scale(PROBE_DISTANCE)));

        // Which way the working face points in the world, and whether that is close enough to level.
        Vec3 faceDirection = worldProbe.subtract(worldCentre).normalize();
        boolean nearLevel = Math.abs(faceDirection.y) <= TILT_LIMIT;

        // Must run every tick, loaded or not, so the record of the movement has no gaps.
        pushTracker.update(worldProbe);
        double forwardShare = pushTracker.share(faceDirection);

        // Falling material that comes down right in front of the blade joins the pile.
        if (nearLevel) {
            catchFallingMaterial(serverLevel, localCentre, forward);
        }

        // One block looks after the whole blade's load: the anchor.
        if (isAnchor()) {
            if (!nearLevel || forwardShare < -BACK_SHARE) {
                // Tilted too far, or backing away: the pile stays behind.
                setWholePileDown(serverLevel);
            } else {
                runIntoGaps(serverLevel);
            }
        }

        // Remember pushing for a short while, so a blade that is stopped by the
        // block in front of it still takes that block. Backing away ends it at once.
        if (forwardShare > PUSH_SHARE) {
            pushingTicksLeft = PUSH_MEMORY_TICKS;
        } else if (forwardShare < -BACK_SHARE) {
            pushingTicksLeft = 0;
        } else if (pushingTicksLeft > 0) {
            pushingTicksLeft--;
        }
        if (!nearLevel || pushingTicksLeft <= 0) {
            return;
        }

        // Pushed face first: take every diggable block the working face touches.
        for (BlockPos target : probeTargets(serverLevel, localCentre, forward)) {
            tryTake(serverLevel, target);
        }
    }

    /** The world blocks that this block's working face currently touches. */
    private Set<BlockPos> probeTargets(ServerLevel serverLevel, Vec3 localCentre, Vec3 forward) {
        BucketGroup.Axes axes = BladeGroup.axes(getBlockState());
        Vec3 right = Vec3.atLowerCornerOf(axes.right().getNormal());
        Vec3 up = Vec3.atLowerCornerOf(axes.up().getNormal());

        Set<BlockPos> targets = new LinkedHashSet<>();
        for (double r : PROBE_ACROSS) {
            for (double u : PROBE_UP) {
                Vec3 local = localCentre.add(right.scale(r)).add(up.scale(u)).add(forward.scale(PROBE_DISTANCE));
                targets.add(BlockPos.containing(Sable.HELPER.projectOutOfSubLevel(serverLevel, local)));
            }
        }
        return targets;
    }

    /**
     * Takes the block at the given place in the world into the pile, if it may
     * be taken. What stands on that block follows it into the pile as far as
     * there is room. With a full pile the block is moved to the side instead;
     * if there is no room there either, it stays where it is and stops the blade.
     */
    private void tryTake(ServerLevel serverLevel, BlockPos target) {
        // Diggable, in the normal world, not unbreakable: the rules shared by all tools.
        if (!WorldDig.canTake(serverLevel, target)) {
            return;
        }
        // Find room before touching the world: in the pile, or else beside the blade.
        BulldozerBladeBlockEntity receiver = findReceiver();
        BlockPos overflowPlace = null;
        if (receiver == null) {
            overflowPlace = findOverflowPlace(serverLevel);
            if (overflowPlace == null) {
                return;
            }
        }

        // Taken without break particles: the block is not broken, it is pushed along.
        // No item drops either. A quiet sound of the material being moved.
        WorldDig.DugBlock dug = WorldDig.take(serverLevel, target, false);
        if (dug == null) {
            return;
        }
        serverLevel.playSound(null, target, dug.state().getSoundType(serverLevel, target, null).getPlaceSound(),
            SoundSource.BLOCKS, 0.6F, 0.8F);
        if (receiver == null) {
            // Full: the block goes off the end of the blade. Nothing more is brought down.
            putDown(serverLevel, overflowPlace, dug.state(), dug.blockEntityData());
            return;
        }
        addToPile(serverLevel, receiver, dug.state(), dug.blockEntityData(), target.getCenter());

        // What stood on the taken block comes into the pile too, but only as much
        // as the pile can hold. The rest stays where it is until the next pass.
        // Nothing falls: falling blocks around a moving machine end up under it.
        if (!Config.COLLAPSE_ENABLED.get()) {
            return;
        }
        int maxHeight = Config.COLLAPSE_MAX_HEIGHT.get();
        for (int step = 1; step <= maxHeight; step++) {
            BlockPos above = target.above(step);
            if (!WorldDig.canTake(serverLevel, above)) {
                return;
            }
            receiver = findReceiver();
            if (receiver == null) {
                return;
            }
            WorldDig.DugBlock fallen = WorldDig.take(serverLevel, above, false);
            if (fallen == null) {
                return;
            }
            addToPile(serverLevel, receiver, fallen.state(), fallen.blockEntityData(), above.getCenter());
        }
    }

    /**
     * Stores a block in the given blade block, as the newest block of that
     * column's pile, and notes where it came from so that it can be drawn
     * sliding into its place.
     *
     * @param cameFrom the middle of the place in the world it came from
     */
    private void addToPile(ServerLevel serverLevel, BulldozerBladeBlockEntity receiver, BlockState state,
                           @Nullable CompoundTag blockEntityData, Vec3 cameFrom) {
        int column = receiver.getColumn();
        int[] slot = BladePile.slot(getGroupHeight(), countColumn(column));

        // The blade's own directions as they point in the world right now.
        BucketGroup.Axes axes = BladeGroup.axes(getBlockState());
        Vec3 localOrigin = getBlockPos().getCenter();
        Vec3 worldOrigin = Sable.HELPER.projectOutOfSubLevel(serverLevel, localOrigin);
        Vec3 worldRight = Sable.HELPER.projectOutOfSubLevel(serverLevel,
            localOrigin.add(Vec3.atLowerCornerOf(axes.right().getNormal()))).subtract(worldOrigin);
        Vec3 worldUp = Sable.HELPER.projectOutOfSubLevel(serverLevel,
            localOrigin.add(Vec3.atLowerCornerOf(axes.up().getNormal()))).subtract(worldOrigin);
        Vec3 worldForward = Sable.HELPER.projectOutOfSubLevel(serverLevel,
            localOrigin.add(Vec3.atLowerCornerOf(axes.back().getOpposite().getNormal()))).subtract(worldOrigin);

        // From the block's new place in the pile to where it came from, in those directions.
        Vec3 worldSlot = Sable.HELPER.projectOutOfSubLevel(serverLevel, localPilePlace(column, slot[0], slot[1]));
        Vec3 offset = cameFrom.subtract(worldSlot);
        if (offset.length() > MAX_SLIDE_DISTANCE) {
            offset = offset.normalize().scale(MAX_SLIDE_DISTANCE);
        }
        receiver.stored.add(new Stored(state, blockEntityData,
            (float) offset.dot(worldRight), (float) offset.dot(worldUp), (float) offset.dot(worldForward),
            serverLevel.getGameTime()));
        receiver.notifyUpdate();
    }

    /**
     * Takes in falling material that is in front of this block: over the front
     * of the blade block or over the place in front of it. It joins the pile if
     * there is room. With a full pile it is left alone and lands as usual.
     */
    private void catchFallingMaterial(ServerLevel serverLevel, Vec3 localCentre, Vec3 forward) {
        Vec3 worldMiddle = Sable.HELPER.projectOutOfSubLevel(serverLevel, localCentre.add(forward.scale(CATCH_ZONE_DISTANCE)));
        AABB catchZone = AABB.ofSize(worldMiddle, CATCH_ZONE_SIZE, CATCH_ZONE_SIZE, CATCH_ZONE_SIZE);
        List<FallingMaterialEntity> found = serverLevel.getEntitiesOfClass(
            FallingMaterialEntity.class,
            catchZone,
            entity -> entity.isAlive() && catchZone.contains(entity.getBoundingBox().getCenter())
        );
        for (FallingMaterialEntity entity : found) {
            BlockState carried = entity.getCarriedState();
            if (carried.isAir()) {
                continue;
            }
            BulldozerBladeBlockEntity receiver = findReceiver();
            if (receiver == null) {
                return;
            }
            addToPile(serverLevel, receiver, carried, entity.getCarriedBlockData(), entity.getBoundingBox().getCenter());
            entity.discard();
        }
    }

    // ------------------------------------------------------------------ places

    /** The anchor block's position: the bottom left block of the blade. */
    private BlockPos getAnchorPos() {
        return getBlockPos().offset(getAnchorOffset());
    }

    /**
     * The middle of a place in a column's pile, in the blade's own coordinates
     * (not yet converted to the world). Columns are counted from the left;
     * depth and level are explained in {@link BladePile}.
     */
    private Vec3 localPilePlace(int column, int depth, int level) {
        BucketGroup.Axes axes = BladeGroup.axes(getBlockState());
        Direction facing = getBlockState().getValue(BulldozerBladeBlock.FACING);
        return getAnchorPos().relative(axes.right(), column).getCenter()
            .add(Vec3.atLowerCornerOf(facing.getNormal()).scale(1.0 + depth))
            .add(Vec3.atLowerCornerOf(axes.up().getNormal()).scale(level));
    }

    /** The world block that a place in a column's pile lies in. */
    private BlockPos pilePlace(ServerLevel serverLevel, int column, int depth, int level) {
        return BlockPos.containing(Sable.HELPER.projectOutOfSubLevel(serverLevel, localPilePlace(column, depth, level)));
    }

    /**
     * Where a full blade puts a block it cannot carry: one block beyond the end
     * of the blade, beside the first stack of the pile, on the side nearer to
     * this block. A block in the exact middle picks a side at random.
     * If that place is taken, the other end. Null if neither is free.
     */
    @Nullable
    private BlockPos findOverflowPlace(ServerLevel serverLevel) {
        int width = getGroupWidth();
        int column = getColumn();
        // Twice the distance from the middle: negative means nearer to the left end.
        int offCentre = 2 * column - (width - 1);
        boolean leftFirst = offCentre < 0 || (offCentre == 0 && serverLevel.random.nextBoolean());

        BlockPos left = pilePlace(serverLevel, -1, 0, 0);
        BlockPos right = pilePlace(serverLevel, width, 0, 0);
        BlockPos first = leftFirst ? left : right;
        BlockPos second = leftFirst ? right : left;
        if (isFree(serverLevel, first)) {
            return first;
        }
        if (isFree(serverLevel, second)) {
            return second;
        }
        return null;
    }

    /**
     * The world position that lies under one stack of a column's pile, one block
     * below the blade's cutting edge. If this place is free, there is a gap under
     * the pile that is at least {@link #GAP_DEPTH} deep.
     *
     * It is measured from the cutting edge and not from the block grid, because a
     * blade on a vehicle rarely sits exactly on the grid: it may ride a little
     * high or low, and that must not count as a gap.
     */
    private BlockPos placeUnderPile(ServerLevel serverLevel, int column, int depth) {
        BucketGroup.Axes axes = BladeGroup.axes(getBlockState());
        // A point on the level of the cutting edge, in the middle of that stack.
        Vec3 local = localPilePlace(column, depth, 0)
            .add(Vec3.atLowerCornerOf(axes.up().getNormal()).scale(-0.5));
        Vec3 world = Sable.HELPER.projectOutOfSubLevel(serverLevel, local);
        return BlockPos.containing(world.x, world.y - GAP_DEPTH, world.z);
    }

    /**
     * Puts a block down in the world as a block, at once. Starting at the given
     * place it goes down as far as there is free room, like something that is
     * dropped, and if the place itself is taken it goes up a few places.
     *
     * The blade does not let its material fall as an entity the way the bucket
     * does. A fall takes several ticks, and in that time the blade drives on, so
     * the material would land inside the blade or under the vehicle and lift it.
     * Only if no place can be found at all is it handed to the falling material,
     * which ends as an item in the worst case.
     */
    private static void putDown(ServerLevel serverLevel, BlockPos start, BlockState state, @Nullable CompoundTag blockEntityData) {
        BlockPos.MutableBlockPos place = start.mutable();
        int stepsUp = 0;
        while (!isFree(serverLevel, place) && stepsUp < MAX_STEPS_UP) {
            place.move(Direction.UP);
            stepsUp++;
        }
        if (!isFree(serverLevel, place)) {
            FallingMaterialEntity.spawn(serverLevel, start, state, blockEntityData);
            return;
        }
        while (isFree(serverLevel, place.below())) {
            place.move(Direction.DOWN);
        }
        if (!state.canSurvive(serverLevel, place) || !serverLevel.setBlock(place, state, Block.UPDATE_ALL)) {
            FallingMaterialEntity.spawn(serverLevel, start, state, blockEntityData);
            return;
        }
        if (blockEntityData != null) {
            BlockEntity blockEntity = serverLevel.getBlockEntity(place);
            if (blockEntity != null) {
                blockEntity.loadWithComponents(blockEntityData, serverLevel.registryAccess());
                blockEntity.setChanged();
            }
        }
    }

    /** True if a block may be put here: the place is loaded, in the normal world, and holds air or something replaceable. */
    private static boolean isFree(ServerLevel serverLevel, BlockPos place) {
        if (serverLevel.isOutsideBuildHeight(place) || !serverLevel.isLoaded(place)) {
            return false;
        }
        if (Sable.HELPER.getContaining(serverLevel, place) != null) {
            return false;
        }
        return serverLevel.getBlockState(place).canBeReplaced();
    }

    // ----------------------------------------------------------- letting go

    /**
     * Runs on the anchor block every tick. For every column, if there is a gap
     * under one of the stacks of its pile, the newest block of the pile is put
     * down into that gap. The far end of the pile is looked at first, because it
     * reaches a hole first. One block per column per tick. That fills holes and
     * trenches, and it empties the blade when it is lifted by a block or more.
     */
    private void runIntoGaps(ServerLevel serverLevel) {
        int height = getGroupHeight();
        int width = getGroupWidth();
        for (int column = 0; column < width; column++) {
            int count = countColumn(column);
            if (count == 0) {
                continue;
            }
            // How many stacks deep this column's pile currently is.
            int usedDepth = BladePile.slot(height, count - 1)[0];
            for (int depth = usedDepth; depth >= 0; depth--) {
                BlockPos under = placeUnderPile(serverLevel, column, depth);
                if (isFree(serverLevel, under)) {
                    BulldozerBladeBlockEntity top = findTopOfColumn(column);
                    if (top != null) {
                        Stored block = top.stored.remove(top.stored.size() - 1);
                        top.notifyUpdate();
                        putDown(serverLevel, under, block.state(), block.blockEntityData());
                    }
                    break;
                }
            }
        }
    }

    /**
     * Runs on the anchor block. The whole pile leaves the blade and is put down
     * in the shape it was drawn in: each block at its stack of the pile, the
     * oldest first, so the stacks build up again from the bottom.
     */
    private void setWholePileDown(ServerLevel serverLevel) {
        int height = getGroupHeight();
        List<List<Stored>> columns = getColumnContents();
        boolean any = false;
        for (int column = 0; column < columns.size(); column++) {
            List<Stored> pile = columns.get(column);
            for (int index = 0; index < pile.size(); index++) {
                any = true;
                Stored block = pile.get(index);
                int depth = BladePile.slot(height, index)[0];
                putDown(serverLevel, pilePlace(serverLevel, column, depth, 0), block.state(), block.blockEntityData());
            }
        }
        if (!any) {
            return;
        }
        for (BulldozerBladeBlockEntity member : getGroupMembers()) {
            if (!member.stored.isEmpty()) {
                member.stored.clear();
                member.notifyUpdate();
            }
        }
    }

    // --------------------------------------------------------------- breaking

    /** Empties this block and drops what its part of the pile would drop if broken in the world with an unenchanted shovel tool. */
    public void dropContentAsBroken(ServerLevel serverLevel) {
        if (stored.isEmpty()) {
            return;
        }
        BlockPos pos = getBlockPos();
        List<ItemStack> drops = new ArrayList<>();
        for (Stored block : stored) {
            BlockEntity storedBlockEntity = block.blockEntityData() == null ? null
                : BlockEntity.loadStatic(pos, block.state(), block.blockEntityData(), serverLevel.registryAccess());
            drops.addAll(
                Block.getDrops(block.state(), serverLevel, pos, storedBlockEntity, null, new ItemStack(Items.IRON_SHOVEL))
            );
            // Containers such as chests spill their items when broken. Shulker boxes
            // keep their items inside the dropped box instead, so they are skipped here.
            if (storedBlockEntity instanceof Container container && !(block.state().getBlock() instanceof ShulkerBoxBlock)) {
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    ItemStack stack = container.getItem(slot);
                    if (!stack.isEmpty()) {
                        drops.add(stack);
                    }
                }
            }
        }
        stored.clear();
        setChanged();
        for (ItemStack stack : drops) {
            Containers.dropItemStack(serverLevel, pos.getX(), pos.getY(), pos.getZ(), stack);
        }
    }

    // ------------------------------------------------- saving and sending

    // Create's base class calls these two for both jobs: saving the block with the
    // world, and sending it to the players' games (then clientPacket is true).

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        // Always written, also when empty, so that "now empty" reaches the client.
        ListTag list = new ListTag();
        for (Stored block : stored) {
            CompoundTag entry = new CompoundTag();
            entry.put(TAG_BLOCK, NbtUtils.writeBlockState(block.state()));
            if (block.blockEntityData() != null) {
                entry.put(TAG_BLOCK_ENTITY, block.blockEntityData().copy());
            }
            entry.putFloat(TAG_FROM_RIGHT, block.fromRight());
            entry.putFloat(TAG_FROM_UP, block.fromUp());
            entry.putFloat(TAG_FROM_FORWARD, block.fromForward());
            entry.putLong(TAG_TAKEN_AT, block.takenAt());
            list.add(entry);
        }
        tag.put(TAG_STORED, list);
        tag.putInt(TAG_ANCHOR_X, anchorOffset.getX());
        tag.putInt(TAG_ANCHOR_Y, anchorOffset.getY());
        tag.putInt(TAG_ANCHOR_Z, anchorOffset.getZ());
        tag.putInt(TAG_GROUP_HEIGHT, groupHeight);
        tag.putInt(TAG_GROUP_WIDTH, groupWidth);
        tag.putInt(TAG_MOUNT, mount.ordinal());
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        stored.clear();
        if (tag.contains(TAG_STORED, Tag.TAG_LIST)) {
            ListTag list = tag.getList(TAG_STORED, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                BlockState state = NbtUtils.readBlockState(
                    registries.lookupOrThrow(Registries.BLOCK), entry.getCompound(TAG_BLOCK)
                );
                if (state.isAir()) {
                    continue;
                }
                CompoundTag data = entry.contains(TAG_BLOCK_ENTITY, Tag.TAG_COMPOUND)
                    ? entry.getCompound(TAG_BLOCK_ENTITY).copy() : null;
                stored.add(new Stored(state, data,
                    entry.getFloat(TAG_FROM_RIGHT), entry.getFloat(TAG_FROM_UP), entry.getFloat(TAG_FROM_FORWARD),
                    entry.getLong(TAG_TAKEN_AT)));
            }
        } else if (tag.contains(TAG_OLD_STORED_BLOCK, Tag.TAG_COMPOUND)) {
            // Saved by the first version of the blade: a single block.
            BlockState state = NbtUtils.readBlockState(
                registries.lookupOrThrow(Registries.BLOCK), tag.getCompound(TAG_OLD_STORED_BLOCK)
            );
            if (!state.isAir()) {
                CompoundTag data = tag.contains(TAG_OLD_STORED_BLOCK_ENTITY, Tag.TAG_COMPOUND)
                    ? tag.getCompound(TAG_OLD_STORED_BLOCK_ENTITY).copy() : null;
                stored.add(new Stored(state, data, 0.0F, 0.0F, 0.0F, 0L));
            }
        }
        anchorOffset = new BlockPos(tag.getInt(TAG_ANCHOR_X), tag.getInt(TAG_ANCHOR_Y), tag.getInt(TAG_ANCHOR_Z));
        groupHeight = Math.max(1, tag.getInt(TAG_GROUP_HEIGHT));
        groupWidth = Math.max(1, tag.getInt(TAG_GROUP_WIDTH));
        if (tag.contains(TAG_MOUNT)) {
            mount = BladeMount.byIndex(tag.getInt(TAG_MOUNT));
        } else if (tag.contains(TAG_MOUNT_SHOWN)) {
            // Saved by an older version: "shown" was what is now the mount at the ends.
            mount = tag.getBoolean(TAG_MOUNT_SHOWN) ? BladeMount.ENDS : BladeMount.NONE;
        } else {
            mount = BladeMount.CENTRE;
        }
    }
}
