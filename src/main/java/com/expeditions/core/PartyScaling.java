package com.expeditions.core;

/**
 * Party difficulty/reward scaling. Pure functions so the policy is unit
 * testable; the tuning values come from server config (and, in a future
 * milestone, per-run options chosen at the terminal).
 *
 * <p>Rewards scale up with party size — {@code bonusPerExtraMember} is the
 * fractional bonus added to the total pool for every member beyond the
 * first — and are then distributed either split evenly among the members
 * present at completion or paid in full to each.
 */
public final class PartyScaling {
    private PartyScaling() {
    }

    /**
     * The scaled total reward pool for a party of {@code partySize}:
     * {@code base * (1 + bonusPerExtraMember * (partySize - 1))}, rounded down
     * and never below {@code base}.
     */
    public static int scaledTotal(int baseReward, int partySize, double bonusPerExtraMember) {
        if (partySize <= 1 || bonusPerExtraMember <= 0) {
            return baseReward;
        }
        return (int) Math.floor(baseReward * (1 + bonusPerExtraMember * (partySize - 1)));
    }

    /**
     * What each member receives from {@code scaledTotal}: an even split
     * (rounded down) when {@code split} is set, the full amount otherwise.
     */
    public static int memberShare(int scaledTotal, int partySize, boolean split) {
        return split ? scaledTotal / Math.max(1, partySize) : scaledTotal;
    }
}
