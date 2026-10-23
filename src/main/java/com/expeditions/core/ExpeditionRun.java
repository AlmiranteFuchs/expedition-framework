package com.expeditions.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * State of one expedition run shared by a party of players. Lifecycle rules
 * live here rather than in Minecraft code so they can be validated by plain
 * unit tests:
 *
 * <ul>
 *   <li>A new run is {@link ExpeditionStatus#ACTIVE} with zero checkpoints done.</li>
 *   <li>Checkpoints activate strictly in order, matched by resolved position.</li>
 *   <li>While no checkpoint is done the portal is open: players may join the
 *   party and the route may be extended for extra members. The first
 *   checkpoint activation seals both.</li>
 *   <li>An active run may complete only once every checkpoint is done, or fail
 *   at any time. Terminal runs reject every further transition.</li>
 *   <li>The completion reward may be recorded at most once.</li>
 * </ul>
 */
public final class ExpeditionRun {
    /** Sentinel for "start time unknown" (e.g. restored from an older save). */
    public static final long UNKNOWN_START = -1L;

    private final UUID runId;
    private final UUID playerId;
    private final Set<UUID> party;
    private final ExpeditionDefinition definition;
    private final List<Waypoint> checkpoints;
    private final Waypoint exit;
    private final Waypoint terminal;
    private final Waypoint portal;
    private final Waypoint entry;
    private final long startedGameTime;
    private final List<UUID> challengeEntities;
    private ExpeditionStatus status;
    private int completedCheckpoints;
    private boolean rewarded;
    private int tokensAwarded;

    private ExpeditionRun(UUID runId, UUID playerId, Set<UUID> party, ExpeditionDefinition definition,
            List<Waypoint> checkpoints, Waypoint exit, Waypoint terminal, Waypoint portal,
            Waypoint entry, long startedGameTime, ExpeditionStatus status, int completedCheckpoints,
            boolean rewarded, int tokensAwarded, List<UUID> challengeEntities) {
        this.runId = Objects.requireNonNull(runId, "runId");
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.party = new HashSet<>(party);
        if (this.party.isEmpty() || !this.party.contains(playerId)) {
            throw new IllegalArgumentException("Party must contain the owning player");
        }
        this.definition = Objects.requireNonNull(definition, "definition");
        this.checkpoints = new ArrayList<>(checkpoints);
        this.exit = Objects.requireNonNull(exit, "exit");
        this.terminal = Objects.requireNonNull(terminal, "terminal");
        this.portal = Objects.requireNonNull(portal, "portal");
        this.entry = Objects.requireNonNull(entry, "entry");
        this.startedGameTime = startedGameTime;
        this.status = Objects.requireNonNull(status, "status");
        this.completedCheckpoints = completedCheckpoints;
        this.rewarded = rewarded;
        this.tokensAwarded = tokensAwarded;
        this.challengeEntities = new ArrayList<>(challengeEntities);
    }

    /**
     * Starts a fresh run for {@code playerId} over a route already resolved to
     * world positions. {@code terminal}/{@code portal}/{@code entry} record
     * where the run was launched, where its join portal sits, and where the
     * portal delivers members. {@code startedGameTime} is the level's game
     * time at start, used for the optional time bonus.
     */
    public static ExpeditionRun start(UUID runId, UUID playerId, ExpeditionDefinition definition,
            List<Waypoint> resolvedCheckpoints, Waypoint resolvedExit, Waypoint terminal,
            Waypoint portal, Waypoint entry, long startedGameTime) {
        return start(runId, playerId, Set.of(playerId), definition, resolvedCheckpoints,
                resolvedExit, terminal, portal, entry, startedGameTime);
    }

    /** Starts a run shared by a party (party must contain the owning player). */
    public static ExpeditionRun start(UUID runId, UUID playerId, Set<UUID> party,
            ExpeditionDefinition definition, List<Waypoint> resolvedCheckpoints,
            Waypoint resolvedExit, Waypoint terminal, Waypoint portal, Waypoint entry,
            long startedGameTime) {
        if (resolvedCheckpoints.isEmpty()) {
            throw new IllegalArgumentException("Run must contain at least one checkpoint");
        }
        return new ExpeditionRun(runId, playerId, party, definition, resolvedCheckpoints,
                resolvedExit, terminal, portal, entry,
                startedGameTime, ExpeditionStatus.ACTIVE, 0, false, 0, List.of());
    }

    /**
     * Starts a fresh run without portal bookkeeping (unit tests and saves from
     * before portals existed): the exit doubles as terminal/portal and the
     * first checkpoint as entry.
     */
    public static ExpeditionRun start(UUID runId, UUID playerId, ExpeditionDefinition definition,
            List<Waypoint> resolvedCheckpoints, Waypoint resolvedExit, long startedGameTime) {
        return start(runId, playerId, Set.of(playerId), definition, resolvedCheckpoints,
                resolvedExit, resolvedExit, resolvedExit, resolvedCheckpoints.get(0),
                startedGameTime);
    }

    /** Starts a run with unknown timing: never earns a time bonus. */
    public static ExpeditionRun start(UUID runId, UUID playerId, ExpeditionDefinition definition,
            List<Waypoint> resolvedCheckpoints, Waypoint resolvedExit) {
        return start(runId, playerId, definition, resolvedCheckpoints, resolvedExit, UNKNOWN_START);
    }

    /**
     * Restores a run from persisted state. Only used by saved-data loading.
     *
     * @throws IllegalArgumentException if the persisted shape is structurally
     *         invalid (callers treat it as a corrupt record and skip it)
     */
    public static ExpeditionRun restore(UUID runId, UUID playerId, Set<UUID> party,
            ExpeditionDefinition definition,
            List<Waypoint> checkpoints, Waypoint exit, Waypoint terminal, Waypoint portal,
            Waypoint entry, long startedGameTime,
            ExpeditionStatus status, int completedCheckpoints, boolean rewarded,
            int tokensAwarded, List<UUID> challengeEntities) {
        if (checkpoints.isEmpty()) {
            throw new IllegalArgumentException("Restored run must contain at least one checkpoint");
        }
        if (completedCheckpoints < 0 || completedCheckpoints > checkpoints.size()) {
            throw new IllegalArgumentException("Restored checkpoint progress out of range");
        }
        if (rewarded && status != ExpeditionStatus.COMPLETED) {
            throw new IllegalArgumentException("Only completed runs may be marked rewarded");
        }
        return new ExpeditionRun(runId, playerId, party, definition, checkpoints, exit,
                terminal, portal, entry,
                startedGameTime, status, completedCheckpoints, rewarded, tokensAwarded,
                challengeEntities);
    }

    /** Convenience restore for saves written before portals existed. */
    public static ExpeditionRun restore(UUID runId, UUID playerId, Set<UUID> party,
            ExpeditionDefinition definition,
            List<Waypoint> checkpoints, Waypoint exit, long startedGameTime,
            ExpeditionStatus status, int completedCheckpoints, boolean rewarded,
            int tokensAwarded) {
        return restore(runId, playerId, party, definition, checkpoints, exit, exit, exit,
                checkpoints.isEmpty() ? exit : checkpoints.get(0),
                startedGameTime, status, completedCheckpoints, rewarded, tokensAwarded,
                List.of());
    }

    /** Convenience restore for saves written before parties existed. */
    public static ExpeditionRun restore(UUID runId, UUID playerId, ExpeditionDefinition definition,
            List<Waypoint> checkpoints, Waypoint exit, long startedGameTime,
            ExpeditionStatus status, int completedCheckpoints, boolean rewarded,
            int tokensAwarded) {
        return restore(runId, playerId, Set.of(playerId), definition, checkpoints, exit,
                startedGameTime, status, completedCheckpoints, rewarded, tokensAwarded);
    }

    public UUID runId() {
        return runId;
    }

    public UUID playerId() {
        return playerId;
    }

    /**
     * Players participating in this run (unmodifiable). Members join while the
     * portal is open and may leave on death/abandon; the set never becomes
     * empty — the last member failing or leaving fails the run instead.
     */
    public Set<UUID> party() {
        return Collections.unmodifiableSet(party);
    }

    /** Whether {@code playerId} participates in this run. */
    public boolean isMember(UUID playerId) {
        return party.contains(playerId);
    }

    /**
     * Whether the run's portal is open to teleports and new members: active
     * with no checkpoint reached yet. The first activation seals the party.
     */
    public boolean portalOpen() {
        return status == ExpeditionStatus.ACTIVE && completedCheckpoints == 0;
    }

    /**
     * Adds {@code playerId} to the party. Idempotent for existing members.
     *
     * @throws IllegalStateException if the portal has sealed or the run ended
     */
    public void joinParty(UUID playerId) {
        if (party.contains(playerId)) {
            return;
        }
        ensureActive();
        if (!portalOpen()) {
            throw new IllegalStateException(
                    "Run " + runId + " is already underway; the party is sealed");
        }
        party.add(playerId);
    }

    /**
     * Removes {@code playerId} from the party (death or abandon). The last
     * member cannot leave — the manager fails the run instead, keeping the
     * non-empty invariant.
     *
     * @throws IllegalStateException if not active, not a member, or the last member
     */
    public void leaveParty(UUID playerId) {
        ensureActive();
        if (!party.contains(playerId)) {
            throw new IllegalStateException("Player " + playerId + " is not in run " + runId);
        }
        if (party.size() <= 1) {
            throw new IllegalStateException(
                    "The last member cannot leave run " + runId + "; fail it instead");
        }
        party.remove(playerId);
    }

    /**
     * Appends extra checkpoints to the route (party difficulty scaling when a
     * member joins). Only allowed while the portal is open.
     */
    public void extendRoute(List<Waypoint> additionalCheckpoints) {
        ensureActive();
        if (!portalOpen()) {
            throw new IllegalStateException(
                    "Run " + runId + " route cannot change after the first checkpoint");
        }
        checkpoints.addAll(additionalCheckpoints);
    }

    /**
     * Entities this run's checkpoint challenges spawned and still owns
     * (unmodifiable). Persisted so a reload keeps cleanup responsibility: the
     * manager discards them when the next checkpoint is reached or the run
     * ends, so a horde can never outlive its expedition.
     */
    public List<UUID> challengeEntities() {
        return Collections.unmodifiableList(challengeEntities);
    }

    /**
     * Records entities spawned by the challenge that just triggered.
     *
     * @throws IllegalStateException if the run is not active
     */
    public void trackChallengeEntities(Collection<UUID> entities) {
        ensureActive();
        challengeEntities.addAll(entities);
    }

    /**
     * Stops tracking one challenge entity after the manager disposed of it.
     * Entities the cleanup could not reach (e.g. still pending in an unloaded
     * chunk) stay tracked so a later sweep can retry — nothing is forgotten
     * before it is gone.
     */
    public void untrackChallengeEntity(UUID entityId) {
        challengeEntities.remove(entityId);
    }

    public ExpeditionDefinition definition() {
        return definition;
    }

    public ExpeditionStatus status() {
        return status;
    }

    public boolean isActive() {
        return status == ExpeditionStatus.ACTIVE;
    }

    /**
     * Game time at which the run started, or {@value #UNKNOWN_START} when not
     * known. Exposed for persistence; use {@link #elapsedTicks(long)} for rules.
     */
    public long startedGameTime() {
        return startedGameTime;
    }

    /**
     * Ticks elapsed since the run started, or {@value #UNKNOWN_START} when the
     * start time is not known (restored legacy runs never earn a time bonus).
     */
    public long elapsedTicks(long gameTime) {
        return startedGameTime == UNKNOWN_START
                ? UNKNOWN_START
                : Math.max(0, gameTime - startedGameTime);
    }

    /** Resolved checkpoint positions in activation order. */
    public List<Waypoint> checkpoints() {
        return Collections.unmodifiableList(checkpoints);
    }

    /** Resolved exit position. */
    public Waypoint exit() {
        return exit;
    }

    /** The terminal block position this run was launched from. */
    public Waypoint terminal() {
        return terminal;
    }

    /** The portal block position members enter through. */
    public Waypoint portal() {
        return portal;
    }

    /** The position the portal teleports members to. */
    public Waypoint entry() {
        return entry;
    }

    public int completedCheckpoints() {
        return completedCheckpoints;
    }

    public int totalCheckpoints() {
        return checkpoints.size();
    }

    public boolean allCheckpointsDone() {
        return completedCheckpoints == checkpoints.size();
    }

    /** The position of the checkpoint the run expects next, if any remain. */
    public Optional<Waypoint> nextCheckpoint() {
        return completedCheckpoints < checkpoints.size()
                ? Optional.of(checkpoints.get(completedCheckpoints))
                : Optional.empty();
    }

    /**
     * Activates the checkpoint at {@code position}. Only the next expected
     * checkpoint may advance the run: out-of-order, duplicate or foreign
     * positions are rejected. The first activation seals the party.
     *
     * @throws IllegalStateException if the run is not active or the position is
     *         not the expected next checkpoint
     */
    public void activateCheckpoint(Waypoint position) {
        ensureActive();
        Waypoint expected = nextCheckpoint().orElseThrow(
                () -> new IllegalStateException("Run " + runId + " has no checkpoints left"));
        if (!expected.equals(position)) {
            throw new IllegalStateException(
                    "Run " + runId + " expected checkpoint at " + expected + " but got " + position);
        }
        completedCheckpoints++;
    }

    /**
     * Marks the run completed. Requires an active run with all checkpoints done.
     *
     * @throws IllegalStateException otherwise
     */
    public void complete() {
        ensureActive();
        if (!allCheckpointsDone()) {
            throw new IllegalStateException("Run " + runId + " still has "
                    + (checkpoints.size() - completedCheckpoints) + " checkpoints remaining");
        }
        status = ExpeditionStatus.COMPLETED;
    }

    /**
     * Marks the run failed. Only valid while active.
     *
     * @throws IllegalStateException if the run is already in a terminal state
     */
    public void fail() {
        ensureActive();
        status = ExpeditionStatus.FAILED;
    }

    public boolean isRewarded() {
        return rewarded;
    }

    /** Tokens granted per member by {@link #markRewarded(int)}, or 0 if not rewarded. */
    public int tokensAwarded() {
        return tokensAwarded;
    }

    /**
     * Records that the completion reward of {@code tokens} per member has been
     * granted. Valid exactly once, and only on a completed run.
     *
     * @throws IllegalStateException otherwise
     */
    public void markRewarded(int tokens) {
        if (status != ExpeditionStatus.COMPLETED) {
            throw new IllegalStateException("Cannot reward run " + runId + " in state " + status);
        }
        if (rewarded) {
            throw new IllegalStateException("Run " + runId + " was already rewarded");
        }
        if (tokens < 0) {
            throw new IllegalArgumentException("Token amount must be >= 0");
        }
        rewarded = true;
        tokensAwarded = tokens;
    }

    private void ensureActive() {
        if (status != ExpeditionStatus.ACTIVE) {
            throw new IllegalStateException("Run " + runId + " is " + status);
        }
    }
}
