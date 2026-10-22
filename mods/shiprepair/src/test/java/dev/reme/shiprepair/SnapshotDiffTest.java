package dev.reme.shiprepair;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SnapshotDiffTest {
    @Test void repairsMissingAndRotatedBeforeRemovingExtraJunk() {
        var edits=SnapshotDiff.plan(
                Map.of(1L,"hull",2L,"stairs[north]",3L,"engine"),
                Map.of(2L,"stairs[south]",3L,"engine",99L,"junk"));
        assertEquals(3,edits.size());
        assertEquals(new SnapshotDiff.Edit<>(1L,null,"hull"),edits.get(0));
        assertEquals(new SnapshotDiff.Edit<>(2L,"stairs[south]","stairs[north]"),edits.get(1));
        assertEquals(new SnapshotDiff.Edit<>(99L,"junk",null),edits.get(2));
    }
    @Test void identicalShipsProduceNoEdits() {
        assertTrue(SnapshotDiff.plan(Map.of(1L,"hull"),Map.of(1L,"hull")).isEmpty());
    }
    @Test void extraBlocksOnlyGetRemovedNotSaved() {
        var plan=SnapshotDiff.plan(Map.of(1L,"hull"),Map.of(1L,"hull",4L,"junk"));
        assertEquals(1,plan.size());
        assertNull(plan.getFirst().after());
    }
}
