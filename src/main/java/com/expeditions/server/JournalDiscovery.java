package com.expeditions.server;

import com.expeditions.block.entity.AssimilationHeartBlockEntity;
import com.expeditions.core.JournalChapter;
import com.expeditions.registry.ModAttachments;
import com.expeditions.registry.ModSounds;
import com.expeditions.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

/** Server-observed discoveries; owning or opening a book never grants expedition rewards. */
public final class JournalDiscovery {
    private JournalDiscovery() { }

    public static void unlock(ServerPlayer player, JournalChapter chapter) {
        int old = player.getData(ModAttachments.JOURNAL_DISCOVERIES);
        int next = chapter.unlock(old);
        grantBookDiscovery(player, chapter);
        if (next == old) return;
        player.setData(ModAttachments.JOURNAL_DISCOVERIES, next);
        boolean hasBook = player.getInventory().items.stream()
                .anyMatch(stack -> stack.is(ModItems.EXPEDITION_FIELD_JOURNAL.get()))
                || player.getOffhandItem().is(ModItems.EXPEDITION_FIELD_JOURNAL.get());
        if (hasBook) {
            player.displayClientMessage(Component.translatable("message.expeditions.journal_discovery"), true);
            player.playNotifySound(ModSounds.JOURNAL_DISCOVERY.get(), SoundSource.PLAYERS, 0.5f, 0.8f);
        }
    }

    /** Bounded observation of already-loaded hearts, plus facts the server already knows. */
    public static void observe(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()) return;
        reconcileBookDiscoveries(player);
        String dimension = player.level().dimension().location().toString();
        int known = player.getData(ModAttachments.JOURNAL_DISCOVERIES);
        if (!JournalChapter.BLOOM.known(known)) {
            for (var heart : AssimilationHeartBlockEntity.liveHearts(dimension)) {
                if (player.blockPosition().distSqr(heart) <= 16 * 16 && player.serverLevel().isLoaded(heart)) {
                    unlock(player, JournalChapter.BLOOM);
                    break;
                }
            }
        }
        if (player.getInventory().items.stream().anyMatch(stack -> stack.is(ModItems.ASSIMILATION_ESSENCE.get()) || stack.is(ModItems.DORMANT_FLAKE.get()))) {
            unlock(player, JournalChapter.ESSENCE);
        }
        if(player.getInventory().items.stream().anyMatch(s->s.is(ModItems.ASSIMILATION_HEART_ITEM.get())||s.is(ModItems.SURVEY_LENS.get())||s.is(ModItems.FIELD_WARD_ITEM.get()))) unlock(player,JournalChapter.PREPARATION);
        if (Assimilation.infection(player) > 0) unlock(player, JournalChapter.BLOOD);
        var manager = ExpeditionManager.get(player.server);
        if (manager.activeRun(player.getUUID()).filter(run -> run.guardianEncounter().isPresent()).isPresent()) {
            unlock(player, JournalChapter.GUARDIAN);
        }
        if (manager.savedData().pressure(dimension) >= 25) unlock(player, JournalChapter.PRESSURE);
        if (manager.savedData().isDimensionCleared(dimension)) unlock(player, JournalChapter.LIBERATION);
    }

    /** Migrate existing journals without inventing any new discoveries. */
    public static void reconcileBookDiscoveries(ServerPlayer player) {
        int known = player.getData(ModAttachments.JOURNAL_DISCOVERIES);
        for (var chapter : JournalChapter.values()) {
            if (chapter.known(known)) grantBookDiscovery(player, chapter);
        }
    }

    private static void grantBookDiscovery(ServerPlayer player, JournalChapter chapter) {
        var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("expeditions", "journal/" + chapter.id());
        var advancement = player.server.getAdvancements().get(id);
        if (advancement != null && !player.getAdvancements().getOrStartProgress(advancement).isDone()) {
            player.getAdvancements().award(advancement, "discovered");
        }
    }
}
