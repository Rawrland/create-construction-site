package dev.rawrland.constructionsite.content.dumpbody;

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
 * Decides which dump body blocks belong together.
 *
 * Touching dump body blocks are divided into boxes. A box is at most
 * {@link #MAX_HEIGHT} high; its two level sides are at most {@link #MAX_SHORT}
 * and {@link #MAX_LONG} long, whichever way round. A single block is a box of
 * 1 by 1 by 1 and is not "joined".
 *
 * As for buckets and blades, an existing box is never cut apart: it either stays
 * as it is or becomes part of a bigger box as a whole.
 *
 * With three free sizes there are far more possible boxes than for a bucket or a
 * blade, so boxes are grown instead of tried size by size over the whole cluster:
 * from the lowest free block (the box's corner with the lowest coordinates) the
 * box reaches as far as the cluster lets it along x, along z and upward, and the
 * biggest box within that reach that fits is taken. For a cluster that is a box,
 * that is the box itself. For odd clusters a smaller box than possible may come
 * out; accepted (design decision 2).
 *
 * Boxes line up with the world's axes (the dump body has no facing). Each block
 * stores where its anchor is (the corner with the lowest x, y and z) and the
 * box's three sizes.
 */
public final class DumpBodyGroup {

    /** The highest dump body that can be built. */
    public static final int MAX_HEIGHT = 4;
    /** The longer level side of a dump body is at most this long. */
    public static final int MAX_LONG = 15;
    /** The shorter level side of a dump body is at most this long. */
    public static final int MAX_SHORT = 7;

    /** Flood fill stops here: room for two of the largest dump bodies (420 blocks each) side by side. */
    private static final int SEARCH_LIMIT = 1024;

    /**
     * While true, placing a dump body block does not regroup (set while
     * {@link DumpBodyItem} places a whole layer, which is joined once at the end).
     * Only touched on the server thread.
     */
    static boolean suppressRegroup;

    private DumpBodyGroup() {
    }

    /** True for the sizes a dump body may have. */
    public static boolean isAllowedSize(int sizeX, int sizeY, int sizeZ) {
        if (sizeX < 1 || sizeY < 1 || sizeZ < 1 || sizeY > MAX_HEIGHT) {
            return false;
        }
        int longer = Math.max(sizeX, sizeZ);
        int shorter = Math.min(sizeX, sizeZ);
        return longer <= MAX_LONG && shorter <= MAX_SHORT;
    }

    /** A box-to-be: its anchor (lowest corner) and its sizes. */
    record Box(BlockPos anchor, int sizeX, int sizeY, int sizeZ) {
        int volume() {
            return sizeX * sizeY * sizeZ;
        }

        boolean isSingle() {
            return volume() == 1;
        }

        List<BlockPos> positions() {
            List<BlockPos> result = new ArrayList<>(volume());
            for (int x = 0; x < sizeX; x++) {
                for (int z = 0; z < sizeZ; z++) {
                    for (int y = 0; y < sizeY; y++) {
                        result.add(anchor.offset(x, y, z));
                    }
                }
            }
            return result;
        }
    }

    /**
     * Looks at all dump body blocks connected to the one at {@code start} and
     * divides them into boxes. Call on the server after a block was placed, or
     * for the neighbours of a block that was removed.
     */
    public static void regroup(Level level, BlockPos start) {
        if (level.isClientSide || suppressRegroup) {
            return;
        }
        BlockState startState = level.getBlockState(start);
        if (!(startState.getBlock() instanceof DumpBodyBlock)) {
            return;
        }

        // 1. Collect the connected dump body blocks.
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
                if (level.getBlockState(next).getBlock() == startState.getBlock()) {
                    cluster.add(next);
                    open.add(next);
                }
            }
        }

        // A fixed order (bottom to top, then north to south, then west to east), so the
        // result never depends on chance, and every box is grown from its lowest corner.
        List<BlockPos> ordered = new ArrayList<>(cluster);
        ordered.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY)
            .thenComparingInt(BlockPos::getZ)
            .thenComparingInt(BlockPos::getX));

        // 2. Find the boxes that exist right now and are still complete.
        Map<BlockPos, Box> existingAt = new HashMap<>();
        for (BlockPos pos : ordered) {
            if (existingAt.containsKey(pos) || !(level.getBlockEntity(pos) instanceof DumpBodyBlockEntity body)
                || !body.isJoined()) {
                continue;
            }
            Box box = new Box(pos.offset(body.getAnchorOffset()), body.getSizeX(), body.getSizeY(), body.getSizeZ());
            if (!isAllowedSize(box.sizeX(), box.sizeY(), box.sizeZ()) || box.isSingle()) {
                continue;
            }
            List<BlockPos> members = box.positions();
            boolean complete = true;
            for (BlockPos member : members) {
                if (!cluster.contains(member) || existingAt.containsKey(member)
                    || !(level.getBlockEntity(member) instanceof DumpBodyBlockEntity other)
                    || !other.isJoined()
                    || !member.offset(other.getAnchorOffset()).equals(box.anchor())
                    || other.getSizeX() != box.sizeX() || other.getSizeY() != box.sizeY()
                    || other.getSizeZ() != box.sizeZ()) {
                    complete = false;
                    break;
                }
            }
            if (complete) {
                for (BlockPos member : members) {
                    existingAt.put(member, box);
                }
            }
        }

        // 3. Grow boxes from the lowest free blocks.
        Map<BlockPos, Box> assigned = new HashMap<>();
        for (BlockPos anchor : ordered) {
            if (assigned.containsKey(anchor)) {
                continue;
            }
            Box box = grow(anchor, cluster, assigned, existingAt);
            for (BlockPos member : box.positions()) {
                assigned.put(member, box);
            }
        }

        // 4. Tell every block what it now belongs to. All blocks of a box take the same
        //    gate settings, so the box opens the same way all round: those of the biggest
        //    dump body that was there before and is now part of it, or else the anchor's.
        Map<Box, BlockState> gatesOf = new HashMap<>();
        for (Box box : new HashSet<>(assigned.values())) {
            Box biggestBefore = null;
            for (BlockPos member : box.positions()) {
                Box before = existingAt.get(member);
                if (before != null && (biggestBefore == null || before.volume() > biggestBefore.volume())) {
                    biggestBefore = before;
                }
            }
            gatesOf.put(box, level.getBlockState(biggestBefore != null ? biggestBefore.anchor() : box.anchor()));
        }
        for (BlockPos pos : ordered) {
            Box box = assigned.get(pos);
            boolean joined = box != null && !box.isSingle();
            BlockState state = level.getBlockState(pos);
            BlockState wanted = state.setValue(DumpBodyBlock.JOINED, joined);
            if (joined) {
                wanted = DumpBodyBlock.withGatesOf(wanted, gatesOf.get(box));
            }
            if (wanted != state) {
                // UPDATE_CLIENTS only: tell players, but do not notify neighbouring blocks.
                level.setBlock(pos, wanted, Block.UPDATE_CLIENTS);
            }
            if (level.getBlockEntity(pos) instanceof DumpBodyBlockEntity body) {
                if (joined) {
                    body.setGroup(box.anchor().subtract(pos), box.sizeX(), box.sizeY(), box.sizeZ());
                } else {
                    body.setGroup(BlockPos.ZERO, 1, 1, 1);
                }
            }
        }
    }

    /**
     * The biggest box with its lowest corner at {@code anchor} that fits: within
     * how far the free blocks reach from the anchor along x, z and up, the biggest
     * allowed size that fits (see {@link #fits}). At least the anchor block alone.
     */
    private static Box grow(BlockPos anchor, Set<BlockPos> cluster, Map<BlockPos, Box> assigned,
                            Map<BlockPos, Box> existingAt) {
        int reachX = reach(anchor, Direction.EAST, MAX_LONG, cluster, assigned);
        int reachZ = reach(anchor, Direction.SOUTH, MAX_LONG, cluster, assigned);
        int reachY = reach(anchor, Direction.UP, MAX_HEIGHT, cluster, assigned);
        Box best = new Box(anchor, 1, 1, 1);
        // Biggest volume first; of equal ones the lower one (more floor), then the longer along x.
        for (int volume = reachX * reachY * reachZ; volume > 1; volume--) {
            for (int sizeY = 1; sizeY <= reachY; sizeY++) {
                if (volume % sizeY != 0) {
                    continue;
                }
                int floor = volume / sizeY;
                for (int sizeX = Math.min(reachX, floor); sizeX >= 1; sizeX--) {
                    if (floor % sizeX != 0) {
                        continue;
                    }
                    int sizeZ = floor / sizeX;
                    if (sizeZ > reachZ || !isAllowedSize(sizeX, sizeY, sizeZ)) {
                        continue;
                    }
                    Box candidate = new Box(anchor, sizeX, sizeY, sizeZ);
                    if (fits(candidate, cluster, assigned, existingAt)) {
                        return candidate;
                    }
                }
            }
        }
        return best;
    }

    /** How many free blocks of the cluster lie in a row from {@code from} in one direction, counting itself, at most {@code max}. */
    private static int reach(BlockPos from, Direction direction, int max, Set<BlockPos> cluster,
                             Map<BlockPos, Box> assigned) {
        int count = 0;
        BlockPos pos = from;
        while (count < max && cluster.contains(pos) && !assigned.containsKey(pos)) {
            count++;
            pos = pos.relative(direction);
        }
        return Math.max(1, count);
    }

    /**
     * True if a new box may take exactly these blocks: all of them are dump body
     * blocks of this cluster, none is taken yet, and no existing box would be cut
     * apart (an existing box must lie completely inside or completely outside).
     */
    private static boolean fits(Box candidate, Set<BlockPos> cluster, Map<BlockPos, Box> assigned,
                                Map<BlockPos, Box> existingAt) {
        List<BlockPos> members = candidate.positions();
        Set<BlockPos> memberSet = new HashSet<>(members);
        Set<Box> touched = new HashSet<>();
        for (BlockPos member : members) {
            if (!cluster.contains(member) || assigned.containsKey(member)) {
                return false;
            }
            Box box = existingAt.get(member);
            if (box != null && touched.add(box)) {
                for (BlockPos other : box.positions()) {
                    if (!memberSet.contains(other)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
