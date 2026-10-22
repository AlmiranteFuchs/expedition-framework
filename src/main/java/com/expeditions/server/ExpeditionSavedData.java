package com.expeditions.server;

import com.expeditions.core.ExpeditionDefinition;
import com.expeditions.core.ExpeditionRun;
import com.expeditions.core.ExpeditionStatus;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * World-level persistence for expedition state, stored in the overworld's
 * {@code DimensionDataStorage} so runs survive disconnects and restarts without
 * duplicating rewards. Only serialized fields live here; lifecycle rules stay in
 * {@link ExpeditionRun}.
 */
public class ExpeditionSavedData extends SavedData {
    public static final String DATA_NAME = "expeditions_runs";

    public static final Factory<ExpeditionSavedData> FACTORY = new Factory<>(
            ExpeditionSavedData::create, ExpeditionSavedData::load, null);

    private static final String TAG_RUN_ID = "RunId";
    private static final String TAG_DEFINITION_ID = "DefinitionId";
    private static final String TAG_DEFINITION_NAME = "DefinitionName";
    private static final String TAG_DIFFICULTY = "Difficulty";
    private static final String TAG_STATUS = "Status";

    @Nullable
    private ExpeditionRun run;

    public static ExpeditionSavedData create() {
        return new ExpeditionSavedData();
    }

    public static ExpeditionSavedData load(CompoundTag tag, HolderLookup.Provider lookup) {
        ExpeditionSavedData data = create();
        if (tag.contains(TAG_RUN_ID)) {
            ExpeditionDefinition definition = new ExpeditionDefinition(
                    tag.getString(TAG_DEFINITION_ID),
                    tag.getString(TAG_DEFINITION_NAME),
                    tag.getInt(TAG_DIFFICULTY));
            data.run = new ExpeditionRun(
                    tag.getUUID(TAG_RUN_ID),
                    definition,
                    ExpeditionStatus.valueOf(tag.getString(TAG_STATUS)));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        if (run != null) {
            tag.putUUID(TAG_RUN_ID, run.runId());
            tag.putString(TAG_DEFINITION_ID, run.definition().id());
            tag.putString(TAG_DEFINITION_NAME, run.definition().displayName());
            tag.putInt(TAG_DIFFICULTY, run.definition().difficulty());
            tag.putString(TAG_STATUS, run.status().name());
        }
        return tag;
    }

    @Nullable
    public ExpeditionRun run() {
        return run;
    }

    public void setRun(@Nullable ExpeditionRun run) {
        this.run = run;
        setDirty();
    }
}
