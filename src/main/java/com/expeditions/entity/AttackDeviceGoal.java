package com.expeditions.entity;

import com.expeditions.server.ExpeditionManager;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Siege behavior: pathfind to the expedition's defense device and chip its
 * health on a cooldown through {@link ExpeditionManager#damageDeviceAt} —
 * the run record stays authoritative, the mob is only a damage source.
 * Registered at goal priority 1 so it outranks the mob's normal wander
 * behavior; the mob still fights players who engage it through its usual
 * hurt-by targeting.
 *
 * <p>The goal self-terminates when the device stops being tracked (siege
 * survived, destroyed, or the run ended) and the mob is discarded by the
 * run's challenge-entity cleanup anyway.
 */
public class AttackDeviceGoal extends Goal {
    /** Three-dimensional reach at which a hit lands, squared. */
    private static final double REACH_SQR = 4.0;

    private final Mob mob;
    private final BlockPos device;
    private final UUID runId;
    private final int damage;
    private final int intervalTicks;
    private int cooldown;
    private int repathDelay;

    public AttackDeviceGoal(Mob mob, BlockPos device, UUID runId, int damage, int intervalTicks) {
        this.mob = mob;
        this.device = device.immutable();
        this.runId = runId;
        this.damage = damage;
        this.intervalTicks = intervalTicks;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return mob.level() instanceof ServerLevel level
                && ExpeditionManager.get(level.getServer())
                        .isAttendedDefense(level, device, runId);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (cooldown > 0) {
            cooldown--;
        }
        double distSqr = mob.distanceToSqr(device.getX() + 0.5,
                device.getY() + 0.5, device.getZ() + 0.5);
        if (distSqr > REACH_SQR) {
            if (--repathDelay <= 0) {
                repathDelay = 10;
                // Pick an exact attack position so navigation cannot stop diagonally
                // below the ward, outside the goal's three-dimensional reach.
                var attackPositions = new HashSet<BlockPos>();
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    BlockPos adjacent = device.relative(side);
                    attackPositions.add(adjacent);
                    attackPositions.add(adjacent.below());
                }
                mob.getNavigation().moveTo(mob.getNavigation().createPath(attackPositions, 0), 1.0);
            }
            return;
        }
        mob.getNavigation().stop();
        mob.getLookControl().setLookAt(device.getX() + 0.5,
                device.getY() + 0.5, device.getZ() + 0.5);
        if (cooldown <= 0 && mob.level() instanceof ServerLevel level) {
            cooldown = intervalTicks;
            mob.swing(InteractionHand.MAIN_HAND);
            ExpeditionManager.get(level.getServer())
                    .damageDeviceAt(level, device, damage);
        }
    }
}
