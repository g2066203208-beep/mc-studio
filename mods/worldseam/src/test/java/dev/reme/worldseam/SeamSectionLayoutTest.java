package dev.reme.worldseam;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeamSectionLayoutTest {
    @Test void netherRoofMapsOneToOneOntoOverworldFloor() {
        assertEquals(-65,SeamSectionLayout.mappedBlockY(127,-64,128));
        assertEquals(-80,SeamSectionLayout.mappedSectionMinY(127,-64,128));
    }
    @Test void endTopToNetherBottomWorksAtNonVanillaCustomOffset() {
        assertEquals(11,SeamSectionLayout.mappedBlockY(255,11,255));
        assertEquals(-4,SeamSectionLayout.mappedSectionMinY(255,11,255));
    }
    @Test void negativeSectionUsesFloorNotIntegerTruncation() {
        assertEquals(-32,SeamSectionLayout.mappedSectionMinY(-17,0,0));
        assertEquals(-16,SeamSectionLayout.mappedSectionMinY(-1,0,0));
    }
    @Test void entireSixteenBlockSliceEnclosesAllMappedBlocks() {
        for(int nativeY=-64;nativeY<320;nativeY++) {
            int mapped=SeamSectionLayout.mappedBlockY(nativeY,-64,128);
            int minimum=SeamSectionLayout.mappedSectionMinY(nativeY,-64,128);
            assertTrue(mapped>=minimum && mapped<minimum+16,"Y="+nativeY);
        }
    }
    @Test void drawOnlyWhenActuallyNearSourceConnectionPlane() {
        assertTrue(SeamSectionLayout.nearPlane(-63.5,-64,80));
        assertTrue(SeamSectionLayout.nearPlane(16,-64,80));
        assertFalse(SeamSectionLayout.nearPlane(17,-64,80));
        assertFalse(SeamSectionLayout.nearPlane(Double.NaN,-64,80));
        assertFalse(SeamSectionLayout.nearPlane(10,-64,-1));
    }
}
