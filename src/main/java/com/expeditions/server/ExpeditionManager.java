package com.expeditions.server;

import com.expeditions.ExpeditionsConfig;
import com.expeditions.core.CheckpointChallenge;
import com.expeditions.core.EffectSpec;
import com.expeditions.core.ExpeditionDefinition;
import com.expeditions.core.ExpeditionOptions;
import com.expeditions.core.ExpeditionLedger;
import com.expeditions.core.SafeColumnSearch;
import com.expeditions.core.ExpeditionObjective;
import com.expeditions.core.ExpeditionRoute;
import com.expeditions.core.ExpeditionRun;
import com.expeditions.core.ExpeditionStatus;
import com.expeditions.core.ObjectiveKind;
import com.expeditions.core.ObjectiveState;
import com.expeditions.core.PartyScaling;
import com.expeditions.core.RequirementCheck;
import com.expeditions.core.RouteAnchor;
import com.expeditions.core.RouteGeneration;
import com.expeditions.core.RoutePlanner;
import com.expeditions.core.RouteSpec;
import com.expeditions.core.WeatherModifier;
import com.expeditions.core.DefenseState;
import com.expeditions.core.Waypoint;
import com.expeditions.entity.AttackDeviceGoal;
import com.expeditions.item.ExpeditionArtifactItem;
import com.expeditions.registry.ModBlocks;
import com.expeditions.registry.ModDataComponents;
import com.expeditions.registry.ModItems;
import com.expeditions.server.data.ExpeditionDefinitions;
import com.expeditions.server.dimension.DestinationChecks;
import com.expeditions.server.dimension.ExpeditionAdapters;
import com.expeditions.server.dimension.ExpeditionDimensionAdapter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Server-authoritative coordinator for expedition runs.
 *
 * <p>State lives in an {@link ExpeditionLedger} persisted by
 * {@link ExpeditionSavedData}; the manager is stateless. It owns the
 * Minecraft-side work: picking the expedition for the player's dimension,
 * resolving the route through the dimension's adapter, materializing marker
 * blocks and the join portal, moving members between the terminal and the
 * route, and enforcing party membership rules.
 *
 * <p>Run lifecycle with portals: a terminal click resolves the full route at
 * a remote landing spot and spawns a portal above the terminal. Players
 * entering the portal are teleported to the run's entry and join its party
 * (the route may be extended per member, per config). The first checkpoint
 * activation seals the party and removes the portal. Completion or failure
 * removes all markers, strips compasses and teleports members back to the
 * terminal.
 */
public final class ExpeditionManager {
    /** Candidate columns retried when a generated anchor fails validation. */
    /** How many fresh seeds a generated route may roll before the start
     * refuses — a bad random walk shouldn't doom an otherwise safe site.
     * Resolution is cheap (a bounded column scan per anchor), so a generous
     * count keeps rough terrain from refusing a start that a re-roll fixes. */
    private static final int GENERATED_ROUTE_REROLLS = 8;

    private static final int[][] JITTER = {
            {16, 0}, {-16, 0}, {0, 16}, {0, -16},
            {16, 16}, {-16, -16}, {16, -16}, {-16, 16}};

    private static final org.slf4j.Logger LOGGER =
            com.mojang.logging.LogUtils.getLogger();

    private final MinecraftServer server;
    private final ExpeditionLedger ledger;
    private final ExpeditionSavedData data;

    private ExpeditionManager(MinecraftServer server, ExpeditionSavedData data) {
        this.server = server;
        this.ledger = data.ledger();
        this.data = data;
    }

    public static ExpeditionManager get(MinecraftServer server) {
        return new ExpeditionManager(server, server.overworld().getDataStorage()
                .computeIfAbsent(ExpeditionSavedData.FACTORY, ExpeditionSavedData.DATA_NAME));
    }

    public Optional<ExpeditionRun> activeRun(UUID playerId) {
        return ledger.activeRun(playerId);
    }

    public Optional<ExpeditionRun> latestRun(UUID playerId) {
        return ledger.run(playerId);
    }

    public int tokenBalance(UUID playerId) {
        return ledger.tokenBalance(playerId);
    }

    /** All tracked runs (any state), for diagnostics and commands. */
    public Collection<ExpeditionRun> runs() {
        return ledger.runs();
    }

    /** The player's held progression keys, for diagnostics and commands. */
    public Set<String> unlocks(UUID playerId) {
        return ledger.unlocks(playerId);
    }

    /**
     * Starts a run at the terminal the player clicked: resolves the full
     * route at a remote landing spot, places markers, and spawns the join
     * portal above the terminal. Members actually reach the route — and
     * receive their compasses — by stepping into the portal.
     *
     * @throws IllegalStateException if the player already has an active run, no
     *         expedition exists for the dimension, the terminal has no room for
     *         a portal, or no safe route resolves
     */
    public ExpeditionRun startRun(ServerPlayer player, BlockPos terminalPos) {
        return startRun(player, terminalPos, ExpeditionOptions.DEFAULT);
    }

    /**
     * Starts a run with explicit per-run options: an empty expedition id
     * resolves the player's own dimension like before, a non-empty id picks
     * any startable expedition — possibly in another dimension, with the
     * portal staying at the terminal and the route resolving remotely.
     * Checkpoints and time options reshape the run's definition defaults.
     */
    public ExpeditionRun startRun(ServerPlayer player, BlockPos terminalPos,
            ExpeditionOptions options) {
        if (ledger.activeRun(player.getUUID()).isPresent()) {
            throw new IllegalStateException("You already have an active expedition");
        }
        ServerLevel portalLevel = player.serverLevel();
        ExpeditionDefinition definition = options.expeditionId().isBlank()
                ? ExpeditionDefinitions
                        .forDimension(portalLevel.dimension().location().toString())
                        .or(() -> ExpeditionsConfig.definitionFor(portalLevel.dimension()))
                        .orElseThrow(() -> new IllegalStateException(
                                "No expedition is available in this dimension"))
                : listStartable().stream()
                        .filter(d -> d.id().equals(options.expeditionId()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "Unknown or unavailable expedition: " + options.expeditionId()));
        RequirementCheck requirements = ledger.checkRequirements(
                player.getUUID(), definition);
        if (!requirements.isSatisfied()) {
            throw new IllegalStateException(
                    "Expedition locked: " + describeMissing(requirements));
        }
        ServerLevel level = levelFor(definition.dimension());
        if (level == null) {
            throw new IllegalStateException(
                    "The expedition's dimension is not loaded");
        }
        ExpeditionDimensionAdapter adapter = ExpeditionAdapters.forLevel(level)
                .orElseThrow(() -> new IllegalStateException(
                        "This dimension cannot host expeditions"));

        // Everything below is validated before a single block is placed so a
        // failed start leaves the world untouched. The portal stays in the
        // terminal's dimension even when the expedition runs elsewhere.
        BlockPos portalPos = findPortalSpot(portalLevel, terminalPos).orElseThrow(
                () -> new IllegalStateException(
                        "The expedition terminal needs clear space above it"));

        RouteSpec spec = definition.route();
        if (options.checkpoints() > 0 && spec instanceof RouteGeneration g) {
            spec = g.withCheckpoints(options.checkpoints());
        }
        RandomSource rng = player.getRandom();
        ExpeditionRoute route = null;
        BlockPos entryPos = null;
        List<BlockPos> checkpointPositions = null;
        BlockPos exitPos = null;

        // Generated routes get a few fresh rolls: a random walk can clip
        // unresolvable terrain where another seed succeeds. Authored routes
        // are exact — one strict pass, no retries.
        int rolls = spec instanceof RouteGeneration ? GENERATED_ROUTE_REROLLS : 1;
        for (int roll = 0; roll < rolls && route == null; roll++) {
            try {
                ExpeditionRoute candidate;
                int maxAttempts;
                BlockPos candidateEntry;
                switch (spec) {
                    case RouteGeneration g -> {
                        // The portal travels a random distance from the
                        // terminal; the route generates around that landing.
                        double angle = rng.nextDouble() * Math.PI * 2;
                        int distance = g.entryMin()
                                + rng.nextInt(g.entryMax() - g.entryMin() + 1);
                        int baseX = terminalPos.getX()
                                + (int) Math.round(Math.cos(angle) * distance);
                        int baseZ = terminalPos.getZ()
                                + (int) Math.round(Math.sin(angle) * distance);
                        candidate = RoutePlanner.generate(g, baseX, baseZ, rng.nextLong());
                        maxAttempts = g.maxAttempts();
                        candidateEntry = resolveAnchor(adapter, level,
                                new RouteAnchor(baseX, baseZ), maxAttempts);
                    }
                    case ExpeditionRoute r -> {
                        // Authored routes keep exact anchors; the portal lands
                        // members on top of the first checkpoint.
                        candidate = r;
                        maxAttempts = 0;
                        candidateEntry = null;
                    }
                }
                List<BlockPos> positions = new ArrayList<>();
                for (RouteAnchor anchor : candidate.checkpoints()) {
                    positions.add(resolveAnchor(adapter, level, anchor, maxAttempts));
                }
                BlockPos exit = resolveAnchor(adapter, level, candidate.exit(), maxAttempts);
                route = candidate;
                entryPos = candidateEntry != null
                        ? candidateEntry : positions.get(0).above();
                checkpointPositions = positions;
                exitPos = exit;
            } catch (IllegalStateException unresolvable) {
                // Try the next roll.
            }
        }
        if (route == null) {
            throw new IllegalStateException("No safe route could be resolved for this expedition");
        }

        String dimension = definition.dimension();
        String portalDimension = portalLevel.dimension().location().toString();
        for (BlockPos pos : checkpointPositions) {
            level.setBlock(pos, ModBlocks.EXPEDITION_CHECKPOINT.get().defaultBlockState(), 3);
            data.trackMarker(dimension, pos);
        }
        level.setBlock(exitPos, ModBlocks.EXPEDITION_EXIT.get().defaultBlockState(), 3);
        data.trackMarker(dimension, exitPos);
        portalLevel.setBlock(portalPos, ModBlocks.EXPEDITION_PORTAL.get().defaultBlockState(), 3);
        data.trackMarker(portalDimension, portalPos);

        ExpeditionRun run = ExpeditionRun.start(UUID.randomUUID(), player.getUUID(),
                Set.of(player.getUUID()), definition,
                checkpointPositions.stream().map(ExpeditionManager::toWaypoint).toList(),
                toWaypoint(exitPos), toWaypoint(terminalPos), toWaypoint(portalPos),
                toWaypoint(entryPos), level.getGameTime(), options, portalDimension);
        // Charged last — after every validation and world change, when the
        // requirements check above guarantees the balance suffices. A spend
        // that still fails leaves orphan markers for start-up pruning.
        if (definition.costTokens() > 0
                && !ledger.spendTokens(player.getUUID(), definition.costTokens())) {
            throw new IllegalStateException(
                    "Not enough tokens — this expedition costs "
                            + definition.costTokens());
        }
        ledger.startRun(run);
        applyMemberEffects(run, player);
        applyWeather(run, level);
        data.setDirty();
        return run;
    }

    /**
     * What blocks {@code playerId} from starting {@code definition} — the
     * data behind the menu's locked-expedition lore and {@link #startRun}'s
     * refusal. Pure read; the authoritative gate is in {@code startRun}.
     */
    public RequirementCheck missingRequirements(UUID playerId,
            ExpeditionDefinition definition) {
        return ledger.checkRequirements(playerId, definition);
    }

    private static String describeMissing(RequirementCheck check) {
        List<String> parts = new ArrayList<>();
        if (!check.missingKeys().isEmpty()) {
            parts.add("requires " + String.join(", ", check.missingKeys()));
        }
        if (check.missingTokens() > 0) {
            parts.add("costs " + check.missingTokens() + " more tokens");
        }
        return String.join(" and ", parts);
    }

    /**
     * Every expedition that could start right now: datapack definitions plus
     * the built-in config ones they don't shadow, filtered to dimensions that
     * are loaded and have an adapter — the same "can a run actually resolve
     * here" test the start path applies.
     */
    public List<ExpeditionDefinition> listStartable() {
        Map<String, ExpeditionDefinition> startable = new TreeMap<>();
        Set<String> coveredDimensions = new HashSet<>();
        for (ExpeditionDefinition definition : ExpeditionDefinitions.all().values()) {
            if (isStartable(definition)) {
                startable.put(definition.id(), definition);
                coveredDimensions.add(definition.dimension());
            }
        }
        for (ResourceKey<Level> builtin : List.of(Level.OVERWORLD, Level.NETHER)) {
            if (coveredDimensions.contains(builtin.location().toString())) {
                continue;
            }
            ExpeditionsConfig.definitionFor(builtin)
                    .filter(this::isStartable)
                    .ifPresent(d -> startable.put(d.id(), d));
        }
        return List.copyOf(startable.values());
    }

    private boolean isStartable(ExpeditionDefinition definition) {
        ServerLevel level = levelFor(definition.dimension());
        return level != null && ExpeditionAdapters.forLevel(level).isPresent();
    }

    /**
     * Handles a player colliding with a portal block: joins them to the run's
     * party when the portal is still open (extending a generated route per
     * the party config) and teleports them to the run's entry. Portals whose
     * run has sealed or ended remove themselves — the portal is always
     * authoritative-clean.
     */
    public void enterPortal(ServerPlayer player, BlockPos portalPos) {
        ServerLevel level = player.serverLevel();
        String dimension = level.dimension().location().toString();
        Waypoint portalWaypoint = toWaypoint(portalPos);
        Optional<ExpeditionRun> match = ledger.runs().stream()
                .filter(r -> r.portal().equals(portalWaypoint)
                        && r.portalDimension().equals(dimension))
                .findFirst();
        if (match.isEmpty() || !match.get().portalOpen()) {
            // Orphaned or sealed portal: self-heal.
            removeMarkerAt(level, portalPos);
            data.untrackMarker(dimension, portalPos);
            data.setDirty();
            return;
        }
        ExpeditionRun run = match.get();
        if (!run.isMember(player.getUUID())) {
            if (ledger.activeRun(player.getUUID()).isPresent()) {
                player.sendSystemMessage(Component.translatable(
                        "message.expeditions.already_in_run"));
                return;
            }
            run.joinParty(player.getUUID());
            applyMemberEffects(run, player);
            ServerLevel runLevel = levelFor(run.definition().dimension());
            if (runLevel != null) {
                extendRouteForMember(run, runLevel);
            }
            player.sendSystemMessage(Component.translatable(
                    "message.expeditions.party_joined"));
            data.setDirty();
        }
        if (!hasCompass(player)) {
            grantCompass(player, run);
        }
        // The portal may sit in a different dimension than the expedition;
        // teleport into the run's own level.
        ServerLevel destination = levelFor(run.definition().dimension());
        Waypoint entry = run.entry();
        if (destination != null) {
            player.teleportTo(destination, entry.x() + 0.5, entry.y(), entry.z() + 0.5,
                    Set.of(), player.getYRot(), player.getXRot());
        }
    }

    /**
     * Advances the run of the interacting player if {@code pos} is its next
     * checkpoint. The first activation seals the party and removes the
     * portal. Each activation also resolves the checkpoint challenge: the
     * previous challenge's entities are discarded and a fresh roll may
     * spawn a replacement (a horde can never outlive the next checkpoint).
     *
     * @return the updated run
     * @throws IllegalStateException if the activation is invalid
     */
    public ExpeditionRun activateCheckpoint(ServerPlayer player, BlockPos pos) {
        ExpeditionRun run = requireActive(player.getUUID());
        requireRunDimension(run, player.serverLevel());
        for (ExpeditionObjective objective : run.activateCheckpoint(toWaypoint(pos))) {
            player.sendSystemMessage(Component.translatable(
                    "message.expeditions.objective_completed", objective.description()));
        }
        if (run.completedCheckpoints() == 1) {
            closePortal(run);
        }
        // A checkpoint holding the mission cargo issues the run's artifact
        // exactly once; repeat activations never duplicate the item.
        if (!run.artifactIssued()
                && run.definition().artifactObjectiveAt(run.completedCheckpoints())
                        .isPresent()) {
            grantArtifact(player, run);
            run.markArtifactIssued();
            player.sendSystemMessage(Component.translatable(
                    "message.expeditions.artifact_recovered"));
        }
        // A checkpoint holding the defense device deploys it — the only
        // valid activation stage is its declared checkpoint.
        run.definition().defenseObjectiveAt(run.completedCheckpoints())
                .ifPresent(objective -> startSiege(player, run, objective));
        // Guaranteed tokens are banked per checkpoint — they are paid into
        // member balances immediately and survive failure, death and
        // restarts. Completion only tops members up to their full share.
        int portion = run.checkpointTokenPortion(run.completedCheckpoints());
        if (portion > 0) {
            int scaled = PartyScaling.scaledTotal(portion, run.party().size(),
                    ExpeditionsConfig.partyRewardBonusPerExtraMember())
                    * run.options().difficulty().rewardPercent() / 100;
            int share = PartyScaling.memberShare(scaled, run.party().size(),
                    ExpeditionsConfig.partySplitRewards());
            if (share > 0) {
                for (UUID member : run.party()) {
                    ledger.addTokens(member, share);
                    run.secureTokens(member, share);
                }
                player.sendSystemMessage(Component.translatable(
                        "message.expeditions.checkpoint_secured", share));
            }
        }
        retargetPartyCompasses(run);
        // The previous challenge spawned around the checkpoint before this
        // one; its chunks may be unloaded now that the party moved on.
        if (run.completedCheckpoints() >= 2) {
            clearChallengeEntities(run, player.serverLevel(),
                    toPos(run.checkpoints().get(run.completedCheckpoints() - 2)));
        }
        triggerCheckpointChallenge(run, player);
        data.setDirty();
        return run;
    }

    /**
     * Completes the interacting player's run and settles the scaled award to
     * every member present — base tokens plus each completed objective's
     * bonus, times the configured party bonus, then split evenly or paid in
     * full per config — exactly once. Members keep whatever checkpoint
     * credits they already banked; the run only pays the difference between
     * their secured total and their full share (late joiners equalize; the
     * at-risk remainder is what extraction actually pays). Timed objectives
     * are evaluated first; unfinished required objectives refuse the
     * completion entirely. Members are teleported back to the run's
     * terminal.
     *
     * @throws IllegalStateException if the run is missing, finished, has
     *         checkpoints left or unfinished required objectives
     */
    public ExpeditionRun completeRun(ServerPlayer player) {
        ExpeditionRun run = requireActive(player.getUUID());
        requireRunDimension(run, player.serverLevel());
        long elapsed = run.elapsedTicks(player.serverLevel().getGameTime());
        for (ExpeditionObjective objective : run.evaluateTimedObjectives(elapsed)) {
            player.sendSystemMessage(Component.translatable(
                    "message.expeditions.objective_completed", objective.description()));
        }
        // Artifact delivery re-evaluates at every exit click like timed
        // objectives: the item must be in a member's hands at this moment.
        // It is consumed only AFTER complete() succeeds — a refused click
        // (other required objectives missing) must not destroy the cargo.
        HeldArtifact held = findHeldArtifact(run);
        for (ExpeditionObjective objective : run.definition().objectives()) {
            if (objective.kind() == ObjectiveKind.RECOVER_ARTIFACT
                    && run.deliverArtifact(objective.id(), held != null)) {
                player.sendSystemMessage(Component.translatable(
                        "message.expeditions.objective_completed",
                        objective.description()));
            }
        }
        run.complete();
        if (held != null) {
            held.consume();
        }
        if (!run.isRewarded()) {
            int base = run.definition().totalReward(elapsed,
                    run.completedObjectiveIds(), run.options().bonusTimePercent());
            int scaled = PartyScaling.scaledTotal(base, run.party().size(),
                    ExpeditionsConfig.partyRewardBonusPerExtraMember())
                    * run.options().difficulty().rewardPercent() / 100;
            int share = PartyScaling.memberShare(scaled, run.party().size(),
                    ExpeditionsConfig.partySplitRewards());
            for (UUID member : run.party()) {
                // Top up to the full share — checkpoint credits already paid
                // count toward it, so no member is paid twice. A member whose
                // secured total exceeds the final share (party shrank after
                // crediting) keeps the overage; it was earned.
                int topUp = Math.max(0, share - run.securedTokens(member));
                ledger.addTokens(member, topUp);
            }
            run.markRewarded(share);
            grantBonusDrops(run);
        }
        // Unlock keys are party-wide progression — every member earns proof
        // of the completion, online or not, in the same atomic save.
        for (String key : run.definition().grants()) {
            for (UUID member : run.party()) {
                ledger.grantUnlock(member, key);
            }
        }
        if (!run.definition().grants().isEmpty()) {
            for (UUID member : run.party()) {
                ServerPlayer memberPlayer = server.getPlayerList().getPlayer(member);
                if (memberPlayer != null) {
                    memberPlayer.sendSystemMessage(Component.translatable(
                            "message.expeditions.unlocked",
                            String.join(", ", run.definition().grants())));
                }
            }
        }
        endRun(run);
        return run;
    }

    /**
     * Token sink: converts the configured redeem cost into a supply draw from
     * the reward pool of the dimension the player stands in — so supplies
     * redeemed in the Nether look Netherish. The spend lands in the same
     * atomic ledger save as balances always have.
     *
     * @return whether the cost was available and items were granted
     */
    public boolean redeemTokens(ServerPlayer player) {
        int cost = ExpeditionsConfig.redeemTokenCost();
        List<Item> pool = ItemRewards.pool(
                player.serverLevel().dimension().location().toString());
        if (pool.isEmpty()) {
            player.sendSystemMessage(Component.translatable(
                    "message.expeditions.redeem_no_pool"));
            return false;
        }
        if (!ledger.spendTokens(player.getUUID(), cost)) {
            player.sendSystemMessage(Component.translatable(
                    "message.expeditions.redeem_no_tokens", cost));
            return false;
        }
        int budget = (int) (cost * ExpeditionsConfig.bonusDropValuePerToken());
        for (ItemStack drop : ItemRewards.draw(pool, budget, player.getRandom(),
                ExpeditionsConfig.maxBonusDrops())) {
            if (!player.getInventory().add(drop)) {
                player.drop(drop, false);
            }
        }
        data.setDirty();
        player.sendSystemMessage(Component.translatable(
                "message.expeditions.redeemed", cost));
        return true;
    }

    /**
     * Grants bonus item drops to every online member: each member's token
     * share converts to an item-value budget drawn from the run's own
     * dimension's reward pool, so Nether completions pay Nether supplies.
     * Stacks merge into the inventory; leftovers drop at the member's feet.
     * Offline members keep their tokens — item drops don't persist.
     */
    private void grantBonusDrops(ExpeditionRun run) {
        if (!ExpeditionsConfig.bonusDropsEnabled()) {
            return;
        }
        int budget = (int) (run.tokensAwarded() * ExpeditionsConfig.bonusDropValuePerToken());
        if (budget <= 0) {
            return;
        }
        List<Item> pool = ItemRewards.pool(run.definition().dimension());
        for (UUID member : run.party()) {
            ServerPlayer memberPlayer = this.server.getPlayerList().getPlayer(member);
            if (memberPlayer == null) {
                continue;
            }
            for (ItemStack drop : ItemRewards.draw(pool, budget,
                    memberPlayer.getRandom(), ExpeditionsConfig.maxBonusDrops())) {
                if (!memberPlayer.getInventory().add(drop)) {
                    memberPlayer.drop(drop, false);
                }
            }
        }
    }

    /**
     * Removes the interacting player from their run (death or abandon).
     * Remaining members continue; when the last member leaves, the run fails
     * and the party is teleported back.
     */
    public ExpeditionRun failRun(ServerPlayer player) {
        return failRun(player, "ended");
    }

    /**
     * {@link #failRun(ServerPlayer)} recording why the run ended —
     * {@code reasonKey} names a {@code message.expeditions.fail_reason.*}
     * entry shown in the end-of-run summary.
     */
    public ExpeditionRun failRun(ServerPlayer player, String reasonKey) {
        ExpeditionRun run = requireActive(player.getUUID());
        if (run.party().size() > 1) {
            run.leaveParty(player.getUUID());
            removeCompasses(player);
            teleportToTerminal(player, run);
            data.setDirty();
            return run;
        }
        run.fail(reasonKey);
        endRun(run);
        return run;
    }

    /**
     * Recovery policy for a returning or interacting player: resume their
     * active run when its world state is still valid — the dimension exists
     * and every marker the run depends on is present. Otherwise the run is
     * terminated as failed (no reward) and its remaining markers are cleaned
     * up, so a stale run can never strand a player.
     */
    public Optional<ExpeditionRun> resumeOrFail(ServerPlayer player) {
        Optional<ExpeditionRun> active = ledger.activeRun(player.getUUID());
        if (active.isEmpty() || isRunRecoverable(active.get())) {
            return active;
        }
        ExpeditionRun run = active.get();
        run.fail(levelFor(run.definition().dimension()) == null
                ? "dimension_lost" : "site_lost");
        endRun(run);
        return Optional.empty();
    }

    /**
     * Strips expedition compasses from a player who has no active run —
     * covers compasses left over from runs that ended while the member was
     * offline. Called on login.
     */
    public void sweepCompasses(ServerPlayer player) {
        if (ledger.activeRun(player.getUUID()).isEmpty()) {
            removeCompasses(player);
        }
        removeStaleArtifacts(player);
    }

    /**
     * Strips artifacts bound to runs that no longer exist or already ended.
     * A live run's artifact is never touched — even a thief holding another
     * party's cargo keeps their leverage.
     */
    private void removeStaleArtifacts(ServerPlayer player) {
        Set<UUID> liveRuns = new HashSet<>();
        for (ExpeditionRun run : ledger.runs()) {
            if (run.isActive()) {
                liveRuns.add(run.runId());
            }
        }
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (ExpeditionArtifactItem.boundRun(inventory.getItem(i))
                    .filter(bound -> !liveRuns.contains(bound)).isPresent()) {
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
        if (ExpeditionArtifactItem.boundRun(player.containerMenu.getCarried())
                .filter(bound -> !liveRuns.contains(bound)).isPresent()) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
    }

    /**
     * Counts {@code picked} (the stack a player just took from the ground)
     * toward the run's {@link ObjectiveKind#COLLECT_ITEMS} objectives. Any
     * party member's pickup counts toward the shared run objective; pickups
     * by players outside the run affect nothing. Completing an objective is
     * reported to the picking player.
     */
    public void onItemPickup(ServerPlayer player, ItemStack picked) {
        Optional<ExpeditionRun> active = ledger.activeRun(player.getUUID());
        if (active.isEmpty() || picked.isEmpty()) {
            return;
        }
        ExpeditionRun run = active.get();
        if (ExpeditionArtifactItem.isBoundTo(picked, run.runId())) {
            player.sendSystemMessage(Component.translatable(
                    "message.expeditions.artifact_recovered"));
        }
        boolean dirty = false;
        for (ExpeditionObjective objective : run.definition().objectives()) {
            if (objective.kind() != ObjectiveKind.COLLECT_ITEMS
                    || run.objectiveState(objective.id())
                            .map(ObjectiveState::completed).orElse(true)
                    || !matchesObjective(picked, objective)) {
                continue;
            }
            if (run.addObjectiveProgress(objective.id(), picked.getCount())) {
                player.sendSystemMessage(Component.translatable(
                        "message.expeditions.objective_completed", objective.description()));
            }
            dirty = true;
        }
        if (dirty) {
            data.setDirty();
        }
    }

    private static boolean matchesObjective(ItemStack stack, ExpeditionObjective objective) {
        if (objective.item() != null) {
            return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(objective.item()))
                    .map(stack::is).orElse(false);
        }
        return stack.is(TagKey.create(Registries.ITEM,
                ResourceLocation.parse(objective.tag())));
    }

    /**
     * Removes tracked marker blocks no active run still references. Called on
     * server start: a crash can roll back the run record while the marker
     * blocks were already flushed with their chunk, which would otherwise
     * leave indestructible blocks behind forever.
     */
    public static void pruneOrphanMarkers(MinecraftServer server) {
        ExpeditionManager manager = get(server);
        // Snapshot the map: untrackMarker drops empty dimension keys, and a
        // remote expedition's markers span two keys (route dim + portal dim),
        // so mutating during iteration is a real CME risk, not theoretical.
        for (Map.Entry<String, Set<BlockPos>> entry
                : Map.copyOf(manager.data.liveMarkers()).entrySet()) {
            ServerLevel level = manager.levelFor(entry.getKey());
            if (level == null) {
                continue; // unknown dimension: leave the tracking record alone
            }
            for (BlockPos pos : Set.copyOf(entry.getValue())) {
                if (!manager.isReferencedByActiveRun(entry.getKey(), pos)) {
                    removeMarkerAt(level, pos);
                    manager.data.untrackMarker(entry.getKey(), pos);
                }
            }
        }
        manager.data.setDirty();
    }

    private boolean isReferencedByActiveRun(String dimension, BlockPos pos) {
        Waypoint waypoint = toWaypoint(pos);
        return ledger.runs().stream()
                .filter(ExpeditionRun::isActive)
                .anyMatch(r -> (r.definition().dimension().equals(dimension)
                        && (r.checkpoints().contains(waypoint) || r.exit().equals(waypoint)
                                || r.defense().map(d -> d.device().equals(waypoint))
                                        .orElse(false)))
                        || (r.portalDimension().equals(dimension)
                                && r.portal().equals(waypoint)));
    }

    private boolean isRunRecoverable(ExpeditionRun run) {
        ServerLevel level = levelFor(run.definition().dimension());
        if (level == null) {
            return false;
        }
        for (Waypoint w : run.checkpoints()) {
            if (!level.getBlockState(toPos(w)).is(ModBlocks.EXPEDITION_CHECKPOINT.get())) {
                return false;
            }
        }
        return level.getBlockState(toPos(run.exit())).is(ModBlocks.EXPEDITION_EXIT.get());
    }

    /**
     * Shared run-end cleanup: remove the portal if it somehow remains, strip
     * compasses and teleport online members back to the terminal, then remove
     * route markers no other active run needs.
     */
    private void endRun(ExpeditionRun run) {
        String dimension = run.definition().dimension();
        ServerLevel level = levelFor(dimension);
        ServerLevel portalLevel = levelFor(run.portalDimension());
        if (portalLevel != null) {
            BlockPos portalPos = toPos(run.portal());
            if (!isReferencedByActiveRun(run.portalDimension(), portalPos)) {
                removeMarkerAt(portalLevel, portalPos);
                data.untrackMarker(run.portalDimension(), portalPos);
            }
        }
        if (level != null && run.completedCheckpoints() > 0) {
            clearChallengeEntities(run, level,
                    toPos(run.checkpoints().get(run.completedCheckpoints() - 1)));
        }
        // A siege still running when the run ends (abandon, death) stands
        // down with the rest of the markers.
        if (level != null) {
            run.defense().ifPresent(d -> {
                BlockPos devicePos = toPos(d.device());
                if (!isReferencedByActiveRun(dimension, devicePos)) {
                    removeMarkerAt(level, devicePos);
                    data.untrackMarker(dimension, devicePos);
                }
            });
        }
        for (UUID member : run.party()) {
            ServerPlayer memberPlayer = server.getPlayerList().getPlayer(member);
            if (memberPlayer != null) {
                removeMemberEffects(run, memberPlayer);
                removeArtifacts(memberPlayer, run.runId());
                removeCompasses(memberPlayer);
                teleportToTerminal(memberPlayer, run);
            }
        }
        if (level != null && run.definition().modifiers().weather() != WeatherModifier.NONE) {
            // Only clear when no other active run in the dimension still
            // wants the forced weather — parallel runs share the sky.
            boolean weatherStillWanted = ledger.runs().stream()
                    .filter(ExpeditionRun::isActive)
                    .anyMatch(r -> r.definition().dimension().equals(dimension)
                            && r.definition().modifiers().weather() != WeatherModifier.NONE);
            if (!weatherStillWanted) {
                level.setWeatherParameters(6000, 0, false, false);
            }
        }
        cleanupMarkers(run);
        broadcastSummary(run);
        data.setDirty();
    }

    /**
     * The end-of-run summary every online member sees — a structured
     * breakdown separating what was banked, what extraction paid, and what
     * was lost: the legible part of "danger with counterplay". On failure
     * the recorded cause is surfaced alongside the kept credits and the
     * at-risk estimate (what completion would have paid at that moment).
     */
    private void broadcastSummary(ExpeditionRun run) {
        boolean completed = run.status() == ExpeditionStatus.COMPLETED;
        // The at-risk estimate is the share the run would have settled now;
        // when the run's dimension is gone there is nothing to estimate.
        ServerLevel level = levelFor(run.definition().dimension());
        int share;
        if (level != null) {
            int total = run.definition().totalReward(
                    run.elapsedTicks(level.getGameTime()),
                    run.completedObjectiveIds(), run.options().bonusTimePercent());
            share = PartyScaling.memberShare(
                    PartyScaling.scaledTotal(total, run.party().size(),
                            ExpeditionsConfig.partyRewardBonusPerExtraMember())
                            * run.options().difficulty().rewardPercent() / 100,
                    run.party().size(), ExpeditionsConfig.partySplitRewards());
        } else {
            share = 0;
        }
        int objectives = run.definition().objectives().size();
        for (UUID member : run.party()) {
            ServerPlayer memberPlayer = server.getPlayerList().getPlayer(member);
            if (memberPlayer == null) {
                continue;
            }
            int secured = run.securedTokens(member);
            if (completed) {
                memberPlayer.sendSystemMessage(Component.translatable(
                        "message.expeditions.summary_complete",
                        run.definition().displayName()));
                memberPlayer.sendSystemMessage(Component.translatable(
                        "message.expeditions.summary_progress",
                        run.completedCheckpoints(), run.totalCheckpoints(),
                        run.completedObjectiveIds().size(), objectives));
                memberPlayer.sendSystemMessage(Component.translatable(
                        "message.expeditions.summary_rewards", secured,
                        Math.max(0, share - secured), Math.max(secured, share),
                        ledger.tokenBalance(member)));
            } else {
                memberPlayer.sendSystemMessage(Component.translatable(
                        "message.expeditions.summary_failed",
                        run.definition().displayName()));
                memberPlayer.sendSystemMessage(Component.translatable(
                        "message.expeditions.summary_reason",
                        Component.translatable("message.expeditions.fail_reason."
                                + run.failReason().orElse("ended"))));
                memberPlayer.sendSystemMessage(Component.translatable(
                        "message.expeditions.summary_progress",
                        run.completedCheckpoints(), run.totalCheckpoints(),
                        run.completedObjectiveIds().size(), objectives));
                memberPlayer.sendSystemMessage(Component.translatable(
                        "message.expeditions.summary_kept", secured,
                        Math.max(0, share - secured)));
            }
        }
    }

    /** Applies the run's declared member effects (last while the run is active). */
    private void applyMemberEffects(ExpeditionRun run, ServerPlayer player) {
        for (EffectSpec spec : run.definition().modifiers().playerEffects()) {
            effectOf(spec).ifPresent(effect -> player.addEffect(new MobEffectInstance(
                    effect, MobEffectInstance.INFINITE_DURATION, spec.amplifier(),
                    false, false, true)));
        }
    }

    /** Strips the run's declared member effects — other effects are untouched. */
    private void removeMemberEffects(ExpeditionRun run, ServerPlayer player) {
        for (EffectSpec spec : run.definition().modifiers().playerEffects()) {
            effectOf(spec).ifPresent(player::removeEffect);
        }
    }

    /** Forces the run's declared weather onto its dimension (no-op for NONE). */
    private void applyWeather(ExpeditionRun run, ServerLevel level) {
        switch (run.definition().modifiers().weather()) {
            case CLEAR -> level.setWeatherParameters(6000, 0, false, false);
            case RAIN -> level.setWeatherParameters(0, 24000, true, false);
            case THUNDER -> level.setWeatherParameters(0, 24000, true, true);
            default -> {
            }
        }
    }

    /** Effect holder for a spec — datapack-validated, but absent ids degrade to a no-op. */
    private static Optional<Holder.Reference<MobEffect>> effectOf(EffectSpec spec) {
        return BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(spec.effectId()));
    }

    /**
     * Rolls the challenge for the checkpoint just activated. The single kind
     * today — a hunting horde — spawns around the checkpoint with a size that
     * scales with the sealed party; its entities are tracked on the run so a
     * later checkpoint or run end can discard them.
     */
    private void triggerCheckpointChallenge(ExpeditionRun run, ServerPlayer player) {
        if (!ExpeditionsConfig.challengesEnabled()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        CheckpointChallenge kind = CheckpointChallenge.roll(
                ExpeditionsConfig.challengeHordeChance(), level.getRandom().nextFloat());
        if (kind != CheckpointChallenge.HORDE) {
            return;
        }
        spawnHorde(run, level,
                toPos(run.checkpoints().get(run.completedCheckpoints() - 1)));
        if (run.challengeEntities().isEmpty()) {
            return;
        }
        for (UUID member : run.party()) {
            ServerPlayer memberPlayer = server.getPlayerList().getPlayer(member);
            if (memberPlayer != null) {
                memberPlayer.sendSystemMessage(Component.translatable(
                        "message.expeditions.challenge_horde"));
            }
        }
    }

    /**
     * Spawns the configured horde mob in a ring around {@code center} and
     * tracks them on the run. Each mob persists (no despawn) and hunts the
     * nearest online party member; a configured type that is not a
     * {@link Mob} contributes nothing.
     */
    private void spawnHorde(ExpeditionRun run, ServerLevel level, BlockPos center) {
        // One type per activation, rolled from the spawn site's own mob pool
        // (biome spawn table → tag override → config fallback).
        EntityType<?> type = HordePools.pick(level, center,
                run.definition().dimension(), level.getRandom());
        int size = (ExpeditionsConfig.challengeHordeSize()
                + ExpeditionsConfig.challengeHordeSizePerExtraMember()
                        * (run.party().size() - 1))
                * run.options().difficulty().hordePercent() / 100;
        int radius = ExpeditionsConfig.challengeHordeSpawnRadius();
        List<UUID> spawned = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            Mob mob = trySpawnMob(type, level, center, radius);
            if (mob == null) {
                continue;
            }
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()),
                    MobSpawnType.EVENT, null);
            mob.setPersistenceRequired();
            for (EffectSpec spec : run.definition().modifiers().hordeEffects()) {
                effectOf(spec).ifPresent(effect -> mob.addEffect(new MobEffectInstance(
                        effect, MobEffectInstance.INFINITE_DURATION, spec.amplifier(),
                        false, false)));
            }
            mob.setTarget(nearestMember(run, level, mob.blockPosition()));
            if (level.addFreshEntity(mob)) {
                spawned.add(mob.getUUID());
            }
        }
        if (!spawned.isEmpty()) {
            run.trackChallengeEntities(spawned);
        }
    }

    // ---- Defense events ----------------------------------------------------

    /**
     * Deploys the defense device above the just-activated checkpoint and
     * starts the siege: tracked marker, explicit health in the run record,
     * an immediate first wave. The device is a marker — it shares the
     * pickaxe protection and the orphan-pruning safety net.
     */
    private void startSiege(ServerPlayer player, ExpeditionRun run,
            ExpeditionObjective objective) {
        ServerLevel level = player.serverLevel();
        // The device goes above the just-activated checkpoint, but never over
        // another marker: two runs in the same dimension can resolve
        // overlapping positions, and clobbering a shared checkpoint would
        // silently destroy another run. Take the first replaceable spot in a
        // bounded column; if none exists the siege does not start.
        BlockPos devicePos = toPos(run.checkpoints()
                .get(run.completedCheckpoints() - 1)).above();
        for (int i = 0; i < 4 && !level.getBlockState(devicePos).canBeReplaced(); i++) {
            devicePos = devicePos.above();
        }
        if (!level.getBlockState(devicePos).canBeReplaced()) {
            LOGGER.warn("No room for a defense device above {}; siege for run {} not started",
                    devicePos, run.runId());
            return;
        }
        level.setBlock(devicePos, ModBlocks.EXPEDITION_DEVICE.get().defaultBlockState(), 3);
        data.trackMarker(level.dimension().location().toString(), devicePos);
        long now = level.getGameTime();
        run.startDefense(objective.id(), toWaypoint(devicePos),
                ExpeditionsConfig.defenseDeviceHp(), now,
                objective.timeLimitTicks(),
                ExpeditionsConfig.defenseWaveSeconds() * 20L);
        spawnSiegeWave(run, level, devicePos);
        for (UUID member : run.party()) {
            ServerPlayer memberPlayer = server.getPlayerList().getPlayer(member);
            if (memberPlayer != null) {
                memberPlayer.sendSystemMessage(Component.translatable(
                        "message.expeditions.defense_started",
                        objective.timeLimitTicks() / 20));
            }
        }
    }

    /**
     * Device damage entry point — siege mobs and player punches both land
     * here so the run record stays the single authority on device health.
     * No-op for positions no active run defends.
     */
    public void damageDeviceAt(ServerLevel level, BlockPos pos, int amount) {
        defenseRunAt(level, pos).ifPresent(run -> {
            // Capture the sieged objective before destruction clears the state.
            String objectiveId = run.defense().map(DefenseState::objectiveId).orElse(null);
            if (run.damageDevice(amount)) {
                onDeviceDestroyed(run, level, pos, objectiveId);
            }
            data.setDirty();
        });
    }

    /** Whether {@code pos} is the live device of an active run's siege. */
    public boolean isDefenseDeviceAt(ServerLevel level, BlockPos pos) {
        return defenseRunAt(level, pos).isPresent();
    }

    /** Status of the device at {@code pos} for player feedback, if live. */
    public Optional<DeviceStatus> defenseStatusAt(ServerLevel level, BlockPos pos) {
        return defenseRunAt(level, pos)
                .flatMap(ExpeditionRun::defense)
                .map(d -> new DeviceStatus(d.hp(), d.maxHp(),
                        Math.max(0, d.endTick() - level.getGameTime())));
    }

    /** Remaining siege time for {@link ExpeditionDeviceBlock} feedback. */
    public record DeviceStatus(int hp, int maxHp, long remainingTicks) {
    }

    private Optional<ExpeditionRun> defenseRunAt(ServerLevel level, BlockPos pos) {
        String dimension = level.dimension().location().toString();
        Waypoint waypoint = toWaypoint(pos);
        return ledger.runs().stream()
                .filter(ExpeditionRun::isActive)
                .filter(r -> r.definition().dimension().equals(dimension))
                .filter(r -> r.defense().map(d -> d.device().equals(waypoint)).orElse(false))
                .findFirst();
    }

    /**
     * Exactly-once destruction: the block comes down, the siege is called
     * off, the objective is already marked failed by the domain. A required
     * objective fails the whole run by mission rule; an optional one just
     * never pays.
     */
    private void onDeviceDestroyed(ExpeditionRun run, ServerLevel level, BlockPos devicePos,
            String objectiveId) {
        removeMarkerAt(level, devicePos);
        data.untrackMarker(level.dimension().location().toString(), devicePos);
        clearChallengeEntities(run, level, devicePos);
        ExpeditionObjective objective = run.definition().objectives().stream()
                .filter(o -> o.id().equals(objectiveId))
                .findFirst().orElse(null);
        for (UUID member : run.party()) {
            ServerPlayer memberPlayer = server.getPlayerList().getPlayer(member);
            if (memberPlayer != null) {
                memberPlayer.sendSystemMessage(Component.translatable(
                        "message.expeditions.device_destroyed"));
            }
        }
        if (objective != null && objective.required() && run.isActive()) {
            run.fail("device_destroyed");
            endRun(run);
        }
    }

    /**
     * Advances every active siege once a second: expired timers complete
     * their objective and stand the device down, due waves spawn, and a
     * missing device block counts as destruction (fail-safe).
     */
    public void tickDefenses() {
        for (ExpeditionRun run : ledger.runs()) {
            if (!run.isActive() || run.defense().isEmpty()) {
                continue;
            }
            ServerLevel level = levelFor(run.definition().dimension());
            if (level == null) {
                continue;
            }
            DefenseState defense = run.defense().get();
            BlockPos devicePos = toPos(defense.device());
            long now = level.getGameTime();
            if (!level.getBlockState(devicePos).is(ModBlocks.EXPEDITION_DEVICE.get())) {
                // The block vanished without manager involvement (creative
                // break, /fill): the device is destroyed by definition.
                String objectiveId = defense.objectiveId();
                if (run.damageDevice(Integer.MAX_VALUE)) {
                    onDeviceDestroyed(run, level, devicePos, objectiveId);
                }
                continue;
            }
            Optional<ExpeditionObjective> survived = run.completeDefenseIfExpired(now);
            if (survived.isPresent()) {
                standDownDevice(run, level, devicePos);
                for (UUID member : run.party()) {
                    ServerPlayer memberPlayer = server.getPlayerList().getPlayer(member);
                    if (memberPlayer != null) {
                        memberPlayer.sendSystemMessage(Component.translatable(
                                "message.expeditions.objective_completed",
                                survived.get().description()));
                    }
                }
                continue;
            }
            if (run.defenseWaveDue(now)) {
                spawnSiegeWave(run, level, devicePos);
                run.defenseWaveSpawned(now + ExpeditionsConfig.defenseWaveSeconds() * 20L);
            }
            data.setDirty();
        }
    }

    /** Removes a survived device and calls off the remaining siege mobs. */
    private void standDownDevice(ExpeditionRun run, ServerLevel level, BlockPos devicePos) {
        removeMarkerAt(level, devicePos);
        data.untrackMarker(level.dimension().location().toString(), devicePos);
        clearChallengeEntities(run, level, devicePos);
    }

    /**
     * Spawns one wave of siege mobs around the device, bounded by the
     * configured cap of live challenge entities and wave size — the type
     * rolls from the device's own natural mob pool like any horde. Each
     * mob pathfinds to the device and chips it; players intercept them.
     */
    private void spawnSiegeWave(ExpeditionRun run, ServerLevel level, BlockPos devicePos) {
        int alive = 0;
        for (UUID entityId : run.challengeEntities()) {
            if (level.getEntity(entityId) != null) {
                alive++;
            }
        }
        int room = ExpeditionsConfig.defenseMobCap() - alive;
        int count = Math.min(room, ExpeditionsConfig.defenseWaveSize());
        if (count <= 0) {
            return;
        }
        EntityType<?> type = HordePools.pick(level, devicePos,
                run.definition().dimension(), level.getRandom());
        int radius = ExpeditionsConfig.challengeHordeSpawnRadius();
        List<UUID> spawned = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Mob mob = trySpawnMob(type, level, devicePos, radius);
            if (mob == null) {
                continue;
            }
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()),
                    MobSpawnType.EVENT, null);
            mob.setPersistenceRequired();
            for (EffectSpec spec : run.definition().modifiers().hordeEffects()) {
                effectOf(spec).ifPresent(effect -> mob.addEffect(new MobEffectInstance(
                        effect, MobEffectInstance.INFINITE_DURATION, spec.amplifier(),
                        false, false)));
            }
            mob.goalSelector.addGoal(1, new AttackDeviceGoal(mob, devicePos,
                    ExpeditionsConfig.defenseMobDamage(),
                    ExpeditionsConfig.defenseMobAttackTicks()));
            if (level.addFreshEntity(mob)) {
                spawned.add(mob.getUUID());
            }
        }
        if (!spawned.isEmpty()) {
            run.trackChallengeEntities(spawned);
        }
    }

    /** How far above an event's elevation a spawn column is scanned. */
    private static final int MOB_SPAWN_SCAN_UP = 16;
    /** How far below — deep enough to reach floors in enclosed dimensions. */
    private static final int MOB_SPAWN_SCAN_DOWN = 40;

    /** Random columns a single mob gets to find a home in, like vanilla's
     * per-mob spawn retries — a lone ledge or a tall mob shouldn't empty
     * the whole event. */
    private static final int MOB_SPAWN_ATTEMPTS = 8;

    /**
     * Places a positioned, collision-free {@link Mob} of {@code type} in a
     * random column of the ring around {@code center}, or null when no
     * attempted column works. Not yet finalized or added to the level.
     */
    private static Mob trySpawnMob(EntityType<?> type, ServerLevel level,
            BlockPos center, int radius) {
        for (int attempt = 0; attempt < MOB_SPAWN_ATTEMPTS; attempt++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            int distance = 2 + level.getRandom().nextInt(radius - 1);
            BlockPos column = center.offset(
                    (int) Math.round(Math.cos(angle) * distance), 0,
                    (int) Math.round(Math.sin(angle) * distance));
            // Force-load the spawn column (bounded by the configured radius)
            // before reading its blocks — an ungenerated column reports
            // stale state — and so the mob is actually live.
            level.getChunk(column);
            Optional<BlockPos> surface = findMobSpawn(level, column,
                    center.getY());
            if (surface.isEmpty() || !(type.create(level) instanceof Mob mob)) {
                continue;
            }
            mob.moveTo(surface.get().getX() + 0.5, surface.get().getY(),
                    surface.get().getZ() + 0.5,
                    level.getRandom().nextFloat() * 360f, 0f);
            // The standing contract guarantees two free blocks; a mob taller
            // or wider than that would suffocate in the wall — keep looking.
            if (level.noCollision(mob)) {
                return mob;
            }
        }
        return null;
    }

    /**
     * A standable spot in {@code column} near {@code nearY}: the same
     * "two free blocks above a sturdy, non-hazardous floor" contract route
     * resolution uses, scanned in a bounded window around the event's own
     * elevation. The heightmap is deliberately not consulted — in enclosed
     * dimensions it names the ceiling (a horde on the nether roof is a
     * horde that doesn't exist), and in open ones the window keeps spawns
     * near the players' own level instead of a far ridge top.
     */
    private static Optional<BlockPos> findMobSpawn(ServerLevel level,
            BlockPos column, int nearY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(
                column.getX(), 0, column.getZ());
        int top = Math.min(level.getMaxBuildHeight() - 1,
                nearY + MOB_SPAWN_SCAN_UP);
        int bottom = Math.max(level.getMinBuildHeight() + 1,
                nearY - MOB_SPAWN_SCAN_DOWN);
        OptionalInt y = SafeColumnSearch.findStandingY(
                yy -> DestinationChecks.isFree(level, pos.setY(yy)),
                yy -> DestinationChecks.isSturdyFloor(level, pos.setY(yy)),
                yy -> DestinationChecks.isHazard(level, pos.setY(yy)),
                top, bottom);
        return y.isPresent()
                ? Optional.of(pos.setY(y.getAsInt()).immutable())
                : Optional.empty();
    }

    /** The online, living party member nearest to {@code pos}, if any. */
    private ServerPlayer nearestMember(ExpeditionRun run, ServerLevel level, BlockPos pos) {
        ServerPlayer nearest = null;
        double best = Double.MAX_VALUE;
        for (UUID member : run.party()) {
            ServerPlayer memberPlayer = server.getPlayerList().getPlayer(member);
            if (memberPlayer == null || !memberPlayer.isAlive()
                    || memberPlayer.level() != level) {
                continue;
            }
            double distance = memberPlayer.blockPosition().distSqr(pos);
            if (distance < best) {
                best = distance;
                nearest = memberPlayer;
            }
        }
        return nearest;
    }

    /**
     * Discards every reachable entity a previous challenge spawned. The
     * horde may sit in chunks that unloaded while the party walked on — the
     * area it spawned in is force-loaded first (bounded by the spawn
     * radius) so entities serialized with their chunk are restored to the
     * section storage. Entities still unreachable keep their tracking entry
     * so the periodic sweep can retry — a horde can never leak unseen.
     */
    private void clearChallengeEntities(ExpeditionRun run, ServerLevel level, BlockPos center) {
        if (run.challengeEntities().isEmpty()) {
            return;
        }
        int radius = ExpeditionsConfig.challengeHordeSpawnRadius();
        for (int cx = (center.getX() - radius) >> 4;
                cx <= (center.getX() + radius) >> 4; cx++) {
            for (int cz = (center.getZ() - radius) >> 4;
                    cz <= (center.getZ() + radius) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        AABB box = columnBox(level, center, radius + 1);
        for (UUID entityId : List.copyOf(run.challengeEntities())) {
            Entity entity = findInSections(level, box, entityId);
            if (entity != null) {
                entity.discard();
                run.untrackChallengeEntity(entityId);
            }
        }
    }

    /**
     * Search volume for a challenge's entities: the spawn radius in X/Z and
     * the full column height — horde members are placed at each column's
     * surface, which can sit far below an elevated checkpoint or device.
     */
    private static AABB columnBox(ServerLevel level, BlockPos center, int radius) {
        return new AABB(center.getX() - radius, level.getMinBuildHeight(),
                center.getZ() - radius, center.getX() + radius + 1,
                level.getMaxBuildHeight(), center.getZ() + radius + 1);
    }

    /**
     * Opportunistic retry for tracked challenge entities of ENDED runs that
     * were pending when their cleanup ran (e.g. their chunk had unloaded):
     * discards any that have since returned to the section storage. Active
     * runs are skipped — their tracked entities are the live horde, not
     * stragglers. Called on a slow server tick — cheap, and ended runs keep
     * unresolved ids tracked so no horde can outlive its expedition unseen.
     */
    public void sweepChallengeEntities() {
        boolean removed = false;
        for (ExpeditionRun run : ledger.runs()) {
            if (run.isActive() || run.challengeEntities().isEmpty()
                    || run.completedCheckpoints() == 0) {
                continue;
            }
            ServerLevel level = levelFor(run.definition().dimension());
            if (level == null) {
                continue;
            }
            // The current horde spawned around the last activated checkpoint.
            Waypoint center = run.checkpoints().get(
                    Math.min(run.completedCheckpoints(), run.totalCheckpoints()) - 1);
            AABB box = columnBox(level, toPos(center),
                    ExpeditionsConfig.challengeHordeSpawnRadius() + 1);
            for (UUID entityId : List.copyOf(run.challengeEntities())) {
                Entity entity = findInSections(level, box, entityId);
                if (entity != null) {
                    entity.discard();
                    run.untrackChallengeEntity(entityId);
                    removed = true;
                }
            }
        }
        if (removed) {
            data.setDirty();
        }
    }

    /**
     * Finds an entity by UUID through the entity section storage — which,
     * unlike {@code level.getEntity(uuid)} (the visible entity lookup), also
     * sees entities parked in sections whose chunk is not entity-ticking.
     */
    private static Entity findInSections(ServerLevel level, AABB box, UUID entityId) {
        List<Entity> found = new ArrayList<>();
        level.getEntities().get(box, entity -> {
            if (found.isEmpty() && entity.getUUID().equals(entityId)) {
                found.add(entity);
            }
        });
        return found.isEmpty() ? null : found.get(0);
    }

    /** Removes the portal block when the first checkpoint seals the party. */
    private void closePortal(ExpeditionRun run) {
        String dimension = run.definition().dimension();
        ServerLevel level = levelFor(dimension);
        if (level != null) {
            BlockPos portalPos = toPos(run.portal());
            removeMarkerAt(level, portalPos);
            data.untrackMarker(dimension, portalPos);
        }
    }

    /**
     * Party difficulty scaling: each member beyond the first extends a
     * generated route by the configured number of checkpoint legs, continued
     * from the last checkpoint and placed through the adapter like the base
     * route. Legs that cannot resolve are skipped — a member never fails to
     * join over terrain.
     */
    private void extendRouteForMember(ExpeditionRun run, ServerLevel level) {
        if (!(run.definition().route() instanceof RouteGeneration spec)) {
            return;
        }
        int extra = ExpeditionsConfig.partyCheckpointsPerExtraMember();
        if (extra <= 0) {
            return;
        }
        ExpeditionDimensionAdapter adapter = ExpeditionAdapters.forLevel(level).orElse(null);
        if (adapter == null) {
            return;
        }
        Waypoint last = run.checkpoints().get(run.checkpoints().size() - 1);
        List<RouteAnchor> anchors = RoutePlanner.generateLegs(spec,
                last.x(), last.z(), extra, level.getRandom().nextLong());
        List<Waypoint> added = new ArrayList<>();
        for (RouteAnchor anchor : anchors) {
            try {
                BlockPos pos = resolveAnchor(adapter, level, anchor, spec.maxAttempts());
                level.setBlock(pos, ModBlocks.EXPEDITION_CHECKPOINT.get().defaultBlockState(), 3);
                data.trackMarker(run.definition().dimension(), pos);
                added.add(toWaypoint(pos));
            } catch (IllegalStateException e) {
                break; // keep the party and whatever legs resolved
            }
        }
        if (!added.isEmpty()) {
            run.extendRoute(added);
        }
    }

    private void teleportToTerminal(ServerPlayer player, ExpeditionRun run) {
        if (!player.isAlive()) {
            return;
        }
        // The terminal lives in the portal's home dimension — which differs
        // from the run's dimension for remote expeditions.
        ServerLevel level = levelFor(run.portalDimension());
        if (level == null) {
            return;
        }
        Waypoint terminal = run.terminal();
        player.teleportTo(level, terminal.x() + 0.5, terminal.y() + 1.0, terminal.z() + 0.5,
                Set.of(), player.getYRot(), player.getXRot());
    }

    private void cleanupMarkers(ExpeditionRun run) {
        // Markers can coincide with another active run's — only remove the
        // positions no still-active run references. The ending run is already
        // terminal, so the active-run check never counts it back in.
        String dimension = run.definition().dimension();
        ServerLevel level = levelFor(dimension);
        if (level == null) {
            return;
        }
        for (Waypoint w : run.checkpoints()) {
            if (!isReferencedByActiveRun(dimension, toPos(w))) {
                removeMarker(level, w,
                        ModBlocks.EXPEDITION_CHECKPOINT.get().defaultBlockState());
            }
        }
        if (!isReferencedByActiveRun(dimension, toPos(run.exit()))) {
            removeMarker(level, run.exit(),
                    ModBlocks.EXPEDITION_EXIT.get().defaultBlockState());
        }
    }

    /**
     * Gives the player an expedition compass pointed at the run's current
     * target, dropping it at their feet if the inventory is full.
     */
    private static void grantCompass(ServerPlayer player, ExpeditionRun run) {
        ItemStack stack = new ItemStack(ModItems.EXPEDITION_COMPASS.get());
        retarget(stack, runDimension(run), nextTarget(run));
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /** Points every expedition compass of every online member at the next target. */
    private void retargetPartyCompasses(ExpeditionRun run) {
        Waypoint target = nextTarget(run);
        ResourceKey<Level> dimension = runDimension(run);
        for (UUID member : run.party()) {
            ServerPlayer memberPlayer = server.getPlayerList().getPlayer(member);
            if (memberPlayer != null) {
                forEachCompass(memberPlayer, stack -> retarget(stack, dimension, target));
            }
        }
    }

    /**
     * Issues the run's artifact into the activator's inventory (or at their
     * feet), bound by the {@code run_id} component — the physical proof that
     * only this run's exit can consume.
     */
    private static void grantArtifact(ServerPlayer player, ExpeditionRun run) {
        ItemStack stack = new ItemStack(ModItems.EXPEDITION_ARTIFACT.get());
        stack.set(ModDataComponents.RUN_ID.get(), run.runId().toString());
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /** An artifact stack currently held by an online party member. */
    private record HeldArtifact(ServerPlayer holder, int slot) {
        /** Removes the artifact — only called after the run actually completes. */
        void consume() {
            if (slot >= 0) {
                holder.getInventory().setItem(slot, ItemStack.EMPTY);
            } else {
                holder.containerMenu.setCarried(ItemStack.EMPTY);
            }
        }
    }

    /**
     * The first artifact stack bound to {@code run} found in any online
     * member's inventory (cursor-held counts; slot -1). Offline members
     * cannot hand over items — documented extraction rule.
     */
    private HeldArtifact findHeldArtifact(ExpeditionRun run) {
        for (UUID member : run.party()) {
            ServerPlayer memberPlayer = server.getPlayerList().getPlayer(member);
            if (memberPlayer == null) {
                continue;
            }
            var inventory = memberPlayer.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                if (ExpeditionArtifactItem.isBoundTo(inventory.getItem(i), run.runId())) {
                    return new HeldArtifact(memberPlayer, i);
                }
            }
            if (ExpeditionArtifactItem.isBoundTo(
                    memberPlayer.containerMenu.getCarried(), run.runId())) {
                return new HeldArtifact(memberPlayer, -1);
            }
        }
        return null;
    }

    /** Strips this run's remaining artifacts from a member's inventory. */
    private static void removeArtifacts(ServerPlayer player, UUID runId) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (ExpeditionArtifactItem.isBoundTo(inventory.getItem(i), runId)) {
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
        if (ExpeditionArtifactItem.isBoundTo(player.containerMenu.getCarried(), runId)) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
    }

    /** Removes every expedition compass the player is carrying. */
    private static void removeCompasses(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (isCompass(inventory.getItem(i))) {
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
        ItemStack carried = player.containerMenu.getCarried();
        if (isCompass(carried)) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
    }

    private static boolean hasCompass(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (isCompass(inventory.getItem(i))) {
                return true;
            }
        }
        return false;
    }

    private static void forEachCompass(ServerPlayer player,
            java.util.function.Consumer<ItemStack> action) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (isCompass(stack)) {
                action.accept(stack);
            }
        }
        ItemStack carried = player.containerMenu.getCarried();
        if (isCompass(carried)) {
            action.accept(carried);
        }
    }

    private static boolean isCompass(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModItems.EXPEDITION_COMPASS.get());
    }

    /** The position the compass should point at: next checkpoint, else the exit. */
    private static Waypoint nextTarget(ExpeditionRun run) {
        return run.nextCheckpoint().orElse(run.exit());
    }

    private static void retarget(ItemStack stack, ResourceKey<Level> dimension, Waypoint target) {
        stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(
                Optional.of(GlobalPos.of(dimension, toPos(target))), false));
    }

    /**
     * The run's own dimension key — NOT the portal's. A remote expedition's
     * compass must target a {@link GlobalPos} in the dimension the holder
     * will stand in, or the vanilla needle-validity check spins it.
     */
    private static ResourceKey<Level> runDimension(ExpeditionRun run) {
        return ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse(run.definition().dimension()));
    }

    /**
     * Finds a free spot for the portal above the terminal: the block directly
     * above, or one higher when that space is occupied.
     */
    private static Optional<BlockPos> findPortalSpot(ServerLevel level, BlockPos terminalPos) {
        for (BlockPos pos : List.of(terminalPos.above(), terminalPos.above(2))) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.canBeReplaced()) {
                return Optional.of(pos);
            }
        }
        return Optional.empty();
    }

    private ServerLevel levelFor(String dimensionId) {
        ResourceLocation location = ResourceLocation.tryParse(dimensionId);
        if (location == null) {
            return null;
        }
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, location));
    }

    private void removeMarker(ServerLevel level, Waypoint w, BlockState expected) {
        removeMarkerAt(level, toPos(w));
        data.untrackMarker(level.dimension().location().toString(), toPos(w));
    }

    /** Removes the block at {@code pos} if it is one of our marker blocks. */
    private static void removeMarkerAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(ModBlocks.EXPEDITION_CHECKPOINT.get())
                || state.is(ModBlocks.EXPEDITION_EXIT.get())
                || state.is(ModBlocks.EXPEDITION_PORTAL.get())
                || state.is(ModBlocks.EXPEDITION_DEVICE.get())) {
            level.removeBlock(pos, false);
        }
    }

    /**
     * Resolves one route anchor. On failure up to {@code maxAttempts} nearby
     * candidate columns (a fixed ±16 offset ring) are tried before giving up.
     * Sampling is bounded: at most 1 + maxAttempts columns and one chunk load
     * each; an anchor that still finds nothing safe fails the run start
     * cleanly.
     */
    private static BlockPos resolveAnchor(ExpeditionDimensionAdapter adapter, ServerLevel level,
            RouteAnchor anchor, int maxAttempts) {
        Optional<BlockPos> resolved = adapter.resolveAnchor(level, anchor);
        for (int i = 0; resolved.isEmpty() && i < maxAttempts && i < JITTER.length; i++) {
            resolved = adapter.resolveAnchor(level,
                    new RouteAnchor(anchor.x() + JITTER[i][0], anchor.z() + JITTER[i][1]));
        }
        return resolved.orElseThrow(() -> new IllegalStateException(
                "No safe expedition route could be resolved in this dimension"));
    }

    private ExpeditionRun requireActive(UUID playerId) {
        return ledger.activeRun(playerId)
                .orElseThrow(() -> new IllegalStateException("No active expedition"));
    }

    /**
     * Cross-dimensional interactions are rejected: a run's markers only exist
     * in its own dimension, so progress from elsewhere can never be valid.
     */
    private static void requireRunDimension(ExpeditionRun run, ServerLevel level) {
        if (!run.definition().dimension()
                .equals(level.dimension().location().toString())) {
            throw new IllegalStateException("Run " + run.runId() + " belongs to "
                    + run.definition().dimension());
        }
    }

    public static Waypoint toWaypoint(BlockPos pos) {
        return new Waypoint(pos.getX(), pos.getY(), pos.getZ());
    }

    public static BlockPos toPos(Waypoint w) {
        return new BlockPos(w.x(), w.y(), w.z());
    }
}
