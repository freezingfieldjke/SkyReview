package com.skyblockrating.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.skyblockrating.config.ModConfig;
import com.skyblockrating.data.BlacklistEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BlacklistManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File BLACKLIST_FILE = new File(Minecraft.getInstance().gameDirectory, "config/skyblockrating/blacklist.json");
    private static final Map<String, BlacklistEntry> blacklistMap = new ConcurrentHashMap<>();
    private static final Map<String, Long> lastActionTimestamps = new ConcurrentHashMap<>();

    private static boolean isPartyLeader = true;
    private static final Pattern PARTY_JOIN_PATTERN = Pattern.compile(
        "(?i)(?:^|[\\s\\]>:])([a-zA-Z0-9_]{3,16})\\s*(?:\\[[^\\]]+\\]\\s*)?joined the (?:party|group|dungeon group)"
    );

    private static final ScheduledExecutorService actionScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "SkyReview-BlacklistAction");
        t.setDaemon(true);
        return t;
    });

    static {
        loadBlacklist();
    }

    public static synchronized void loadBlacklist() {
        if (!BLACKLIST_FILE.exists()) return;
        try (FileReader reader = new FileReader(BLACKLIST_FILE)) {
            Type listType = new TypeToken<List<BlacklistEntry>>() {}.getType();
            List<BlacklistEntry> list = GSON.fromJson(reader, listType);
            if (list != null) {
                blacklistMap.clear();
                for (BlacklistEntry entry : list) {
                    if (entry != null && entry.getUsername() != null && !entry.getUsername().trim().isEmpty()) {
                        blacklistMap.put(entry.getUsername().trim().toLowerCase(), entry);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[SkyReview] Failed to load blacklist: " + e.getMessage());
        }
    }

    public static void saveBlacklistAsync() {
        List<BlacklistEntry> list = new ArrayList<>(blacklistMap.values());
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                File dir = BLACKLIST_FILE.getParentFile();
                if (!dir.exists()) dir.mkdirs();
                try (FileWriter writer = new FileWriter(BLACKLIST_FILE)) {
                    GSON.toJson(list, writer);
                }
            } catch (Exception e) {
                System.err.println("[SkyReview] Failed to save blacklist: " + e.getMessage());
            }
        });
    }

    public static boolean addPlayer(String username, String reason) {
        if (username == null || username.trim().isEmpty()) return false;
        String clean = username.replaceAll("[^a-zA-Z0-9_]", "").trim();
        if (clean.length() < 3 || clean.length() > 16) return false;

        BlacklistEntry entry = new BlacklistEntry(clean, reason, System.currentTimeMillis());
        blacklistMap.put(clean.toLowerCase(), entry);
        saveBlacklistAsync();
        return true;
    }

    public static boolean removePlayer(String username) {
        if (username == null || username.trim().isEmpty()) return false;
        String key = username.trim().toLowerCase();
        if (blacklistMap.remove(key) != null) {
            saveBlacklistAsync();
            return true;
        }
        return false;
    }

    public static boolean isBlacklisted(String username) {
        if (username == null || username.trim().isEmpty()) return false;
        String clean = username.replaceAll("(?i)§[0-9a-z]", "").replaceAll("[^a-zA-Z0-9_]", "").trim().toLowerCase();
        return blacklistMap.containsKey(clean);
    }

    public static BlacklistEntry getEntry(String username) {
        if (username == null) return null;
        String clean = username.replaceAll("(?i)§[0-9a-z]", "").replaceAll("[^a-zA-Z0-9_]", "").trim().toLowerCase();
        return blacklistMap.get(clean);
    }

    public static List<BlacklistEntry> getEntries() {
        List<BlacklistEntry> list = new ArrayList<>(blacklistMap.values());
        list.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        return list;
    }

    public static void clearBlacklist() {
        blacklistMap.clear();
        saveBlacklistAsync();
    }

    public static int getCount() {
        return blacklistMap.size();
    }

    public static boolean isPartyLeader() {
        return isPartyLeader;
    }

    public static void setPartyLeader(boolean leader) {
        isPartyLeader = leader;
    }

    /**
     * Inspects incoming chat messages to track party leadership and detect blacklisted player joins.
     */
    public static void handleChatMessage(String cleanText, String lower) {
        if (cleanText == null || cleanText.isEmpty()) return;

        Minecraft client = Minecraft.getInstance();
        String myUser = (client != null && client.getUser() != null) ? client.getUser().getName().trim() : "";

        // Process line by line to accurately parse multiline messages and avoid boundary issues
        String[] lines = cleanText.split("\r?\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            String lineLower = line.toLowerCase();

            // 1. Party Leadership State Tracking
            if (lineLower.contains("you are now the party leader") ||
                lineLower.contains("the party was transferred to you") ||
                lineLower.contains("you invited") ||
                lineLower.contains("you have invited") ||
                lineLower.contains("you created a party") ||
                lineLower.contains("created a party") ||
                lineLower.contains("party created") ||
                lineLower.contains("party finder > group created") ||
                lineLower.contains("created a dungeon finder group") ||
                (!myUser.isEmpty() && lineLower.contains("party was transferred to " + myUser.toLowerCase())) ||
                (!myUser.isEmpty() && lineLower.contains("party leader:") && lineLower.contains(myUser.toLowerCase()))) {
                isPartyLeader = true;
            } else if (lineLower.contains("you joined") ||
                       lineLower.contains("you have joined") ||
                       lineLower.contains("you are not the party leader") ||
                       lineLower.contains("you cannot kick players because you are not the party leader") ||
                       lineLower.contains("only the party leader can") ||
                       lineLower.contains("you must be the party leader") ||
                       (!myUser.isEmpty() && lineLower.contains("the party was transferred to ") && !lineLower.contains(myUser.toLowerCase())) ||
                       (!myUser.isEmpty() && lineLower.contains("party leader:") && !lineLower.contains(myUser.toLowerCase()))) {
                isPartyLeader = false;
            } else if (lineLower.contains("left the party") ||
                       lineLower.contains("disbanded") ||
                       lineLower.contains("kicked from the party") ||
                       lineLower.contains("not in a party")) {
                isPartyLeader = true; // Reset state when out of party
            }

            // 2. Party Join Detection (Party Invite & Party Finder)
            if (lineLower.contains("joined the party") || lineLower.contains("joined the group") || lineLower.contains("joined the dungeon group")) {
                Matcher matcher = PARTY_JOIN_PATTERN.matcher(line);
                if (matcher.find()) {
                    String joinedPlayer = matcher.group(1);
                    if (joinedPlayer != null && !joinedPlayer.equalsIgnoreCase("you") && (myUser.isEmpty() || !joinedPlayer.equalsIgnoreCase(myUser))) {
                        if (isBlacklisted(joinedPlayer)) {
                            BlacklistEntry entry = getEntry(joinedPlayer);
                            String reason = (entry != null) ? entry.getReason() : "No reason provided";
                            onBlacklistedPlayerJoined(joinedPlayer, reason);
                        }
                    }
                }
            }
        }
    }

    private static void onBlacklistedPlayerJoined(String player, String reason) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;

        // Dedup: Prevent duplicate alerts or multiple kicks within 3 seconds
        long now = System.currentTimeMillis();
        Long lastTime = lastActionTimestamps.get(player.toLowerCase());
        if (lastTime != null && (now - lastTime < 3000L)) {
            return;
        }
        lastActionTimestamps.put(player.toLowerCase(), now);

        boolean autoKick = ModConfig.isAutoKickBlacklist();

        if (autoKick && isPartyLeader) {
            // Execute /p kick with 250ms delay
            actionScheduler.schedule(() -> {
                client.execute(() -> {
                    if (client.player != null && client.player.connection != null) {
                        try {
                            client.player.connection.sendCommand("p kick " + player);
                        } catch (Throwable t) {
                            try {
                                client.player.connection.sendChat("/p kick " + player);
                            } catch (Throwable ignored) {}
                        }

                        client.player.sendSystemMessage(Component.literal("§c§m-----------------------------------------------------"));
                        client.player.sendSystemMessage(Component.literal("§6§lSky§e§lReview §8| §cAuto-Kicked Blacklisted Player!"));
                        client.player.sendSystemMessage(Component.literal("§ePlayer §c" + player + " §ewas kicked from your party."));
                        client.player.sendSystemMessage(Component.literal("§7Reason: §f" + reason));
                        client.player.sendSystemMessage(Component.literal("§c§m-----------------------------------------------------"));
                    }
                });
            }, 250, TimeUnit.MILLISECONDS);
        } else {
            // Player is not leader or autoKick is disabled: send warning only (no leave button)
            client.execute(() -> {
                if (client.player != null) {
                    client.player.sendSystemMessage(Component.literal("§c§m-----------------------------------------------------"));
                    client.player.sendSystemMessage(Component.literal("§c§lSky§e§lReview §8| §c⚠ Blacklisted Player in Party!"));
                    client.player.sendSystemMessage(Component.literal("§ePlayer §c" + player + " §eis in your blacklist!"));
                    client.player.sendSystemMessage(Component.literal("§7Reason: §f" + reason));
                    client.player.sendSystemMessage(Component.literal("§c§m-----------------------------------------------------"));
                }
            });
        }
    }
}
