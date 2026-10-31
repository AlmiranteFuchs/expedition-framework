package com.expeditions.entity;

import com.expeditions.server.Assimilation;
import com.expeditions.server.HiveMind;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;

/** Native combat retains priority; converted passive hosts gain pursuit and a weak contact attack. */
public final class HiveMindGoal extends Goal {
    private final Mob mob;
    private LivingEntity host;
    private Mob leader;
    private int nextAttack;
    public HiveMindGoal(Mob mob) { this.mob=mob;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK)); }
    @Override public boolean canUse() {
        if(!HiveMind.active(mob)) return false;
        host=HiveMind.targetFor(mob); leader=host==null?HiveMind.leaderFor(mob):null;
        return host!=null || leader!=null && mob.distanceToSqr(leader)>25;
    }
    @Override public boolean canContinueToUse() {
        return HiveMind.active(mob) && (host!=null?HiveMind.validHost(mob,host) && mob.distanceToSqr(host)<32*32
                :leader!=null && HiveMind.active(leader) && mob.distanceToSqr(leader)>9 && mob.distanceToSqr(leader)<24*24);
    }
    @Override public void tick() {
        LivingEntity destination=host!=null?host:leader;
        if(destination==null) return;
        mob.getLookControl().setLookAt(destination,20,20);
        if(mob.tickCount%10==0) mob.getNavigation().moveTo(destination,1);
        if(host==null) return;
        mob.setTarget(host);
        double reach=mob.getBbWidth()+host.getBbWidth()*.5+.5;
        if(mob.tickCount>=nextAttack && mob.distanceToSqr(host)<=reach*reach && mob.getSensing().hasLineOfSight(host)) {
            nextAttack=mob.tickCount+20;
            boolean hit=mob.getAttribute(Attributes.ATTACK_DAMAGE)!=null?mob.doHurtTarget(host)
                    :host.hurt(mob.damageSources().mobAttack(mob),2);
            if(hit) Assimilation.onContact(host);
        }
    }
    @Override public void stop() { mob.getNavigation().stop();host=null;leader=null; }
}
