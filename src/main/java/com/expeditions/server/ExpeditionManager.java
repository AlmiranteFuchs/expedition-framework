package com.expeditions.server;

import com.expeditions.ExpeditionsConfig;
import com.expeditions.core.ExpeditionDefinition;
import com.expeditions.core.ExpeditionLedger;
import com.expeditions.core.ExpeditionRoute;
import com.expeditions.core.ExpeditionRun;
import com.expeditions.core.RouteAnchor;
import com.expeditions.core.RouteGeneration;
import com.expeditions.core.RoutePlanner;
import com.expeditions.core.Waypoint;
import com.expeditions.registry.ModBlocks;
import com.expeditions.registry.ModItems;
import com.expeditions.server.data.ExpeditionDefinitions;
import com.expeditions.server.dimension.ExpeditionAdapters;
import com.expeditions.server.dimension.ExpeditionDimensionAdapter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;

/**
 * Server-authoritative coordinator for expedition runs.
 *
 * <p>State lives in an {@link ExpeditionLedger} persisted by
 * {@link ExpeditionSavedData}; the manager is stateless. It additionally owns
 * the Minecraft-side work: picking the expedition configured for the player's
 * dimension, resolving its route anchors through the dimension's adapter, and
 * materializing/removing the marker blocks players interact with.
 */
public final class ExpeditionManager {
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
     * Resolves the route of the expedition configured for the player's current
     * dimension, places marker blocks and starts a run.
     *
     * @throws IllegalStateException if the player already has an active run, no
     *         expedition exists for the dimension, or no safe route resolves
     */
    public ExpeditionRun startRun(ServerPlayer player) {
        if (ledger.activeRun(player.getUUID()).isPresent()) {
            throw new IllegalStateException("You already have an active expedition");
        }
        ServerLevel level = player.serverLevel();
        // Datapack definitions take precedence over the built-in config ones.
        ExpeditionDefinition definition = ExpeditionDefinitions
                .forDimension(level.dimension().location().toString())
                .or(() -> ExpeditionsConfig.definitionFor(level.dimension()))
                .orElseThrow(() -> new IllegalStateException(
                        "No expedition is available in this dimension"));
        ExpeditionDimensionAdapter adapter = ExpeditionAdapters.forLevel(level)
                .orElseThrow(() -> new IllegalStateException(
                        "This dimension cannot host expeditions"));

        // A generated route is rolled fresh per run, anchored to the player's
        // position at the terminal; authored routes stay exact.
        boolean generated = definition.route() instanceof RouteGeneration;
        ExpeditionRoute route = switch (definition.route()) {
            case ExpeditionRoute r -> r;
            case RouteGeneration g -> RoutePlanner.generate(g,
                    player.blockPosition().getX(), player.blockPosition().getZ(),
                    player.getRandom().nextLong());
        };

        // Resolve the whole route before touching the world so a partially
        // unsafe route never leaves stray markers behind. Generated anchors
        // get a small jitter fallback since their positions are not authored.
        List<BlockPos> checkpointPositions = new ArrayList<>();
        for (RouteAnchor anchor : route.checkpoints()) {
            checkpointPositions.add(resolveAnchor(adapter, level, anchor, generated));
        }
        BlockPos exitPos = resolveAnchor(adapter, level, route.exit(), generated);

        String dimension = definition.dimension();
        for (BlockPos pos : checkpointPositions) {
            level.setBlock(pos, ModBlocks.EXPEDITION_CHECKPOINT.get().defaultBlockState(), 3);
            data.trackMarker(dimension, pos);
        }
        level.setBlock(exitPos, ModBlocks.EXPEDITION_EXIT.get().defaultBlockState(), 3);
        data.trackMarker(dimension, exitPos);

        ExpeditionRun run = ExpeditionRun.start(UUID.randomUUID(), player.getUUID(), definition,
                checkpointPositions.stream().map(ExpeditionManager::toWaypoint).toList(),
                toWaypoint(exitPos), level.getGameTime());
        ledger.startRun(run);
        grantCompass(player, run, level.dimension());
        data.setDirty();
        return run;
    }

    /**
     * Advances the player's active run if {@code pos} is its next checkpoint.
     *
     * @return the updated run
     * @throws IllegalStateException if the activation is invalid
     */
    public ExpeditionRun activateCheckpoint(ServerPlayer player, BlockPos pos) {
        ExpeditionRun run = requireActive(player.getUUID());
        run.activateCheckpoint(toWaypoint(pos));
        retargetCompasses(player, run, player.serverLevel().dimension());
        data.setDirty();
        return run;
    }

    /**
     * Completes the player's active run and grants the token reward — the
     * guaranteed amount plus a time bonus when earned — exactly once, then
     * removes marker blocks if no other run in the dimension needs them.
     *
     * @throws IllegalStateException if the run is missing, finished or incomplete
     */
    public ExpeditionRun completeRun(ServerPlayer player) {
        ExpeditionRun run = requireActive(player.getUUID());
        run.complete();
        if (!run.isRewarded()) {
            long elapsed = run.elapsedTicks(player.serverLevel().getGameTime());
            int tokens = run.definition().totalReward(elapsed);
            ledger.addTokens(player.getUUID(), tokens);
            run.markRewarded(tokens);
        }
        data.setDirty();
        removeCompasses(player);
        cleanupMarkers(run);
        return run;
    }

    /**
     * Fails the player's active run (death or abandon). No reward is granted.
     */
    public ExpeditionRun failRun(ServerPlayer player) {
        ExpeditionRun run = requireActive(player.getUUID());
        run.fail();
        data.setDirty();
        removeCompasses(player);
        cleanupMarkers(run);
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
        data.setDirty();
        removeCompasses(player);
        cleanupMarkers(run);
        return Optional.empty();
    }

    /**
     * Removes tracked marker blocks no active run still references. Called on
     * server start: a crash can roll back the run record while the marker
     * blocks were already flushed with their chunk, which would otherwise
     * leave indestructible blocks behind forever.
     */
    public static void pruneOrphanMarkers(MinecraftServer server) {
        ExpeditionManager manager = get(server);
        for (Map.Entry<String, Set<BlockPos>> entry : manager.data.liveMarkers().entrySet()) {
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
                .filter(r -> r.definition().dimension().equals(dimension))
                .anyMatch(r -> r.checkpoints().contains(waypoint) || r.exit().equals(waypoint));
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

    /** Points every expedition compass in the inventory at the run's next target. */
    private static void retargetCompasses(ServerPlayer player, ExpeditionRun run,
            ResourceKey<Level> dimension) {
        Waypoint target = nextTarget(run);
        forEachCompass(player, stack -> retarget(stack, dimension, target));
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
                || state.is(ModBlocks.EXPEDITION_EXIT.get())) {
            level.removeBlock(pos, false);
        }
    }

    /**
     * Resolves one route anchor. Generated anchors are retried on a small ring
     * of offsets before giving up; authored anchors are strict.
     */
    private static BlockPos resolveAnchor(ExpeditionDimensionAdapter adapter, ServerLevel level,
            RouteAnchor anchor, boolean allowJitter) {
        Optional<BlockPos> resolved = adapter.resolveAnchor(level, anchor);
        if (resolved.isEmpty() && allowJitter) {
            int[] ring = {16, -16};
            for (int dx : ring) {
                for (int dz : ring) {
                    resolved = adapter.resolveAnchor(level,
                            new RouteAnchor(anchor.x() + dx, anchor.z() + dz));
                    if (resolved.isPresent()) {
                        break;
                    }
                }
                if (resolved.isPresent()) {
                    break;
                }
            }
        }
        return resolved.orElseThrow(() -> new IllegalStateException(
                "No safe expedition route could be resolved in this dimension"));
    }

    private ExpeditionRun requireActive(UUID playerId) {
        return ledger.activeRun(playerId)
                .orElseThrow(() -> new IllegalStateException("No active expedition"));
    }

    public static Waypoint toWaypoint(BlockPos pos) {
        return new Waypoint(pos.getX(), pos.getY(), pos.getZ());
    }

    public static BlockPos toPos(Waypoint w) {
        return new BlockPos(w.x(), w.y(), w.z());
    }
}
