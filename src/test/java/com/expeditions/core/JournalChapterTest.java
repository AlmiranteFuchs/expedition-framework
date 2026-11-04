package com.expeditions.core;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class JournalChapterTest {
    @Test
    void aFreshGuideExplainsHowToBeginWithoutRevealingDiscoveries() {
        for (var chapter : JournalChapter.values()) assertEquals(chapter == JournalChapter.FIRST_STEPS, chapter.known(0));
    }

    @Test
    void discoveryNeverErasesAnotherEntryAndIsIdempotent() {
        int known = JournalChapter.BLOOM.unlock(0);
        known = JournalChapter.CARGO.unlock(known);
        assertTrue(JournalChapter.BLOOM.known(known));
        assertTrue(JournalChapter.CARGO.known(known));
        assertFalse(JournalChapter.LIBERATION.known(known));
        assertEquals(known, JournalChapter.CARGO.unlock(known));
    }

    @Test
    void savedBitPositionsRemainCompatible() {
        assertTrue(JournalChapter.BLOOM.known(2));
        assertTrue(JournalChapter.BLOOD.known(256));
        assertTrue(JournalChapter.LIBERATION.known(2048));
        assertFalse(JournalChapter.DEFENSE.known(256));
    }
}
