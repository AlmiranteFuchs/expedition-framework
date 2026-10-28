package com.expeditions.server.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Loads {@code data/<namespace>/expedition_dimensions/<path>.json} into the
 * {@link ExpeditionTuning} snapshot — the resource id names the dimension it
 * overrides. Re-applied on every datapack reload, like expedition defs.
 */
public class ExpeditionTuningLoader extends SimpleJsonResourceReloadListener {
    /**
     * Test-only seam: the game-test suite suppresses datapack loading so the
     * shipped tuning files never shadow the global config values its tests
     * rely on; tests inject via {@link ExpeditionTuning#reload}.
     */
    private static volatile boolean loadingSuppressedForTests;

    public static void suppressLoadingForTests(boolean suppressed) {
        loadingSuppressedForTests = suppressed;
    }

    public ExpeditionTuningLoader() {
        super(new Gson(), "expedition_dimensions");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
            ProfilerFiller profiler) {
        if (!loadingSuppressedForTests) {
            ExpeditionTuning.reload(entries);
        }
    }
}
