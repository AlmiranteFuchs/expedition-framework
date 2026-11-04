package com.expeditions.server;

import com.expeditions.core.Difficulty;
import com.expeditions.core.ExpeditionDefinition;
import com.expeditions.core.ExpeditionOptions;
import com.expeditions.core.ExpeditionRun;
import com.expeditions.core.RequirementCheck;
import com.expeditions.core.Waypoint;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
    public static final int SLOT_BRIEFING = 0, SLOT_READINESS = 1;
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
                { if (!guided()) difficultyIndex = Math.floorMod(difficultyIndex + step, Difficulty.values().length); }
            case SLOT_CHECKPOINTS -> {
                if (!guided() && !startable.isEmpty() && startable.get(expeditionIndex).route() instanceof com.expeditions.core.RouteGeneration) {
                    int minimum = startable.get(expeditionIndex).objectives().stream().filter(o -> o.required())
                            .mapToInt(o -> o.checkpoint()).max().orElse(1);
                    do { checkpointsIndex = Math.floorMod(checkpointsIndex + step, CHECKPOINT_CHOICES.length); }
                    while (CHECKPOINT_CHOICES[checkpointsIndex] > 0 && CHECKPOINT_CHOICES[checkpointsIndex] < minimum);
                }
            }
            case SLOT_TIME -> { if (!guided()) timeIndex = Math.floorMod(timeIndex + step, TIME_CHOICES.length); }
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
                player.playNotifySound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.55f, 0.7f);
            } else {
                sendSapTrail(player, graft, aperture);
            }
            player.closeContainer();
            player.displayClientMessage(Component.translatable("message.expeditions.portal_answers"), true);
        } catch (IllegalStateException e) {
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
            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal(selected.displayName()));
            lore.add(Component.literal(selected.dimension()));
            lore.add(Component.literal("Mission " + (expeditionIndex + 1) + " / " + startable.size()));
            if (ready) {
                lore.add(Component.translatable("gui.expeditions.click_cycle"));
                container.setItem(SLOT_EXPEDITION, button(Items.COMPASS,
                        "gui.expeditions.expedition", lore));
            } else {
                // Locked expeditions stay listed — the lore says what opens
                // them, and clicking start repeats the refusal in chat.
                lore.add(Component.translatable("gui.expeditions.locked"));
                if (trainingDone) lore.add(Component.translatable("gui.expeditions.training_done"));
                for (String key : check.missingKeys()) {
                    lore.add(Component.translatable(
                            "gui.expeditions.locked_key", key));
                }
                if (check.missingTokens() > 0) {
                    lore.add(Component.translatable(
                            "gui.expeditions.locked_cost",
                            check.missingTokens()));
                }
                if (!check.currentState().atLeast(check.requiredState())) {
                    lore.add(Component.translatable(
                            "gui.expeditions.locked_campaign",
                            check.requiredState().id(),
                            check.currentState().id()));
                }
                lore.add(Component.translatable("gui.expeditions.click_cycle"));
                container.setItem(SLOT_EXPEDITION, button(Items.TRIAL_KEY,
                        "gui.expeditions.expedition", lore));
            }
            List<Component> brief = new ArrayList<>();
            int checkpoints = guided() ? ExpeditionOptions.GUIDED_CHECKPOINTS
                    : CHECKPOINT_CHOICES[checkpointsIndex] > 0 ? CHECKPOINT_CHOICES[checkpointsIndex]
                    : selected.route() instanceof com.expeditions.core.RouteGeneration generated
                            ? generated.checkpoints() : ((com.expeditions.core.ExpeditionRoute) selected.route()).checkpoints().size();
            brief.add(Component.literal("Route: " + checkpoints + " checkpoints, then extraction"));
            int bonuses = selected.bonusTokens() + selected.objectives().stream().mapToInt(o -> o.bonusTokens()).sum();
            if (guided()) {
                brief.add(Component.translatable("gui.expeditions.training_brief"));
                brief.add(Component.translatable("gui.expeditions.training_rewards", selected.tokenReward() + bonuses));
            } else {
                brief.add(Component.literal("Guaranteed base reward: " + selected.tokenReward() + " tokens"));
                brief.add(Component.literal("Possible base bonuses: " + bonuses + " tokens"));
                brief.add(Component.literal("Rewards scale with difficulty and party."));
            }
            brief.add(Component.literal("Deployment fee: " + selected.costTokens() + " tokens"));
            brief.add(Component.literal(""));
            if (selected.bigBad()) brief.add(Component.literal("! Defeat the Rootbound Guardian before extraction."));
            for (var objective : selected.objectives()) {
                if (guided() && objective.id().equals("practice_complete") && !objective.required()) continue;
                brief.add(Component.literal((objective.required() ? "! " : "+ ") + objective.description()));
            }
            if (!guided()) {
                if (selected.objectives().isEmpty()) brief.add(Component.literal("! Activate every checkpoint and use extraction."));
                brief.add(Component.literal(""));
                brief.add(Component.literal("Checkpoint tokens are secured. Unfinished bonuses remain at risk."));
            }
            container.setItem(SLOT_BRIEFING, button(Items.WRITABLE_BOOK, "gui.expeditions.briefing", brief));
            List<Component> requirements = new ArrayList<>();
            requirements.add(Component.literal(ready ? "READY TO DEPLOY" : "REQUIREMENTS MISSING"));
            requirements.add(Component.literal("Your balance: " + manager.tokenBalance(playerId) + " tokens"));
            requirements.add(Component.literal(""));
            requirements.add(Component.translatable(frame.messageKey()));
            if (trainingDone) requirements.add(Component.translatable("gui.expeditions.training_done"));
            for (String key : check.missingKeys()) requirements.add(Component.literal(proofHint(key)));
            if (check.missingTokens() > 0) requirements.add(Component.literal("Earn " + check.missingTokens() + " more tokens."));
            if (!check.currentState().atLeast(check.requiredState())) {
                requirements.add(Component.literal("Campaign: " + check.currentState().id() + "; needs " + check.requiredState().id() + "."));
                if (check.requiredState() == com.expeditions.core.CampaignState.CLEARED
                        && com.expeditions.server.data.ExpeditionTuning.bool(selected.dimension(), "campaign.require_guardian", true)) {
                    requirements.add(Component.translatable("message.expeditions.campaign_guardian_gate"));
                } else {
                    requirements.add(Component.literal("Complete expeditions in " + selected.dimension() + " to advance its campaign."));
                }
            }
            if (ready) requirements.add(Component.literal("No missing requirements. Choose your equipment, then open the portal."));
            requirements.add(Component.literal(""));
            requirements.add(Component.translatable(solo || guided() ? "gui.expeditions.party_private_hint" : "gui.expeditions.party_open_hint"));
            container.setItem(SLOT_READINESS, button(ready ? Items.LIME_DYE : Items.RED_DYE,
                    "gui.expeditions.requirements", requirements));
        }
        Difficulty difficulty = guided() ? Difficulty.STANDARD : Difficulty.values()[difficultyIndex];
        container.setItem(SLOT_DIFFICULTY, button(Items.IRON_SWORD,
                "gui.expeditions.difficulty",
                List.of(Component.literal(difficulty.name()),
                        Component.translatable("gui.expeditions.difficulty_hint",
                                difficulty.hordePercent(), difficulty.rewardPercent()),
                        Component.translatable(guided() ? "gui.expeditions.training_fixed" : "gui.expeditions.click_cycle"))));
        container.setItem(SLOT_CHECKPOINTS, button(Items.MAP,
                "gui.expeditions.length",
                List.of(Component.literal(routeSummary()),
                        Component.translatable(guided() ? "gui.expeditions.training_fixed" : "gui.expeditions.click_cycle"))));
        container.setItem(SLOT_TIME, button(Items.CLOCK,
                "gui.expeditions.time",
                List.of(Component.literal(bonusWindowSummary()),
                        Component.literal("Changes the base time bonus; objective clocks stay fixed."),
                        Component.translatable(guided() ? "gui.expeditions.training_fixed" : "gui.expeditions.click_cycle"))));
        container.setItem(SLOT_PARTY, button(Items.TRIAL_KEY, "gui.expeditions.party",
                List.of(Component.translatable(guided() ? "gui.expeditions.party_guided"
                                : solo ? "gui.expeditions.party_solo" : "gui.expeditions.party_open"),
                        Component.translatable(solo || guided() ? "gui.expeditions.party_private_hint" : "gui.expeditions.party_open_hint"))));
        container.setItem(SLOT_START, button(ready ? Items.LIME_CONCRETE : Items.BARRIER,
                "gui.expeditions.start", List.of()));
        broadcastChanges();
    }

    private static String proofHint(String key) {
        return switch (key) {
            case "expeditions:field_clear" -> "Complete Field Survey or First Bearing to earn its proof.";
            case "expeditions:salvage_clear" -> "Complete Deep Salvage to earn its proof.";
            case "expeditions:attuned" -> "Use an attunement sigil against the terminal.";
            case "expeditions:guardian_clear" -> "Defeat the Rootbound Guardian and extract.";
            default -> "Requires proof: " + key;
        };
    }

    private String routeSummary() {
        if (startable.isEmpty()) return "Unavailable";
        if (guided()) return ExpeditionOptions.GUIDED_CHECKPOINTS + " checkpoints";
        var route = startable.get(expeditionIndex).route();
        if (route instanceof com.expeditions.core.ExpeditionRoute authored) return authored.checkpoints().size() + " (authored)";
        int count = CHECKPOINT_CHOICES[checkpointsIndex] > 0 ? CHECKPOINT_CHOICES[checkpointsIndex]
                : ((com.expeditions.core.RouteGeneration) route).checkpoints();
        return count + " checkpoints";
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
