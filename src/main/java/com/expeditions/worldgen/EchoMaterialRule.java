package com.expeditions.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SurfaceRules;

/** Resolve optional terrain materials after block tags have loaded. */
public record EchoMaterialRule(ResourceLocation materialTag, BlockState fallback) implements SurfaceRules.RuleSource {
    public static final MapCodec<EchoMaterialRule> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("material_tag").forGetter(EchoMaterialRule::materialTag),
            BlockState.CODEC.fieldOf("fallback").forGetter(EchoMaterialRule::fallback)
    ).apply(instance, EchoMaterialRule::new));

    @Override
    public KeyDispatchDataCodec<? extends SurfaceRules.RuleSource> codec() {
        return KeyDispatchDataCodec.of(CODEC);
    }

    @Override
    public SurfaceRules.SurfaceRule apply(SurfaceRules.Context context) {
        BlockState state = resolve(materialTag, fallback);
        return (x, y, z) -> state;
    }

    static BlockState resolve(ResourceLocation materialTag, BlockState fallback) {
        var tag = TagKey.create(Registries.BLOCK, materialTag);
        return BuiltInRegistries.BLOCK.getTag(tag)
                .flatMap(values -> values.stream().map(Holder::value).findFirst())
                .map(block -> block.defaultBlockState()).orElse(fallback);
    }
}
