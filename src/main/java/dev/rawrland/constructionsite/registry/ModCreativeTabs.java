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
                // Each tool bare, then in its 16 colours; then the metal blocks.
                for (Item item : ModItems.EXCAVATOR_BUCKETS.items()) output.accept(item);
                for (Item item : ModItems.BULLDOZER_BLADES.items()) output.accept(item);
                for (Item item : ModItems.DUMP_BODIES.items()) output.accept(item);
                // Each plating with its stairs and slab, like vanilla's planks.
                for (int i = 0; i < ModItems.BRASS_PLATING.all().size(); i++) {
                    output.accept(ModItems.BRASS_PLATING.all().get(i).get());
                    output.accept(ModItems.BRASS_PLATING_STAIRS.all().get(i).get());
                    output.accept(ModItems.BRASS_PLATING_SLABS.all().get(i).get());
                }
                output.accept(ModItems.DARK_STEEL_PLATING.get());
                output.accept(ModItems.DARK_STEEL_PLATING_STAIRS.get());
                output.accept(ModItems.DARK_STEEL_PLATING_SLAB.get());
                output.accept(ModItems.POLISHED_IRON_PLATING.get());
                output.accept(ModItems.POLISHED_IRON_PLATING_STAIRS.get());
                output.accept(ModItems.POLISHED_IRON_PLATING_SLAB.get());
                output.accept(ModItems.TREAD_PLATE.get());
                output.accept(ModItems.TREAD_PLATE_SLAB.get());
                output.accept(ModItems.TREAD_PLATE_STAIRS.get());
                output.accept(ModItems.HAZARD_STRIPES.get());
                output.accept(ModItems.HAZARD_PLATE.get());
                output.accept(ModItems.HAZARD_STRIPES_RED_WHITE.get());
                output.accept(ModItems.HAZARD_PLATE_RED_WHITE.get());
                for (Item item : ModItems.CORRUGATED_SHEETS.items()) output.accept(item);
                output.accept(ModItems.GRATING.get());
                output.accept(ModItems.GRATING_SLAB.get());
                output.accept(ModItems.TRAFFIC_CONE.get());
                output.accept(ModItems.WARNING_FLASHER.get());
                output.accept(ModItems.WARNING_BEACON.get());
                output.accept(ModItems.DELINEATOR.get());
                output.accept(ModItems.BARRIER_BOARD.get());
                output.accept(ModItems.SITE_FENCE.get());
                output.accept(ModItems.CYLINDER_MOUNT.get());
                output.accept(ModItems.HYDRAULIC_CYLINDER.get());
                output.accept(ModItems.OPERATOR_CONSOLE.get());
            })
            .build()
    );

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
