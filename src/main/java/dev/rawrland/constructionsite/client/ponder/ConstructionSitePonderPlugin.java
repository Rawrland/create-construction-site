package dev.rawrland.constructionsite.client.ponder;

import dev.rawrland.constructionsite.CreateConstructionSite;
import dev.rawrland.constructionsite.compat.Mods;
import dev.rawrland.constructionsite.compat.aeronautics.client.BucketRopeScene;
import dev.rawrland.constructionsite.compat.aeronautics.client.SimulatedContraptionScenes;
import dev.rawrland.constructionsite.registry.ModItems;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Tells Create's Ponder which scenes this mod has. Holding W over an item opens its scenes.
 * Added on the client in {@link dev.rawrland.constructionsite.CreateConstructionSiteClient}.
 * See docs/notes/create-ponder.md.
 */
public class ConstructionSitePonderPlugin implements PonderPlugin {

    /** The Ponder index tag that holds the mod's tools. */
    public static final ResourceLocation CONSTRUCTION_TOOLS = id("construction_tools");

    @Override
    public String getModId() {
        return CreateConstructionSite.MODID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PonderSceneRegistrationHelper<DeferredItem<?>> items = helper.withKeyFunction(DeferredItem::getId);

        items.forComponents(ModItems.EXCAVATOR_BUCKET)
            .addStoryBoard(id("excavator_bucket/sizes"), BucketScenes::sizes, CONSTRUCTION_TOOLS)
            .addStoryBoard(id("excavator_bucket/digging"), ContraptionScenes::digging)
            .addStoryBoard(id("excavator_bucket/emptying"), BucketScenes::emptying);

        items.forComponents(ModItems.BULLDOZER_BLADE)
            .addStoryBoard(id("bulldozer_blade/sizes"), BladeScenes::sizes, CONSTRUCTION_TOOLS)
            .addStoryBoard(id("bulldozer_blade/gaps"), ContraptionScenes::gaps)
            .addStoryBoard(id("bulldozer_blade/full"), BladeScenes::full);

        items.forComponents(ModItems.DUMP_BODY)
            .addStoryBoard(id("dump_body/sizes"), DumpBodyScenes::sizes, CONSTRUCTION_TOOLS)
            .addStoryBoard(id("dump_body/loading"), DumpBodyScenes::loading)
            .addStoryBoard(id("dump_body/tipping"), DumpBodyScenes::tipping);

        // Scenes that need Create Aeronautics are only added when it is installed. Their classes
        // live in compat.aeronautics and are only loaded here, inside these checks.
        // First the vehicle scenes (its wheels come from Create Aeronautics' Offroad part),
        // then the bucket's rope scene.
        if (Mods.AERONAUTICS && Mods.OFFROAD) {
            items.forComponents(ModItems.EXCAVATOR_BUCKET)
                .addStoryBoard(id("excavator_bucket/simulated"), SimulatedContraptionScenes::bucket);
            items.forComponents(ModItems.BULLDOZER_BLADE)
                .addStoryBoard(id("bulldozer_blade/simulated"), SimulatedContraptionScenes::blade);
        }
        if (Mods.AERONAUTICS) {
            items.forComponents(ModItems.EXCAVATOR_BUCKET)
                .addStoryBoard(id("excavator_bucket/rope"), BucketRopeScene::rope);
            items.forComponents(ModItems.DUMP_BODY)
                .addStoryBoard(id("dump_body/simulated"), SimulatedContraptionScenes::dumpBody);
        }
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.registerTag(CONSTRUCTION_TOOLS)
            .addToIndex()
            .item(ModItems.EXCAVATOR_BUCKET.get(), true, false)
            .title("Construction Tools")
            .description("Tools that dig, push, carry and tip material when a contraption moves them")
            .register();

        PonderTagRegistrationHelper<DeferredItem<?>> items = helper.withKeyFunction(DeferredItem::getId);
        items.addToTag(CONSTRUCTION_TOOLS)
            .add(ModItems.EXCAVATOR_BUCKET)
            .add(ModItems.BULLDOZER_BLADE)
            .add(ModItems.DUMP_BODY);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(CreateConstructionSite.MODID, path);
    }
}
