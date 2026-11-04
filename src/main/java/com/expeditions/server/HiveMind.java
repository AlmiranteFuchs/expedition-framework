package com.expeditions.server;

import com.expeditions.entity.HiveMindGoal;
import com.expeditions.registry.ModAttachments;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.util.Mth;
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
        return infected(mob) && mob.isAlive() && !AssimilationProtection.protectedFromInfection(mob)
                && (!(mob.level() instanceof ServerLevel level) || !Stillness.suppresses(level,mob.blockPosition()));
    }
    public static boolean validHost(Mob mob,LivingEntity candidate) {
        return candidate!=null && candidate!=mob && candidate.isAlive() && candidate.level()==mob.level()
                && mob.distanceToSqr(candidate)<=32*32
                && !(candidate instanceof ArmorStand) && !candidate.isSpectator()
                && !(candidate instanceof Mob && infected(candidate)) && !mob.isAlliedTo(candidate);
    }
    public static void tick(Mob mob) {
        var speed=mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if(!active(mob)) {
            if(speed!=null) speed.removeModifier(SLOW);
            if(mob.hasData(ModAttachments.HIVE_FOCUS)) {
                var old=mob.getData(ModAttachments.HIVE_FOCUS);
                if(old.target()!=null && mob.getTarget()==old.target()) mob.setTarget(null);
                mob.setData(ModAttachments.HIVE_FOCUS,HiveFocus.EMPTY);
            }
            return;
        }
        if((mob.tickCount+mob.getId())%20==0) {
            if(speed!=null && speed.getModifier(SLOW)==null)
                speed.addTransientModifier(new AttributeModifier(SLOW,-.15,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            if(mob.goalSelector.getAvailableGoals().stream().noneMatch(g->g.getGoal() instanceof HiveMindGoal))
                mob.goalSelector.addGoal(4,new HiveMindGoal(mob));
        }
        LivingEntity target=targetFor(mob);
        if(target!=null) {
            // Native target/look goals can run first; authoritative shared attention wins afterward.
            mob.setTarget(target);
            faceTarget(mob,target);
        }
    }

    public static LivingEntity targetFor(Mob mob) {
        if(!active(mob) || !(mob.level() instanceof ServerLevel level)) return null;
        long now=level.getGameTime();
        var focus=mob.getData(ModAttachments.HIVE_FOCUS);
        if(now<focus.refreshAt() && now<focus.expiresAt()
                && (focus.target()==null || validHost(mob,focus.target()))) return focus.target();
        var peers=cohort(mob,level);
        LivingEntity target=select(peers,level,now);
        publish(peers,target,now);
        return target;
    }

    /** An actual attack immediately alerts the same nearby pack, including ranged attackers. */
    public static void alert(Mob victim,LivingEntity attacker) {
        if(!active(victim) || !(victim.level() instanceof ServerLevel level)) return;
        var peers=cohort(victim,level);
        if(commonHost(peers,attacker)) publish(peers,attacker,level.getGameTime());
    }

    private static List<Mob> cohort(Mob origin,ServerLevel level) {
        var peers=new ArrayList<>(level.getEntitiesOfClass(Mob.class,origin.getBoundingBox().inflate(24),
                p->p!=origin && active(p) && origin.distanceToSqr(p)<=24*24));
        peers.sort(Comparator.comparingInt(Mob::getId));
        if(peers.size()>31) peers.subList(31,peers.size()).clear();
        peers.add(origin);peers.sort(Comparator.comparingInt(Mob::getId));
        return peers;
    }

    private static boolean commonHost(List<Mob> peers,LivingEntity host) {
        return host!=null && peers.stream().allMatch(p->validHost(p,host));
    }

    private static LivingEntity select(List<Mob> peers,ServerLevel level,long now) {
        LivingEntity threat=null;int freshest=101;
        for(Mob peer:peers) {
            int age=Math.max(0,peer.tickCount-peer.getLastHurtByMobTimestamp());
            LivingEntity attacker=peer.getLastHurtByMob();
            if(age<=100 && commonHost(peers,attacker) && (age<freshest
                    || age==freshest && (threat==null || attacker.getId()<threat.getId()))) {
                threat=attacker;freshest=age;
            }
        }
        if(threat!=null) return threat;
        // Keep the pack's live focus before considering any member's private AI target.
        for(Mob peer:peers) {
            var old=peer.getData(ModAttachments.HIVE_FOCUS);
            if(now<old.expiresAt() && commonHost(peers,old.target())) return old.target();
        }
        for(Mob peer:peers) if(commonHost(peers,peer.getTarget())) return peer.getTarget();
        Mob leader=peers.getFirst();
        LivingEntity nearest=null;double best=Double.MAX_VALUE;
        for(LivingEntity host:level.getEntitiesOfClass(LivingEntity.class,leader.getBoundingBox().inflate(32),
                e->commonHost(peers,e))) {
            if(host instanceof Player p && p.isCreative()) continue;
            boolean seen=peers.stream().anyMatch(p->p.distanceToSqr(host)<16*16 && p.getSensing().hasLineOfSight(host));
            double score=peers.stream().mapToDouble(p->p.distanceToSqr(host)).sum();
            if(seen && (score<best || score==best && (nearest==null || host.getId()<nearest.getId()))) {
                nearest=host;best=score;
            }
        }
        return nearest;
    }

    private static void publish(List<Mob> peers,LivingEntity target,long now) {
        var focus=new HiveFocus(target,now+10,now+40);
        for(Mob peer:peers) {
            peer.setData(ModAttachments.HIVE_FOCUS,focus);
            peer.setTarget(target);
            if(target!=null) faceTarget(peer,target);
        }
    }

    private static void faceTarget(Mob mob,LivingEntity target) {
        mob.getLookControl().setLookAt(target,40,40);
        double dx=target.getX()-mob.getX(),dz=target.getZ()-mob.getZ();
        double dy=target.getEyeY()-mob.getEyeY();
        float yaw=(float)(Math.atan2(dz,dx)*180/Math.PI)-90;
        float pitch=(float)(-Math.atan2(dy,Math.sqrt(dx*dx+dz*dz))*180/Math.PI);
        mob.setYHeadRot(Mth.approachDegrees(mob.getYHeadRot(),yaw,20));
        mob.setXRot(Mth.approachDegrees(mob.getXRot(),Mth.clamp(pitch,-60,60),15));
        if(mob.getDeltaMovement().horizontalDistanceSqr()<.0004)
            mob.yBodyRot=Mth.approachDegrees(mob.yBodyRot,yaw,10);
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
