package com.expeditions.registry;

import com.expeditions.ExpeditionsMod;
import com.expeditions.block.AssimilationHeartBlock;
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
    public static final DeferredHolder<Block, Block> PALE_WOOD = BLOCKS.register("pale_wood",
            () -> new net.minecraft.world.level.block.RotatedPillarBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_WHITE).strength(2).sound(SoundType.WOOD)));
    public static final DeferredHolder<Block, Block> PALE_PLANKS = BLOCKS.register("pale_planks",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_WHITE).strength(2).sound(SoundType.WOOD)));
    public static final DeferredHolder<Block, Block> PALE_CHEST = BLOCKS.register("pale_chest",
            () -> new com.expeditions.block.PaleChestBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_WHITE).strength(2.5f).sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredHolder<Block, Block> FIELD_CACHE = BLOCKS.register("field_cache",
            () -> new com.expeditions.block.FieldCacheBlock(BlockBehaviour.Properties.of()
                    .strength(-1, 3600000).sound(SoundType.WOOD).noLootTable().noOcclusion()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK).lightLevel(state -> 4)));

    public static final DeferredHolder<Block, ExpeditionTerminalBlock> EXPEDITION_TERMINAL = BLOCKS.register(
            "expedition_terminal",
            () -> new ExpeditionTerminalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> 7)));

    public static final DeferredHolder<Block, Block> RECLAIMING_BASIN = BLOCKS.register(
            "reclaiming_basin", () -> new com.expeditions.block.ReclaimingBasinBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f)
                            .sound(SoundType.COPPER).noOcclusion()));

    public static final DeferredHolder<Block, Block> GRAFT_POST = BLOCKS.register("graft_post",
            () -> new net.minecraft.world.level.block.RotatedPillarBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_WHITE).strength(2.0f).sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredHolder<Block, Block> GRAFT_COLLAR = BLOCKS.register("graft_collar",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(2.5f).sound(SoundType.COPPER).noOcclusion()));

    public static final DeferredHolder<Block, net.minecraft.world.level.block.LiquidBlock> RETURN_SAP = BLOCKS.register(
            "return_sap", () -> new net.minecraft.world.level.block.LiquidBlock(ModFluids.SAP.get(),
                    BlockBehaviour.Properties.of().noCollission().strength(100).noLootTable().liquid()));
    public static final DeferredHolder<Block, Block> SAP_JAR = BLOCKS.register("sap_jar",
            () -> new com.expeditions.block.SapVesselBlock(BlockBehaviour.Properties.of().strength(2)
                    .sound(SoundType.WOOD).noOcclusion(), com.expeditions.block.SapVesselBlock.Kind.JAR));
    public static final DeferredHolder<Block, Block> ROOTWICK = BLOCKS.register("rootwick",
            () -> new com.expeditions.block.SapVesselBlock(BlockBehaviour.Properties.of().strength(2)
                    .sound(SoundType.WOOD).noOcclusion(), com.expeditions.block.SapVesselBlock.Kind.WICK));

    /**
     * Route markers placed by the server while a run is active. They are
     * indestructible so a run cannot become unwinnable through breaking them.
     */
    public static final DeferredHolder<Block, ExpeditionCheckpointBlock> EXPEDITION_CHECKPOINT = BLOCKS.register(
            "expedition_checkpoint",
            () -> new ExpeditionCheckpointBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_WHITE)
                    .strength(-1.0f, 3_600_000.0f)
                    .noLootTable()
                    .noOcclusion()
                    .sound(SoundType.WOOD)
                    .lightLevel(state -> 9)));

    public static final DeferredHolder<Block, ExpeditionExitBlock> EXPEDITION_EXIT = BLOCKS.register(
            "expedition_exit",
            () -> new ExpeditionExitBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_WHITE)
                    .strength(-1.0f, 3_600_000.0f)
                    .noLootTable()
                    .noOcclusion()
                    .sound(SoundType.WOOD)
                    .lightLevel(state -> 11)));

    public static final DeferredHolder<Block, com.expeditions.block.RecallTreePartBlock> RECALL_TREE_PART = BLOCKS.register(
            "recall_tree_part", () -> new com.expeditions.block.RecallTreePartBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_WHITE).strength(-1.0f, 3_600_000.0f)
                    .noLootTable().noOcclusion().sound(SoundType.WOOD).lightLevel(state ->
                            com.expeditions.server.RecallTreeLayout.portal(state.getValue(com.expeditions.block.RecallTreePartBlock.PART)) ? 8 : 0)));

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
     * The Big Bad's assimilation layer: a thin membrane that coats sturdy
     * faces of its cell — several at once, sculk-vein style — and renders
     * them grayscale. Harvestable for assimilation essence; each face dies
     * with its own support.
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
     * The Shutter Bloom: a folded, non-sturdy colony source. A bounded ticker
     * seeds supported host terrain and grows the colony to its configured cap.
     * Its old registry id remains stable for worlds, recipes and colony links.
     */
    public static final DeferredHolder<Block, AssimilationHeartBlock> ASSIMILATION_HEART = BLOCKS.register(
            "assimilation_heart",
            () -> new AssimilationHeartBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_YELLOW)
                    .strength(1.5f, 6.0f)
                    .sound(SoundType.WOOD).noOcclusion()
                    .lightLevel(state -> 2+state.getValue(com.expeditions.block.BloomSourceBlock.OPEN))));

    /**
     * Temporary teleport marker spawned above a terminal while a run is open
     * for members. Indestructible, non-solid, never an item.
     */
    public static final DeferredHolder<Block, ExpeditionPortalBlock> EXPEDITION_PORTAL = BLOCKS.register(
            "expedition_portal",
            () -> new ExpeditionPortalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_ORANGE)
                    .noOcclusion()
                    .noCollission()
                    .strength(-1.0f, 3_600_000.0f)
                    .noLootTable()
                    .lightLevel(state -> 11)));

    public static final DeferredHolder<Block,com.expeditions.block.DormantLayerBlock> DORMANT_LAYER=BLOCKS.register("dormant_layer",
            ()->new com.expeditions.block.DormantLayerBlock(BlockBehaviour.Properties.of().strength(.2f).noLootTable().noOcclusion().sound(SoundType.AZALEA_LEAVES).pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));
    public static final DeferredHolder<Block,com.expeditions.block.DormantHeartBlock> DORMANT_HEART=BLOCKS.register("dormant_heart",
            ()->new com.expeditions.block.DormantHeartBlock(BlockBehaviour.Properties.of().strength(1.5f).noLootTable().noOcclusion().sound(SoundType.WOOD)));
    public static final DeferredHolder<Block,com.expeditions.block.ResonanceMachineBlock> STILLNESS_DYNAMO=BLOCKS.register("stillness_dynamo",
            ()->new com.expeditions.block.ResonanceMachineBlock(BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.WOOD)));
    public static final DeferredHolder<Block,com.expeditions.block.ResonanceMachineBlock> ESSENCE_CONDENSER=BLOCKS.register("essence_condenser",
            ()->new com.expeditions.block.ResonanceMachineBlock(BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.WOOD)));

    public static final DeferredHolder<Block,com.expeditions.block.ResonanceMachineBlock> LIVING_HEART_ENGINE=BLOCKS.register("living_heart_engine",
            ()->new com.expeditions.block.ResonanceMachineBlock(BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.COPPER)));
    public static final DeferredHolder<Block,com.expeditions.block.ResonanceMachineBlock> SEVERANCE_GENERATOR=BLOCKS.register("severance_generator",
            ()->new com.expeditions.block.ResonanceMachineBlock(BlockBehaviour.Properties.of().strength(3f).sound(SoundType.COPPER)));

    public static final DeferredHolder<Block,com.expeditions.block.FieldWardBlock> FIELD_WARD=BLOCKS.register("field_ward",
            ()->new com.expeditions.block.FieldWardBlock(BlockBehaviour.Properties.of().strength(1.5f).sound(SoundType.WOOD).noOcclusion().lightLevel(s->s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)?10:0)));

    private ModBlocks() {
    }
}
