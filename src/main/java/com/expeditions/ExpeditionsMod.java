package com.expeditions;

import com.expeditions.registry.ModBlocks;
import com.expeditions.registry.ModCreativeTabs;
import com.expeditions.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Entry point for the Expedition Framework mod.
 *
 * <p>Expeditions are structured journeys through a dimension with checkpoints,
 * objectives, completion conditions and rewards. This milestone provides the
 * bootstrap, an expedition terminal block and the minimal run lifecycle model.
 */
@Mod(ExpeditionsMod.MOD_ID)
public class ExpeditionsMod {
    public static final String MOD_ID = "expeditions";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ExpeditionsMod(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        LOGGER.info("Expedition Framework initialized");
    }
}
