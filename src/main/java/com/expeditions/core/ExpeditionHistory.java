package com.expeditions.core;

import java.util.*;

/** Bounded personal reports. These are observations, never reward authority. */
public final class ExpeditionHistory {
    public static final int LIMIT = 10;
    public record Entry(UUID runId, String expeditionId, String name, String dimension,
            ExpeditionStatus outcome, int checkpoints, int totalCheckpoints, int tokensKept,
            long elapsedTicks, long endedGameTime, String reason, boolean finalResult) {
        public Entry {
            Objects.requireNonNull(runId);
            Objects.requireNonNull(outcome);
            if (expeditionId == null || expeditionId.isBlank() || name == null || name.isBlank()
                    || dimension == null || dimension.isBlank() || reason == null || reason.isBlank()
                    || outcome == ExpeditionStatus.ACTIVE || totalCheckpoints < 1
                    || checkpoints < 0 || checkpoints > totalCheckpoints || tokensKept < 0
                    || elapsedTicks < -1 || endedGameTime < 0
                    || outcome == ExpeditionStatus.COMPLETED && (checkpoints != totalCheckpoints || !finalResult)) {
                throw new IllegalArgumentException("Invalid expedition history entry");
            }
        }
    }

    private final Map<UUID, List<Entry>> reports = new HashMap<>();

    /** A departure may be replaced if a member rejoins before the party seals. */
    public boolean record(UUID player, Entry entry) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(entry);
        var entries = new ArrayList<>(entries(player));
        var previous = entries.stream().filter(e -> e.runId().equals(entry.runId())).findFirst();
        if (previous.isPresent() && (previous.get().finalResult() || previous.get().equals(entry))) return false;
        entries.removeIf(e -> e.runId().equals(entry.runId()));
        entries.add(entry);
        entries.sort(Comparator.comparingLong(Entry::endedGameTime).reversed().thenComparing(Entry::runId));
        reports.put(player, List.copyOf(entries.subList(0, Math.min(LIMIT, entries.size()))));
        return true;
    }

    public List<Entry> entries(UUID player) { return reports.getOrDefault(player, List.of()); }
    public Map<UUID, List<Entry>> reports() { return Map.copyOf(reports); }
}
