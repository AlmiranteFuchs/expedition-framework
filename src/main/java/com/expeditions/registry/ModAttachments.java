package com.expeditions.registry;

import com.expeditions.ExpeditionsMod;
import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.payload.SyncAttachmentsPayload;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Data attachment registrations — entity-level metadata synced to tracking
 * clients, the channel that lets the client overlay and ember particles
 * telegraph "taken" on any entity, ours or a mod's.
 */
public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ExpeditionsMod.MOD_ID);

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
}
