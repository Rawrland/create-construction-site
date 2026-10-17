package dev.rawrland.constructionsite;

import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;
import dev.rawrland.constructionsite.client.BulldozerBladeRenderer;
import dev.rawrland.constructionsite.client.DumpBodyRenderer;
import dev.rawrland.constructionsite.client.ExcavatorBucketRenderer;
import dev.rawrland.constructionsite.client.FallingMaterialRenderer;
import dev.rawrland.constructionsite.client.ToolItemRenderer;
import dev.rawrland.constructionsite.client.ponder.ConstructionSitePonderPlugin;
import dev.rawrland.constructionsite.registry.ModBlockEntities;
import dev.rawrland.constructionsite.registry.ModEntities;
import dev.rawrland.constructionsite.registry.ModItems;
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

        // Create's Ponder scenes for the mod's tools (hold W over an item).
        PonderIndex.addPlugin(new ConstructionSitePonderPlugin());
    }

    private static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        // The bucket and blade items are drawn by the same code as the placed tools (their item
        // models only say "drawn in code" and how to hold them).
        event.registerItem(ToolItemRenderer.extensions(ToolItemRenderer.Tool.BUCKET), ModItems.EXCAVATOR_BUCKET.get());
        event.registerItem(ToolItemRenderer.extensions(ToolItemRenderer.Tool.BLADE), ModItems.BULLDOZER_BLADE.get());
        event.registerItem(ToolItemRenderer.extensions(ToolItemRenderer.Tool.DUMP_BODY), ModItems.DUMP_BODY.get());
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        // Create's "Hold [Shift] for Summary" tooltips, the same way Create registers its own items.
        // The text comes from the language file: the item's name key plus ".tooltip.summary" and so on.
        event.enqueueWork(() -> {
            addCreateTooltip(ModItems.EXCAVATOR_BUCKET.get());
            addCreateTooltip(ModItems.BULLDOZER_BLADE.get());
            addCreateTooltip(ModItems.DUMP_BODY.get());
        });
    }

    private static void addCreateTooltip(Item item) {
        TooltipModifier.REGISTRY.register(item, new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE));
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.EXCAVATOR_BUCKET.get(), ExcavatorBucketRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.BULLDOZER_BLADE.get(), BulldozerBladeRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DUMP_BODY.get(), DumpBodyRenderer::new);
        event.registerEntityRenderer(ModEntities.FALLING_MATERIAL.get(), FallingMaterialRenderer::new);
    }
}
