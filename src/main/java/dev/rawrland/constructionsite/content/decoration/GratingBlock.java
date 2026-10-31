package dev.rawrland.constructionsite.content.decoration;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.TransparentBlock;

/**
 * An iron floor grating: a full block you can see through, like glass, but walkable and
 * solid for dropped items. Faces between two gratings are not drawn, so a floor of them
 * looks like one grating.
 */
public class GratingBlock extends TransparentBlock {

    public static final MapCodec<GratingBlock> CODEC = simpleCodec(GratingBlock::new);

    public GratingBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends TransparentBlock> codec() {
        return CODEC;
    }
}
