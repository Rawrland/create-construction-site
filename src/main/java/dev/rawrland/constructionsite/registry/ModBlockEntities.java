package dev.rawrland.constructionsite.registry;

import dev.rawrland.constructionsite.CreateConstructionSite;
import dev.rawrland.constructionsite.content.bucket.ExcavatorBucketBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {

    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateConstructionSite.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ExcavatorBucketBlockEntity>> EXCAVATOR_BUCKET =
        BLOCK_ENTITY_TYPES.register("excavator_bucket", () -> BlockEntityType.Builder.of(
            ExcavatorBucketBlockEntity::new, ModBlocks.EXCAVATOR_BUCKET.get()
        ).build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
