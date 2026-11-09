package com.expeditions.server;

import com.expeditions.ExpeditionsConfig;
import com.expeditions.core.CampaignState;
import com.expeditions.core.Difficulty;
import com.expeditions.core.ExpeditionDefinition;
import com.expeditions.core.ExpeditionObjective;
import com.expeditions.core.ExpeditionOptions;
import com.expeditions.core.ExpeditionRun;
import com.expeditions.core.ObjectiveKind;
import com.expeditions.core.RequirementCheck;
import com.expeditions.core.Waypoint;
import com.expeditions.registry.ModSounds;
import com.expeditions.server.data.ExpeditionTuning;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.util.Unit;
import net.minecraft.world.item.component.ItemLore;
import org.slf4j.Logger;

/**
 * Server-authoritative configuration for the custom deployment board. A
 * ChestMenu transports read-only option/briefing stacks through vanilla sync;
 * each option button cycles its value on click —
 * expedition, difficulty, route length, bonus-time window — and the start
 * button launches the run with the assembled {@link ExpeditionOptions}.
 * All clicks resolve on the server; the container is read-only so players
 * can never pull the button items out.
 */
public class ExpeditionMenu extends ChestMenu {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int ROWS = 4;
    public static final int SLOT_BRIEFING = 0, SLOT_READINESS = 1, SLOT_EXTRAS = 2;
    public static final int SLOT_PREVIOUS = 9, SLOT_EXPEDITION = 10, SLOT_NEXT = 11;
    public static final int SLOT_DIFFICULTY = 12, SLOT_CHECKPOINTS = 14, SLOT_TIME = 16, SLOT_START = 31;
    public static final int SLOT_PARTY = 18;

    private static final int[] CHECKPOINT_CHOICES = {0, 1, 2, 3, 4, 5, 6, 8, 10, 12, 16};
    private static final int[] TIME_CHOICES = {0, 50, 100, 150, 200};

    private final SimpleContainer container;
    private final ExpeditionManager manager;
    private final BlockPos terminalPos;
    private final net.minecraft.server.level.ServerLevel terminalLevel;
    private final List<ExpeditionDefinition> startable;
    private final UUID playerId;
    private int expeditionIndex;
    private int difficultyIndex = Difficulty.STANDARD.ordinal();
    private int checkpointsIndex;
    private int timeIndex;
    private boolean solo;

    /** Client factory. Items/lore arrive through ordinary server slot sync. */
    public ExpeditionMenu(int containerId, Inventory playerInventory) {
        super(com.expeditions.registry.ModMenus.EXPEDITION.get(), containerId, playerInventory,
                new SimpleContainer(9 * ROWS), ROWS);
        container = (SimpleContainer) getContainer();
        manager = null; terminalPos = BlockPos.ZERO; terminalLevel = null; startable = List.of();
        playerId = playerInventory.player.getUUID();
    }

    public ExpeditionMenu(int containerId, Inventory playerInventory,
            ExpeditionManager manager, BlockPos terminalPos, String homeDimension) {
        super(com.expeditions.registry.ModMenus.EXPEDITION.get(), containerId, playerInventory,
                new SimpleContainer(9 * ROWS), ROWS);
        this.container = (SimpleContainer) this.getContainer();
        this.manager = manager;
        this.terminalPos = terminalPos;
        this.terminalLevel = (net.minecraft.server.level.ServerLevel) playerInventory.player.level();
        this.playerId = playerInventory.player.getUUID();
        this.startable = manager.listStartable();
        // Preselect the expedition of the terminal's own dimension — the
        // historical default — so clicking start without cycling behaves
        // like the old quick-start.
        for (int i = 0; i < startable.size(); i++) {
            if (startable.get(i).dimension().equals(homeDimension)) {
                expeditionIndex = i;
                break;
            }
        }
        // Prefer a mission the player can actually deploy into, while keeping
        // the terminal's home dimension ahead of a remote unlocked mission.
        for (int i = 0; i < startable.size(); i++) {
            if (startable.get(i).dimension().equals(homeDimension)
                    && !(isTraining(startable.get(i)) && manager.trainingComplete(playerId))
                    && manager.missingRequirements(playerId, startable.get(i)).isSatisfied()) {
                expeditionIndex = i;
                break;
            }
        }
        if (!manager.trainingComplete(playerId) && !manager.unlocks(playerId).contains("expeditions:field_clear")) {
            for (int i = 0; i < startable.size(); i++) {
                var charter = startable.get(i);
                if (isTraining(charter) && charter.dimension().equals(homeDimension)
                        && manager.missingRequirements(playerId, charter).isSatisfied()) {
                    expeditionIndex = i;
                    break;
                }
            }
        }
        refresh();
    }

    private static boolean isTraining(ExpeditionDefinition charter) {
        return ExpeditionOptions.FIRST_BEARING.equals(charter.id());
    }

    private boolean guided() { return !startable.isEmpty() && isTraining(startable.get(expeditionIndex)); }

    private boolean aftergrowth() {
        return !startable.isEmpty() && com.expeditions.core.FrontierProgression.isAftergrowth(startable.get(expeditionIndex));
    }

    private boolean mastery() {
        return !startable.isEmpty() && com.expeditions.core.FrontierProgression.isMastery(startable.get(expeditionIndex));
    }

    private boolean fixedRoute() { return guided() || aftergrowth() || mastery(); }

    private int fixedCheckpointCount() {
        return mastery() ? com.expeditions.core.FrontierProgression.MASTERY_CHECKPOINTS : 2;
    }

    private ExpeditionOptions options() {
        return new ExpeditionOptions(startable.isEmpty()
                ? "" : startable.get(expeditionIndex).id(),
                Difficulty.values()[difficultyIndex],
                CHECKPOINT_CHOICES[checkpointsIndex], TIME_CHOICES[timeIndex], solo);
    }

    /**
     * Consumes clicks on the option row (the container is display-only —
     * nothing may leave or enter it) and defers clicks in the player's own
     * inventory to vanilla handling.
     */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < container.getContainerSize()) {
            if (player instanceof ServerPlayer serverPlayer) {
                onButton(slotId, button, serverPlayer);
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    /** Shift-clicking would insert into the option row; nothing may move. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }

    private void onButton(int slotId, int mouseButton, ServerPlayer player) {
        int step = mouseButton == 1 ? -1 : 1;
        switch (slotId) {
            case SLOT_EXPEDITION, SLOT_PREVIOUS, SLOT_NEXT -> {
                if (!startable.isEmpty()) {
                    expeditionIndex = Math.floorMod(expeditionIndex + (slotId == SLOT_PREVIOUS ? -1 : step), startable.size());
                    checkpointsIndex = 0;
                }
            }
            case SLOT_DIFFICULTY ->
                { if (!fixedRoute()) difficultyIndex = Math.floorMod(difficultyIndex + step, Difficulty.values().length); }
            case SLOT_CHECKPOINTS -> {
                if (!fixedRoute() && !startable.isEmpty() && startable.get(expeditionIndex).route() instanceof com.expeditions.core.RouteGeneration) {
                    int minimum = startable.get(expeditionIndex).objectives().stream().filter(o -> o.required())
                            .mapToInt(o -> o.checkpoint()).max().orElse(1);
                    do { checkpointsIndex = Math.floorMod(checkpointsIndex + step, CHECKPOINT_CHOICES.length); }
                    while (CHECKPOINT_CHOICES[checkpointsIndex] > 0 && CHECKPOINT_CHOICES[checkpointsIndex] < minimum);
                }
            }
            case SLOT_TIME -> { if (!fixedRoute()) timeIndex = Math.floorMod(timeIndex + step, TIME_CHOICES.length); }
            case SLOT_PARTY -> { if (!guided()) solo = !solo; }
            case SLOT_START -> {
                start(player);
                return;
            }
            default -> {
            }
        }
        refresh();
    }

    private void start(ServerPlayer player) {
        if (startable.isEmpty()) {
            player.sendSystemMessage(Component.translatable(
                    "message.expeditions.no_expeditions"));
            return;
        }
        try {
            ExpeditionRun run = manager.startRun(player, terminalPos, options());
            BlockPos portal = ExpeditionManager.toPos(run.portal());
            var graft = net.minecraft.world.phys.Vec3.atBottomCenterOf(terminalPos).add(0, 0.75, 0);
            var aperture = net.minecraft.world.phys.Vec3.atCenterOf(portal);
            if (portal.getY() == terminalPos.getY()) {
                var nearPost = graft.lerp(aperture, 0.5);
                var crown = aperture.add(0, 1.85, 0);
                sendSapTrail(player, graft, nearPost);
                sendSapTrail(player, nearPost, nearPost.add(0, 1.65, 0));
                sendSapTrail(player, nearPost.add(0, 1.65, 0), crown);
                sendSapTrail(player, crown, aperture);
                player.playNotifySound(ModSounds.WAYROOT_GRAFT.get(),
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.55f, 0.7f);
            } else {
                sendSapTrail(player, graft, aperture);
            }
            player.closeContainer();
        } catch (IllegalStateException e) {
            player.playNotifySound(ModSounds.TERMINAL_REFUSE.get(),
                    net.minecraft.sounds.SoundSource.BLOCKS, .6f, 1.0f);
            LOGGER.warn("Expedition start refused for {}: {}",
                    player.getScoreboardName(), e.getMessage());
            player.sendSystemMessage(Component.literal(e.getMessage()));
            refresh();
        }
    }

    private static void sendSapTrail(ServerPlayer player, net.minecraft.world.phys.Vec3 from,
            net.minecraft.world.phys.Vec3 to) {
        for (int step = 0; step <= 6; step++) {
            var spark = from.lerp(to, step / 6.0);
            player.serverLevel().sendParticles(com.expeditions.block.RouteMarkerBlock.AMBER,
                    spark.x, spark.y, spark.z, 2, 0.06, 0.03, 0.06, 0);
        }
    }

    private void refresh() {
        container.clearContent();
        boolean ready = false;
        if (startable.isEmpty()) {
            container.setItem(SLOT_EXPEDITION, button(Items.BARRIER,
                    "gui.expeditions.no_expeditions", List.of()));
        } else {
            ExpeditionDefinition selected = startable.get(expeditionIndex);
            RequirementCheck check = manager.missingRequirements(playerId, selected);
            boolean trainingDone = guided() && manager.trainingComplete(playerId);
            var frame = WayrootFrame.inspect(terminalLevel, terminalPos);
            ready = check.isSatisfied() && !trainingDone && frame.canOpen();
            container.setItem(SLOT_EXPEDITION, button(ready ? Items.COMPASS : Items.TRIAL_KEY,
                    "gui.expeditions.expedition", List.of(
                            Component.literal(ExpeditionText.title(selected.displayName())),
                            ExpeditionText.dimension(selected.dimension()),
                            Component.literal("Charter " + (expeditionIndex + 1) + " / " + startable.size()))));
            container.setItem(SLOT_BRIEFING, button(Items.WRITABLE_BOOK,
                    "gui.expeditions.briefing", missionBrief(selected)));
            container.setItem(SLOT_READINESS, button(ready ? Items.LIME_DYE : Items.RED_DYE,
                    "gui.expeditions.requirements", readiness(selected, check, frame, trainingDone)));
            container.setItem(SLOT_EXTRAS, button(Items.PAPER,
                    "gui.expeditions.extras", extras(selected)));
        }
        Difficulty difficulty = mastery() ? Difficulty.HARD
                : fixedRoute() ? Difficulty.STANDARD : Difficulty.values()[difficultyIndex];
        container.setItem(SLOT_DIFFICULTY, button(Items.IRON_SWORD,
                "gui.expeditions.difficulty",
                List.of(Component.literal(difficulty.name()),
                        Component.translatable("gui.expeditions.difficulty_hint",
                                difficulty.hordePercent(), difficulty.rewardPercent()),
                        Component.translatable(fixedRoute() ? "gui.expeditions.survey_fixed" : "gui.expeditions.click_cycle"))));
        container.setItem(SLOT_CHECKPOINTS, button(Items.MAP,
                "gui.expeditions.length",
                List.of(Component.literal(routeSummary()),
                        Component.translatable(fixedRoute() ? "gui.expeditions.survey_fixed" : "gui.expeditions.click_cycle"))));
        container.setItem(SLOT_TIME, button(Items.CLOCK,
                "gui.expeditions.time",
                List.of(Component.literal(bonusWindowSummary()),
                        Component.literal("Changes the base time bonus; objective clocks stay fixed."),
                        Component.translatable(fixedRoute() ? "gui.expeditions.survey_fixed" : "gui.expeditions.click_cycle"))));
        container.setItem(SLOT_PARTY, button(Items.TRIAL_KEY, "gui.expeditions.party",
                List.of(Component.translatable(guided() ? "gui.expeditions.party_guided"
                                : solo ? "gui.expeditions.party_solo" : "gui.expeditions.party_open"),
                        Component.translatable(solo || guided() ? "gui.expeditions.party_private_hint" : "gui.expeditions.party_open_hint"))));
        container.setItem(SLOT_START, button(ready ? Items.LIME_CONCRETE : Items.BARRIER,
                "gui.expeditions.start", List.of()));
        broadcastChanges();
    }

    private List<Component> missionBrief(ExpeditionDefinition definition) {
        var brief = new ArrayList<Component>();
        var required = definition.objectives().stream().filter(ExpeditionObjective::required).toList();
        boolean cargo = required.stream().anyMatch(objective -> objective.kind() == ObjectiveKind.RECOVER_ARTIFACT);
        brief.add(Component.literal(cargo ? "Bring a sealed sample home."
                : definition.bigBad() ? "Break the Bloom's hold on this land."
                : "Follow the witness trail and return."));
        brief.add(Component.literal("Required in the field").withStyle(ChatFormatting.BOLD));
        brief.add(Component.literal("1. Activate all " + routeCheckpointCount() + " witness stumps in order."));
        int step = 2;
        for (var objective : required) {
            // Every witness is required by the route itself.
            if (objective.kind() == ObjectiveKind.VISIT_CHECKPOINT) continue;
            brief.add(Component.literal(step++ + ". " + objectiveInstruction(objective)));
        }
        if (definition.bigBad()) {
            brief.add(Component.literal(step++ + ". Defeat the Rootbound Guardian."));
        }
        brief.add(Component.literal(step + (cargo ? ". Extract at the Recall Hollow with the cargo."
                : ". Extract at the Recall Hollow.")));
        var earnedProofs = manager.unlocks(playerId);
        var followups = startable.stream()
                .filter(next -> !next.id().equals(definition.id()) && next.dimension().equals(definition.dimension()))
                .filter(next -> next.requires().stream().anyMatch(key ->
                        definition.grants().contains(key) && !earnedProofs.contains(key)))
                .filter(next -> next.requires().stream().allMatch(key ->
                        definition.grants().contains(key) || earnedProofs.contains(key)))
                .map(next -> ExpeditionText.title(next.displayName())).distinct().toList();
        if (!followups.isEmpty()) {
            brief.add(Component.literal("Earns proof for").withStyle(ChatFormatting.BOLD));
            for (String name : followups) brief.add(Component.literal(name));
        }
        return brief;
    }

    private static String objectiveInstruction(ExpeditionObjective objective) {
        return switch (objective.kind()) {
            case RECOVER_ARTIFACT -> "Pick up the cargo at stump " + objective.checkpoint() + ".";
            case DEFEND_DEVICE -> "At stump " + objective.checkpoint() + ", defend the extractor for "
                    + Math.ceilDiv(objective.timeLimitTicks(), 20) + " seconds.";
            default -> objective.description().equals(objective.id())
                    ? ExpeditionText.label(objective.id()) : ExpeditionText.title(objective.description());
        };
    }

    private List<Component> readiness(ExpeditionDefinition definition, RequirementCheck check,
            WayrootFrame.Condition frame, boolean trainingDone) {
        var requirements = new ArrayList<Component>();
        boolean campaignReady = check.currentState().atLeast(check.requiredState());
        boolean guardianGate = check.requiredState() == CampaignState.CLEARED
                && ExpeditionTuning.bool(definition.dimension(), "campaign.require_guardian", true);
        Component campaignStep = guardianGate ? Component.literal("Complete a Guardian charter and extract.")
                : Component.literal("Complete more expeditions in ").append(ExpeditionText.dimension(definition.dimension())).append(".");
        var missing = check.missingKeys().stream().sorted().toList();
        Component nextStep;
        if (trainingDone) nextStep = Component.literal("Choose Field Survey for your next charter.");
        else if (!frame.canOpen()) nextStep = Component.literal("Check the Wayroot arch.");
        else if (!missing.isEmpty()) nextStep = unlockAction(missing.getFirst());
        else if (!campaignReady) nextStep = campaignStep;
        else if (check.missingTokens() > 0) nextStep = Component.literal("Earn " + check.missingTokens() + " more tokens.");
        else nextStep = Component.literal("Ready to deploy.");
        // The board also shows this first line alongside the Deploy button.
        requirements.add(nextStep);
        if (trainingDone) requirements.add(Component.translatable("gui.expeditions.training_done"));
        for (String key : definition.requires().stream().sorted().toList()) {
            boolean earned = !check.missingKeys().contains(key);
            requirements.add(requirementLine(earned, earned ? ExpeditionText.proof(key) : unlockAction(key)));
        }
        if (check.requiredState() != CampaignState.FRONTIER) {
            Component progress;
            if (guardianGate) {
                progress = Component.literal("Liberate ").append(ExpeditionText.dimension(definition.dimension()));
            } else {
                int goal = check.requiredState() == CampaignState.CLEARED
                        ? ExpeditionsConfig.campaignClearedAtCompletions() : ExpeditionsConfig.campaignEstablishedAtCompletions();
                int completed = Math.min(goal, manager.savedData().campaignCompletions(definition.dimension()));
                progress = ExpeditionText.dimension(definition.dimension()).copy()
                        .append(" · " + completed + " / " + goal + " expeditions complete");
            }
            requirements.add(requirementLine(campaignReady, progress));
            if (!campaignReady && guardianGate) requirements.add(campaignStep);
        }
        if (definition.costTokens() > 0) {
            requirements.add(requirementLine(check.missingTokens() == 0,
                    Component.literal("Entry fee · " + manager.tokenBalance(playerId) + " / " + definition.costTokens() + " tokens")));
        }
        if (!frame.canOpen()) requirements.add(Component.translatable(frame.messageKey()));
        if (requirements.size() == 1) requirements.add(Component.literal("No entry fee or campaign requirements."));
        return requirements;
    }

    private Component unlockAction(String key) {
        return startable.stream().filter(candidate -> candidate.grants().contains(key))
                .filter(candidate -> !(isTraining(candidate) && manager.trainingComplete(playerId)))
                .filter(candidate -> manager.missingRequirements(playerId, candidate).isSatisfied())
                .findFirst().<Component>map(candidate -> Component.literal("Complete "
                        + ExpeditionText.title(candidate.displayName()) + "."))
                .orElseGet(() -> ExpeditionText.proofAction(key));
    }

    private static Component requirementLine(boolean met, Component text) {
        return Component.literal(met ? "✓ " : "○ ")
                .withStyle(met ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_RED)
                .append(text.copy().withStyle(ChatFormatting.RESET));
    }

    private List<Component> extras(ExpeditionDefinition definition) {
        var details = new ArrayList<Component>();
        int bonuses = definition.bonusTokens() + definition.objectives().stream().mapToInt(ExpeditionObjective::bonusTokens).sum();
        bonuses += definition.commissionPool().stream().mapToInt(ExpeditionObjective::bonusTokens).max().orElse(0);
        if (guided()) {
            details.add(Component.translatable("gui.expeditions.training_brief"));
            details.add(Component.translatable("gui.expeditions.training_rewards", definition.tokenReward() + bonuses));
        } else {
            details.add(Component.literal("Base reward · " + definition.tokenReward() + " tokens"));
            if (bonuses > 0) details.add(Component.literal("Possible bonuses · up to " + bonuses + " base tokens"));
            details.add(Component.literal("Rewards scale with difficulty and party size."));
            if (aftergrowth()) details.add(Component.translatable("gui.expeditions.aftergrowth_brief"));
            if (mastery()) details.add(Component.translatable("gui.expeditions.mastery_brief"));
            var optional = definition.objectives().stream().filter(objective -> !objective.required()).toList();
            if (!optional.isEmpty()) details.add(Component.literal("Optional objectives").withStyle(ChatFormatting.BOLD));
            for (var objective : optional) details.add(Component.literal(objectiveInstruction(objective)));
            if (!definition.commissionPool().isEmpty()) {
                details.add(Component.translatable("gui.expeditions.commission_pool").withStyle(ChatFormatting.BOLD));
                for (var commission : definition.commissionPool()) details.add(Component.literal(objectiveInstruction(commission)));
            }
        }
        details.add(Component.translatable(solo || guided() ? "gui.expeditions.party_private_hint" : "gui.expeditions.party_open_hint"));
        return details;
    }

    private int routeCheckpointCount() {
        if (fixedRoute()) return fixedCheckpointCount();
        var route = startable.get(expeditionIndex).route();
        if (route instanceof com.expeditions.core.ExpeditionRoute authored) return authored.checkpoints().size();
        int chosen = CHECKPOINT_CHOICES[checkpointsIndex];
        return chosen > 0 ? chosen : ((com.expeditions.core.RouteGeneration) route).checkpoints();
    }

    private String routeSummary() {
        return startable.isEmpty() ? "Unavailable" : routeCheckpointCount() + " checkpoints";
    }

    private String bonusWindowSummary() {
        if (startable.isEmpty()) return "Unavailable";
        var definition = startable.get(expeditionIndex);
        if (definition.bonusTokens() == 0 || definition.bonusTimeTicks() == 0) return "No timed bonus";
        long ticks = definition.bonusTimeTicks() * (long) (TIME_CHOICES[timeIndex] == 0 ? 100 : TIME_CHOICES[timeIndex]) / 100;
        return ticks / 1200 + "m " + ticks / 20 % 60 + "s" + (timeIndex == 0 ? " (default)" : "");
    }

    private static ItemStack button(net.minecraft.world.item.Item item, String nameKey,
            List<Component> lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME,
                Component.translatable(nameKey).withStyle(
                        style -> style.withItalic(false)));
        if (!lore.isEmpty()) {
            stack.set(DataComponents.LORE, new ItemLore(lore));
        }
        stack.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
        return stack;
    }

    static String format(Waypoint w) {
        return w.x() + ", " + w.y() + ", " + w.z();
    }
}
