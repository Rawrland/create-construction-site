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

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
