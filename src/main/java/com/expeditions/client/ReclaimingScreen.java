package com.expeditions.client;

import com.expeditions.server.ReclaimingMenu;
import com.expeditions.block.entity.SapVesselBlockEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

/** Salvage ledger backed by the menu's synchronized quote stacks. */
public final class ReclaimingScreen extends AbstractContainerScreen<ReclaimingMenu> {
    private static final int INK = 0xff4d3626;
    private int spacing;

    public ReclaimingScreen(ReclaimingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        imageWidth = Math.min(252, width - 16);
        imageHeight = Math.min(162, height - 16);
        super.init();
        spacing = Math.min(33, Math.max(21, (imageHeight - 54) / 3));
        for (int slot = 0; slot < 3; slot++) {
            addRenderableWidget(new Quote(leftPos + 12, topPos + 33 + slot * spacing, imageWidth - 24, spacing, slot));
        }
        addRenderableWidget(new Quote(leftPos + 12, topPos + imageHeight - 17, 90, 14, 18));
        addRenderableWidget(new Quote(leftPos + imageWidth - 102, topPos + imageHeight - 17, 90, 14, 26));
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float tick) {
        gui.fill(0, 0, width, height, 0x81221912);
        renderPaper(gui);
        gui.drawString(font, Component.translatable("gui.expeditions.basin_ledger"), leftPos + 12, topPos + 10, INK, false);
        var account = menu.getContainer().getItem(22);
        if (!account.isEmpty()) {
            gui.drawString(font, font.plainSubstrByWidth(account.getHoverName().getString(), imageWidth - 91),
                    leftPos + 12, topPos + 23, 0xff765539, false);
        } else {
            var note = menu.getContainer().getItem(13);
            if (!note.isEmpty()) {
                int y = topPos + 29;
                for (var line : font.split(note.getHoverName(), imageWidth - 24)) {
                    gui.drawString(font, line, leftPos + 12, y, INK, false);
                    y += 11;
                }
            }
        }
        renderSapStain(gui, mouseX, mouseY);
        for (var widget : children()) {
            if (widget instanceof Quote quote) quote.render(gui, mouseX, mouseY, tick);
        }
        for (var widget : children()) {
            if (widget instanceof Quote quote && quote.isHoveredOrFocused()) {
                var lore = menu.getContainer().getItem(quote.slot).get(DataComponents.LORE);
                int fieldLines = 1;
                if (lore != null && lore.lines().size() > fieldLines) {
                    gui.renderComponentTooltip(font, lore.lines().subList(fieldLines, lore.lines().size()), mouseX, mouseY);
                }
                break;
            }
        }
    }

    private void renderPaper(GuiGraphics gui) {
        int right = leftPos + imageWidth;
        int bottom = topPos + imageHeight;
        gui.fill(leftPos - 3, topPos + 3, right + 5, bottom + 6, 0x9b281e18);
        gui.fill(leftPos - 2, topPos + 1, right + 2, bottom + 3, 0xff6e5942);
        for (int row = 0; row < imageHeight; row++) {
            int edge = row == 0 || row == imageHeight - 1 ? 4 : 1 + row / 5 % 3;
            int y = topPos + row;
            gui.fill(leftPos + edge, y, right - edge, y + 1, 0xffad8960);
            gui.fill(leftPos + edge + 2, y, right - edge - 2, y + 1, 0xffdfc69b);
        }
        // Wear follows the edge of the leaf and stays out of the text's contrast range.
        for (int mark = 0; mark < 34; mark++) {
            int x = leftPos + 6 + (mark * 53) % (imageWidth - 12);
            int y = topPos + 6 + (mark * 23) % (imageHeight - 12);
            gui.fill(x, y, x + 1 + mark % 3, y + 1, 0x247d5e3b);
        }
        gui.hLine(leftPos + 10, right - 12, bottom - 3, 0xffb2936d);
        int rootX = right - 34;
        int rootY = topPos + 6;
        // Stepped pressure marks left by the graft under the copper clip.
        for (int step = 0; step < 8; step++) {
            int x = rootX + step * 3;
            int y = rootY + step / 2;
            gui.fill(x, y, x + 4, y + 1, 0x596d5030);
            if (step % 2 == 0) gui.fill(x + 1, y - 2, x + 2, y, 0x426d5030);
        }
        int clip = leftPos + imageWidth / 2;
        gui.fill(clip - 13, topPos - 4, clip + 13, topPos + 4, 0xff67412b);
        gui.fill(clip - 11, topPos - 3, clip + 11, topPos + 2, 0xffb47b49);
        gui.hLine(clip - 9, clip + 9, topPos - 2, 0xffdfad71);
        gui.fill(clip - 9, topPos, clip - 7, topPos + 2, 0xff795135);
        gui.fill(clip + 7, topPos, clip + 9, topPos + 2, 0xff795135);
    }

    private void renderSapStain(GuiGraphics gui, int mouseX, int mouseY) {
        int x = leftPos + imageWidth - 70;
        int y = topPos + 20;
        int amount = menu.sapAmount();
        int capacity = SapVesselBlockEntity.BASIN_CAPACITY;
        int filled = Math.clamp(amount * 54 / capacity, 0, 54);
        gui.fill(x - 2, y - 1, x + 55, y + 8, 0xffc4a06e);
        gui.fill(x, y + 1, x + 54, y + 7, 0xffd1b383);
        if (filled > 0) {
            gui.fill(x, y + 1, x + filled, y + 7, 0xff985026);
            gui.fill(x, y + 2, x + filled, y + 4, 0xffc48443);
            gui.fill(x + filled - 1, y, x + filled, y + 8, 0xffa36035);
        }
        for (int mark = 1; mark < 4; mark++) {
            int at = x + mark * 54 / 4;
            gui.vLine(at, y + 4, y + 6, INK);
        }
        if (mouseX >= x - 3 && mouseX <= x + 56 && mouseY >= y - 4 && mouseY <= y + 10) {
            gui.renderTooltip(font, Component.translatable("message.expeditions.sap_amount", amount, capacity), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics gui, float tick, int mouseX, int mouseY) { }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        for (var widget : children()) {
            if (widget instanceof Quote quote && quote.mouseClicked(x, y, button)) return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return false; }

    @Override
    public boolean mouseReleased(double x, double y, int button) { return false; }

    private final class Quote extends Button {
        private final int slot;

        private Quote(int x, int y, int width, int height, int slot) {
            super(x, y, width, height, Component.empty(), ignored -> {
                if (minecraft.gameMode != null && minecraft.player != null && !menu.getContainer().getItem(slot).isEmpty()) {
                    minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.PICKUP, minecraft.player);
                }
            }, DEFAULT_NARRATION);
            this.slot = slot;
        }

        @Override
        public void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float tick) {
            var stack = menu.getContainer().getItem(slot);
            if (stack.isEmpty()) return;
            var lore = stack.get(DataComponents.LORE);
            var notes = lore == null ? java.util.List.<Component>of() : lore.lines();
            String name = stack.getHoverName().getString();
            setMessage(stack.getHoverName());
            int color = isHoveredOrFocused() ? 0xff9a4628 : INK;
            if (slot >= 3) {
                gui.drawString(font, name, getX(), getY() + 3, color, false);
                return;
            }
            int x = getX() + 26;
            int y = getY() + 4;
            int available = getWidth() - 26;
            gui.fill(getX(), y, getX() + 21, y + 21, 0xffb18c62);
            gui.fill(getX() + 1, y + 1, getX() + 20, y + 20, 0xffcdb38b);
            gui.renderItem(stack, getX() + 3, y + 3);
            gui.drawString(font, font.plainSubstrByWidth(name, available), x, y, color, false);
            if (!notes.isEmpty()) {
                gui.drawString(font, font.plainSubstrByWidth(notes.getFirst().getString(), available),
                        x, y + 10, 0xff765539, false);
            }
            gui.hLine(getX(), getX() + getWidth() - 1, getY() + getHeight() - 1, 0xffb5986b);
        }
    }
}
