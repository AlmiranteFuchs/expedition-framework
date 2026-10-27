package com.expeditions.core;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable description of an expedition: which dimension it lives in, its
 * route, difficulty and rewards. Kept free of Minecraft types so it can be
 * sourced from config (and later datapacks) and tested in isolation.
 *
 * @param id             stable identifier, e.g. {@code "expeditions:overworld_trial"}
 * @param displayName    human-readable name shown to players
 * @param difficulty     non-negative difficulty rating used by future reward scaling
 * @param dimension      dimension id the route resolves in, e.g.
 *                       {@code "minecraft:overworld"}
 * @param route          the route spec: an explicit {@link ExpeditionRoute} or
 *                       {@link RouteGeneration} parameters
 * @param tokenReward    tokens granted exactly once on successful completion
 * @param bonusTokens    extra tokens for finishing within {@code bonusTimeTicks};
 *                       0 disables the time bonus
 * @param bonusTimeTicks elapsed-tick budget for the bonus; 0 disables it
 * @param objectives     optional/required objectives paid as per-objective
 *                       bonuses on successful completion
 * @param modifiers      environmental/combat modifiers applied while the run
 *                       is active (weather, member effects, horde buffs)
 * @param requires       unlock keys the player must hold to start
 * @param grants         unlock keys awarded to every member on completion
 * @param costTokens     tokens charged to the starting player at run start
 */
public record ExpeditionDefinition(String id, String displayName, int difficulty,
        String dimension, RouteSpec route, int tokenReward,
        int bonusTokens, int bonusTimeTicks, List<ExpeditionObjective> objectives,
        ExpeditionModifiers modifiers, Set<String> requires, Set<String> grants,
        int costTokens) {

    /** Definition without progression fields (config defaults, older saves). */
    public ExpeditionDefinition(String id, String displayName, int difficulty,
            String dimension, RouteSpec route, int tokenReward,
            int bonusTokens, int bonusTimeTicks, List<ExpeditionObjective> objectives,
            ExpeditionModifiers modifiers) {
        this(id, displayName, difficulty, dimension, route, tokenReward,
                bonusTokens, bonusTimeTicks, objectives, modifiers,
                Set.of(), Set.of(), 0);
    }

    /** Definition without modifiers (config defaults, older saves). */
    public ExpeditionDefinition(String id, String displayName, int difficulty,
            String dimension, RouteSpec route, int tokenReward,
            int bonusTokens, int bonusTimeTicks, List<ExpeditionObjective> objectives) {
        this(id, displayName, difficulty, dimension, route, tokenReward,
                bonusTokens, bonusTimeTicks, objectives, ExpeditionModifiers.NONE);
    }

    /** Definition without objectives (config defaults, saves from before objectives existed). */
    public ExpeditionDefinition(String id, String displayName, int difficulty,
            String dimension, RouteSpec route, int tokenReward,
            int bonusTokens, int bonusTimeTicks) {
        this(id, displayName, difficulty, dimension, route, tokenReward,
                bonusTokens, bonusTimeTicks, List.of());
    }

    /** Definition without a time bonus, in the Overworld. */
    public ExpeditionDefinition(String id, String displayName, int difficulty,
            ExpeditionRoute route, int tokenReward) {
        this(id, displayName, difficulty, "minecraft:overworld", route, tokenReward, 0, 0);
    }

    public ExpeditionDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(route, "route");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Expedition id must not be blank");
        }
        if (dimension.isBlank()) {
            throw new IllegalArgumentException("Dimension must not be blank");
        }
        if (difficulty < 0) {
            throw new IllegalArgumentException("Difficulty must be >= 0");
        }
        if (tokenReward < 0 || bonusTokens < 0) {
            throw new IllegalArgumentException("Rewards must be >= 0");
        }
        if (bonusTimeTicks < 0) {
            throw new IllegalArgumentException("Bonus time must be >= 0");
        }
        objectives = List.copyOf(objectives);
        Objects.requireNonNull(modifiers, "modifiers");
        if (costTokens < 0) {
            throw new IllegalArgumentException("Cost must be >= 0");
        }
        requires = validateKeys(requires, "requires");
        grants = validateKeys(grants, "grants");
        Set<String> seen = new HashSet<>();
        int checkpoints = routeCheckpointCount(route);
        for (ExpeditionObjective objective : objectives) {
            Objects.requireNonNull(objective, "objective");
            if (!seen.add(objective.id())) {
                throw new IllegalArgumentException(
                        "Duplicate objective id \"" + objective.id()
                                + "\" in expedition \"" + id + "\"");
            }
            if ((objective.kind() == ObjectiveKind.VISIT_CHECKPOINT
                    || objective.kind() == ObjectiveKind.RECOVER_ARTIFACT
                    || objective.kind() == ObjectiveKind.DEFEND_DEVICE)
                    && objective.checkpoint() > checkpoints) {
                throw new IllegalArgumentException(
                        objective.kind().name().toLowerCase() + " objective \""
                                + objective.id() + "\" targets checkpoint "
                                + objective.checkpoint()
                                + " but the route has only " + checkpoints);
            }
        }
    }

    /**
     * The {@link ObjectiveKind#RECOVER_ARTIFACT} objective issued at
     * checkpoint {@code index} (1-based), if one targets it. Definitions
     * reject duplicate objective ids, so at most one artifact objective per
     * checkpoint — but nothing prevents several checkpoints from each
     * holding one.
     */
    public java.util.Optional<ExpeditionObjective> artifactObjectiveAt(int index) {
        return objectives.stream()
                .filter(o -> o.kind() == ObjectiveKind.RECOVER_ARTIFACT
                        && o.checkpoint() == index)
                .findFirst();
    }

    /**
     * The {@link ObjectiveKind#DEFEND_DEVICE} objective triggering at
     * checkpoint {@code index} (1-based), if one targets it.
     */
    public java.util.Optional<ExpeditionObjective> defenseObjectiveAt(int index) {
        return objectives.stream()
                .filter(o -> o.kind() == ObjectiveKind.DEFEND_DEVICE
                        && o.checkpoint() == index)
                .findFirst();
    }

    /** Unlock-key lists are copied immutable and must hold non-blank ids. */
    private static Set<String> validateKeys(Set<String> keys, String field) {
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException(field + " keys must not be blank");
            }
        }
        return Set.copyOf(keys);
    }

    private static int routeCheckpointCount(RouteSpec route) {
        return switch (route) {
            case ExpeditionRoute r -> r.checkpoints().size();
            case RouteGeneration g -> g.checkpoints();
        };
    }

    /**
     * Total tokens a run earns when completed in {@code elapsedTicks} ticks:
     * the guaranteed reward plus the time bonus when configured and earned.
     * A negative {@code elapsedTicks} (unknown start time) never earns a bonus.
     */
    public int totalReward(long elapsedTicks) {
        boolean bonusEarned = bonusTokens > 0 && bonusTimeTicks > 0
                && elapsedTicks >= 0 && elapsedTicks <= bonusTimeTicks;
        return tokenReward + (bonusEarned ? bonusTokens : 0);
    }

    /**
     * Total tokens for a run that completed {@code completedObjectiveIds}:
     * {@link #totalReward(long)} plus each completed objective's bonus. Each
     * objective counts at most once and unknown ids are ignored, so the
     * result stays deterministic for any set the caller supplies.
     */
    public int totalReward(long elapsedTicks, Collection<String> completedObjectiveIds) {
        return totalReward(elapsedTicks, completedObjectiveIds, 0);
    }

    /**
     * {@link #totalReward(long, Collection)} with the bonus time window
     * scaled by {@code bonusTimePercent} (a per-run option; 0 uses the
     * definition's window unchanged). Objective bonuses are unaffected —
     * only the time-bonus window stretches or shrinks.
     */
    public int totalReward(long elapsedTicks, Collection<String> completedObjectiveIds,
            int bonusTimePercent) {
        long window = bonusTimePercent > 0
                ? bonusTimeTicks * (long) bonusTimePercent / 100 : bonusTimeTicks;
        boolean bonusEarned = bonusTokens > 0 && window > 0
                && elapsedTicks >= 0 && elapsedTicks <= window;
        int total = tokenReward + (bonusEarned ? bonusTokens : 0);
        Set<String> completed = Set.copyOf(completedObjectiveIds);
        for (ExpeditionObjective objective : objectives) {
            if (completed.contains(objective.id())) {
                total += objective.bonusTokens();
            }
        }
        return total;
    }
}
