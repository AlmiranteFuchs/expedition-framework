package com.expeditions.core;

/**
 * How far a dimension's campaign has progressed, ordered so
 * {@link Enum#ordinal()} is the progression axis:
 * {@code FRONTIER} (expeditions just opened) → {@code ESTABLISHED}
 * (enough operations completed) → {@code CLEARED} (the dimension is won —
 * the microbiome's shutoff seam reads exactly this).
 *
 * <p>Pure domain; persistence stores the {@link #id} string so renamed
 * constants never corrupt a save.
 */
public enum CampaignState {
    FRONTIER("frontier"),
    ESTABLISHED("established"),
    CLEARED("cleared");

    private final String id;

    CampaignState(String id) {
        this.id = id;
    }

    /** Stable persistence id, e.g. {@code "established"}. */
    public String id() {
        return id;
    }

    /** The state for {@code id}, or null for an unknown/blank id. */
    public static CampaignState byId(String id) {
        for (CampaignState state : values()) {
            if (state.id.equals(id)) {
                return state;
            }
        }
        return null;
    }

    /** Whether this state satisfies a minimum of {@code required}. */
    public boolean atLeast(CampaignState required) {
        return ordinal() >= required.ordinal();
    }
}
