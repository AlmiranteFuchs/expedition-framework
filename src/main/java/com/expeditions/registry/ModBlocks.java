package com.expeditions.registry;

import com.expeditions.ExpeditionsMod;
import com.expeditions.block.ExpeditionTerminalBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Block registrations. All blocks use {@link DeferredRegister} so they are
 * created at the correct registry phase and referenced via {@link DeferredHolder}.
 */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ExpeditionsMod.MOD_ID);

    public static final DeferredHolder<Block, ExpeditionTerminalBlock> EXPEDITION_TERMINAL = BLOCKS.register(
            "expedition_terminal",
            () -> new ExpeditionTerminalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> 7)));

    private ModBlocks() {
    }
}
