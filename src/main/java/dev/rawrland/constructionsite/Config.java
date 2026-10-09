package dev.rawrland.constructionsite;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config options of the mod. There are none yet; the empty spec is kept
 * registered so that later features can add options here.
 */
public final class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
