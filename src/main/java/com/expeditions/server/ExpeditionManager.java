package com.expeditions.server;

import com.expeditions.core.ExpeditionDefinition;
import com.expeditions.core.ExpeditionRun;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * Server-authoritative entry point for expedition lifecycle transitions.
 *
 * <p>State is backed by {@link ExpeditionSavedData} on the overworld, so the
 * manager itself stays stateless. A single global run is tracked for now;
 * per-player and per-dimension runs are a later milestone and will likely move
 * this to a keyed structure rather than changing this facade's contract.
 */
public final class ExpeditionManager {
    private final ExpeditionSavedData data;

    private ExpeditionManager(ExpeditionSavedData data) {
        this.data = data;
    }

    public static ExpeditionManager get(MinecraftServer server) {
        return new ExpeditionManager(server.overworld().getDataStorage()
                .computeIfAbsent(ExpeditionSavedData.FACTORY, ExpeditionSavedData.DATA_NAME));
    }

    /**
     * @return the current run if one exists and has not reached a terminal state.
     */
    public Optional<ExpeditionRun> activeRun() {
        ExpeditionRun run = data.run();
        return run != null && run.isActive() ? Optional.of(run) : Optional.empty();
    }

    /**
     * Starts a new run for {@code definition}.
     *
     * @throws IllegalStateException if a run is already active
     */
    public ExpeditionRun startRun(ExpeditionDefinition definition) {
        if (activeRun().isPresent()) {
            throw new IllegalStateException("An expedition is already active");
        }
        ExpeditionRun run = new ExpeditionRun(UUID.randomUUID(), definition);
        data.setRun(run);
        return run;
    }

    /**
     * Completes the active run.
     *
     * @throws IllegalStateException if no run is active
     */
    public void completeActiveRun() {
        run().complete();
        data.setDirty();
    }

    /**
     * Fails the active run.
     *
     * @throws IllegalStateException if no run is active
     */
    public void failActiveRun() {
        run().fail();
        data.setDirty();
    }

    private ExpeditionRun run() {
        return activeRun().orElseThrow(() -> new IllegalStateException("No expedition is active"));
    }
}
