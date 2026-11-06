package com.expeditions.registry;
import com.expeditions.ExpeditionsMod;
import com.expeditions.worldgen.DormantScarFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.registries.*;
public final class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES=DeferredRegister.create(Registries.FEATURE,ExpeditionsMod.MOD_ID);
    public static final DeferredHolder<Feature<?>,DormantScarFeature> DORMANT_SCAR=FEATURES.register("dormant_scar",()->new DormantScarFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredRegister<com.mojang.serialization.MapCodec<? extends
            net.minecraft.world.level.levelgen.SurfaceRules.RuleSource>> MATERIAL_RULES =
            DeferredRegister.create(Registries.MATERIAL_RULE, ExpeditionsMod.MOD_ID);

    public static final DeferredRegister<com.mojang.serialization.MapCodec<? extends
            net.minecraft.world.level.biome.BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, ExpeditionsMod.MOD_ID);

    public static final DeferredRegister<com.mojang.serialization.MapCodec<? extends
            net.minecraft.world.level.chunk.ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, ExpeditionsMod.MOD_ID);
    public static final DeferredRegister<net.minecraft.world.level.levelgen.structure.StructureType<?>> STRUCTURES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, ExpeditionsMod.MOD_ID);
    public static final DeferredHolder<net.minecraft.world.level.levelgen.structure.StructureType<?>,
            net.minecraft.world.level.levelgen.structure.StructureType<com.expeditions.worldgen.ShatterwakeLandmark>>
            SHATTERWAKE_LANDMARK = STRUCTURES.register("shatterwake_landmark",
                    () -> () -> com.expeditions.worldgen.ShatterwakeLandmark.CODEC);

    static {
        CHUNK_GENERATORS.register("shatterwake", () -> com.expeditions.worldgen.ShatterwakeChunkGenerator.CODEC);
        BIOME_SOURCES.register("echoes", () -> com.expeditions.worldgen.EchoBiomeSource.CODEC);
        MATERIAL_RULES.register("echo_material", () -> com.expeditions.worldgen.EchoMaterialRule.CODEC);
    }

    public static final DeferredHolder<Feature<?>, com.expeditions.worldgen.ShatterwakeRemnantFeature> SHATTERWAKE_REMNANT =
            FEATURES.register("shatterwake_remnant", com.expeditions.worldgen.ShatterwakeRemnantFeature::new);

    private ModFeatures() { }
}
