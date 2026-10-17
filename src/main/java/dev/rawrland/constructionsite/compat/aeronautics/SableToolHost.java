package dev.rawrland.constructionsite.compat.aeronautics;

import dev.rawrland.constructionsite.content.tool.ToolHost;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * A tool on a simulated contraption of Create Aeronautics (a Sable "sub-level").
 *
 * The blocks of such a contraption are real blocks with real block entities,
 * stored far away from where the player sees them. Sable converts ("projects")
 * their positions to the world.
 *
 * @param level the server level the contraption is in
 */
public record SableToolHost(ServerLevel level) implements ToolHost {

    @Override
    public Vec3 toWorld(Vec3 localPoint) {
        return Sable.HELPER.projectOutOfSubLevel(level, localPoint);
    }

    @Nullable
    @Override
    public BlockEntity blockEntityAt(BlockPos localPos) {
        return level.isLoaded(localPos) ? level.getBlockEntity(localPos) : null;
    }
}
