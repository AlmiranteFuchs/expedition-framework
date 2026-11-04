package com.expeditions.registry;

import com.expeditions.ExpeditionsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, ExpeditionsMod.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_OPEN = register("block.expedition_portal.open");
    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_TRAVEL = register("block.expedition_portal.travel");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHECKPOINT = register("block.expedition_checkpoint.activate");
    public static final DeferredHolder<SoundEvent, SoundEvent> HORDE = register("event.horde.spawn");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEVICE_START = register("block.expedition_device.activate");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEVICE_BREAK = register("block.expedition_device.destroy");
    public static final DeferredHolder<SoundEvent, SoundEvent> RECALL = register("block.recall_tree.answer");
    public static final DeferredHolder<SoundEvent, SoundEvent> FAILURE = register("event.expedition.fail");
    public static final DeferredHolder<SoundEvent, SoundEvent> WAYROOT_GRAFT = register("block.wayroot.graft");

    private ModSounds() { }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String path) {
        return EVENTS.register(path, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(ExpeditionsMod.MOD_ID, path)));
    }
}
