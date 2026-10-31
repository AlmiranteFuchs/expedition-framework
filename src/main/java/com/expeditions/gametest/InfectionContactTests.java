package com.expeditions.gametest;

import com.expeditions.ExpeditionsConfig;
import com.expeditions.block.entity.AssimilationHeartBlockEntity;
import com.expeditions.block.entity.AssimilationLayerBlockEntity;
import com.expeditions.registry.*;
import com.expeditions.server.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("expeditions_asm")
@PrefixGameTestTemplate(false)
public final class InfectionContactTests {
    private static void begin(ServerLevel level) {
        level.getServer().overworld().getDataStorage().computeIfAbsent(ExpeditionSavedData.FACTORY,
                ExpeditionSavedData.DATA_NAME).resetCampaign(level.dimension().location().toString());
        Assimilation.suppressForTests(false);
    }
    private static BlockState layer(Direction support) {
        return ModBlocks.ASSIMILATION_LAYER.get().defaultBlockState().setValue(MultifaceBlock.getFaceProperty(support),true);
    }
    private static int interval() { return ExpeditionsConfig.infectionIntervalTicks(); }
    private static void cadence(Mob mob) { mob.tickCount=interval(); Assimilation.onLivingTick(mob); }

    @GameTest(template="arena",batch="infection_surface")
    public static void directSurfaceAndFrayedBoundaryAreRequired(GameTestHelper h) {
        var level=h.getLevel(); var p=h.absolutePos(new BlockPos(4,2,4));
        var player=h.makeMockServerPlayerInLevel(); begin(level);
        try {
            level.setBlockAndUpdate(p.below(),Blocks.OAK_PLANKS.defaultBlockState());
            level.setBlockAndUpdate(p,layer(Direction.DOWN));
            player.teleportTo(p.getX()+.5,p.getY()+.2,p.getZ()+.5);
            Assimilation.touchLayer(level,p,level.getBlockState(p),player);
            player.tickCount=interval(); Assimilation.onPlayerTick(player);
            h.assertTrue(Assimilation.infection(player)==0,"hovering near the surface cannot infect");
            player.teleportTo(p.getX()-.29,p.getY(),p.getZ()+.5);
            Assimilation.touchLayer(level,p,level.getBlockState(p),player);
            player.tickCount=interval()*2; Assimilation.onPlayerTick(player);
            h.assertTrue(Assimilation.infection(player)==0,"the unpainted outer rim cannot infect");
            player.teleportTo(p.getX()+.5,p.getY(),p.getZ()+.5);
            Assimilation.touchLayer(level,p,level.getBlockState(p),player);
            player.tickCount=interval()*3; Assimilation.onPlayerTick(player);
            h.assertTrue(Assimilation.infection(player)>0,"touching actual tissue must infect");
        } finally { Assimilation.suppressForTests(true);level.removeBlock(p,false);level.removeBlock(p.below(),false); }
        h.succeed();
    }

    @GameTest(template="arena",batch="infection_faces")
    public static void contactWorksOnAllSixAttachedFaces(GameTestHelper h) {
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(4,4,4)); begin(level);
        try {
            for(Direction normal:Direction.values()) {
                Direction support=normal.getOpposite();
                level.setBlockAndUpdate(p.relative(support),Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(p,layer(support));
                Mob host=EntityType.SILVERFISH.create(level);
                double x=.5,y=.35,z=.5;
                switch(normal) {
                    case UP -> y=0; case DOWN -> y=.8;
                    case NORTH -> z=.98; case SOUTH -> z=.02;
                    case WEST -> x=.98; case EAST -> x=.02;
                }
                host.setPos(p.getX()+x,p.getY()+y,p.getZ()+z);
                Assimilation.touchLayer(level,p,level.getBlockState(p),host); cadence(host);
                h.assertTrue(Assimilation.infection(host)>0,"contact failed on "+normal);
                level.removeBlock(p,false);level.removeBlock(p.relative(support),false);
            }
        } finally { Assimilation.suppressForTests(true);level.removeBlock(p,false);
            for(Direction d:Direction.values()) level.removeBlock(p.relative(d),false); }
        h.succeed();
    }

    @GameTest(template="arena",batch="infection_ward")
    public static void effectAndEquippedCharmBlockContactButInventoryDoesNot(GameTestHelper h) {
        var level=h.getLevel(); var player=h.makeMockServerPlayerInLevel(); begin(level);
        try {
            player.addEffect(new MobEffectInstance(ModEffects.ASSIMILATION_DISRUPTION,200));
            Assimilation.onContact(player); player.tickCount=interval(); Assimilation.onPlayerTick(player);
            h.assertTrue(Assimilation.infection(player)==0,"given disruption must prevent exposure");
            player.removeEffect(ModEffects.ASSIMILATION_DISRUPTION);
            player.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(ModItems.DISRUPTION_CHARM.get()));
            Assimilation.onContact(player);player.tickCount=interval()*2;Assimilation.onPlayerTick(player);
            h.assertTrue(Assimilation.infection(player)==0,"offhand charm must prevent exposure");
            Assimilation.onLivingTick(player);
            h.assertTrue(player.hasEffect(ModEffects.ASSIMILATION_DISRUPTION),"equipped charm must grant the effect");
            player.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);player.removeEffect(ModEffects.ASSIMILATION_DISRUPTION);
            player.getInventory().add(new ItemStack(ModItems.DISRUPTION_CHARM.get()));
            h.assertTrue(!AssimilationProtection.protectedFromInfection(player),"a packed charm is not equipped");
            Assimilation.onContact(player);player.tickCount=interval()*3;Assimilation.onPlayerTick(player);
            h.assertTrue(Assimilation.infection(player)>0,"unprotected touch must still infect");
        } finally { Assimilation.suppressForTests(true); }
        h.succeed();
    }

    @GameTest(template="arena",batch="infection_cold")
    public static void deadHeartScarsAreNotInfectious(GameTestHelper h) {
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(4,2,4));var heart=p.east(3);begin(level);
        Mob host=EntityType.COW.create(level);host.setPos(p.getX()+.5,p.getY(),p.getZ()+.5);
        try {
            level.setBlockAndUpdate(p.below(),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(p,layer(Direction.DOWN));
            ((AssimilationLayerBlockEntity)level.getBlockEntity(p)).setHeart(heart);
            Assimilation.touchLayer(level,p,level.getBlockState(p),host);cadence(host);
            h.assertTrue(Assimilation.infection(host)==0,"cold scars must not infect");
            level.setBlockAndUpdate(heart,ModBlocks.ASSIMILATION_HEART.get().defaultBlockState());
            Assimilation.touchLayer(level,p,level.getBlockState(p),host);cadence(host);
            h.assertTrue(Assimilation.infection(host)>0,"a living colony's tissue must infect");
        } finally { Assimilation.suppressForTests(true);level.removeBlock(p,false);level.removeBlock(p.below(),false);level.removeBlock(heart,false); }
        h.succeed();
    }

    @GameTest(template="arena",batch="infection_variant")
    public static void infectedMobsKeepTheirVariantAcrossSaveAndSeparation(GameTestHelper h) {
        var level=h.getLevel(); begin(level); Mob host=EntityType.COW.create(level);
        try {
            Assimilation.infect(host,ExpeditionsConfig.infectionStage1At()-.5);Assimilation.onContact(host);cadence(host);
            h.assertTrue(host.getData(ModAttachments.ASSIMILATED)==1,"stage one must create an infected variant");
            var tag=new CompoundTag();host.saveWithoutId(tag);tag.putString("id","minecraft:cow");
            Mob loaded=(Mob)EntityType.loadEntityRecursive(tag,level,e->e);
            h.assertTrue(loaded!=null && loaded.getData(ModAttachments.ASSIMILATED)==1,"infected appearance must persist");
            cadence(loaded);
            h.assertTrue(loaded.getData(ModAttachments.ASSIMILATED)==1 && loaded.getType()==EntityType.COW,
                    "separation must retain the native host's infected variant");
        } finally { Assimilation.suppressForTests(true); }
        h.succeed();
    }

    @GameTest(template="arena",batch="infection_carrier")
    public static void infectedBodiesTransmitByTouchNotProximity(GameTestHelper h) {
        var level=h.getLevel();begin(level);
        Mob source=h.spawn(EntityType.COW,new BlockPos(3,2,3));Mob host=h.spawn(EntityType.COW,new BlockPos(6,2,3));
        source.setNoAi(true);host.setNoAi(true);source.setData(ModAttachments.ASSIMILATED,1);
        try {
            source.tickCount=8-Math.floorMod(source.getId(),4);Assimilation.onLivingTick(source);cadence(host);
            h.assertTrue(Assimilation.infection(host)==0,"nearby carriers must not infect");
            host.setPos(source.getX()+.2,source.getY(),source.getZ());
            source.tickCount+=4;Assimilation.onLivingTick(source);cadence(host);
            h.assertTrue(Assimilation.infection(host)>0,"overlapping bodies must transmit exposure");
        } finally { Assimilation.suppressForTests(true);source.discard();host.discard(); }
        h.succeed();
    }

    @GameTest(template="arena",batch="infection_hive")
    public static void hiveSharesThreatsAndAppliesOneMildSlowdown(GameTestHelper h) {
        var level=h.getLevel();begin(level);
        Mob source=h.spawn(EntityType.COW,new BlockPos(3,2,3));Mob peer=h.spawn(EntityType.COW,new BlockPos(6,2,3));
        Mob threat=h.spawn(EntityType.ZOMBIE,new BlockPos(8,2,3));
        source.setNoAi(true);peer.setNoAi(true);threat.setNoAi(true);
        source.setData(ModAttachments.ASSIMILATED,1);peer.setData(ModAttachments.ASSIMILATED,1);
        try {
            source.setLastHurtByMob(threat);
            h.assertTrue(HiveMind.targetFor(peer)==threat,"a peer must share the same perceived attacker");
            double original=peer.getAttributeValue(Attributes.MOVEMENT_SPEED);
            peer.tickCount=40-Math.floorMod(peer.getId(),20);HiveMind.tick(peer);
            h.assertTrue(Math.abs(peer.getAttributeValue(Attributes.MOVEMENT_SPEED)-original*.85)<.00001,
                    "infected bodies must move fifteen percent slower");
            peer.tickCount+=20;HiveMind.tick(peer);
            h.assertTrue(Math.abs(peer.getAttributeValue(Attributes.MOVEMENT_SPEED)-original*.85)<.00001,"slowdown must not stack");
            h.assertTrue(peer.getTarget()==threat && peer.getType()==EntityType.COW,"shared pursuit must preserve native species");
            peer.addEffect(new MobEffectInstance(ModEffects.ASSIMILATION_DISRUPTION,100));peer.tickCount+=20;HiveMind.tick(peer);
            h.assertTrue(HiveMind.targetFor(peer)==null && Math.abs(peer.getAttributeValue(Attributes.MOVEMENT_SPEED)-original)<.00001,
                    "disruption must interrupt hive coordination and its slowdown");
            peer.removeEffect(ModEffects.ASSIMILATION_DISRUPTION);threat.discard();
            peer.setPos(source.getX()+7,source.getY(),source.getZ());
            h.assertTrue(HiveMind.leaderFor(peer)==source,"idle infected bodies must have a shared regrouping leader");
            h.assertTrue(new com.expeditions.entity.HiveMindGoal(peer).canUse(),"idle peers must regroup instead of wandering independently");
        } finally { Assimilation.suppressForTests(true);source.discard();peer.discard();threat.discard(); }
        h.succeed();
    }

    @GameTest(template="arena",batch="infection_growth")
    public static void colonyGrowsAutonomouslyAndSeeksNonPlayerHosts(GameTestHelper h) {
        var level=h.getLevel();begin(level);var origin=h.absolutePos(new BlockPos(1,0,1));var p=origin.offset(6,0,6);
        Mob host=null;
        try {
            for(int x=0;x<13;x++) for(int z=0;z<13;z++) {
                level.setBlockAndUpdate(origin.offset(x,0,z),Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(origin.offset(x,1,z),Blocks.AIR.defaultBlockState());
            }
            level.setBlockAndUpdate(p,ModBlocks.ASSIMILATION_HEART.get().defaultBlockState());
            var heart=(AssimilationHeartBlockEntity)level.getBlockEntity(p);
            heart.pulse(level);h.assertTrue(heart.coverage(level)>0,"growth must not require a nearby host");
            host=EntityType.COW.create(level);host.setNoAi(true);host.setPos(p.getX()+5.5,p.getY()+1,p.getZ()+.5);level.addFreshEntity(host);
            for(int i=0;i<22 && heart.coverage(level)<14;i++) heart.pulse(level);
            long east=heart.colony().stream().filter(c->c.getX()>p.getX()).count();
            long west=heart.colony().stream().filter(c->c.getX()<p.getX()).count();
            h.assertTrue(east>=west+3,"growth must seek a cow without requiring a player: "+east+"/"+west);
            for(var cell:heart.colony()) level.removeBlock(cell,false);
        } finally { Assimilation.suppressForTests(true);if(host!=null)host.discard();level.removeBlock(p,false); }
        h.succeed();
    }

}
