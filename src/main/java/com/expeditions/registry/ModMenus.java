package com.expeditions.registry;

import com.expeditions.ExpeditionsMod;
import com.expeditions.server.ExpeditionMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** A vanilla slot-sync transport with a dedicated deployment presentation. */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ExpeditionsMod.MOD_ID);
    public static final DeferredHolder<MenuType<?>, MenuType<ExpeditionMenu>> EXPEDITION =
            MENUS.register("expedition", () -> new MenuType<>(ExpeditionMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>,MenuType<com.expeditions.server.ResonanceMenu>> RESONANCE=
            MENUS.register("resonance",()->net.neoforged.neoforge.common.extensions.IMenuTypeExtension.<com.expeditions.server.ResonanceMenu>create(com.expeditions.server.ResonanceMenu::new));
    private ModMenus() { }
}
