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
    public ExpeditionDefinitionLoader() {
        super(new Gson(), "expeditions");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
            ProfilerFiller profiler) {
        ExpeditionDefinitions.reload(entries);
    }
}
