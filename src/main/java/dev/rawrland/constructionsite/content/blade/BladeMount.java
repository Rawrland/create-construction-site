package dev.rawrland.constructionsite.content.blade;

/**
 * How a blade shows its mount: the beam, push arms and pin on its back.
 * Purely a matter of looks. Changed with Create's wrench.
 */
public enum BladeMount {
    /** One pair of arms in the middle of the blade, one block wide. Suits a vehicle narrower than the blade. */
    CENTRE,
    /** One arm near each end of the blade. Suits a vehicle as wide as the blade. */
    ENDS,
    /** No mount. */
    NONE;

    private static final BladeMount[] VALUES = values();

    public BladeMount next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public static BladeMount byIndex(int index) {
        return index >= 0 && index < VALUES.length ? VALUES[index] : CENTRE;
    }
}
