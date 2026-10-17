package dev.rawrland.constructionsite.content.blade;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import dev.rawrland.constructionsite.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * The bulldozer blade block.
 * Taking, carrying and dropping material live in {@link BulldozerBladeBlockEntity},
 * joining several blocks into one blade in {@link BladeGroup}.
 *
 * IBE is Create's helper for blocks with a block entity: it creates the block
 * entity and makes the game call it every tick.
 */
public class BulldozerBladeBlock extends Block implements IBE<BulldozerBladeBlockEntity>, IWrenchable {

    public static final MapCodec<BulldozerBladeBlock> CODEC = simpleCodec(BulldozerBladeBlock::new);

    /** The direction the working face points: north, east, south or west. The cutting edge is always at the bottom. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** True while this block is part of a bigger blade. */
    public static final BooleanProperty JOINED = BooleanProperty.create("joined");

    public BulldozerBladeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(JOINED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, JOINED);
    }

    /**
     * The working face points toward the player who places the blade. Blades are always placed upright.
     * Placed against another blade, the new one faces the same way as that one, so they
     * can join. Sneaking while placing switches that off.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        Player player = context.getPlayer();
        boolean sneaking = player != null && player.isShiftKeyDown();
        if (!sneaking) {
            BlockPos against = context.getClickedPos().relative(context.getClickedFace().getOpposite());
            BlockState neighbour = context.getLevel().getBlockState(against);
            if (neighbour.getBlock() == this) {
                facing = neighbour.getValue(FACING);
            }
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    public Class<BulldozerBladeBlockEntity> getBlockEntityClass() {
        return BulldozerBladeBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends BulldozerBladeBlockEntity> getBlockEntityType() {
        return ModBlockEntities.BULLDOZER_BLADE.get();
    }

    /** A new blade block may complete or extend a bigger blade. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        // Skip when only a property of an existing blade block changed (regroup itself does that).
        if (!oldState.is(state.getBlock())) {
            if (movedByPiston) {
                // Moved here by a contraption (Create Aeronautics puts its blocks down this way):
                // the other blocks and this block's saved data are not in place yet. Join
                // once everything has arrived, on the next tick.
                level.scheduleTick(pos, this, 1);
            } else {
                BladeGroup.regroup(level, pos);
            }
        }
    }

    /** Runs the join planned in {@link #onPlace} after a move. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BladeGroup.regroup(level, pos);
    }

    /**
     * When a blade block is removed, its load is broken the way a shovel tool
     * would break it, and the blocks around it work out anew whether they still join.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        boolean removed = !state.is(newState.getBlock());
        // A block that a contraption moves away is not broken: its load goes with it
        // (Create Aeronautics removes the old block this way after copying it).
        if (removed && !movedByPiston && level instanceof ServerLevel serverLevel
            && level.getBlockEntity(pos) instanceof BulldozerBladeBlockEntity blade) {
            blade.dropContentAsBroken(serverLevel);
        }
        // Create's way of removing the block entity.
        IBE.onRemove(state, level, pos, newState);
        if (removed && !movedByPiston) {
            for (Direction direction : Direction.values()) {
                BladeGroup.regroup(level, pos.relative(direction));
            }
        }
    }

    /** Wrench, right-click: switches the mount on the back of the whole blade: in the middle, at the ends, none. */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof BulldozerBladeBlockEntity blade) {
            blade.cycleGroupMount();
            IWrenchable.playRotateSound(level, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
