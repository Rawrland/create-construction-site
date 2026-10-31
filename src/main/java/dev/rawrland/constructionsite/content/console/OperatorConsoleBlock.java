package dev.rawrland.constructionsite.content.console;

import com.mojang.serialization.MapCodec;
import dev.rawrland.constructionsite.registry.ModBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Operator Console (add-operator-console). Faces the player who places it. On Create's Train
 * Controls desk (its model is reused): a cross of 4 buttons and 4 bonus buttons at the back,
 * 5 levers at the front, lever 1 at the operator's left.
 *
 * Right-click: take control (keys move the levers). Sneak + right-click with an empty hand:
 * the frequencies window.
 */
public class OperatorConsoleBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<OperatorConsoleBlock> CODEC = simpleCodec(OperatorConsoleBlock::new);

    /** Desk at the back half, base plate under the operator's feet; by facing (the side the operator stands on). */
    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            SHAPES[facing.get2DDataValue()] = Shapes.or(box(facing, 0, 0, 0, 16, 2, 12), box(facing, 0, 2, 0, 16, 16, 10));
        }
    }

    /**
     * A box given in the console's own frame (operator on the +Z side, as for facing south),
     * turned to the facing.
     */
    private static VoxelShape box(Direction facing, double x0, double y0, double z0, double x1, double y1, double z1) {
        return switch (facing) {
            case NORTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case EAST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            case WEST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
            default -> Block.box(x0, y0, z0, x1, y1, z1);
        };
    }

    public OperatorConsoleBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof OperatorConsoleBlockEntity console)) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(new SimpleMenuProvider(
                        (id, inventory, p) -> new OperatorConsoleMenu(id, inventory, console),
                        Component.translatable("block.create_construction_site.operator_console")),
                    buffer -> buffer.writeBlockPos(pos));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) {
            // Client-only class, only touched in the player's game.
            dev.rawrland.constructionsite.client.OperatorConsoleControls.start(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.OPERATOR_CONSOLE.get().create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.OPERATOR_CONSOLE.get()) {
            return null;
        }
        return (l, p, s, blockEntity) -> ((OperatorConsoleBlockEntity) blockEntity).tickServer();
    }
}
