package com.expeditions.client;

import com.expeditions.core.JournalChapter;
import com.expeditions.registry.ModAttachments;
import com.expeditions.registry.ModItems;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;

/** A personal, discovery-gated field guide. All unlock authority remains on the server. */
public final class FieldJournalScreen extends Screen {
    private static final int INK = 0xff393126, MUTED = 0xff7b6c50, EMBER = 0xffb46a27;
    private final InteractionHand hand;
    private final JsonObject text;
    private final List<Button> tabs = new ArrayList<>();
    private JournalChapter selected = JournalChapter.FIRST_STEPS;
    private int page;
    private int left, top, bookWidth, bookHeight, margin, pageX, pageWidth;

    public FieldJournalScreen(InteractionHand hand) {
        super(Component.literal("The Charter Field Guide"));
        this.hand = hand;
        JsonObject loaded;
        try (var reader = Minecraft.getInstance().getResourceManager().openAsReader(
                ResourceLocation.fromNamespaceAndPath("expeditions", "journal/chapters.json"))) {
            loaded = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception error) {
            com.expeditions.ExpeditionsMod.LOGGER.warn("Could not read journal chapters", error);
            loaded = new JsonObject();
        }
        text = loaded;
    }

    @Override
    protected void init() {
        bookWidth = Math.min(430, width - 14);
        bookHeight = Math.min(290, height - 14);
        left = (width - bookWidth) / 2;
        top = (height - bookHeight) / 2;
        margin = Math.min(128, bookWidth / 3);
        pageX = left + margin + 12;
        pageWidth = bookWidth - margin - 24;
        tabs.clear();
        int rowHeight = Math.min(17, (bookHeight - 62) / JournalChapter.values().length);
        int index = 0;
        for (var chapter : JournalChapter.values()) {
            var button = new PaperButton(left + 9, top + 33 + index++ * rowHeight,
                    margin - 15, rowHeight, Component.literal(title(chapter)), ignored -> {
                        selected = chapter; page = 0; turnSound();
                    }, chapter);
            tabs.add(addRenderableWidget(button));
        }
        addRenderableWidget(new PaperButton(pageX, top + bookHeight - 24, 40, 16,
                Component.literal("<"), ignored -> { if (page > 0) { page--; turnSound(); } }, null));
        addRenderableWidget(new PaperButton(left + bookWidth - 54, top + bookHeight - 24, 40, 16,
                Component.literal(">"), ignored -> { if (page + 1 < pages().size()) { page++; turnSound(); } }, null));
    }

    private int discoveries() {
        return minecraft.player == null ? 0 : minecraft.player.getData(ModAttachments.JOURNAL_DISCOVERIES);
    }

    private JsonObject entry(JournalChapter chapter) {
        return text.has(chapter.id()) ? text.getAsJsonObject(chapter.id()) : new JsonObject();
    }

    private String field(JsonObject object, String key, String fallback) {
        return object.has(key) ? object.get(key).getAsString() : fallback;
    }

    private String title(JournalChapter chapter) {
        return field(entry(chapter), "title", chapter.id());
    }

    private record Page(String heading, String body) { }

    private List<Page> pages() {
        var entry = entry(selected);
        if (!selected.known(discoveries())) {
            return List.of(new Page("An unwritten page", "The paper is cold. Something is missing from your field notes.\n\n"
                    + field(entry, "unlock_hint", "Continue exploring to discover this entry.")));
        }
        List<Page> result = new ArrayList<>();
        if (entry.has("pages")) {
            for (var item : entry.getAsJsonArray("pages")) {
                var object = item.getAsJsonObject();
                String heading = field(object, "heading", title(selected));
                String body = field(object, "body", "");
                // Paginate against the actual window/font so large GUI scales
                // cannot hide a crucial instruction below the page edge.
                appendPages(result, heading, body);
            }
        }
        int record = selected == JournalChapter.FIRST_STEPS ? 0
                : selected == JournalChapter.PRESSURE ? 1 : selected == JournalChapter.BLOOD ? 2 : -1;
        if (record >= 0 && minecraft.player != null) {
            var content = minecraft.player.getItemInHand(hand).get(DataComponents.WRITTEN_BOOK_CONTENT);
            if (content != null && content.getPages(false).size() > record) {
                String body = content.getPages(false).get(record).getString().replaceAll("§[0-9a-fk-or]", "");
                appendPages(result, "Current field record", body);
            }
        }
        return result.isEmpty() ? List.of(new Page(title(selected), "Field notes are being transcribed.")) : result;
    }

    private void appendPages(List<Page> result, String heading, String body) {
        var lines = font.split(Component.literal(body), pageWidth - 16);
        int linesPerPage = Math.max(5, (bookHeight - 88) / 10);
        for (int start = 0; start < lines.size(); start += linesPerPage) {
            StringBuilder part = new StringBuilder();
            for (int n = start; n < Math.min(lines.size(), start + linesPerPage); n++) {
                lines.get(n).accept((i, style, codepoint) -> { part.appendCodePoint(codepoint); return true; });
                part.append('\n');
            }
            result.add(new Page(heading, part.toString()));
        }
    }

    @Override
    public void tick() {
        if (minecraft.player == null || !minecraft.player.getItemInHand(hand)
                .is(ModItems.EXPEDITION_FIELD_JOURNAL.get())) onClose();
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        gui.fill(0, 0, width, height, 0xc8101411);
        // Dark leather, pale edge wear, stitched spine, and a warm paper leaf.
        gui.fill(left - 2, top - 2, left + bookWidth + 2, top + bookHeight + 2, 0xff181b16);
        gui.fill(left, top, left + bookWidth, top + bookHeight, 0xff39372b);
        gui.fill(left + 3, top + 3, left + bookWidth - 3, top + bookHeight - 3, 0xff70674e);
        gui.fill(left + 5, top + 5, left + margin, top + bookHeight - 5, 0xff2c3027);
        gui.fill(left + margin, top + 5, left + bookWidth - 5, top + bookHeight - 5, 0xffdfd3b1);
        gui.fill(left + margin, top + 5, left + margin + 4, top + bookHeight - 5, 0xff9d8d67);
        for (int y = top + 10; y < top + bookHeight - 8; y += 9) {
            gui.fill(left + 3, y, left + 6, y + 2, 0xffbbb18d);
            gui.fill(left + bookWidth - 5, y, left + bookWidth - 3, y + 2, 0xffb9aa85);
        }
        gui.drawString(font, "THE CHARTER", left + 13, top + 12, 0xffd6cfac, false);
        gui.drawString(font, "Field notes", left + 13, top + 23, 0xffa49c7c, false);
        var pages = pages();
        page = Math.min(page, pages.size() - 1);
        var current = pages.get(page);
        gui.drawString(font, font.plainSubstrByWidth(current.heading, pageWidth - 32), pageX + 3, top + 18, INK, false);
        if (selected.known(discoveries())) {
            var item = switch (selected) {
                case BLOOM, LIBERATION -> ModItems.ASSIMILATION_HEART_ITEM.get();
                case ESSENCE, BLOOD, PRESSURE -> ModItems.ASSIMILATION_ESSENCE.get();
                case TERMINAL -> ModItems.EXPEDITION_TERMINAL_ITEM.get();
                case WAYFINDING -> ModItems.EXPEDITION_COMPASS.get();
                case CHECKPOINTS, DEFENSE -> ModItems.EXPEDITION_CHECKPOINT_ITEM.get();
                case CARGO -> ModItems.EXPEDITION_ARTIFACT.get();
                case EXTRACTION -> ModItems.EXPEDITION_EXIT_ITEM.get();
                default -> ModItems.EXPEDITION_FIELD_JOURNAL.get();
            };
            gui.renderFakeItem(new net.minecraft.world.item.ItemStack(item), left + bookWidth - 33, top + 12);
        }
        gui.fill(pageX + 3, top + 32, left + bookWidth - 17, top + 33, 0xffb69f71);
        int y = top + 43;
        for (var line : font.split(Component.literal(current.body), pageWidth - 16)) {
            if (y > top + bookHeight - 39) break;
            gui.drawString(font, line, pageX + 3, y, INK, false);
            y += 10;
        }
        long known = java.util.Arrays.stream(JournalChapter.values()).filter(c -> c.known(discoveries())).count();
        gui.drawString(font, known + "/" + JournalChapter.values().length + " discoveries",
                left + 12, top + bookHeight - 16, 0xffb5ac8b, false);
        String number = (page + 1) + " / " + pages.size();
        gui.drawString(font, number, pageX + (pageWidth - font.width(number)) / 2,
                top + bookHeight - 20, MUTED, false);
        super.render(gui, mouseX, mouseY, partialTick);
    }

    private void turnSound() {
        if (minecraft.player != null) minecraft.player.playSound(SoundEvents.BOOK_PAGE_TURN, 0.5f, 0.9f);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private final class PaperButton extends Button {
        private final JournalChapter chapter;
        PaperButton(int x, int y, int width, int height, Component message, OnPress press, JournalChapter chapter) {
            super(x, y, width, height, message, press, DEFAULT_NARRATION);
            this.chapter = chapter;
        }
        @Override
        public void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float tick) {
            boolean known = chapter == null || chapter.known(discoveries());
            boolean current = chapter != null && selected == chapter;
            int background = chapter == null ? 0xffc4b48e : current ? 0xff5f5035 : 0xff2c3027;
            if (isHoveredOrFocused()) background = chapter == null ? 0xffb9a276 : 0xff4b4936;
            gui.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight() - 1, background);
            if (current) gui.fill(getX(), getY(), getX() + 2, getY() + getHeight() - 1, EMBER);
            String label = font.plainSubstrByWidth((known ? "" : "· ") + getMessage().getString(), getWidth() - 8);
            gui.drawString(font, label, getX() + 5, getY() + (getHeight() - 8) / 2,
                    chapter == null ? INK : known ? 0xffe3d8b2 : 0xff847f6a, false);
        }
    }
}
