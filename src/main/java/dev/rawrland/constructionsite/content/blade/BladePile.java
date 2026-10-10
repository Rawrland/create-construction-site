package dev.rawrland.constructionsite.content.blade;

/**
 * The size and shape of the pile a blade pushes in front of itself.
 *
 * A blade can only push what fits in front of it, so the pile grows with the
 * blade's height: each blade block carries as many blocks as the blade is
 * high. Per column (per block of the blade's width) that is 1 block for a
 * blade 1 high, 4 for a blade 2 high and 9 for a blade 3 high.
 *
 * When the pile is put down as blocks it forms stacks in front of the blade:
 * the first as high as the blade, the following ones lower. A place in the
 * pile is given as "depth" (0 = right in front of the blade, 1 = one block
 * further away, ...) and "level" (0 = on the cutting edge's level, 1 = one
 * block higher, ...). The stack next to the blade fills first, bottom to top,
 * then the next stack, and so on.
 */
public final class BladePile {

    /**
     * How high the stacks of a column's pile are, from the blade outward, for a
     * blade 1, 2 and 3 high. Each row adds up to the column's capacity.
     */
    private static final int[][] STACKS = {
        {1},
        {2, 1, 1},
        {3, 3, 2, 1},
    };

    private BladePile() {
    }

    private static int[] stacks(int bladeHeight) {
        return STACKS[Math.max(1, Math.min(STACKS.length, bladeHeight)) - 1];
    }

    /** How many blocks one column of a blade of this height can carry: 1, 4 or 9. */
    public static int capacityPerColumn(int bladeHeight) {
        int total = 0;
        for (int stack : stacks(bladeHeight)) {
            total += stack;
        }
        return total;
    }

    /** How many blocks one blade block stores: as many as the blade is high. */
    public static int capacityPerBlock(int bladeHeight) {
        int height = Math.max(1, bladeHeight);
        // Rounded up, so that the blocks of a column together always hold the whole pile.
        return (capacityPerColumn(bladeHeight) + height - 1) / height;
    }

    /** How many stacks deep the pile of a blade of this height is. */
    public static int depth(int bladeHeight) {
        return stacks(bladeHeight).length;
    }

    /**
     * Where the block with the given number sits in its column's pile.
     *
     * @param index 0 for the first block the column took, 1 for the next, ...
     * @return {depth, level}; numbers beyond the pile's size give the last place
     */
    public static int[] slot(int bladeHeight, int index) {
        int[] stacks = stacks(bladeHeight);
        int remaining = index;
        for (int depth = 0; depth < stacks.length; depth++) {
            if (remaining < stacks[depth]) {
                return new int[] {depth, remaining};
            }
            remaining -= stacks[depth];
        }
        return new int[] {stacks.length - 1, 0};
    }
}
