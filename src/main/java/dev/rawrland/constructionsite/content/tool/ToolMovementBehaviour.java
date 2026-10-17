package dev.rawrland.constructionsite.content.tool;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.gantry.GantryContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/**
 * Lets the bucket and the blade work on Create's own contraptions: bearings,
 * pistons, gantries, minecarts and trains.
 *
 * Create calls this for every block of a moving contraption that has one, in
 * both the server and the players' games. The work only runs on the server: it
 * gets the block's stand-in copy ({@link ContraptionToolShadow}), lets it work,
 * and writes its data back.
 *
 * Blocks in the way: on a bearing, piston, minecart or train the machine keeps
 * moving; a tool that meets a block it may not take, or has no room for, simply
 * does nothing with it and works again as soon as it can. Only a gantry stops:
 * there the tool stops the whole contraption until the block is gone or the tool
 * has room. Create clears a contraption's stall every tick and sets it again from
 * its blocks, so the tool sets it again every tick for as long as the block still
 * stops it. (Owner's choice after testing, 12 October 2026.)
 *
 * Registered for both tools in the mod's common setup.
 */
public class ToolMovementBehaviour implements MovementBehaviour {

    /** Where the block that stopped the tool is, in the movement context's saved data. */
    private static final String TAG_BLOCKED_AT = "BlockedAt";

    @Override
    public void tick(MovementContext context) {
        if (!(context.world instanceof ServerLevel level)) {
            return;
        }
        ContraptionToolShadow shadow = ContraptionToolShadow.of(context);
        if (shadow == null) {
            return;
        }
        ContraptionTool tool = shadow.tool();

        BlockPos blocking = tool.work(level);
        if (blocking != null) {
            context.data.put(TAG_BLOCKED_AT, NbtUtils.writeBlockPos(blocking));
        }
        updateStall(context, level, tool);

        shadow.writeBack(context, true);
    }

    /** On a gantry: keeps it stopped while the block that stopped this tool is still in its way. */
    private static void updateStall(MovementContext context, ServerLevel level, ContraptionTool tool) {
        if (!(context.contraption.entity instanceof GantryContraptionEntity)) {
            // Every other machine moves on; the tool just leaves the block alone.
            context.data.remove(TAG_BLOCKED_AT);
            context.stall = false;
            return;
        }
        Optional<BlockPos> blockedAt = NbtUtils.readBlockPos(context.data, TAG_BLOCKED_AT);
        if (blockedAt.isPresent() && tool.isBlockedBy(level, blockedAt.get())) {
            context.stall = true;
            return;
        }
        context.data.remove(TAG_BLOCKED_AT);
        context.stall = false;
    }

    /** Called when the contraption is saved: the block's saved data must be up to date. */
    @Override
    public void writeExtraData(MovementContext context) {
        if (context.world instanceof ServerLevel && context.temporaryData instanceof ContraptionToolShadow shadow) {
            shadow.writeBack(context, false);
        }
    }

    /**
     * Called when the contraption stops, just before it puts its blocks down.
     * The block's data gets its final state, and a note that the tool should
     * check its joining again once it is back in the world.
     */
    @Override
    public void stopMoving(MovementContext context) {
        if (!(context.world instanceof ServerLevel)) {
            return;
        }
        if (context.temporaryData instanceof ContraptionToolShadow shadow) {
            shadow.writeBack(context, true);
        }
        context.temporaryData = null;
        context.data.remove(TAG_BLOCKED_AT);
        if (context.blockEntityData != null) {
            context.blockEntityData.putBoolean(ContraptionTool.TAG_REGROUP_AFTER_MOVE, true);
        }
    }
}
