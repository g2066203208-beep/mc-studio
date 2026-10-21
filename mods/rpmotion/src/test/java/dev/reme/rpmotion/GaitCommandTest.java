package dev.reme.rpmotion;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class GaitCommandTest {
    @Test void refusesFakeJointAndUnsafeCommands(){
        assertThrows(IllegalArgumentException.class,()->new GaitCommand("FAKE.LEG",0,5,1));
        assertThrows(IllegalArgumentException.class,()->new GaitCommand("LF.HFE",Double.NaN,5,1));
        assertThrows(IllegalArgumentException.class,()->new GaitCommand("LF.HFE",0,Double.POSITIVE_INFINITY,1));
        assertThrows(IllegalArgumentException.class,()->new GaitCommand("LF.HFE",0,-1,1));
        assertFalse(new GaitCommand("LF.KFE",20,15,2).fresh(3));
        assertTrue(new GaitCommand("LF.KFE",20,15,2).fresh(2));
    }
}
