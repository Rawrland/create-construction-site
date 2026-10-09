package dev.rawrland.constructionsite.registry;

import dev.rawrland.constructionsite.CreateConstructionSite;
import dev.rawrland.constructionsite.content.bucket.ExcavatorBucketBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreateConstructionSite.MODID);

    public static final DeferredBlock<ExcavatorBucketBlock> EXCAVATOR_BUCKET = BLOCKS.registerBlock(
        "excavator_bucket",
        ExcavatorBucketBlock::new,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_YELLOW)
            .strength(3.0F, 6.0F)
            .sound(SoundType.METAL)
            // The model is hollow, so neighbouring block faces must stay visible.
            .noOcclusion()
    );

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
