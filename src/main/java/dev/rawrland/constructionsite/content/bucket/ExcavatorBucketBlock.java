package dev.rawrland.constructionsite.content.bucket;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
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
 */
public class ExcavatorBucketBlock extends Block implements EntityBlock, IWrenchable {

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
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ExcavatorBucketBlockEntity(pos, state);
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
        super.onRemove(state, level, pos, newState, movedByPiston);
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
