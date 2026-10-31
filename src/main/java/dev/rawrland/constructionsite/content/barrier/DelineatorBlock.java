package dev.rawrland.constructionsite.content.barrier;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import dev.rawrland.constructionsite.content.decoration.HazardStripesBlock;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A delineator post (German "Leitbake"): a narrow red-white striped board on a black rubber
 * foot. Its striped face looks toward the player who places it. The wrench flips which way the
 * stripes run, as real ones come for the left and the right side of a road.
 */
public class DelineatorBlock extends HorizontalDirectionalBlock implements IWrenchable {

    public static final MapCodec<DelineatorBlock> CODEC = simpleCodec(DelineatorBlock::new);

    // Board across x (seen from north or south), and across z (from east or west).
    private static final VoxelShape ACROSS_X = Shapes.or(box(3, 0, 5, 13, 2, 11), box(5, 2, 7, 11, 16, 9));
    private static final VoxelShape ACROSS_Z = Shapes.or(box(5, 0, 3, 11, 2, 13), box(7, 2, 5, 9, 16, 11));

    public DelineatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
            .setValue(HazardStripesBlock.FLIPPED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HazardStripesBlock.FLIPPED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? ACROSS_X : ACROSS_Z;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        if (!context.getLevel().isClientSide) {
            context.getLevel().setBlock(context.getClickedPos(), state.cycle(HazardStripesBlock.FLIPPED), 3);
            IWrenchable.playRotateSound(context.getLevel(), context.getClickedPos());
        }
        return InteractionResult.SUCCESS;
    }
}
