package com.expeditions.server;

import com.expeditions.ExpeditionsConfig;
import com.expeditions.core.FrontierAttention;
import com.expeditions.core.FrontierAttention.Stage;
import com.expeditions.core.CampaignState;
import com.expeditions.registry.ModAttachments;
import com.expeditions.registry.ModSounds;
import com.expeditions.registry.ModEntities;
import com.expeditions.server.data.ExpeditionTuning;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameRules;

/** Server-instance service. Player residence survives relog/death; owned hunters expire even after chunk unload. */
public final class FrontierThreat {
    public static final String ROOT="expeditions:frontier";
    public static final String OWNER="expeditions:frontier_owner";
    private static final String UNTIL="expeditions:frontier_until";
    private static volatile boolean suppressed;
    private final Map<UUID,ServerBossEvent> bars=new HashMap<>();
    public static void suppressForTests(boolean value) { suppressed=value && Boolean.getBoolean("expeditions.gameTestRun"); }
    public static boolean rulesActive() { return !suppressed; }
    public static boolean enabled(ServerLevel level) {
        return !suppressed && (!Stillness.shelters(level) || Stillness.hasBreach(level)) && ExpeditionTuning.bool(level.dimension(),"frontier.enabled",ExpeditionsConfig.frontierEnabled());
    }
    public static int limit(ServerLevel level) {
        return Math.clamp(ExpeditionTuning.integer(level.dimension(),"frontier.guardian_after_seconds",ExpeditionsConfig.frontierLimit()),60,7200);
    }
    public static boolean refuge(ServerLevel level,BlockPos pos) {
        if(!enabled(level) || !level.dimension().equals(Level.OVERWORLD)) return false;
        int radius=Math.clamp(ExpeditionTuning.integer(level.dimension(),"frontier.home_refuge_radius",ExpeditionsConfig.frontierHomeRadius()),0,1024);
        var spawn=level.getSharedSpawnPos();long dx=(long)pos.getX()-spawn.getX(),dz=(long)pos.getZ()-spawn.getZ();
        return radius>0 && dx*dx+dz*dz<=(long)radius*radius;
    }
    public static CompoundTag residence(ServerPlayer player) {
        CompoundTag data=player.getPersistentData();
        if(!data.contains(ROOT,Tag.TAG_COMPOUND)) data.put(ROOT,new CompoundTag());
        return data.getCompound(ROOT);
    }
    public static CompoundTag record(ServerPlayer player,String dimension) {
        var root=residence(player);
        if(!root.contains(dimension,Tag.TAG_COMPOUND)) root.put(dimension,new CompoundTag());
        return root.getCompound(dimension);
    }
    public static int attention(ServerPlayer p,String dim) { return Math.max(0,record(p,dim).getInt("seconds")); }
    public static void relief(ServerPlayer p,String dim,int seconds) {
        var entry=record(p,dim);entry.putInt("seconds",Math.max(0,entry.getInt("seconds")-Math.max(0,seconds)));
    }
    public void tick(MinecraftServer server,ExpeditionSavedData data) {
        var online=new HashSet<UUID>();
        for(ServerPlayer player:server.getPlayerList().getPlayers()) {
            online.add(player.getUUID());tickPlayer(player,data,false);
        }
        bars.entrySet().removeIf(e->{if(online.contains(e.getKey())) return false;e.getValue().removeAllPlayers();return true;});
    }
    /** Test driver may include mock creative players; the production caller always passes false. */
    public void tickPlayer(ServerPlayer player,ExpeditionSavedData data,boolean testSurvival) {
        String dim=player.level().dimension().location().toString();var level=player.serverLevel();
        boolean cleared=data.isDimensionCleared(dim);
        boolean exposed=enabled(level) && !cleared && player.isAlive() && !player.isSpectator()
                && (testSurvival || !player.isCreative()) && !refuge(level,player.blockPosition()) && !Stillness.suppresses(level,player.blockPosition());
        var root=residence(player);
        // Portal hopping cools the old clock instead of resetting it. Offline time never clears attention.
        for(String other:new HashSet<>(root.getAllKeys())) if(!other.equals(dim) && root.get(other) instanceof CompoundTag old)
            old.putInt("seconds",Math.max(0,old.getInt("seconds")-2));
        var entry=record(player,dim);int max=limit(level);int before=Math.clamp(entry.getInt("seconds"),0,max);
        int rate=(level.dimension().equals(Level.OVERWORLD)?2:1)*(data.campaignState(dim)==CampaignState.ESTABLISHED?2:1);
        int cycle=Math.floorMod(entry.getInt("cycle")+1,rate);entry.putInt("cycle",cycle);
        boolean warded=exposed && com.expeditions.block.entity.FieldWardBlockEntity.covers(level,player.blockPosition());
        int after=cleared?0:FrontierAttention.step(before,exposed?(warded?0:cycle==0?1:0):-2,max);entry.putInt("seconds",after);
        if(!exposed) { hide(player);cleanupVisible(player,entry,level);return; }
        Stage stage=FrontierAttention.stage(after,max);
        int remaining=Math.max(0,(FrontierAttention.nextThreshold(after,max)-after)*rate);
        var bar=bars.computeIfAbsent(player.getUUID(),id->new ServerBossEvent(Component.empty(),BossEvent.BossBarColor.YELLOW,BossEvent.BossBarOverlay.NOTCHED_10));
        bar.addPlayer(player);bar.setProgress((float)after/max);
        bar.setColor(stage.ordinal()>=Stage.OVERRUN.ordinal()?BossEvent.BossBarColor.RED:BossEvent.BossBarColor.YELLOW);
        var stageName=Component.translatable("frontier.expeditions.stage."+stage.name().toLowerCase(Locale.ROOT));
        bar.setName(stage==Stage.RECKONING?Component.translatable("frontier.expeditions.bar_reckoning"):
                Component.translatable("frontier.expeditions.bar",stageName,
                        Component.translatable("frontier.expeditions.stage."+Stage.values()[stage.ordinal()+1].name().toLowerCase(Locale.ROOT)),
                        remaining/60,String.format(Locale.ROOT,"%02d",remaining%60)));
        if(before==0 || FrontierAttention.stage(before,max)!=stage) {
            player.displayClientMessage(Component.translatable("frontier.expeditions.warning."+stage.name().toLowerCase(Locale.ROOT)), true);
            level.playSound(null,player.blockPosition(),ModSounds.FRONTIER_WARNING.get(),SoundSource.HOSTILE,.6f,stage==Stage.RECKONING?.5f:.8f);
        }
        if(stage.ordinal()>=Stage.HUNTED.ordinal()) {
            JournalDiscovery.unlock(player,com.expeditions.core.JournalChapter.PRESSURE);
            hunt(player,entry,level,stage);
        }
    }
    private void hide(ServerPlayer p) { var bar=bars.remove(p.getUUID());if(bar!=null)bar.removeAllPlayers(); }
    private static void cleanupVisible(ServerPlayer player,CompoundTag entry,ServerLevel level) {
        var list=entry.getList("hunters",Tag.TAG_COMPOUND);
        for(Tag value:list) {var t=(CompoundTag)value;if(t.hasUUID("id")) {var e=level.getEntity(t.getUUID("id"));if(e!=null && owned(e,player.getUUID()))e.discard();}}
        // Keep unloaded records until expiry, so leaving and returning cannot duplicate a parked wave.
    }
    private static boolean owned(net.minecraft.world.entity.Entity e,UUID owner) {
        var t=e.getPersistentData();return t.hasUUID(OWNER) && t.getUUID(OWNER).equals(owner);
    }
    private static void hunt(ServerPlayer player,CompoundTag entry,ServerLevel level,Stage stage) {
        long now=level.getGameTime();var list=entry.getList("hunters",Tag.TAG_COMPOUND);
        for(int i=list.size()-1;i>=0;i--) {
            var t=list.getCompound(i);var e=t.hasUUID("id")?level.getEntity(t.getUUID("id")):null;
            if(!t.hasUUID("id") || now>=t.getLong("until") || e!=null && (!e.isAlive() || e.isRemoved())) list.remove(i);
        }
        entry.put("hunters",list);
        if(level.getDifficulty()==Difficulty.PEACEFUL || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) return;
        boolean hasBoss=false;for(Tag t:list) if(((CompoundTag)t).getBoolean("boss"))hasBoss=true;
        boolean bossDue=stage==Stage.RECKONING && !hasBoss && now>=entry.getLong("nextBoss");
        boolean waveDue=now>=entry.getLong("nextWave");
        if(!bossDue && !waveDue) return;
        // A local cap also prevents overlapping players from multiplying one ambush into an army.
        int nearby=level.getEntitiesOfClass(Mob.class,player.getBoundingBox().inflate(48),m->m.getPersistentData().hasUUID(OWNER)).size();
        int count=bossDue?1:stage==Stage.HUNTED?2:3;
        int cap=Math.clamp(ExpeditionTuning.integer(level.dimension(),"frontier.max_hunters",7),1,12);
        for(int i=0;i<count && list.size()<cap && nearby<cap;i++) {
            Mob mob=bossDue?MobSpawns.trySpawn(ModEntities.ROOTBOUND_GUARDIAN.get(),level,player.blockPosition(),18)
                    :MobSpawns.trySpawnFromPool(level,player.blockPosition(),18,level.dimension().location().toString());
            if(mob==null) break; // No safe body fit: retry after cooldown, never place through terrain.
            mob.finalizeSpawn(level,level.getCurrentDifficultyAt(mob.blockPosition()),net.minecraft.world.entity.MobSpawnType.EVENT,null);
            long until=now+(bossDue?2400:1800);var tag=mob.getPersistentData();tag.putUUID(OWNER,player.getUUID());tag.putLong(UNTIL,until);
            mob.setPersistenceRequired();mob.setTarget(player);mob.setData(ModAttachments.ASSIMILATED,bossDue?3:1);
            if(mob instanceof net.minecraft.world.entity.NeutralMob neutral) {neutral.setPersistentAngerTarget(player.getUUID());neutral.setRemainingPersistentAngerTime(2400);}
            if(!level.addFreshEntity(mob)) {mob.discard();break;}
            HiveMind.alert(mob,player);
            var tracked=new CompoundTag();tracked.putUUID("id",mob.getUUID());tracked.putLong("until",until);tracked.putBoolean("boss",bossDue);list.add(tracked);nearby++;
            if(bossDue) {
                entry.putLong("nextBoss",now+3600);
                player.displayClientMessage(Component.translatable("frontier.expeditions.pursuer"), true);
            }
        }
        if(bossDue) entry.putLong("nextBoss",Math.max(entry.getLong("nextBoss"),now+400));
        if(waveDue || bossDue) entry.putLong("nextWave",now+(stage==Stage.HUNTED?1200:700));
    }
    /** Runs on loaded entities, so stale ownership still cleans up after logout or chunk reload. */
    public static boolean tickHunter(Mob mob) {
        var tag=mob.getPersistentData();if(!tag.hasUUID(OWNER) || !(mob.level() instanceof ServerLevel level))return false;
        var player=level.getServer().getPlayerList().getPlayer(tag.getUUID(OWNER));
        var data=ExpeditionManager.get(level.getServer()).savedData();String dim=level.dimension().location().toString();
        if(!enabled(level) || player==null || !player.isAlive() || player.isCreative() || player.isSpectator()
                || player.serverLevel()!=level || data.isDimensionCleared(dim) || refuge(level,player.blockPosition())
                || attention(player,dim)<limit(level)*4/10 || mob.distanceToSqr(player)>96*96 || level.getGameTime()>=tag.getLong(UNTIL)) {
            mob.discard();return true;
        }
        if(mob.getTarget()==null || !mob.getTarget().isAlive())mob.setTarget(player);
        return false;
    }
}
