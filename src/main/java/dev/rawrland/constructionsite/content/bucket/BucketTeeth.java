package dev.rawrland.constructionsite.content.bucket;

/**
 * Where the teeth of a bucket sit, and where its rope ring is.
 *
 * Used by the renderer to draw them and by the block entity to say where a
 * rope ends, so the two can never disagree.
 *
 * All numbers are in pixels (1/16 of a block) in the bucket's own directions:
 * x along the row of teeth from the bucket's left edge, y upward from its
 * underside, z backward from its open face (so teeth have negative z).
 */
public final class BucketTeeth {

    /** How many teeth each block of width has, for 1-high, 2-high and 3-high buckets. */
    private static final int[] TEETH_PER_BLOCK = {5, 3, 2};

    /** Height of the middle of the rope ring above the bucket's underside, for a 1-high bucket. */
    private static final float RING_Y = 1.3F;
    /** How far the middle of the rope ring lies in front of the open face, for a 1-high bucket. */
    private static final float RING_Z = -3.15F;

    private BucketTeeth() {
    }

    public static int perBlock(int height) {
        return TEETH_PER_BLOCK[Math.max(0, Math.min(2, height - 1))];
    }

    /** Width of one tooth. */
    public static float toothWidth(int height) {
        return 2.0F * height;
    }

    /** The gap between two teeth. Half a gap is left at each edge of a block, so all gaps are equal. */
    public static float gap(int height) {
        int teeth = perBlock(height);
        return (16.0F - teeth * toothWidth(height)) / teeth;
    }

    /** Left edge of a tooth: the block's place along the width (0 = leftmost) and the tooth's number in that block. */
    public static float toothLeft(int column, int tooth, int height) {
        return column * 16.0F + gap(height) * 0.5F + tooth * (toothWidth(height) + gap(height));
    }

    /** Middle of a tooth along the width. */
    public static float toothMiddle(int column, int tooth, int height) {
        return toothLeft(column, tooth, height) + toothWidth(height) * 0.5F;
    }

    /**
     * The middle tooth of one block. If the block has an even number of teeth
     * there are two middle ones; then the one nearer the middle of the whole bucket.
     */
    public static int middleTooth(int column, int width, int height) {
        float blockMiddle = column * 16.0F + 8.0F;
        float bucketMiddle = width * 8.0F;
        int best = 0;
        float bestDistance = Float.MAX_VALUE;
        float bestToBucketMiddle = Float.MAX_VALUE;
        for (int tooth = 0; tooth < perBlock(height); tooth++) {
            float x = toothMiddle(column, tooth, height);
            float distance = Math.abs(x - blockMiddle);
            float toBucketMiddle = Math.abs(x - bucketMiddle);
            boolean nearer = distance < bestDistance - 0.001F;
            boolean equallyNear = Math.abs(distance - bestDistance) <= 0.001F;
            if (nearer || (equallyNear && toBucketMiddle < bestToBucketMiddle - 0.001F)) {
                best = tooth;
                bestDistance = distance;
                bestToBucketMiddle = toBucketMiddle;
            }
        }
        return best;
    }

    /** Where along the width the rope ring of a block sits: on its middle tooth. */
    public static float ringX(int column, int width, int height) {
        return toothMiddle(column, middleTooth(column, width, height), height);
    }

    public static float ringY(int height) {
        return RING_Y * height;
    }

    public static float ringZ(int height) {
        return RING_Z * height;
    }
}
