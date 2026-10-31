package dev.rawrland.constructionsite.client;

/**
 * The side profile of a bucket, used to draw buckets in code.
 *
 * All numbers are in pixels (1/16 block) for a 1-high bucket; a bucket 2 or 3
 * blocks high multiplies them by its height. "z" runs from the open face toward
 * the back, "y" from the underside up.
 *
 * Every edge is level or upright, like Create's own blocks:
 *
 * <pre>
 *   roof  ______________________________
 *        |                              |  back
 *        |  opening                     |
 *        |                         _____|  second step
 *        |                    ____|        first step
 *        |___________________|
 *        floor
 * </pre>
 *
 * The whole bucket stays inside its blocks at the front: the opening lies on
 * the front face of the blocks, and only the teeth stick out past it.
 */
final class BucketShape {

    /** Where the opening is: the front face of the blocks. Only the teeth reach past it. */
    static final float OPENING_Z = 0.0F;
    /** Where the floor ends and the first step goes up. */
    static final float STEP_1_Z = 11.0F;
    /** Height of the first step's underside. Whole pixels, so the frame lines land on the pixel grid (owner, 29 October 2026). */
    static final float STEP_1_Y = 3.0F;
    /** Where the first step ends and the second goes up. Whole pixels, like STEP_1_Y. */
    static final float STEP_2_Z = 14.0F;
    /** Height at which the straight back starts. The back mount sits above this. */
    static final float BACK_START_Y = 5.0F;
    /** Where the back's outer face is. */
    static final float BACK_Z = 16.0F;
    /** Height of the roof's top. */
    static final float TOP_Y = 16.0F;

    private BucketShape() {
    }

    /** Wall thickness in pixels for a bucket of the given height in blocks: 2, 3 or 4. */
    static float thickness(float height) {
        return height + 1.0F;
    }

    /**
     * How far back the inside of the bucket reaches at a given height, both in
     * pixels of a bucket of the given height. Below the first step the inside
     * ends at the first step, and so on.
     */
    static float innerBackAt(float y, float height) {
        float s = height;
        float t = thickness(height);
        if (y < STEP_1_Y * s + t) {
            return STEP_1_Z * s - t;
        }
        if (y < BACK_START_Y * s + t) {
            return STEP_2_Z * s - t;
        }
        return BACK_Z * s - t;
    }
}
