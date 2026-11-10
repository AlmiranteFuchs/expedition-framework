package com.expeditions.client;

import com.expeditions.block.entity.SapVesselBlockEntity;
import com.expeditions.server.SapBrewMenu;
import com.expeditions.server.SapTrace;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class SapBrewScreen extends AbstractContainerScreen<SapBrewMenu> {
    private static final ResourceLocation FRAME = ResourceLocation.fromNamespaceAndPath("expeditions", "salvage_frame");
    private static final int INK = 0xff493a2c;

    public SapBrewScreen(SapBrewMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 212;
        imageHeight = 199;
    }

    @Override protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        gui.blitSprite(FRAME, leftPos, topPos, imageWidth, imageHeight);
        for (var slot : menu.slots) {
            if (slot.x < 0) continue;
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            gui.fill(x - 1, y - 1, x + 17, y + 17, 0xff9c825f);
            gui.fill(x, y, x + 16, y + 16, 0xffc4ab85);
        }
        int color = 0xff000000 | SapTrace.of(menu.inputTrace()).color();
        gui.fill(leftPos + 24, topPos + 68, leftPos + 188, topPos + 72, 0xffa48863);
        int progress = 164 * menu.progress() / SapVesselBlockEntity.BATCH_TICKS;
        gui.fill(leftPos + 24, topPos + 68, leftPos + 24 + progress, topPos + 72, color);
    }

    @Override protected void renderLabels(GuiGraphics gui, int mouseX, int mouseY) {
        gui.drawString(font, title, 20, 11, INK, false);
        String state = switch (menu.status()) {
            case NEED_KNOT -> "Add a bound Return Knot";
            case NEED_ESSENCE -> "Add Assimilation Essence";
            case NEED_WATER -> "Add 1,000 mB water";
            case OUTPUT_BLOCKED -> "Drain the sap output";
            case NEED_POWER -> "Needs power or a charged cell";
            case PAUSED -> "Paused by redstone";
            case BREWING -> "Drawing the trace · " + menu.progress() / 2 + "%";
        };
        gui.drawString(font, font.plainSubstrByWidth(state, 180), 20, 24, INK, false);
        gui.drawString(font, "Knot", 40, 36, INK, false);
        gui.drawString(font, "Essence", 83, 36, INK, false);
        gui.drawString(font, "Cell", 144, 36, INK, false);
        gui.drawString(font, "Water " + menu.water() + " mB   ·   Power " + menu.energy() + " R", 20, 78, INK, false);
        var trace = menu.sap() > 0 ? menu.outputTrace() : menu.inputTrace();
        var output = Component.literal(menu.sap() + " mB sap  ").append(SapTrace.label(trace));
        gui.drawString(font, font.plainSubstrByWidth(output.getString(), 180), 20, 91,
                0xff000000 | SapTrace.of(trace).color(), false);
        gui.drawString(font, playerInventoryTitle, 26, 103, INK, false);
    }

    @Override public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);
        renderTooltip(gui, mouseX, mouseY);
    }
}
