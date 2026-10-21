package dev.reme.worldseam;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeamRenderSamplesTest {
    @Test void sampleWindowKeepsOnlyRecentFrames() {
        var samples=new SeamRenderSamples(4);
        samples.add(1_000_000);
        samples.add(2_000_000);
        samples.add(3_000_000);
        samples.add(4_000_000);
        assertEquals(4,samples.p95Ms());
        samples.add(5_000_000);
        assertEquals(5,samples.p95Ms());
        assertEquals(4,samples.count());
    }
    @Test void emptyAfterClearAndValidZeroSample() {
        var x=new SeamRenderSamples(3);
        assertEquals(0,x.p95Ms());
        x.add(0);
        assertEquals(0,x.p95Ms());
        x.add(2_000_000);
        x.clear();
        assertEquals(0,x.count());
        assertEquals(0,x.p95Ms());
    }
    @Test void rejectInvalidCapacityAndNegativeTime() {
        assertThrows(IllegalArgumentException.class,()->new SeamRenderSamples(0));
        var x=new SeamRenderSamples(2);
        assertThrows(IllegalArgumentException.class,()->x.add(-1));
    }
}
