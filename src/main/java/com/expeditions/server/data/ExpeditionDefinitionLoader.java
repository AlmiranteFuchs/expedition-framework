package com.expeditions.server.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Loads {@code data/<namespace>/expeditions/<name>.json} from datapacks into
 * the {@link ExpeditionDefinitions} snapshot. Re-applied on every datapack
 * reload ({@code /reload}, join, datapack changes).
 */
public class ExpeditionDefinitionLoader extends SimpleJsonResourceReloadListener {
    /**
     * Test-only seam: the game-test suite suppresses datapack loading so the
     * shipped expedition definitions never shadow the config defaults its
     * tests rely on; tests inject defs via {@link ExpeditionDefinitions#reload}.
     */
    private static volatile boolean loadingSuppressedForTests;

    public static void suppressLoadingForTests(boolean suppressed) {
        loadingSuppressedForTests = suppressed;
    }

    public ExpeditionDefinitionLoader() {
        super(new Gson(), "expeditions");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
            ProfilerFiller profiler) {
        if (!loadingSuppressedForTests) {
            ExpeditionDefinitions.reload(entries);
        }
    }
}
