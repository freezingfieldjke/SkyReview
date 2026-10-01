package com.skyblockrating.util;

import com.skyblockrating.config.ModConfig;
import com.skyblockrating.gui.PlayerRatingEntry;
import com.skyblockrating.storage.RatingStorage;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PartyFinderChatHelper {
    private static final Pattern PF_JOIN_PATTERN = Pattern.compile(
        "(?i)([a-zA-Z0-9_]{3,16})\\s*(?:\\[[^\\]]+\\]\\s*)?joined the (?:party|group|dungeon group)"
    );

    public static Component formatPartyFinderJoinMessage(Component message) {
        if (message == null) return null;
        if (!ModConfig.isPartyFinderJoinRating()) return message;

        String rawText = message.getString();
        if (rawText == null || rawText.isEmpty()) return message;

        String cleanText = rawText.replaceAll("(?i)§[0-9a-z]", "").replaceAll("(?i)&[0-9a-z]", "").trim();
        String lower = cleanText.toLowerCase();

        // Check if message is a Party Finder or Dungeon Finder join message
        if (!lower.contains("party finder") && !lower.contains("dungeon finder")) {
            return message;
        }

        if (!lower.contains("joined the group") && !lower.contains("joined the dungeon group") && !lower.contains("joined the party")) {
            return message;
        }

        // Avoid duplicate rating tag if already formatted
        if (rawText.contains("★") || rawText.contains("No reviews")) {
            return message;
        }

        Matcher matcher = PF_JOIN_PATTERN.matcher(cleanText);
        if (matcher.find()) {
            String ign = matcher.group(1);
            if (ign != null && !ign.equalsIgnoreCase("you") && !ign.equalsIgnoreCase("party") &&
                !ign.equalsIgnoreCase("finder") && !ign.equalsIgnoreCase("dungeon") && !ign.equalsIgnoreCase("group")) {

                // Determine category (Dungeons vs Kuudra) and extract dungeon class
                String category = null;
                String detectedClass = null;
                if (lower.contains("mage")) detectedClass = "Mage";
                else if (lower.contains("archer")) detectedClass = "Archer";
                else if (lower.contains("berserk")) detectedClass = "Berserk";
                else if (lower.contains("healer")) detectedClass = "Healer";
                else if (lower.contains("tank")) detectedClass = "Tank";

                if (detectedClass != null || lower.contains("dungeon") || lower.contains("catacombs")) {
                    category = "Dungeons";
                    if (detectedClass != null) {
                        TeammateHistoryManager.recordPlayerDungeonClass(ign, detectedClass);
                    }
                } else if (lower.contains("combat") || lower.contains("kuudra")) {
                    category = "Kuudra";
                }

                PlayerRatingEntry entry = RatingStorage.getPlayerEntry(ign, category);
                if (entry == null && category != null) {
                    entry = RatingStorage.getPlayerEntry(ign, null);
                }

                String ratingSuffix;
                if (entry != null && entry.getReviewsCount() > 0) {
                    ratingSuffix = String.format(Locale.US, " §8(§6★ §e%.1f §7/ §f%d rev§8)", entry.getRating(), entry.getReviewsCount());
                } else {
                    ratingSuffix = " §8(§7No reviews§8)";
                }

                return Component.empty().append(message).append(Component.literal(ratingSuffix));
            }
        }

        return message;
    }
}
