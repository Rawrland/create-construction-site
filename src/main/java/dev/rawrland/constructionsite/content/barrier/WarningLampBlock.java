package dev.rawrland.constructionsite.content.barrier;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A Warning Flasher or Warning Beacon on its own (owner, 29 October 2026): a small blinking amber
 * lamp for vehicles, walls and ceilings. It sits on the floor, on a wall or under a ceiling, like a
 * button, and gives light. Used on a traffic cone the same item clips onto the cone instead
 * (TrafficConeBlock), so this block is only what is left when it is placed on anything else.
 *
 * The flasher's lens faces the player who placed it (on a wall: away from the wall); the beacon is
 * round. Like the hazard plates it stays where it was put, even when the block it sits on goes.
 */
public class WarningLampBlock extends FaceAttachedHorizontalDirectionalBlock {

    /** As bright as a lamp on a cone. */
    public static final int LIGHT = TrafficConeBlock.LAMP_LIGHT;

    private final ConeLamp lamp;
    private final MapCodec<WarningLampBlock> codec;
    /** Shapes by face (floor, wall, ceiling) and facing (2D data value). */
    private final VoxelShape[][] shapes = new VoxelShape[3][4];

    public WarningLampBlock(ConeLamp lamp, Properties properties) {
        super(properties);
        this.lamp = lamp;
        this.codec = simpleCodec(p -> new WarningLampBlock(lamp, p));
        registerDefaultState(stateDefinition.any().setValue(FACE, AttachFace.FLOOR).setValue(FACING, Direction.NORTH));
        boolean flasher = lamp == ConeLamp.FLASHER;
        // Each shape as for facing north; turned for the others. A wall lamp facing north sits on
        // the wall to its south (z = 16) and sticks out to the north.
        double[] floor = flasher ? new double[] {5.5, 0, 6.75, 10.5, 6, 9.25} : new double[] {6, 0, 6, 10, 5, 10};
        double[] ceiling = flasher ? new double[] {5.5, 10, 6.75, 10.5, 16, 9.25} : new double[] {6, 11, 6, 10, 16, 10};
        double[] wall = flasher ? new double[] {5.5, 2.5, 12.5, 10.5, 7.5, 16} : new double[] {6, 6, 11, 10, 10, 16};
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            int i = facing.get2DDataValue();
            shapes[0][i] = turned(floor, facing);
            shapes[1][i] = turned(wall, facing);
            shapes[2][i] = turned(ceiling, facing);
        }
    }

    public ConeLamp lamp() {
        return lamp;
    }

    @Override
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACE, FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int face = switch (state.getValue(FACE)) {
            case FLOOR -> 0;
            case WALL -> 1;
            case CEILING -> 2;
        };
        return shapes[face][state.getValue(FACING).get2DDataValue()];
    }

    /** Stays put on anything, also on vehicles where the block behind it may be a part of a contraption. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return true;
    }

    /** A box given for facing north, turned about the block's middle to face the given way. */
    private static VoxelShape turned(double[] b, Direction facing) {
        double x0 = b[0], y0 = b[1], z0 = b[2], x1 = b[3], y1 = b[4], z1 = b[5];
        return switch (facing) {
            case EAST -> box(16 - z1, y0, x0, 16 - z0, y1, x1);
            case SOUTH -> box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case WEST -> box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            default -> box(x0, y0, z0, x1, y1, z1);
        };
    }
}
