package dev.reme.worldseam;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SeamGpuBudgetTest {
    @Test void estimatesRemainBoundedWithoutIntegerOverflow() {
        assertEquals(8192+7680,SeamGpuBudget.estimateBytes(10,2));
        assertThrows(IllegalArgumentException.class,
            ()->SeamGpuBudget.estimateBytes(-1,1));
    }
    @Test void coverageCannotExceedTotalSurface() {
        assertEquals(0,SeamGpuBudget.coverage(0,200));
        assertEquals(1,SeamGpuBudget.coverage(100,1000));
        assertEquals(0.8,SeamGpuBudget.coverage(100,80),1e-8);
    }
    @Test void uploadBudgetAllowsFirstLargeSliceButNotSecondSpike() {
        assertTrue(SeamGpuBudget.shouldCompile(1200,700,0));
        assertFalse(SeamGpuBudget.shouldCompile(1200,700,1));
        assertTrue(SeamGpuBudget.shouldCompile(400,700,1));
    }
}
