package dev.rawrland.constructionsite;

import com.mojang.logging.LogUtils;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import dev.rawrland.constructionsite.content.dumpbody.DumpBodyMovingInteraction;
import dev.rawrland.constructionsite.content.tool.ToolMovementBehaviour;
import dev.rawrland.constructionsite.devtools.TestStations;
import dev.rawrland.constructionsite.network.ModNetwork;
import dev.rawrland.constructionsite.registry.ModBlockEntities;
import dev.rawrland.constructionsite.registry.ModBlocks;
import dev.rawrland.constructionsite.registry.ModCreativeTabs;
import dev.rawrland.constructionsite.registry.ModEntities;
import dev.rawrland.constructionsite.registry.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/** Entry point of the mod. It wires the registries, the network message and the hooks into Create together. */
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
        modEventBus.addListener(ModNetwork::register);
        modEventBus.addListener(CreateConstructionSite::commonSetup);
        if (!FMLEnvironment.production) {
            // The test stations, only in the game started from the development setup.
            modEventBus.addListener(TestStations::addPackFinders);
        }

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Lets the bucket, blade and dump body work on Create's own contraptions (bearings, pistons, carts, trains).
            ToolMovementBehaviour tools = new ToolMovementBehaviour();
            MovementBehaviour.REGISTRY.register(ModBlocks.EXCAVATOR_BUCKET.get(), tools);
            MovementBehaviour.REGISTRY.register(ModBlocks.BULLDOZER_BLADE.get(), tools);
            MovementBehaviour.REGISTRY.register(ModBlocks.DUMP_BODY.get(), tools);
            // The wrench on a dump body while it moves on one of Create's contraptions.
            MovingInteractionBehaviour.REGISTRY.register(ModBlocks.DUMP_BODY.get(), new DumpBodyMovingInteraction());
        });
    }
}
