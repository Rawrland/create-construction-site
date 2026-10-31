package dev.rawrland.constructionsite.content.barrier;

import com.mojang.serialization.MapCodec;
import dev.rawrland.constructionsite.registry.ModItems;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A traffic cone: orange with one white band, standing on its orange skirt.
 *
 * Cones stack without limit (owner, 29 October 2026): using a cone on any cone of a stack puts
 * one more on top, each 2 pixels higher than the one below. Eight fit in one block; the ninth
 * starts the next block up, so a stack can grow into a tall column. Breaking any block of a
 * column breaks the whole column and drops every cone. Sneaking with a cone or an empty hand takes
 * the top cone off again and gives it back (owner, 29 October 2026); with an empty hand a lamp on
 * top comes off first.
 *
 * CONES: how many cones stand with their foot in this block (1 to 8). BASE: this block stands on
 * the ground (no cone block below). TOP: no cone block above, so the top cone is drawn whole here.
 *
 * A Warning Flasher or Warning Beacon used on a stack clips onto its top (LAMP, on the top block); it
 * blinks and gives light. A flasher's lens faces the player who put it on (LAMP_AXIS). Sneaking with an empty hand takes it off; breaking drops it too.
 */
public class TrafficConeBlock extends Block {

    public static final MapCodec<TrafficConeBlock> CODEC = simpleCodec(TrafficConeBlock::new);
    /** Cones per block: each sits 2 pixels higher, so eight fill a block. */
    public static final int CONES_PER_BLOCK = 8;
    public static final IntegerProperty CONES = IntegerProperty.create("cones", 1, CONES_PER_BLOCK);
    public static final BooleanProperty BASE = BooleanProperty.create("base");
    public static final BooleanProperty TOP = BooleanProperty.create("top");
    public static final EnumProperty<ConeLamp> LAMP = EnumProperty.create("lamp", ConeLamp.class);
    /** Which way a Warning Flasher's lens faces: along Z (north-south) or X (east-west). */
    public static final EnumProperty<Direction.Axis> LAMP_AXIS =
        EnumProperty.create("lamp_axis", Direction.Axis.class, Direction.Axis.X, Direction.Axis.Z);
    /** How bright a cone with a lamp is. */
    public static final int LAMP_LIGHT = 10;

    private static final VoxelShape FULL = box(2.5, 0, 2.5, 13.5, 16, 13.5);
    private static final VoxelShape[] TOP_SHAPES = new VoxelShape[CONES_PER_BLOCK + 1];

    static {
        for (int cones = 1; cones <= CONES_PER_BLOCK; cones++) {
            // The skirt, and the top cone up to its tip (12 pixels, 2 more per cone below it).
            TOP_SHAPES[cones] = box(2.5, 0, 2.5, 13.5, Math.min(16, 12 + 2 * (cones - 1)), 13.5);
        }
    }

    public TrafficConeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CONES, 1).setValue(BASE, true).setValue(TOP, true)
            .setValue(LAMP, ConeLamp.NONE).setValue(LAMP_AXIS, Direction.Axis.Z));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CONES, BASE, TOP, LAMP, LAMP_AXIS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(TOP) ? TOP_SHAPES[state.getValue(CONES)] : FULL;
    }

    // ------------------------------------------------------------ placing

    /** A new cone on the ground, or the first cone of the next block of a full column. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState below = context.getLevel().getBlockState(context.getClickedPos().below());
        if (!below.is(this)) {
            return defaultBlockState();
        }
        // On top of a cone block: only as the next cone of a full column (a gap would show).
        return below.getValue(CONES) == CONES_PER_BLOCK && below.getValue(LAMP) == ConeLamp.NONE
            ? defaultBlockState().setValue(BASE, false) : null;
    }

    /** A cone or a lamp in hand goes onto the top of the stack, wherever the stack was clicked. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos topPos = top(level, pos);
        BlockState top = level.getBlockState(topPos);
        if (stack.is(asItem()) && player.isShiftKeyDown()) {
            // Sneaking with a cone: the top cone comes off and goes back into the hand's stack.
            if (!level.isClientSide) {
                takeTopCone(level, topPos, top, player);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stack.is(asItem())) {
            if (top.getValue(CONES) < CONES_PER_BLOCK) {
                if (!level.isClientSide) {
                    level.setBlock(topPos, top.setValue(CONES, top.getValue(CONES) + 1), 3);
                }
            } else {
                BlockPos above = topPos.above();
                if (above.getY() >= level.getMaxBuildHeight() || !level.getBlockState(above).canBeReplaced()) {
                    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                }
                if (!level.isClientSide) {
                    // A lamp moves up with the top of the stack.
                    level.setBlock(above, defaultBlockState().setValue(BASE, false).setValue(LAMP, top.getValue(LAMP))
                        .setValue(LAMP_AXIS, top.getValue(LAMP_AXIS)), 3);
                    if (top.getValue(LAMP) != ConeLamp.NONE) {
                        level.setBlock(topPos, level.getBlockState(topPos).setValue(LAMP, ConeLamp.NONE), 3);
                    }
                }
            }
            if (!level.isClientSide) {
                stack.consume(1, player);
                level.playSound(null, pos, getSoundType(state, level, pos, player).getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        ConeLamp lamp = stack.is(ModItems.WARNING_FLASHER.get()) ? ConeLamp.FLASHER
            : stack.is(ModItems.WARNING_BEACON.get()) ? ConeLamp.BEACON : ConeLamp.NONE;
        if (lamp == ConeLamp.NONE || top.getValue(LAMP) != ConeLamp.NONE) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            // The lens faces the player who puts it on.
            level.setBlock(topPos, top.setValue(LAMP, lamp).setValue(LAMP_AXIS, player.getDirection().getAxis()), 3);
            stack.consume(1, player);
            level.playSound(null, topPos, SoundEvents.LANTERN_PLACE, SoundSource.BLOCKS, 1.0F, 1.2F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Sneaking with an empty hand takes the lamp off the top, or else the top cone, and gives it back. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        BlockPos topPos = top(level, pos);
        BlockState top = level.getBlockState(topPos);
        ConeLamp lamp = top.getValue(LAMP);
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (lamp == ConeLamp.NONE) {
            if (!level.isClientSide) {
                takeTopCone(level, topPos, top, player);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            level.setBlock(topPos, top.setValue(LAMP, ConeLamp.NONE), 3);
            ItemStack item = new ItemStack(lamp == ConeLamp.FLASHER ? ModItems.WARNING_FLASHER.get() : ModItems.WARNING_BEACON.get());
            if (!player.getInventory().add(item)) {
                Block.popResource(level, topPos, item);
            }
            level.playSound(null, topPos, SoundEvents.LANTERN_BREAK, SoundSource.BLOCKS, 1.0F, 1.2F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Takes the top cone off a stack and gives it to the player (not in creative). When that empties
     * the top block, a lamp on it moves down onto the cone below.
     */
    private void takeTopCone(Level level, BlockPos topPos, BlockState top, Player player) {
        if (top.getValue(CONES) > 1) {
            level.setBlock(topPos, top.setValue(CONES, top.getValue(CONES) - 1), 3);
        } else {
            BlockPos below = topPos.below();
            BlockState under = level.getBlockState(below);
            // The block is emptied without drops: the cone goes to the player below, the lamp moves down.
            level.setBlock(topPos, Blocks.AIR.defaultBlockState(), 3);
            if (under.is(this) && top.getValue(LAMP) != ConeLamp.NONE) {
                level.setBlock(below, level.getBlockState(below).setValue(LAMP, top.getValue(LAMP))
                    .setValue(LAMP_AXIS, top.getValue(LAMP_AXIS)), 3);
            } else if (top.getValue(LAMP) != ConeLamp.NONE) {
                ItemStack lamp = new ItemStack(top.getValue(LAMP) == ConeLamp.FLASHER
                    ? ModItems.WARNING_FLASHER.get() : ModItems.WARNING_BEACON.get());
                if (!player.isCreative() && !player.getInventory().add(lamp)) {
                    Block.popResource(level, topPos, lamp);
                }
            }
        }
        if (!player.isCreative()) {
            ItemStack cone = new ItemStack(asItem());
            if (!player.getInventory().add(cone)) {
                Block.popResource(level, topPos, cone);
            }
        }
        level.playSound(null, topPos, getSoundType(top, level, topPos, player).getBreakSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    // ------------------------------------------------------------ the column

    /** Breaking one block of a column breaks all of it; every block drops its cones. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            boolean drop = !player.isCreative();
            // From the top down to just above this block, so nothing above is left hanging.
            BlockPos top = top(level, pos);
            for (BlockPos above = top; above.getY() > pos.getY(); above = above.below()) {
                level.destroyBlock(above, drop, player);
            }
            // This block must not fall with the ones below it: it is being broken by the player.
            BlockState self = level.getBlockState(pos);
            if (self.is(this)) {
                level.setBlock(pos, self.setValue(BASE, true), 2 | 16);
            }
            for (BlockPos below = pos.below(); level.getBlockState(below).is(this); below = below.below()) {
                level.destroyBlock(below, drop, player);
            }
        }
        return super.playerWillDestroy(level, pos, level.getBlockState(pos), player);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (direction == Direction.DOWN && !state.getValue(BASE) && !neighbour.is(this)) {
            // The cones below are gone: this part of the column goes too, and drops its cones.
            return Blocks.AIR.defaultBlockState();
        }
        if (direction == Direction.UP) {
            return state.setValue(TOP, !neighbour.is(this));
        }
        return state;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        if (rotation == Rotation.CLOCKWISE_90
            || rotation == Rotation.COUNTERCLOCKWISE_90) {
            return state.setValue(LAMP_AXIS, state.getValue(LAMP_AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
        }
        return state;
    }

    /** The top block of the column this block belongs to. */
    private BlockPos top(BlockGetter level, BlockPos pos) {
        BlockPos top = pos;
        while (level.getBlockState(top.above()).is(this)) {
            top = top.above();
        }
        return top;
    }
}
