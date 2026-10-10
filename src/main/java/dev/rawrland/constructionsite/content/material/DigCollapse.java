package dev.rawrland.constructionsite.content.material;

import dev.rawrland.constructionsite.Config;
import dev.rawrland.constructionsite.registry.ModTags;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

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
            if (level.isOutsideBuildHeight(pos) || !level.isLoaded(pos)) {
                return;
            }
            // Only the normal world collapses, never blocks that belong to a contraption.
            if (Sable.HELPER.getContaining(level, pos) != null) {
                return;
            }

            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.is(ModTags.BUCKET_DIGGABLE)) {
                return;
            }
            // Negative destroy speed means unbreakable, whatever the tag says.
            if (state.getDestroySpeed(level, pos) < 0) {
                return;
            }

            // Keep the block's extra data, then empty it so nothing spills when it is removed.
            CompoundTag blockEntityData = null;
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity != null) {
                blockEntityData = blockEntity.saveWithId(level.registryAccess());
                Clearable.tryClear(blockEntity);
            }

            // Removed without drops, particles or sound: a whole column of break sounds would be noise.
            if (!level.removeBlock(pos, false)) {
                return;
            }
            FallingMaterialEntity.spawn(level, pos, state, blockEntityData);
        }
    }
}
