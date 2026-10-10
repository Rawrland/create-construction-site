package dev.rawrland.constructionsite.content.blade;

import dev.rawrland.constructionsite.content.bucket.BucketGroup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides which blade blocks belong together.
 *
 * Blade blocks that touch and face the same way are divided into "joined"
 * blades, biggest first. A blade is always 1 block deep. It is 1 to 3 blocks
 * high and up to 9 blocks wide. A block that fits into no bigger blade stays
 * a single blade.
 *
 * The rules are the same as for buckets (see {@link BucketGroup}); only the
 * allowed sizes differ.
 */
public final class BladeGroup {

    /** The highest blade that can be built. */
    public static final int MAX_HEIGHT = 3;
    /** The widest blade that can be built. */
    public static final int MAX_WIDTH = 9;

    /** Flood fill stops here. Room for many of the largest blades (27 blocks each) side by side. */
    private static final int SEARCH_LIMIT = 512;

    private BladeGroup() {
    }

    /**
     * The blade's own directions: "back" points away from the working face,
     * "up" is up, "right" runs along the cutting edge. Blade and bucket use the
     * same facing property, so the bucket's helper works for both.
     */
    public static BucketGroup.Axes axes(BlockState state) {
        return BucketGroup.axes(state);
    }

    private static boolean matches(BlockState state, BlockState other) {
        return other.getBlock() == state.getBlock()
            && other.getValue(BulldozerBladeBlock.FACING) == state.getValue(BulldozerBladeBlock.FACING);
    }

    /** True for the sizes a joined blade may have. A single block (1 high, 1 wide) is not "joined". */
    public static boolean isAllowedSize(int height, int width) {
        if (height < 1 || height > MAX_HEIGHT || width < 1 || width > MAX_WIDTH) {
            return false;
        }
        return height * width >= 2;
    }

    /** A blade-to-be: its anchor (bottom left block), its height and its width. */
    private record Box(BlockPos anchor, int height, int width) {
        int volume() {
            return height * width;
        }
    }

    /** Every allowed size as {height, width}, ordered by number of blocks, biggest first; of equal ones the higher first. */
    private static final List<int[]> SIZES_BIGGEST_FIRST = new ArrayList<>();

    static {
        for (int height = 1; height <= MAX_HEIGHT; height++) {
            for (int width = 1; width <= MAX_WIDTH; width++) {
                if (isAllowedSize(height, width)) {
                    SIZES_BIGGEST_FIRST.add(new int[] {height, width});
                }
            }
        }
        SIZES_BIGGEST_FIRST.sort(Comparator.<int[]>comparingInt(size -> -size[0] * size[1])
            .thenComparingInt(size -> -size[0]));
    }

    private static List<BlockPos> positions(Box box, BucketGroup.Axes axes) {
        List<BlockPos> result = new ArrayList<>(box.volume());
        for (int r = 0; r < box.width(); r++) {
            for (int u = 0; u < box.height(); u++) {
                result.add(box.anchor().relative(axes.right(), r).relative(axes.up(), u));
            }
        }
        return result;
    }

    /**
     * Looks at all blade blocks connected to the one at {@code start} and divides
     * them into blades: the biggest allowed blade that fits is formed first, then
     * the next biggest from what is left, and so on.
     *
     * A blade that already exists is never cut apart by this. It either stays as
     * it is or becomes part of a bigger blade as a whole.
     *
     * Call on the server after a blade block was placed, or for the neighbours
     * of a blade block that was removed.
     */
    public static void regroup(Level level, BlockPos start) {
        if (level.isClientSide) {
            return;
        }
        BlockState startState = level.getBlockState(start);
        if (!(startState.getBlock() instanceof BulldozerBladeBlock)) {
            return;
        }
        BucketGroup.Axes axes = axes(startState);

        // 1. Collect the connected blade blocks that face the same way.
        Set<BlockPos> cluster = new HashSet<>();
        Deque<BlockPos> open = new ArrayDeque<>();
        cluster.add(start.immutable());
        open.add(start.immutable());
        while (!open.isEmpty() && cluster.size() < SEARCH_LIMIT) {
            BlockPos current = open.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (cluster.contains(next) || !level.isLoaded(next)) {
                    continue;
                }
                if (matches(startState, level.getBlockState(next))) {
                    cluster.add(next);
                    open.add(next);
                }
            }
        }

        // A fixed order (left to right, bottom to top, front to back), so the result never depends on chance.
        List<BlockPos> ordered = new ArrayList<>(cluster);
        ordered.sort(Comparator.<BlockPos>comparingInt(pos -> BucketGroup.along(pos, axes.right()))
            .thenComparingInt(pos -> BucketGroup.along(pos, axes.up()))
            .thenComparingInt(pos -> BucketGroup.along(pos, axes.back())));

        // 2. Find the blades that exist right now and are still complete.
        Map<BlockPos, Box> existingAt = new HashMap<>();
        List<Box> existing = new ArrayList<>();
        for (BlockPos pos : ordered) {
            if (existingAt.containsKey(pos) || !(level.getBlockEntity(pos) instanceof BulldozerBladeBlockEntity blade)
                || !blade.isJoined()) {
                continue;
            }
            Box box = new Box(pos.offset(blade.getAnchorOffset()), blade.getGroupHeight(), blade.getGroupWidth());
            if (!isAllowedSize(box.height(), box.width())) {
                continue;
            }
            List<BlockPos> members = positions(box, axes);
            boolean complete = true;
            for (BlockPos member : members) {
                if (!cluster.contains(member) || existingAt.containsKey(member)
                    || !(level.getBlockEntity(member) instanceof BulldozerBladeBlockEntity other)
                    || !other.isJoined()
                    || !member.offset(other.getAnchorOffset()).equals(box.anchor())
                    || other.getGroupHeight() != box.height() || other.getGroupWidth() != box.width()) {
                    complete = false;
                    break;
                }
            }
            if (complete) {
                existing.add(box);
                for (BlockPos member : members) {
                    existingAt.put(member, box);
                }
            }
        }

        // 3. Hand out the blocks, biggest blade first.
        Map<BlockPos, Box> assigned = new HashMap<>();
        for (int[] size : SIZES_BIGGEST_FIRST) {
            int height = size[0];
            int width = size[1];
            // Try the places of existing blades of this size first, so they stay where they are.
            List<BlockPos> anchors = new ArrayList<>();
            for (Box box : existing) {
                if (box.height() == height && box.width() == width) {
                    anchors.add(box.anchor());
                }
            }
            anchors.addAll(ordered);
            for (BlockPos anchor : anchors) {
                if (assigned.containsKey(anchor)) {
                    continue;
                }
                Box candidate = new Box(anchor, height, width);
                List<BlockPos> members = positions(candidate, axes);
                if (fits(members, cluster, assigned, existingAt, axes)) {
                    for (BlockPos member : members) {
                        assigned.put(member, candidate);
                    }
                }
            }
        }

        // 4. Tell every block what it now belongs to.
        for (BlockPos pos : ordered) {
            Box box = assigned.get(pos);
            boolean joined = box != null;
            BlockState state = level.getBlockState(pos);
            if (state.getValue(BulldozerBladeBlock.JOINED) != joined) {
                // UPDATE_CLIENTS only: tell players, but do not notify neighbouring blocks.
                level.setBlock(pos, state.setValue(BulldozerBladeBlock.JOINED, joined), Block.UPDATE_CLIENTS);
            }
            if (level.getBlockEntity(pos) instanceof BulldozerBladeBlockEntity blade) {
                if (joined) {
                    blade.setGroup(box.anchor().subtract(pos), box.height(), box.width());
                } else {
                    blade.setGroup(BlockPos.ZERO, 1, 1);
                }
            }
        }
    }

    /**
     * True if a new blade may take exactly these blocks: all of them are blade
     * blocks of this cluster, none is taken yet, and no existing blade would be
     * cut apart (an existing blade must lie completely inside or completely outside).
     */
    private static boolean fits(List<BlockPos> members, Set<BlockPos> cluster, Map<BlockPos, Box> assigned,
                                Map<BlockPos, Box> existingAt, BucketGroup.Axes axes) {
        Set<BlockPos> memberSet = new HashSet<>(members);
        for (BlockPos member : members) {
            if (!cluster.contains(member) || assigned.containsKey(member)) {
                return false;
            }
        }
        Set<Box> touched = new HashSet<>();
        for (BlockPos member : members) {
            Box box = existingAt.get(member);
            if (box != null && touched.add(box)) {
                for (BlockPos other : positions(box, axes)) {
                    if (!memberSet.contains(other)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** All block positions of the blade that the block at {@code self} belongs to: column by column, bottom first. */
    public static List<BlockPos> members(BlockPos self, BlockState state, BlockPos anchorOffset, int height, int width) {
        List<BlockPos> result = new ArrayList<>(height * width);
        if (!state.getValue(BulldozerBladeBlock.JOINED)) {
            result.add(self);
            return result;
        }
        BucketGroup.Axes axes = axes(state);
        BlockPos anchor = self.offset(anchorOffset);
        for (int r = 0; r < width; r++) {
            for (int u = 0; u < height; u++) {
                result.add(anchor.relative(axes.right(), r).relative(axes.up(), u));
            }
        }
        return result;
    }
}
