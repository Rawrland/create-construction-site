package dev.rawrland.constructionsite.registry;

import dev.rawrland.constructionsite.compat.Mods;
import dev.rawrland.constructionsite.compat.aeronautics.AeroBladeBlockEntity;
import dev.rawrland.constructionsite.compat.aeronautics.AeroBucketBlockEntity;
import dev.rawrland.constructionsite.compat.aeronautics.AeroDumpBodyBlockEntity;

import dev.rawrland.constructionsite.CreateConstructionSite;
import dev.rawrland.constructionsite.content.blade.BulldozerBladeBlockEntity;
import dev.rawrland.constructionsite.content.bucket.ExcavatorBucketBlockEntity;
import dev.rawrland.constructionsite.content.dumpbody.DumpBodyBlockEntity;
import dev.rawrland.constructionsite.compat.aeronautics.AeroCylinderMountBlockEntity;
import dev.rawrland.constructionsite.content.cylinder.CylinderMountBlockEntity;
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
    // One type per tool serves all its paint versions.

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ExcavatorBucketBlockEntity>> EXCAVATOR_BUCKET =
        BLOCK_ENTITY_TYPES.register("excavator_bucket", () -> BlockEntityType.Builder.of(
            Mods.AERONAUTICS ? AeroBucketBlockEntity::new : ExcavatorBucketBlockEntity::new,
            ModBlocks.EXCAVATOR_BUCKETS.blocks()
        ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BulldozerBladeBlockEntity>> BULLDOZER_BLADE =
        BLOCK_ENTITY_TYPES.register("bulldozer_blade", () -> BlockEntityType.Builder.of(
            Mods.AERONAUTICS ? AeroBladeBlockEntity::new : BulldozerBladeBlockEntity::new,
            ModBlocks.BULLDOZER_BLADES.blocks()
        ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DumpBodyBlockEntity>> DUMP_BODY =
        BLOCK_ENTITY_TYPES.register("dump_body", () -> BlockEntityType.Builder.of(
            Mods.AERONAUTICS ? AeroDumpBodyBlockEntity::new : DumpBodyBlockEntity::new,
            ModBlocks.DUMP_BODIES.blocks()
        ).build(null));

    /** One end of a hydraulic cylinder; with Create Aeronautics the subclass that can push (add-cylinder-push). */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CylinderMountBlockEntity>> CYLINDER_MOUNT =
        BLOCK_ENTITY_TYPES.register("cylinder_mount", () -> BlockEntityType.Builder.of(
            Mods.AERONAUTICS ? AeroCylinderMountBlockEntity::new : CylinderMountBlockEntity::new,
            ModBlocks.CYLINDER_MOUNT.get()
        ).build(null));

    /** Operator Console (add-operator-console). */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.rawrland.constructionsite.content.console.OperatorConsoleBlockEntity>> OPERATOR_CONSOLE =
        BLOCK_ENTITY_TYPES.register("operator_console", () -> BlockEntityType.Builder.of(
            dev.rawrland.constructionsite.content.console.OperatorConsoleBlockEntity::new,
            ModBlocks.OPERATOR_CONSOLE.get()
        ).build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
