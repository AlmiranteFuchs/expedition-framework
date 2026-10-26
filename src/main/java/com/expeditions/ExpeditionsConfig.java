package com.expeditions;

import com.expeditions.core.ExpeditionDefinition;
import com.expeditions.core.ExpeditionRoute;
import com.expeditions.core.RouteAnchor;
import com.expeditions.core.RouteGeneration;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side configuration for the expedition framework
 * ({@code expeditions-server.toml} in the world's {@code serverconfig} folder).
 *
 * <p>One expedition is configured per dimension section. The Overworld
 * expedition generates its route dynamically around the player at run start
 * (see {@code expeditions.overworld.generation.*}); setting
 * {@code expeditions.overworld.generate} to false falls back to the authored
 * {@code checkpoints}/{@code exit} columns below. Authored routes are X/Z
 * columns whose heights are resolved by the dimension's adapter when a run
 * starts. Pick positions that are safe to walk to on foot and keep them
 * within a few hundred blocks of spawn so the route stays loadable.
 */
public final class ExpeditionsConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue OVERWORLD_GENERATE = BUILDER
            .comment("Generate the Overworld route dynamically per run instead of",
                    "using the authored checkpoints/exit columns below.")
            .define("expeditions.overworld.generate", true);

    private static final ModConfigSpec.IntValue OVERWORLD_GENERATION_CHECKPOINTS = BUILDER
            .comment("How many checkpoints a generated Overworld route contains.")
            .defineInRange("expeditions.overworld.generation.checkpoints", 3, 1, 16);

    private static final ModConfigSpec.IntValue OVERWORLD_GENERATION_MIN_SPACING = BUILDER
            .comment("Minimum distance between successive generated waypoints.")
            .defineInRange("expeditions.overworld.generation.minSpacing", 48, 1, 4096);

    private static final ModConfigSpec.IntValue OVERWORLD_GENERATION_MAX_SPACING = BUILDER
            .comment("Maximum distance between successive generated waypoints.")
            .defineInRange("expeditions.overworld.generation.maxSpacing", 96, 1, 4096);

    private static final ModConfigSpec.IntValue OVERWORLD_GENERATION_EXIT_MIN = BUILDER
            .comment("Minimum distance from the last checkpoint to the generated exit.")
            .defineInRange("expeditions.overworld.generation.exitMin", 96, 1, 4096);

    private static final ModConfigSpec.IntValue OVERWORLD_GENERATION_EXIT_MAX = BUILDER
            .comment("Maximum distance from the last checkpoint to the generated exit.")
            .defineInRange("expeditions.overworld.generation.exitMax", 160, 1, 4096);

    private static final ModConfigSpec.IntValue OVERWORLD_GENERATION_MAX_ATTEMPTS = BUILDER
            .comment("How many nearby candidate columns a generated anchor may",
                    "retry when its position fails safety validation. Bounds the",
                    "chunk lookups spent on one anchor; 0 makes anchors strict.")
            .defineInRange("expeditions.overworld.generation.maxAttempts", 4, 0, 8);

    private static final ModConfigSpec.IntValue OVERWORLD_GENERATION_ENTRY_MIN = BUILDER
            .comment("Minimum distance the expedition portal travels from the",
                    "terminal to the remote landing spot the route generates around.")
            .defineInRange("expeditions.overworld.generation.entryMin", 128, 1, 4096);

    private static final ModConfigSpec.IntValue OVERWORLD_GENERATION_ENTRY_MAX = BUILDER
            .comment("Maximum distance the expedition portal travels from the terminal.")
            .defineInRange("expeditions.overworld.generation.entryMax", 256, 1, 4096);

    private static final ModConfigSpec.IntValue PARTY_EXTRA_CHECKPOINTS = BUILDER
            .comment("Checkpoints appended to a generated route for every member",
                    "who joins through the portal beyond the first — party",
                    "difficulty scaling. 0 keeps routes fixed at their base length.")
            .defineInRange("expeditions.party.checkpointsPerExtraMember", 1, 0, 4);

    private static final ModConfigSpec.DoubleValue PARTY_REWARD_BONUS = BUILDER
            .comment("Fractional bonus added to the completion reward pool for",
                    "each member beyond the first (0.5 = +50% per extra member).")
            .defineInRange("expeditions.party.rewardBonusPerExtraMember", 0.5, 0.0, 4.0);

    private static final ModConfigSpec.BooleanValue PARTY_SPLIT_REWARDS = BUILDER
            .comment("Whether the scaled reward pool is split evenly among the",
                    "members present at completion (true) or paid in full to",
                    "each member (false).")
            .define("expeditions.party.splitRewards", true);

    private static final ModConfigSpec.BooleanValue CHALLENGES_ENABLED = BUILDER
            .comment("Whether checkpoint activations roll challenges.")
            .define("expeditions.challenges.enabled", true);

    private static final ModConfigSpec.DoubleValue HORDE_CHANCE = BUILDER
            .comment("Chance that an activated checkpoint spawns a hostile",
                    "horde (1.0 = every checkpoint, 0.0 = never). With a",
                    "single challenge kind this is the whole probability mass.")
            .defineInRange("expeditions.challenges.hordeChance", 1.0, 0.0, 1.0);

    private static final ModConfigSpec.IntValue HORDE_SIZE = BUILDER
            .comment("Base number of hostile mobs a checkpoint horde spawns.")
            .defineInRange("expeditions.challenges.hordeSize", 4, 1, 32);

    private static final ModConfigSpec.IntValue HORDE_SIZE_PER_MEMBER = BUILDER
            .comment("Extra horde mobs per party member beyond the first.")
            .defineInRange("expeditions.challenges.hordeSizePerExtraMember", 2, 0, 16);

    private static final ModConfigSpec.IntValue HORDE_SPAWN_RADIUS = BUILDER
            .comment("Maximum distance from the activated checkpoint horde",
                    "mobs may spawn at (they always hunt the nearest member).")
            .defineInRange("expeditions.challenges.hordeSpawnRadius", 8, 2, 32);

    private static final ModConfigSpec.ConfigValue<String> HORDE_MOB = BUILDER
            .comment("Entity type horde mobs spawn as (entity registry id).")
            .define("expeditions.challenges.hordeMob", "minecraft:zombie");

    private static final ModConfigSpec.IntValue DEFENSE_DEVICE_HP = BUILDER
            .comment("Health of the defense device a defend_device objective",
                    "deploys (points of siege damage it survives).")
            .defineInRange("expeditions.challenges.defenseDeviceHp", 20, 1, 1000);

    private static final ModConfigSpec.IntValue DEFENSE_MOB_CAP = BUILDER
            .comment("Maximum live siege mobs per defense event — new waves",
                    "pause while the cap is reached.")
            .defineInRange("expeditions.challenges.defenseMobCap", 6, 1, 64);

    private static final ModConfigSpec.IntValue DEFENSE_WAVE_SECONDS = BUILDER
            .comment("Seconds between siege waves during a defense event.")
            .defineInRange("expeditions.challenges.defenseWaveSeconds", 10, 1, 300);

    private static final ModConfigSpec.IntValue DEFENSE_WAVE_SIZE = BUILDER
            .comment("Mobs each siege wave tries to spawn, capped by",
                    "defenseMobCap headroom.")
            .defineInRange("expeditions.challenges.defenseWaveSize", 3, 1, 32);

    private static final ModConfigSpec.IntValue DEFENSE_MOB_DAMAGE = BUILDER
            .comment("Device health each siege mob hit removes.")
            .defineInRange("expeditions.challenges.defenseMobDamage", 1, 1, 100);

    private static final ModConfigSpec.IntValue DEFENSE_MOB_ATTACK_TICKS = BUILDER
            .comment("Ticks between a siege mob's hits once in reach of the device.")
            .defineInRange("expeditions.challenges.defenseMobAttackTicks", 20, 1, 200);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> OVERWORLD_CHECKPOINTS =
            BUILDER
                    .comment("Ordered checkpoint columns \"x,z\" for the Overworld expedition.",
                            "Only used when \"expeditions.overworld.generate\" is false.",
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

    private static final ModConfigSpec.BooleanValue NETHER_GENERATE = BUILDER
            .comment("Generate the Nether route dynamically per run instead of",
                    "using the authored checkpoints/exit columns below.",
                    "Distances stay shorter than the Overworld defaults — the",
                    "safe scan window below the bedrock ceiling is tight.")
            .define("expeditions.nether.generate", true);

    private static final ModConfigSpec.IntValue NETHER_GENERATION_CHECKPOINTS = BUILDER
            .comment("How many checkpoints a generated Nether route contains.")
            .defineInRange("expeditions.nether.generation.checkpoints", 3, 1, 16);

    private static final ModConfigSpec.IntValue NETHER_GENERATION_MIN_SPACING = BUILDER
            .comment("Minimum distance between successive generated waypoints.")
            .defineInRange("expeditions.nether.generation.minSpacing", 32, 1, 4096);

    private static final ModConfigSpec.IntValue NETHER_GENERATION_MAX_SPACING = BUILDER
            .comment("Maximum distance between successive generated waypoints.")
            .defineInRange("expeditions.nether.generation.maxSpacing", 64, 1, 4096);

    private static final ModConfigSpec.IntValue NETHER_GENERATION_EXIT_MIN = BUILDER
            .comment("Minimum distance from the last checkpoint to the generated exit.")
            .defineInRange("expeditions.nether.generation.exitMin", 64, 1, 4096);

    private static final ModConfigSpec.IntValue NETHER_GENERATION_EXIT_MAX = BUILDER
            .comment("Maximum distance from the last checkpoint to the generated exit.")
            .defineInRange("expeditions.nether.generation.exitMax", 128, 1, 4096);

    private static final ModConfigSpec.IntValue NETHER_GENERATION_MAX_ATTEMPTS = BUILDER
            .comment("How many nearby candidate columns a generated anchor may",
                    "retry when its position fails safety validation.")
            .defineInRange("expeditions.nether.generation.maxAttempts", 4, 0, 8);

    private static final ModConfigSpec.IntValue NETHER_GENERATION_ENTRY_MIN = BUILDER
            .comment("Minimum distance the expedition portal travels from the",
                    "terminal to the remote landing spot the route generates around.")
            .defineInRange("expeditions.nether.generation.entryMin", 64, 1, 4096);

    private static final ModConfigSpec.IntValue NETHER_GENERATION_ENTRY_MAX = BUILDER
            .comment("Maximum distance the expedition portal travels from the terminal.")
            .defineInRange("expeditions.nether.generation.entryMax", 128, 1, 4096);

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

    private static final ModConfigSpec.BooleanValue BONUS_DROPS = BUILDER
            .comment("Grant item drops alongside tokens on expedition completion.")
            .define("expeditions.rewards.bonusDrops", true);

    private static final ModConfigSpec.DoubleValue DROP_VALUE_PER_TOKEN = BUILDER
            .comment("Item-value budget granted per awarded token (ItemValuator score units).")
            .defineInRange("expeditions.rewards.dropValuePerToken", 1.0, 0.0, 100.0);

    private static final ModConfigSpec.IntValue MAX_BONUS_DROPS = BUILDER
            .comment("Maximum stacks a single bonus-drop draw may grant.")
            .defineInRange("expeditions.rewards.maxBonusDrops", 8, 0, 64);

    private static final ModConfigSpec.IntValue REDEEM_COST = BUILDER
            .comment("Token cost of /expeditions redeem (one supply draw).")
            .defineInRange("expeditions.rewards.redeemCost", 10, 1, 1_000_000);

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
            if (OVERWORLD_GENERATE.get()) {
                return Optional.of(new ExpeditionDefinition(
                        "expeditions:overworld_trial", "Overworld Trial", 0,
                        dimension.location().toString(),
                        new RouteGeneration(
                                OVERWORLD_GENERATION_CHECKPOINTS.get(),
                                OVERWORLD_GENERATION_MIN_SPACING.get(),
                                OVERWORLD_GENERATION_MAX_SPACING.get(),
                                OVERWORLD_GENERATION_EXIT_MIN.get(),
                                OVERWORLD_GENERATION_EXIT_MAX.get(),
                                OVERWORLD_GENERATION_MAX_ATTEMPTS.get(),
                                OVERWORLD_GENERATION_ENTRY_MIN.get(),
                                OVERWORLD_GENERATION_ENTRY_MAX.get()),
                        OVERWORLD_TOKENS.get(), OVERWORLD_BONUS_TOKENS.get(),
                        OVERWORLD_BONUS_SECONDS.get() * TICKS_PER_SECOND));
            }
            return definition("expeditions:overworld_trial", "Overworld Trial",
                    dimension, OVERWORLD_CHECKPOINTS.get(), OVERWORLD_EXIT.get(),
                    OVERWORLD_TOKENS.get(), OVERWORLD_BONUS_TOKENS.get(),
                    OVERWORLD_BONUS_SECONDS.get());
        }
        if (dimension.equals(Level.NETHER) && NETHER_ENABLED.get()) {
            if (NETHER_GENERATE.get()) {
                return Optional.of(new ExpeditionDefinition(
                        "expeditions:nether_trial", "Nether Trial", 0,
                        dimension.location().toString(),
                        new RouteGeneration(
                                NETHER_GENERATION_CHECKPOINTS.get(),
                                NETHER_GENERATION_MIN_SPACING.get(),
                                NETHER_GENERATION_MAX_SPACING.get(),
                                NETHER_GENERATION_EXIT_MIN.get(),
                                NETHER_GENERATION_EXIT_MAX.get(),
                                NETHER_GENERATION_MAX_ATTEMPTS.get(),
                                NETHER_GENERATION_ENTRY_MIN.get(),
                                NETHER_GENERATION_ENTRY_MAX.get()),
                        NETHER_TOKENS.get(), NETHER_BONUS_TOKENS.get(),
                        NETHER_BONUS_SECONDS.get() * TICKS_PER_SECOND));
            }
            return definition("expeditions:nether_trial", "Nether Trial",
                    dimension, NETHER_CHECKPOINTS.get(), NETHER_EXIT.get(),
                    NETHER_TOKENS.get(), NETHER_BONUS_TOKENS.get(),
                    NETHER_BONUS_SECONDS.get());
        }
        return Optional.empty();
    }

    /** Extra checkpoints appended per member joining through the portal. */
    public static int partyCheckpointsPerExtraMember() {
        return PARTY_EXTRA_CHECKPOINTS.get();
    }

    /** Fractional reward bonus per member beyond the first. */
    public static double partyRewardBonusPerExtraMember() {
        return PARTY_REWARD_BONUS.get();
    }

    /** Whether the scaled reward pool is split evenly among members. */
    public static boolean partySplitRewards() {
        return PARTY_SPLIT_REWARDS.get();
    }

    /** Whether checkpoint activations roll challenges. */
    public static boolean challengesEnabled() {
        return CHALLENGES_ENABLED.get();
    }

    /** Chance an activated checkpoint spawns a horde. */
    public static double challengeHordeChance() {
        return HORDE_CHANCE.get();
    }

    /** Base horde mob count. */
    public static int challengeHordeSize() {
        return HORDE_SIZE.get();
    }

    /** Extra horde mobs per party member beyond the first. */
    public static int challengeHordeSizePerExtraMember() {
        return HORDE_SIZE_PER_MEMBER.get();
    }

    /** Maximum spawn distance of horde mobs from the checkpoint. */
    public static int challengeHordeSpawnRadius() {
        return HORDE_SPAWN_RADIUS.get();
    }

    /** Entity registry id horde mobs spawn as. */
    public static String challengeHordeMob() {
        return HORDE_MOB.get();
    }

    /** Health of a deployed defense device. */
    public static int defenseDeviceHp() {
        return DEFENSE_DEVICE_HP.get();
    }

    /** Maximum live siege mobs per defense event. */
    public static int defenseMobCap() {
        return DEFENSE_MOB_CAP.get();
    }

    /** Seconds between siege waves. */
    public static int defenseWaveSeconds() {
        return DEFENSE_WAVE_SECONDS.get();
    }

    /** Mobs each siege wave tries to spawn. */
    public static int defenseWaveSize() {
        return DEFENSE_WAVE_SIZE.get();
    }

    /** Device health removed per siege mob hit. */
    public static int defenseMobDamage() {
        return DEFENSE_MOB_DAMAGE.get();
    }

    /** Ticks between a siege mob's hits in reach of the device. */
    public static int defenseMobAttackTicks() {
        return DEFENSE_MOB_ATTACK_TICKS.get();
    }

    /** Whether completion grants bonus item drops alongside tokens. */
    public static boolean bonusDropsEnabled() {
        return BONUS_DROPS.get();
    }

    /** Item-value budget granted per awarded token. */
    public static double bonusDropValuePerToken() {
        return DROP_VALUE_PER_TOKEN.get();
    }

    /** Maximum stacks a single draw may grant. */
    public static int maxBonusDrops() {
        return MAX_BONUS_DROPS.get();
    }

    /** Token cost of one redeemed supply draw. */
    public static int redeemTokenCost() {
        return REDEEM_COST.get();
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
