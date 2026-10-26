package com.expeditions.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final ExpeditionOptions options;
    private final String portalDimension;
    private ExpeditionStatus status;
    private int completedCheckpoints;
    private boolean rewarded;
    private int tokensAwarded;
    private boolean artifactIssued;
    private DefenseState defense;
    private final Map<String, ObjectiveState> objectiveStates;
    private final Set<String> failedObjectives;
    private final Map<UUID, Integer> securedTokens;

    private ExpeditionRun(UUID runId, UUID playerId, Set<UUID> party, ExpeditionDefinition definition,
            List<Waypoint> checkpoints, Waypoint exit, Waypoint terminal, Waypoint portal,
            Waypoint entry, long startedGameTime, ExpeditionStatus status, int completedCheckpoints,
            boolean rewarded, int tokensAwarded, boolean artifactIssued,
            List<UUID> challengeEntities,
            Map<String, ObjectiveState> objectiveStates, ExpeditionOptions options,
            String portalDimension, DefenseState defense, Set<String> failedObjectives) {
        this(runId, playerId, party, definition, checkpoints, exit, terminal, portal,
                entry, startedGameTime, status, completedCheckpoints, rewarded,
                tokensAwarded, artifactIssued, challengeEntities, objectiveStates,
                options, portalDimension, defense, failedObjectives, Map.of());
    }

    private ExpeditionRun(UUID runId, UUID playerId, Set<UUID> party, ExpeditionDefinition definition,
            List<Waypoint> checkpoints, Waypoint exit, Waypoint terminal, Waypoint portal,
            Waypoint entry, long startedGameTime, ExpeditionStatus status, int completedCheckpoints,
            boolean rewarded, int tokensAwarded, boolean artifactIssued,
            List<UUID> challengeEntities,
            Map<String, ObjectiveState> objectiveStates, ExpeditionOptions options,
            String portalDimension, DefenseState defense, Set<String> failedObjectives,
            Map<UUID, Integer> securedTokens) {
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
        this.artifactIssued = artifactIssued;
        this.challengeEntities = new ArrayList<>(challengeEntities);
        this.options = Objects.requireNonNull(options, "options");
        if (portalDimension == null || portalDimension.isBlank()) {
            throw new IllegalArgumentException("Portal dimension must not be blank");
        }
        this.portalDimension = portalDimension;
        // Every defined objective gets a tracked state; states persisted for
        // ids the definition no longer knows are kept (harmless) so a later
        // reload can re-attach them.
        this.objectiveStates = new LinkedHashMap<>(objectiveStates);
        for (ExpeditionObjective objective : definition.objectives()) {
            this.objectiveStates.putIfAbsent(objective.id(), ObjectiveState.UNSTARTED);
        }
        this.defense = defense;
        this.failedObjectives = new HashSet<>(failedObjectives);
        this.securedTokens = new HashMap<>(securedTokens);
        if (defense != null
                && objective(defense.objectiveId()).kind() != ObjectiveKind.DEFEND_DEVICE) {
            throw new IllegalArgumentException(
                    "Defense state must reference a defend_device objective");
        }
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
        return start(runId, playerId, party, definition, resolvedCheckpoints, resolvedExit,
                terminal, portal, entry, startedGameTime, ExpeditionOptions.DEFAULT,
                definition.dimension());
    }

    /**
     * Starts a run with explicit per-run options and the dimension its
     * launch portal lives in (the terminal's dimension — which may differ
     * from the run's dimension when the player picked a remote expedition).
     */
    public static ExpeditionRun start(UUID runId, UUID playerId, Set<UUID> party,
            ExpeditionDefinition definition, List<Waypoint> resolvedCheckpoints,
            Waypoint resolvedExit, Waypoint terminal, Waypoint portal, Waypoint entry,
            long startedGameTime, ExpeditionOptions options, String portalDimension) {
        if (resolvedCheckpoints.isEmpty()) {
            throw new IllegalArgumentException("Run must contain at least one checkpoint");
        }
        return new ExpeditionRun(runId, playerId, party, definition, resolvedCheckpoints,
                resolvedExit, terminal, portal, entry,
                startedGameTime, ExpeditionStatus.ACTIVE, 0, false, 0, false,
                List.of(), Map.of(), options, portalDimension, null, Set.of());
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
            int tokensAwarded, List<UUID> challengeEntities,
            Map<String, ObjectiveState> objectiveStates) {
        return restore(runId, playerId, party, definition, checkpoints, exit, terminal,
                portal, entry, startedGameTime, status, completedCheckpoints, rewarded,
                tokensAwarded, challengeEntities, objectiveStates,
                ExpeditionOptions.DEFAULT, definition.dimension());
    }

    /**
     * Restores a run with its per-run options and portal dimension (saves
     * written before either existed default to {@link ExpeditionOptions#DEFAULT}
     * and the run's own dimension).
     */
    public static ExpeditionRun restore(UUID runId, UUID playerId, Set<UUID> party,
            ExpeditionDefinition definition,
            List<Waypoint> checkpoints, Waypoint exit, Waypoint terminal, Waypoint portal,
            Waypoint entry, long startedGameTime,
            ExpeditionStatus status, int completedCheckpoints, boolean rewarded,
            int tokensAwarded, List<UUID> challengeEntities,
            Map<String, ObjectiveState> objectiveStates, ExpeditionOptions options,
            String portalDimension) {
        return restore(runId, playerId, party, definition, checkpoints, exit, terminal,
                portal, entry, startedGameTime, status, completedCheckpoints, rewarded,
                tokensAwarded, false, challengeEntities, objectiveStates, options,
                portalDimension);
    }

    /**
     * Restores a run with its artifact-issue latch (saves written before
     * artifacts existed default to not-issued).
     */
    public static ExpeditionRun restore(UUID runId, UUID playerId, Set<UUID> party,
            ExpeditionDefinition definition,
            List<Waypoint> checkpoints, Waypoint exit, Waypoint terminal, Waypoint portal,
            Waypoint entry, long startedGameTime,
            ExpeditionStatus status, int completedCheckpoints, boolean rewarded,
            int tokensAwarded, boolean artifactIssued, List<UUID> challengeEntities,
            Map<String, ObjectiveState> objectiveStates, ExpeditionOptions options,
            String portalDimension) {
        return restore(runId, playerId, party, definition, checkpoints, exit, terminal,
                portal, entry, startedGameTime, status, completedCheckpoints, rewarded,
                tokensAwarded, artifactIssued, challengeEntities, objectiveStates, options,
                portalDimension, null, Set.of());
    }

    /**
     * Restores a run with its defense state and failed objectives (saves
     * written before sieges existed default to none).
     */
    public static ExpeditionRun restore(UUID runId, UUID playerId, Set<UUID> party,
            ExpeditionDefinition definition,
            List<Waypoint> checkpoints, Waypoint exit, Waypoint terminal, Waypoint portal,
            Waypoint entry, long startedGameTime,
            ExpeditionStatus status, int completedCheckpoints, boolean rewarded,
            int tokensAwarded, boolean artifactIssued, List<UUID> challengeEntities,
            Map<String, ObjectiveState> objectiveStates, ExpeditionOptions options,
            String portalDimension, DefenseState defense, Set<String> failedObjectives) {
        return restore(runId, playerId, party, definition, checkpoints, exit, terminal,
                portal, entry, startedGameTime, status, completedCheckpoints, rewarded,
                tokensAwarded, artifactIssued, challengeEntities, objectiveStates, options,
                portalDimension, defense, failedObjectives, Map.of());
    }

    /**
     * Restores a run with its per-member secured token credits (saves
     * written before checkpoint crediting existed default to none — those
     * runs simply top up to the full share at completion).
     */
    public static ExpeditionRun restore(UUID runId, UUID playerId, Set<UUID> party,
            ExpeditionDefinition definition,
            List<Waypoint> checkpoints, Waypoint exit, Waypoint terminal, Waypoint portal,
            Waypoint entry, long startedGameTime,
            ExpeditionStatus status, int completedCheckpoints, boolean rewarded,
            int tokensAwarded, boolean artifactIssued, List<UUID> challengeEntities,
            Map<String, ObjectiveState> objectiveStates, ExpeditionOptions options,
            String portalDimension, DefenseState defense, Set<String> failedObjectives,
            Map<UUID, Integer> securedTokens) {
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
                artifactIssued, challengeEntities, objectiveStates, options, portalDimension,
                defense, failedObjectives, securedTokens);
    }

    /** Convenience restore for saves written before objective tracking existed. */
    public static ExpeditionRun restore(UUID runId, UUID playerId, Set<UUID> party,
            ExpeditionDefinition definition,
            List<Waypoint> checkpoints, Waypoint exit, Waypoint terminal, Waypoint portal,
            Waypoint entry, long startedGameTime,
            ExpeditionStatus status, int completedCheckpoints, boolean rewarded,
            int tokensAwarded, List<UUID> challengeEntities) {
        return restore(runId, playerId, party, definition, checkpoints, exit, terminal,
                portal, entry, startedGameTime, status, completedCheckpoints, rewarded,
                tokensAwarded, challengeEntities, Map.of());
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

    /** Per-run modifiers chosen at the terminal (default = none). */
    public ExpeditionOptions options() {
        return options;
    }

    /**
     * Dimension id of the launch terminal and portal — the run's home
     * dimension. Equals {@code definition().dimension()} except for
     * expeditions started for a remote dimension.
     */
    public String portalDimension() {
        return portalDimension;
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
     * @return objectives completed by reaching this checkpoint
     * @throws IllegalStateException if the run is not active or the position is
     *         not the expected next checkpoint
     */
    public List<ExpeditionObjective> activateCheckpoint(Waypoint position) {
        ensureActive();
        Waypoint expected = nextCheckpoint().orElseThrow(
                () -> new IllegalStateException("Run " + runId + " has no checkpoints left"));
        if (!expected.equals(position)) {
            throw new IllegalStateException(
                    "Run " + runId + " expected checkpoint at " + expected + " but got " + position);
        }
        completedCheckpoints++;
        return completeVisitObjectives(completedCheckpoints);
    }

    /**
     * Whether the run's mission artifact has already been issued at its
     * checkpoint — a persisted latch so the item exists exactly once per run.
     */
    public boolean artifactIssued() {
        return artifactIssued;
    }

    /**
     * Records that the artifact was issued (idempotent).
     *
     * @throws IllegalStateException if the run is not active
     */
    public void markArtifactIssued() {
        ensureActive();
        artifactIssued = true;
    }

    /**
     * Delivers a {@link ObjectiveKind#RECOVER_ARTIFACT} objective: the
     * caller has already verified a member physically holds the run-bound
     * item, so this latches the objective completed. Delivery re-evaluates
     * like timed objectives — a refused completion retried without the item
     * must not leave the objective complete, so a negative call unlatches it.
     *
     * @return whether this call newly completed the objective
     * @throws IllegalStateException    if the run is not active
     * @throws IllegalArgumentException if the objective is unknown or not an
     *         artifact objective
     */
    public boolean deliverArtifact(String objectiveId, boolean delivered) {
        ensureActive();
        ExpeditionObjective objective = objective(objectiveId);
        if (objective.kind() != ObjectiveKind.RECOVER_ARTIFACT) {
            throw new IllegalArgumentException(
                    "Objective \"" + objectiveId + "\" is not a recover_artifact objective");
        }
        boolean was = state(objectiveId).completed();
        objectiveStates.put(objectiveId, new ObjectiveState(delivered ? 1 : 0, delivered));
        return delivered && !was;
    }

    // ---- Defense events ----------------------------------------------------

    /** The active defense siege, if one is running (transient, never on a restored terminal run). */
    public Optional<DefenseState> defense() {
        return Optional.ofNullable(defense);
    }

    /** Ids of objectives permanently failed by the run (e.g. a destroyed device). */
    public Set<String> failedObjectiveIds() {
        return Collections.unmodifiableSet(failedObjectives);
    }

    /** Whether objective {@code id} was permanently failed by an event. */
    public boolean objectiveFailed(String objectiveId) {
        return failedObjectives.contains(objectiveId);
    }

    /**
     * Starts the siege for a {@link ObjectiveKind#DEFEND_DEVICE} objective:
     * the device stands at {@code device} with {@code maxHp} health, the
     * siege ends {@code durationTicks} from {@code now}, and waves may spawn
     * every {@code waveIntervalTicks}. Only one siege runs at a time — a
     * definition declaring several declares them at different checkpoints.
     *
     * @throws IllegalStateException if the run is not active or a siege is
     *         already running
     * @throws IllegalArgumentException if the objective is unknown, not a
     *         defense objective, or already resolved
     */
    public void startDefense(String objectiveId, Waypoint device, int maxHp,
            long now, long durationTicks, long waveIntervalTicks) {
        ensureActive();
        if (defense != null) {
            throw new IllegalStateException("A defense event is already active");
        }
        ExpeditionObjective objective = objective(objectiveId);
        if (objective.kind() != ObjectiveKind.DEFEND_DEVICE) {
            throw new IllegalArgumentException(
                    "Objective \"" + objectiveId + "\" is not a defend_device objective");
        }
        if (failedObjectives.contains(objectiveId) || state(objectiveId).completed()) {
            throw new IllegalArgumentException(
                    "Objective \"" + objectiveId + "\" is already resolved");
        }
        defense = new DefenseState(objectiveId, device, maxHp, maxHp,
                now + durationTicks, now + waveIntervalTicks);
    }

    /**
     * Chips the device by {@code amount} health. When the device falls, the
     * siege ends and its objective is permanently marked failed — the caller
     * decides whether that objective's {@code required} flag fails the run.
     *
     * @return true exactly once: the call that destroyed the device
     */
    public boolean damageDevice(int amount) {
        ensureActive();
        if (defense == null || amount < 1) {
            return false;
        }
        defense = defense.damaged(amount);
        if (defense.hp() > 0) {
            return false;
        }
        failedObjectives.add(defense.objectiveId());
        defense = null;
        return true;
    }

    /**
     * Completes the defense objective when the siege timer has expired with
     * the device standing — the only kind that resolves mid-run rather than
     * at the exit.
     *
     * @return the completed objective, or empty when the siege is still running
     */
    public Optional<ExpeditionObjective> completeDefenseIfExpired(long now) {
        ensureActive();
        if (defense == null || !defense.expired(now)) {
            return Optional.empty();
        }
        ExpeditionObjective objective = objective(defense.objectiveId());
        objectiveStates.put(objective.id(), new ObjectiveState(1, true));
        defense = null;
        return Optional.of(objective);
    }

    /** Whether another siege wave may spawn at {@code now}. */
    public boolean defenseWaveDue(long now) {
        return defense != null && defense.waveDue(now);
    }

    /** Marks a wave as spawned; the next one may spawn at {@code nextTick}. */
    public void defenseWaveSpawned(long nextTick) {
        if (defense != null) {
            defense = defense.nextWaveAt(nextTick);
        }
    }

    /**
     * Objective states keyed by objective id (unmodifiable, definition
     * order). Every objective declared by the definition has an entry even
     * if it has never progressed.
     */
    public Map<String, ObjectiveState> objectiveStates() {
        return Collections.unmodifiableMap(objectiveStates);
    }

    /** The tracked state of objective {@code id}, if the definition knows it. */
    public Optional<ObjectiveState> objectiveState(String objectiveId) {
        return Optional.ofNullable(objectiveStates.get(objectiveId));
    }

    /**
     * Ids of objectives already completed. Every kind latches except
     * {@link ObjectiveKind#RECOVER_ARTIFACT}: its completion mirrors
     * physical possession at the exit, so losing the artifact unlatches it.
     */
    public Set<String> completedObjectiveIds() {
        Set<String> completed = new HashSet<>();
        objectiveStates.forEach((id, state) -> {
            if (state.completed()) {
                completed.add(id);
            }
        });
        return completed;
    }

    /**
     * Required objectives that are not completed yet. A non-empty result
     * keeps {@link #complete()} from succeeding.
     */
    public List<ExpeditionObjective> missingRequiredObjectives() {
        List<ExpeditionObjective> missing = new ArrayList<>();
        for (ExpeditionObjective objective : definition.objectives()) {
            if (objective.required() && !state(objective.id()).completed()) {
                missing.add(objective);
            }
        }
        return missing;
    }

    /**
     * Adds collected progress to a {@link ObjectiveKind#COLLECT_ITEMS}
     * objective (server-side pickup events pass the stack size). Progress
     * clamps at the target and completion latches — calls on an already
     * completed objective are no-ops.
     *
     * @return whether this call completed the objective
     * @throws IllegalStateException    if the run is not active
     * @throws IllegalArgumentException if the objective is unknown, not a
     *         collect objective, or {@code amount} is negative
     */
    public boolean addObjectiveProgress(String objectiveId, int amount) {
        ensureActive();
        if (amount < 0) {
            throw new IllegalArgumentException("Objective progress must be >= 0");
        }
        ExpeditionObjective objective = objective(objectiveId);
        if (objective.kind() != ObjectiveKind.COLLECT_ITEMS) {
            throw new IllegalArgumentException(
                    "Objective \"" + objectiveId + "\" is not a collect_items objective");
        }
        ObjectiveState state = state(objectiveId);
        if (state.completed()) {
            return false;
        }
        int next = state.progress() + amount;
        boolean done = next >= objective.progressTarget();
        objectiveStates.put(objectiveId,
                new ObjectiveState(Math.min(next, objective.progressTarget()), done));
        return done;
    }

    /**
     * Evaluates {@link ObjectiveKind#TIME_LIMIT} objectives against
     * {@code elapsedTicks}: every timed objective's state is replaced by the
     * latest evaluation — the elapsed time as progress, completed iff within
     * the limit. Unlike collect/visit objectives this does NOT latch: a
     * refused completion (e.g. an unfinished required objective) may be
     * retried much later, and the bonus must reflect the elapsed time at the
     * completion that actually succeeds.
     *
     * @return the objectives this evaluation newly completed (false→true
     *         transitions, for player feedback)
     * @throws IllegalStateException if the run is not active
     */
    public List<ExpeditionObjective> evaluateTimedObjectives(long elapsedTicks) {
        ensureActive();
        List<ExpeditionObjective> done = new ArrayList<>();
        for (ExpeditionObjective objective : definition.objectives()) {
            if (objective.kind() != ObjectiveKind.TIME_LIMIT) {
                continue;
            }
            boolean was = state(objective.id()).completed();
            int progress = elapsedTicks > 0
                    ? (int) Math.min(elapsedTicks, Integer.MAX_VALUE)
                    : 0;
            boolean completed = elapsedTicks >= 0 && elapsedTicks <= objective.timeLimitTicks();
            objectiveStates.put(objective.id(), new ObjectiveState(progress, completed));
            if (completed && !was) {
                done.add(objective);
            }
        }
        return done;
    }

    /** Completes {@link ObjectiveKind#VISIT_CHECKPOINT} objectives targeting {@code index}. */
    private List<ExpeditionObjective> completeVisitObjectives(int index) {
        List<ExpeditionObjective> done = new ArrayList<>();
        for (ExpeditionObjective objective : definition.objectives()) {
            if (objective.kind() == ObjectiveKind.VISIT_CHECKPOINT
                    && objective.checkpoint() == index
                    && !state(objective.id()).completed()) {
                objectiveStates.put(objective.id(), new ObjectiveState(1, true));
                done.add(objective);
            }
        }
        return done;
    }

    private ExpeditionObjective objective(String objectiveId) {
        for (ExpeditionObjective objective : definition.objectives()) {
            if (objective.id().equals(objectiveId)) {
                return objective;
            }
        }
        throw new IllegalArgumentException(
                "Run " + runId + " has no objective \"" + objectiveId + "\"");
    }

    private ObjectiveState state(String objectiveId) {
        return objectiveStates.getOrDefault(objectiveId, ObjectiveState.UNSTARTED);
    }

    /**
     * Marks the run completed. Requires an active run with all checkpoints
     * done and every required objective completed (timed objectives are
     * evaluated by the caller first via {@link #evaluateTimedObjectives}).
     *
     * @throws IllegalStateException otherwise
     */
    public void complete() {
        ensureActive();
        if (!allCheckpointsDone()) {
            throw new IllegalStateException("Run " + runId + " still has "
                    + (checkpoints.size() - completedCheckpoints) + " checkpoints remaining");
        }
        List<ExpeditionObjective> missing = missingRequiredObjectives();
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Run " + runId + " has unfinished required objectives: "
                            + missing.stream().map(ExpeditionObjective::id)
                                    .collect(Collectors.joining(", ")));
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

    /**
     * The per-member award value recorded by {@link #markRewarded(int)}:
     * the full completion share including tokens members already banked at
     * checkpoints. 0 if not rewarded.
     */
    public int tokensAwarded() {
        return tokensAwarded;
    }

    /**
     * Guaranteed tokens already banked for {@code memberId} by checkpoint
     * activations — paid out immediately, so they survive failure, death
     * and restarts. 0 for members who banked nothing (or late joiners).
     */
    public int securedTokens(UUID memberId) {
        return securedTokens.getOrDefault(memberId, 0);
    }

    /** Per-member secured totals (unmodifiable); may include ex-members. */
    public Map<UUID, Integer> securedTokens() {
        return Collections.unmodifiableMap(securedTokens);
    }

    /**
     * Records {@code tokens} banked for {@code memberId} — the manager has
     * already paid them into the ledger; this only keeps the run's books so
     * completion can top up instead of double-paying.
     *
     * @throws IllegalStateException    if the run is not active
     * @throws IllegalArgumentException if the member is not in the party or
     *                                  the amount is negative
     */
    public void secureTokens(UUID memberId, int tokens) {
        ensureActive();
        if (!party.contains(memberId)) {
            throw new IllegalArgumentException(
                    "Cannot secure tokens for non-member " + memberId);
        }
        if (tokens < 0) {
            throw new IllegalArgumentException("Token amount must be >= 0");
        }
        securedTokens.merge(memberId, tokens, Integer::sum);
    }

    /**
     * The guaranteed-token portion credited when the checkpoint with
     * 1-based {@code checkpointIndex} activates: an even split of the
     * definition's base {@code tokens} over the resolved route, balanced so
     * the portions sum exactly to the total
     * ({@code tokens * i / n - tokens * (i-1) / n}).
     */
    public int checkpointTokenPortion(int checkpointIndex) {
        int n = checkpoints.size();
        if (checkpointIndex < 1 || checkpointIndex > n) {
            throw new IllegalArgumentException(
                    "Checkpoint index " + checkpointIndex + " out of range 1.." + n);
        }
        int total = definition.tokenReward();
        return total * checkpointIndex / n - total * (checkpointIndex - 1) / n;
    }

    /**
     * Records that the completion award of {@code tokens} per member has been
     * settled — the full share value; part of it may already have been paid
     * out as checkpoint credits (see {@link #securedTokens()}). Valid
     * exactly once, and only on a completed run.
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
