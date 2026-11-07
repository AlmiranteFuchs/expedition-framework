package com.expeditions.client;

import com.expeditions.core.FieldEquipment;
import com.expeditions.core.ResonanceProcessing;
import com.expeditions.core.ResonanceStatus;
import com.expeditions.server.ResonanceMenu;
import java.util.ArrayList;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** The workshop uses the Charter's carved frame and wooden leaves. */
public final class ResonanceScreen extends AbstractContainerScreen<ResonanceMenu> {
    private static final int INK = 0xff493a2c;
    private static final ResourceLocation FRAME = ResourceLocation.parse("expeditions:salvage_frame");
    private static final ResourceLocation LEAF = ResourceLocation.parse("expeditions:salvage_leaf");
    private static final ResourceLocation SLOT = ResourceLocation.parse("expeditions:machine_slot");

    public ResonanceScreen(ResonanceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 212;
        imageHeight = 228;
    }

    @Override
    protected void renderBg(GuiGraphics gui, float tick, int mouseX, int mouseY) {
        gui.blitSprite(FRAME, leftPos, topPos, imageWidth, imageHeight);
        gui.fill(leftPos + 15, topPos + 14, leftPos + imageWidth - 15, topPos + 48, 0xffbba788);
        gui.blitSprite(LEAF, leftPos + 15, topPos + 49, 182, 72);
        gui.blitSprite(LEAF, leftPos + 20, topPos + 132, 172, 83);
        var status = Component.translatable(menu.status().translationKey());
        gui.drawString(font, font.plainSubstrByWidth(status.getString(), 166),
                leftPos + 23, topPos + 35, INK, false);
        label(gui, menu.dynamo ? "machine.expeditions.source_slot" : "machine.expeditions.input_slot", 66, 52);
        if (!menu.dynamo) label(gui, "machine.expeditions.output_slot", 116, 52);
        label(gui, "machine.expeditions.cell_slot", 172, 52);
        for (int index = 0; index < menu.slots.size(); index++) {
            var slot = menu.getSlot(index);
            gui.blitSprite(SLOT, leftPos + slot.x - 1, topPos + slot.y - 1, 18, 18);
        }
        // The dedicated dock has a copper edge and a separate cell-charge bar.
        gui.fill(leftPos + 162, topPos + 63, leftPos + 182, topPos + 64, 0xffac7c4d);
        gui.fill(leftPos + 164, topPos + 84, leftPos + 180, topPos + 87, 0xff64513b);
        int charge = 16 * menu.cellEnergy() / FieldEquipment.CELL_CAPACITY;
        gui.fill(leftPos + 164, topPos + 84, leftPos + 164 + charge, topPos + 86, 0xffd09a4d);
        energyGauge(gui);
        processing(gui);
        var mode = cellStatus();
        gui.drawString(font, font.plainSubstrByWidth(mode.getString(), 150),
                leftPos + 38, topPos + 94, 0xff765539, false);
        var output = menu.dynamo
                ? Component.translatable("machine.expeditions.output_rate", menu.actualGeneration())
                : Component.translatable("machine.expeditions.recipe_cost");
        gui.drawString(font, output, leftPos + 38, topPos + 107, INK, false);
    }

    private void label(GuiGraphics gui, String key, int center, int y) {
        var label = Component.translatable(key);
        gui.drawString(font, label, leftPos + center - font.width(label) / 2, topPos + y, INK, false);
    }

    private Component cellStatus() {
        String key;
        if (menu.cell().isEmpty()) key = "cell_insert";
        else if (menu.dynamo) {
            key = menu.cellEnergy() >= FieldEquipment.CELL_CAPACITY ? "cell_full"
                    : menu.energy() > 0 || menu.actualGeneration() > 0 ? "cell_charging" : "cell_waiting";
        } else {
            key = menu.cellEnergy() <= 0 ? "cell_empty"
                    : menu.energy() < ResonanceProcessing.CAPACITY ? "cell_discharging" : "cell_buffer_full";
        }
        return Component.translatable("machine.expeditions." + key);
    }

    private void energyGauge(GuiGraphics gui) {
        int x = leftPos + 24;
        int y = topPos + 58;
        int height = 52 * menu.energy() / ResonanceProcessing.CAPACITY;
        gui.fill(x - 2, y - 2, x + 8, y + 54, 0xff745a3e);
        gui.fill(x, y, x + 6, y + 52, 0xff46382a);
        if (height > 0) {
            gui.fill(x + 1, y + 52 - height, x + 5, y + 52, 0xffbb7c35);
            gui.fill(x + 2, y + 52 - height, x + 3, y + 52, 0xffdfa852);
        }
        for (int mark = 1; mark < 4; mark++) gui.hLine(x + 3, x + 5, y + mark * 13, INK);
    }

    private void processing(GuiGraphics gui) {
        int x = leftPos + 83;
        int y = topPos + 72;
        if (menu.dynamo) {
            gui.hLine(x, leftPos + 151, y + 1, 0xff9a8060);
            gui.fill(leftPos + 149, y - 1, leftPos + 152, y + 4, 0xff9a8060);
            gui.fill(leftPos + 152, y, leftPos + 154, y + 3, 0xff9a8060);
            if (menu.sourceKind() == 1 && menu.progress() > 0) {
                int remaining = Math.clamp(24 * menu.progress() / 600, 0, 24);
                gui.fill(x, y + 5, x + remaining, y + 7, 0xffb98542);
            }
        } else {
            gui.fill(x, y, x + 20, y + 4, 0xff8f7858);
            int progress = Math.clamp(20 * menu.progress() / ResonanceProcessing.PROCESS_TICKS, 0, 20);
            gui.fill(x, y + 1, x + progress, y + 3, 0xffd6a152);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics gui, int mouseX, int mouseY) {
        String heading = font.plainSubstrByWidth(title.getString(), imageWidth - 40);
        gui.drawString(font, heading, (imageWidth - font.width(heading)) / 2, 18, INK, false);
        gui.drawString(font, playerInventoryTitle, 25, 125, INK, false);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float tick) {
        super.render(gui, mouseX, mouseY, tick);
        renderTooltip(gui, mouseX, mouseY);
        int x = mouseX - leftPos;
        int y = mouseY - topPos;
        if (x >= 21 && x < 34 && y >= 55 && y < 113) {
            gui.renderTooltip(font, Component.translatable("equipment.expeditions.cell_charge",
                    menu.energy(), ResonanceProcessing.CAPACITY), mouseX, mouseY);
        } else if (x >= 22 && x < 190 && y >= 33 && y < 46) {
            var lines = new ArrayList<Component>();
            lines.add(Component.translatable(menu.status().translationKey() + ".hint"));
            if (menu.dynamo && menu.sourceKind() == 0 && menu.collectors() > 1
                    && menu.status() != ResonanceStatus.SIMPLE_GATHERING) {
                lines.add(Component.translatable("machine.expeditions.shared_field", menu.collectors()));
            }
            lines.add(Component.translatable(menu.dynamo ? "machine.expeditions.comparator_power"
                    : "machine.expeditions.comparator_stock", menu.comparatorSignal()));
            gui.renderComponentTooltip(font, lines, mouseX, mouseY);
        } else if (x >= 38 && x < 188 && y >= 92 && y < 104) {
            gui.renderTooltip(font, Component.translatable(menu.dynamo ? "machine.expeditions.cell_charge_hint"
                    : "machine.expeditions.cell_supply_hint"), mouseX, mouseY);
        } else if (menu.dynamo && menu.sourceKind() == 1 && x >= 82 && x < 109 && y >= 76 && y < 81) {
            gui.renderTooltip(font, Component.translatable("machine.expeditions.fuel_remaining",
                    (menu.progress() + 19) / 20), mouseX, mouseY);
        }
    }
}
