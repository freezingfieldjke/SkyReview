package com.skyblockrating.util;

import com.skyblockrating.gui.PlayerRatingEntry;
import com.skyblockrating.storage.RatingStorage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PartyFinderTooltipHelper {

    // Regex for Dungeons Party Finder lines: " PlayerName: Berserk (15)"
    private static final Pattern DUNGEON_MEMBER_PATTERN = Pattern.compile("(?i)^\\s*(?:§[0-9a-fk-or])*([a-zA-Z0-9_]{2,16}):\\s*(?:§[0-9a-fk-or])*([a-zA-Z]+)\\s*(?:§[0-9a-fk-or])*\\((\\d+)\\)");

    // Regex for Kuudra / Generic Party Finder lines: " PlayerName: (Combat 60)" or " PlayerName: Mage (50)" or " PlayerName: ..."
    private static final Pattern GENERIC_MEMBER_PATTERN = Pattern.compile("(?i)^\\s*(?:§[0-9a-fk-or])*([a-zA-Z0-9_]{2,16}):(?:\\s*(?:§[0-9a-fk-or])*(.*))?");

    private static final Set<String> RESERVED_WORDS = new SetBuilder()
            .add("party").add("leader").add("members").add("moderators").add("member")
            .add("requirements").add("note").add("dungeon").add("catacombs").add("floor")
            .add("kuudra").add("tier").add("status").add("stage").add("cost").add("click")
            .add("combat").add("level").add("offline").add("online").add("safari")
            .build();

    private static class SetBuilder {
        private final Set<String> set = new HashSet<>();
        public SetBuilder add(String s) {
            set.add(s.toLowerCase());
            return this;
        }
        public Set<String> build() {
            return set;
        }
    }

    public static void processTooltipLines(List<Component> lines) {
        if (lines == null || lines.isEmpty()) return;
        if (RatingStorage.isModDisabled() && !com.skyblockrating.config.OwnerLock.isOwner()) return;

        // 1. Detect Category: Kuudra vs Dungeons
        String detectedCategory = "Dungeons";
        for (Component c : lines) {
            if (c == null) continue;
            String text = c.getString().toLowerCase();
            if (text.contains("kuudra") || text.contains("crimson isle") || text.contains("basic tier")
                    || text.contains("hot tier") || text.contains("burning tier") || text.contains("fiery tier")
                    || text.contains("infernal tier") || text.contains("t1") || text.contains("t2")
                    || text.contains("t3") || text.contains("t4") || text.contains("t5")) {
                detectedCategory = "Kuudra";
                break;
            } else if (text.contains("catacombs") || text.contains("the catacombs") || text.contains("master mode")
                    || text.contains("floor i") || text.contains("floor ii") || text.contains("floor iii")
                    || text.contains("floor iv") || text.contains("floor v") || text.contains("floor vi")
                    || text.contains("floor vii") || text.contains("entrance")) {
                detectedCategory = "Dungeons";
            }
        }

        // 2. Parse Member Lines & Append Rating for the Detected Category
        for (int i = 0; i < lines.size(); i++) {
            Component lineComp = lines.get(i);
            if (lineComp == null) continue;

            String plainText = lineComp.getString();
            String foundIgn = null;

            // Try Dungeon Pattern first
            Matcher dungMatcher = DUNGEON_MEMBER_PATTERN.matcher(plainText);
            if (dungMatcher.find()) {
                foundIgn = dungMatcher.group(1);
            } else {
                // Try Generic/Kuudra Pattern
                Matcher genMatcher = GENERIC_MEMBER_PATTERN.matcher(plainText);
                if (genMatcher.find()) {
                    String candidate = genMatcher.group(1);
                    if (candidate != null && !RESERVED_WORDS.contains(candidate.toLowerCase()) && candidate.length() >= 3 && candidate.length() <= 16) {
                        foundIgn = candidate;
                    }
                }
            }

            if (foundIgn != null && !RESERVED_WORDS.contains(foundIgn.toLowerCase())) {
                boolean isBlacklisted = BlacklistManager.isBlacklisted(foundIgn);
                if (isBlacklisted && !plainText.contains("BLACKLISTED")) {
                    com.skyblockrating.data.BlacklistEntry blEntry = BlacklistManager.getEntry(foundIgn);
                    String blReason = (blEntry != null) ? blEntry.getReason() : "Blacklisted";
                    String blacklistTag = " §c§l[⛔ BLACKLISTED: " + blReason + "]";
                    if (lineComp instanceof MutableComponent mutable) {
                        mutable.append(Component.literal(blacklistTag));
                    } else {
                        MutableComponent newComp = lineComp.copy().append(Component.literal(blacklistTag));
                        lines.set(i, newComp);
                    }
                }

                PlayerRatingEntry entry = RatingStorage.getPlayerEntry(foundIgn, detectedCategory);

                if (entry != null && entry.getReviewsCount() > 0) {
                    double rating = entry.getRating();
                    // Clean rating format without letter prefixes
                    String ratingTagText = String.format(" §f[§e%.1f★§f]", rating);

                    // Avoid duplicate tags on re-renders
                    if (!lines.get(i).getString().contains("★")) {
                        if (lines.get(i) instanceof MutableComponent mutable) {
                            mutable.append(Component.literal(ratingTagText));
                        } else {
                            MutableComponent newComp = lines.get(i).copy().append(Component.literal(ratingTagText));
                            lines.set(i, newComp);
                        }
                    }
                }
            }
        }
    }
}
