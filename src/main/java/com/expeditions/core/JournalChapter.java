package com.expeditions.core;

/** Stable discovery bits. Never reorder/reuse bit positions in saved player data. */
public enum JournalChapter {
    FIRST_STEPS(0, "first_steps"), BLOOM(1, "bloom"), ESSENCE(2, "essence"),
    TERMINAL(3, "terminal"), WAYFINDING(4, "wayfinding"), CHECKPOINTS(5, "checkpoints"),
    CARGO(6, "cargo"), DEFENSE(7, "defense"), BLOOD(8, "blood"),
    PRESSURE(9, "pressure"), EXTRACTION(10, "extraction"), LIBERATION(11, "liberation");

    private final int bit;
    private final String id;

    JournalChapter(int position, String id) { this.bit = 1 << position; this.id = id; }
    public String id() { return id; }
    public boolean known(int discoveries) { return this == FIRST_STEPS || (discoveries & bit) != 0; }
    public int unlock(int discoveries) { return discoveries | bit; }
}
