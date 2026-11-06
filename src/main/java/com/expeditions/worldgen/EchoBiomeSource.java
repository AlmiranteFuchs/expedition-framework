package com.expeditions.worldgen;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;

/** Native biomes retain their registered features, spawning rules, and identity. */
public final class EchoBiomeSource extends BiomeSource {
    public static final MapCodec<EchoBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Entry.CODEC.codec().listOf().fieldOf("biomes").forGetter(source -> source.entries),
            RegistryOps.retrieveGetter(Registries.BIOME)
    ).apply(instance, EchoBiomeSource::new));

    private final List<Entry> entries;
    private final MultiNoiseBiomeSource delegate;

    private EchoBiomeSource(List<Entry> entries, HolderGetter<Biome> biomes) {
        this.entries = List.copyOf(entries);
        List<Pair<Climate.ParameterPoint, Holder<Biome>>> parameters = this.entries.stream().map(entry -> {
            Holder<Biome> biome = entry.preferred()
                    .flatMap(id -> biomes.get(ResourceKey.create(Registries.BIOME, id)))
                    .orElseGet(() -> biomes.getOrThrow(ResourceKey.create(Registries.BIOME, entry.fallback())));
            return Pair.of(entry.parameters(), biome);
        }).toList();
        this.delegate = MultiNoiseBiomeSource.createFromList(new Climate.ParameterList<>(parameters));
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return delegate.possibleBiomes().stream();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        return delegate.getNoiseBiome(x, y, z, sampler);
    }

    private record Entry(ResourceLocation fallback, Optional<ResourceLocation> preferred,
            Climate.ParameterPoint parameters) {
        private static final MapCodec<Entry> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("fallback").forGetter(Entry::fallback),
                ResourceLocation.CODEC.optionalFieldOf("preferred").forGetter(Entry::preferred),
                Climate.ParameterPoint.CODEC.fieldOf("parameters").forGetter(Entry::parameters)
        ).apply(instance, Entry::new));
    }
}
