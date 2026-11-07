package com.expeditions.client;

import com.expeditions.server.ReclaimingMenu;
import com.expeditions.block.entity.SapVesselBlockEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

/** Salvage ledger backed by the menu's synchronized quote stacks. */
public final class ReclaimingScreen extends AbstractContainerScreen<ReclaimingMenu> {
    private static final int INK = 0xff493a2c;
    private static final ResourceLocation FRAME = ResourceLocation.parse("expeditions:salvage_frame");
    private static final ResourceLocation LEAF = ResourceLocation.parse("expeditions:salvage_leaf");
    private int spacing;

    public ReclaimingScreen(ReclaimingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        imageWidth = Math.min(252, width - 16);
        imageHeight = Math.min(176, height - 16);
        super.init();
        spacing = Math.min(34, Math.max(21, (imageHeight - 70) / 3));
        for (int slot = 0; slot < 3; slot++) {
            addRenderableWidget(new Quote(leftPos + 15, topPos + 44 + slot * spacing, imageWidth - 44, spacing, slot));
        }
        addRenderableWidget(new Quote(leftPos + 15, topPos + imageHeight - 28, 86, 14, 18));
        addRenderableWidget(new Quote(leftPos + imageWidth - 101, topPos + imageHeight - 28, 86, 14, 26));
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float tick) {
        gui.fill(0, 0, width, height, 0x81221912);
        renderPaper(gui);
        var title = Component.translatable("gui.expeditions.basin_ledger");
        gui.drawString(font, title, leftPos + (imageWidth - font.width(title)) / 2, topPos + 17, INK, false);
        var account = menu.getContainer().getItem(22);
        if (!account.isEmpty()) {
            gui.drawString(font, font.plainSubstrByWidth(account.getHoverName().getString(), imageWidth - 34),
                    leftPos + 20, topPos + 30, 0xff765539, false);
        }
        var note = menu.getContainer().getItem(13);
        if (!note.isEmpty()) {
            int y = topPos + (account.isEmpty() ? 42 : 48);
            for (var line : font.split(note.getHoverName(), imageWidth - 48)) {
                gui.drawString(font, line, leftPos + 17, y, INK, false);
                y += 11;
            }
        }
        renderSapStain(gui, mouseX, mouseY);
        var page = menu.getContainer().getItem(23);
        if (!page.isEmpty()) {
            var label = page.getHoverName();
            gui.drawString(font, label, leftPos + (imageWidth - font.width(label)) / 2,
                    topPos + imageHeight - 25, INK, false);
        }
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
        gui.blitSprite(FRAME, leftPos, topPos, imageWidth, imageHeight);
    }

    private void renderSapStain(GuiGraphics gui, int mouseX, int mouseY) {
        int x = leftPos + imageWidth - 23;
        int y = topPos + 46;
        int length = imageHeight - 84;
        int amount = menu.sapAmount();
        int capacity = SapVesselBlockEntity.BASIN_CAPACITY;
        int filled = Math.clamp(amount * (length - 4) / capacity, 0, length - 4);
        gui.fill(x - 2, y - 2, x + 10, y + length + 2, 0xff59452f);
        gui.fill(x - 1, y - 1, x + 9, y + length + 1, 0xff9a8060);
        gui.fill(x, y, x + 8, y + length, 0xff493a2c);
        gui.fill(x + 1, y + 1, x + 2, y + length - 1, 0xffc0ab8b);
        if (filled > 0) {
            int surface = y + length - 2 - filled;
            gui.fill(x + 2, surface, x + 7, y + length - 2, 0xff9b5728);
            gui.fill(x + 3, surface, x + 5, y + length - 2, 0xffc88a3d);
            gui.hLine(x + 2, x + 6, surface, 0xffe1b56e);
        }
        for (int mark = 1; mark < 4; mark++) {
            int at = y + mark * length / 4;
            gui.hLine(x + 5, x + 7, at, 0xff372b20);
        }
        if (mouseX >= x - 3 && mouseX <= x + 11 && mouseY >= y - 3 && mouseY <= y + length + 3) {
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
                int labelX = slot == 26 ? getX() + getWidth() - font.width(name) : getX();
                gui.drawString(font, name, labelX, getY() + 3, color, false);
                return;
            }
            gui.blitSprite(LEAF, getX(), getY(), getWidth(), getHeight() - 1);
            int x = getX() + 26;
            int y = getY() + 4;
            int available = getWidth() - 26;
            gui.fill(getX(), y, getX() + 21, y + 21, 0xff927c5d);
            gui.fill(getX() + 1, y + 1, getX() + 20, y + 20, 0xffb6a185);
            gui.renderItem(stack, getX() + 3, y + 3);
            var price = notes.size() > 1 ? notes.get(1) : Component.empty();
            int priceWidth = font.width(price);
            gui.drawString(font, price, getX() + getWidth() - priceWidth, y, 0xff765539, false);
            gui.drawString(font, font.plainSubstrByWidth(name, available - priceWidth - 6), x, y, color, false);
            if (!notes.isEmpty()) {
                gui.drawString(font, font.plainSubstrByWidth(notes.getFirst().getString(), available),
                        x, y + 10, 0xff765539, false);
            }

        }
    }
}
