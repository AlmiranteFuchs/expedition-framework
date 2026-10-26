package com.expeditions.core;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One objective on an expedition definition: a stable id, a player-facing
 * description, whether it is required for completion, the kind-specific
 * parameters, and the bonus tokens granted when it completes.
 *
 * <p>Objectives are optional by default; a {@code required} one must be
 * completed before the run may complete ({@link ExpeditionRun#complete()}).
 * Every objective may carry a bonus that is paid at most once per run, and
 * only on successful completion — failed runs never pay bonuses.
 *
 * <p>Fields that do not apply to an objective's kind are normalized to
 * {@code 0}/{@code null} so equality and persistence stay canonical:
 * {@code checkpoint} for {@link ObjectiveKind#VISIT_CHECKPOINT},
 * {@code item}/{@code tag}/{@code count} for
 * {@link ObjectiveKind#COLLECT_ITEMS}, and {@code timeLimitTicks} for
 * {@link ObjectiveKind#TIME_LIMIT}.
 */
public record ExpeditionObjective(String id, String description, boolean required,
        ObjectiveKind kind, int checkpoint, String item, String tag, int count,
        long timeLimitTicks, int bonusTokens) {

    /**
     * Resource-location shape accepted for item ids and tag ids (the domain
     * cannot load Minecraft's ResourceLocation): {@code namespace:path} or a
     * bare path, lowercase, dots/dashes/slashes allowed.
     */
    private static final Pattern RESOURCE_ID =
            Pattern.compile("([a-z0-9_.-]+:)?[a-z0-9_./-]+");

    public ExpeditionObjective {
        Objects.requireNonNull(kind, "kind");
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Objective id must be non-empty");
        }
        if (description == null || description.isBlank()) {
            description = id;
        }
        if (bonusTokens < 0) {
            throw new IllegalArgumentException("Objective bonus must be >= 0");
        }
        switch (kind) {
            case VISIT_CHECKPOINT -> {
                if (checkpoint < 1) {
                    throw new IllegalArgumentException(
                            "visit_checkpoint objective \"" + id
                                    + "\" needs a checkpoint index >= 1");
                }
                item = null;
                tag = null;
                count = 0;
                timeLimitTicks = 0;
            }
            case COLLECT_ITEMS -> {
                boolean hasItem = item != null && !item.isBlank();
                boolean hasTag = tag != null && !tag.isBlank();
                if (hasItem == hasTag) {
                    throw new IllegalArgumentException(
                            "collect_items objective \"" + id
                                    + "\" needs exactly one of item or tag");
                }
                String target = hasItem ? item : tag;
                if (!RESOURCE_ID.matcher(target).matches()) {
                    throw new IllegalArgumentException(
                            "collect_items objective \"" + id
                                    + "\" has invalid id \"" + target + "\"");
                }
                if (count < 1) {
                    throw new IllegalArgumentException(
                            "collect_items objective \"" + id + "\" needs a count >= 1");
                }
                checkpoint = 0;
                timeLimitTicks = 0;
            }
            case TIME_LIMIT -> {
                if (timeLimitTicks < 1) {
                    throw new IllegalArgumentException(
                            "time_limit objective \"" + id
                                    + "\" needs a limit >= 1 tick");
                }
                checkpoint = 0;
                item = null;
                tag = null;
                count = 0;
            }
            case RECOVER_ARTIFACT -> {
                if (checkpoint < 1) {
                    throw new IllegalArgumentException(
                            "recover_artifact objective \"" + id
                                    + "\" needs a checkpoint index >= 1");
                }
                item = null;
                tag = null;
                count = 0;
                timeLimitTicks = 0;
            }
            case DEFEND_DEVICE -> {
                if (checkpoint < 1) {
                    throw new IllegalArgumentException(
                            "defend_device objective \"" + id
                                    + "\" needs a checkpoint index >= 1");
                }
                if (timeLimitTicks < 1) {
                    throw new IllegalArgumentException(
                            "defend_device objective \"" + id
                                    + "\" needs a siege duration >= 1 tick");
                }
                item = null;
                tag = null;
                count = 0;
            }
        }
    }

    /** A checkpoint-visit objective: completes when checkpoint {@code index} activates. */
    public static ExpeditionObjective visitCheckpoint(String id, String description,
            boolean required, int index, int bonusTokens) {
        return new ExpeditionObjective(id, description, required,
                ObjectiveKind.VISIT_CHECKPOINT, index, null, null, 0, 0, bonusTokens);
    }

    /**
     * A collection objective: completes once {@code count} matching items are
     * picked up during the run. Exactly one of {@code item} / {@code tag} must
     * be non-null.
     */
    public static ExpeditionObjective collectItems(String id, String description,
            boolean required, String item, String tag, int count, int bonusTokens) {
        return new ExpeditionObjective(id, description, required,
                ObjectiveKind.COLLECT_ITEMS, 0, item, tag, count, 0, bonusTokens);
    }

    /** A timed objective: completes only if the run finishes within {@code ticks}. */
    public static ExpeditionObjective timeLimit(String id, String description,
            boolean required, long ticks, int bonusTokens) {
        return new ExpeditionObjective(id, description, required,
                ObjectiveKind.TIME_LIMIT, 0, null, null, 0, ticks, bonusTokens);
    }

    /**
     * An artifact objective: the artifact is issued when checkpoint
     * {@code index} activates and completes when delivered at the exit.
     */
    public static ExpeditionObjective recoverArtifact(String id, String description,
            boolean required, int index, int bonusTokens) {
        return new ExpeditionObjective(id, description, required,
                ObjectiveKind.RECOVER_ARTIFACT, index, null, null, 0, 0, bonusTokens);
    }

    /**
     * A defense objective: a destructible device spawns when checkpoint
     * {@code index} activates and must survive {@code ticks} of siege.
     */
    public static ExpeditionObjective defendDevice(String id, String description,
            boolean required, int index, long ticks, int bonusTokens) {
        return new ExpeditionObjective(id, description, required,
                ObjectiveKind.DEFEND_DEVICE, index, null, null, 0, ticks, bonusTokens);
    }

    /** Progress value at which this objective completes. */
    public int progressTarget() {
        return switch (kind) {
            case VISIT_CHECKPOINT, RECOVER_ARTIFACT, DEFEND_DEVICE -> 1;
            case COLLECT_ITEMS -> count;
            case TIME_LIMIT -> (int) Math.min(timeLimitTicks, Integer.MAX_VALUE);
        };
    }
}
