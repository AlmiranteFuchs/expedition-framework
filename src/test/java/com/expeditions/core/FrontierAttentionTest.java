package com.expeditions.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FrontierAttentionTest {
    @Test void stagesWarnBeforeHuntsAndGuardian() {
        assertEquals(FrontierAttention.Stage.QUIET,FrontierAttention.stage(119,600));
        assertEquals(FrontierAttention.Stage.NOTICED,FrontierAttention.stage(120,600));
        assertEquals(FrontierAttention.Stage.HUNTED,FrontierAttention.stage(240,600));
        assertEquals(FrontierAttention.Stage.OVERRUN,FrontierAttention.stage(420,600));
        assertEquals(FrontierAttention.Stage.RECKONING,FrontierAttention.stage(600,600));
        assertEquals(240,FrontierAttention.nextThreshold(120,600));
    }
    @Test void retreatAndCheckpointReliefLowerDanger() {
        assertEquals(238,FrontierAttention.step(240,-2,600));
        assertEquals(FrontierAttention.Stage.NOTICED,FrontierAttention.stage(FrontierAttention.step(260,-45,600),600));
        assertEquals(0,FrontierAttention.step(30,-45,600));
    }
    @Test void persistedGarbageAndOverflowCannotWrap() {
        assertEquals(600,FrontierAttention.step(Integer.MAX_VALUE,Integer.MAX_VALUE,600));
        assertEquals(0,FrontierAttention.step(-300,-2,600));
        assertThrows(IllegalArgumentException.class,()->FrontierAttention.stage(2,0));
    }
    @Test void generatedLiberationRequiresPreparationAndActualBossProof() {
        var base=new ExpeditionDefinition("test:survey","Survey",0,"test:moon",
                new RouteGeneration(2,32,64,24,48,4,128,256),20,0,0);
        var locked=FrontierProgression.prepare(base,false);
        assertTrue(locked.requires().contains("expeditions:field_clear"));
        assertSame(base,FrontierProgression.prepare(base,true));
        var boss=FrontierProgression.liberation(locked);
        assertTrue(boss.bigBad());assertEquals(CampaignState.ESTABLISHED,boss.minCampaignState());
        assertEquals(3,((RouteGeneration)boss.route()).checkpoints());
        assertEquals("expeditions:liberation/test/moon",boss.id());
        assertEquals(locked.requires(),boss.requires());
    }
}
