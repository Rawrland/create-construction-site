package dev.rawrland.constructionsite.content.decoration;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.core.Vec3i;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A thin warning plate that sticks to one face of any block, like a decal: on the floor,
 * a wall or the ceiling. FACING is the way the plate looks; the block it sticks to is behind it.
 * On the floor it can be walked on like a carpet; on walls and ceilings nothing bumps into it.
 * The wrench flips the stripes.
 *
 * The plate keeps a 2-pixel margin to the edges of the block's face, so a single plate is a
 * panel on the face, not the whole face. Next to a plate of the same kind, looking the same way
 * with its stripes the same way, the margin on that side closes and the two join into one
 * panel (owner, 27 October 2026). NORTH, EAST, SOUTH and WEST say which sides are joined, in
 * the plate's own model before it is turned onto its face (the model lies on the floor).
 */
public class HazardPlateBlock extends Block implements IWrenchable {

    public static final MapCodec<HazardPlateBlock> CODEC = simpleCodec(HazardPlateBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        SHAPES.put(Direction.UP, box(0, 0, 0, 16, 1, 16));
        SHAPES.put(Direction.DOWN, box(0, 15, 0, 16, 16, 16));
        SHAPES.put(Direction.NORTH, box(0, 0, 15, 16, 16, 16));
        SHAPES.put(Direction.SOUTH, box(0, 0, 0, 16, 16, 1));
        SHAPES.put(Direction.WEST, box(15, 0, 0, 16, 16, 16));
        SHAPES.put(Direction.EAST, box(0, 0, 0, 1, 16, 16));
    }

    public HazardPlateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(HazardStripesBlock.FLIPPED, false)
            .setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HazardStripesBlock.FLIPPED, NORTH, EAST, SOUTH, WEST);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // Only a plate on the floor is stood on; on walls and ceilings it is too thin to bump into.
        return state.getValue(FACING) == Direction.UP ? SHAPES.get(Direction.UP) : Shapes.empty();
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getClickedFace());
        return joined(state, context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        // The plate sticks to any block, full or not (stairs, slabs, Create's parts), and stays
        // when that block is gone (owner, 27 October 2026). It only works out its joins anew.
        return joined(state, level, pos);
    }

    /** The state with its four joined sides worked out from the plates around it. */
    private BlockState joined(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        return state
            .setValue(NORTH, joins(state, level, pos, toWorld(facing, Direction.NORTH)))
            .setValue(EAST, joins(state, level, pos, toWorld(facing, Direction.EAST)))
            .setValue(SOUTH, joins(state, level, pos, toWorld(facing, Direction.SOUTH)))
            .setValue(WEST, joins(state, level, pos, toWorld(facing, Direction.WEST)));
    }

    /** True if the block that way is the same plate, looking the same way, with its stripes the same way. */
    private boolean joins(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        BlockState other = level.getBlockState(pos.relative(side));
        return other.is(this) && other.getValue(FACING) == state.getValue(FACING)
            && other.getValue(HazardStripesBlock.FLIPPED) == state.getValue(HazardStripesBlock.FLIPPED);
    }

    /**
     * Where a side of the plate's model points in the world once the model is turned onto its face.
     * The turns are the block state file's (x first, then y), done the way Minecraft does them.
     */
    static Direction toWorld(Direction facing, Direction local) {
        int x;
        int y;
        switch (facing) {
            case DOWN -> { x = 180; y = 0; }
            case NORTH -> { x = 90; y = 0; }
            case EAST -> { x = 90; y = 90; }
            case SOUTH -> { x = 90; y = 180; }
            case WEST -> { x = 90; y = 270; }
            default -> { x = 0; y = 0; }
        }
        Vec3i v = local.getNormal();
        // Turn about x by -x degrees, then about y by -y degrees.
        int cx = cos(-x), sx = sin(-x);
        int vx = v.getX();
        int vy = v.getY() * cx - v.getZ() * sx;
        int vz = v.getY() * sx + v.getZ() * cx;
        int cy = cos(-y), sy = sin(-y);
        int wx = vx * cy + vz * sy;
        int wz = -vx * sy + vz * cy;
        return Direction.fromDelta(wx, vy, wz);
    }

    private static int cos(int degrees) {
        return switch (Math.floorMod(degrees, 360)) {
            case 0 -> 1;
            case 180 -> -1;
            default -> 0;
        };
    }

    private static int sin(int degrees) {
        return switch (Math.floorMod(degrees, 360)) {
            case 90 -> 1;
            case 270 -> -1;
            default -> 0;
        };
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        // Flip, then join anew: a plate only joins neighbours whose stripes run the same way.
        if (!context.getLevel().isClientSide) {
            BlockPos pos = context.getClickedPos();
            context.getLevel().setBlock(pos, joined(state.cycle(HazardStripesBlock.FLIPPED), context.getLevel(), pos), 3);
            IWrenchable.playRotateSound(context.getLevel(), pos);
        }
        return InteractionResult.SUCCESS;
    }
}
