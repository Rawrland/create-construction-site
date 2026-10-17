package dev.rawrland.constructionsite.content.tool;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * The contraption a tool (bucket, blade) is working on, as the tool sees it.
 *
 * A tool's block entity does its work in its own block coordinates. These are
 * not where the player sees the tool: a contraption moves and turns its blocks.
 * The host answers the two questions the work needs:
 * <ul>
 *     <li>where a point of the tool is in the world right now, and</li>
 *     <li>which block entity holds another block of the same joined tool.</li>
 * </ul>
 *
 * There are two hosts, one per kind of contraption:
 * {@code compat.aeronautics.SableToolHost} for Create Aeronautics' simulated contraptions, and
 * {@link ContraptionToolHost} for Create's own (bearings, pistons, gantries,
 * minecarts, trains).
 */
public interface ToolHost {

    /**
     * Where a point of the tool is in the world.
     *
     * @param localPoint a point in the tool's own block coordinates (the coordinates of its block entity)
     */
    Vec3 toWorld(Vec3 localPoint);

    /**
     * The block entity at a position in the tool's own block coordinates, or
     * null if there is none (the block is not on the contraption, or not loaded).
     */
    @Nullable
    BlockEntity blockEntityAt(BlockPos localPos);
}
