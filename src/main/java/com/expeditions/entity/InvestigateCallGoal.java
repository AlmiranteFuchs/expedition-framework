package com.expeditions.entity;

import com.expeditions.server.HiveMind;
import java.util.EnumSet;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/** A short-lived noise detour yields immediately to combat and higher-priority siege orders. */
final class InvestigateCallGoal extends Goal {
    private final Mob mob;
    private Vec3 sound;
    private long until;
    private int nextPathUpdate;

    private InvestigateCallGoal(Mob mob) { this.mob = mob; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

    static void direct(Mob mob, Vec3 sound, long until) {
        var goal = mob.goalSelector.getAvailableGoals().stream().map(wrapped -> wrapped.getGoal())
                .filter(InvestigateCallGoal.class::isInstance).map(InvestigateCallGoal.class::cast).findFirst()
                .orElseGet(() -> {
                    var created = new InvestigateCallGoal(mob);
                    mob.goalSelector.addGoal(3, created);
                    return created;
                });
        goal.sound = sound;
        goal.until = until;
    }

    @Override public boolean canUse() {
        return sound != null && mob.level().getGameTime() < until && HiveMind.active(mob)
                && mob.getTarget() == null && mob.position().distanceToSqr(sound) > 2;
    }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public void tick() {
        mob.getLookControl().setLookAt(sound.x, sound.y, sound.z);
        if (mob.tickCount >= nextPathUpdate) {
            nextPathUpdate = mob.tickCount + 10;
            mob.getNavigation().moveTo(sound.x, sound.y, sound.z, 1);
        }
    }
    @Override public void stop() { mob.getNavigation().stop(); }
}
