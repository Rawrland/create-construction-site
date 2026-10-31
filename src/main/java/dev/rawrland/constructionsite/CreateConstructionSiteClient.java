package dev.rawrland.constructionsite;

import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;
import dev.rawrland.constructionsite.client.BulldozerBladeRenderer;
import dev.rawrland.constructionsite.client.DumpBodyRenderer;
import dev.rawrland.constructionsite.client.ExcavatorBucketRenderer;
import dev.rawrland.constructionsite.client.FallingMaterialRenderer;
import dev.rawrland.constructionsite.client.HydraulicCylinderRenderer;
import dev.rawrland.constructionsite.client.ToolItemRenderer;
import dev.rawrland.constructionsite.client.ponder.ConstructionSitePonderPlugin;
import dev.rawrland.constructionsite.registry.ModBlockEntities;
import dev.rawrland.constructionsite.registry.ModEntities;
import dev.rawrland.constructionsite.registry.ModItems;
import dev.rawrland.constructionsite.registry.PaintedItems;
import dev.rawrland.constructionsite.client.MetalBlockTextures;
import net.createmod.catnip.lang.FontHelper;
import net.createmod.ponder.foundation.PonderIndex;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.minecraft.world.item.Item;

/** Client-only entry point. This class is never loaded on a dedicated server. */
@Mod(value = CreateConstructionSite.MODID, dist = Dist.CLIENT)
public class CreateConstructionSiteClient {

    public CreateConstructionSiteClient(IEventBus modEventBus, ModContainer container) {
        // Lets players open this mod's config from the Mods screen.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modEventBus.addListener(CreateConstructionSiteClient::registerRenderers);
        modEventBus.addListener(CreateConstructionSiteClient::clientSetup);
        modEventBus.addListener(CreateConstructionSiteClient::registerClientExtensions);
        modEventBus.addListener(CreateConstructionSiteClient::registerBlockColours);
        // Operator Console (add-operator-console): its keys, its window, taking control and scrolling.
        modEventBus.addListener(dev.rawrland.constructionsite.client.OperatorConsoleControls::registerKeys);
        modEventBus.addListener(CreateConstructionSiteClient::registerMenuScreens);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            dev.rawrland.constructionsite.client.OperatorConsoleControls::onClientTick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            dev.rawrland.constructionsite.client.OperatorConsoleControls::onScroll);

        // Create's connected textures for the Painted Metal Blocks: one frame around a whole wall.
        MetalBlockTextures.register();

        // Create's Ponder scenes for the mod's tools (hold W over an item).
        PonderIndex.addPlugin(new ConstructionSitePonderPlugin());
    }

    private static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        // The bucket and blade items are drawn by the same code as the placed tools (their item
        // models only say "drawn in code" and how to hold them).
        event.registerItem(ToolItemRenderer.extensions(ToolItemRenderer.Tool.BUCKET), ModItems.EXCAVATOR_BUCKETS.items());
        event.registerItem(ToolItemRenderer.extensions(ToolItemRenderer.Tool.BLADE), ModItems.BULLDOZER_BLADES.items());
        event.registerItem(ToolItemRenderer.extensions(ToolItemRenderer.Tool.DUMP_BODY), ModItems.DUMP_BODIES.items());
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        // Create's "Hold [Shift] for Summary" tooltips, the same way Create registers its own items.
        // The text comes from the language file: the item's name key plus ".tooltip.summary" and so on.
        event.enqueueWork(() -> {
            // Painted versions share the bare tool's text, as Create's seats do.
            addCreateTooltips(ModItems.EXCAVATOR_BUCKETS);
            addCreateTooltips(ModItems.BULLDOZER_BLADES);
            addCreateTooltips(ModItems.DUMP_BODIES);
            // The site barriers: a short hint at what they can do.
            addCreateTooltip(ModItems.TRAFFIC_CONE.get());
            addCreateTooltip(ModItems.WARNING_FLASHER.get());
            addCreateTooltip(ModItems.WARNING_BEACON.get());
            addCreateTooltip(ModItems.SITE_FENCE.get());
            // Hydraulic cylinder prototype.
            addCreateTooltip(ModItems.CYLINDER_MOUNT.get());
            addCreateTooltip(ModItems.HYDRAULIC_CYLINDER.get());
            addCreateTooltip(ModItems.OPERATOR_CONSOLE.get());
        });
    }

    private static void addCreateTooltips(PaintedItems<?> items) {
        Item bare = items.bare().get();
        for (Item item : items.items()) {
            if (item != bare) {
                ItemDescription.referKey(item, () -> bare);
            }
            addCreateTooltip(item);
        }
    }

    private static void addCreateTooltip(Item item) {
        TooltipModifier.REGISTRY.register(item, new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE));
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.EXCAVATOR_BUCKET.get(), ExcavatorBucketRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.BULLDOZER_BLADE.get(), BulldozerBladeRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DUMP_BODY.get(), DumpBodyRenderer::new);
        event.registerEntityRenderer(ModEntities.FALLING_MATERIAL.get(), FallingMaterialRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CYLINDER_MOUNT.get(), HydraulicCylinderRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.OPERATOR_CONSOLE.get(),
            dev.rawrland.constructionsite.client.OperatorConsoleRenderer::new);
    }

    private static void registerMenuScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(dev.rawrland.constructionsite.registry.ModMenus.OPERATOR_CONSOLE.get(),
            dev.rawrland.constructionsite.client.OperatorConsoleScreen::new);
    }

    /** The site fence's privacy tarp: white wool tinted in the carpet's colour. */
    private static void registerBlockColours(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            net.minecraft.world.item.DyeColor colour =
                state.getValue(dev.rawrland.constructionsite.content.barrier.SiteFenceBlock.TARP).colour();
            return colour == null ? 0xFFFFFFFF : colour.getTextureDiffuseColor();
        }, dev.rawrland.constructionsite.registry.ModBlocks.SITE_FENCE.get());
    }
}
