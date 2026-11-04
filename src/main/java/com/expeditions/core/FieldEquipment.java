package com.expeditions.core;

/** Shared field-kit budgets; no game state or client authority. */
public final class FieldEquipment {
    public static final int CELL_CAPACITY = 16000;
    public static final int TRANSFER = 1000;
    public static final int WARD_COST = 4000;
    public static final int WARD_TICKS = 2400;
    public static final int WARD_RADIUS = 8;
    public static final int READING_COST = 200;
    private FieldEquipment() { }
    public static int stored(int value) { return Math.clamp(value, 0, CELL_CAPACITY); }
    public static int transfer(int stored, int requested, boolean receive) {
        return Math.min(Math.max(0, requested), Math.min(TRANSFER,
                receive ? CELL_CAPACITY - stored(stored) : stored(stored)));
    }
}
