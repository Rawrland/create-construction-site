package dev.rawrland.constructionsite.content.barrier;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A barrier board (German "Absperrschranke"): a red-white board on white stands with black
 * rubber feet. Boards in a row, looking the same way, join into one long barrier with one
 * stand at every joint instead of two. LEFT and RIGHT say which ends are joined, seen from the
 * front (the side the board faces).
 */
public class BarrierBoardBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<BarrierBoardBlock> CODEC = simpleCodec(BarrierBoardBlock::new);
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");

    /** Outline shapes by state, built the first time they are asked for. */
    private static final java.util.Map<BlockState, VoxelShape> OUTLINES = new java.util.concurrent.ConcurrentHashMap<>();
    /** Collision: as high as a fence along the whole board, so nobody steps or jumps over it (owner, 29 October 2026). */
    private static final VoxelShape COLLISION_ACROSS_X = box(0, 0, 6, 16, 24, 10);
    private static final VoxelShape COLLISION_ACROSS_Z = box(6, 0, 0, 10, 24, 16);

    public BarrierBoardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
            .setValue(LEFT, false).setValue(RIGHT, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LEFT, RIGHT);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        return joined(state, context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return joined(state, level, pos);
    }

    /** The outline follows the model: the board, and a stand with its foot at each free end and at a joint. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINES.computeIfAbsent(state, BarrierBoardBlock::outline);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? COLLISION_ACROSS_X : COLLISION_ACROSS_Z;
    }

    private static VoxelShape outline(BlockState state) {
        // In the model's own frame (front to the north): left is +x. The joint stand reaches into
        // the neighbour in the model; the outline stops at the block's edge.
        Direction facing = state.getValue(FACING);
        VoxelShape shape = turned(facing, 0, 9, 7, 16, 14, 9);
        if (state.getValue(LEFT)) {
            shape = Shapes.or(shape, turned(facing, 15, 1, 7, 16, 9, 9), turned(facing, 14, 0, 4, 16, 1, 12));
        } else {
            shape = Shapes.or(shape, turned(facing, 13, 1, 7, 15, 9, 9), turned(facing, 12, 0, 4, 16, 1, 12));
        }
        if (!state.getValue(RIGHT)) {
            shape = Shapes.or(shape, turned(facing, 1, 1, 7, 3, 9, 9), turned(facing, 0, 0, 4, 4, 1, 12));
        }
        return shape;
    }

    /** A box given for the model facing north, turned to the block's facing (as the block state file turns the model). */
    private static VoxelShape turned(Direction facing, double x0, double y0, double z0, double x1, double y1, double z1) {
        return switch (facing) {
            case EAST -> box(16 - z1, y0, x0, 16 - z0, y1, x1);
            case SOUTH -> box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case WEST -> box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            default -> box(x0, y0, z0, x1, y1, z1);
        };
    }

    /** Seen from the front, the player's left: the facing turned clockwise (facing north, the player looks south and their left is east). */
    static Direction left(Direction facing) {
        return facing.getClockWise();
    }

    private BlockState joined(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        return state.setValue(LEFT, joins(level, pos.relative(left(facing)), facing))
            .setValue(RIGHT, joins(level, pos.relative(left(facing).getOpposite()), facing));
    }

    private boolean joins(BlockGetter level, BlockPos pos, Direction facing) {
        BlockState other = level.getBlockState(pos);
        return other.is(this) && other.getValue(FACING) == facing;
    }
}
