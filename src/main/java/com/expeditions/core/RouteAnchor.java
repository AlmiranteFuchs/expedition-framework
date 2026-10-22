package com.expeditions.core;

/**
 * A configured route position as an X/Z column. The Y coordinate is not part of
 * the configuration: at run start the server resolves it to the terrain surface,
 * so routes survive different world heights instead of assuming fixed terrain.
 */
public record RouteAnchor(int x, int z) {
}
