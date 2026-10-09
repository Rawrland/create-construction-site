package dev.rawrland.constructionsite.content.bucket;

/**
 * Where a bucket shows its mounting ears and pin. Purely a matter of looks:
 * on top suits an excavator arm, on the back suits a wheel loader's arms.
 * Changed with Create's wrench.
 */
public enum BucketMount {
    TOP,
    BACK,
    NONE;

    private static final BucketMount[] VALUES = values();

    public BucketMount next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public static BucketMount byIndex(int index) {
        return index >= 0 && index < VALUES.length ? VALUES[index] : TOP;
    }
}
