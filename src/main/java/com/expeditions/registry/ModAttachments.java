package com.expeditions.registry;

import com.expeditions.ExpeditionsMod;
import com.expeditions.server.HiveFocus;
import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.payload.SyncAttachmentsPayload;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Entity metadata. Appearance/discovery attachments synchronize to clients;
 * shared hive attention remains transient and server-only.
 */
public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ExpeditionsMod.MOD_ID);
    public static final DeferredHolder<AttachmentType<?>,AttachmentType<HiveFocus>> HIVE_FOCUS =
            ATTACHMENTS.register("hive_focus",()->AttachmentType.builder(()->HiveFocus.EMPTY).build());

    /** Contact recorded during movement and consumed after the packet handler returns. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Optional<GlobalPos>>> PORTAL_CONTACT =
            ATTACHMENTS.register("portal_contact", () -> AttachmentType.<Optional<GlobalPos>>builder(Optional::empty).build());

    public static final int ARRIVAL_GLOW_TICKS = 32;
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> ARRIVAL_GLOW_UNTIL =
            ATTACHMENTS.register("arrival_glow_until", () -> AttachmentType.builder(() -> 0L)
                    .sync((holder, to) -> NetworkRegistry.hasChannel(
                            to.connection, SyncAttachmentsPayload.TYPE.id()), ByteBufCodecs.VAR_LONG)
                    .build());

    /**
     * Assimilation stage carried by an entity: 0 clean, 1-3 increasingly
     * taken. Set on expedition-spawned hordes (the organism's antibodies
     * wearing local fauna) and mirrored onto infected players from
     * {@code Assimilation}'s stage machine. Persisted so a save→load keeps
     * the mark; synced so the client renders the overlay.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> ASSIMILATED =
            ATTACHMENTS.register("assimilated", () -> AttachmentType
                    .builder(() -> 0)
                    .serialize(Codec.INT)
                    // GameTest mock players ride embedded channels that never
                    // negotiated payloads — sending to one throws. Gate the
                    // sync on the channel actually accepting it.
                    .sync((holder, to) -> NetworkRegistry.hasChannel(
                                    to.connection, SyncAttachmentsPayload.TYPE.id()),
                            ByteBufCodecs.VAR_INT)
                    .build());

    private ModAttachments() {
    }

    /** Personal field notes survive death and synchronize through the negotiated attachment channel. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> JOURNAL_DISCOVERIES =
            ATTACHMENTS.register("journal_discoveries", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT).copyOnDeath()
                    .sync((holder, to) -> NetworkRegistry.hasChannel(
                            to.connection, SyncAttachmentsPayload.TYPE.id()), ByteBufCodecs.VAR_INT)
                    .build());
}
