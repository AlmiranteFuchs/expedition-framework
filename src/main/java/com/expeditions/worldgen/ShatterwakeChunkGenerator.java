package com.expeditions.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/** Native biome decoration runs here, with a separate set of void-safe structures. */
public final class ShatterwakeChunkGenerator extends NoiseBasedChunkGenerator {
    public static final MapCodec<ShatterwakeChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ShatterwakeChunkGenerator::getBiomeSource),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(ShatterwakeChunkGenerator::generatorSettings)
    ).apply(instance, ShatterwakeChunkGenerator::new));

    public ShatterwakeChunkGenerator(BiomeSource biomes, Holder<NoiseGeneratorSettings> settings) {
        super(biomes, settings);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structures, RandomState random, long seed) {
        var local = structures.listElements().filter(set -> {
            var id = set.key().location();
            return id.getNamespace().equals("expeditions") && id.getPath().startsWith("shatterwake/");
        }).<Holder<StructureSet>>map(set -> set);
        return ChunkGeneratorStructureState.createForFlat(random, seed, getBiomeSource(), local);
    }
}
