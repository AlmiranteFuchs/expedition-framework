package com.expeditions.item;

import com.expeditions.ExpeditionsConfig;
import com.expeditions.core.ExpeditionRun;
import com.expeditions.server.Assimilation;
import com.expeditions.server.ExpeditionManager;
import com.expeditions.server.ExpeditionSavedData;
import com.expeditions.server.data.ExpeditionTuning;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;

/**
 * The Charter-issue field journal. Right-click writes a fresh survey onto its
 * own pages and opens the vanilla book GUI — the active expedition, this
 * dimension's campaign, and the Bloom's pressure (the organism's awareness of
 * you). The report lives in a {@code WRITTEN_BOOK_CONTENT} component on the
 * stack, so the journal physically holds its last survey between uses.
 *
 * <p>Sneak-use lets the journal drink a blood sample — a small health cost,
 * never lethal — and only a blooded journal fills its third page with the
 * carrier's infection assay. The flag rides {@code CUSTOM_DATA} on the stack:
 * the journal remembers whose blood it drank, not the player.
 */
public class ExpeditionFieldJournalItem extends Item {
    private static final String TAG_BLOOD_SAMPLED = "expeditions:blood_sampled";

    /** The journal's price for reading what's inside you. Never kills. */
    private static final float BLOOD_SAMPLE_COST = 2.0f;

    /**
     * Bloodletting rides {@code bypasses_invulnerability} — a creative-mode
     * player still pays the sample's price.
     */
    private static final ResourceKey<DamageType> BLOOD_LETTING =
            ResourceKey.create(Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath("expeditions", "blood_letting"));

    public ExpeditionFieldJournalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer server)) {
            return InteractionResultHolder.success(stack);
        }
        if (player.isShiftKeyDown()) {
            return sampleBlood(server, stack)
                    ? InteractionResultHolder.sidedSuccess(stack, false)
                    : InteractionResultHolder.fail(stack);
        }
        openReport(server, stack, hand);
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    /**
     * Sneak-use: the journal drinks. Costs {@link #BLOOD_SAMPLE_COST} health —
     * refused when it would kill — and latches the blood flag so the assay
     * page renders. Returns whether the sample was taken.
     */
    private static boolean sampleBlood(ServerPlayer player, ItemStack journal) {
        if (player.getHealth() <= BLOOD_SAMPLE_COST) {
            player.sendSystemMessage(Component.translatable(
                    "message.expeditions.journal_too_weak"));
            return false;
        }
        player.hurt(player.damageSources().source(BLOOD_LETTING), BLOOD_SAMPLE_COST);
        CustomData.update(DataComponents.CUSTOM_DATA, journal,
                tag -> tag.putBoolean(TAG_BLOOD_SAMPLED, true));
        player.sendSystemMessage(Component.translatable(
                "message.expeditions.journal_blood"));
        return true;
    }

    private static boolean bloodSampled(ItemStack journal) {
        CustomData data = journal.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean(TAG_BLOOD_SAMPLED);
    }

    /**
     * Writes this tick's survey onto the stack and opens the book GUI. The
     * client reads the component off the held stack, so the slot sync must be
     * enqueued before the open packet — same ordering {@code openItemGui}
     * uses via {@code broadcastChanges}.
     */
    private static void openReport(ServerPlayer player, ItemStack journal, InteractionHand hand) {
        journal.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough("Field Journal"), "The Charter", 0,
                List.of(
                        Filterable.passThrough(expeditionPage(player)),
                        Filterable.passThrough(surveyPage(player)),
                        Filterable.passThrough(assayPage(player, journal))),
                true));
        player.containerMenu.broadcastChanges();
        player.connection.send(new ClientboundOpenBookPacket(hand));
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
        long elapsed = player.serverLevel().getGameTime() - active.startedGameTime();
        page.append(active.definition().displayName()).append('\n');
        page.append(active.definition().dimension()).append("\n\n");
        page.append("Checkpoints: ").append(active.completedCheckpoints())
                .append('/').append(active.totalCheckpoints()).append('\n');
        page.append("Afield: ").append(elapsed / 1200).append(" min\n");
        page.append("Party: ").append(active.party().size());
        return Component.literal(page.toString());
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
        page.append(dimension).append('\n');
        page.append("Charter: ").append(data.campaignCompletions(dimension))
                .append(" filed — ").append(data.campaignState(dimension).id())
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

    private static Component assayPage(ServerPlayer player, ItemStack journal) {
        if (!bloodSampled(journal)) {
            return Component.literal("§1§lBLOOD ASSAY§r\n\n"
                    + "These pages stay blank.\n\n"
                    + "The journal reads only what flows in you. "
                    + "Hold it to a vein and let it drink.\n\n"
                    + "(Sneak + use — it will cost a little of you.)");
        }
        double infection = Assimilation.infection(player);
        StringBuilder page = new StringBuilder("§1§lBLOOD ASSAY§r\n\n");
        if (infection <= 0) {
            page.append("Your blood answers clean.\nThe Bloom has no hold.");
        } else {
            int max = ExpeditionTuning.integer(player.level().dimension(),
                    "microbiome.infection_max", ExpeditionsConfig.infectionMax());
            page.append("Infection: ")
                    .append(Math.round(infection / max * 100)).append("%\n");
            page.append("Stage: ").append(stageName(Assimilation.stage(player)))
                    .append("\n\nIt is in the blood.");
        }
        return Component.literal(page.toString());
    }

    /** Stage words matching the whisper fiction: notice → grip → take. */
    private static String stageName(int stage) {
        return switch (stage) {
            case 1 -> "the Bloom's notice";
            case 2 -> "the grip";
            case 3 -> "the take";
            default -> "unmarked";
        };
    }
}
