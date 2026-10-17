package dev.rawrland.constructionsite.compat;

import net.neoforged.fml.ModList;

/**
 * Which optional mods are installed. Read once, when the mod starts.
 *
 * Code outside the {@code compat.aeronautics} package only ever asks these
 * flags, and then calls into that package. It never names a class of the
 * optional mods itself: Java could then not load that code without them.
 */
public final class Mods {

    /**
     * True if Create Aeronautics is installed. Its "Simulated" part has the ropes
     * and the simulated contraptions, and itself requires Sable, so it stands for both.
     */
    public static final boolean AERONAUTICS = ModList.get().isLoaded("simulated");

    /** True if Create Aeronautics' "Offroad" part is installed: wheel mounts and tires. */
    public static final boolean OFFROAD = ModList.get().isLoaded("offroad");

    private Mods() {
    }
}
