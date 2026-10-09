package dev.rawrland.constructionsite;

import com.mojang.logging.LogUtils;
import dev.rawrland.constructionsite.registry.ModBlockEntities;
import dev.rawrland.constructionsite.registry.ModBlocks;
import dev.rawrland.constructionsite.registry.ModCreativeTabs;
import dev.rawrland.constructionsite.registry.ModEntities;
import dev.rawrland.constructionsite.registry.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/** Entry point of the mod. It only wires the registries together. */
@Mod(CreateConstructionSite.MODID)
public class CreateConstructionSite {

    /** Must match the mod id in gradle.properties and neoforge.mods.toml. */
    public static final String MODID = "create_construction_site";

    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateConstructionSite(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModEntities.register(modEventBus);
        ModCreativeTabs.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
