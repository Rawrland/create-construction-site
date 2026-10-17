package dev.rawrland.constructionsite.registry;

import dev.rawrland.constructionsite.compat.Mods;
import dev.rawrland.constructionsite.compat.aeronautics.AeroBladeBlockEntity;
import dev.rawrland.constructionsite.compat.aeronautics.AeroBucketBlockEntity;
import dev.rawrland.constructionsite.compat.aeronautics.AeroDumpBodyBlockEntity;

import dev.rawrland.constructionsite.CreateConstructionSite;
import dev.rawrland.constructionsite.content.blade.BulldozerBladeBlockEntity;
import dev.rawrland.constructionsite.content.bucket.ExcavatorBucketBlockEntity;
import dev.rawrland.constructionsite.content.dumpbody.DumpBodyBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {

    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateConstructionSite.MODID);

    // With Create Aeronautics installed, the block entities are their subclasses that
    // add ropes and the tick on simulated contraptions (see compat.aeronautics).
    // The ids are the same either way, so saved worlds open with or without it.

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ExcavatorBucketBlockEntity>> EXCAVATOR_BUCKET =
        BLOCK_ENTITY_TYPES.register("excavator_bucket", () -> BlockEntityType.Builder.of(
            Mods.AERONAUTICS ? AeroBucketBlockEntity::new : ExcavatorBucketBlockEntity::new,
            ModBlocks.EXCAVATOR_BUCKET.get()
        ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BulldozerBladeBlockEntity>> BULLDOZER_BLADE =
        BLOCK_ENTITY_TYPES.register("bulldozer_blade", () -> BlockEntityType.Builder.of(
            Mods.AERONAUTICS ? AeroBladeBlockEntity::new : BulldozerBladeBlockEntity::new,
            ModBlocks.BULLDOZER_BLADE.get()
        ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DumpBodyBlockEntity>> DUMP_BODY =
        BLOCK_ENTITY_TYPES.register("dump_body", () -> BlockEntityType.Builder.of(
            Mods.AERONAUTICS ? AeroDumpBodyBlockEntity::new : DumpBodyBlockEntity::new,
            ModBlocks.DUMP_BODY.get()
        ).build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
