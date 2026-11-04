package com.expeditions.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrontierProgressionTest {
    @Test
    void masteryRequiresClearedGroundAndPersonalExtractionProof() {
        String dimension = "example:grove/deep";
        var charter = FrontierProgression.mastery(dimension);
        assertEquals("expeditions:mastery/example/grove/deep", charter.id());
        assertTrue(FrontierProgression.isMastery(charter));
        assertFalse(FrontierProgression.isAftergrowth(charter));
        assertEquals(CampaignState.CLEARED, charter.minCampaignState());
        assertTrue(charter.requires().contains(FrontierProgression.EXTRACTION_TRACE_PREFIX + dimension));
        assertTrue(charter.artifactObjectiveAt(2).orElseThrow().required());
        assertTrue(charter.defenseObjectiveAt(3).orElseThrow().required());
    }

    @Test
    void aftergrowthIsASeparateClearedDimensionCharter() {
        var survey = FrontierProgression.aftergrowth("example:grove/deep");
        assertEquals("expeditions:aftergrowth/example/grove/deep", survey.id());
        assertTrue(FrontierProgression.isAftergrowth(survey));
        assertFalse(FrontierProgression.isAftergrowth(FrontierProgression.liberation(survey)));
        assertEquals(CampaignState.CLEARED, survey.minCampaignState());
        assertEquals(2, ((RouteGeneration) survey.route()).checkpoints());
        assertEquals(0, survey.costTokens());
        assertEquals(0, survey.bonusTokens());
    }
}
