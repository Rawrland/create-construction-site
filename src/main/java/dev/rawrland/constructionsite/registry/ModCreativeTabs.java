package dev.rawrland.constructionsite.registry;

import dev.rawrland.constructionsite.CreateConstructionSite;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {

    private static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateConstructionSite.MODID);

    /** The mod's single creative tab. Its label comes from en_us.json. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = CREATIVE_MODE_TABS.register(
        "main",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + CreateConstructionSite.MODID))
            .icon(() -> ModItems.EXCAVATOR_BUCKET.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                // 1. Technical blocks, bare: the tools, then the hydraulics and the console.
                output.accept(ModItems.EXCAVATOR_BUCKET.get());
                output.accept(ModItems.BULLDOZER_BLADE.get());
                output.accept(ModItems.DUMP_BODY.get());
                output.accept(ModItems.CYLINDER_MOUNT.get());
                output.accept(ModItems.HYDRAULIC_CYLINDER.get());
                output.accept(ModItems.OPERATOR_CONSOLE.get());

                // 2. Site barriers.
                output.accept(ModItems.TRAFFIC_CONE.get());
                output.accept(ModItems.WARNING_FLASHER.get());
                output.accept(ModItems.WARNING_BEACON.get());
                output.accept(ModItems.DELINEATOR.get());
                output.accept(ModItems.BARRIER_BOARD.get());
                output.accept(ModItems.SITE_FENCE.get());

                // 3. Building blocks, bare: each plating with its stairs and slab, like vanilla's planks.
                output.accept(ModItems.BRASS_PLATING.bare().get());
                output.accept(ModItems.BRASS_PLATING_STAIRS.bare().get());
                output.accept(ModItems.BRASS_PLATING_SLABS.bare().get());
                output.accept(ModItems.DARK_STEEL_PLATING.get());
                output.accept(ModItems.DARK_STEEL_PLATING_STAIRS.get());
                output.accept(ModItems.DARK_STEEL_PLATING_SLAB.get());
                output.accept(ModItems.POLISHED_IRON_PLATING.get());
                output.accept(ModItems.POLISHED_IRON_PLATING_STAIRS.get());
                output.accept(ModItems.POLISHED_IRON_PLATING_SLAB.get());
                output.accept(ModItems.TREAD_PLATE.get());
                output.accept(ModItems.TREAD_PLATE_STAIRS.get());
                output.accept(ModItems.TREAD_PLATE_SLAB.get());
                output.accept(ModItems.GRATING.get());
                output.accept(ModItems.GRATING_SLAB.get());
                output.accept(ModItems.HAZARD_STRIPES.get());
                output.accept(ModItems.HAZARD_PLATE.get());
                output.accept(ModItems.HAZARD_STRIPES_RED_WHITE.get());
                output.accept(ModItems.HAZARD_PLATE_RED_WHITE.get());
                output.accept(ModItems.CORRUGATED_SHEETS.bare().get());

                // 4. The coloured versions, grouped by block (all buckets, all blades, ...), each
                //    group in the colour order of the painted lists (index 0 is the bare one).
                colours(output, ModItems.EXCAVATOR_BUCKETS);
                colours(output, ModItems.BULLDOZER_BLADES);
                colours(output, ModItems.DUMP_BODIES);
                for (int i = 1; i < ModItems.BRASS_PLATING.all().size(); i++) {
                    output.accept(ModItems.BRASS_PLATING.all().get(i).get());
                    output.accept(ModItems.BRASS_PLATING_STAIRS.all().get(i).get());
                    output.accept(ModItems.BRASS_PLATING_SLABS.all().get(i).get());
                }
                colours(output, ModItems.CORRUGATED_SHEETS);
            })
            .build()
    );

    /** The coloured versions of a painted item, without the bare one (shown with the bare blocks). */
    private static void colours(CreativeModeTab.Output output, PaintedItems<?> painted) {
        for (int i = 1; i < painted.all().size(); i++) {
            output.accept(painted.all().get(i).get());
        }
    }

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
