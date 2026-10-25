package dev.reme.worldseam;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SeamRemoteEntityOwnershipTest {
    @Test void nativeUuidWinsEvenWhenPacketUsesDifferentNumericId() {
        UUID vanilla=UUID.randomUUID();
        var resident=new HashSet<UUID>();
        resident.add(vanilla);
        assertFalse(SeamRemoteEntityOwnership.mayCreate(resident,vanilla));
        UUID other=UUID.randomUUID();
        assertTrue(SeamRemoteEntityOwnership.mayCreate(resident,other));
        resident.add(other);
        assertFalse(SeamRemoteEntityOwnership.mayCreate(resident,other));
        assertFalse(SeamRemoteEntityOwnership.mayCreate(resident,null));
    }

    @Test void sameUuidAndIdCannotGrantOwnershipOfReplacementVanillaInstance() {
        var owned=new HashMap<Integer,Object>();
        Object snapshot=new String("same UUID and ID");
        Object vanilla=new String("same UUID and ID");
        owned.put(42,snapshot);
        assertEquals(snapshot,vanilla);
        assertTrue(SeamRemoteEntityOwnership.owns(owned,42,snapshot));
        assertFalse(SeamRemoteEntityOwnership.owns(owned,42,vanilla));
        assertFalse(SeamRemoteEntityOwnership.owns(owned,43,snapshot));
        assertFalse(SeamRemoteEntityOwnership.owns(owned,42,null));
        owned.clear();
        assertFalse(SeamRemoteEntityOwnership.owns(owned,42,snapshot));
    }
}
