package dev.rawrland.constructionsite.content.tool;

/**
 * How steeply a load has to be tipped before it comes out. Shared by the
 * excavator bucket and the dump body, so both follow the same rule (set by the
 * spec): a full one starts to spill when it points 20 degrees below level, and
 * the needed angle grows evenly as it empties, up to 45 degrees for the last block.
 *
 * "Downward" is how strongly a direction points down: 0 when level, 1 when
 * straight down (the sine of its angle below level).
 */
public final class TipAngle {

    /** A full load starts to come out at this many degrees below level. */
    public static final double FULL = 20.0;
    /** The last block only comes out at this many degrees below level. */
    public static final double LAST = 45.0;
    /** The downward value at which a full load starts to come out. */
    public static final double FULL_LIMIT = Math.sin(Math.toRadians(FULL));

    private TipAngle() {
    }

    /**
     * True if something holding this many blocks out of its capacity lets one
     * go when it is tipped this far.
     */
    public static boolean isSteepEnough(double downward, int held, int capacity) {
        if (held <= 0 || capacity <= 0) {
            return false;
        }
        double emptyShare = 1.0 - (double) held / capacity;
        double neededAngle = FULL + (LAST - FULL) * emptyShare;
        return downward >= Math.sin(Math.toRadians(neededAngle));
    }
}
