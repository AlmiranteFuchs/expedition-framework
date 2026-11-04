package com.expeditions.client;

import java.util.function.UnaryOperator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import vazkii.patchouli.api.ICustomComponent;
import vazkii.patchouli.api.IComponentRenderContext;
import vazkii.patchouli.api.IVariable;

/** A live, paginated view of the report synchronized into the particular held journal. */
public final class JournalRecordComponent implements ICustomComponent {
    public int record;
    private transient int x, y, pageNumber, offset;
    private static final int LINES = 12;

    @Override public void onVariablesAvailable(UnaryOperator<IVariable> lookup, HolderLookup.Provider registries) { }
    @Override public void build(int x, int y, int pageNumber) {
        this.x = x; this.y = y; this.pageNumber = pageNumber;
    }
    private java.util.List<net.minecraft.util.FormattedCharSequence> lines() {
        var mc = Minecraft.getInstance();
        String body = "Open this entry while holding your field journal to read its latest record.";
        if (mc.player != null) {
            var stack = mc.player.getItemInHand(JournalBook.hand());
            if (stack.is(com.expeditions.registry.ModItems.EXPEDITION_FIELD_JOURNAL.get())) {
                var report = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
                if (report != null && report.getPages(false).size() > record) {
                    body = report.getPages(false).get(record).getString().replaceAll("§[0-9a-fk-or]", "");
                }
            }
        }
        return mc.font.split(Component.literal(body), 112);
    }
    @Override public void onDisplayed(IComponentRenderContext context) {
        context.addWidget(new LeafArrow(x, y + 155, "<", () -> offset = Math.max(0, offset - 1)), pageNumber);
        context.addWidget(new LeafArrow(x + 93, y + 155, ">", () -> {
            if ((offset + 1) * LINES < lines().size()) offset++;
        }), pageNumber);
    }
    @Override public void render(GuiGraphics graphics, IComponentRenderContext context, float ticks, int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;
        var lines = lines();
        int count = Math.max(1, (lines.size() + LINES - 1) / LINES);
        offset = Math.min(offset, count - 1);
        graphics.drawString(font, "Current field record", x, y, context.getHeaderColor(), false);
        graphics.fill(x, y + 11, x + 112, y + 12, 0xffa79770);
        for (int i = offset * LINES; i < Math.min(lines.size(), (offset + 1) * LINES); i++) {
            graphics.drawString(font, lines.get(i), x, y + 18 + (i % LINES) * 9, context.getTextColor(), false);
        }
        graphics.drawCenteredString(font, (offset + 1) + " / " + count, x + 56, y + 140, 0xff76664a);
    }
    private static final class LeafArrow extends Button {
        LeafArrow(int x, int y, String label, Runnable action) {
            super(x, y, 19, 14, Component.literal(label), ignored -> action.run(), DEFAULT_NARRATION);
        }
        @Override public void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float tick) {
            gui.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + 9, getY() + 2,
                    isHoveredOrFocused() ? 0xffb46a27 : 0xff675335);
        }
    }
}
