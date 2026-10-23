package com.expeditions.item;

import net.minecraft.world.item.Item;

/**
 * Expedition compass: granted to each player when a run starts and removed
 * when the run ends. The needle target is driven entirely server-side via the
 * {@code LODESTONE_TRACKER} data component — the server retargets it to the
 * next checkpoint, then the exit, as the run progresses. It carries no
 * run logic of its own.
 */
public class ExpeditionCompassItem extends Item {
    public ExpeditionCompassItem(Properties properties) {
        super(properties);
    }
}
