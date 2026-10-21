package dev.reme.worldseam;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeamRenderBudgetTest {
    @Test void expensiveFramesConvergeToReasonableCpuSubmissionCap() {
        var budget=new SeamRenderBudget();
        for(int i=0;i<15;i++) {
            int cap=budget.limit();
            budget.observe(cap,cap * 25_000L); // 25 microseconds per model
        }
        assertTrue(budget.limit()<450,"CPU cap must fall below 450 models");
        assertTrue(budget.limit()>=SeamRenderBudget.MIN_MODELS);
    }
    @Test void cheapFramesRecoverWithoutSuddenCpuSpikes() {
        var budget=new SeamRenderBudget();
        budget.observe(900,60_000_000L);
        int before=budget.limit();
        budget.observe(before,1_000_000L);
        assertTrue(budget.limit()>before);
        assertTrue(budget.limit()<before*1.10);
    }
    @Test void avoidIncreasingBudgetWhenSceneHasFewModels() {
        var budget=new SeamRenderBudget();
        int before=budget.limit();
        budget.observe(12,10_000L);
        assertEquals(before,budget.limit());
    }
    @Test void emptyOrZeroClockSampleDoesNotPoisonState() {
        var budget=new SeamRenderBudget();
        budget.observe(0,Long.MAX_VALUE);
        budget.observe(10,0);
        assertEquals(900,budget.limit());
    }
    @Test void extremelyExpensiveFramesHaveHardLowerBound() {
        var budget=new SeamRenderBudget();
        for(int i=0;i<100;i++)budget.observe(budget.limit(),Long.MAX_VALUE);
        assertEquals(SeamRenderBudget.MIN_MODELS,budget.limit());
        budget.reset();
        assertEquals(900,budget.limit());
    }
    @Test void cheapFramesCannotGrowPastHardUpperBound() {
        var budget=new SeamRenderBudget();
        for(int i=0;i<200;i++)budget.observe(budget.limit(),1L);
        assertEquals(SeamRenderBudget.MAX_MODELS,budget.limit());
    }
}
