package dev.rawrland.constructionsite.content.tool;

import dev.rawrland.constructionsite.compat.Mods;
import dev.rawrland.constructionsite.compat.aeronautics.SableWorld;
import dev.rawrland.constructionsite.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Takes a block out of the normal world for a tool of this mod.
 *
 * Every tool (bucket, blade) and the collapse use the same two steps: check
 * whether a block may be taken, and take it together with its data. They live
 * here so that all of them follow exactly the same rules.
 */
public final class WorldDig {

    /** A block that was taken out of the world: what it was, and its extra data (null if it had none). */
    public record DugBlock(BlockState state, @Nullable CompoundTag blockEntityData) {
    }

    private WorldDig() {
    }

    /**
     * True if a tool may take the block at this place: the place is loaded, it
     * belongs to the normal world and not to a contraption, and the block is in
     * the diggable tag and not unbreakable.
     */
    public static boolean canTake(ServerLevel level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos) || !level.isLoaded(pos)) {
            return false;
        }
        // Only the normal world, never blocks that belong to a contraption.
        if (isOnSimulatedContraption(level, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.is(ModTags.BUCKET_DIGGABLE)) {
            return false;
        }
        // Negative destroy speed means unbreakable (bedrock and the like), whatever the tag says.
        return state.getDestroySpeed(level, pos) >= 0;
    }

    /**
     * True if this place belongs to a Create Aeronautics simulated contraption, not
     * to the normal world. Always false without Create Aeronautics, which is the
     * only thing that makes such contraptions.
     */
    public static boolean isOnSimulatedContraption(ServerLevel level, BlockPos pos) {
        return Mods.AERONAUTICS && SableWorld.isOnSubLevel(level, pos);
    }

    /**
     * True if the block at this place would stop a tool that cannot take it:
     * something solid. Air, water, grass and other blocks a tool simply moves
     * through do not count, and neither do blocks without collision such as torches.
     */
    public static boolean isObstacle(ServerLevel level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos) || !level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && !state.canBeReplaced() && !state.getCollisionShape(level, pos).isEmpty();
    }

    /**
     * Takes the block at this place out of the world, without dropping an item.
     *
     * @param withEffects true to show break particles and play the break sound, false to remove it silently
     * @return the block and its data, or null if it may not be taken or could not be removed
     */
    @Nullable
    public static DugBlock take(ServerLevel level, BlockPos pos, boolean withEffects) {
        if (!canTake(level, pos)) {
            return null;
        }
        BlockState state = level.getBlockState(pos);

        // Keep the block's extra data, then empty it so nothing spills when it is removed.
        CompoundTag blockEntityData = null;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            blockEntityData = blockEntity.saveWithId(level.registryAccess());
            Clearable.tryClear(blockEntity);
        }

        // The "false" means no item drops in both cases.
        boolean removed = withEffects ? level.destroyBlock(pos, false) : level.removeBlock(pos, false);
        return removed ? new DugBlock(state, blockEntityData) : null;
    }
}
