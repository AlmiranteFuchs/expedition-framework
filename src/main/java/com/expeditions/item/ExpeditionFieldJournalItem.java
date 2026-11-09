package com.expeditions.item;

import com.expeditions.ExpeditionsConfig;
import com.expeditions.core.ExpeditionRun;
import com.expeditions.server.ExpeditionManager;
import com.expeditions.server.ExpeditionText;
import com.expeditions.server.ExpeditionSavedData;
import com.expeditions.server.FieldOrders;
import com.expeditions.server.data.ExpeditionTuning;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;

/**
 * The Charter-issue field journal. Right-click writes a fresh survey onto its
 * own pages for the chapter guide — the active expedition, this
 * dimension's campaign, and the Bloom's pressure (the organism's awareness of
 * you). The report lives in a {@code WRITTEN_BOOK_CONTENT} component on the
 * stack, so the journal physically holds its last survey between uses.
 *
 * Blood sampling belongs to the separate field assay kit.
 */
public class ExpeditionFieldJournalItem extends Item {
    // A presentation callback installed only by ExpeditionsClient. Dedicated
    // servers never load a screen class or depend on a client implementation.
    private static java.util.function.Consumer<InteractionHand> clientOpener = hand -> { };

    public static void setClientOpener(java.util.function.Consumer<InteractionHand> opener) {
        clientOpener = opener;
    }
    public ExpeditionFieldJournalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer server)) {
            clientOpener.accept(hand);
            return InteractionResultHolder.success(stack);
        }
        com.expeditions.server.JournalDiscovery.observe(server);
        openReport(server, stack);
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    /**
     * Writes this tick's survey onto the stack. The custom client guide reads
     * the synchronized component on its field-record pages. Keep broadcasting
     * changes: the screen can open before this server-side refresh arrives.
     */
    private static void openReport(ServerPlayer player, ItemStack journal) {
        journal.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough("Field Journal"), "The Charter", 0,
                List.of(
                        Filterable.passThrough(expeditionPage(player)),
                        Filterable.passThrough(surveyPage(player))),
                true));
        player.containerMenu.broadcastChanges();
        // The custom chapter screen reads this synchronized report as its
        // field-record pages. It opens client-side through the use callback.
    }

    private static Component expeditionPage(ServerPlayer player) {
        StringBuilder page = new StringBuilder("§1§lSURVEY LOG§r\n\n");
        Optional<ExpeditionRun> run = ExpeditionManager.get(player.server)
                .activeRun(player.getUUID());
        if (run.isEmpty()) {
            page.append("No active expedition.\n\nThe Charter waits.");
            return Component.literal(page.toString());
        }
        ExpeditionRun active = run.get();
        long elapsed = active.elapsedTicks(player.serverLevel().getGameTime());
        page.append(ExpeditionText.title(active.definition().displayName())).append('\n');
        page.append(ExpeditionText.dimension(active.definition().dimension()).getString()).append("\n\n");
        page.append("Checkpoints: ").append(active.completedCheckpoints())
                .append('/').append(active.totalCheckpoints()).append('\n');
        if (elapsed >= 0) {
            page.append("Afield: ").append(elapsed / 1200).append(" min\n");
        }
        page.append("Party: ").append(active.party().size());
        var report = Component.literal(page.toString());
        var orders = FieldOrders.describe(active);
        if (!orders.isEmpty()) {
            report.append("\n\n").append(Component.translatable("journal.expeditions.field_orders"));
            for (Component order : orders) {
                report.append("\n").append(order);
            }
        }
        return report;
    }

    private static Component surveyPage(ServerPlayer player) {
        ExpeditionSavedData data = ExpeditionManager.get(player.server).savedData();
        String dimension = player.level().dimension().location().toString();
        int pressure = data.pressure(dimension);
        int max = ExpeditionTuning.integer(dimension,
                "pressure.max", ExpeditionsConfig.pressureMax());
        int hordePer = ExpeditionTuning.integer(dimension,
                "pressure.horde_bonus_per", ExpeditionsConfig.pressureHordeBonusPer());
        int buffAt = ExpeditionTuning.integer(dimension,
                "pressure.buff_threshold", ExpeditionsConfig.pressureBuffThreshold());
        StringBuilder page = new StringBuilder("§1§lFIELD SURVEY§r\n\n");
        page.append(ExpeditionText.dimension(dimension).getString()).append('\n');
        page.append("Charter: ").append(data.campaignCompletions(dimension))
                .append(" filed — ").append(ExpeditionText.label(data.campaignState(dimension).id()))
                .append("\n\n");
        if (data.isDimensionCleared(dimension)) {
            page.append("The Bloom is dead here.\nIt reads nothing of you.");
        } else {
            page.append("The Bloom: ").append(awarenessBand(pressure, max)).append('\n');
            page.append("Awareness: ").append(pressure).append('/').append(max).append('\n');
            int bonus = hordePer > 0 ? pressure / hordePer : 0;
            if (bonus > 0) {
                page.append("Hordes swell +").append(bonus).append('\n');
            }
            if (pressure >= buffAt) {
                page.append("Its spawn run empowered.");
            }
        }
        return Component.literal(page.toString());
    }

    /** Awareness bands — the organism's attention, not a raw number. */
    private static String awarenessBand(int pressure, int max) {
        if (pressure <= 0) {
            return "DORMANT";
        }
        double fraction = max <= 0 ? 1.0 : (double) pressure / max;
        if (fraction >= 0.75) {
            return "RAGING";
        }
        return fraction >= 0.5 ? "ROUSED" : "STIRRING";
    }

}
