package com.expeditions.registry;

import com.expeditions.ExpeditionsMod;
import com.expeditions.item.ExpeditionArtifactItem;
import com.expeditions.item.ExpeditionCompassItem;
import com.expeditions.item.ExpeditionFieldJournalItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Item registrations, paired with {@link ModBlocks} where a block item applies.
 */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ExpeditionsMod.MOD_ID);
    public static final DeferredItem<com.expeditions.item.FieldAssayKitItem> FIELD_ASSAY_KIT = ITEMS.register(
            "field_assay_kit", () -> new com.expeditions.item.FieldAssayKitItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> DISRUPTION_CHARM = ITEMS.register(
            "disruption_charm",()->new com.expeditions.item.DisruptionCharmItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> RETURN_KNOT = ITEMS.registerSimpleItem("return_knot");
    public static final DeferredItem<Item> RETURN_SAP_BUCKET = ITEMS.register("return_sap_bucket",
            () -> new net.minecraft.world.item.BucketItem(ModFluids.SAP.get(),
                    new Item.Properties().craftRemainder(net.minecraft.world.item.Items.BUCKET).stacksTo(1)));
    public static final DeferredItem<BlockItem> SAP_JAR = ITEMS.registerSimpleBlockItem("sap_jar", ModBlocks.SAP_JAR);
    public static final DeferredItem<BlockItem> ROOTWICK = ITEMS.registerSimpleBlockItem("rootwick", ModBlocks.ROOTWICK);
    public static final DeferredItem<BlockItem> GRAFT_POST = ITEMS.registerSimpleBlockItem("graft_post", ModBlocks.GRAFT_POST);
    public static final DeferredItem<BlockItem> GRAFT_COLLAR = ITEMS.registerSimpleBlockItem("graft_collar", ModBlocks.GRAFT_COLLAR);

    public static final DeferredItem<BlockItem> RECLAIMING_BASIN = ITEMS.registerSimpleBlockItem(
            "reclaiming_basin", ModBlocks.RECLAIMING_BASIN);

    public static final DeferredItem<BlockItem> EXPEDITION_TERMINAL_ITEM = ITEMS.registerSimpleBlockItem(
            "expedition_terminal", ModBlocks.EXPEDITION_TERMINAL);

    public static final DeferredItem<BlockItem> EXPEDITION_CHECKPOINT_ITEM = ITEMS.registerSimpleBlockItem(
            "expedition_checkpoint", ModBlocks.EXPEDITION_CHECKPOINT);

    public static final DeferredItem<BlockItem> EXPEDITION_EXIT_ITEM = ITEMS.registerSimpleBlockItem(
            "expedition_exit", ModBlocks.EXPEDITION_EXIT);

    public static final DeferredItem<ExpeditionCompassItem> EXPEDITION_COMPASS = ITEMS.register(
            "expedition_compass",
            () -> new ExpeditionCompassItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<ExpeditionArtifactItem> EXPEDITION_ARTIFACT = ITEMS.register(
            "expedition_artifact",
            () -> new ExpeditionArtifactItem(new Item.Properties().stacksTo(1)));

    /** Places the assimilation layer; {@code clickedFace} becomes its FACING. */
    public static final DeferredItem<BlockItem> ASSIMILATION_LAYER_ITEM = ITEMS.registerSimpleBlockItem(
            "assimilation_layer", ModBlocks.ASSIMILATION_LAYER);

    /** Places the microbiome's growth organ — the colony's killable core. */
    public static final DeferredItem<BlockItem> ASSIMILATION_HEART_ITEM = ITEMS.registerSimpleBlockItem(
            "assimilation_heart", ModBlocks.ASSIMILATION_HEART);

    /** Harvest drop from the assimilation layer — progression placeholder. */
    public static final DeferredItem<Item> ASSIMILATION_ESSENCE = ITEMS.registerSimpleItem(
            "assimilation_essence");

    /**
     * The crafted progression artifact: carries an {@code attunement_key}
     * component; used at the terminal it grants that key permanently and
     * is never consumed — the artifact itself is the proof of attunement.
     */
    public static final DeferredItem<Item> ATTUNEMENT_SIGIL = ITEMS.register(
            "attunement_sigil", () -> new com.expeditions.item.AttunementSigilItem(new Item.Properties().stacksTo(1)));

    /**
     * The survey tool: opens as a book carrying a live report — expedition
     * status, dimension campaign, Bloom pressure; sneak-use spends health on
     * a blood sample that unlocks the infection assay page.
     */
    public static final DeferredItem<ExpeditionFieldJournalItem> EXPEDITION_FIELD_JOURNAL = ITEMS.register(
            "expedition_field_journal",
            () -> new ExpeditionFieldJournalItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> DORMANT_FLAKE=ITEMS.registerSimpleItem("dormant_flake");
    public static final DeferredItem<BlockItem> DORMANT_LAYER_ITEM=ITEMS.registerSimpleBlockItem("dormant_layer",ModBlocks.DORMANT_LAYER);
    public static final DeferredItem<BlockItem> DORMANT_HEART_ITEM=ITEMS.registerSimpleBlockItem("dormant_heart",ModBlocks.DORMANT_HEART);
    public static final DeferredItem<BlockItem> STILLNESS_DYNAMO_ITEM=ITEMS.registerSimpleBlockItem("stillness_dynamo",ModBlocks.STILLNESS_DYNAMO);
    public static final DeferredItem<BlockItem> ESSENCE_CONDENSER_ITEM=ITEMS.registerSimpleBlockItem("essence_condenser",ModBlocks.ESSENCE_CONDENSER);

    public static final DeferredItem<Item> DORMANT_KNOT=ITEMS.registerSimpleItem("dormant_knot");
    public static final DeferredItem<com.expeditions.item.SeveranceCoreItem> SEVERANCE_CORE=ITEMS.register("severance_core",()->new com.expeditions.item.SeveranceCoreItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<BlockItem> LIVING_HEART_ENGINE_ITEM=ITEMS.registerSimpleBlockItem("living_heart_engine",ModBlocks.LIVING_HEART_ENGINE);
    public static final DeferredItem<BlockItem> SEVERANCE_GENERATOR_ITEM=ITEMS.registerSimpleBlockItem("severance_generator",ModBlocks.SEVERANCE_GENERATOR);

    public static final DeferredItem<com.expeditions.item.ResonanceCellItem> RESONANCE_CELL=ITEMS.register("resonance_cell",()->new com.expeditions.item.ResonanceCellItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<com.expeditions.item.SurveyLensItem> SURVEY_LENS=ITEMS.register("survey_lens",()->new com.expeditions.item.SurveyLensItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<BlockItem> FIELD_WARD_ITEM=ITEMS.registerSimpleBlockItem("field_ward",ModBlocks.FIELD_WARD);

    private ModItems() {
    }
}
