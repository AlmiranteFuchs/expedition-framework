package com.expeditions.core;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExpeditionHistoryTest {
    private static ExpeditionHistory.Entry entry(UUID run, long time, boolean complete, boolean settled) {
        return new ExpeditionHistory.Entry(run,"test:route","Route","test:dimension",
                complete ? ExpeditionStatus.COMPLETED : ExpeditionStatus.FAILED,
                complete ? 3 : 1,3,7,-1,time,"ended",settled);
    }
    @Test void retainsNewestTenAndSeparatesPlayers() {
        var h=new ExpeditionHistory();var player=UUID.randomUUID();
        for(int i=0;i<15;i++) h.record(player,entry(UUID.randomUUID(),i,false,true));
        assertEquals(10,h.entries(player).size());assertEquals(14,h.entries(player).getFirst().endedGameTime());
        assertEquals(5,h.entries(player).getLast().endedGameTime());
        assertTrue(h.entries(UUID.randomUUID()).isEmpty());
        assertThrows(UnsupportedOperationException.class,()->h.entries(player).clear());
    }
    @Test void finalReportsAreIdempotent() {
        var h=new ExpeditionHistory();var player=UUID.randomUUID();var run=UUID.randomUUID();
        assertTrue(h.record(player,entry(run,1,true,true)));
        assertFalse(h.record(player,entry(run,2,false,true)));
        assertEquals(ExpeditionStatus.COMPLETED,h.entries(player).getFirst().outcome());
    }
    @Test void rejoiningReplacesDepartureWithoutDuplicatingRun() {
        var h=new ExpeditionHistory();var player=UUID.randomUUID();var run=UUID.randomUUID();
        h.record(player,entry(run,1,false,false));h.record(player,entry(run,2,true,true));
        assertEquals(1,h.entries(player).size());assertTrue(h.entries(player).getFirst().finalResult());
        assertEquals(ExpeditionStatus.COMPLETED,h.entries(player).getFirst().outcome());
    }
    @Test void rejectsActiveReportsAndImpossibleRewards() {
        assertThrows(IllegalArgumentException.class,()->new ExpeditionHistory.Entry(UUID.randomUUID(),"a","a","a",
                ExpeditionStatus.ACTIVE,0,1,0,0,0,"ended",true));
        assertThrows(IllegalArgumentException.class,()->new ExpeditionHistory.Entry(UUID.randomUUID(),"a","a","a",
                ExpeditionStatus.FAILED,0,1,-1,0,0,"ended",true));
        assertThrows(IllegalArgumentException.class,()->new ExpeditionHistory.Entry(UUID.randomUUID(),"a","a","a",
                ExpeditionStatus.COMPLETED,0,1,0,0,0,"ended",true));
    }
}
