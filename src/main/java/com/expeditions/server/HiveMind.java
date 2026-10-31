package com.expeditions.server;

import com.expeditions.entity.HiveMindGoal;
import com.expeditions.registry.ModAttachments;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

/** Local, decentralized hive awareness: no global targets, cross-world maps or forced chunk loads. */
public final class HiveMind {
    private static final ResourceLocation SLOW = ResourceLocation.parse("expeditions:hive_languor");
    private HiveMind() { }
    public static boolean infected(LivingEntity entity) {
        return entity.getData(ModAttachments.ASSIMILATED)>0;
    }
    public static boolean active(Mob mob) {
        return infected(mob) && mob.isAlive() && !AssimilationProtection.protectedFromInfection(mob);
    }
    public static boolean validHost(Mob mob,LivingEntity candidate) {
        return candidate!=null && candidate!=mob && candidate.isAlive() && candidate.level()==mob.level()
                && mob.distanceToSqr(candidate)<=32*32
                && !(candidate instanceof ArmorStand) && !candidate.isSpectator()
                && !(candidate instanceof Mob && infected(candidate)) && !mob.isAlliedTo(candidate)
                && !AssimilationProtection.protectedFromInfection(candidate);
    }
    public static void tick(Mob mob) {
        if((mob.tickCount+mob.getId())%20!=0) return;
        var speed=mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if(!active(mob)) {
            if(speed!=null) speed.removeModifier(SLOW);
            return;
        }
        if(speed!=null && speed.getModifier(SLOW)==null)
            speed.addTransientModifier(new AttributeModifier(SLOW,-.15,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        if(mob.goalSelector.getAvailableGoals().stream().noneMatch(g->g.getGoal() instanceof HiveMindGoal))
            mob.goalSelector.addGoal(4,new HiveMindGoal(mob));
        LivingEntity target=targetFor(mob);
        if(target!=null) mob.setTarget(target);
    }
    public static LivingEntity targetFor(Mob mob) {
        if(!active(mob) || !(mob.level() instanceof ServerLevel level)) return null;
        if(validHost(mob,mob.getLastHurtByMob())) return mob.getLastHurtByMob();
        if(validHost(mob,mob.getTarget())) return mob.getTarget();
        var area=mob.getBoundingBox().inflate(24);
        for(Mob peer:level.getEntitiesOfClass(Mob.class,area,p->p!=mob && active(p))) {
            if(validHost(mob,peer.getLastHurtByMob())) return peer.getLastHurtByMob();
            if(validHost(mob,peer.getTarget())) return peer.getTarget();
        }
        LivingEntity nearest=null; double distance=16*16;
        for(LivingEntity host:level.getEntitiesOfClass(LivingEntity.class,area,e->validHost(mob,e))) {
            if(host instanceof Player p && p.isCreative()) continue;
            double d=mob.distanceToSqr(host);
            if(d<distance && mob.getSensing().hasLineOfSight(host)) { nearest=host;distance=d; }
        }
        return nearest;
    }
    public static Mob leaderFor(Mob mob) {
        if(!(mob.level() instanceof ServerLevel level)) return null;
        Mob leader=null;
        for(Mob peer:level.getEntitiesOfClass(Mob.class,mob.getBoundingBox().inflate(16),
                p->p.getId()<mob.getId() && active(p)))
            if(leader==null || peer.getId()<leader.getId()) leader=peer;
        return leader;
    }
}
