package com.expeditions.core;

/**
 * A resolved position in the world. Pure value type so the domain does not
 * depend on Minecraft's {@code BlockPos}.
 */
public record Waypoint(int x, int y, int z) {
}
