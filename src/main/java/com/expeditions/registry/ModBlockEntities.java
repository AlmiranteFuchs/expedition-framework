package com.expeditions.registry;

import com.expeditions.ExpeditionsMod;
import com.expeditions.block.entity.AssimilationHeartBlockEntity;
import com.expeditions.block.entity.AssimilationLayerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Block entity type registrations. */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ExpeditionsMod.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<AssimilationLayerBlockEntity>> ASSIMILATION_LAYER =
            BLOCK_ENTITIES.register("assimilation_layer",
                    () -> BlockEntityType.Builder.of(AssimilationLayerBlockEntity::new,
                            ModBlocks.ASSIMILATION_LAYER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<AssimilationHeartBlockEntity>> ASSIMILATION_HEART =
            BLOCK_ENTITIES.register("assimilation_heart",
                    () -> BlockEntityType.Builder.of(AssimilationHeartBlockEntity::new,
                            ModBlocks.ASSIMILATION_HEART.get()).build(null));

    private ModBlockEntities() {
    }
}
