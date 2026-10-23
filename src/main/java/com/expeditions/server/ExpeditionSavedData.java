package com.expeditions.server;

import com.expeditions.core.Difficulty;
import com.expeditions.core.ExpeditionDefinition;
import com.expeditions.core.ExpeditionOptions;
import com.expeditions.core.ExpeditionLedger;
import com.expeditions.core.ExpeditionObjective;
import com.expeditions.core.ExpeditionRoute;
import com.expeditions.core.ExpeditionRun;
import com.expeditions.core.ExpeditionStatus;
import com.expeditions.core.ObjectiveKind;
import com.expeditions.core.ObjectiveState;
import com.expeditions.core.RouteAnchor;
import com.expeditions.core.Waypoint;
import com.mojang.logging.LogUtils;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

/**
 * World-level persistence for expedition state, stored in the overworld's
 * {@code DimensionDataStorage} so runs, token balances and live marker
 * positions survive disconnects and restarts. Only serialization lives here;
 * all rules stay in {@link ExpeditionLedger} and {@link ExpeditionRun}.
 *
 * <p>Runs and balances live in one file, so a crash can only roll back both
 * together — a reward and its {@code rewarded} flag can never disagree.
 *
 * <p>Loading is conservative per record: a malformed run or balance entry is
 * skipped and logged instead of failing the whole file and losing every
 * player's state.
 */
public class ExpeditionSavedData extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String DATA_NAME = "expeditions_runs";

    public static final Factory<ExpeditionSavedData> FACTORY = new Factory<>(
            ExpeditionSavedData::create, ExpeditionSavedData::load, null);

    private static final String TAG_RUNS = "Runs";
    private static final String TAG_TOKENS = "Tokens";
    private static final String TAG_MARKERS = "Markers";
    private static final String TAG_PLAYER = "Player";
    private static final String TAG_RUN_ID = "RunId";
    private static final String TAG_PARTY = "Party";
    private static final String TAG_DEFINITION_ID = "DefinitionId";
    private static final String TAG_DEFINITION_NAME = "DefinitionName";
    private static final String TAG_DIFFICULTY = "Difficulty";
    private static final String TAG_DIMENSION = "Dimension";
    private static final String TAG_TOKEN_REWARD = "TokenReward";
    private static final String TAG_BONUS_TOKENS = "BonusTokens";
    private static final String TAG_BONUS_TICKS = "BonusTicks";
    private static final String TAG_STATUS = "Status";
    private static final String TAG_DONE = "Done";
    private static final String TAG_REWARDED = "Rewarded";
    private static final String TAG_TOKENS_AWARDED = "TokensAwarded";
    private static final String TAG_STARTED = "StartedGameTime";
    private static final String TAG_CHECKPOINTS = "Checkpoints";
    private static final String TAG_EXIT = "Exit";
    private static final String TAG_TERMINAL = "Terminal";
    private static final String TAG_PORTAL = "Portal";
    private static final String TAG_ENTRY = "Entry";
    private static final String TAG_CHALLENGE_ENTITIES = "ChallengeEntities";
    private static final String TAG_ENTITY_ID = "EntityId";
    private static final String TAG_OBJECTIVES = "Objectives";
    private static final String TAG_OBJ_ID = "Id";
    private static final String TAG_OBJ_DESCRIPTION = "Description";
    private static final String TAG_OBJ_KIND = "Kind";
    private static final String TAG_OBJ_REQUIRED = "Required";
    private static final String TAG_OBJ_CHECKPOINT = "Checkpoint";
    private static final String TAG_OBJ_ITEM = "Item";
    private static final String TAG_OBJ_TAG = "Tag";
    private static final String TAG_OBJ_COUNT = "Count";
    private static final String TAG_OBJ_LIMIT_TICKS = "LimitTicks";
    private static final String TAG_OBJ_BONUS = "BonusTokens";
    private static final String TAG_OBJ_PROGRESS = "Progress";
    private static final String TAG_OBJ_DONE = "Done";
    private static final String TAG_BALANCE = "Balance";
    private static final String TAG_PORTAL_DIMENSION = "PortalDimension";
    private static final String TAG_OPTIONS = "Options";
    private static final String TAG_OPT_EXPEDITION = "ExpeditionId";
    private static final String TAG_OPT_DIFFICULTY = "Difficulty";
    private static final String TAG_OPT_CHECKPOINTS = "Checkpoints";
    private static final String TAG_OPT_TIME_PERCENT = "TimePercent";
    private static final String TAG_X = "x";
    private static final String TAG_Y = "y";
    private static final String TAG_Z = "z";

    private static final String DEFAULT_DIMENSION = "minecraft:overworld";

    private final ExpeditionLedger ledger = new ExpeditionLedger();
    private final Map<String, Set<BlockPos>> liveMarkers = new HashMap<>();

    public static ExpeditionSavedData create() {
        return new ExpeditionSavedData();
    }

    public static ExpeditionSavedData load(CompoundTag tag, HolderLookup.Provider lookup) {
        ExpeditionSavedData data = create();
        for (Tag t : tag.getList(TAG_RUNS, Tag.TAG_COMPOUND)) {
            try {
                data.ledger.restoreRun(readRun((CompoundTag) t));
            } catch (RuntimeException e) {
                LOGGER.warn("Skipping malformed expedition run record: {}", e.getMessage());
            }
        }
        for (Tag t : tag.getList(TAG_TOKENS, Tag.TAG_COMPOUND)) {
            try {
                CompoundTag tokenTag = (CompoundTag) t;
                data.ledger.restoreTokens(tokenTag.getUUID(TAG_PLAYER),
                        tokenTag.getInt(TAG_BALANCE));
            } catch (RuntimeException e) {
                LOGGER.warn("Skipping malformed token balance record: {}", e.getMessage());
            }
        }
        for (Tag t : tag.getList(TAG_MARKERS, Tag.TAG_COMPOUND)) {
            try {
                CompoundTag markerTag = (CompoundTag) t;
                data.trackMarker(markerTag.getString(TAG_DIMENSION),
                        readPos(markerTag));
            } catch (RuntimeException e) {
                LOGGER.warn("Skipping malformed marker record: {}", e.getMessage());
            }
        }
        return data;
    }

    private static ExpeditionRun readRun(CompoundTag runTag) {
        List<Waypoint> checkpoints = runTag.getList(TAG_CHECKPOINTS, Tag.TAG_COMPOUND)
                .stream().map(e -> readWaypoint((CompoundTag) e)).toList();
        Waypoint exit = readWaypoint(runTag.getCompound(TAG_EXIT));
        // Rebuild the definition's anchor route from the persisted resolved
        // positions so a restored run stays self-consistent. Fields absent in
        // saves from earlier versions fall back to defaults: overworld, no
        // bonus, unknown start time.
        List<RouteAnchor> anchors = checkpoints.stream()
                .map(w -> new RouteAnchor(w.x(), w.z())).toList();
        String dimension = runTag.getString(TAG_DIMENSION);
        UUID playerId = runTag.getUUID(TAG_PLAYER);
        // Party is optional in saves from before the field existed.
        java.util.Set<UUID> party = new java.util.HashSet<>();
        for (Tag pt : runTag.getList(TAG_PARTY, Tag.TAG_COMPOUND)) {
            party.add(((CompoundTag) pt).getUUID(TAG_PLAYER));
        }
        party.add(playerId);
        // Portal bookkeeping is absent in saves from before portals existed;
        // fall back to plausible values (only used for diagnostics/teleports).
        Waypoint entry = checkpoints.isEmpty() ? exit : checkpoints.get(0);
        Waypoint terminal = runTag.contains(TAG_TERMINAL)
                ? readWaypoint(runTag.getCompound(TAG_TERMINAL)) : exit;
        Waypoint portal = runTag.contains(TAG_PORTAL)
                ? readWaypoint(runTag.getCompound(TAG_PORTAL)) : exit;
        if (runTag.contains(TAG_ENTRY)) {
            entry = readWaypoint(runTag.getCompound(TAG_ENTRY));
        }
        // Tracked challenge entities are absent in saves from before
        // checkpoint challenges existed.
        List<UUID> challengeEntities = new java.util.ArrayList<>();
        for (Tag et : runTag.getList(TAG_CHALLENGE_ENTITIES, Tag.TAG_COMPOUND)) {
            challengeEntities.add(((CompoundTag) et).getUUID(TAG_ENTITY_ID));
        }
        // Objectives are absent in saves from before objectives existed.
        List<ExpeditionObjective> objectives = new java.util.ArrayList<>();
        java.util.Map<String, ObjectiveState> objectiveStates = new java.util.LinkedHashMap<>();
        for (Tag ot : runTag.getList(TAG_OBJECTIVES, Tag.TAG_COMPOUND)) {
            CompoundTag objectiveTag = (CompoundTag) ot;
            ExpeditionObjective objective = new ExpeditionObjective(
                    objectiveTag.getString(TAG_OBJ_ID),
                    objectiveTag.getString(TAG_OBJ_DESCRIPTION),
                    objectiveTag.getBoolean(TAG_OBJ_REQUIRED),
                    ObjectiveKind.valueOf(objectiveTag.getString(TAG_OBJ_KIND)),
                    objectiveTag.getInt(TAG_OBJ_CHECKPOINT),
                    objectiveTag.contains(TAG_OBJ_ITEM)
                            ? objectiveTag.getString(TAG_OBJ_ITEM) : null,
                    objectiveTag.contains(TAG_OBJ_TAG)
                            ? objectiveTag.getString(TAG_OBJ_TAG) : null,
                    objectiveTag.getInt(TAG_OBJ_COUNT),
                    objectiveTag.getLong(TAG_OBJ_LIMIT_TICKS),
                    objectiveTag.getInt(TAG_OBJ_BONUS));
            objectives.add(objective);
            objectiveStates.put(objective.id(), new ObjectiveState(
                    objectiveTag.getInt(TAG_OBJ_PROGRESS),
                    objectiveTag.getBoolean(TAG_OBJ_DONE)));
        }
        ExpeditionDefinition definition = new ExpeditionDefinition(
                runTag.getString(TAG_DEFINITION_ID),
                runTag.getString(TAG_DEFINITION_NAME),
                runTag.getInt(TAG_DIFFICULTY),
                dimension.isBlank() ? DEFAULT_DIMENSION : dimension,
                new ExpeditionRoute(anchors, new RouteAnchor(exit.x(), exit.z())),
                runTag.getInt(TAG_TOKEN_REWARD),
                runTag.getInt(TAG_BONUS_TOKENS),
                runTag.getInt(TAG_BONUS_TICKS),
                objectives);
        return ExpeditionRun.restore(
                runTag.getUUID(TAG_RUN_ID),
                playerId,
                party,
                definition,
                checkpoints,
                exit,
                terminal,
                portal,
                entry,
                runTag.contains(TAG_STARTED) ? runTag.getLong(TAG_STARTED)
                        : ExpeditionRun.UNKNOWN_START,
                ExpeditionStatus.valueOf(runTag.getString(TAG_STATUS)),
                runTag.getInt(TAG_DONE),
                runTag.getBoolean(TAG_REWARDED),
                runTag.getInt(TAG_TOKENS_AWARDED),
                challengeEntities,
                objectiveStates,
                readOptions(runTag),
                readPortalDimension(runTag, dimension));
    }

    /**
     * Per-run options, absent in saves from before terminal selection
     * existed — those runs behaved like {@link ExpeditionOptions#DEFAULT}.
     */
    private static ExpeditionOptions readOptions(CompoundTag runTag) {
        if (!runTag.contains(TAG_OPTIONS)) {
            return ExpeditionOptions.DEFAULT;
        }
        CompoundTag opt = runTag.getCompound(TAG_OPTIONS);
        Difficulty tier = Difficulty.byName(opt.getString(TAG_OPT_DIFFICULTY));
        return new ExpeditionOptions(opt.getString(TAG_OPT_EXPEDITION),
                tier != null ? tier : Difficulty.STANDARD,
                opt.getInt(TAG_OPT_CHECKPOINTS), opt.getInt(TAG_OPT_TIME_PERCENT));
    }

    /**
     * The portal's home dimension, absent in saves from before remote
     * expeditions existed — then the portal always sat in the run's own
     * dimension.
     */
    private static String readPortalDimension(CompoundTag runTag, String runDimension) {
        String stored = runTag.getString(TAG_PORTAL_DIMENSION);
        return stored.isBlank()
                ? (runDimension.isBlank() ? DEFAULT_DIMENSION : runDimension) : stored;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        ListTag runs = new ListTag();
        for (ExpeditionRun run : ledger.runs()) {
            CompoundTag runTag = new CompoundTag();
            runTag.putUUID(TAG_RUN_ID, run.runId());
            runTag.putUUID(TAG_PLAYER, run.playerId());
            runTag.putString(TAG_DEFINITION_ID, run.definition().id());
            runTag.putString(TAG_DEFINITION_NAME, run.definition().displayName());
            runTag.putInt(TAG_DIFFICULTY, run.definition().difficulty());
            runTag.putString(TAG_DIMENSION, run.definition().dimension());
            runTag.putInt(TAG_TOKEN_REWARD, run.definition().tokenReward());
            runTag.putInt(TAG_BONUS_TOKENS, run.definition().bonusTokens());
            runTag.putInt(TAG_BONUS_TICKS, run.definition().bonusTimeTicks());
            runTag.putString(TAG_STATUS, run.status().name());
            runTag.putInt(TAG_DONE, run.completedCheckpoints());
            runTag.putBoolean(TAG_REWARDED, run.isRewarded());
            runTag.putInt(TAG_TOKENS_AWARDED, run.tokensAwarded());
            runTag.putLong(TAG_STARTED, run.startedGameTime());
            ListTag party = new ListTag();
            for (UUID member : run.party()) {
                CompoundTag memberTag = new CompoundTag();
                memberTag.putUUID(TAG_PLAYER, member);
                party.add(memberTag);
            }
            runTag.put(TAG_PARTY, party);
            ListTag checkpoints = new ListTag();
            run.checkpoints().forEach(w -> checkpoints.add(writeWaypoint(w)));
            runTag.put(TAG_CHECKPOINTS, checkpoints);
            runTag.put(TAG_EXIT, writeWaypoint(run.exit()));
            runTag.put(TAG_TERMINAL, writeWaypoint(run.terminal()));
            runTag.put(TAG_PORTAL, writeWaypoint(run.portal()));
            runTag.put(TAG_ENTRY, writeWaypoint(run.entry()));
            runTag.putString(TAG_PORTAL_DIMENSION, run.portalDimension());
            CompoundTag optionsTag = new CompoundTag();
            optionsTag.putString(TAG_OPT_EXPEDITION, run.options().expeditionId());
            optionsTag.putString(TAG_OPT_DIFFICULTY, run.options().difficulty().name());
            optionsTag.putInt(TAG_OPT_CHECKPOINTS, run.options().checkpoints());
            optionsTag.putInt(TAG_OPT_TIME_PERCENT, run.options().bonusTimePercent());
            runTag.put(TAG_OPTIONS, optionsTag);
            ListTag entities = new ListTag();
            for (UUID entityId : run.challengeEntities()) {
                CompoundTag entityTag = new CompoundTag();
                entityTag.putUUID(TAG_ENTITY_ID, entityId);
                entities.add(entityTag);
            }
            runTag.put(TAG_CHALLENGE_ENTITIES, entities);
            ListTag objectives = new ListTag();
            for (ExpeditionObjective objective : run.definition().objectives()) {
                CompoundTag objectiveTag = new CompoundTag();
                objectiveTag.putString(TAG_OBJ_ID, objective.id());
                objectiveTag.putString(TAG_OBJ_DESCRIPTION, objective.description());
                objectiveTag.putString(TAG_OBJ_KIND, objective.kind().name());
                objectiveTag.putBoolean(TAG_OBJ_REQUIRED, objective.required());
                objectiveTag.putInt(TAG_OBJ_CHECKPOINT, objective.checkpoint());
                if (objective.item() != null) {
                    objectiveTag.putString(TAG_OBJ_ITEM, objective.item());
                }
                if (objective.tag() != null) {
                    objectiveTag.putString(TAG_OBJ_TAG, objective.tag());
                }
                objectiveTag.putInt(TAG_OBJ_COUNT, objective.count());
                objectiveTag.putLong(TAG_OBJ_LIMIT_TICKS, objective.timeLimitTicks());
                objectiveTag.putInt(TAG_OBJ_BONUS, objective.bonusTokens());
                ObjectiveState state = run.objectiveState(objective.id())
                        .orElse(ObjectiveState.UNSTARTED);
                objectiveTag.putInt(TAG_OBJ_PROGRESS, state.progress());
                objectiveTag.putBoolean(TAG_OBJ_DONE, state.completed());
                objectives.add(objectiveTag);
            }
            runTag.put(TAG_OBJECTIVES, objectives);
            runs.add(runTag);
        }
        tag.put(TAG_RUNS, runs);
        ListTag tokens = new ListTag();
        ledger.tokenBalances().forEach((player, balance) -> {
            CompoundTag tokenTag = new CompoundTag();
            tokenTag.putUUID(TAG_PLAYER, player);
            tokenTag.putInt(TAG_BALANCE, balance);
            tokens.add(tokenTag);
        });
        tag.put(TAG_TOKENS, tokens);
        ListTag markers = new ListTag();
        liveMarkers.forEach((dimension, positions) -> {
            for (BlockPos pos : positions) {
                CompoundTag markerTag = writePos(pos);
                markerTag.putString(TAG_DIMENSION, dimension);
                markers.add(markerTag);
            }
        });
        tag.put(TAG_MARKERS, markers);
        return tag;
    }

    /**
     * Records that a marker block exists at {@code pos} in {@code dimension}.
     * Tracked markers that outlive their run (e.g. a crash rolled the run back
     * but not the chunk) are pruned on server start.
     */
    public void trackMarker(String dimension, BlockPos pos) {
        liveMarkers.computeIfAbsent(dimension, d -> new HashSet<>()).add(pos);
    }

    public void untrackMarker(String dimension, BlockPos pos) {
        Set<BlockPos> positions = liveMarkers.get(dimension);
        if (positions != null) {
            positions.remove(pos);
            if (positions.isEmpty()) {
                liveMarkers.remove(dimension);
            }
        }
    }

    /** Tracked marker positions per dimension id (unmodifiable). */
    public Map<String, Set<BlockPos>> liveMarkers() {
        return Collections.unmodifiableMap(liveMarkers);
    }

    private static Waypoint readWaypoint(CompoundTag tag) {
        return new Waypoint(tag.getInt(TAG_X), tag.getInt(TAG_Y), tag.getInt(TAG_Z));
    }

    private static BlockPos readPos(CompoundTag tag) {
        return new BlockPos(tag.getInt(TAG_X), tag.getInt(TAG_Y), tag.getInt(TAG_Z));
    }

    private static CompoundTag writeWaypoint(Waypoint w) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_X, w.x());
        tag.putInt(TAG_Y, w.y());
        tag.putInt(TAG_Z, w.z());
        return tag;
    }

    private static CompoundTag writePos(BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_X, pos.getX());
        tag.putInt(TAG_Y, pos.getY());
        tag.putInt(TAG_Z, pos.getZ());
        return tag;
    }

    public ExpeditionLedger ledger() {
        return ledger;
    }
}
