package com.expeditions.registry;

import com.expeditions.ExpeditionsMod;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Data component registrations — stack-level metadata, persisted with the
 * item stack through inventories, drops and saves.
 */
public final class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ExpeditionsMod.MOD_ID);

    /**
     * The expedition run an artifact stack belongs to (UUID string). Binds
     * the physical item to exactly one run — foreign or stale artifacts can
     * never satisfy another run's delivery.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> RUN_ID =
            DATA_COMPONENTS.register("run_id",
                    () -> DataComponentType.<String>builder()
                            .persistent(Codec.STRING)
                            .build());

    /**
     * The progression key an attunement sigil grants at the terminal.
     * Unlike {@link #RUN_ID} this binds nothing to a run — the sigil is a
     * permanent artifact, and the key it carries is a player's permanent
     * unlock once attuned.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> ATTUNEMENT_KEY =
            DATA_COMPONENTS.register("attunement_key",
                    () -> DataComponentType.<String>builder()
                            .persistent(Codec.STRING)
                            .build());

    private ModDataComponents() {
    }
}
