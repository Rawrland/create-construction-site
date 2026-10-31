package dev.rawrland.constructionsite.content.cylinder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.world.level.Level;

/**
 * Finds the other end of a hydraulic cylinder.
 *
 * Both ends carry the same link id. The ends are found by that id, not by their
 * block position: when Create Aeronautics assembles a contraption, every block is
 * moved to a new place (its "plot"), so a stored position would point at nothing
 * afterwards. The id travels with the block entity's saved data, so it survives.
 *
 * One table for the client and one for the server: in single player both run in
 * the same program and must not see each other's block entities.
 */
public final class CylinderLinks {

    private record Key(UUID link, boolean barrelEnd) {
    }

    private static final Map<Key, CylinderMountBlockEntity> CLIENT = new ConcurrentHashMap<>();
    private static final Map<Key, CylinderMountBlockEntity> SERVER = new ConcurrentHashMap<>();

    private CylinderLinks() {
    }

    /** Remembers a linked end that is loaded. Calling it twice does no harm. */
    static void register(CylinderMountBlockEntity end) {
        Level level = end.getLevel();
        UUID link = end.linkId();
        if (level == null || link == null || end.isRemoved()) {
            return;
        }
        table(level).put(new Key(link, end.isBarrelEnd()), end);
    }

    /** Forgets an end, but only if the table still points at this very block entity. */
    static void unregister(CylinderMountBlockEntity end) {
        Level level = end.getLevel();
        UUID link = end.linkId();
        if (level == null || link == null) {
            return;
        }
        // On assembly the moved copy is loaded before the old block is removed.
        // Removing by key alone would forget the new copy.
        table(level).remove(new Key(link, end.isBarrelEnd()), end);
    }

    /** The loaded other end of this end's cylinder, or null if it is not loaded or gone. */
    @Nullable
    static CylinderMountBlockEntity partner(CylinderMountBlockEntity end) {
        Level level = end.getLevel();
        UUID link = end.linkId();
        if (level == null || link == null) {
            return null;
        }
        CylinderMountBlockEntity other = table(level).get(new Key(link, !end.isBarrelEnd()));
        if (other == null || other.isRemoved() || other.getLevel() != level) {
            return null;
        }
        return other;
    }

    private static Map<Key, CylinderMountBlockEntity> table(Level level) {
        return level.isClientSide ? CLIENT : SERVER;
    }
}
