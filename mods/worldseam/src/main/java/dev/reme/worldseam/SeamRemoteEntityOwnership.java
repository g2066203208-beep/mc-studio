package dev.reme.worldseam;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Client-only ownership guards for reused foreign ClientLevels.
 * A vanilla entity can have the same UUID/ID as a previous snapshot but
 * must never be removed or mutated by the WorldSeam mirror lifecycle.
 * No server entities or native Sable bodies are created by this class.
 */
final class SeamRemoteEntityOwnership {
    private SeamRemoteEntityOwnership() {}

    static boolean mayCreate(Set<UUID> residentUuids, UUID candidate) {
        return candidate != null && !residentUuids.contains(candidate);
    }

    static <T> boolean owns(Map<Integer,T> owned, int id, T current) {
        return current != null && owned.get(id) == current;
    }
}
