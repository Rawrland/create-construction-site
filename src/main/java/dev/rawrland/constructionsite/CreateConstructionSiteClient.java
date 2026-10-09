package dev.rawrland.constructionsite;

import dev.rawrland.constructionsite.client.ExcavatorBucketRenderer;
import dev.rawrland.constructionsite.registry.ModBlockEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client-only entry point. This class is never loaded on a dedicated server. */
@Mod(value = CreateConstructionSite.MODID, dist = Dist.CLIENT)
public class CreateConstructionSiteClient {

    public CreateConstructionSiteClient(IEventBus modEventBus, ModContainer container) {
        // Lets players open this mod's config from the Mods screen.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modEventBus.addListener(CreateConstructionSiteClient::registerRenderers);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.EXCAVATOR_BUCKET.get(), ExcavatorBucketRenderer::new);
    }
}
