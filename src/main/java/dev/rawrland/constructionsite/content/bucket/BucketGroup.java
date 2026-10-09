package dev.rawrland.constructionsite.content.bucket;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
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
 * Decides which bucket blocks belong together.
 *
 * Bucket blocks that touch and face the same way are divided into "joined"
 * buckets of the allowed sizes, biggest first. Sizes are high x deep x wide:
 * 1x1x2 and 1x1x3, 2x2x1 up to 2x2x5, and 3x3x1 up to 3x3x7.
 * Blocks that fit into no allowed size stay single buckets.
 */
public final class BucketGroup {

    /** Flood fill stops here. Room for several of the largest buckets (63 blocks each) side by side. */
    private static final int SEARCH_LIMIT = 512;

    private BucketGroup() {
    }

    /**
     * The bucket's own directions. "back" points away from the open side,
     * "up" is up (the teeth are at the bottom), "right" runs along the row of teeth.
     */
    public record Axes(Direction right, Direction up, Direction back) {
    }

    public static Axes axes(BlockState state) {
        Direction back = state.getValue(ExcavatorBucketBlock.FACING).getOpposite();
        Direction up = Direction.UP;
        return new Axes(cross(up, back), up, back);
    }

    /** The direction at right angles to both, following the right-hand rule. */
    private static Direction cross(Direction a, Direction b) {
        Vec3i c = a.getNormal().cross(b.getNormal());
        Direction result = Direction.fromDelta(c.getX(), c.getY(), c.getZ());
        return result == null ? Direction.EAST : result;
    }

    /** How far a position lies along a direction, in blocks. */
    public static int along(BlockPos pos, Direction direction) {
        Vec3i n = direction.getNormal();
        return pos.getX() * n.getX() + pos.getY() * n.getY() + pos.getZ() * n.getZ();
    }

    private static boolean matches(BlockState state, BlockState other) {
        return other.getBlock() == state.getBlock()
            && other.getValue(ExcavatorBucketBlock.FACING) == state.getValue(ExcavatorBucketBlock.FACING);
    }

    public static boolean isAllowedSize(int height, int depth, int width) {
        if (height == 1 && depth == 1) {
            return width >= 2 && width <= 3;
        }
        if (height == 2 && depth == 2) {
            return width >= 1 && width <= 5;
        }
        if (height == 3 && depth == 3) {
            return width >= 1 && width <= 7;
        }
        return false;
    }

    /** A bucket-to-be: its anchor (bottom front left block), its height (and depth) and its width. */
    private record Box(BlockPos anchor, int height, int width) {
        int volume() {
            return height * height * width;
        }
    }

    /** Every allowed size as {height, width}, ordered by number of blocks, biggest first. */
    private static final int[][] SIZES_BIGGEST_FIRST = {
        {3, 7}, {3, 6}, {3, 5}, {3, 4}, {3, 3}, {2, 5}, {3, 2}, {2, 4}, {2, 3}, {3, 1}, {2, 2}, {2, 1}, {1, 3}, {1, 2}
    };

    private static List<BlockPos> positions(Box box, Axes axes) {
        List<BlockPos> result = new ArrayList<>(box.volume());
        for (int r = 0; r < box.width(); r++) {
            for (int u = 0; u < box.height(); u++) {
                for (int b = 0; b < box.height(); b++) {
                    result.add(box.anchor().relative(axes.right(), r).relative(axes.up(), u).relative(axes.back(), b));
                }
            }
        }
        return result;
    }

    /**
     * Looks at all bucket blocks connected to the one at {@code start} and divides
     * them into buckets, the way Create's vaults and tanks divide up:
     * the biggest allowed bucket that fits is formed first, then the next biggest
     * from what is left, and so on. Blocks that fit nowhere stay single buckets.
     *
     * A bucket that already exists is never cut apart by this. It either stays as
     * it is or becomes part of a bigger bucket as a whole.
     *
     * Call on the server after a bucket block was placed, or for the neighbours
     * of a bucket block that was removed.
     */
    public static void regroup(Level level, BlockPos start) {
        if (level.isClientSide) {
            return;
        }
        BlockState startState = level.getBlockState(start);
        if (!(startState.getBlock() instanceof ExcavatorBucketBlock)) {
            return;
        }
        Axes axes = axes(startState);

        // 1. Collect the connected bucket blocks that face the same way.
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
        ordered.sort(Comparator.<BlockPos>comparingInt(pos -> along(pos, axes.right()))
            .thenComparingInt(pos -> along(pos, axes.up()))
            .thenComparingInt(pos -> along(pos, axes.back())));

        // 2. Find the buckets that exist right now and are still complete.
        Map<BlockPos, Box> existingAt = new HashMap<>();
        List<Box> existing = new ArrayList<>();
        for (BlockPos pos : ordered) {
            if (existingAt.containsKey(pos) || !(level.getBlockEntity(pos) instanceof ExcavatorBucketBlockEntity bucket)
                || !bucket.isJoined()) {
                continue;
            }
            Box box = new Box(pos.offset(bucket.getAnchorOffset()), bucket.getGroupHeight(), bucket.getGroupWidth());
            if (!isAllowedSize(box.height(), box.height(), box.width())) {
                continue;
            }
            List<BlockPos> members = positions(box, axes);
            boolean complete = true;
            for (BlockPos member : members) {
                if (!cluster.contains(member) || existingAt.containsKey(member)
                    || !(level.getBlockEntity(member) instanceof ExcavatorBucketBlockEntity other)
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

        // 3. Hand out the blocks, biggest bucket first.
        Map<BlockPos, Box> assigned = new HashMap<>();
        for (int[] size : SIZES_BIGGEST_FIRST) {
            int height = size[0];
            int width = size[1];
            // Try the places of existing buckets of this size first, so they stay where they are.
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
            if (state.getValue(ExcavatorBucketBlock.JOINED) != joined) {
                // UPDATE_CLIENTS only: tell players, but do not notify neighbouring blocks.
                level.setBlock(pos, state.setValue(ExcavatorBucketBlock.JOINED, joined), Block.UPDATE_CLIENTS);
            }
            if (level.getBlockEntity(pos) instanceof ExcavatorBucketBlockEntity bucket) {
                if (joined) {
                    bucket.setGroup(box.anchor().subtract(pos), box.height(), box.width());
                } else {
                    bucket.setGroup(BlockPos.ZERO, 1, 1);
                }
            }
        }
    }

    /**
     * True if a new bucket may take exactly these blocks: all of them are bucket
     * blocks of this cluster, none is taken yet, and no existing bucket would be
     * cut apart (an existing bucket must lie completely inside or completely outside).
     */
    private static boolean fits(List<BlockPos> members, Set<BlockPos> cluster, Map<BlockPos, Box> assigned,
                                Map<BlockPos, Box> existingAt, Axes axes) {
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

    /** All block positions of the bucket that the block at {@code self} belongs to. */
    public static List<BlockPos> members(BlockPos self, BlockState state, BlockPos anchorOffset, int height, int width) {
        List<BlockPos> result = new ArrayList<>(height * height * width);
        if (!state.getValue(ExcavatorBucketBlock.JOINED)) {
            result.add(self);
            return result;
        }
        Axes axes = axes(state);
        BlockPos anchor = self.offset(anchorOffset);
        for (int r = 0; r < width; r++) {
            for (int u = 0; u < height; u++) {
                for (int b = 0; b < height; b++) {
                    result.add(anchor.relative(axes.right(), r).relative(axes.up(), u).relative(axes.back(), b));
                }
            }
        }
        return result;
    }

    /**
     * The block positions of one slice of a bucket: all blocks at the same place
     * along the width as the block at {@code self}. A single bucket is its own slice.
     */
    public static List<BlockPos> slice(BlockPos self, BlockState state, BlockPos anchorOffset, int height) {
        List<BlockPos> result = new ArrayList<>(height * height);
        if (!state.getValue(ExcavatorBucketBlock.JOINED)) {
            result.add(self);
            return result;
        }
        Axes axes = axes(state);
        BlockPos anchor = self.offset(anchorOffset);
        int column = along(self.subtract(anchor), axes.right());
        for (int u = 0; u < height; u++) {
            for (int b = 0; b < height; b++) {
                result.add(anchor.relative(axes.right(), column).relative(axes.up(), u).relative(axes.back(), b));
            }
        }
        return result;
    }

    /** True if the block is in the bottom row of its bucket, the one with the teeth. */
    public static boolean isBottomRow(BlockState state, BlockPos anchorOffset) {
        if (!state.getValue(ExcavatorBucketBlock.JOINED)) {
            return true;
        }
        // anchorOffset points from this block to the anchor, which is in the bottom row.
        return along(anchorOffset, axes(state).up()) == 0;
    }

    /** True if the block at {@code self} is in the front layer, the one with the open side. */
    public static boolean isFrontLayer(BlockState state, BlockPos anchorOffset) {
        if (!state.getValue(ExcavatorBucketBlock.JOINED)) {
            return true;
        }
        // anchorOffset points from this block to the anchor, which is in the front layer.
        return along(anchorOffset, axes(state).back()) == 0;
    }
}
