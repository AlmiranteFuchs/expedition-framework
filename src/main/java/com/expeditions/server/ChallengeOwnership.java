package com.expeditions.server;

import com.expeditions.core.ExpeditionRun;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

/** Durable ownership closes the cleanup seam when a mob wanders out of its spawn column. */
public final class ChallengeOwnership {
    private static final String RUN = "expeditions:challenge_run";
    private ChallengeOwnership() { }

    public static void mark(Entity entity, ExpeditionRun run) {
        entity.getPersistentData().putUUID(RUN, run.runId());
    }

    public static boolean cleanupIfEnded(Entity entity) {
        return check(entity, false);
    }

    public static boolean onTick(Entity entity) {
        return check(entity, true);
    }

    private static boolean check(Entity entity, boolean registerActive) {
        if (!(entity.level() instanceof ServerLevel level) || !entity.getPersistentData().hasUUID(RUN)) return false;
        var manager = ExpeditionManager.get(level.getServer());
        var id = entity.getPersistentData().getUUID(RUN);
        var owner = manager.runs().stream().filter(run -> run.runId().equals(id)).findFirst();
        if (owner.filter(ExpeditionRun::isActive).isPresent()) {
            if (registerActive && !owner.get().challengeEntities().contains(entity.getUUID())) {
                owner.get().trackChallengeEntities(java.util.List.of(entity.getUUID()));
                manager.savedData().setDirty();
            }
            return false;
        }
        entity.discard();
        owner.ifPresent(run -> {
            run.untrackChallengeEntity(entity.getUUID());
            manager.savedData().setDirty();
        });
        return true;
    }

    public static void onSplit(net.neoforged.neoforge.event.entity.living.MobSplitEvent event) {
        var parent = event.getParent();
        if (!(parent.level() instanceof ServerLevel level) || !parent.getPersistentData().hasUUID(RUN)) return;
        var id = parent.getPersistentData().getUUID(RUN);
        var owner = ExpeditionManager.get(level.getServer()).runs().stream()
                .filter(run -> run.runId().equals(id) && run.isActive()).findFirst();
        if (owner.isEmpty()) {
            event.setCanceled(true);
            return;
        }
        for (var child : event.getChildren()) {
            mark(child, owner.get());
            child.setPersistenceRequired();
            child.setData(com.expeditions.registry.ModAttachments.ASSIMILATED,
                    parent.getData(com.expeditions.registry.ModAttachments.ASSIMILATED));
        }
        // Track only after a child actually ticks: another mod may cancel the split or its spawn.
    }

    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel) || !(event.getEntity() instanceof net.minecraft.world.entity.Mob)) return;
        Entity entity = event.getEntity();
        // A legacy tracked UUID gains ownership when it is next loaded.
        if (!entity.getPersistentData().hasUUID(RUN)) {
            Integer stage = entity.getExistingDataOrNull(com.expeditions.registry.ModAttachments.ASSIMILATED);
            if ((stage == null || stage <= 0) && !(entity instanceof com.expeditions.entity.RootboundGuardian)) return;
            ExpeditionManager.get(event.getLevel().getServer()).runs().stream()
                    .filter(run -> run.challengeEntities().contains(entity.getUUID())).findFirst()
                    .ifPresent(run -> mark(entity, run));
        }
        if (cleanupIfEnded(entity)) event.setCanceled(true);
    }

    public static void onLeave(EntityLeaveLevelEvent event) {
        Entity entity = event.getEntity();
        var reason = entity.getRemovalReason();
        if (!(entity instanceof net.minecraft.world.entity.Mob) || !(event.getLevel() instanceof ServerLevel level) || !entity.getPersistentData().hasUUID(RUN)
                || (reason != Entity.RemovalReason.KILLED && reason != Entity.RemovalReason.DISCARDED)) return;
        var manager = ExpeditionManager.get(level.getServer());
        var id = entity.getPersistentData().getUUID(RUN);
        manager.runs().stream().filter(run -> run.runId().equals(id)).findFirst().ifPresent(run -> {
            run.untrackChallengeEntity(entity.getUUID());
            manager.savedData().setDirty();
        });
    }
}
