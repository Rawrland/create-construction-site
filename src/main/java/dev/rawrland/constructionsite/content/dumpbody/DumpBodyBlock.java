package dev.rawrland.constructionsite.content.dumpbody;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import dev.rawrland.constructionsite.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * The dump body block: one cell of an open-top box that carries loose material
 * and tips it out. Holding, catching and emptying live in
 * {@link DumpBodyBlockEntity}, joining blocks into one box in {@link DumpBodyGroup}.
 *
 * A box has no front, so the block has no facing. Its block state says whether
 * it is joined, and for each of the four sides whether that side may open (a
 * tailgate) or is always closed (a fixed wall). Create's wrench switches a side
 * for the whole dump body. Because the sides are part of the block state, a
 * contraption that turns the dump body turns its sides with it.
 */
public class DumpBodyBlock extends Block implements IBE<DumpBodyBlockEntity>, IWrenchable {

    public static final MapCodec<DumpBodyBlock> CODEC = simpleCodec(DumpBodyBlock::new);

    /** True while this block is part of a bigger dump body. */
    public static final BooleanProperty JOINED = BooleanProperty.create("joined");

    /** True if the north side may open; false if it is always closed. The same for the other three. */
    public static final BooleanProperty GATE_NORTH = BooleanProperty.create("gate_north");
    public static final BooleanProperty GATE_EAST = BooleanProperty.create("gate_east");
    public static final BooleanProperty GATE_SOUTH = BooleanProperty.create("gate_south");
    public static final BooleanProperty GATE_WEST = BooleanProperty.create("gate_west");

    public DumpBodyBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(JOINED, false)
            // A new dump body is closed on all sides (owner's choice, 17 October 2026);
            // the wrench opens a side into a tailgate.
            .setValue(GATE_NORTH, false)
            .setValue(GATE_EAST, false)
            .setValue(GATE_SOUTH, false)
            .setValue(GATE_WEST, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(JOINED, GATE_NORTH, GATE_EAST, GATE_SOUTH, GATE_WEST);
    }

    /** The property for one of the four level sides. */
    public static BooleanProperty gate(Direction side) {
        return switch (side) {
            case NORTH -> GATE_NORTH;
            case EAST -> GATE_EAST;
            case SOUTH -> GATE_SOUTH;
            case WEST -> GATE_WEST;
            default -> throw new IllegalArgumentException("Only level sides have gates: " + side);
        };
    }

    /** True if this side of a dump body block may open. */
    public static boolean mayOpen(BlockState state, Direction side) {
        return state.getValue(gate(side));
    }

    /** The same block state with the four gate settings of another one. */
    public static BlockState withGatesOf(BlockState state, BlockState gatesFrom) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            state = state.setValue(gate(side), gatesFrom.getValue(gate(side)));
        }
        return state;
    }

    /**
     * Placed against another dump body, the new block takes that body's side
     * settings, so adding to a body with a closed side keeps it closed.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos against = context.getClickedPos().relative(context.getClickedFace().getOpposite());
        BlockState neighbour = context.getLevel().getBlockState(against);
        BlockState state = defaultBlockState();
        return neighbour.getBlock() == this ? withGatesOf(state, neighbour) : state;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        BlockState result = state;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            result = result.setValue(gate(rotation.rotate(side)), state.getValue(gate(side)));
        }
        return result;
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState result = state;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            result = result.setValue(gate(mirror.mirror(side)), state.getValue(gate(side)));
        }
        return result;
    }

    @Override
    public Class<DumpBodyBlockEntity> getBlockEntityClass() {
        return DumpBodyBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends DumpBodyBlockEntity> getBlockEntityType() {
        return ModBlockEntities.DUMP_BODY.get();
    }

    /** A new dump body block may complete or extend a bigger dump body. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        // Skip when only a property of an existing block changed (regroup and the wrench do that).
        if (!oldState.is(state.getBlock())) {
            if (movedByPiston) {
                // Moved here by a contraption (Create Aeronautics puts its blocks down this way):
                // the other blocks and this block's saved data are not in place yet. Join
                // once everything has arrived, on the next tick.
                level.scheduleTick(pos, this, 1);
            } else {
                DumpBodyGroup.regroup(level, pos);
            }
        }
    }

    /** Runs the join planned in {@link #onPlace} after a move. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        DumpBodyGroup.regroup(level, pos);
    }

    /**
     * When a dump body block is removed, its load is broken the way a shovel
     * tool would break it, and the blocks around it work out anew which box they form.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        boolean removed = !state.is(newState.getBlock());
        // A block that a contraption moves away is not broken: its load goes with it
        // (Create Aeronautics removes the old block this way after copying it).
        if (removed && !movedByPiston && level instanceof ServerLevel serverLevel
            && level.getBlockEntity(pos) instanceof DumpBodyBlockEntity body) {
            body.dropContentAsBroken(serverLevel);
        }
        // Create's way of removing the block entity.
        IBE.onRemove(state, level, pos, newState);
        if (removed && !movedByPiston) {
            for (Direction direction : Direction.values()) {
                DumpBodyGroup.regroup(level, pos.relative(direction));
            }
        }
    }

    /**
     * Wrench, right-click on a side: switches that side of the whole dump body
     * between "may open" and "always closed". The top and the bottom have no gate.
     */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Direction side = context.getClickedFace();
        if (!side.getAxis().isHorizontal()) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof DumpBodyBlockEntity body) {
            boolean nowOpen = body.toggleGate(side);
            level.playSound(null, pos, nowOpen ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE,
                SoundSource.BLOCKS, 0.6F, 1.4F);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Wrench, sneak and right-click: picks up one slice of the dump body, with its load,
     * into the player's inventory, like the Excavator Bucket (owner, 20 October 2026).
     * A slice is the layer of blocks the clicked block is in, flat against the clicked face.
     * (Create calls this method by name; it replaces Create's default of picking up one block.)
     */
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof DumpBodyBlockEntity clicked)) {
            return InteractionResult.PASS;
        }
        // Work out the slice first: removing its first block already takes the dump body apart.
        List<BlockPos> slice = clicked.getLayerPositions(context.getClickedFace());
        boolean giveItems = player != null && !player.isCreative();
        for (BlockPos slicePos : slice) {
            if (!(level.getBlockEntity(slicePos) instanceof DumpBodyBlockEntity member)) {
                continue;
            }
            // Take the load out first, so removing the block does not drop it on the ground.
            List<ItemStack> items = member.removeContentAsBroken(serverLevel);
            items.add(new ItemStack(level.getBlockState(slicePos).getBlock()));
            if (giveItems) {
                for (ItemStack stack : items) {
                    player.getInventory().placeItemBackInInventory(stack);
                }
            }
            level.destroyBlock(slicePos, false);
        }
        return InteractionResult.SUCCESS;
    }
}
