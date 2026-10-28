package com.expeditions.server;

import com.expeditions.ExpeditionsConfig;
import com.expeditions.core.CompassPulse;
import com.expeditions.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;

/**
 * The expedition compass's heartbeat — while a player holds a compass whose
 * tracker has a target in their dimension, the compass beats like the site's
 * heart calling to it: two quick thumps (a lub-dub) every pulse interval,
 * the interval tightening as the target nears. Sound goes only to the holder
 * — it is the compass reacting, not a broadcast. The trigger treats the
 * horizontal and vertical gaps independently (either within {@code
 * expeditions.compass.pingRange} pulses), so a checkpoint directly below in a
 * Nether cave still calls even though the 2D needle already reads "here".
 */
public final class CompassPing {

    private CompassPing() {
    }

    /**
     * Per-player tick hook. Reads the target straight off the held compass's
     * own {@code LODESTONE_TRACKER} — whatever the needle points at is what
     * beats, no run logic duplicated.
     */
    public static void onPlayerTick(net.minecraft.server.level.ServerPlayer player) {
        double range = ExpeditionsConfig.compassPingRange();
        if (range <= 0) {
            return;
        }
        GlobalPos target = targetOf(player);
        if (target == null) {
            return;
        }
        BlockPos pos = target.pos();
        double dx = pos.getX() + 0.5 - player.getX();
        double dy = pos.getY() + 0.5 - player.getY();
        double dz = pos.getZ() + 0.5 - player.getZ();
        double horizontal = Math.hypot(dx, dz);
        double vertical = Math.abs(dy);
        int interval = CompassPulse.intervalTicks(horizontal, vertical, range);
        if (interval < 0) {
            return;
        }
        float pitch = CompassPulse.pitch(horizontal, vertical, range);
        int phase = player.tickCount % interval;
        if (phase == 0) {
            player.playNotifySound(SoundEvents.WARDEN_HEARTBEAT,
                    SoundSource.PLAYERS, 1.0f, pitch);
        } else if (phase == CompassPulse.DUB_DELAY_TICKS) {
            player.playNotifySound(SoundEvents.WARDEN_HEARTBEAT,
                    SoundSource.PLAYERS, 0.7f, pitch * 0.9f);
        }
    }

    /**
     * The tracker target of an expedition compass held in either hand — the
     * first valid one found, in the holder's current dimension only.
     */
    private static GlobalPos targetOf(net.minecraft.server.level.ServerPlayer player) {
        for (var hand : net.minecraft.world.InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (!stack.is(ModItems.EXPEDITION_COMPASS.get())) {
                continue;
            }
            LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
            if (tracker == null || tracker.target().isEmpty()) {
                continue;
            }
            GlobalPos target = tracker.target().get();
            if (target.dimension() != player.level().dimension()) {
                continue; // the needle spins across dimensions; the heart stays home
            }
            return target;
        }
        return null;
    }
}
