package com.expeditions;

import com.expeditions.core.ExpeditionDefinition;
import com.expeditions.core.ExpeditionRoute;
import com.expeditions.core.RouteAnchor;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side configuration for the expedition framework
 * ({@code expeditions-server.toml} in the world's {@code serverconfig} folder).
 *
 * <p>The reference Overworld expedition is configured as a list of X/Z
 * checkpoint columns plus an exit column. Heights are resolved to the terrain
 * surface when a run starts, so the route adapts to the actual world instead of
 * assuming fixed terrain. Pick positions that are safe to walk to on foot and
 * keep them within a few hundred blocks of spawn so the route stays loadable.
 */
public final class ExpeditionsConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<List<? extends String>> CHECKPOINTS = BUILDER
            .comment("Ordered checkpoint positions as \"x,z\" columns in the Overworld.",
                    "Heights are resolved to the surface when an expedition starts.",
                    "All checkpoints must be activated in order before the exit works.")
            .defineList("route.checkpoints",
                    List.of("48,16", "96,-24", "144,48"),
                    () -> "0,0",
                    o -> o instanceof String s && s.matches("-?\\d+,-?\\d+"));

    public static final ModConfigSpec.ConfigValue<String> EXIT = BUILDER
            .comment("Exit position as an \"x,z\" column in the Overworld.",
                    "Height is resolved to the surface when an expedition starts.")
            .define("route.exit", "192,0",
                    o -> o instanceof String s && s.matches("-?\\d+,-?\\d+"));

    public static final ModConfigSpec.IntValue TOKEN_REWARD = BUILDER
            .comment("Guaranteed expedition tokens granted exactly once on completion.")
            .defineInRange("rewards.tokens", 8, 0, 1_000_000);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static final String EXPEDITION_ID = "expeditions:overworld_trial";
    public static final String EXPEDITION_NAME = "Overworld Trial";

    private ExpeditionsConfig() {
    }

    /**
     * Builds the current Overworld expedition definition from config values.
     *
     * @throws IllegalStateException if a configured position cannot be parsed
     */
    public static ExpeditionDefinition overworldDefinition() {
        List<RouteAnchor> checkpoints = CHECKPOINTS.get().stream()
                .map(ExpeditionsConfig::parseAnchor)
                .toList();
        RouteAnchor exit = parseAnchor(EXIT.get());
        return new ExpeditionDefinition(EXPEDITION_ID, EXPEDITION_NAME, 0,
                new ExpeditionRoute(checkpoints, exit), TOKEN_REWARD.get());
    }

    private static RouteAnchor parseAnchor(String value) {
        String[] parts = value.split(",", -1);
        try {
            return new RouteAnchor(Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()));
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "Invalid expedition route position \"" + value
                            + "\" in expeditions-server.toml; expected \"x,z\"", e);
        }
    }
}
