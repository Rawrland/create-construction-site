package dev.rawrland.constructionsite.content.cylinder;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import dev.rawrland.constructionsite.registry.ModBlockEntities;
import dev.rawrland.constructionsite.registry.ModItems;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Cylinder Mount: a bracket with a pin. Two mounts linked with a Hydraulic Cylinder
 * item show a cylinder between their pins. Empty hand on a linked mount opens its
 * settings; the wrench turns the pin.
 *
 * Facing is the side the bracket points to; it sits on the block behind it.
 */
public class CylinderMountBlock extends DirectionalBlock implements EntityBlock, IWrenchable {

    public static final MapCodec<CylinderMountBlock> CODEC = simpleCodec(CylinderMountBlock::new);

    /** The pin turned 90 degrees about the facing: the wrench switches it. */
    public static final BooleanProperty TURNED = BooleanProperty.create("turned");

    /** The pin block in the middle, the same for every facing. */
    private static final VoxelShape PIN = Block.box(4, 4, 4, 12, 12, 12);
    /** The base plate, by the side it sits on. */
    private static final VoxelShape[] PLATES = new VoxelShape[6];

    static {
        PLATES[Direction.DOWN.get3DDataValue()] = Block.box(2, 0, 2, 14, 3, 14);
        PLATES[Direction.UP.get3DDataValue()] = Block.box(2, 13, 2, 14, 16, 14);
        PLATES[Direction.NORTH.get3DDataValue()] = Block.box(2, 2, 0, 14, 14, 3);
        PLATES[Direction.SOUTH.get3DDataValue()] = Block.box(2, 2, 13, 14, 14, 16);
        PLATES[Direction.WEST.get3DDataValue()] = Block.box(0, 2, 2, 3, 14, 14);
        PLATES[Direction.EAST.get3DDataValue()] = Block.box(13, 2, 2, 16, 14, 14);
    }

    public CylinderMountBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(TURNED, false));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TURNED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction base = state.getValue(FACING).getOpposite();
        return Shapes.or(PLATES[base.get3DDataValue()], PIN);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        // Through the registered type, so that with Create Aeronautics the subclass that takes
        // part in Sable's physics steps is made, also for placed and assembled mounts. (Before
        // 30 October 2026 this made the plain class, and only mounts loaded from a save pushed.)
        return ModBlockEntities.CYLINDER_MOUNT.get().create(pos, state);
    }

    // ----------------------------------------------------------------- redstone

    /** The barrel end of a driven cylinder takes its target length from redstone around it. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof CylinderMountBlockEntity end
            && end.isLinked() && end.isBarrelEnd()) {
            end.readSignal();
        }
    }

    // ----------------------------------------------------------- settings window

    /**
     * Empty hand on a linked mount: opens the cylinder's settings window
     * (add-cylinder-settings). The window lives in the client package and is only
     * loaded in the player's game.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof CylinderMountBlockEntity end) || !end.isLinked()) {
            return InteractionResult.PASS;
        }
        // The other end is gone (owner, 30 October 2026: one end was broken and the other still
        // opened its settings). The cylinder can be at most 24 blocks long, so with the player
        // standing here the other end would be loaded if it still existed: this end is loosened.
        if (end.partner() == null) {
            if (!level.isClientSide) {
                loosenLonelyEnd(level, pos, end);
                message(player, "message.create_construction_site.hydraulic_cylinder.other_end_gone");
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) {
            dev.rawrland.constructionsite.client.HydraulicCylinderScreen.open(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ----------------------------------------------------------------- breaking

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        boolean removed = !state.is(newState.getBlock());
        // Moved by a contraption assembly: not broken, the cylinder goes along.
        if (removed && !movedByPiston && !level.isClientSide
            && level.getBlockEntity(pos) instanceof CylinderMountBlockEntity end && end.isLinked()) {
            breakCylinder(level, pos, end);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * A linked mount is broken: the cylinder comes off as an item, and the other end
     * is loosened. Only one item drops per cylinder: if the other end is not loaded,
     * nothing drops now and the other end drops it when it is broken.
     */
    private static void breakCylinder(Level level, BlockPos pos, CylinderMountBlockEntity end) {
        CylinderMountBlockEntity other = end.partner();
        boolean drop = end.isBarrelEnd() || other != null;
        if (other != null) {
            other.unlink();
        }
        end.unlink();
        if (drop) {
            Block.popResource(level, pos, new ItemStack(ModItems.HYDRAULIC_CYLINDER.get()));
        }
    }

    /**
     * An end whose other end no longer exists: loosened, with the same one-item rule as
     * breaking. The barrel end always drops when broken, so a lonely rod end gets nothing; a
     * lonely barrel end means its rod end was broken without dropping, so it drops now.
     */
    private static void loosenLonelyEnd(Level level, BlockPos pos, CylinderMountBlockEntity end) {
        boolean drop = end.isBarrelEnd();
        end.unlink();
        if (drop) {
            Block.popResource(level, pos, new ItemStack(ModItems.HYDRAULIC_CYLINDER.get()));
        }
    }

    // ------------------------------------------------------------ Create's wrench

    /**
     * Wrench: turns the pin 90 degrees about the mount's facing, so it can lie across
     * the arm's swing. Works on linked mounts too; the cylinder stays.
     */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!level.isClientSide) {
            level.setBlock(pos, state.cycle(TURNED), Block.UPDATE_ALL);
            IWrenchable.playRotateSound(level, pos);
        }
        return InteractionResult.SUCCESS;
    }

    static void message(@Nullable Player player, String key) {
        if (player != null) {
            player.displayClientMessage(Component.translatable(key), true);
        }
    }
}
