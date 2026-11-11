package com.expeditions.gametest;

import com.expeditions.entity.BloomListener;
import com.expeditions.registry.ModAttachments;
import com.expeditions.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("expeditions_bloom")
@PrefixGameTestTemplate(false)
public final class BloomListenerTests {
    private BloomListenerTests() { }

    private static BloomListener listener(ServerLevel level, int x) {
        var listener = ModEntities.BLOOM_LISTENER.get().create(level);
        listener.moveTo(x + .5, 140, .5);
        return listener;
    }

    @GameTest(template = "arena", batch = "listener_interrupt")
    public static void damageInterruptsAndReloadCannotFinishOldCall(GameTestHelper helper) {
        var level = helper.getLevel().getServer().getLevel(Level.NETHER);
        var listener = listener(level, 6000);
        var copy = listener(level, 6000);
        try {
            var at = listener.position().add(4, 0, 0);
            helper.assertTrue(listener.soundListener().handleGameEvent(level, GameEvent.BLOCK_DESTROY,
                    GameEvent.Context.of(Blocks.STONE.defaultBlockState()), at), "mining starts a warning");
            helper.assertTrue(listener.warningTicks() == 60, "the call gives three seconds to react");
            copy.load(listener.saveWithoutId(new CompoundTag()));
            helper.assertTrue(copy.warningTicks() == 0 && !copy.soundListener().handleGameEvent(level,
                    GameEvent.BLOCK_ACTIVATE, GameEvent.Context.of(Blocks.STONE.defaultBlockState()), at),
                    "reload cancels the pending call and preserves a recovery interval");
            listener.hurt(level.damageSources().generic(), 1);
            helper.assertTrue(listener.warningTicks() == 0, "a damaging hit closes the face");
            for (int i = 0; i < 60; i++) listener.tick();
            helper.assertTrue(!listener.soundListener().handleGameEvent(level, GameEvent.BLOCK_DESTROY,
                    GameEvent.Context.of(Blocks.STONE.defaultBlockState()), at),
                    "the player has time to retreat after interrupting");
        } finally { listener.discard(); copy.discard(); }
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "listener_stealth")
    public static void creepingWoolAndInfectedFootstepsStayQuiet(GameTestHelper helper) {
        var level = helper.getLevel().getServer().getLevel(Level.NETHER);
        var listener = listener(level, 6100);
        var source = EntityType.COW.create(level);
        var wall = new BlockPos(6102, 140, 0);
        var old = level.getBlockState(wall);
        source.moveTo(6104.5, 140, .5);
        try {
            source.setShiftKeyDown(true);
            helper.assertTrue(!listener.soundListener().handleGameEvent(level, GameEvent.STEP,
                    GameEvent.Context.of(source), source.position()), "careful footsteps stay quiet");
            source.setShiftKeyDown(false);
            source.setData(ModAttachments.ASSIMILATED, 1);
            helper.assertTrue(!listener.soundListener().handleGameEvent(level, GameEvent.STEP,
                    GameEvent.Context.of(source), source.position()), "the hive cannot trigger itself");
            source.setData(ModAttachments.ASSIMILATED, 0);
            level.setBlockAndUpdate(wall, Blocks.WHITE_WOOL.defaultBlockState());
            helper.assertTrue(!listener.soundListener().handleGameEvent(level, GameEvent.BLOCK_DESTROY,
                    GameEvent.Context.of(source), source.position()), "wool blocks the noise path");
            level.setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
            helper.assertTrue(listener.soundListener().handleGameEvent(level, GameEvent.STEP,
                    GameEvent.Context.of(source), source.position()), "ordinary exposed footsteps are heard");
        } finally { level.setBlockAndUpdate(wall, old); listener.discard(); source.discard(); }
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "listener_ward")
    public static void wardedWorkDoesNotCarryAndLightingAWardClosesTheMask(GameTestHelper helper) {
        var level = helper.getLevel().getServer().getLevel(Level.NETHER);
        var listener = listener(level, 6300);
        var wardPos = new BlockPos(6312, 140, 0);
        var old = level.getBlockState(wardPos);
        try {
            level.setBlockAndUpdate(wardPos, com.expeditions.registry.ModBlocks.FIELD_WARD.get().defaultBlockState());
            var ward = (com.expeditions.block.entity.FieldWardBlockEntity) level.getBlockEntity(wardPos);
            var cell = new net.minecraft.world.item.ItemStack(com.expeditions.registry.ModItems.RESONANCE_CELL.get());
            cell.set(com.expeditions.registry.ModDataComponents.RESONANCE.get(), 4000);
            helper.assertTrue(ward.ignite(cell), "the ward fixture must be powered");
            helper.assertTrue(!listener.soundListener().handleGameEvent(level, GameEvent.BLOCK_ACTIVATE,
                    GameEvent.Context.of(Blocks.STONE.defaultBlockState()), Vec3.atCenterOf(wardPos)),
                    "a working machine inside a ward cannot disturb a Listener outside it");
            helper.assertTrue(listener.soundListener().handleGameEvent(level, GameEvent.BLOCK_ACTIVATE,
                    GameEvent.Context.of(Blocks.STONE.defaultBlockState()), listener.position().add(-3, 0, 0)),
                    "unwarded machinery starts a warning");
            listener.moveTo(6310.5, 140, .5);
            listener.tick();
            helper.assertTrue(listener.warningTicks() == 0, "ward coverage cancels a pending call");
        } finally { listener.discard(); level.setBlockAndUpdate(wardPos, old); }
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "listener_rally", timeoutTicks = 400)
    public static void registeredGameEventWarnsBeforeRallyingOnlyNearbyInfected(GameTestHelper helper) {
        var level = helper.getLevel();
        boolean wasSheltered = com.expeditions.server.Stillness.shelters(level);
        com.expeditions.server.Stillness.suppressForTests(true);
        var center = helper.absolutePos(new BlockPos(2, 2, 2));
        int x = center.getX();
        var listener = listener(level, x);
        listener.moveTo(x + .5, center.getY(), center.getZ() + .5);
        Cow source = EntityType.COW.create(level);
        Zombie infected = EntityType.ZOMBIE.create(level);
        Zombie clean = EntityType.ZOMBIE.create(level);
        source.moveTo(x + 5.5, center.getY(), center.getZ() + .5);
        infected.moveTo(x + 2.5, center.getY(), center.getZ() + .5);
        clean.moveTo(x + 3.5, center.getY(), center.getZ() + .5);
        infected.setData(ModAttachments.ASSIMILATED, 1);
        source.setNoAi(true);
        infected.setNoAi(true);
        clean.setNoAi(true);
        listener.setNoGravity(true);
        source.setNoGravity(true); infected.setNoGravity(true); clean.setNoGravity(true);
        int chunkX = x >> 4;
        int chunkZ = center.getZ() >> 4;
        boolean alreadyForced = level.getForcedChunks().contains(net.minecraft.world.level.ChunkPos.asLong(chunkX, chunkZ));
        level.setChunkForced(chunkX, chunkZ, true);
        level.getChunk(chunkX, chunkZ);
        level.addFreshEntity(listener); level.addFreshEntity(source);
        level.addFreshEntity(infected); level.addFreshEntity(clean);
        awaitTracked(helper, level, listener, 240, () -> {
                level.gameEvent(GameEvent.BLOCK_DESTROY, source.position(), GameEvent.Context.of(source));
                helper.assertTrue(listener.warningTicks() == 60,
                        "the real dispatcher reaches the tracked listener");
                helper.assertTrue(infected.getTarget() == null, "the warning itself never alerts the hive");
                for (int i = 0; i < 59; i++) listener.tick();
                helper.assertTrue(infected.getTarget() == null, "the full warning precedes target sharing");
                listener.tick();
                helper.assertTrue(infected.getTarget() == source, "the finished call shares the actual disturbance");
                helper.assertTrue(clean.getTarget() == null, "uninfected wildlife is not recruited");
                helper.assertTrue(!listener.soundListener().handleGameEvent(level, GameEvent.BLOCK_DESTROY,
                        GameEvent.Context.of(source), source.position()), "the call cannot repeat immediately");
                helper.succeed();
        }, () -> {
            listener.discard(); source.discard(); infected.discard(); clean.discard();
            com.expeditions.server.Stillness.suppressForTests(!wasSheltered);
            if (!alreadyForced) level.setChunkForced(chunkX, chunkZ, false);
        });
    }

    @GameTest(template = "arena", batch = "listener_lure", timeoutTicks = 400)
    public static void projectileCallMakesAnInfectedMobInvestigateItsImpact(GameTestHelper helper) {
        var level = helper.getLevel();
        boolean wasSheltered = com.expeditions.server.Stillness.shelters(level);
        com.expeditions.server.Stillness.suppressForTests(true);
        var center = helper.absolutePos(new BlockPos(2, 1, 2));
        var listener = ModEntities.BLOOM_LISTENER.get().create(level);
        var infected = EntityType.COW.create(level);
        var thrower = EntityType.COW.create(level);
        var projectile = EntityType.SNOWBALL.create(level);
        var impact = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 1, 7)));
        listener.moveTo(Vec3.atBottomCenterOf(center));
        infected.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(6, 1, 2))));
        thrower.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(7, 1, 2))));
        projectile.moveTo(impact);
        projectile.setOwner(thrower);
        infected.setData(ModAttachments.ASSIMILATED, 1);
        infected.setNoAi(true);
        listener.setNoGravity(true);
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState());
                for (int y = 1; y <= 3; y++)
                    level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, y, z)), Blocks.AIR.defaultBlockState());
            }
        }
        level.addFreshEntity(listener);
        level.addFreshEntity(infected);
        Runnable cleanup = () -> {
            listener.discard(); infected.discard(); projectile.discard(); thrower.discard();
            com.expeditions.server.Stillness.suppressForTests(!wasSheltered);
        };
        boolean[] movementCheckScheduled = {false};
        awaitTracked(helper, level, listener, 240, () -> {
            helper.assertTrue(listener.soundListener().handleGameEvent(level, GameEvent.PROJECTILE_LAND,
                    GameEvent.Context.of(projectile), impact), "a projectile impact starts the warning");
            for (int tick = 0; tick < BloomListener.WARNING_TICKS; tick++) listener.tick();
            helper.assertTrue(infected.getTarget() == null, "a distant thrower is not revealed as a combat target");
            double before = infected.position().distanceToSqr(impact);
            infected.setNoAi(false);
            helper.runAfterDelay(60, () -> {
                try {
                    helper.assertTrue(infected.getTarget() == null, "investigation stays separate from combat");
                    helper.assertTrue(infected.position().distanceToSqr(impact) < before - 1,
                            "the mob walks toward the impact; before=" + before
                                    + ", after=" + infected.position().distanceToSqr(impact)
                                    + ", ticks=" + infected.tickCount
                                    + ", visible=" + (level.getEntity(infected.getUUID()) != null)
                                    + ", active=" + com.expeditions.server.HiveMind.active(infected)
                                    + ", noAI=" + infected.isNoAi()
                                    + ", path=" + infected.getNavigation().getPath()
                                    + ", running=" + infected.goalSelector.getAvailableGoals().stream()
                                        .filter(goal -> goal.isRunning())
                                        .map(goal -> goal.getGoal().getClass().getSimpleName()).toList());
                    helper.succeed();
                } finally { cleanup.run(); }
            });
            movementCheckScheduled[0] = true;
        }, () -> {
            if (!movementCheckScheduled[0]) cleanup.run();
        });
    }

    private static void awaitTracked(GameTestHelper helper, ServerLevel level, BloomListener listener,
            int remaining, Runnable check, Runnable cleanup) {
        if (level.getEntity(listener.getUUID()) == null && remaining > 0) {
            helper.runAfterDelay(1, () -> awaitTracked(helper, level, listener, remaining - 1, check, cleanup));
            return;
        }
        try {
            helper.assertTrue(level.getEntity(listener.getUUID()) == listener,
                    "the forced chunk must promote the entity before testing game events");
            check.run();
        } finally { cleanup.run(); }
    }
}
