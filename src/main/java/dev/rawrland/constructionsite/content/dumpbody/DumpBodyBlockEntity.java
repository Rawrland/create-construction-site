package dev.rawrland.constructionsite.content.dumpbody;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.rawrland.constructionsite.content.material.FallingMaterialEntity;
import dev.rawrland.constructionsite.content.tool.ContraptionTool;
import dev.rawrland.constructionsite.content.tool.ContraptionToolShadow;
import dev.rawrland.constructionsite.content.tool.TipAngle;
import dev.rawrland.constructionsite.content.tool.ToolHost;
import dev.rawrland.constructionsite.content.tool.WorldDig;
import dev.rawrland.constructionsite.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
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
import java.util.List;
import java.util.Map;

/**
 * One cell of a dump body: holds at most one block of the load. The anchor cell
 * (the box's corner with the lowest x, y and z) does the work for the whole
 * dump body: catching falling material that comes down into it, and emptying it
 * over its low side when it is tilted far enough.
 *
 * The box is thought of as columns: one per block of its floor, as high as the
 * box. A column fills from the bottom and empties from the top.
 *
 * The work runs in {@link #work}:
 * <ul>
 *     <li>in the normal world, from {@link #tick} (catching only: a dump body
 *     standing on the ground is never tilted);</li>
 *     <li>on Create Aeronautics' simulated contraptions, from
 *     {@code compat.aeronautics.AeroDumpBodyBlockEntity} through
 *     {@link #tickOnSimulatedContraption};</li>
 *     <li>on Create's own contraptions (bearings, pistons, gantries, minecarts,
 *     trains), on a stand-in copy (see {@link ContraptionToolShadow}).</li>
 * </ul>
 * All positions go through the {@link ToolHost}, so the same code runs on each.
 * A dump body never stops the machine it is on.
 *
 * This class works without Create Aeronautics and must never name a Create
 * Aeronautics, Simulated or Sable class.
 */
public class DumpBodyBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, ContraptionTool {

    /** While emptying, each lane lets one block go once every this many ticks. Set by the spec. */
    private static final int RELEASE_INTERVAL_TICKS = 2;

    /**
     * A different side only takes over as the low side when it points down
     * clearly more than the current one: this much in sine, about 3 degrees.
     * Keeps the stream from jumping between two sides on a wobbling vehicle.
     */
    private static final double SIDE_CHANGE_MARGIN = 0.05;

    /** The open gate's angle is only sent to players again when it changed by this many degrees. */
    private static final float ANGLE_SEND_STEP = 3.0F;

    /** Falling material is caught while it is up to this far above the walls, in blocks. */
    private static final double CATCH_HEIGHT = 1.5;
    /** ... and while it is at most this far sideways from the middle of a column. */
    private static final double CATCH_REACH = 0.75;

    private static final String GOGGLES_TITLE = "gui.goggles.create_construction_site.dump_body.title";
    private static final String GOGGLES_FILL = "gui.goggles.create_construction_site.dump_body.fill";
    private static final String GOGGLES_EMPTY = "gui.goggles.create_construction_site.dump_body.empty";

    private static final String TAG_STORED_BLOCK = "StoredBlock";
    private static final String TAG_STORED_BLOCK_ENTITY = "StoredBlockEntity";
    private static final String TAG_ANCHOR_X = "AnchorX";
    private static final String TAG_ANCHOR_Y = "AnchorY";
    private static final String TAG_ANCHOR_Z = "AnchorZ";
    private static final String TAG_SIZE_X = "SizeX";
    private static final String TAG_SIZE_Y = "SizeY";
    private static final String TAG_SIZE_Z = "SizeZ";
    /** The side that is open right now (Direction 3D index; 1 = up, the open top), or -1. Only on the anchor. */
    public static final String TAG_OPEN_SIDE = "OpenSide";
    /** How far the open side points below level, in degrees: the angle the gate hangs at. Only on the anchor. */
    public static final String TAG_OPEN_ANGLE = "OpenAngle";
    /** Which way is down for the body, in its own directions (see the field). Only on the anchor. */
    public static final String TAG_LEAN_X = "LeanX";
    public static final String TAG_LEAN_Y = "LeanY";
    public static final String TAG_LEAN_Z = "LeanZ";

    // ----------------------------------------------------------------- content

    /** The block this cell holds, or null when empty. */
    @Nullable
    private BlockState storedState;

    /** Its extra data (for example the items in a chest), or null. */
    @Nullable
    private CompoundTag storedBlockEntityData;

    // ------------------------------------------------------------------- group

    /** Where the anchor is, counted from this block. Zero for a single block. */
    private BlockPos anchorOffset = BlockPos.ZERO;
    private int sizeX = 1;
    private int sizeY = 1;
    private int sizeZ = 1;

    // ------------------------------------------------------- the anchor's gate

    /** The side (or the top) the body is tilted toward and open on now, or null. Sent to players, who draw that gate open. */
    @Nullable
    private Direction openSide;

    /** How far the open side points below level, in degrees. */
    private float openAngle;

    /**
     * Which way "down" is for the body right now, in its own coordinates (x east,
     * y up, z south of the body as built): straight down (0, -1, 0) when level. Sent to
     * the players' games, which draw the load settled that way (only for show: where
     * the blocks are stored does not change). Only on the anchor.
     */
    private float leanX, leanY = -1.0F, leanZ;

    // ------------------------------------------------------- not saved, per tick

    private int releaseCooldown;

    /** Set after the first tick on a simulated contraption, where joining is checked once more. */
    private boolean regroupedOnContraption;

    /**
     * Set when this block was put down by one of Create's contraptions. Joining
     * is then checked again, because the contraption may have turned the box.
     */
    private boolean regroupAfterMove;

    /**
     * The contraption this block is working on, while it works. Null in the
     * normal world: then positions are world positions and the other cells are
     * looked up in this block's level.
     */
    @Nullable
    private ToolHost host;

    // ------------------------------------------------------- drawing (players' games only)

    /** How far each gate is drawn open, 0 to 1, by 2D index. Eased by the renderer. */
    public final float[] gateOpenProgress = new float[4];
    /** When the renderer last eased the gates, in its own clock. */
    public long gateAnimatedAt = Long.MIN_VALUE;

    public DumpBodyBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DUMP_BODY.get(), pos, state);
    }

    /** The dump body uses none of Create's plug-in parts. */
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    /**
     * Runs every tick in the normal world: after one of Create's contraptions has
     * put the dump body down, checks once which blocks belong together; and lets
     * the anchor of a dump body standing on the ground catch falling material.
     */
    @Override
    public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (regroupAfterMove) {
            regroupAfterMove = false;
            DumpBodyGroup.regroup(serverLevel, getBlockPos());
            return;
        }
        // On a simulated contraption the block also ticks here, but there its work is
        // done by sable$tick with the right positions.
        if (isAnchor() && !WorldDig.isOnSimulatedContraption(serverLevel, getBlockPos())) {
            host = null;
            work(serverLevel);
        }
    }

    // ------------------------------------------------------------ this block

    @Nullable
    public BlockState getStoredState() {
        return storedState;
    }

    public boolean isEmpty() {
        return storedState == null;
    }

    private void setContent(BlockState state, @Nullable CompoundTag blockEntityData) {
        storedState = state;
        storedBlockEntityData = blockEntityData == null ? null : blockEntityData.copy();
        notifyUpdate();
    }

    private void clearContent() {
        storedState = null;
        storedBlockEntityData = null;
        notifyUpdate();
    }

    // --------------------------------------------------------- the whole body

    /** Called by {@link DumpBodyGroup} when the box this block belongs to changes. */
    public void setGroup(BlockPos anchorOffset, int sizeX, int sizeY, int sizeZ) {
        if (this.anchorOffset.equals(anchorOffset) && this.sizeX == sizeX && this.sizeY == sizeY
            && this.sizeZ == sizeZ) {
            return;
        }
        this.anchorOffset = anchorOffset.immutable();
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        if (!isAnchor()) {
            openSide = null;
        }
        notifyUpdate();
    }

    public boolean isJoined() {
        return getBlockState().getValue(DumpBodyBlock.JOINED);
    }

    /** True for the one block of a dump body that draws it and works for it. Always true for a single block. */
    public boolean isAnchor() {
        return !isJoined() || anchorOffset.equals(BlockPos.ZERO);
    }

    public BlockPos getAnchorOffset() {
        return isJoined() ? anchorOffset : BlockPos.ZERO;
    }

    public int getSizeX() {
        return isJoined() ? sizeX : 1;
    }

    public int getSizeY() {
        return isJoined() ? sizeY : 1;
    }

    public int getSizeZ() {
        return isJoined() ? sizeZ : 1;
    }

    /** One block per cell. */
    public int getCapacity() {
        return getSizeX() * getSizeY() * getSizeZ();
    }

    /** The side that is emptying now, as the players' games know it; null if none. Only meaningful on the anchor. */
    @Nullable
    public Direction getOpenSide() {
        return openSide;
    }

    /** How far the open side points below level, in degrees. */
    public float getOpenAngle() {
        return openAngle;
    }

    /** The cell at a place of the box, counted from the anchor: x and z along the floor, y up. */
    @Nullable
    public DumpBodyBlockEntity cell(int x, int y, int z) {
        return cellAt(getBlockPos().offset(getAnchorOffset()).offset(x, y, z));
    }

    /**
     * The dump body block entity at a position, in this block's own coordinates:
     * through the contraption while working on one, otherwise in this block's level.
     */
    @Nullable
    private DumpBodyBlockEntity cellAt(BlockPos pos) {
        if (pos.equals(getBlockPos())) {
            return this;
        }
        if (host != null) {
            return host.blockEntityAt(pos) instanceof DumpBodyBlockEntity other ? other : null;
        }
        if (level != null && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof DumpBodyBlockEntity other) {
            return other;
        }
        return null;
    }

    /** The anchor's block entity, or null if it cannot be found. */
    @Nullable
    private DumpBodyBlockEntity getAnchor() {
        return cellAt(getBlockPos().offset(getAnchorOffset()));
    }

    /** All cells of the dump body that could be found. Works on server and client. */
    public List<DumpBodyBlockEntity> getMembers() {
        List<DumpBodyBlockEntity> members = new ArrayList<>(getCapacity());
        if (!isJoined()) {
            members.add(this);
            return members;
        }
        DumpBodyBlockEntity anchor = getAnchor();
        if (anchor == null) {
            members.add(this);
            return members;
        }
        for (int x = 0; x < getSizeX(); x++) {
            for (int z = 0; z < getSizeZ(); z++) {
                for (int y = 0; y < getSizeY(); y++) {
                    DumpBodyBlockEntity cell = anchor.cell(x, y, z);
                    if (cell != null) {
                        members.add(cell);
                    }
                }
            }
        }
        return members;
    }

    /** How many blocks the whole dump body holds. */
    public int countHeld() {
        int count = 0;
        for (DumpBodyBlockEntity member : getMembers()) {
            if (!member.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /** What the whole dump body holds: each kind of block with its count, most first. */
    public Map<Block, Integer> countContent() {
        Map<Block, Integer> counts = new LinkedHashMap<>();
        for (DumpBodyBlockEntity member : getMembers()) {
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

    /**
     * Switches one side of the whole dump body between "may open" and "always
     * closed", by the wrench. Every block of the body gets the new setting.
     *
     * @return true if the side may open now
     */
    public boolean toggleGate(Direction side) {
        boolean nowOpen = !DumpBodyBlock.mayOpen(getBlockState(), side);
        if (level == null) {
            return nowOpen;
        }
        for (DumpBodyBlockEntity member : getMembers()) {
            BlockState state = level.getBlockState(member.getBlockPos());
            if (state.getBlock() instanceof DumpBodyBlock) {
                level.setBlock(member.getBlockPos(), state.setValue(DumpBodyBlock.gate(side), nowOpen),
                    Block.UPDATE_CLIENTS);
            }
        }
        return nowOpen;
    }

    // ---------------------------------------------------------- Create's goggles

    /** Shown when a player wearing Create's goggles looks at any block of the dump body. */
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Map<Block, Integer> content = countContent();
        int total = 0;
        for (int count : content.values()) {
            total += count;
        }
        CreateLang.builder()
            .add(Component.translatable(GOGGLES_TITLE))
            .style(ChatFormatting.WHITE)
            .forGoggles(tooltip);
        CreateLang.builder()
            .add(Component.translatable(GOGGLES_FILL, total, getCapacity()))
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

    /**
     * One tick on a Create Aeronautics simulated contraption, called by
     * AeroDumpBodyBlockEntity with the host that knows where the contraption is.
     */
    protected void tickOnSimulatedContraption(ServerLevel serverLevel, ToolHost simulatedHost) {
        // Whether assembling runs the normal "block placed" logic is not known,
        // so joining is checked once more, by the anchor.
        if (!regroupedOnContraption) {
            regroupedOnContraption = true;
            if (isAnchor()) {
                DumpBodyGroup.regroup(serverLevel, getBlockPos());
            }
        }
        host = simulatedHost;
        try {
            work(serverLevel);
        } finally {
            host = null;
        }
    }

    @Override
    public void setHost(@Nullable ToolHost host) {
        this.host = host;
    }

    /**
     * One tick of the dump body's work, done by the anchor for the whole body:
     * find the low side, empty over it if it may open and is steep enough for the
     * load, and otherwise catch falling material that comes down into the body.
     *
     * @return always null: a dump body never stops the machine it is on
     */
    @Nullable
    @Override
    public BlockPos work(ServerLevel serverLevel) {
        if (!isAnchor()) {
            return null;
        }
        int sx = getSizeX();
        int sy = getSizeY();
        int sz = getSizeZ();
        Vec3 localCentre = Vec3.atLowerCornerOf(getBlockPos()).add(sx / 2.0, sy / 2.0, sz / 2.0);
        Vec3 worldCentre = toWorld(localCentre);

        // How strongly each of the four sides, and the open top, points down in the world.
        // The top counts too: a body turned upside down empties through its open top.
        Direction low = null;
        double lowDownward = -2.0;
        double currentDownward = -2.0;
        double downEast = 0.0;
        double downSouth = 0.0;
        double downUp = -1.0;
        for (Direction side : OUTLETS) {
            Vec3 worldSide = toWorld(localCentre.add(Vec3.atLowerCornerOf(side.getNormal()).scale(0.5)));
            double downward = -worldSide.subtract(worldCentre).normalize().y;
            if (side == Direction.EAST) {
                downEast = downward;
            } else if (side == Direction.SOUTH) {
                downSouth = downward;
            } else if (side == Direction.UP) {
                downUp = downward;
            }
            if (downward > lowDownward) {
                low = side;
                lowDownward = downward;
            }
            if (side == openSide) {
                currentDownward = downward;
            }
        }
        // "Down" in the body's own directions: how far each of its axes points down.
        setLean(downEast, downUp, downSouth);
        // The side that is already emptying stays the low side until another is clearly lower.
        if (openSide != null && low != openSide && lowDownward < currentDownward + SIDE_CHANGE_MARGIN) {
            low = openSide;
            lowDownward = currentDownward;
        }

        // The low side's gate opens as soon as the body is tilted toward it far enough to
        // empty a full body, and stays open as long as it is, also while what is left needs
        // a steeper tilt and when the body is empty (owner's choice, 17 October 2026).
        boolean open = low != null && lowDownward >= TipAngle.FULL_LIMIT && mayOpen(low);
        if (open) {
            int held = countHeld();
            if (TipAngle.isSteepEnough(lowDownward, held, getCapacity())) {
                release(serverLevel, low, lowDownward, held);
            }
        }
        setOpen(serverLevel, open ? low : null,
            (float) Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, lowDownward)))));

        // A dump body with an open gate must not swallow what it lets go.
        if (!open) {
            catchFallingMaterial(serverLevel, sx, sy, sz);
        }
        return null;
    }

    @Override
    public boolean isBlockedBy(ServerLevel serverLevel, BlockPos pos) {
        return false;
    }

    /** The ways out: the four sides and the open top. */
    private static final Direction[] OUTLETS = {
        Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP};

    /** The top is always open; a side only if it was opened with the wrench. */
    private boolean mayOpen(Direction outlet) {
        return outlet == Direction.UP || DumpBodyBlock.mayOpen(getBlockState(), outlet);
    }

    /** Where a point of this block, in its own coordinates, is in the world. In the normal world the same point. */
    private Vec3 toWorld(Vec3 localPoint) {
        return host == null ? localPoint : host.toWorld(localPoint);
    }

    /** The lean is sent again once it has turned by this much (cosine of 3 degrees). */
    private static final double LEAN_SEND_COS = Math.cos(Math.toRadians(3.0));

    /** Way "down" in the body's own directions, for drawing the load (see {@link #leanX}). */
    public float[] getLean() {
        return new float[] {leanX, leanY, leanZ};
    }

    private void setLean(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 1.0E-6) {
            return;
        }
        x /= length;
        y /= length;
        z /= length;
        if (x * leanX + y * leanY + z * leanZ >= LEAN_SEND_COS) {
            return;
        }
        leanX = (float) x;
        leanY = (float) y;
        leanZ = (float) z;
        notifyUpdate();
    }

    /** Opens the gate on a side (null: all closed), with a latch sound, and tells the players. */
    private void setOpen(ServerLevel serverLevel, @Nullable Direction side, float angle) {
        boolean sideChanged = side != openSide;
        boolean angleChanged = side != null && Math.abs(angle - openAngle) >= ANGLE_SEND_STEP;
        if (!sideChanged && !angleChanged) {
            return;
        }
        if (sideChanged) {
            Vec3 where = toWorld(getBlockPos().getCenter());
            serverLevel.playSound(null, BlockPos.containing(where),
                side != null ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE,
                SoundSource.BLOCKS, 0.8F, 0.7F);
            releaseCooldown = 0;
        }
        openSide = side;
        openAngle = side == null ? 0.0F : angle;
        notifyUpdate();
    }

    // --------------------------------------------------------------- emptying

    /**
     * Lets the load go over the low side: every {@link #RELEASE_INTERVAL_TICKS}
     * ticks one block per lane. A lane is a row of columns running from the low
     * side to the opposite side. From each lane, the top block of the column
     * nearest to the low side that holds anything leaves, so the load drains from
     * the low end, top first. It appears one block outside the low side, level
     * with the floor, as falling material. Where that place is not free, the lane waits.
     */
    private void release(ServerLevel serverLevel, Direction low, double downward, int held) {
        if (releaseCooldown > 0) {
            releaseCooldown--;
            return;
        }
        releaseCooldown = RELEASE_INTERVAL_TICKS - 1;

        int sx = getSizeX();
        int sy = getSizeY();
        int sz = getSizeZ();
        if (low == Direction.UP) {
            releaseThroughTop(serverLevel, downward, held, sx, sy, sz);
            return;
        }
        boolean alongX = low.getAxis() == Direction.Axis.Z; // lanes run along z, the low side spans x
        int lanes = alongX ? sx : sz;
        int depths = alongX ? sz : sx;
        int capacity = getCapacity();
        for (int lane = 0; lane < lanes; lane++) {
            if (!TipAngle.isSteepEnough(downward, held, capacity)) {
                return;
            }
            for (int depth = 0; depth < depths; depth++) {
                int[] column = columnAt(low, lane, depth, sx, sz);
                DumpBodyBlockEntity top = topOfColumn(column[0], column[1], sy);
                if (top == null) {
                    continue;
                }
                // One block outside the low side, in this lane, level with the floor.
                int[] outside = columnAt(low, lane, -1, sx, sz);
                Vec3 local = Vec3.atLowerCornerOf(getBlockPos()).add(outside[0] + 0.5, 0.5, outside[1] + 0.5);
                Vec3 worldPlace = toWorld(local);
                BlockPos place = BlockPos.containing(worldPlace);
                // Where the place is taken (the heap has grown up to the side, or something is
                // in the way), this lane stops: drive on to tip out the rest somewhere else.
                if (isFree(serverLevel, place) && top.storedState != null) {
                    // The released block never slides back toward the body, so the heap
                    // grows away from it and not underneath it.
                    Vec3 towardBody = toWorld(Vec3.atLowerCornerOf(getBlockPos())
                        .add(getSizeX() / 2.0, 0.5, getSizeZ() / 2.0)).subtract(worldPlace);
                    Direction avoid = Direction.getNearest(towardBody.x, 0.0, towardBody.z);
                    FallingMaterialEntity.spawn(serverLevel, place, top.storedState, top.storedBlockEntityData, avoid);
                    top.clearContent();
                    held--;
                }
                break;
            }
        }
    }

    /**
     * Upside down: every column lets its block nearest to the open top go, out
     * over the top. Where the place above the column is not free, it waits.
     */
    private void releaseThroughTop(ServerLevel serverLevel, double downward, int held, int sx, int sy, int sz) {
        int capacity = getCapacity();
        for (int x = 0; x < sx; x++) {
            for (int z = 0; z < sz; z++) {
                if (!TipAngle.isSteepEnough(downward, held, capacity)) {
                    return;
                }
                DumpBodyBlockEntity top = topOfColumn(x, z, sy);
                if (top == null || top.storedState == null) {
                    continue;
                }
                Vec3 local = Vec3.atLowerCornerOf(getBlockPos()).add(x + 0.5, sy + 0.5, z + 0.5);
                BlockPos place = BlockPos.containing(toWorld(local));
                if (isFree(serverLevel, place)) {
                    FallingMaterialEntity.spawn(serverLevel, place, top.storedState, top.storedBlockEntityData);
                    top.clearContent();
                    held--;
                }
            }
        }
    }

    /**
     * The column (x and z, counted from the anchor) in a lane at a depth from the
     * low side: depth 0 lies at the low side, depth -1 just outside it.
     */
    private static int[] columnAt(Direction low, int lane, int depth, int sx, int sz) {
        return switch (low) {
            case NORTH -> new int[] {lane, depth};
            case SOUTH -> new int[] {lane, sz - 1 - depth};
            case WEST -> new int[] {depth, lane};
            default -> new int[] {sx - 1 - depth, lane};
        };
    }

    /** The highest cell of a column that holds anything, or null if the column is empty. */
    @Nullable
    private DumpBodyBlockEntity topOfColumn(int x, int z, int sy) {
        for (int y = sy - 1; y >= 0; y--) {
            DumpBodyBlockEntity cell = cell(x, y, z);
            if (cell != null && !cell.isEmpty()) {
                return cell;
            }
        }
        return null;
    }

    /** True if released material may appear here: loaded, in the normal world, and air or something replaceable. */
    private static boolean isFree(ServerLevel serverLevel, BlockPos place) {
        if (serverLevel.isOutsideBuildHeight(place) || !serverLevel.isLoaded(place)) {
            return false;
        }
        if (WorldDig.isOnSimulatedContraption(serverLevel, place)) {
            return false;
        }
        return serverLevel.getBlockState(place).canBeReplaced();
    }

    // --------------------------------------------------------------- catching

    /**
     * Takes in falling material that comes down over the dump body, as long as it
     * has room. The level is asked once for falling material in a box around the
     * dump body and the space above it; only if there is any, each piece is given
     * to the column it is over, and stored there by the heap rule ({@link #chooseColumn}).
     */
    private void catchFallingMaterial(ServerLevel serverLevel, int sx, int sy, int sz) {
        Vec3 corner = Vec3.atLowerCornerOf(getBlockPos());
        AABB zone = null;
        for (int i = 0; i < 8; i++) {
            Vec3 world = toWorld(corner.add((i & 1) * sx, ((i >> 1) & 1) * sy, ((i >> 2) & 1) * sz));
            zone = zone == null ? new AABB(world, world) : zone.minmax(new AABB(world, world));
        }
        AABB catchZone = zone.setMaxY(zone.maxY + CATCH_HEIGHT);
        List<FallingMaterialEntity> found = serverLevel.getEntitiesOfClass(
            FallingMaterialEntity.class,
            catchZone,
            entity -> entity.isAlive() && catchZone.contains(entity.getBoundingBox().getCenter())
        );
        if (found.isEmpty()) {
            return;
        }

        // Where each column's middle and floor are in the world, and how full each column is.
        Vec3[][] middles = new Vec3[sx][sz];
        double[][] floors = new double[sx][sz];
        int[][] heights = new int[sx][sz];
        for (int x = 0; x < sx; x++) {
            for (int z = 0; z < sz; z++) {
                middles[x][z] = toWorld(corner.add(x + 0.5, sy, z + 0.5));
                floors[x][z] = toWorld(corner.add(x + 0.5, 0.0, z + 0.5)).y;
                heights[x][z] = columnHeight(x, z, sy);
            }
        }

        boolean caught = false;
        for (FallingMaterialEntity entity : found) {
            BlockState carried = entity.getCarriedState();
            if (carried.isAir()) {
                continue;
            }
            Vec3 at = entity.getBoundingBox().getCenter();
            int[] over = null;
            double nearest = CATCH_REACH * CATCH_REACH;
            for (int x = 0; x < sx; x++) {
                for (int z = 0; z < sz; z++) {
                    if (at.y < floors[x][z] - 0.25) {
                        continue;
                    }
                    double dx = at.x - middles[x][z].x;
                    double dz = at.z - middles[x][z].z;
                    double distance = dx * dx + dz * dz;
                    if (distance <= nearest) {
                        nearest = distance;
                        over = new int[] {x, z};
                    }
                }
            }
            if (over == null) {
                continue;
            }
            int[] target = chooseColumn(over[0], over[1], heights, sy);
            if (target == null) {
                // Full: whatever else comes down lands as usual.
                return;
            }
            DumpBodyBlockEntity cell = cell(target[0], heights[target[0]][target[1]], target[1]);
            if (cell == null) {
                continue;
            }
            cell.setContent(carried, entity.getCarriedBlockData());
            heights[target[0]][target[1]]++;
            entity.discard();
            if (!caught) {
                caught = true;
                serverLevel.playSound(null, BlockPos.containing(at), carried.getSoundType().getPlaceSound(),
                    SoundSource.BLOCKS, 0.6F, 0.9F);
            }
        }
    }

    /** How many blocks a column holds (they always lie at the bottom). */
    private int columnHeight(int x, int z, int sy) {
        int height = 0;
        for (int y = 0; y < sy; y++) {
            DumpBodyBlockEntity cell = cell(x, y, z);
            if (cell == null || cell.isEmpty()) {
                break;
            }
            height++;
        }
        return height;
    }

    /**
     * The heap rule inside the body: a block that comes down over a column stays
     * in that column, unless the column is full or a column right beside it is at
     * least 2 lower; then it goes to the lowest column beside it with room. If
     * none of those has room, it goes to the nearest column with room.
     *
     * @return the column (x, z), or null if the dump body is full
     */
    @Nullable
    static int[] chooseColumn(int x, int z, int[][] heights, int sy) {
        int sx = heights.length;
        int sz = heights[0].length;
        int own = heights[x][z];
        int[] lowestBeside = null;
        boolean muchLowerBeside = false;
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] step : steps) {
            int nx = x + step[0];
            int nz = z + step[1];
            if (nx < 0 || nz < 0 || nx >= sx || nz >= sz) {
                continue;
            }
            int h = heights[nx][nz];
            if (h <= own - 2) {
                muchLowerBeside = true;
            }
            if (h < sy && (lowestBeside == null || h < heights[lowestBeside[0]][lowestBeside[1]])) {
                lowestBeside = new int[] {nx, nz};
            }
        }
        if (own < sy && !muchLowerBeside) {
            return new int[] {x, z};
        }
        if (lowestBeside != null) {
            return lowestBeside;
        }
        // Everything around is full: the nearest column that still has room.
        int[] best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int cx = 0; cx < sx; cx++) {
            for (int cz = 0; cz < sz; cz++) {
                if (heights[cx][cz] >= sy) {
                    continue;
                }
                int distance = Math.abs(cx - x) + Math.abs(cz - z);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = new int[] {cx, cz};
                }
            }
        }
        return best;
    }

    // --------------------------------------------------------------- breaking

    /** Empties this cell and drops what its content would drop if broken in the world with an unenchanted shovel tool. */
    public void dropContentAsBroken(ServerLevel serverLevel) {
        if (storedState == null) {
            return;
        }
        BlockPos pos = getBlockPos();
        BlockEntity storedBlockEntity = storedBlockEntityData == null ? null
            : BlockEntity.loadStatic(pos, storedState, storedBlockEntityData, serverLevel.registryAccess());
        List<ItemStack> drops = new ArrayList<>(
            Block.getDrops(storedState, serverLevel, pos, storedBlockEntity, null, new ItemStack(Items.IRON_SHOVEL)));
        // Containers such as chests spill their items when broken; shulker boxes keep them inside.
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
        for (ItemStack stack : drops) {
            Containers.dropItemStack(serverLevel, pos.getX(), pos.getY(), pos.getZ(), stack);
        }
    }

    // ------------------------------------------------- saving and sending

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (storedState != null) {
            tag.put(TAG_STORED_BLOCK, NbtUtils.writeBlockState(storedState));
            if (storedBlockEntityData != null) {
                tag.put(TAG_STORED_BLOCK_ENTITY, storedBlockEntityData.copy());
            }
        }
        tag.putInt(TAG_ANCHOR_X, anchorOffset.getX());
        tag.putInt(TAG_ANCHOR_Y, anchorOffset.getY());
        tag.putInt(TAG_ANCHOR_Z, anchorOffset.getZ());
        tag.putInt(TAG_SIZE_X, sizeX);
        tag.putInt(TAG_SIZE_Y, sizeY);
        tag.putInt(TAG_SIZE_Z, sizeZ);
        tag.putInt(TAG_OPEN_SIDE, openSide == null ? -1 : openSide.get3DDataValue());
        tag.putFloat(TAG_OPEN_ANGLE, openAngle);
        tag.putFloat(TAG_LEAN_X, leanX);
        tag.putFloat(TAG_LEAN_Y, leanY);
        tag.putFloat(TAG_LEAN_Z, leanZ);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        storedState = null;
        storedBlockEntityData = null;
        if (tag.contains(TAG_STORED_BLOCK, Tag.TAG_COMPOUND)) {
            BlockState state = NbtUtils.readBlockState(
                registries.lookupOrThrow(Registries.BLOCK), tag.getCompound(TAG_STORED_BLOCK));
            if (!state.isAir()) {
                storedState = state;
                storedBlockEntityData = tag.contains(TAG_STORED_BLOCK_ENTITY, Tag.TAG_COMPOUND)
                    ? tag.getCompound(TAG_STORED_BLOCK_ENTITY).copy() : null;
            }
        }
        anchorOffset = new BlockPos(tag.getInt(TAG_ANCHOR_X), tag.getInt(TAG_ANCHOR_Y), tag.getInt(TAG_ANCHOR_Z));
        sizeX = Math.max(1, tag.getInt(TAG_SIZE_X));
        sizeY = Math.max(1, tag.getInt(TAG_SIZE_Y));
        sizeZ = Math.max(1, tag.getInt(TAG_SIZE_Z));
        int side = tag.contains(TAG_OPEN_SIDE) ? tag.getInt(TAG_OPEN_SIDE) : -1;
        openSide = side < 0 || side > 5 ? null : Direction.from3DDataValue(side);
        openAngle = tag.getFloat(TAG_OPEN_ANGLE);
        if (tag.contains(TAG_LEAN_Y)) {
            leanX = tag.getFloat(TAG_LEAN_X);
            leanY = tag.getFloat(TAG_LEAN_Y);
            leanZ = tag.getFloat(TAG_LEAN_Z);
        } else {
            leanX = 0.0F;
            leanY = -1.0F;
            leanZ = 0.0F;
        }
        // Not saved: only ever in the data a Create contraption puts the block down with.
        if (tag.getBoolean(ContraptionTool.TAG_REGROUP_AFTER_MOVE)) {
            regroupAfterMove = true;
        }
    }
}
