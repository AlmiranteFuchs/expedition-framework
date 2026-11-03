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
        control(leftPos + 12, topPos + 35, 52, 20, "< Mission", ExpeditionMenu.SLOT_PREVIOUS, null);
        control(leftPos + 68, topPos + 35, 58, 20, "Mission >", ExpeditionMenu.SLOT_NEXT, null);
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
        control(column, topPos + imageHeight - 29, detailWidth, 21, "Open portal", ExpeditionMenu.SLOT_START, null);
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
        gui.fill(0, 0, width, height, 0xc8101411);
        gui.fill(leftPos - 2, topPos - 2, leftPos + imageWidth + 2, topPos + imageHeight + 2, 0xff171b16);
        gui.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff8b8060);
        gui.fill(leftPos + 4, topPos + 4, leftPos + 134, topPos + imageHeight - 4, 0xff2c3027);
        gui.fill(leftPos + 135, topPos + 4, leftPos + imageWidth - 4, topPos + imageHeight - 4, 0xffdfd3b1);
        for (int y = topPos + 12; y < topPos + imageHeight - 8; y += 12) gui.fill(leftPos + 2, y, leftPos + 5, y + 2, 0xffd0c9a3);
        gui.drawString(font, "CHARTER", leftPos + 15, topPos + 12, 0xffded4af, false);
        gui.drawString(font, "Deployment terminal", leftPos + 15, topPos + 23, 0xffa49c7c, false);
        gui.drawString(font, font.plainSubstrByWidth(selected, detailWidth), column, topPos + 11, INK, false);
        gui.drawString(font, font.plainSubstrByWidth(line(ExpeditionMenu.SLOT_EXPEDITION, 1, ""), detailWidth),
                column, topPos + 25, 0xff786b4e, false);
        gui.drawString(font, line(ExpeditionMenu.SLOT_EXPEDITION, 2, ""), leftPos + 15, topPos + 58, 0xffaaa181, false);
        if (imageHeight >= 252) {
            gui.drawString(font, "Secured tokens", leftPos + 15, topPos + imageHeight - 44, 0xffc8bc95, false);
        gui.drawString(font, "survive failure.", leftPos + 15, topPos + imageHeight - 33, 0xffaaa181, false);
        }
        if (imageHeight >= 180) gui.drawString(font, "Esc: close", leftPos + 15, topPos + imageHeight - 16, 0xff847e65, false);
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
            int color = deploy ? ready() ? 0xff795b30 : 0xff704333 : slot >= 0 ? 0xff424637 : 0xffc5b58f;
            if (isHoveredOrFocused()) color = deploy ? 0xff98723d : slot >= 0 ? 0xff5b5c43 : 0xffb7a070;
            gui.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), color);
            String heading = deploy ? ready() ? "Open portal" : "Requirements missing" : label;
            if (option && !twoLines) heading = label + ": " + line(slot, 0, "");
            gui.drawString(font, font.plainSubstrByWidth(heading, getWidth() - 8), getX() + 5,
                    getY() + (twoLines ? 4 : (getHeight() - 8) / 2), slot >= 0 ? 0xffeee0b8 : INK, false);
            if (twoLines) gui.drawString(font, font.plainSubstrByWidth(line(slot, 0, "Default"), getWidth() - 8),
                    getX() + 5, getY() + 17, 0xffc5bb96, false);
        }
    }
}
