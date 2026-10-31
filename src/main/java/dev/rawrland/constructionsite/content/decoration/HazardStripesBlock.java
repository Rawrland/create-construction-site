package dev.rawrland.constructionsite.content.decoration;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A block of warning stripes, yellow-black or red-white. The wrench flips the direction
 * the stripes run (the model turns the texture a quarter turn).
 */
public class HazardStripesBlock extends Block implements IWrenchable {

    public static final MapCodec<HazardStripesBlock> CODEC = simpleCodec(HazardStripesBlock::new);
    public static final BooleanProperty FLIPPED = BooleanProperty.create("flipped");

    public HazardStripesBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FLIPPED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FLIPPED);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        flip(state, context);
        return InteractionResult.SUCCESS;
    }

    static void flip(BlockState state, UseOnContext context) {
        if (!context.getLevel().isClientSide) {
            context.getLevel().setBlock(context.getClickedPos(), state.cycle(FLIPPED), 3);
            IWrenchable.playRotateSound(context.getLevel(), context.getClickedPos());
        }
    }
}
