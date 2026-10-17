package dev.rawrland.constructionsite.registry;

import dev.rawrland.constructionsite.compat.Mods;
import dev.rawrland.constructionsite.compat.aeronautics.AeroBucketBlock;
import java.util.function.Function;

import dev.rawrland.constructionsite.CreateConstructionSite;
import dev.rawrland.constructionsite.content.blade.BulldozerBladeBlock;
import dev.rawrland.constructionsite.content.bucket.ExcavatorBucketBlock;
import dev.rawrland.constructionsite.content.dumpbody.DumpBodyBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreateConstructionSite.MODID);

    /**
     * With Create Aeronautics installed the bucket is its subclass with ropes
     * (see compat.aeronautics). Chosen once here; the id is the same either way,
     * so worlds open with or without Create Aeronautics.
     */
    private static final Function<BlockBehaviour.Properties, ExcavatorBucketBlock> BUCKET_FACTORY =
        Mods.AERONAUTICS ? AeroBucketBlock::new : ExcavatorBucketBlock::new;

    public static final DeferredBlock<ExcavatorBucketBlock> EXCAVATOR_BUCKET = BLOCKS.registerBlock(
        "excavator_bucket",
        BUCKET_FACTORY,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_YELLOW)
            .strength(3.0F, 6.0F)
            .sound(SoundType.METAL)
            // The model is hollow, so neighbouring block faces must stay visible.
            .noOcclusion()
    );

    public static final DeferredBlock<BulldozerBladeBlock> BULLDOZER_BLADE = BLOCKS.registerBlock(
        "bulldozer_blade",
        BulldozerBladeBlock::new,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_YELLOW)
            .strength(3.0F, 6.0F)
            .sound(SoundType.METAL)
            // The blade is drawn thinner than the block, so neighbouring block faces must stay visible.
            .noOcclusion()
    );

    public static final DeferredBlock<DumpBodyBlock> DUMP_BODY = BLOCKS.registerBlock(
        "dump_body",
        DumpBodyBlock::new,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_YELLOW)
            .strength(3.0F, 6.0F)
            .sound(SoundType.METAL)
            // The body is drawn open at the top with thin walls, so neighbouring block faces must stay visible.
            .noOcclusion()
    );

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
