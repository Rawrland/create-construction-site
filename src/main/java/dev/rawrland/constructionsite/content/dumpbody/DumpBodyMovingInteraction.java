package dev.rawrland.constructionsite.content.dumpbody;

import com.simibubi.create.AllItems;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import dev.rawrland.constructionsite.content.tool.ContraptionToolShadow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.MutablePair;

import java.util.List;

/**
 * Lets a player use the wrench on a dump body while it is part of one of Create's
 * own contraptions (bearing, piston, gantry, minecart, train): the clicked side of
 * the whole dump body switches between "always closed" and "may open", as in the world.
 *
 * On such a contraption the blocks are not in the world, so the block's own wrench
 * code never runs; Create asks a moving interaction instead (registered in the mod's
 * common setup; read in Create 6.0.10, {@code SimpleBlockMovingInteraction}). Create
 * only says which block was clicked, so the side is found by following the player's
 * view into the contraption's own grid. Create Aeronautics' vehicles keep their blocks
 * in the world, where the block's own wrench code works.
 *
 * Create picks it up when a contraption is assembled or loaded (read in {@code Contraption}).
 */
public class DumpBodyMovingInteraction extends MovingInteractionBehaviour {

    /** How far the player's view is followed, in blocks. */
    private static final double REACH = 8.0;

    @Override
    public boolean handlePlayerInteraction(Player player, InteractionHand activeHand, BlockPos localPos,
                                           AbstractContraptionEntity contraptionEntity) {
        if (!AllItems.WRENCH.isIn(player.getItemInHand(activeHand))) {
            return false;
        }
        Contraption contraption = contraptionEntity.getContraption();
        StructureBlockInfo clicked = contraption.getBlocks().get(localPos);
        if (clicked == null || !(clicked.state().getBlock() instanceof DumpBodyBlock)) {
            return false;
        }
        // Which face of the block the player looks at, in the contraption's own grid.
        Vec3 eye = player.getEyePosition();
        Vec3 localFrom = contraptionEntity.toLocalVector(eye, 1.0F);
        Vec3 localTo = contraptionEntity.toLocalVector(eye.add(player.getViewVector(1.0F).scale(REACH)), 1.0F);
        BlockHitResult hit = AABB.clip(List.of(new AABB(0, 0, 0, 1, 1, 1)), localFrom, localTo, localPos);
        if (hit == null || !hit.getDirection().getAxis().isHorizontal()) {
            return true;
        }
        if (player.level().isClientSide) {
            return true;
        }
        Direction side = hit.getDirection();
        boolean nowOpen = !DumpBodyBlock.mayOpen(clicked.state(), side);

        // Every block of that dump body, from the clicked block's saved data.
        CompoundTag data = clicked.nbt();
        BlockPos anchor = localPos;
        int sx = 1, sy = 1, sz = 1;
        if (data != null && clicked.state().getValue(DumpBodyBlock.JOINED)) {
            anchor = localPos.offset(data.getInt("AnchorX"), data.getInt("AnchorY"), data.getInt("AnchorZ"));
            sx = Math.max(1, data.getInt("SizeX"));
            sy = Math.max(1, data.getInt("SizeY"));
            sz = Math.max(1, data.getInt("SizeZ"));
        }
        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    BlockPos pos = anchor.offset(x, y, z);
                    StructureBlockInfo info = contraption.getBlocks().get(pos);
                    if (info == null || !(info.state().getBlock() instanceof DumpBodyBlock)) {
                        continue;
                    }
                    BlockState state = info.state().setValue(DumpBodyBlock.gate(side), nowOpen);
                    setContraptionBlockData(contraptionEntity, pos, new StructureBlockInfo(info.pos(), state, info.nbt()));
                    // The tool's stand-in copy reads its sides from its own block state.
                    MutablePair<StructureBlockInfo, MovementContext> actor = contraption.getActorAt(pos);
                    if (actor != null && actor.right != null) {
                        actor.right.state = state;
                        if (actor.right.temporaryData instanceof ContraptionToolShadow shadow) {
                            shadow.blockEntity().setBlockState(state);
                        }
                    }
                }
            }
        }
        player.level().playSound(null, player.blockPosition(),
            nowOpen ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.6F, 1.4F);
        return true;
    }
}
