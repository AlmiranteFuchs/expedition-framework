package com.expeditions;

import com.expeditions.core.ExpeditionDefinition;
import com.expeditions.core.ExpeditionRoute;
import com.expeditions.core.RouteAnchor;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side configuration for the expedition framework
 * ({@code expeditions-server.toml} in the world's {@code serverconfig} folder).
 *
 * <p>One expedition is configured per dimension section. Routes are X/Z
 * columns whose heights are resolved by the dimension's adapter when a run
 * starts, so the same config adapts to the actual terrain. Pick positions that
 * are safe to walk to on foot and keep them within a few hundred blocks of
 * spawn so the route stays loadable.
 */
public final class ExpeditionsConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.ConfigValue<List<? extends String>> OVERWORLD_CHECKPOINTS =
            BUILDER
                    .comment("Ordered checkpoint columns \"x,z\" for the Overworld expedition.",
                            "Heights are resolved to the surface when a run starts.")
                    .defineList("expeditions.overworld.checkpoints",
                            List.of("48,16", "96,-24", "144,48"),
                            () -> "0,0",
                            ExpeditionsConfig::isAnchor);

    private static final ModConfigSpec.ConfigValue<String> OVERWORLD_EXIT = BUILDER
            .comment("Exit column \"x,z\" for the Overworld expedition.")
            .define("expeditions.overworld.exit", "192,0", ExpeditionsConfig::isAnchor);

    private static final ModConfigSpec.IntValue OVERWORLD_TOKENS = BUILDER
            .comment("Guaranteed tokens for completing the Overworld expedition.")
            .defineInRange("expeditions.overworld.tokens", 8, 0, 1_000_000);

    private static final ModConfigSpec.IntValue OVERWORLD_BONUS_TOKENS = BUILDER
            .comment("Extra tokens for finishing within the bonus time. 0 disables the bonus.")
            .defineInRange("expeditions.overworld.bonusTokens", 4, 0, 1_000_000);

    private static final ModConfigSpec.IntValue OVERWORLD_BONUS_SECONDS = BUILDER
            .comment("Bonus time budget in seconds.")
            .defineInRange("expeditions.overworld.bonusTimeSeconds", 300, 0, 86_400);

    private static final ModConfigSpec.BooleanValue NETHER_ENABLED = BUILDER
            .comment("Whether the Nether expedition is available. Disable rather than",
                    "clearing the route so defaults can be restored later.")
            .define("expeditions.nether.enabled", true);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> NETHER_CHECKPOINTS =
            BUILDER
                    .comment("Ordered checkpoint columns \"x,z\" for the Nether expedition.",
                            "Positions are resolved by scanning down for a safe,",
                            "hazard-free standing spot below the bedrock ceiling.")
                    .defineList("expeditions.nether.checkpoints",
                            List.of("24,24", "48,-16", "72,40"),
                            () -> "0,0",
                            ExpeditionsConfig::isAnchor);

    private static final ModConfigSpec.ConfigValue<String> NETHER_EXIT = BUILDER
            .comment("Exit column \"x,z\" for the Nether expedition.")
            .define("expeditions.nether.exit", "96,0", ExpeditionsConfig::isAnchor);

    private static final ModConfigSpec.IntValue NETHER_TOKENS = BUILDER
            .comment("Guaranteed tokens for completing the Nether expedition.")
            .defineInRange("expeditions.nether.tokens", 16, 0, 1_000_000);

    private static final ModConfigSpec.IntValue NETHER_BONUS_TOKENS = BUILDER
            .comment("Extra tokens for finishing within the bonus time. 0 disables the bonus.")
            .defineInRange("expeditions.nether.bonusTokens", 8, 0, 1_000_000);

    private static final ModConfigSpec.IntValue NETHER_BONUS_SECONDS = BUILDER
            .comment("Bonus time budget in seconds.")
            .defineInRange("expeditions.nether.bonusTimeSeconds", 240, 0, 86_400);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static final int TICKS_PER_SECOND = 20;

    private ExpeditionsConfig() {
    }

    /**
     * The expedition configured for {@code dimension}, if any. Dimensions with
     * no configured (or disabled) expedition have none — starting fails with a
     * clear message before any world changes happen.
     */
    public static Optional<ExpeditionDefinition> definitionFor(ResourceKey<Level> dimension) {
        if (dimension.equals(Level.OVERWORLD)) {
            return definition("expeditions:overworld_trial", "Overworld Trial",
                    dimension, OVERWORLD_CHECKPOINTS.get(), OVERWORLD_EXIT.get(),
                    OVERWORLD_TOKENS.get(), OVERWORLD_BONUS_TOKENS.get(),
                    OVERWORLD_BONUS_SECONDS.get());
        }
        if (dimension.equals(Level.NETHER) && NETHER_ENABLED.get()) {
            return definition("expeditions:nether_trial", "Nether Trial",
                    dimension, NETHER_CHECKPOINTS.get(), NETHER_EXIT.get(),
                    NETHER_TOKENS.get(), NETHER_BONUS_TOKENS.get(),
                    NETHER_BONUS_SECONDS.get());
        }
        return Optional.empty();
    }

    private static Optional<ExpeditionDefinition> definition(String id, String name,
            ResourceKey<Level> dimension, List<? extends String> checkpoints, String exit,
            int tokens, int bonusTokens, int bonusSeconds) {
        if (checkpoints.isEmpty()) {
            return Optional.empty();
        }
        List<RouteAnchor> anchors = checkpoints.stream()
                .map(ExpeditionsConfig::parseAnchor).toList();
        return Optional.of(new ExpeditionDefinition(id, name, 0,
                dimension.location().toString(),
                new ExpeditionRoute(anchors, parseAnchor(exit)),
                tokens, bonusTokens, bonusSeconds * TICKS_PER_SECOND));
    }

    private static boolean isAnchor(Object o) {
        return o instanceof String s && s.matches("-?\\d+,-?\\d+");
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
