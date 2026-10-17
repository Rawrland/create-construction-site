package dev.rawrland.constructionsite.compat.aeronautics;

import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Small questions to Sable, the physics behind Create Aeronautics' simulated
 * contraptions. Only called when Create Aeronautics is installed
 * (see {@link dev.rawrland.constructionsite.compat.Mods#AERONAUTICS}).
 */
public final class SableWorld {

    private SableWorld() {
    }

    /** True if this place belongs to a simulated contraption ("sub-level"), not to the normal world. */
    public static boolean isOnSubLevel(ServerLevel level, BlockPos pos) {
        return Sable.HELPER.getContaining(level, pos) != null;
    }
}
