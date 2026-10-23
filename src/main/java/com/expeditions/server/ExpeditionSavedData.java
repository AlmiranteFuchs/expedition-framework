package com.expeditions.server;

import com.expeditions.core.ExpeditionDefinition;
import com.expeditions.core.ExpeditionLedger;
import com.expeditions.core.ExpeditionRoute;
import com.expeditions.core.ExpeditionRun;
import com.expeditions.core.ExpeditionStatus;
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
    private static final String TAG_BALANCE = "Balance";
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
        ExpeditionDefinition definition = new ExpeditionDefinition(
                runTag.getString(TAG_DEFINITION_ID),
                runTag.getString(TAG_DEFINITION_NAME),
                runTag.getInt(TAG_DIFFICULTY),
                dimension.isBlank() ? DEFAULT_DIMENSION : dimension,
                new ExpeditionRoute(anchors, new RouteAnchor(exit.x(), exit.z())),
                runTag.getInt(TAG_TOKEN_REWARD),
                runTag.getInt(TAG_BONUS_TOKENS),
                runTag.getInt(TAG_BONUS_TICKS));
        UUID playerId = runTag.getUUID(TAG_PLAYER);
        // Party is optional in saves from before the field existed.
        java.util.Set<UUID> party = new java.util.HashSet<>();
        for (Tag pt : runTag.getList(TAG_PARTY, Tag.TAG_COMPOUND)) {
            party.add(((CompoundTag) pt).getUUID(TAG_PLAYER));
        }
        party.add(playerId);
        return ExpeditionRun.restore(
                runTag.getUUID(TAG_RUN_ID),
                playerId,
                party,
                definition,
                checkpoints,
                exit,
                runTag.contains(TAG_STARTED) ? runTag.getLong(TAG_STARTED)
                        : ExpeditionRun.UNKNOWN_START,
                ExpeditionStatus.valueOf(runTag.getString(TAG_STATUS)),
                runTag.getInt(TAG_DONE),
                runTag.getBoolean(TAG_REWARDED),
                runTag.getInt(TAG_TOKENS_AWARDED));
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
