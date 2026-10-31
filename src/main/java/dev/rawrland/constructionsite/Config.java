package dev.rawrland.constructionsite;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config options of the mod. They are stored in the common config file
 * (config/create_construction_site-common.toml) and can be changed on the
 * mod's config screen.
 *
 * The names shown on that screen come from the language file: the entry
 * "create_construction_site.configuration.<option name>", and the same with
 * ".tooltip" at the end for the explanation.
 */
public final class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** Whether material above a dug block comes down. */
    public static final ModConfigSpec.BooleanValue COLLAPSE_ENABLED = BUILDER
        .comment("When a tool of this mod digs a block, the diggable blocks standing directly on top of it fall down.")
        .define("collapseEnabled", true);

    /** How many blocks above one dug block may come down at most. */
    public static final ModConfigSpec.IntValue COLLAPSE_MAX_HEIGHT = BUILDER
        .comment("How many blocks above one dug block fall down at most.")
        .defineInRange("collapseMaxHeight", 16, 1, 64);

    // --- driven hydraulic cylinders (add-cylinder-settings) ---

    /**
     * How quickly a driven cylinder may change its speed, in blocks per second per second.
     * The cylinder sets its parts' speed directly, so this is the same for light and heavy parts.
     */
    public static final ModConfigSpec.DoubleValue CYLINDER_MAX_ACCELERATION = BUILDER
        .comment("How quickly a driven hydraulic cylinder may speed up or slow down (blocks per second, per second). "
            + "Higher: snappier starts and stops; lower: smoother, but heavy arms may sag a little more.")
        .defineInRange("cylinderMaxAcceleration", 400.0, 1.0, 5000.0);

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
