package com.expeditions.server;

import com.expeditions.core.ExpeditionDefinition;
import com.expeditions.core.ExpeditionLedger;
import com.expeditions.core.ExpeditionRoute;
import com.expeditions.core.ExpeditionRun;
import com.expeditions.core.ExpeditionStatus;
import com.expeditions.core.RouteAnchor;
import com.expeditions.core.Waypoint;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * World-level persistence for expedition state, stored in the overworld's
 * {@code DimensionDataStorage} so runs and token balances survive disconnects
 * and restarts. Only serialization lives here; all rules stay in
 * {@link ExpeditionLedger} and {@link ExpeditionRun}.
 */
public class ExpeditionSavedData extends SavedData {
    public static final String DATA_NAME = "expeditions_runs";

    public static final Factory<ExpeditionSavedData> FACTORY = new Factory<>(
            ExpeditionSavedData::create, ExpeditionSavedData::load, null);

    private static final String TAG_RUNS = "Runs";
    private static final String TAG_TOKENS = "Tokens";
    private static final String TAG_PLAYER = "Player";
    private static final String TAG_RUN_ID = "RunId";
    private static final String TAG_DEFINITION_ID = "DefinitionId";
    private static final String TAG_DEFINITION_NAME = "DefinitionName";
    private static final String TAG_DIFFICULTY = "Difficulty";
    private static final String TAG_TOKEN_REWARD = "TokenReward";
    private static final String TAG_STATUS = "Status";
    private static final String TAG_DONE = "Done";
    private static final String TAG_REWARDED = "Rewarded";
    private static final String TAG_CHECKPOINTS = "Checkpoints";
    private static final String TAG_EXIT = "Exit";
    private static final String TAG_BALANCE = "Balance";
    private static final String TAG_X = "x";
    private static final String TAG_Y = "y";
    private static final String TAG_Z = "z";

    private final ExpeditionLedger ledger = new ExpeditionLedger();

    public static ExpeditionSavedData create() {
        return new ExpeditionSavedData();
    }

    public static ExpeditionSavedData load(CompoundTag tag, HolderLookup.Provider lookup) {
        ExpeditionSavedData data = create();
        for (Tag t : tag.getList(TAG_RUNS, Tag.TAG_COMPOUND)) {
            CompoundTag runTag = (CompoundTag) t;
            List<Waypoint> checkpoints = runTag.getList(TAG_CHECKPOINTS, Tag.TAG_COMPOUND)
                    .stream().map(e -> readWaypoint((CompoundTag) e)).toList();
            Waypoint exit = readWaypoint(runTag.getCompound(TAG_EXIT));
            // Rebuild the definition's anchor route from the persisted resolved
            // positions so a restored run stays self-consistent.
            List<RouteAnchor> anchors = checkpoints.stream()
                    .map(w -> new RouteAnchor(w.x(), w.z())).toList();
            ExpeditionDefinition definition = new ExpeditionDefinition(
                    runTag.getString(TAG_DEFINITION_ID),
                    runTag.getString(TAG_DEFINITION_NAME),
                    runTag.getInt(TAG_DIFFICULTY),
                    new ExpeditionRoute(anchors, new RouteAnchor(exit.x(), exit.z())),
                    runTag.getInt(TAG_TOKEN_REWARD));
            data.ledger.restoreRun(ExpeditionRun.restore(
                    runTag.getUUID(TAG_RUN_ID),
                    runTag.getUUID(TAG_PLAYER),
                    definition,
                    checkpoints,
                    exit,
                    ExpeditionStatus.valueOf(runTag.getString(TAG_STATUS)),
                    runTag.getInt(TAG_DONE),
                    runTag.getBoolean(TAG_REWARDED)));
        }
        for (Tag t : tag.getList(TAG_TOKENS, Tag.TAG_COMPOUND)) {
            CompoundTag tokenTag = (CompoundTag) t;
            data.ledger.restoreTokens(tokenTag.getUUID(TAG_PLAYER), tokenTag.getInt(TAG_BALANCE));
        }
        return data;
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
            runTag.putInt(TAG_TOKEN_REWARD, run.definition().tokenReward());
            runTag.putString(TAG_STATUS, run.status().name());
            runTag.putInt(TAG_DONE, run.completedCheckpoints());
            runTag.putBoolean(TAG_REWARDED, run.isRewarded());
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
        return tag;
    }

    private static Waypoint readWaypoint(CompoundTag tag) {
        return new Waypoint(tag.getInt(TAG_X), tag.getInt(TAG_Y), tag.getInt(TAG_Z));
    }

    private static CompoundTag writeWaypoint(Waypoint w) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_X, w.x());
        tag.putInt(TAG_Y, w.y());
        tag.putInt(TAG_Z, w.z());
        return tag;
    }

    public ExpeditionLedger ledger() {
        return ledger;
    }
}
