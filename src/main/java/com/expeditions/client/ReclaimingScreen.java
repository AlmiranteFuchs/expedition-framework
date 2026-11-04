package com.expeditions.client;

import com.expeditions.server.ReclaimingMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

/** Parchment price list backed by the menu's synchronized quote stacks. */
public final class ReclaimingScreen extends AbstractContainerScreen<ReclaimingMenu> {
    private static final int INK = 0xff4d3626;
    private int spacing;

    public ReclaimingScreen(ReclaimingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        imageWidth = Math.min(330, width - 14);
        imageHeight = Math.min(250, height - 14);
        super.init();
        spacing = Math.min(51, Math.max(24, (imageHeight - 56) / 3));
        for (int slot = 0; slot < 3; slot++) {
            addRenderableWidget(new Quote(leftPos + 12, topPos + 37 + slot * spacing, imageWidth - 24, spacing, slot));
        }
        addRenderableWidget(new Quote(leftPos + 12, topPos + imageHeight - 17, 90, 14, 18));
        addRenderableWidget(new Quote(leftPos + imageWidth - 102, topPos + imageHeight - 17, 90, 14, 26));
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float tick) {
        gui.fill(0, 0, width, height, 0xa5231811);
        gui.fill(leftPos + 3, topPos + 4, leftPos + imageWidth + 3, topPos + imageHeight + 4, 0x802c211a);
        for (int row = 0; row < imageHeight; row++) {
            int edge = row == 0 || row == imageHeight - 1 ? 4 : row / 6 % 3;
            gui.fill(leftPos + edge, topPos + row, leftPos + imageWidth - edge, topPos + row + 1, 0xffdfc69b);
        }
        gui.drawString(font, title, leftPos + 12, topPos + 10, INK, false);
        var account = menu.getContainer().getItem(22);
        if (!account.isEmpty()) {
            gui.drawString(font, font.plainSubstrByWidth(account.getHoverName().getString(), imageWidth - 24),
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
        for (var widget : children()) {
            if (widget instanceof Quote quote) quote.render(gui, mouseX, mouseY, tick);
        }
        for (var widget : children()) {
            if (widget instanceof Quote quote && quote.isHoveredOrFocused()) {
                var lore = menu.getContainer().getItem(quote.slot).get(DataComponents.LORE);
                if (lore != null && !lore.lines().isEmpty()) gui.renderComponentTooltip(font, lore.lines(), mouseX, mouseY);
                break;
            }
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
            String price = lore == null || lore.lines().isEmpty() ? "" : lore.lines().getFirst().getString();
            String name = stack.getHoverName().getString();
            setMessage(Component.literal(price.isEmpty() ? name : name + " · " + price));
            int y = slot < 3 ? getY() + 3 : getY() + (getHeight() - 8) / 2;
            int color = isHoveredOrFocused() ? 0xff9a4628 : INK;
            int priceWidth = font.width(price);
            gui.drawString(font, font.plainSubstrByWidth(name, Math.max(0, getWidth() - priceWidth - 6)), getX(), y, color, false);
            if (!price.isEmpty()) {
                gui.drawString(font, price, getX() + getWidth() - priceWidth, y, color, false);
                for (int line = 1; line < lore.lines().size() && 3 + 9 * line + 8 <= getHeight(); line++) {
                    String note = font.plainSubstrByWidth(lore.lines().get(line).getString(), getWidth());
                    gui.drawString(font, note, getX(), y + 9 * line, 0xff765539, false);
                }
                gui.hLine(getX(), getX() + getWidth() - 1, getY() + getHeight() - 1, 0xffb5986b);
            }
        }
    }
}
