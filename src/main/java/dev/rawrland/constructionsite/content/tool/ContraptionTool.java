package dev.rawrland.constructionsite.content.tool;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;

/**
 * A tool block entity that can also work on Create's own contraptions
 * (bearings, pistons, gantries, minecarts, trains). Both the bucket and the blade are one.
 *
 * On such a contraption there is no real block entity. {@link ToolMovementBehaviour}
 * makes a stand-in copy from the block's saved data, gives it a {@link ToolHost}
 * and lets it do its usual work every tick. See {@link ContraptionToolShadow}.
 */
public interface ContraptionTool {

    /**
     * Put into a tool block's data when a Create contraption stops, just before
     * it puts its blocks down. The block entity then checks joining again on its
     * first tick, because the contraption may have turned the tool. Never saved.
     */
    String TAG_REGROUP_AFTER_MOVE = "RegroupAfterMove";

    /** Sets where the tool is; null for a block in the normal world or in the player's game. */
    void setHost(@Nullable ToolHost host);

    /**
     * Does one tick of the tool's work, exactly as on a simulated contraption.
     * The host must be set.
     *
     * @return a world position in the tool's way that it may not or cannot take
     *         while it is moving into material, or null if nothing blocks it
     */
    @Nullable
    BlockPos work(ServerLevel level);

    /**
     * True while the block at this world position still stops the tool: it is
     * still there and the tool still may not take it, or still has no room for it.
     */
    boolean isBlockedBy(ServerLevel level, BlockPos pos);
}
