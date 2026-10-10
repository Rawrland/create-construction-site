package dev.rawrland.constructionsite.content.material;

import dev.rawrland.constructionsite.Config;
import dev.rawrland.constructionsite.content.tool.WorldDig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Brings down the material that stood on a block a tool has just dug away.
 *
 * Plain Minecraft leaves dirt hanging in the air when the block under it is
 * removed. A tool of this mod calls {@link #collapseAbove} after it has dug a
 * block, and the diggable blocks straight above then fall as
 * {@link FallingMaterialEntity}. Nothing else in the world calls this, so dirt
 * that no tool digs under stays where it is.
 */
public final class DigCollapse {

    private DigCollapse() {
    }

    /**
     * Lets the unbroken column of diggable blocks straight above the given
     * place fall. Call it after the block at that place has been removed.
     *
     * The column ends at the first block that is not diggable (air included),
     * and at the height limit from the config. Does nothing if the collapse is
     * switched off in the config.
     */
    public static void collapseAbove(ServerLevel level, BlockPos dug) {
        // Read each time, so a changed config applies without restarting the world.
        if (!Config.COLLAPSE_ENABLED.get()) {
            return;
        }
        int maxHeight = Config.COLLAPSE_MAX_HEIGHT.get();

        // Bottom to top, all in this tick, so the column falls as one piece.
        for (int step = 1; step <= maxHeight; step++) {
            BlockPos pos = dug.above(step);
            // Taken without particles or sound: a whole column of break sounds would be noise.
            WorldDig.DugBlock block = WorldDig.take(level, pos, false);
            if (block == null) {
                // Not diggable (air included), part of a contraption, or outside the world: the column ends here.
                return;
            }
            FallingMaterialEntity.spawn(level, pos, block.state(), block.blockEntityData());
        }
    }
}
