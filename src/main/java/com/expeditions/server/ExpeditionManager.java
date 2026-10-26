package com.expeditions.server;

import com.expeditions.ExpeditionsConfig;
import com.expeditions.core.CheckpointChallenge;
import com.expeditions.core.ExpeditionDefinition;
import com.expeditions.core.ExpeditionOptions;
import com.expeditions.core.ExpeditionLedger;
import com.expeditions.core.ExpeditionObjective;
import com.expeditions.core.ExpeditionRoute;
import com.expeditions.core.ExpeditionRun;
import com.expeditions.core.ObjectiveKind;
import com.expeditions.core.ObjectiveState;
import com.expeditions.core.PartyScaling;
import com.expeditions.core.RouteAnchor;
import com.expeditions.core.RouteGeneration;
import com.expeditions.core.RoutePlanner;
import com.expeditions.core.RouteSpec;
import com.expeditions.core.Waypoint;
import com.expeditions.registry.ModBlocks;
import com.expeditions.registry.ModItems;
import com.expeditions.server.data.ExpeditionDefinitions;
import com.expeditions.server.dimension.ExpeditionAdapters;
import com.expeditions.server.dimension.ExpeditionDimensionAdapter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
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
        ledger.startRun(run);
        data.setDirty();
        return run;
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
            ServerLevel runLevel = levelFor(run.definition().dimension());
            if (runLevel != null) {
                extendRouteForMember(run, runLevel);
            }
            player.sendSystemMessage(Component.translatable(
                    "message.expeditions.party_joined"));
            data.setDirty();
        }
        if (!hasCompass(player)) {
            grantCompass(player, run, level.dimension());
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
     * Completes the interacting player's run and grants the scaled reward to
     * every member present — base tokens plus each completed objective's
     * bonus, times the configured party bonus, then split evenly or paid in
     * full per config — exactly once. Timed objectives are evaluated first;
     * unfinished required objectives refuse the completion entirely. Members
     * are teleported back to the run's terminal.
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
        run.complete();
        if (!run.isRewarded()) {
            int base = run.definition().totalReward(elapsed,
                    run.completedObjectiveIds(), run.options().bonusTimePercent());
            int scaled = PartyScaling.scaledTotal(base, run.party().size(),
                    ExpeditionsConfig.partyRewardBonusPerExtraMember())
                    * run.options().difficulty().rewardPercent() / 100;
            int share = PartyScaling.memberShare(scaled, run.party().size(),
                    ExpeditionsConfig.partySplitRewards());
            for (UUID member : run.party()) {
                ledger.addTokens(member, share);
            }
            run.markRewarded(share);
            grantBonusDrops(run);
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
        ExpeditionRun run = requireActive(player.getUUID());
        if (run.party().size() > 1) {
            run.leaveParty(player.getUUID());
            removeCompasses(player);
            teleportToTerminal(player, run);
            data.setDirty();
            return run;
        }
        run.fail();
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
        run.fail();
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
                        && (r.checkpoints().contains(waypoint) || r.exit().equals(waypoint)))
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
            removeMarkerAt(portalLevel, portalPos);
            data.untrackMarker(run.portalDimension(), portalPos);
        }
        if (level != null && run.completedCheckpoints() > 0) {
            clearChallengeEntities(run, level,
                    toPos(run.checkpoints().get(run.completedCheckpoints() - 1)));
        }
        for (UUID member : run.party()) {
            ServerPlayer memberPlayer = server.getPlayerList().getPlayer(member);
            if (memberPlayer != null) {
                removeCompasses(memberPlayer);
                teleportToTerminal(memberPlayer, run);
            }
        }
        cleanupMarkers(run);
        data.setDirty();
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
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(
                ResourceLocation.parse(ExpeditionsConfig.challengeHordeMob()));
        int size = (ExpeditionsConfig.challengeHordeSize()
                + ExpeditionsConfig.challengeHordeSizePerExtraMember()
                        * (run.party().size() - 1))
                * run.options().difficulty().hordePercent() / 100;
        int radius = ExpeditionsConfig.challengeHordeSpawnRadius();
        List<UUID> spawned = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            int distance = 2 + level.getRandom().nextInt(radius - 1);
            BlockPos column = center.offset(
                    (int) Math.round(Math.cos(angle) * distance), 0,
                    (int) Math.round(Math.sin(angle) * distance));
            // Force-load the spawn column (bounded by the configured radius)
            // before reading the heightmap — an ungenerated column reports a
            // stale height — and so the mob is actually live.
            level.getChunk(column);
            BlockPos surface = level.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            if (!(type.create(level) instanceof Mob mob)) {
                continue;
            }
            mob.moveTo(surface.getX() + 0.5, surface.getY(), surface.getZ() + 0.5,
                    level.getRandom().nextFloat() * 360f, 0f);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(surface),
                    MobSpawnType.EVENT, null);
            mob.setPersistenceRequired();
            mob.setTarget(nearestMember(run, level, surface));
            level.addFreshEntity(mob);
            spawned.add(mob.getUUID());
        }
        if (!spawned.isEmpty()) {
            run.trackChallengeEntities(spawned);
        }
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
        AABB box = new AABB(center).inflate(radius + 1);
        for (UUID entityId : List.copyOf(run.challengeEntities())) {
            Entity entity = findInSections(level, box, entityId);
            if (entity != null) {
                entity.discard();
                run.untrackChallengeEntity(entityId);
            }
        }
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
            AABB box = new AABB(toPos(center))
                    .inflate(ExpeditionsConfig.challengeHordeSpawnRadius() + 1);
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
        // Markers are shared per dimension; only remove them once no other
        // active run in that dimension still needs them.
        String dimension = run.definition().dimension();
        if (ledger.anyOtherActiveRunInDimension(run.playerId(), dimension)) {
            return;
        }
        ServerLevel level = levelFor(dimension);
        if (level == null) {
            return;
        }
        for (Waypoint w : run.checkpoints()) {
            removeMarker(level, w, ModBlocks.EXPEDITION_CHECKPOINT.get().defaultBlockState());
        }
        removeMarker(level, run.exit(), ModBlocks.EXPEDITION_EXIT.get().defaultBlockState());
    }

    /**
     * Gives the player an expedition compass pointed at the run's current
     * target, dropping it at their feet if the inventory is full.
     */
    private static void grantCompass(ServerPlayer player, ExpeditionRun run,
            ResourceKey<Level> dimension) {
        ItemStack stack = new ItemStack(ModItems.EXPEDITION_COMPASS.get());
        retarget(stack, dimension, nextTarget(run));
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /** Points every expedition compass of every online member at the next target. */
    private void retargetPartyCompasses(ExpeditionRun run) {
        Waypoint target = nextTarget(run);
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse(run.definition().dimension()));
        for (UUID member : run.party()) {
            ServerPlayer memberPlayer = server.getPlayerList().getPlayer(member);
            if (memberPlayer != null) {
                forEachCompass(memberPlayer, stack -> retarget(stack, dimension, target));
            }
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
                || state.is(ModBlocks.EXPEDITION_PORTAL.get())) {
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
