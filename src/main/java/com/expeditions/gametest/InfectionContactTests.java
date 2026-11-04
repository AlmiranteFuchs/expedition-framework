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
            h.assertTrue(Math.abs(peer.getAttributeValue(Attributes.MOVEMENT_SPEED)-original*.70)<.00001,
                    "infected bodies must move fifteen percent slower");
            peer.tickCount+=20;HiveMind.tick(peer);
            h.assertTrue(Math.abs(peer.getAttributeValue(Attributes.MOVEMENT_SPEED)-original*.70)<.00001,"slowdown must not stack");
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

    @GameTest(template="arena",batch="hive_consensus")
    public static void hiveOverridesConflictingTargetsAndAllHeadsTrackOneHost(GameTestHelper h) {
        var level=h.getLevel();begin(level);
        Mob a=h.spawn(EntityType.COW,new BlockPos(2,2,2));
        Mob b=h.spawn(EntityType.SHEEP,new BlockPos(5,2,3));
        Mob c=h.spawn(EntityType.ZOMBIE,new BlockPos(7,2,2));
        Mob host=h.spawn(EntityType.PIG,new BlockPos(9,2,5));
        Mob rival=h.spawn(EntityType.PIG,new BlockPos(8,2,8));
        var pack=java.util.List.of(a,b,c);var all=java.util.List.of(a,b,c,host,rival);
        all.forEach(e->e.setNoAi(true));pack.forEach(e->e.setData(ModAttachments.ASSIMILATED,1));
        try {
            a.setTarget(host);b.setTarget(rival);c.setTarget(rival);
            HiveMind.tick(c);
            for(Mob peer:pack) h.assertTrue(peer.getTarget()==host,"one deterministic pack decision must replace conflicting targets");
            // Native combat/look AI may choose something else between hive updates.
            b.setTarget(rival);HiveMind.tick(b);
            h.assertTrue(b.getTarget()==host,"private target changes must not steal the pack's focus");
            for(int tick=0;tick<20;tick++) for(Mob peer:pack) HiveMind.tick(peer);
            for(Mob peer:pack) {
                double expected=Math.toDegrees(Math.atan2(host.getZ()-peer.getZ(),host.getX()-peer.getX()))-90;
                h.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(peer.getYHeadRot()-(float)expected))<.01,
                        "every head must actually face the shared world-space host, even without a running movement goal");
                double horizontal=Math.sqrt(peer.distanceToSqr(host.getX(),peer.getY(),host.getZ()));
                double pitch=-Math.toDegrees(Math.atan2(host.getEyeY()-peer.getEyeY(),horizontal));
                h.assertTrue(Math.abs(peer.getXRot()-pitch)<.01,"shared attention must track the host eye height too");
            }
            var saved=new CompoundTag();a.save(saved);
            h.assertTrue(!saved.getCompound("neoforge:attachments").contains("expeditions:hive_focus"),
                    "transient attention must not serialize stale entity references");
            host.discard();b.setTarget(rival);HiveMind.tick(a);
            for(Mob peer:pack) h.assertTrue(peer.getTarget()==rival,"a lost host must cause a shared retarget");
        } finally { Assimilation.suppressForTests(true);all.forEach(Mob::discard); }
        h.succeed();
    }

    @GameTest(template="arena",batch="hive_alert")
    public static void hiveAttackAlertRetargetsRunningGoalAndWardDoesNotHideHost(GameTestHelper h) {
        var level=h.getLevel();begin(level);
        Mob a=h.spawn(EntityType.COW,new BlockPos(2,2,2));Mob b=h.spawn(EntityType.SHEEP,new BlockPos(5,2,3));
        Mob old=h.spawn(EntityType.PIG,new BlockPos(8,2,4));Mob attacker=h.spawn(EntityType.ZOMBIE,new BlockPos(7,2,7));
        var all=java.util.List.of(a,b,old,attacker);all.forEach(e->e.setNoAi(true));
        a.setData(ModAttachments.ASSIMILATED,1);b.setData(ModAttachments.ASSIMILATED,1);
        try {
            a.setTarget(old);HiveMind.tick(a);
            var goal=new com.expeditions.entity.HiveMindGoal(b);
            h.assertTrue(goal.canUse(),"the follower must begin pursuing the original focus");
            attacker.addEffect(new MobEffectInstance(ModEffects.ASSIMILATION_DISRUPTION,100));
            // Real damage event path; the victim's current focus must not hide a fresh threat.
            a.hurt(level.damageSources().mobAttack(attacker),1);
            h.assertTrue(a.getTarget()==attacker && b.getTarget()==attacker,"a hit must immediately alert the pack");
            goal.tick();
            h.assertTrue(b.getTarget()==attacker,"an already-running goal must not restore its cached old host");
            h.assertTrue(HiveMind.validHost(b,attacker),"disruption blocks new infection, not enemies' ability to see the wearer");
            b.addEffect(new MobEffectInstance(ModEffects.ASSIMILATION_DISRUPTION,100));HiveMind.tick(b);
            h.assertTrue(HiveMind.targetFor(b)==null && b.getTarget()==null,"a disrupted member must release shared attention");
        } finally { Assimilation.suppressForTests(true);all.forEach(Mob::discard); }
        h.succeed();
    }

    @GameTest(template="arena",batch="hive_live_navigation",timeoutTicks=120)
    public static void hivePursuitWorksOnBothNativeAiTickSchedules(GameTestHelper h) {
        var level=h.getLevel();begin(level);
        for(int x=1;x<=14;x++) for(int z=1;z<=14;z++) {
            level.setBlockAndUpdate(h.absolutePos(new BlockPos(x,0,z)),Blocks.STONE.defaultBlockState());
            for(int y=1;y<=3;y++) level.setBlockAndUpdate(h.absolutePos(new BlockPos(x,y,z)),Blocks.AIR.defaultBlockState());
        }
        Mob a=h.spawn(EntityType.COW,new BlockPos(3,1,3));Mob b=h.spawn(EntityType.COW,new BlockPos(7,1,3));
        Mob host=h.spawn(EntityType.PIG,new BlockPos(5,1,12));
        host.setNoAi(true);host.setInvulnerable(true);
        host.addEffect(new MobEffectInstance(ModEffects.ASSIMILATION_DISRUPTION,1000));
        var all=java.util.List.of(a,b,host);
        Runnable cleanup=()->{Assimilation.suppressForTests(true);all.forEach(Mob::discard);};
        try {
            h.assertTrue(Math.floorMod(a.getId(),2)!=Math.floorMod(b.getId(),2),"fixture must cover both native AI schedules");
            for(Mob peer:java.util.List.of(a,b)) {
                peer.setData(ModAttachments.ASSIMILATED,1);peer.setTarget(host);
                peer.tickCount=40-Math.floorMod(peer.getId(),20);HiveMind.tick(peer);
            }
            double startA=a.distanceTo(host),startB=b.distanceTo(host);
            h.runAtTickTime(80,()->{
                try {
                    h.assertTrue(a.distanceTo(host)<startA-1 && b.distanceTo(host)<startB-1,
                            "both infected animals must navigate toward the common host, regardless of staggered AI scheduling: "
                                    +a.distanceTo(host)+"/"+b.distanceTo(host));
                    h.assertTrue(a.getTarget()==host && b.getTarget()==host,"native AI must retain common attention during pursuit");
                    for(Mob peer:java.util.List.of(a,b)) {
                        double yaw=Math.toDegrees(Math.atan2(host.getZ()-peer.getZ(),host.getX()-peer.getX()))-90;
                        h.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(peer.getYHeadRot()-(float)yaw))<5,
                                "native AI must not steal head tracking between shared decisions");
                    }
                    h.succeed();
                } finally { cleanup.run(); }
            });
        } catch(RuntimeException | Error failure) { cleanup.run();throw failure; }
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
