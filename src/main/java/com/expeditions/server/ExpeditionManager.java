package com.expeditions.server;

import com.expeditions.ExpeditionsConfig;
import com.expeditions.core.ExpeditionDefinition;
import com.expeditions.core.ExpeditionLedger;
import com.expeditions.core.ExpeditionRun;
import com.expeditions.core.RouteAnchor;
import com.expeditions.core.Waypoint;
import com.expeditions.registry.ModBlocks;
import com.expeditions.server.dimension.ExpeditionAdapters;
import com.expeditions.server.dimension.ExpeditionDimensionAdapter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

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
        ExpeditionDefinition definition = ExpeditionsConfig.definitionFor(level.dimension())
                .orElseThrow(() -> new IllegalStateException(
                        "No expedition is available in this dimension"));
        ExpeditionDimensionAdapter adapter = ExpeditionAdapters.forLevel(level)
                .orElseThrow(() -> new IllegalStateException(
                        "This dimension cannot host expeditions"));

        // Resolve the whole route before touching the world so a partially
        // unsafe route never leaves stray markers behind.
        List<BlockPos> checkpointPositions = new ArrayList<>();
        for (RouteAnchor anchor : definition.route().checkpoints()) {
            checkpointPositions.add(adapter.resolveAnchor(level, anchor)
                    .orElseThrow(() -> new IllegalStateException(
                            "No safe expedition route could be resolved in this dimension")));
        }
        BlockPos exitPos = adapter.resolveAnchor(level, definition.route().exit())
                .orElseThrow(() -> new IllegalStateException(
                        "No safe expedition route could be resolved in this dimension"));

        for (BlockPos pos : checkpointPositions) {
            level.setBlock(pos, ModBlocks.EXPEDITION_CHECKPOINT.get().defaultBlockState(), 3);
        }
        level.setBlock(exitPos, ModBlocks.EXPEDITION_EXIT.get().defaultBlockState(), 3);

        ExpeditionRun run = ExpeditionRun.start(UUID.randomUUID(), player.getUUID(), definition,
                checkpointPositions.stream().map(ExpeditionManager::toWaypoint).toList(),
                toWaypoint(exitPos), level.getGameTime());
        ledger.startRun(run);
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
        cleanupMarkers(run);
        return run;
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

    private ServerLevel levelFor(String dimensionId) {
        ResourceLocation location = ResourceLocation.tryParse(dimensionId);
        if (location == null) {
            return null;
        }
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, location));
    }

    private static void removeMarker(ServerLevel level, Waypoint w, BlockState expected) {
        BlockPos pos = toPos(w);
        if (level.getBlockState(pos).is(expected.getBlock())) {
            level.removeBlock(pos, false);
        }
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
