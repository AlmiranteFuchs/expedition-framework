package com.expeditions.gametest;

import com.expeditions.server.HordePools;
import com.expeditions.server.MobSpawns;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Optional-mod probes using real biome data without dependencies or mock-player networking. */
@GameTestHolder("expeditions_modded_horde")
@PrefixGameTestTemplate(false)
public final class ModdedHordeTests {
    private ModdedHordeTests() { }

    @GameTest(template = "arena", batch = "modded_horde_aether", timeoutTicks = 1200)
    public static void aetherCustomHostilesReachHordeSpawner(GameTestHelper helper) throws Exception {
        nativePool(helper, "aether:skyroot_forest", false);
    }

    @GameTest(template = "arena", batch = "modded_horde_bees", timeoutTicks = 1200)
    public static void bumblezoneNeutralCombatMobsReachHordeSpawner(GameTestHelper helper) throws Exception {
        nativePool(helper, "the_bumblezone:bumbling_beepartments", true);
    }

    @GameTest(template = "arena", batch = "modded_horde_twilight", timeoutTicks = 1200)
    public static void twilightSpawnWeightsKeepNativeMobs(GameTestHelper helper) throws Exception {
        nativePool(helper, "twilightforest:highlands_underground", false);
    }

    private static void nativePool(GameTestHelper helper, String biomeId, boolean neutral) throws Exception {
        var level = helper.getLevel();
        var biomeKey = ResourceKey.create(Registries.BIOME, ResourceLocation.parse(biomeId));
        var biome = level.registryAccess().registryOrThrow(Registries.BIOME).getHolder(biomeKey);
        if (biome.isEmpty()) {
            com.expeditions.ExpeditionsMod.LOGGER.info("Skipping optional horde probe: {} is not installed", biomeId);
            helper.succeed();
            return;
        }
        // GameTest loads only vanilla dimensions. Apply the mod's actual biome
        // to a unique test platform so the production selector reads its data.
        var center = helper.absolutePos(new BlockPos(8, 3, 8));
        for (int dx = -12; dx <= 12; dx++) {
            for (int dz = -12; dz <= 12; dz++) {
                var floor = center.offset(dx, -1, dz);
                level.getChunk(floor);
                level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
                for (int dy = 1; dy <= 18; dy++) {
                    level.setBlock(floor.above(dy), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        var min = center.offset(-12, -3, -12);
        var max = center.offset(12, 8, 12);
        level.getServer().getCommands().getDispatcher().execute(
                "fillbiome %d %d %d %d %d %d %s".formatted(min.getX(), min.getY(), min.getZ(),
                        max.getX(), max.getY(), max.getZ(), biomeId),
                level.getServer().createCommandSourceStack().withLevel(level));
        helper.assertTrue(level.getBiome(center).is(biomeKey), "the actual mod biome must be installed at the probe");
        String namespace = biomeKey.location().getNamespace();
        // The dimension ID doesn't override biome selection when there is no
        // explicit tag. This tests the same biome-driven path as checkpoints.
        String dimension = namespace + ":horde_probe";
        var candidates = HordePools.candidates(level, center, dimension);
        helper.assertTrue(!candidates.isEmpty(), "the native pool must not be empty");
        helper.assertTrue(candidates.stream().anyMatch(type ->
                        BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().equals(namespace)),
                "the pool must include this mod's own mobs, rather than only vanilla fallback mobs");
        if (neutral) {
            helper.assertTrue(candidates.stream().allMatch(type -> type.create(level) instanceof NeutralMob),
                    "a creature-only biome must select combat-capable neutrals, not passive animals");
        } else {
            helper.assertTrue(candidates.stream().allMatch(type -> !type.getCategory().isFriendly()),
                    "hostile biome categories must take priority over passive or neutral creatures");
        }
        for (int i = 0; i < 16; i++) {
            var type = HordePools.pick(level, center, dimension, level.random);
            helper.assertTrue(candidates.contains(type), "weighted picks must stay in the native pool");
        }
        // Exercise the shared placement/finalization path with an actual native
        // mob, rather than checking candidate IDs alone.
        var nativeType = candidates.stream().filter(type ->
                BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().equals(namespace)).findFirst().orElseThrow();
        var mob = MobSpawns.trySpawn(nativeType, level, center, 8);
        helper.assertTrue(mob != null, "a native horde mob must fit on the checkpoint platform");
        try {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null);
            helper.assertTrue(level.addFreshEntity(mob), "the native horde mob must enter the world");
        } finally {
            mob.discard();
        }
        helper.succeed();
    }
}
