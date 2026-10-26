package com.expeditions.registry;

import com.expeditions.ExpeditionsMod;
import com.expeditions.block.AssimilationLayerBlock;
import com.expeditions.block.ExpeditionCheckpointBlock;
import com.expeditions.block.ExpeditionDeviceBlock;
import com.expeditions.block.ExpeditionExitBlock;
import com.expeditions.block.ExpeditionPortalBlock;
import com.expeditions.block.ExpeditionTerminalBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
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

    /**
     * Route markers placed by the server while a run is active. They are
     * indestructible so a run cannot become unwinnable through breaking them.
     */
    public static final DeferredHolder<Block, ExpeditionCheckpointBlock> EXPEDITION_CHECKPOINT = BLOCKS.register(
            "expedition_checkpoint",
            () -> new ExpeditionCheckpointBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(-1.0f, 3_600_000.0f)
                    .noLootTable()
                    .lightLevel(state -> 11)));

    public static final DeferredHolder<Block, ExpeditionExitBlock> EXPEDITION_EXIT = BLOCKS.register(
            "expedition_exit",
            () -> new ExpeditionExitBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GREEN)
                    .strength(-1.0f, 3_600_000.0f)
                    .noLootTable()
                    .lightLevel(state -> 13)));

    /**
     * Defense device deployed by a {@code defend_device} objective. Shares
     * the markers' pickaxe protection — its explicit health lives in the
     * run record, so only siege damage routed through the manager destroys
     * it. Never an item.
     */
    public static final DeferredHolder<Block, ExpeditionDeviceBlock> EXPEDITION_DEVICE = BLOCKS.register(
            "expedition_device",
            () -> new ExpeditionDeviceBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(-1.0f, 3_600_000.0f)
                    .noLootTable()
                    .lightLevel(state -> 9)));

    /**
     * The Big Bad's assimilation layer: a thin membrane that attaches to one
     * face of a solid block and renders it grayscale. Harvestable for
     * assimilation essence; silently disappears when its support does.
     */
    public static final DeferredHolder<Block, AssimilationLayerBlock> ASSIMILATION_LAYER = BLOCKS.register(
            "assimilation_layer",
            () -> new AssimilationLayerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(0.2f)
                    .noLootTable()
                    .sound(SoundType.HONEY_BLOCK)
                    .noOcclusion()
                    .lightLevel(state -> 3)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));

    /**
     * Temporary teleport marker spawned above a terminal while a run is open
     * for members. Indestructible, non-solid, never an item.
     */
    public static final DeferredHolder<Block, ExpeditionPortalBlock> EXPEDITION_PORTAL = BLOCKS.register(
            "expedition_portal",
            () -> new ExpeditionPortalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .noCollission()
                    .strength(-1.0f, 3_600_000.0f)
                    .noLootTable()
                    .lightLevel(state -> 11)));

    private ModBlocks() {
    }
}
