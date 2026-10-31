package dev.rawrland.constructionsite.compat.aeronautics;

import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import javax.annotation.Nullable;

/**
 * A Sable body's mass data (MassData: mass, centre of mass, inertia), or null if it is not
 * usable yet (for example right after assembly).
 *
 * {@code ServerSubLevel.getMassTracker()} was found on 30 October 2026 by searching the class at
 * runtime (the game log said "mass data: method ServerSubLevel.getMassTracker()"); it is called
 * directly now. Only loaded with Create Aeronautics (from AeroCylinderMountBlockEntity).
 */
final class SableMass {

    private SableMass() {
    }

    @Nullable
    static MassData of(ServerSubLevel subLevel) {
        MassData data = subLevel.getMassTracker();
        return data == null || data.isInvalid() ? null : data;
    }
}
