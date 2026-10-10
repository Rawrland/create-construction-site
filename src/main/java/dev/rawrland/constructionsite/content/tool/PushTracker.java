package dev.rawrland.constructionsite.content.tool;

import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Tells how a tool on a contraption is moving, judged over a quarter of a second.
 *
 * A tool feeds it the world position of its working point once per tick. The
 * tracker compares the newest position with the one from 5 ticks ago. Looking
 * at several ticks, not one, makes small bumps and shaking cancel out.
 *
 * The working point should lie on the tool's working side, not in the block's
 * centre: a tool that turns on the spot moves its working side while its
 * centre stays where it is.
 *
 * Not saved with the world. After loading it needs 5 ticks before it reports movement again.
 */
public final class PushTracker {

    /** Movement is judged over this many ticks (a quarter of a second). Set by the spec. */
    public static final int WINDOW_TICKS = 5;

    /**
     * How far (in blocks) the working point must have moved over that time to count as moving at all.
     * Low enough for a slowly turning bearing.
     */
    public static final double MIN_DISTANCE = 0.015;

    /** Where the working point was over the last ticks. Newest first; null until known. */
    private final Vec3[] recentPoints = new Vec3[WINDOW_TICKS + 1];

    /** Where the working point went over the whole window. Null while it is not moving, or not yet known. */
    @Nullable
    private Vec3 movement;

    /** Length of {@link #movement}. */
    private double distance;

    /** Records the working point's position for this tick. Must be called every tick, so the record has no gaps. */
    public void update(Vec3 worldPoint) {
        // Shift the record by one tick and put the newest position in front.
        System.arraycopy(recentPoints, 0, recentPoints, 1, WINDOW_TICKS);
        recentPoints[0] = worldPoint;

        movement = null;
        distance = 0.0;
        Vec3 oldest = recentPoints[WINDOW_TICKS];
        if (oldest == null) {
            return;
        }
        // Back-and-forth shaking cancels out here.
        Vec3 moved = worldPoint.subtract(oldest);
        double length = moved.length();
        if (length < MIN_DISTANCE) {
            return;
        }
        movement = moved;
        distance = length;
    }

    /** True if the working point moved far enough over the window to count as moving. */
    public boolean isMoving() {
        return movement != null;
    }

    /**
     * How much of the movement points in the given direction: 1 means straight
     * that way, 0 sideways or not moving, -1 straight the other way.
     *
     * @param direction a direction in the world, of length 1
     */
    public double share(Vec3 direction) {
        if (movement == null) {
            return 0.0;
        }
        return movement.dot(direction) / distance;
    }
}
