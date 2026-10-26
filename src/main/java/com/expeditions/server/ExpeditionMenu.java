package com.expeditions.server;

import com.expeditions.core.Difficulty;
import com.expeditions.core.ExpeditionDefinition;
import com.expeditions.core.ExpeditionOptions;
import com.expeditions.core.ExpeditionRun;
import com.expeditions.core.Waypoint;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.util.Unit;
import net.minecraft.world.item.component.ItemLore;

/**
 * Terminal configuration screen: a vanilla chest GUI (no custom menu type,
 * so no client code) where each option button cycles its value on click —
 * expedition, difficulty, route length, bonus-time window — and the start
 * button launches the run with the assembled {@link ExpeditionOptions}.
 * All clicks resolve on the server; the container is read-only so players
 * can never pull the button items out.
 */
public class ExpeditionMenu extends ChestMenu {
    private static final int ROWS = 4;
    private static final int SLOT_EXPEDITION = 10;
    private static final int SLOT_DIFFICULTY = 12;
    private static final int SLOT_CHECKPOINTS = 14;
    private static final int SLOT_TIME = 16;
    private static final int SLOT_START = 31;

    private static final int[] CHECKPOINT_CHOICES = {0, 1, 2, 3, 4, 5, 6, 8, 10, 12, 16};
    private static final int[] TIME_CHOICES = {0, 50, 100, 150, 200};

    private final SimpleContainer container;
    private final ExpeditionManager manager;
    private final BlockPos terminalPos;
    private final List<ExpeditionDefinition> startable;
    private int expeditionIndex;
    private int difficultyIndex = Difficulty.STANDARD.ordinal();
    private int checkpointsIndex;
    private int timeIndex;

    public ExpeditionMenu(int containerId, Inventory playerInventory,
            ExpeditionManager manager, BlockPos terminalPos, String homeDimension) {
        super(MenuType.GENERIC_9x4, containerId, playerInventory,
                new SimpleContainer(9 * ROWS), ROWS);
        this.container = (SimpleContainer) this.getContainer();
        this.manager = manager;
        this.terminalPos = terminalPos;
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
        refresh();
    }

    private ExpeditionOptions options() {
        return new ExpeditionOptions(startable.isEmpty()
                ? "" : startable.get(expeditionIndex).id(),
                Difficulty.values()[difficultyIndex],
                CHECKPOINT_CHOICES[checkpointsIndex], TIME_CHOICES[timeIndex]);
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
                onButton(slotId, serverPlayer);
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

    private void onButton(int slotId, ServerPlayer player) {
        switch (slotId) {
            case SLOT_EXPEDITION -> {
                if (!startable.isEmpty()) {
                    expeditionIndex = (expeditionIndex + 1) % startable.size();
                }
            }
            case SLOT_DIFFICULTY ->
                difficultyIndex = (difficultyIndex + 1) % Difficulty.values().length;
            case SLOT_CHECKPOINTS ->
                checkpointsIndex = (checkpointsIndex + 1) % CHECKPOINT_CHOICES.length;
            case SLOT_TIME -> timeIndex = (timeIndex + 1) % TIME_CHOICES.length;
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
            player.closeContainer();
            player.sendSystemMessage(Component.translatable(
                    "message.expeditions.expedition_started",
                    run.definition().displayName()));
            run.nextCheckpoint().ifPresent(first -> player.sendSystemMessage(
                    Component.translatable("message.expeditions.next_checkpoint",
                            run.completedCheckpoints() + 1, run.totalCheckpoints(),
                            format(first))));
        } catch (IllegalStateException e) {
            player.sendSystemMessage(Component.literal(e.getMessage()));
        }
    }

    private void refresh() {
        container.clearContent();
        if (startable.isEmpty()) {
            container.setItem(SLOT_EXPEDITION, button(Items.BARRIER,
                    "gui.expeditions.no_expeditions", List.of()));
        } else {
            ExpeditionDefinition selected = startable.get(expeditionIndex);
            container.setItem(SLOT_EXPEDITION, button(Items.COMPASS,
                    "gui.expeditions.expedition",
                    List.of(Component.literal(selected.displayName()),
                            Component.literal(selected.dimension()),
                            Component.translatable("gui.expeditions.click_cycle"))));
        }
        Difficulty difficulty = Difficulty.values()[difficultyIndex];
        container.setItem(SLOT_DIFFICULTY, button(Items.IRON_SWORD,
                "gui.expeditions.difficulty",
                List.of(Component.literal(difficulty.name()),
                        Component.translatable("gui.expeditions.difficulty_hint",
                                difficulty.hordePercent(), difficulty.rewardPercent()),
                        Component.translatable("gui.expeditions.click_cycle"))));
        container.setItem(SLOT_CHECKPOINTS, button(Items.MAP,
                "gui.expeditions.length",
                List.of(CHECKPOINT_CHOICES[checkpointsIndex] == 0
                        ? Component.translatable("gui.expeditions.default")
                        : Component.literal(CHECKPOINT_CHOICES[checkpointsIndex]
                                + " checkpoints"),
                        Component.translatable("gui.expeditions.click_cycle"))));
        container.setItem(SLOT_TIME, button(Items.CLOCK,
                "gui.expeditions.time",
                List.of(TIME_CHOICES[timeIndex] == 0
                        ? Component.translatable("gui.expeditions.default")
                        : Component.literal(TIME_CHOICES[timeIndex] + "%"),
                        Component.translatable("gui.expeditions.click_cycle"))));
        container.setItem(SLOT_START, button(Items.LIME_CONCRETE,
                "gui.expeditions.start", List.of()));
        broadcastChanges();
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
