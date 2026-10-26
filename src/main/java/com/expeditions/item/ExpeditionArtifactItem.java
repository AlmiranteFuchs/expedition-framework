package com.expeditions.item;

import com.expeditions.registry.ModDataComponents;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Mission artifact: the physical cargo a {@code recover_artifact} objective
 * is about. A normal item in every respect — it drops on death, can be
 * stored, thrown, or picked up by anyone; the run binding lives in the
 * {@link ModDataComponents#RUN_ID} component so only the owning run's
 * delivery can consume it. Non-stackable so each stack is one identity.
 */
public class ExpeditionArtifactItem extends Item {
    public ExpeditionArtifactItem(Properties properties) {
        super(properties);
    }

    /** Whether {@code stack} is an artifact bound to {@code runId}. */
    public static boolean isBoundTo(ItemStack stack, UUID runId) {
        return stack.getItem() instanceof ExpeditionArtifactItem
                && runId.toString().equals(stack.get(ModDataComponents.RUN_ID.get()));
    }

    /** The run this artifact belongs to, if it is a bound artifact stack. */
    public static Optional<UUID> boundRun(ItemStack stack) {
        if (!(stack.getItem() instanceof ExpeditionArtifactItem)) {
            return Optional.empty();
        }
        String stored = stack.get(ModDataComponents.RUN_ID.get());
        if (stored == null || stored.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(stored));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
