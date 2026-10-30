package com.expeditions.server;

import com.expeditions.block.entity.AssimilationHeartBlockEntity;
import com.expeditions.core.JournalChapter;
import com.expeditions.registry.ModAttachments;
import com.expeditions.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Server-observed discoveries; owning or opening a book never grants expedition rewards. */
public final class JournalDiscovery {
    private JournalDiscovery() { }

    public static void unlock(ServerPlayer player, JournalChapter chapter) {
        int old = player.getData(ModAttachments.JOURNAL_DISCOVERIES);
        int next = chapter.unlock(old);
        if (next == old) return;
        player.setData(ModAttachments.JOURNAL_DISCOVERIES, next);
        boolean hasBook = player.getInventory().items.stream()
                .anyMatch(stack -> stack.is(ModItems.EXPEDITION_FIELD_JOURNAL.get()))
                || player.getOffhandItem().is(ModItems.EXPEDITION_FIELD_JOURNAL.get());
        if (hasBook) {
            player.sendSystemMessage(Component.translatable("message.expeditions.journal_discovery"));
            player.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.5f, 0.8f);
        }
    }

    /** Bounded observation of already-loaded hearts, plus facts the server already knows. */
    public static void observe(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()) return;
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
        if (player.getInventory().items.stream().anyMatch(stack -> stack.is(ModItems.ASSIMILATION_ESSENCE.get()))) {
            unlock(player, JournalChapter.ESSENCE);
        }
        if (Assimilation.infection(player) > 0) unlock(player, JournalChapter.BLOOD);
        var manager = ExpeditionManager.get(player.server);
        if (manager.savedData().pressure(dimension) >= 25) unlock(player, JournalChapter.PRESSURE);
        if (manager.savedData().isDimensionCleared(dimension)) unlock(player, JournalChapter.LIBERATION);
    }
}
