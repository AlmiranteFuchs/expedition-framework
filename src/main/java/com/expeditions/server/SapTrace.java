package com.expeditions.server;

import net.minecraft.network.chat.Component;

public record SapTrace(int color, String mark) {
    private static final int[] COLORS = {0xa78e64, 0x8d9272, 0x807e9a, 0xa47770, 0x78928f, 0x9d876e};
    private static final String[] MARKS = {"◆", "●", "▲", "■", "✦", "◈"};

    public static SapTrace of(String dimension) {
        return switch (dimension) {
            case "minecraft:overworld" -> new SapTrace(0xc39556, "●");
            case "minecraft:the_nether" -> new SapTrace(0xb56b52, "▲");
            case "minecraft:the_end" -> new SapTrace(0x9281a6, "◆");
            case "" -> new SapTrace(0x88827b, "○");
            default -> {
                int hash = dimension.hashCode();
                yield new SapTrace(COLORS[Math.floorMod(hash, COLORS.length)], MARKS[Math.floorMod(hash >>> 8, MARKS.length)]);
            }
        };
    }

    public static Component label(String dimension) {
        var trace = of(dimension);
        return Component.literal(trace.mark() + " ").withColor(trace.color())
                .append(dimension.isBlank() ? Component.literal("Unbound") : ExpeditionText.dimension(dimension));
    }
}
