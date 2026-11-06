package com.expeditions.server;

import com.expeditions.core.ExpeditionRun;
import java.util.List;
import net.minecraft.network.chat.Component;

/** Player-facing orders from the authoritative, resolved expedition. */
public final class FieldOrders {
    private FieldOrders() { }

    public static List<Component> describe(ExpeditionRun run) {
        return run.definition().objectives().stream().<Component>map(objective -> {
            var state = run.objectiveState(objective.id()).orElseThrow();
            String progressKey = state.completed() ? "message.expeditions.field_order_done"
                    : run.objectiveFailed(objective.id()) ? "message.expeditions.field_order_failed"
                    : switch (objective.kind()) {
                        case COLLECT_ITEMS, HARVEST -> "message.expeditions.field_order_count";
                        case TIME_LIMIT -> "message.expeditions.field_order_on_return";
                        default -> "message.expeditions.field_order_open";
                    };
            var progress = Component.translatable(progressKey, state.progress(), objective.progressTarget());
            Object description = objective.description();
            if (objective.kind().needsCommissionSite() && !state.completed() && run.commissionSite().isPresent()) {
                var site = run.commissionSite().orElseThrow();
                description = Component.literal(objective.description()).append(Component.translatable(
                        "message.expeditions.detour_coordinates", site.x(), site.y(), site.z()));
            }
            return Component.translatable(objective.required()
                    ? "message.expeditions.field_order_required" : "message.expeditions.field_order_optional",
                    description, progress);
        }).toList();
    }
}
