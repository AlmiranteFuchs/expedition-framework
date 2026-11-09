package com.expeditions.server;

import com.expeditions.core.FrontierProgression;
import java.util.StringJoiner;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Player-facing labels for the identifiers kept in saves, commands and datapacks. */
public final class ExpeditionText {
    private ExpeditionText() { }

    public static Component dimension(String id) {
        var location = ResourceLocation.tryParse(id);
        String fallback = switch (id) {
            case "minecraft:overworld" -> "Overworld";
            case "minecraft:the_nether" -> "The Nether";
            case "minecraft:the_end" -> "The End";
            case "twilightforest:twilightforest" -> "Twilight Forest";
            case "the_bumblezone:the_bumblezone", "bumblezone:bumblezone" -> "The Bumblezone";
            default -> label(id);
        };
        if (location == null) return Component.literal(fallback);
        return Component.translatableWithFallback("dimension." + location.getNamespace() + "."
                + location.getPath().replace('/', '.'), fallback);
    }

    public static String label(String id) {
        String path = id.substring(id.lastIndexOf(':') + 1);
        var label = new StringJoiner(" ");
        for (String word : path.split("[/_.-]+")) {
            if (!word.isBlank()) label.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
        }
        return label.length() == 0 ? "Unknown" : label.toString();
    }

    public static String title(String name) {
        // Older generated charters stored titles such as "Explore mod:dimension".
        int suffixStart = name.lastIndexOf(' ') + 1;
        String suffix = name.substring(suffixStart);
        if (suffix.contains(":") && ResourceLocation.tryParse(suffix) != null) {
            return name.substring(0, suffixStart) + dimension(suffix).getString();
        }
        return name;
    }

    public static Component proof(String key) {
        if (key.startsWith(FrontierProgression.EXTRACTION_TRACE_PREFIX)) {
            return dimension(key.substring(FrontierProgression.EXTRACTION_TRACE_PREFIX.length()))
                    .copy().append(" extraction proof");
        }
        return Component.literal(switch (key) {
            case "expeditions:field_clear" -> "Field Survey proof";
            case "expeditions:recovery_clear" -> "Trace Recovery proof";
            case "expeditions:salvage_clear" -> "Deep Salvage proof";
            case "expeditions:guardian_clear" -> "Guardian victory proof";
            case "expeditions:attuned" -> "Wayroot attunement";
            default -> label(key);
        });
    }

    public static Component proofAction(String key) {
        if (key.startsWith(FrontierProgression.EXTRACTION_TRACE_PREFIX)) {
            return Component.literal("Extract from ").append(
                    dimension(key.substring(FrontierProgression.EXTRACTION_TRACE_PREFIX.length()))).append(".");
        }
        return switch (key) {
            case "expeditions:field_clear" -> Component.literal("Complete Field Survey or First Bearing.");
            case "expeditions:recovery_clear" -> Component.literal("Complete Trace Recovery.");
            case "expeditions:salvage_clear" -> Component.literal("Complete Deep Salvage.");
            case "expeditions:guardian_clear" -> Component.literal("Defeat the Rootbound Guardian and extract.");
            case "expeditions:attuned" -> Component.literal("Use an Attunement Sigil on the Wayroot.");
            default -> Component.literal("Earn ").append(proof(key)).append(".");
        };
    }
}
