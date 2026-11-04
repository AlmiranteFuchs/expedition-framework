package com.expeditions.registry;

import com.expeditions.ExpeditionsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's creative tab. Keeping our own tab keeps the terminal obtainable in
 * creative mode without mutating vanilla tabs.
 */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(
            Registries.CREATIVE_MODE_TAB, ExpeditionsMod.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXPEDITIONS_TAB = CREATIVE_MODE_TABS.register(
            "expeditions",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.expeditions"))
                    .icon(() -> new ItemStack(ModItems.EXPEDITION_TERMINAL_ITEM.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.EXPEDITION_TERMINAL_ITEM.get());
                        output.accept(ModItems.GRAFT_COLLAR.get());
                        output.accept(ModItems.GRAFT_POST.get());
                        output.accept(ModItems.RECLAIMING_BASIN.get());
                        output.accept(ModItems.SAP_JAR.get());
                        output.accept(ModItems.ROOTWICK.get());
                        output.accept(ModItems.RETURN_KNOT.get());
                        output.accept(ModItems.RETURN_SAP_BUCKET.get());
                        output.accept(ModItems.EXPEDITION_CHECKPOINT_ITEM.get());
                        output.accept(ModItems.EXPEDITION_EXIT_ITEM.get());
                        output.accept(ModItems.EXPEDITION_COMPASS.get());
                        output.accept(ModItems.EXPEDITION_ARTIFACT.get());
                        output.accept(ModItems.ASSIMILATION_LAYER_ITEM.get());
                        output.accept(ModItems.ASSIMILATION_HEART_ITEM.get());
                        output.accept(ModItems.ASSIMILATION_ESSENCE.get());
                        output.accept(ModItems.DORMANT_FLAKE.get());output.accept(ModItems.DORMANT_LAYER_ITEM.get());
                        output.accept(ModItems.DORMANT_HEART_ITEM.get());output.accept(ModItems.STILLNESS_DYNAMO_ITEM.get());
                        output.accept(ModItems.ESSENCE_CONDENSER_ITEM.get());
                        output.accept(ModItems.RESONANCE_CELL.get());output.accept(ModItems.SURVEY_LENS.get());output.accept(ModItems.FIELD_WARD_ITEM.get());
                        output.accept(ModItems.DORMANT_KNOT.get());output.accept(ModItems.SEVERANCE_CORE.get());
                        output.accept(ModItems.LIVING_HEART_ENGINE_ITEM.get());output.accept(ModItems.SEVERANCE_GENERATOR_ITEM.get());
                        output.accept(ModItems.ATTUNEMENT_SIGIL.get());
                        output.accept(ModItems.FIELD_ASSAY_KIT.get());
                        output.accept(ModItems.DISRUPTION_CHARM.get());
                        output.accept(ModItems.EXPEDITION_FIELD_JOURNAL.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
