package dev.rawrland.constructionsite.content.bucket;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import dev.rawrland.constructionsite.registry.ModBlockEntities;
import dev.simulated_team.simulated.content.blocks.rope.RopeHolderBlock;
import dev.simulated_team.simulated.content.items.rope.RopeItem.RopeItem;
import dev.simulated_team.simulated.index.SimDataComponents;
import dev.simulated_team.simulated.index.SimTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Map;

/**
 * The excavator bucket block.
 * Digging and content live in {@link ExcavatorBucketBlockEntity},
 * joining several blocks into one bucket in {@link BucketGroup}.
 *
 * RopeHolderBlock is Create Aeronautics' helper for blocks a rope can be tied
 * to. It keeps a rope tied when the block becomes part of a contraption. It
 * includes IBE, Create's helper for blocks with a block entity, which creates
 * the block entity and makes the game call it every tick.
 */
public class ExcavatorBucketBlock extends Block implements RopeHolderBlock<ExcavatorBucketBlockEntity>, IWrenchable {

    public static final MapCodec<ExcavatorBucketBlock> CODEC = simpleCodec(ExcavatorBucketBlock::new);

    /** The direction the open side faces: north, east, south or west. The teeth are always at the bottom. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** True while this block is part of a bigger bucket. Then the renderer draws the bucket, not the block model. */
    public static final BooleanProperty JOINED = BooleanProperty.create("joined");

    private static final String MESSAGE_HOLDS = "message.create_construction_site.excavator_bucket.holds";
    private static final String MESSAGE_EMPTY = "message.create_construction_site.excavator_bucket.empty";
    private static final String MESSAGE_ENTRY = "message.create_construction_site.excavator_bucket.entry";
    private static final String MESSAGE_MORE = "message.create_construction_site.excavator_bucket.more";

    /** How many kinds of block the message names before it sums up the rest. Keeps it on one line. */
    private static final int MESSAGE_MAX_KINDS = 3;

    public ExcavatorBucketBlock(Properties properties) {
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
     * The open side faces the player who places the bucket. Buckets are always placed upright.
     * Placed against another bucket, the new one faces the same way as that one, so they
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
    public Class<ExcavatorBucketBlockEntity> getBlockEntityClass() {
        return ExcavatorBucketBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ExcavatorBucketBlockEntity> getBlockEntityType() {
        return ModBlockEntities.EXCAVATOR_BUCKET.get();
    }

    /** A new bucket block may complete, extend or break up a bigger bucket. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        // Skip when only a property of an existing bucket block changed (regroup itself does that).
        if (!oldState.is(state.getBlock())) {
            BucketGroup.regroup(level, pos);
        }
    }

    /**
     * When a bucket block is removed, its content is broken the way a shovel tool
     * would break it, and the blocks around it work out anew whether they still join.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        boolean removed = !state.is(newState.getBlock());
        if (removed && level instanceof ServerLevel serverLevel
            && level.getBlockEntity(pos) instanceof ExcavatorBucketBlockEntity bucket) {
            bucket.dropContentAsBroken(serverLevel);
        }
        // Create's way of removing the block entity. It also tells the plug-in parts that the block is gone.
        IBE.onRemove(state, level, pos, newState);
        if (removed) {
            for (Direction direction : Direction.values()) {
                BucketGroup.regroup(level, pos.relative(direction));
            }
        }
    }

    // ------------------------------------------------------------ Create's wrench

    /**
     * Wrench, right-click: moves the bucket's mounting ears to the next position:
     * on top, on the back, none. Works on single and joined buckets alike.
     */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ExcavatorBucketBlockEntity bucket) {
            bucket.cycleGroupMount();
            IWrenchable.playRotateSound(level, pos);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Wrench, sneak and right-click: picks up the whole slice the clicked block
     * belongs to, with its content, into the player's inventory.
     * (Create calls this method by name; it replaces Create's default of picking up one block.)
     */
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof ExcavatorBucketBlockEntity clicked)) {
            return InteractionResult.PASS;
        }
        // Work out the slice first: removing its first block already takes the bucket apart.
        List<BlockPos> slice = clicked.getSlicePositions();
        boolean giveItems = player != null && !player.isCreative();
        for (BlockPos slicePos : slice) {
            if (!(level.getBlockEntity(slicePos) instanceof ExcavatorBucketBlockEntity bucket)) {
                continue;
            }
            // Take the content out first, so removing the block does not drop it on the ground.
            List<ItemStack> items = bucket.removeContentAsBroken(serverLevel);
            items.add(new ItemStack(this));
            if (giveItems) {
                for (ItemStack stack : items) {
                    player.getInventory().placeItemBackInInventory(stack);
                }
            }
            level.destroyBlock(slicePos, false);
        }
        return InteractionResult.SUCCESS;
    }

    // -------------------------------------------------------------------- rope

    /**
     * Right-click with an item. Two items matter here:
     * the Rope Coupling ties a rope to the bucket, and shears (anything that
     * cuts Create Aeronautics' ropes) remove it again.
     *
     * A rope goes to the teeth of the clicked block's column. If that column
     * already has a rope, the nearest free column is used. Shears look for a
     * rope in the same order.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof ExcavatorBucketBlockEntity bucket)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (stack.is(SimTags.Items.DESTROYS_ROPE)) {
            ExcavatorBucketBlockEntity roped = bucket.findHookBlock(true);
            if (roped == null) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
                return ItemInteractionResult.SUCCESS;
            }
            return RopeHolderBlock.shearRope(this, level, roped.getBlockPos(), serverPlayer);
        }

        if (stack.getItem() instanceof RopeItem) {
            // A rope from a bucket to itself makes no sense: ignore the second click.
            BlockPos firstEnd = stack.get(SimDataComponents.ROPE_FIRST_CONNECTION);
            if (firstEnd != null && bucket.isPartOfBucket(firstEnd)) {
                return ItemInteractionResult.CONSUME;
            }
            // One rope per block of width: if every column has one, no further rope is started.
            ExcavatorBucketBlockEntity free = bucket.findHookBlock(false);
            if (free == null) {
                return ItemInteractionResult.CONSUME;
            }
            BlockPos hook = free.getBlockPos();
            if (hook.equals(pos)) {
                // The hook block itself was clicked: the item does its normal work on it.
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            // Another block of the bucket was clicked: let the item act as if the hook block had been.
            InteractionResult result = stack.useOn(new UseOnContext(player, hand, hitResult.withPosition(hook)));
            return result.consumesAction()
                ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                : ItemInteractionResult.CONSUME;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    // ------------------------------------------------------------- inspecting

    /** Right-click with an empty hand: show what the whole bucket holds above the hotbar. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ExcavatorBucketBlockEntity bucket) {
            int capacity = bucket.getGroupCapacity();
            Map<Block, Integer> content = bucket.countGroupContent();
            int total = content.values().stream().mapToInt(Integer::intValue).sum();

            Component message;
            if (total == 0) {
                message = Component.translatable(MESSAGE_EMPTY, capacity);
            } else {
                // Name the kinds there is most of; sum up the rest so the line stays short.
                MutableComponent list = Component.empty();
                int named = 0;
                int namedBlocks = 0;
                for (Map.Entry<Block, Integer> entry : content.entrySet()) {
                    if (named == MESSAGE_MAX_KINDS) {
                        break;
                    }
                    if (named > 0) {
                        list.append(", ");
                    }
                    list.append(Component.translatable(MESSAGE_ENTRY, entry.getKey().getName(), entry.getValue()));
                    named++;
                    namedBlocks += entry.getValue();
                }
                if (content.size() > named) {
                    list.append(", ").append(Component.translatable(MESSAGE_MORE, total - namedBlocks));
                }
                message = Component.translatable(MESSAGE_HOLDS, total, capacity, list);
            }
            player.displayClientMessage(message, true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
