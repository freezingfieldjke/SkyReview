package com.skyblockrating.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.skyblockrating.config.ModConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class HypixelApiFetcher {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .executor(Executors.newCachedThreadPool())
        .build();

    private static final Map<String, HypixelStats> CACHE = new ConcurrentHashMap<>();

    public static class DungeonFloorData {
        public int runs = 0;
        public String pbFormatted = "N/A";
    }

    public static class HypixelStats {
        public final String ign;
        public int skyblockLevel = 0;
        public int combatLevel = 0;
        public int accessoryPower = 0;
        public int playtimeHours = 0;

        public int catacombsLevel = 0;
        public int totalSecrets = 0;
        public int totalDungeonRuns = 0;
        public double secretsPerRun = 0.0;

        public int totalKuudraRuns = 0;
        public final Map<String, Integer> kuudraTierRuns = new HashMap<>();
        public final Map<String, DungeonFloorData> floorMap = new HashMap<>();

        // API Hidden Flags
        public boolean isSkillsApiHidden = false;
        public boolean isDungeonsApiHidden = false;
        public boolean isKuudraApiHidden = false;

        // Multi-Profile Support
        public final List<String> availableProfileNames = new ArrayList<>();
        public final Map<String, JsonObject> profileMemberDataMap = new HashMap<>();
        public String selectedProfileName = "";

        public boolean loaded = false;
        public boolean hasSkyBlockProfile = false;
        public String status = "Loading...";

        public HypixelStats(String ign) {
            this.ign = ign;
            resetStats();
        }

        public void resetStats() {
            this.skyblockLevel = 0;
            this.combatLevel = 0;
            this.accessoryPower = 0;
            this.playtimeHours = 0;

            this.catacombsLevel = 0;
            this.totalSecrets = 0;
            this.totalDungeonRuns = 0;
            this.secretsPerRun = 0.0;

            this.totalKuudraRuns = 0;

            this.isSkillsApiHidden = false;
            this.isDungeonsApiHidden = false;
            this.isKuudraApiHidden = false;

            floorMap.clear();
            String[] floors = {"F1", "F2", "F3", "F4", "F5", "F6", "F7", "M1", "M2", "M3", "M4", "M5", "M6", "M7"};
            for (String f : floors) {
                floorMap.put(f, new DungeonFloorData());
            }

            kuudraTierRuns.clear();
            String[] tiers = {"Basic", "Hot", "Burning", "Fiery", "Infernal"};
            for (String t : tiers) {
                kuudraTierRuns.put(t, 0);
            }
        }

        public void selectProfile(String profileName) {
            if (profileMemberDataMap.containsKey(profileName)) {
                this.selectedProfileName = profileName;
                resetStats();
                JsonObject memberObj = profileMemberDataMap.get(profileName);
                if (memberObj != null) {
                    parseMemberStats(memberObj, this);
                }
            }
        }
    }

    public static void validatePlayer(String ign, Consumer<Boolean> callback) {
        if (ign == null || ign.trim().isEmpty()) {
            callback.accept(false);
            return;
        }

        fetchStats(ign, stats -> {
            callback.accept(stats != null && stats.hasSkyBlockProfile);
        });
    }

    public static void forceRefreshStats(String ign, Consumer<HypixelStats> callback) {
        if (ign == null || ign.isEmpty()) return;
        CACHE.remove(ign.toLowerCase().trim());
        fetchStats(ign, callback);
    }

    public static void fetchStats(String ign, Consumer<HypixelStats> callback) {
        if (ign == null || ign.isEmpty()) return;
        String key = ign.toLowerCase().trim();

        if (CACHE.containsKey(key) && CACHE.get(key).loaded) {
            callback.accept(CACHE.get(key));
            return;
        }

        HypixelStats stats = CACHE.computeIfAbsent(key, k -> new HypixelStats(ign));
        callback.accept(stats);

        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                // Step 1: Mojang UUID lookup
                String uuidUrl = "https://api.mojang.com/users/profiles/minecraft/" + ign.trim();
                HttpRequest uuidReq = HttpRequest.newBuilder()
                    .uri(URI.create(uuidUrl))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) SkyReview/1.0")
                    .GET()
                    .build();
                HttpResponse<String> uuidRes = HTTP_CLIENT.send(uuidReq, HttpResponse.BodyHandlers.ofString());

                if (uuidRes.statusCode() != 200) {
                    stats.hasSkyBlockProfile = false;
                    stats.status = "Player IGN does not exist";
                    return;
                }

                JsonObject uuidJson = JsonParser.parseString(uuidRes.body()).getAsJsonObject();
                String rawUuid = uuidJson.get("id").getAsString().replace("-", "");
                stats.hasSkyBlockProfile = true;

                // Step 2: Hypixel SkyBlock Profiles API via SkyReview Cloudflare Worker (with Edge caching & server key)
                String profilesUrl = com.skyblockrating.storage.RatingStorage.getWorkerUrl() + "/api/hypixel/profiles?uuid=" + rawUuid;
                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(profilesUrl))
                    .header("User-Agent", "SkyReview/1.0 (Minecraft Mod)")
                    .header("x-mod-version", "1.0.0")
                    .GET();

                String customKey = ModConfig.getCustomHypixelApiKey();
                if (customKey != null && !customKey.trim().isEmpty()) {
                    reqBuilder.header("x-hypixel-api-key", customKey.trim());
                }

                HttpResponse<String> profilesRes = HTTP_CLIENT.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

                if (profilesRes.statusCode() == 200) {
                    JsonObject profilesJson = JsonParser.parseString(profilesRes.body()).getAsJsonObject();
                    if (profilesJson.has("profiles") && !profilesJson.get("profiles").isJsonNull()) {
                        JsonArray profiles = profilesJson.getAsJsonArray("profiles");
                        if (profiles != null && profiles.size() > 0) {
                            stats.availableProfileNames.clear();
                            stats.profileMemberDataMap.clear();

                            String defaultSelectedName = "";

                            for (JsonElement pEl : profiles) {
                                JsonObject pObj = pEl.getAsJsonObject();
                                String cuteName = pObj.has("cute_name") ? pObj.get("cute_name").getAsString() : "Profile";
                                if (pObj.has("members")) {
                                    JsonObject members = pObj.getAsJsonObject("members");
                                    for (Entry<String, JsonElement> mEntry : members.entrySet()) {
                                        String mKey = mEntry.getKey().replace("-", "");
                                        if (mKey.equalsIgnoreCase(rawUuid)) {
                                            JsonObject memberObj = mEntry.getValue().getAsJsonObject();
                                            stats.availableProfileNames.add(cuteName);
                                            stats.profileMemberDataMap.put(cuteName, memberObj);
                                            if (pObj.has("selected") && pObj.get("selected").getAsBoolean()) {
                                                defaultSelectedName = cuteName;
                                            }
                                        }
                                    }
                                }
                            }

                            if (defaultSelectedName.isEmpty() && !stats.availableProfileNames.isEmpty()) {
                                defaultSelectedName = stats.availableProfileNames.get(0);
                            }

                            if (!stats.selectedProfileName.isEmpty() && stats.profileMemberDataMap.containsKey(stats.selectedProfileName)) {
                                stats.selectProfile(stats.selectedProfileName);
                            } else if (!defaultSelectedName.isEmpty()) {
                                stats.selectProfile(defaultSelectedName);
                            }
                        }
                    }
                    stats.status = "Loaded";
                } else if (profilesRes.statusCode() == 403) {
                    stats.status = "API Key Expired (Settings)";
                } else {
                    stats.status = "API Error (" + profilesRes.statusCode() + ")";
                }

                stats.loaded = true;
                callback.accept(stats);

            } catch (Exception e) {
                stats.loaded = true;
                stats.hasSkyBlockProfile = true;
                stats.status = "Loaded";
                callback.accept(stats);
            }
        });
    }

    private static void parseMemberStats(JsonObject member, HypixelStats stats) {
        // SkyBlock Level (leveling.experience / 100)
        try {
            if (member.has("leveling") && member.getAsJsonObject("leveling").has("experience")) {
                double exp = member.getAsJsonObject("leveling").get("experience").getAsDouble();
                stats.skyblockLevel = (int) (exp / 100);
            } else if (member.has("leveling") && member.getAsJsonObject("leveling").has("level")) {
                stats.skyblockLevel = member.getAsJsonObject("leveling").get("level").getAsInt();
            }
        } catch (Exception ignored) {}

        // Playtime Extraction
        try {
            long maxFoundHours = 0;
            if (member.has("time_played")) {
                maxFoundHours = Math.max(maxFoundHours, member.get("time_played").getAsLong() / 3600);
            }
            if (member.has("player_data") && member.getAsJsonObject("player_data").has("playtime")) {
                maxFoundHours = Math.max(maxFoundHours, member.getAsJsonObject("player_data").get("playtime").getAsLong() / 3600);
            }
            if (member.has("first_join")) {
                long firstJoin = member.get("first_join").getAsLong();
                if (firstJoin > 0) {
                    maxFoundHours = Math.max(maxFoundHours, (System.currentTimeMillis() - firstJoin) / 3600000L);
                }
            }
            stats.playtimeHours = (int) maxFoundHours;
        } catch (Exception ignored) {}

        // Combat XP & Skills API Hidden check
        if (member.has("player_data") && member.getAsJsonObject("player_data").has("experience")) {
            JsonObject exp = member.getAsJsonObject("player_data").getAsJsonObject("experience");
            if (exp.has("SKILL_COMBAT")) {
                double combatXp = exp.get("SKILL_COMBAT").getAsDouble();
                stats.combatLevel = calculateSkillLevel(combatXp);
                stats.isSkillsApiHidden = false;
            } else {
                stats.isSkillsApiHidden = true;
            }
        } else {
            stats.isSkillsApiHidden = true;
        }

        // Accessory Power
        if (member.has("accessory_bag_storage")) {
            JsonObject acc = member.getAsJsonObject("accessory_bag_storage");
            if (acc.has("highest_magical_power")) {
                stats.accessoryPower = acc.get("highest_magical_power").getAsInt();
            } else if (acc.has("magical_power")) {
                stats.accessoryPower = acc.get("magical_power").getAsInt();
            }
        }

        // Dungeons API & Dungeons Hidden check
        if (member.has("dungeons") && !member.get("dungeons").isJsonNull()) {
            JsonObject dungeons = member.getAsJsonObject("dungeons");
            stats.isDungeonsApiHidden = false;
            if (dungeons.has("secrets")) {
                stats.totalSecrets = dungeons.get("secrets").getAsInt();
            }
            if (dungeons.has("dungeon_types")) {
                JsonObject dTypes = dungeons.getAsJsonObject("dungeon_types");
                int totalRunsSum = 0;
                if (dTypes.has("catacombs")) {
                    JsonObject cata = dTypes.getAsJsonObject("catacombs");
                    if (cata.has("experience")) {
                        double cataXp = cata.get("experience").getAsDouble();
                        stats.catacombsLevel = calculateCataLevel(cataXp);
                    }
                    if (cata.has("tier_completions")) {
                        totalRunsSum += parseFloorCompletions(cata.getAsJsonObject("tier_completions"), "F", stats.floorMap);
                    }
                    parseFloorFastestTimes(cata, "F", stats.floorMap);
                }
                if (dTypes.has("master_catacombs")) {
                    JsonObject master = dTypes.getAsJsonObject("master_catacombs");
                    if (master.has("tier_completions")) {
                        totalRunsSum += parseFloorCompletions(master.getAsJsonObject("tier_completions"), "M", stats.floorMap);
                    }
                    parseFloorFastestTimes(master, "M", stats.floorMap);
                }
                stats.totalDungeonRuns = totalRunsSum;
                stats.secretsPerRun = (totalRunsSum > 0) ? ((double) stats.totalSecrets / totalRunsSum) : 0.0;
            }
        } else {
            stats.isDungeonsApiHidden = true;
        }

        // Kuudra API & Kuudra Hidden check
        if (member.has("nether_island_player_data") && !member.get("nether_island_player_data").isJsonNull()) {
            JsonObject nether = member.getAsJsonObject("nether_island_player_data");
            stats.isKuudraApiHidden = false;
            if (nether.has("kuudra_completed_tiers")) {
                JsonObject kuudra = nether.getAsJsonObject("kuudra_completed_tiers");
                int kuudraSum = 0;
                for (Entry<String, JsonElement> entry : kuudra.entrySet()) {
                    try {
                        String tierKey = entry.getKey().toLowerCase();
                        int count = entry.getValue().getAsInt();
                        kuudraSum += count;

                        if (tierKey.equals("none")) {
                            stats.kuudraTierRuns.put("Basic", count);
                        } else if (tierKey.equals("hot")) {
                            stats.kuudraTierRuns.put("Hot", count);
                        } else if (tierKey.equals("burning")) {
                            stats.kuudraTierRuns.put("Burning", count);
                        } else if (tierKey.equals("fiery")) {
                            stats.kuudraTierRuns.put("Fiery", count);
                        } else if (tierKey.equals("infernal")) {
                            stats.kuudraTierRuns.put("Infernal", count);
                        }
                    } catch (Exception ignored) {}
                }
                stats.totalKuudraRuns = kuudraSum;
            }
        } else {
            stats.isKuudraApiHidden = true;
        }
    }

    private static int parseFloorCompletions(JsonObject completionsObj, String prefix, Map<String, DungeonFloorData> floorMap) {
        int sum = 0;
        for (Entry<String, JsonElement> entry : completionsObj.entrySet()) {
            try {
                int tierNum = Integer.parseInt(entry.getKey());
                if (tierNum >= 1 && tierNum <= 7) {
                    String floorKey = prefix + tierNum;
                    int count = entry.getValue().getAsInt();
                    if (floorMap.containsKey(floorKey)) {
                        floorMap.get(floorKey).runs = count;
                    }
                    sum += count;
                }
            } catch (Exception ignored) {}
        }
        return sum;
    }

    private static void parseFloorFastestTimes(JsonObject dungeonObj, String prefix, Map<String, DungeonFloorData> floorMap) {
        JsonObject timesObj = null;
        if (dungeonObj.has("fastest_time_s_plus")) {
            timesObj = dungeonObj.getAsJsonObject("fastest_time_s_plus");
        } else if (dungeonObj.has("fastest_time")) {
            timesObj = dungeonObj.getAsJsonObject("fastest_time");
        }

        if (timesObj == null) return;

        for (Entry<String, JsonElement> entry : timesObj.entrySet()) {
            try {
                int tierNum = Integer.parseInt(entry.getKey());
                if (tierNum >= 1 && tierNum <= 7) {
                    String floorKey = prefix + tierNum;
                    long timeVal = entry.getValue().getAsLong();
                    if (timeVal > 0 && floorMap.containsKey(floorKey)) {
                        floorMap.get(floorKey).pbFormatted = formatRunTime(timeVal);
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private static String formatRunTime(long rawTime) {
        if (rawTime <= 0) return "N/A";
        long totalSec = rawTime / 1000;
        long min = totalSec / 60;
        long sec = totalSec % 60;
        return String.format("%d:%02d", min, sec);
    }

    private static int calculateCataLevel(double xp) {
        int[] cataThresholds = {
            50, 125, 235, 395, 625, 955, 1425, 2095, 3045, 4385, 6275, 8940, 12700,
            17960, 25340, 35640, 50040, 70040, 97640, 135640, 188140, 259640, 356640,
            488640, 668640, 911640, 1239640, 1684640, 2284640, 3084640, 4149640,
            5559640, 7409640, 9809640, 12909640, 16909640, 22059640, 28559640,
            36709640, 46909640, 59559640, 75059640, 93959640, 116759640, 143959640,
            176059640, 213559640, 257059640, 307059640, 364059640, 560000000
        };

        for (int i = 0; i < cataThresholds.length; i++) {
            if (xp < cataThresholds[i]) {
                return i;
            }
        }
        return 50;
    }

    private static int calculateSkillLevel(double xp) {
        int[] skillThresholds = {
            50, 175, 375, 675, 1175, 1925, 2925, 4425, 6425, 9925, 14925, 22425, 32425,
            47425, 67425, 97425, 147425, 222425, 322425, 522425, 822425, 1222425,
            1722425, 2322425, 3022425, 3822425, 4722425, 5722425, 6822425, 8022425,
            9322425, 10722425, 12222425, 13822425, 15522425, 17322425, 19222425,
            21222425, 23322425, 25522425, 27822425, 30222425, 32722425, 35322425,
            38072425, 40972425, 44022425, 47222425, 50572425, 54072425, 57722425,
            61522425, 65472425, 69572425, 73822425, 78222425, 8822425, 87472425, 92322425, 97322425
        };

        for (int i = 0; i < skillThresholds.length; i++) {
            if (xp < skillThresholds[i]) {
                return i;
            }
        }
        return 60;
    }
}
