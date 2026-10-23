package com.expeditions.core;

/**
 * The route specification of an expedition: either an explicit
 * {@link ExpeditionRoute} of configured anchors, or {@link RouteGeneration}
 * parameters from which a route is generated relative to the expedition's
 * start position.
 *
 * <p>Sealed so the exhaustive {@code switch} in route resolution stays
 * compile-checked. Restored runs always hold the resolved static form, so
 * persistence never needs to care which kind produced it.
 */
public sealed interface RouteSpec permits ExpeditionRoute, RouteGeneration {
}
