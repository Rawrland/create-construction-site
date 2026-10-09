package dev.rawrland.constructionsite.client;

import java.util.ArrayList;
import java.util.List;

/**
 * The side profile of a bucket, used to draw buckets in code.
 *
 * All numbers are in pixels (1/16 block). "z" runs from the open face toward the
 * back, "y" from the floor up. The profile is a line that starts at the lip, runs
 * along the floor, up a slanted corner and the straight back, and forward along the
 * roof. The opening is the straight line from the end of the roof back down to the lip.
 */
final class BucketShape {

    /**
     * Corner points of a 1-high bucket: lip, end of the floor, top of the slanted
     * corner, top of the back, front of the roof. Straight edges between them.
     */
    private static final float[][] BASE = {
        {-1.5F, 0.0F}, {11.0F, 0.0F}, {16.0F, 5.0F}, {16.0F, 16.0F}, {5.13F, 16.0F}
    };

    /** How often the corners are rounded off. 0 keeps the edges straight; each pass would double the points. */
    private static final int SMOOTHING_PASSES = 0;

    /** Height at which the straight back starts, for a 1-high bucket. The back mount sits above this. */
    static final float BACK_START_Y = 5.0F;
    /** Where the back's outer face is, for a 1-high bucket. */
    static final float BACK_Z = 16.0F;

    private static final BucketShape[] CACHE = new BucketShape[4];

    /** The bucket's height in blocks: 1, 2 or 3. Everything in the profile is multiplied by it. */
    final float scale;
    /** Wall thickness in pixels. */
    final float thickness;
    /** Outer line of the shell. */
    final float[] outerZ, outerY;
    /** Inner line of the shell, one wall thickness inside the outer one. Same number of points. */
    final float[] innerZ, innerY;

    static BucketShape of(int height) {
        int index = Math.max(1, Math.min(3, height));
        if (CACHE[index] == null) {
            CACHE[index] = new BucketShape(index);
        }
        return CACHE[index];
    }

    private BucketShape(int height) {
        scale = height;
        thickness = height;

        List<float[]> points = new ArrayList<>();
        for (float[] p : BASE) {
            points.add(new float[] {p[0] * scale, p[1] * scale});
        }
        for (int pass = 0; pass < SMOOTHING_PASSES; pass++) {
            points = roundCorners(points);
        }

        int n = points.size();
        outerZ = new float[n];
        outerY = new float[n];
        innerZ = new float[n];
        innerY = new float[n];
        for (int i = 0; i < n; i++) {
            outerZ[i] = points.get(i)[0];
            outerY[i] = points.get(i)[1];
        }

        // The inner line: move every point inward, at right angles to the line.
        for (int i = 0; i < n; i++) {
            float[] before = i > 0 ? inwardNormal(i - 1) : inwardNormal(0);
            float[] after = i < n - 1 ? inwardNormal(i) : inwardNormal(n - 2);
            float nz = before[0] + after[0];
            float ny = before[1] + after[1];
            float length = (float) Math.sqrt(nz * nz + ny * ny);
            nz /= length;
            ny /= length;
            // At a bend the point has to move a little further to keep the wall evenly thick.
            float cos = nz * after[0] + ny * after[1];
            float distance = thickness / Math.max(0.5F, cos);
            innerZ[i] = outerZ[i] + nz * distance;
            innerY[i] = outerY[i] + ny * distance;
        }

        // The shell is cut off along the opening, which leans back. So the two ends
        // of the inner line must lie on that leaning line, not straight above the lip
        // and straight below the roof's front edge. Otherwise the floor would stick
        // out in front of the side walls a little and show an open side there.
        int last = n - 1;
        float lean = (outerZ[last] - outerZ[0]) / (outerY[last] - outerY[0]);
        innerY[0] = outerY[0] + thickness;
        innerZ[0] = outerZ[0] + lean * thickness;
        innerY[last] = outerY[last] - thickness;
        innerZ[last] = outerZ[0] + lean * (innerY[last] - outerY[0]);
    }

    /** Cuts every corner once (Chaikin's method). The first and last point stay where they are. */
    private static List<float[]> roundCorners(List<float[]> in) {
        List<float[]> out = new ArrayList<>();
        int last = in.size() - 1;
        out.add(in.get(0));
        for (int i = 0; i < last; i++) {
            float[] a = in.get(i);
            float[] b = in.get(i + 1);
            if (i > 0) {
                out.add(new float[] {0.75F * a[0] + 0.25F * b[0], 0.75F * a[1] + 0.25F * b[1]});
            }
            if (i < last - 1) {
                out.add(new float[] {0.25F * a[0] + 0.75F * b[0], 0.25F * a[1] + 0.75F * b[1]});
            }
        }
        out.add(in.get(last));
        return out;
    }

    int pointCount() {
        return outerZ.length;
    }

    /** Direction pointing into the bucket, at right angles to the outer line segment that starts at point i. */
    float[] inwardNormal(int i) {
        float dz = outerZ[i + 1] - outerZ[i];
        float dy = outerY[i + 1] - outerY[i];
        float length = (float) Math.sqrt(dz * dz + dy * dy);
        return new float[] {-dy / length, dz / length};
    }

    /** Direction pointing out of the opening, at right angles to it: forward and slightly up. */
    float[] openingNormal() {
        int last = pointCount() - 1;
        float dz = outerZ[last] - outerZ[0];
        float dy = outerY[last] - outerY[0];
        float length = (float) Math.sqrt(dz * dz + dy * dy);
        return new float[] {-dy / length, dz / length};
    }

    /**
     * Where the content surface ends for a given fill level between 0 and 1.
     * The surface starts at the inner lip and tilts up as the bucket fills,
     * until at 1 it reaches the front edge of the roof.
     *
     * @return z and y of the far end of the surface
     */
    float[] contentSurfaceEnd(float fill) {
        int last = pointCount() - 1;
        float startZ = innerZ[0];
        float startY = innerY[0];
        if (fill >= 0.999F) {
            return new float[] {innerZ[last], innerY[last]};
        }
        double fullAngle = Math.atan2(innerY[last] - startY, innerZ[last] - startZ);
        double angle = fullAngle * Math.max(0.0F, fill);
        float dirZ = (float) Math.cos(angle);
        float dirY = (float) Math.sin(angle);

        // Follow that direction until it meets the inner line of the shell.
        float best = Float.MAX_VALUE;
        for (int i = 1; i < last; i++) {
            float az = innerZ[i], ay = innerY[i];
            float ez = innerZ[i + 1] - az, ey = innerY[i + 1] - ay;
            float denominator = dirZ * ey - dirY * ez;
            if (Math.abs(denominator) < 1.0E-5F) {
                continue;
            }
            float t = ((az - startZ) * ey - (ay - startY) * ez) / denominator;
            float s = ((az - startZ) * dirY - (ay - startY) * dirZ) / denominator;
            if (t > 0.01F && s >= -0.001F && s <= 1.001F && t < best) {
                best = t;
            }
        }
        if (best == Float.MAX_VALUE) {
            return new float[] {innerZ[last], innerY[last]};
        }
        return new float[] {startZ + dirZ * best, startY + dirY * best};
    }
}
