package com.expeditions.gametest;

import com.expeditions.core.*;
import com.expeditions.server.*;
import com.expeditions.server.data.ExpeditionTuning;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import java.util.Map;

@GameTestHolder("expeditions_frontier")
@PrefixGameTestTemplate(false)
public final class FrontierTests {
    private static ExpeditionSavedData begin(GameTestHelper h) {
        FrontierThreat.suppressForTests(false);
        ExpeditionTuning.reload(Map.of(ResourceLocation.parse("minecraft:overworld"),JsonParser.parseString(
                "{\"frontier\":{\"home_refuge_radius\":0}}")));
        var data=ExpeditionManager.get(h.getLevel().getServer()).savedData();data.resetCampaign("minecraft:overworld");return data;
    }
    private static void end(ExpeditionSavedData data) {
        data.resetCampaign("minecraft:overworld");ExpeditionTuning.reload(Map.of());FrontierThreat.suppressForTests(true);
    }
    @GameTest(template="arena",batch="frontier_clock")
    public static void presenceRetreatReliefAndCloneDoNotResetProgress(GameTestHelper h) {
        var data=begin(h);var p=h.makeMockServerPlayerInLevel();var service=new FrontierThreat();String dim="minecraft:overworld";
        try {
            h.assertTrue(ExpeditionManager.get(h.getLevel().getServer()).frontier()==
                    ExpeditionManager.get(h.getLevel().getServer()).frontier(),
                    "recreating the stateless manager must not recreate HUD ownership and duplicate boss bars");
            service.tickPlayer(p,data,false);h.assertTrue(FrontierThreat.attention(p,dim)==0,"creative must not attract ambient hunts");
            for(int i=0;i<20;i++)service.tickPlayer(p,data,true);
            h.assertTrue(FrontierThreat.attention(p,dim)==10,"Overworld must afford twice the preparation time");
            FrontierThreat.record(p,"minecraft:the_nether").putInt("seconds",60);service.tickPlayer(p,data,true);
            h.assertTrue(FrontierThreat.attention(p,"minecraft:the_nether")==58,"leaving must cool, not erase, attention");
            FrontierThreat.record(p,dim).putInt("seconds",260);FrontierThreat.relief(p,dim,45);
            h.assertTrue(FrontierThreat.attention(p,dim)==215,"checkpoints must buy forty-five effective seconds");
            var clone=h.makeMockServerPlayerInLevel();
            ExpeditionEventHandler.onPlayerClone(new net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone(clone,p,true));
            h.assertTrue(FrontierThreat.attention(clone,dim)==215,"death must retain exposure history");
            data.setDimensionCleared(dim);service.tickPlayer(p,data,true);
            h.assertTrue(FrontierThreat.attention(p,dim)==0,"world liberation must release personal attention");
        } finally {end(data);}
        h.succeed();
    }
    @GameTest(template="arena",batch="frontier_liberation")
    public static void routineRunsCannotLiberateButEveryDimensionHasBossMission(GameTestHelper h) {
        var data=begin(h);var manager=ExpeditionManager.get(h.getLevel().getServer());
        try {
            for(int i=0;i<8;i++)data.recordCampaignCompletion("minecraft:overworld");
            h.assertTrue(!data.isDimensionCleared("minecraft:overworld") && data.campaignState("minecraft:overworld")==CampaignState.ESTABLISHED,
                    "routine farming must never replace defeating the bound guardian");
            data.resetCampaign("minecraft:overworld");
            var nether=manager.listStartable().stream().filter(d->d.dimension().equals("minecraft:the_nether")).toList();
            h.assertTrue(!nether.isEmpty() && nether.stream().allMatch(d->d.requires().contains("expeditions:field_clear")),
                    "remote deployment must explain home liberation through normal requirements");
            h.assertTrue(nether.stream().anyMatch(d->d.bigBad() && d.minCampaignState()==CampaignState.ESTABLISHED),
                    "an automatic dimension must have a proof-bound liberation mission");
            data.recordCampaignCompletion("minecraft:overworld");
            h.assertTrue(manager.listStartable().stream().filter(d->d.dimension().equals("minecraft:the_nether"))
                    .anyMatch(d->!d.requires().contains("expeditions:field_clear")),"home liberation must open deployments for everyone");
        } finally {end(data);}
        h.succeed();
    }
    @GameTest(template="arena",batch="frontier_refuge")
    public static void refugeAndLiberationRefuseHeartSeedingAtTheSource(GameTestHelper h) {
        var data=begin(h);var level=h.getLevel();
        try {
            ExpeditionTuning.reload(Map.of(ResourceLocation.parse("minecraft:overworld"),JsonParser.parseString(
                    "{\"frontier\":{\"home_refuge_radius\":128}}")));
            h.assertTrue(FrontierThreat.refuge(level,level.getSharedSpawnPos()),"world spawn must be a preparation refuge");
            h.assertTrue(Microbiome.trySpawnHeart(level,level.getSharedSpawnPos(),level.random).isEmpty(),"ambient hearts cannot seed in the refuge");
            data.setDimensionCleared("minecraft:overworld");
            h.assertTrue(Microbiome.trySpawnHeart(level,h.absolutePos(new BlockPos(5,2,5)),level.random).isEmpty(),
                    "direct heart seeding must reject liberated dimensions");
        } finally {end(data);}
        h.succeed();
    }
    @GameTest(template="arena",batch="frontier_hunt")
    public static void reckoningSpawnsOneOwnedGuardianAndNeverGrantsLiberation(GameTestHelper h) {
        var data=begin(h);var level=h.getLevel();var p=h.makeMockServerPlayerInLevel();
        var difficulty=level.getDifficulty();boolean spawning=level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING);
        try {
            level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(true,level.getServer());
            var center=h.absolutePos(new BlockPos(8,1,8));
            for(int x=-20;x<=20;x++)for(int z=-20;z<=20;z++) {
                level.setBlockAndUpdate(center.offset(x,-1,z),Blocks.STONE.defaultBlockState());
                for(int y=0;y<4;y++)level.setBlockAndUpdate(center.offset(x,y,z),Blocks.AIR.defaultBlockState());
            }
            p.teleportTo(center.getX()+.5,center.getY(),center.getZ()+.5);
            var entry=FrontierThreat.record(p,"minecraft:overworld");entry.putInt("seconds",600);
            var service=new FrontierThreat();service.tickPlayer(p,data,true);
            var tracked=entry.getList("hunters",Tag.TAG_COMPOUND);
            h.assertTrue(tracked.size()==1 && tracked.getCompound(0).getBoolean("boss") && tracked.getCompound(0).hasUUID("id"),
                    "reckoning must spawn one guardian: "+entry+" difficulty="+level.getDifficulty()+" spawning="+level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING));
            service.tickPlayer(p,data,true);h.assertTrue(tracked.size()==1,"ticks must not duplicate the guardian");
            h.assertTrue(!data.isDimensionCleared("minecraft:overworld"),"hunting echoes cannot supply campaign proof");
            var saved=new net.minecraft.nbt.CompoundTag();p.saveWithoutId(saved);
            h.assertTrue(saved.getCompound("NeoForgeData").getCompound(FrontierThreat.ROOT).getCompound("minecraft:overworld")
                    .getList("hunters",Tag.TAG_COMPOUND).size()==1,"ownership reservations must survive player saves");
        } finally {
            level.getServer().setDifficulty(difficulty,true);
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(spawning,level.getServer());
            end(data);
        }
        h.succeed();
    }
    @GameTest(template="arena",batch="frontier_cleanup")
    public static void orphanHuntersRetainOwnershipAcrossSaveAndDiscardOnLoad(GameTestHelper h) {
        var data=begin(h);var mob=h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE,new BlockPos(3,2,3));
        net.minecraft.world.entity.Entity restored=null;
        try {
            mob.setNoAi(true);mob.getPersistentData().putUUID(FrontierThreat.OWNER,java.util.UUID.randomUUID());
            mob.getPersistentData().putLong("expeditions:frontier_until",h.getLevel().getGameTime()+1800);
            var tag=new net.minecraft.nbt.CompoundTag();mob.save(tag);
            restored=net.minecraft.world.entity.EntityType.loadEntityRecursive(tag,h.getLevel(),e->e);
            h.assertTrue(restored instanceof net.minecraft.world.entity.Mob,"owned hunters must preserve their native mob type");
            var loaded=(net.minecraft.world.entity.Mob)restored;
            h.assertTrue(loaded.getPersistentData().hasUUID(FrontierThreat.OWNER),"chunk saves must retain ownership");
            h.assertTrue(FrontierThreat.tickHunter(loaded) && loaded.isRemoved(),"a returning hunter without a valid online owner must discard");
        } finally {mob.discard();if(restored!=null)restored.discard();end(data);}
        h.succeed();
    }
}
