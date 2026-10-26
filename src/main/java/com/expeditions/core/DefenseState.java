package com.expeditions.core;

import java.util.Objects;

/**
 * The live state of a {@code defend_device} siege: which objective it feeds,
 * where the device block stands, its remaining health, when the siege ends,
 * and when the next mob wave may spawn. Immutable — {@link ExpeditionRun}
 * swaps the instance on damage and wave pacing, so the whole state
 * round-trips through one persisted record.
 *
 * <p>Health is the authoritative lifecycle: players cannot break the device
 * block (same protection as every run marker); siege mobs chip it through
 * the manager until {@code hp} reaches zero, which fails the objective. The
 * siege completes when {@code endTick} passes with the device standing.
 *
 * @param objectiveId  the {@link ObjectiveKind#DEFEND_DEVICE} objective this
 *                     siege resolves
 * @param device       world position of the device block (a tracked marker)
 * @param hp           remaining device health — zero means destroyed
 * @param maxHp        health at deployment, for feedback/reporting
 * @param endTick      game time at which the device has survived (inclusive)
 * @param nextWaveTick game time at which the next mob wave may spawn
 */
public record DefenseState(String objectiveId, Waypoint device, int hp, int maxHp,
        long endTick, long nextWaveTick) {

    public DefenseState {
        Objects.requireNonNull(objectiveId, "objectiveId");
        Objects.requireNonNull(device, "device");
        if (maxHp < 1) {
            throw new IllegalArgumentException("Device max hp must be >= 1");
        }
        if (hp < 0 || hp > maxHp) {
            throw new IllegalArgumentException("Device hp out of range");
        }
        if (endTick < 0 || nextWaveTick < 0) {
            throw new IllegalArgumentException("Defense ticks must be >= 0");
        }
    }

    /** A copy with {@code amount} health lost (clamped at zero). */
    public DefenseState damaged(int amount) {
        return new DefenseState(objectiveId, device,
                Math.max(0, hp - amount), maxHp, endTick, nextWaveTick);
    }

    /** A copy whose next wave spawn is pushed to {@code tick}. */
    public DefenseState nextWaveAt(long tick) {
        return new DefenseState(objectiveId, device, hp, maxHp, endTick, tick);
    }

    /** Whether the siege has run its full duration by {@code now}. */
    public boolean expired(long now) {
        return now >= endTick;
    }

    /** Whether another mob wave may spawn at {@code now}. */
    public boolean waveDue(long now) {
        return now >= nextWaveTick;
    }
}
