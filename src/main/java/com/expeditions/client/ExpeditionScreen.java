package com.expeditions.client;

import com.expeditions.server.ExpeditionMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;

/** Read-only deployment board; every option and start still resolves on the server. */
public final class ExpeditionScreen extends AbstractContainerScreen<ExpeditionMenu> {
    private static final int INK = 0xff393126;
    private final List<BoardButton> controls = new ArrayList<>();
    private int column, detailWidth, tab, page;
    private String mission = "";

    public ExpeditionScreen(ExpeditionMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    @Override protected void init() {
        imageWidth = Math.min(430, width - 14); imageHeight = Math.min(290, height - 14);
        super.init();
        column = leftPos + 144; detailWidth = imageWidth - 160;
        controls.clear();
        control(leftPos + 12, topPos + 35, 52, 20, "< Leaf", ExpeditionMenu.SLOT_PREVIOUS, null);
        control(leftPos + 68, topPos + 35, 58, 20, "Leaf >", ExpeditionMenu.SLOT_NEXT, null);
        int spacing = Math.min(35, Math.max(17, (imageHeight - 88) / 4));
        control(leftPos + 12, topPos + 69, 114, spacing - 5, "Difficulty", ExpeditionMenu.SLOT_DIFFICULTY, null);
        control(leftPos + 12, topPos + 69 + spacing, 114, spacing - 5, "Route length", ExpeditionMenu.SLOT_CHECKPOINTS, null);
        control(leftPos + 12, topPos + 69 + spacing * 2, 114, spacing - 5, "Bonus window", ExpeditionMenu.SLOT_TIME, null);
        control(leftPos + 12, topPos + 69 + spacing * 3, 114, spacing - 5, "Party access", ExpeditionMenu.SLOT_PARTY, null);
        control(column, topPos + 43, 76, 18, "Briefing", -1, () -> { tab = 0; page = 0; });
        control(column + 80, topPos + 43, 103, 18, "Requirements", -1, () -> { tab = 1; page = 0; });
        control(column, topPos + imageHeight - 50, 28, 16, "<", -1, () -> page = Math.max(0, page - 1));
        control(column + detailWidth - 28, topPos + imageHeight - 50, 28, 16, ">", -1,
                () -> page = Math.min(pageCount() - 1, page + 1));
        control(column, topPos + imageHeight - 29, detailWidth, 21, "Seal charter", ExpeditionMenu.SLOT_START, null);
    }

    private void control(int x, int y, int w, int h, String label, int slot, Runnable action) {
        var control = new BoardButton(x, y, w, h, label, slot, action);
        controls.add(addRenderableWidget(control));
    }

    private List<Component> lore(int slot) {
        var lore = menu.getContainer().getItem(slot).get(DataComponents.LORE);
        return lore == null ? List.of() : lore.lines();
    }
    private String line(int slot, int index, String fallback) {
        var lines = lore(slot);
        return lines.size() > index ? lines.get(index).getString() : fallback;
    }
    private boolean ready() { return menu.getContainer().getItem(ExpeditionMenu.SLOT_START).is(Items.LIME_CONCRETE); }
    private int rows() { return Math.max(3, (imageHeight - 127) / 10); }
    private List<FormattedCharSequence> lines() {
        List<FormattedCharSequence> result = new ArrayList<>();
        for (var line : lore(tab == 0 ? ExpeditionMenu.SLOT_BRIEFING : ExpeditionMenu.SLOT_READINESS)) {
            result.addAll(font.split(line, detailWidth - 6));
        }
        return result;
    }
    private int pageCount() { return Math.max(1, (lines().size() + rows() - 1) / rows()); }
    private void clickSlot(int slot, int mouseButton) {
        if (minecraft.gameMode != null && minecraft.player != null) {
            minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot, mouseButton, ClickType.PICKUP, minecraft.player);
        }
    }

    @Override public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        String selected = line(ExpeditionMenu.SLOT_EXPEDITION, 0, "No expeditions available");
        if (!mission.equals(selected)) { mission = selected; page = 0; }
        gui.fill(0, 0, width, height, 0xb51d1710);
        paper(gui, leftPos + 128, topPos, leftPos + imageWidth, topPos + imageHeight, 0xffe2cda5, 3);
        paper(gui, leftPos, topPos + 5, leftPos + 134, topPos + imageHeight - 4, 0xffd0b48b, 1);
        pin(gui, leftPos + 66, topPos + 8);
        pin(gui, leftPos + imageWidth - 15, topPos + 9);
        gui.drawString(font, Component.translatable("gui.expeditions.terminal"), leftPos + 15, topPos + 20, INK, false);
        gui.drawString(font, font.plainSubstrByWidth(selected, detailWidth - 10), column, topPos + 11, INK, false);
        gui.drawString(font, font.plainSubstrByWidth(line(ExpeditionMenu.SLOT_EXPEDITION, 1, ""), detailWidth),
                column, topPos + 25, 0xff705539, false);
        gui.drawString(font, line(ExpeditionMenu.SLOT_EXPEDITION, 2, ""), leftPos + 15, topPos + 58, 0xff705539, false);
        gui.hLine(column, column + detailWidth - 1, topPos + 62, 0xffb09468);
        var text = lines();
        page = Math.min(page, pageCount() - 1);
        int y = topPos + 69;
        for (int n = page * rows(); n < Math.min(text.size(), (page + 1) * rows()); n++) {
            gui.drawString(font, text.get(n), column + 2, y, INK, false); y += 10;
        }
        String count = (page + 1) + " / " + pageCount();
        gui.drawString(font, count, column + (detailWidth - font.width(count)) / 2,
                topPos + imageHeight - 46, 0xff786b4e, false);
        for (var control : controls) control.render(gui, mouseX, mouseY, partialTick);
        for (var control : controls) {
            if (control.isHoveredOrFocused() && (isOption(control.slot)
                    || control.slot == ExpeditionMenu.SLOT_START && !ready())) {
                gui.renderComponentTooltip(font, lore(control.slot == ExpeditionMenu.SLOT_START
                        ? ExpeditionMenu.SLOT_READINESS : control.slot), mouseX, mouseY);
            }
        }
    }

    private static void paper(GuiGraphics gui, int x, int y, int right, int bottom, int color, int grain) {
        gui.fill(x + 3, y + 4, right + 3, bottom + 4, 0x762d2017);
        for (int row = y; row < bottom; row++) {
            int edge = (row - y + grain) / 5 % 3;
            int inset = row == y || row == bottom - 1 ? 4 : edge;
            gui.fill(x + inset, row, right - inset, row + 1, color);
        }
        gui.hLine(x + 6, right - 6, bottom - 3, 0xffb29267);
        for (int mark = 0; mark < 15; mark++) {
            int fleckX = x + 6 + (mark * 47 + grain) % (right - x - 12);
            int fleckY = y + 6 + (mark * 31 + grain) % (bottom - y - 12);
            gui.fill(fleckX, fleckY, fleckX + 2, fleckY + 1, 0x26775531);
        }
    }

    private static void pin(GuiGraphics gui, int x, int y) {
        gui.fill(x - 3, y, x + 4, y + 5, 0xff8b6543);
        gui.fill(x - 2, y - 1, x + 3, y + 3, 0xff9e633a);
        gui.fill(x - 1, y - 1, x + 1, y + 1, 0xffdfaf72);
    }

    @Override protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) { }
    @Override public boolean mouseClicked(double x, double y, int button) {
        // Do not forward blank-board clicks into the hidden transport slots.
        for (var control : controls) if (control.mouseClicked(x, y, button)) return true;
        return false;
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        for (var control : controls) control.mouseReleased(x, y, button);
        return true;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return false; }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        page = Math.max(0, Math.min(pageCount() - 1, page + (dy < 0 ? 1 : -1))); return true;
    }

    private static boolean isOption(int slot) {
        return slot == ExpeditionMenu.SLOT_DIFFICULTY || slot == ExpeditionMenu.SLOT_CHECKPOINTS
                || slot == ExpeditionMenu.SLOT_TIME || slot == ExpeditionMenu.SLOT_PARTY;
    }

    private final class BoardButton extends Button {
        final int slot;
        final String label;
        BoardButton(int x, int y, int w, int h, String label, int slot, Runnable action) {
            super(x, y, w, h, Component.literal(label), ignored -> {
                if (slot >= 0) clickSlot(slot, 0); else action.run();
            }, DEFAULT_NARRATION);
            this.slot = slot; this.label = label;
        }
        @Override public boolean mouseClicked(double x, double y, int button) {
            if (button == 1 && slot >= 0 && isMouseOver(x, y)) { clickSlot(slot, 1); return true; }
            return super.mouseClicked(x, y, button);
        }
        @Override public void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float tick) {
            boolean option = isOption(slot);
            boolean twoLines = option && getHeight() >= 25;
            boolean deploy = slot == ExpeditionMenu.SLOT_START;
            boolean hovered = isHoveredOrFocused();
            String heading = deploy ? Component.translatable(ready()
                    ? "gui.expeditions.start" : "gui.expeditions.wayroot_unready").getString() : label;
            if (option && !twoLines) heading = line(slot, 0, label);
            int textX = getX() + 5;
            int textY = getY() + (twoLines ? 4 : (getHeight() - 8) / 2);
            int ink = hovered ? 0xff934524 : INK;
            if (deploy) {
                int sealX = getX() + 15;
                int sealY = getY() + getHeight() / 2;
                gui.fill(sealX - 9, sealY - 5, sealX + 9, sealY + 6, 0xff613827);
                gui.fill(sealX - 6, sealY - 8, sealX + 6, sealY + 8, 0xff613827);
                gui.fill(sealX - 7, sealY - 4, sealX + 7, sealY + 4, ready() ? 0xffa7502c : 0xff805b46);
                gui.vLine(sealX, sealY - 5, sealY + 4, ready() ? 0xffefb45e : 0xffb49372);
                gui.hLine(sealX - 3, sealX + 3, sealY, ready() ? 0xffefb45e : 0xffb49372);
                textX += 24;
            }
            int available = getWidth() - (deploy ? 32 : 8);
            String text = font.plainSubstrByWidth(heading, available);
            gui.drawString(font, text, textX, textY, twoLines ? 0xff705539 : ink, false);
            if (twoLines) {
                text = font.plainSubstrByWidth(line(slot, 0, ""), available);
                textY = getY() + 17;
                gui.drawString(font, text, textX, textY, ink, false);
            }
            if (hovered || option || (slot == -1 && label.equals(tab == 0 ? "Briefing" : "Requirements"))) {
                if (!text.isEmpty()) gui.hLine(textX, textX + font.width(text) - 1, textY + 9,
                        hovered ? 0xff934524 : 0xffa88b62);
            }
        }
    }
}
