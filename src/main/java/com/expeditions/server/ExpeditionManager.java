package com.expeditions.server;

import com.expeditions.ExpeditionsConfig;
import com.expeditions.core.ExpeditionDefinition;
import com.expeditions.core.ExpeditionLedger;
import com.expeditions.core.ExpeditionRun;
import com.expeditions.core.RouteAnchor;
import com.expeditions.core.Waypoint;
import com.expeditions.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Server-authoritative coordinator for expedition runs.
 *
 * <p>State lives in an {@link ExpeditionLedger} persisted by
 * {@link ExpeditionSavedData}; the manager is stateless. It additionally owns
 * the Minecraft-side work: resolving configured route anchors to surface
 * positions and materializing/removing the marker blocks players interact with.
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
    public java.util.Collection<ExpeditionRun> runs() {
        return ledger.runs();
    }

    /**
     * Resolves the configured route, places marker blocks and starts a run.
     *
     * @throws IllegalStateException if the player already has an active run
     */
    public ExpeditionRun startRun(ServerPlayer player) {
        if (ledger.activeRun(player.getUUID()).isPresent()) {
            throw new IllegalStateException("You already have an active expedition");
        }
        ExpeditionDefinition definition = ExpeditionsConfig.overworldDefinition();
        ServerLevel overworld = server.overworld();

        List<Waypoint> checkpoints = new ArrayList<>();
        for (RouteAnchor anchor : definition.route().checkpoints()) {
            BlockPos pos = resolveSurface(overworld, anchor);
            overworld.setBlock(pos, ModBlocks.EXPEDITION_CHECKPOINT.get().defaultBlockState(), 3);
            checkpoints.add(toWaypoint(pos));
        }
        BlockPos exitPos = resolveSurface(overworld, definition.route().exit());
        overworld.setBlock(exitPos, ModBlocks.EXPEDITION_EXIT.get().defaultBlockState(), 3);

        ExpeditionRun run = ExpeditionRun.start(UUID.randomUUID(), player.getUUID(), definition,
                checkpoints, toWaypoint(exitPos));
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
     * Completes the player's active run and grants the guaranteed token reward
     * exactly once, then removes marker blocks if no other run needs them.
     *
     * @throws IllegalStateException if the run is missing, finished or incomplete
     */
    public ExpeditionRun completeRun(ServerPlayer player) {
        ExpeditionRun run = requireActive(player.getUUID());
        run.complete();
        if (!run.isRewarded()) {
            ledger.addTokens(player.getUUID(), run.definition().tokenReward());
            run.markRewarded();
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

    /**
     * Resolves a configured X/Z anchor to a position resting on the terrain
     * surface: loads the column, asks the heightmap for the ground, then nudges
     * upward until a replaceable spot is found so markers never bury inside
     * obstacles.
     */
    private static BlockPos resolveSurface(ServerLevel level, RouteAnchor anchor) {
        level.getChunk(anchor.x() >> 4, anchor.z() >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, anchor.x(), anchor.z());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(anchor.x(), y, anchor.z());
        for (int i = 0; i < 16 && pos.getY() < level.getMaxBuildHeight() - 1; i++) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.canBeReplaced()) {
                break;
            }
            pos.move(0, 1, 0);
        }
        return pos.immutable();
    }

    private void cleanupMarkers(ExpeditionRun run) {
        // Marker positions are shared by concurrent runs; only remove them once
        // no other active run still needs them.
        if (ledger.anyOtherActiveRun(run.playerId())) {
            return;
        }
        ServerLevel overworld = server.overworld();
        for (Waypoint w : run.checkpoints()) {
            removeMarker(overworld, w, ModBlocks.EXPEDITION_CHECKPOINT.get().defaultBlockState());
        }
        removeMarker(overworld, run.exit(), ModBlocks.EXPEDITION_EXIT.get().defaultBlockState());
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
