package dev.rawrland.constructionsite.registry;

import dev.rawrland.constructionsite.CreateConstructionSite;
import dev.rawrland.constructionsite.content.material.FallingMaterialEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {

    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(Registries.ENTITY_TYPE, CreateConstructionSite.MODID);

    /** A block tipped out of a bucket, on its way to the ground. Almost a full block in size. */
    public static final DeferredHolder<EntityType<?>, EntityType<FallingMaterialEntity>> FALLING_MATERIAL =
        ENTITY_TYPES.register("falling_material", () -> EntityType.Builder
            .<FallingMaterialEntity>of(FallingMaterialEntity::new, MobCategory.MISC)
            .sized(0.98F, 0.98F)
            .clientTrackingRange(10)
            .updateInterval(2)
            .build("falling_material"));

    private ModEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
