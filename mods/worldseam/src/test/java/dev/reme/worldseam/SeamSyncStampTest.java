package dev.reme.worldseam;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeamSyncStampTest {
    @Test void enableAndDisableAlwaysChangeTheWireStamp() {
        var off=new SeamSyncStamp(7,false);
        var on=new SeamSyncStamp(7,true);
        assertNotEquals(off,on);
        assertNotEquals(on,off);
        assertEquals(off,new SeamSyncStamp(7,false));
    }

    @Test void dimensionStackRevisionChangesWhileEnabled() {
        assertNotEquals(new SeamSyncStamp(7,true),new SeamSyncStamp(8,true));
    }

    @Test void repeatedUnchangedTickDoesNotChangeStamp() {
        assertEquals(new SeamSyncStamp(0,false),new SeamSyncStamp(0,false));
    }
}
