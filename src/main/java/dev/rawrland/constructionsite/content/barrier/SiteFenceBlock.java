package dev.rawrland.constructionsite.content.barrier;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.WoolCarpetBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A site fence (German "Bauzaun"), two blocks tall like a door (owner's look "B", 29 October
 * 2026): mesh panels in a thick steel frame on concrete feet. It connects like iron bars to the
 * same fence next to it and to solid faces, so runs, corners and crossings work.
 *
 * HALF: the lower or upper block of one fence. POST: a straight run shows a post (with its feet
 * and the clamps that join two panels) on every other block, on even x or even z; every end,
 * corner, junction and lone fence has one. BOTTOM: no fence below (only for the lower half): the
 * fence stands on the ground and gets its concrete feet. Fences stack: a fence on a fence goes on
 * upward without feet. TARP: a privacy tarp, hung by using a carpet on the fence, on the side the
 * player stands on (TARP_SOUTH, TARP_EAST); shears take it off again, and it comes back as the carpet.
 */
public class SiteFenceBlock extends CrossCollisionBlock {

    public static final MapCodec<SiteFenceBlock> CODEC = simpleCodec(SiteFenceBlock::new);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty POST = BooleanProperty.create("post");
    public static final BooleanProperty BOTTOM = BooleanProperty.create("bottom");
    public static final EnumProperty<FenceTarp> TARP = EnumProperty.create("tarp", FenceTarp.class);
    /**
     * Which faces the tarp hangs on: the side the player stood on when hanging it (owner, 29 October
     * 2026). TARP_SOUTH for the panels that run east-west (south or north face), TARP_EAST for the
     * panels that run north-south (east or west face). At a corner both are set from where the player
     * stands, so a tarp hung from inside the corner is inside on both arms, and from outside, outside.
     */
    public static final BooleanProperty TARP_SOUTH = BooleanProperty.create("tarp_south");
    public static final BooleanProperty TARP_EAST = BooleanProperty.create("tarp_east");

    public SiteFenceBlock(Properties properties) {
        // Post and panels 2 pixels thick; each half a full block high, so the fence is 2 blocks tall.
        super(2.0F, 2.0F, 16.0F, 16.0F, 16.0F, properties);
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false)
            .setValue(SOUTH, false).setValue(WEST, false).setValue(WATERLOGGED, false)
            .setValue(HALF, DoubleBlockHalf.LOWER).setValue(POST, true).setValue(BOTTOM, true)
            .setValue(TARP, FenceTarp.NONE).setValue(TARP_SOUTH, true).setValue(TARP_EAST, true));
    }

    @Override
    protected MapCodec<? extends CrossCollisionBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, WATERLOGGED, HALF, POST, BOTTOM, TARP, TARP_SOUTH, TARP_EAST);
    }

    // ------------------------------------------------------------ placing and breaking

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        BlockState state = defaultBlockState().setValue(WATERLOGGED, level.getFluidState(pos).getType() == Fluids.WATER);
        return shaped(state, level, pos);
    }

    /** Puts the upper half on top, like a door. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        BlockPos above = pos.above();
        BlockState upper = state.setValue(HALF, DoubleBlockHalf.UPPER)
            .setValue(WATERLOGGED, level.getFluidState(above).getType() == Fluids.WATER);
        level.setBlock(above, shaped(upper, level, above), 3);
    }

    /** In creative, breaking the upper half must not drop the lower one (as doors do). */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            BlockState lower = level.getBlockState(below);
            if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
                BlockState empty = lower.getFluidState().is(Fluids.WATER) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
                level.setBlock(below, empty, 35);
                level.levelEvent(player, 2001, below, Block.getId(lower));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        // The other half is gone: this one goes too (the lower half drops the fence).
        if (direction.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (direction == Direction.UP)
            && !(neighbour.is(this) && neighbour.getValue(HALF) != half)) {
            return Blocks.AIR.defaultBlockState();
        }
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return shaped(state, level, pos);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    // ------------------------------------------------------------------ the tarp

    /** A carpet hangs a privacy tarp of its colour on the whole fence; shears take it off. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        FenceTarp tarp = state.getValue(TARP);
        if (stack.getItem() instanceof BlockItem item && item.getBlock() instanceof WoolCarpetBlock carpet) {
            FenceTarp wanted = FenceTarp.of(carpet.getColor());
            boolean south = player.getZ() > pos.getZ() + 0.5;
            boolean east = player.getX() > pos.getX() + 0.5;
            if (wanted == tarp && south == state.getValue(TARP_SOUTH) && east == state.getValue(TARP_EAST)) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (!level.isClientSide) {
                // The same colour from the other side only moves the tarp over; another colour replaces it.
                if (wanted != tarp) {
                    dropTarp(level, pos, tarp);
                    stack.consume(1, player);
                }
                setTarp(level, pos, state, wanted, south, east);
                level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stack.is(Items.SHEARS) && tarp != FenceTarp.NONE) {
            if (!level.isClientSide) {
                dropTarp(level, pos, tarp);
                setTarp(level, pos, state, FenceTarp.NONE, state.getValue(TARP_SOUTH), state.getValue(TARP_EAST));
                level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private void setTarp(Level level, BlockPos pos, BlockState state, FenceTarp tarp, boolean south, boolean east) {
        BlockPos other = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
        level.setBlock(pos, state.setValue(TARP, tarp).setValue(TARP_SOUTH, south).setValue(TARP_EAST, east), 3);
        BlockState otherState = level.getBlockState(other);
        if (otherState.is(this)) {
            level.setBlock(other, otherState.setValue(TARP, tarp).setValue(TARP_SOUTH, south).setValue(TARP_EAST, east), 3);
        }
    }

    private static void dropTarp(Level level, BlockPos pos, FenceTarp tarp) {
        DyeColor colour = tarp.colour();
        if (colour != null) {
            Block.popResource(level, pos, new ItemStack(BuiltInRegistries.ITEM.get(
                ResourceLocation.withDefaultNamespace(colour.getSerializedName() + "_carpet"))));
        }
    }

    // ------------------------------------------------------------ connections

    /** Works out the connections, the post and whether the fence stands on the ground. */
    private BlockState shaped(BlockState state, BlockGetter level, BlockPos pos) {
        boolean north = attaches(level, pos, Direction.NORTH);
        boolean east = attaches(level, pos, Direction.EAST);
        boolean south = attaches(level, pos, Direction.SOUTH);
        boolean west = attaches(level, pos, Direction.WEST);
        boolean post;
        if (east && west && !north && !south) {
            post = (pos.getX() & 1) == 0;
        } else if (north && south && !east && !west) {
            post = (pos.getZ() & 1) == 0;
        } else {
            post = true;
        }
        boolean bottom = state.getValue(HALF) == DoubleBlockHalf.LOWER && !level.getBlockState(pos.below()).is(this);
        return state.setValue(NORTH, north).setValue(EAST, east).setValue(SOUTH, south).setValue(WEST, west)
            .setValue(POST, post).setValue(BOTTOM, bottom);
    }

    /** Joins the same fence, and sturdy faces of other blocks, like iron bars do. */
    private boolean attaches(BlockGetter level, BlockPos pos, Direction side) {
        BlockPos other = pos.relative(side);
        BlockState state = level.getBlockState(other);
        return state.is(this) || state.isFaceSturdy(level, other, side.getOpposite()) && !isExceptionForConnection(state);
    }
}
