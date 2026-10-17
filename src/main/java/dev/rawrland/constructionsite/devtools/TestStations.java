package dev.rawrland.constructionsite.devtools;

import dev.rawrland.constructionsite.CreateConstructionSite;
import dev.rawrland.constructionsite.compat.Mods;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * Offers the test station data packs (src/main/resources/resourcepacks/test_stations*) in the
 * game started from the development setup. They are turned on by themselves in every world.
 *
 * Only registered when the game is not a finished release (see the mod's entry point), and the
 * files are left out of the finished mod file (build.gradle). The Create Aeronautics stations
 * are only offered when Create Aeronautics is installed: their files name its blocks.
 *
 * Made by tools/teststations/generate_test_stations.py; see docs/notes/test-stations.md.
 */
public final class TestStations {

    private TestStations() {
    }

    public static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }
        offer(event, "test_stations", "Construction Site: test stations");
        if (Mods.AERONAUTICS) {
            offer(event, "test_stations_aeronautics", "Construction Site: Create Aeronautics test stations");
        }
    }

    private static void offer(AddPackFindersEvent event, String folder, String title) {
        event.addPackFinders(
            ResourceLocation.fromNamespaceAndPath(CreateConstructionSite.MODID, "resourcepacks/" + folder),
            PackType.SERVER_DATA,
            Component.literal(title),
            // BUILT_IN: added to worlds by itself, like the game's own built-in packs.
            PackSource.BUILT_IN,
            false,
            Pack.Position.TOP);
    }
}
