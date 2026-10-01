package com.skyblockrating.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.skyblockrating.config.AdminManager;
import com.skyblockrating.config.OwnerLock;
import com.skyblockrating.storage.RatingStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TeammateHistoryManager {
    private static final File TEAMMATES_DUNGEONS_FILE = new File(Minecraft.getInstance().gameDirectory, "config/skyblockrating/teammates_dungeons.json");
    private static final File TEAMMATES_KUUDRA_FILE = new File(Minecraft.getInstance().gameDirectory, "config/skyblockrating/teammates_kuudra.json");
    private static final File TEAMMATES_SAFARI_FILE = new File(Minecraft.getInstance().gameDirectory, "config/skyblockrating/teammates_safari.json");
    private static final File LEGACY_TEAMMATES_FILE = new File(Minecraft.getInstance().gameDirectory, "config/skyblockrating/teammates.json");
    private static final File RECENT_RUNS_FILE = new File(Minecraft.getInstance().gameDirectory, "config/skyblockrating/recent_runs.json");

    // Map format: "player_ign" -> Set of teammates played with in that category
    private static final Map<String, Set<String>> dungeonsTeammatesMap = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> kuudraTeammatesMap = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> safariTeammatesMap = new ConcurrentHashMap<>();

    // Tracks the last 3 runs per category (dungeons, kuudra, safari)
    private static final Map<String, LinkedList<RunRecord>> categoryRecentRuns = new ConcurrentHashMap<>();

    public static class RunRecord {
        private long timestamp;
        private String category;
        private List<String> members = new ArrayList<>();
        private Map<String, String> classes = new HashMap<>();

        public RunRecord() {}

        public RunRecord(long timestamp, String category, List<String> members) {
            this.timestamp = timestamp;
            this.category = category;
            this.members = members != null ? new ArrayList<>(members) : new ArrayList<>();
            this.classes = new HashMap<>();
        }

        public long getTimestamp() { return timestamp; }
        public String getCategory() { return category; }
        public List<String> getMembers() { return members; }
        public Map<String, String> getClasses() {
            if (classes == null) classes = new HashMap<>();
            return classes;
        }

        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        public void setCategory(String category) { this.category = category; }
        public void setMembers(List<String> members) { this.members = members; }
        public void setClasses(Map<String, String> classes) { this.classes = classes; }

        public String getPlayerClass(String username) {
            if (classes == null || username == null) return null;
            return classes.get(username.trim().toLowerCase());
        }

        public void setPlayerClass(String username, String cls) {
            if (classes == null) classes = new HashMap<>();
            if (username != null && cls != null && !cls.isEmpty()) {
                classes.put(username.trim().toLowerCase(), cls);
            }
        }
    }

    public static class TeammateRunInfo {
        private final String name;
        private final String category;
        private final long timestamp;
        private final int runNumber;
        private final String dungeonClass;

        public TeammateRunInfo(String name, String category, long timestamp, int runNumber) {
            this(name, category, timestamp, runNumber, null);
        }

        public TeammateRunInfo(String name, String category, long timestamp, int runNumber, String dungeonClass) {
            this.name = name;
            this.category = category;
            this.timestamp = timestamp;
            this.runNumber = runNumber;
            this.dungeonClass = dungeonClass;
        }

        public String getName() { return name; }
        public String getCategory() { return category; }
        public long getTimestamp() { return timestamp; }
        public int getRunNumber() { return runNumber; }
        public String getDungeonClass() { return dungeonClass; }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static String activeCaptureCategory = "dungeons"; // Default capture category
    private static long lastRunStartDetectedTimestamp = 0;
    private static long lastPartyListSentTimestamp = 0;
    private static boolean expectingPartyListResponse = false;
    private static boolean capturingPartyListLines = false;
    private static final List<String> bufferedPartyListLines = new ArrayList<>();
    private static long partyListCaptureStartTime = 0;
    private static final long DUNGEON_END_COOLDOWN_MS = 20000L;
    private static final Pattern DUNGEON_SCORE_PATTERN =
            Pattern.compile("^(?:[•●>|\\-\\*]\\s*)?(?:team\\s+)?score:\\s*\\d+\\s*\\([a-z0-9\\+\\-]+\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLASS_TAG_PATTERN =
            Pattern.compile("(?i)\\[\\s*(M|Mage|A|Archer|B|Berserk|T|Tank|H|Healer)\\s*\\]");
    private static final Pattern CLASS_PAREN_PATTERN =
            Pattern.compile("(?i)\\(\\s*(M|Mage|A|Archer|B|Berserk|T|Tank|H|Healer)\\b");
    private static final Pattern CLASS_WORD_PATTERN =
            Pattern.compile("(?i)\\b(Mage|Archer|Berserk|Tank|Healer)\\b");
    private static final Pattern CLASS_PREFIX_LINE_PATTERN =
            Pattern.compile("(?i)(?:\\[|\\()\\s*(M|Mage|A|Archer|B|Berserk|T|Tank|H|Healer)\\s*(?:\\]|\\))\\s*(?:\\[[^\\]]+\\]\\s*)?([a-zA-Z0-9_]{3,16})");
    private static final Pattern CLASS_SUFFIX_LINE_PATTERN =
            Pattern.compile("(?i)\\b([a-zA-Z0-9_]{3,16})\\b\\s*(?:\\:)?\\s*(?:\\[|\\()\\s*(M|Mage|A|Archer|B|Berserk|T|Tank|H|Healer)(?:\\s+[a-zA-Z0-9]+)?\\s*(?:\\]|\\))");
    private static final Pattern CHAT_CLASS_PATTERN =
            Pattern.compile("(?i)\\b([a-zA-Z0-9_]{3,16})\\b\\s*(?:\\(|\\[)(M|Mage|A|Archer|B|Berserk|T|Tank|H|Healer)(?:\\)|\\])");
    private static final Pattern CHAT_CLASS_PREFIX_PATTERN =
            Pattern.compile("(?i)(?:\\(|\\[)(M|Mage|A|Archer|B|Berserk|T|Tank|H|Healer)(?:\\)|\\])\\s*\\b([a-zA-Z0-9_]{3,16})\\b");

    private static final Map<String, String> playerDungeonClassMap = new ConcurrentHashMap<>();

    public static void recordPlayerDungeonClass(String ign, String className) {
        if (ign == null || className == null) return;
        String clean = ign.replaceAll("(?i)§[0-9a-z]", "").replaceAll("[^a-zA-Z0-9_]", "").trim();
        if (!isValidPlayerIgn(clean)) return;
        String norm = normalizeDungeonClass(className);
        if (norm != null) {
            String lower = clean.toLowerCase();
            String prev = playerDungeonClassMap.put(lower, norm);
            LinkedList<RunRecord> list = categoryRecentRuns.get("dungeons");
            if (list != null) {
                synchronized (list) {
                    if (!list.isEmpty()) {
                        list.getFirst().setPlayerClass(clean, norm);
                    }
                }
            }
            if (prev == null || !prev.equalsIgnoreCase(norm)) {
                saveRecentRunsAsync();
                System.out.println("[SkyReview] Recorded dungeon class: " + clean + " -> [" + norm + "]");
            }
        }
    }

    public static void scanTextForClassAndPlayer(String rawText) {
        if (rawText == null || rawText.isEmpty()) return;
        String clean = rawText.replaceAll("(?i)§[0-9a-z]", "").replaceAll("(?i)&[0-9a-z]", "").trim();
        if (clean.isEmpty()) return;

        Matcher m1 = CLASS_PREFIX_LINE_PATTERN.matcher(clean);
        while (m1.find()) {
            String cls = normalizeDungeonClass(m1.group(1));
            String ign = m1.group(2);
            if (cls != null && isValidPlayerIgn(ign)) {
                recordPlayerDungeonClass(ign, cls);
            }
        }

        Matcher m2 = CLASS_SUFFIX_LINE_PATTERN.matcher(clean);
        while (m2.find()) {
            String ign = m2.group(1);
            String cls = normalizeDungeonClass(m2.group(2));
            if (cls != null && isValidPlayerIgn(ign)) {
                recordPlayerDungeonClass(ign, cls);
            }
        }
    }

    public static String getPlayerDungeonClass(String ign) {
        if (ign == null) return null;
        String clean = ign.replaceAll("(?i)§[0-9a-z]", "").replaceAll("[^a-zA-Z0-9_]", "").trim().toLowerCase();
        LinkedList<RunRecord> list = categoryRecentRuns.get("dungeons");
        if (list != null) {
            synchronized (list) {
                for (RunRecord run : list) {
                    String cls = run.getPlayerClass(clean);
                    if (cls != null && !cls.isEmpty()) return cls;
                }
            }
        }
        return playerDungeonClassMap.get(clean);
    }

    public static String normalizeDungeonClass(String input) {
        if (input == null) return null;
        String s = input.trim().toLowerCase();
        if (s.equals("m") || s.startsWith("mage")) return "Mage";
        if (s.equals("a") || s.startsWith("arch")) return "Archer";
        if (s.equals("b") || s.startsWith("bers")) return "Berserk";
        if (s.equals("t") || s.startsWith("tank")) return "Tank";
        if (s.equals("h") || s.startsWith("heal")) return "Healer";
        return null;
    }

    public static String getClassColor(String className) {
        if (className == null) return "§7";
        switch (className.toLowerCase()) {
            case "mage": return "§b";
            case "archer": return "§6";
            case "berserk": return "§c";
            case "tank": return "§a";
            case "healer": return "§d";
            default: return "§7";
        }
    }

    private static void detectAndRecordClassFromText(String ign, String text) {
        if (ign == null || text == null || text.isEmpty()) return;
        String clean = text.replaceAll("(?i)§[0-9a-z]", "").replaceAll("(?i)&[0-9a-z]", "").trim();
        Matcher m1 = CLASS_TAG_PATTERN.matcher(clean);
        if (m1.find()) {
            String norm = normalizeDungeonClass(m1.group(1));
            if (norm != null) {
                recordPlayerDungeonClass(ign, norm);
                return;
            }
        }
        Matcher m2 = CLASS_PAREN_PATTERN.matcher(clean);
        if (m2.find()) {
            String norm = normalizeDungeonClass(m2.group(1));
            if (norm != null) {
                recordPlayerDungeonClass(ign, norm);
                return;
            }
        }
        Matcher m3 = CLASS_WORD_PATTERN.matcher(clean);
        if (m3.find()) {
            String norm = normalizeDungeonClass(m3.group(1));
            if (norm != null) {
                recordPlayerDungeonClass(ign, norm);
            }
        }
    }
    private static long lastDungeonEndReminderTimestamp = 0;
    private static long lastKuudraEndReminderTimestamp = 0;
    private static long lastSafariEndReminderTimestamp = 0;
    private static long lastKuudraActivityTimestamp = 0;
    private static long lastDungeonActivityTimestamp = 0;

    private static final ScheduledExecutorService reminderScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "SkyReview-ReminderScheduler");
        t.setDaemon(true);
        return t;
    });

    static {
        loadAllCategoryTeammates();
        loadRecentRuns();
    }

    public static String extractPlainTextWithoutHover(Component component) {
        if (component == null) return "";
        return component.getString();
    }

    public static boolean isValidPlayerIgn(String username) {
        if (username == null) return false;
        String clean = username.replaceAll("(?i)§[0-9a-z]", "").replaceAll("[^a-zA-Z0-9_]", "").trim().toLowerCase();
        if (clean.length() < 3 || clean.length() > 16) return false;
        if (!clean.matches("^[a-zA-Z0-9_]{3,16}$")) return false;
        if (clean.startsWith("team_") || clean.startsWith("sidebar_") || clean.startsWith("score_") || clean.startsWith("obj_")) return false;

        // Reserve words filter
        if (clean.equals("party") || clean.equals("leader") || clean.equals("members") ||
            clean.equals("moderators") || clean.equals("member") || clean.equals("offline") ||
            clean.equals("online") || clean.equals("host") || clean.equals("location") ||
            clean.equals("status") || clean.equals("server") || clean.equals("dungeon") ||
            clean.equals("kuudra") || clean.equals("elle") || clean.equals("adventurers") ||
            clean.equals("safari") || clean.equals("manager") || clean.equals("tickets")) {
            return false;
        }

        return true;
    }

    public static void recordPartyListMembers(List<String> partyMembers, String category) {
        if (partyMembers == null || partyMembers.isEmpty()) return;

        Minecraft client = Minecraft.getInstance();
        String myUser = (client != null && client.getUser() != null && client.getUser().getName() != null)
                ? client.getUser().getName().trim().toLowerCase() : null;

        if (myUser == null || myUser.isEmpty()) return;

        String cat = normalizeCategory(category);
        Map<String, Set<String>> targetMap = getCategoryMap(cat);
        Set<String> myTeammates = targetMap.computeIfAbsent(myUser, k -> ConcurrentHashMap.newKeySet());

        boolean updated = false;
        List<String> validRunTeammates = new ArrayList<>();
        for (String member : partyMembers) {
            if (member != null) {
                String clean = member.replaceAll("(?i)§[0-9a-z]", "").replaceAll("[^a-zA-Z0-9_]", "").trim();
                if (isValidPlayerIgn(clean) && !clean.equalsIgnoreCase(myUser)) {
                    validRunTeammates.add(clean);
                    if (myTeammates.add(clean.toLowerCase())) {
                        updated = true;
                    }
                }
            }
        }

        if (!validRunTeammates.isEmpty()) {
            recordRecentRun(cat, validRunTeammates);
        }

        if (updated) {
            System.out.println("[SkyReview] VERIFIED party teammates recorded for [" + myUser + "] in [" + cat + "]: " + myTeammates);
            saveCategoryTeammatesAsync(cat);
            RatingStorage.recordTeammatesAsync(myUser, cat, myTeammates);
        }
    }

    public static boolean hasPlayedWith(String username) {
        return hasPlayedWith(username, "dungeons");
    }

    public static boolean hasPlayedWith(String username, String category) {
        if (username == null) return false;
        String cleanTarget = username.trim().toLowerCase();

        // Admins can bypass restriction ONLY if explicit bypass toggle is ON in Admin Panel
        if (OwnerLock.isOwner() && AdminManager.isBypassCooldownEnabled()) {
            return true;
        }

        String cat = normalizeCategory(category);
        if (cat.equals("other") || cat.equals("general")) {
            return true; // No restriction for General / Other category
        }

        Map<String, Set<String>> targetMap = getCategoryMap(cat);

        Minecraft client = Minecraft.getInstance();
        String myUser = (client != null && client.getUser() != null && client.getUser().getName() != null)
                ? client.getUser().getName().trim().toLowerCase() : "";

        if (!myUser.isEmpty() && targetMap.containsKey(myUser)) {
            if (targetMap.get(myUser).contains(cleanTarget)) {
                return true;
            }
        }

        // Fallback: check if cleanTarget is present in any entry in targetMap
        for (Set<String> teammates : targetMap.values()) {
            if (teammates.contains(cleanTarget)) {
                return true;
            }
        }

        return false;
    }

    public static boolean handleIncomingChatMessage(Component component) {
        if (component == null) return true;

        // 1. Check raw plain text first (preserves words like "HERE" that contain hover/click events)
        try {
            String rawText = component.getString();
            if (rawText != null && !rawText.isEmpty()) {
                String cleanRaw = rawText.replaceAll("(?i)§[0-9a-z]", "").replaceAll("(?i)&[0-9a-z]", "").trim();
                String rawLower = cleanRaw.toLowerCase();
                long now = System.currentTimeMillis();

                if (rawLower.contains("kuudra") || rawLower.contains("elle:")) {
                    lastKuudraActivityTimestamp = now;
                }
                if (rawLower.contains("catacombs") || rawLower.contains("mort:")) {
                    lastDungeonActivityTimestamp = now;
                }

                if (isDungeonEndTrigger(cleanRaw, rawLower)) {
                    if (com.skyblockrating.config.ModConfig.isDungeonEndReminder() && (now - lastDungeonEndReminderTimestamp > DUNGEON_END_COOLDOWN_MS)) {
                        lastDungeonEndReminderTimestamp = now;
                        System.out.println("[SkyReview] Dungeon end detected from component raw text: " + cleanRaw);
                        sendDungeonEndReminder();
                    }
                } else if (isKuudraDownTrigger(rawLower)) {
                    lastKuudraActivityTimestamp = now;
                    activeCaptureCategory = "kuudra";
                    if (com.skyblockrating.config.ModConfig.isDungeonEndReminder() && (now - lastKuudraEndReminderTimestamp > 10000L)) {
                        lastKuudraEndReminderTimestamp = now;
                        System.out.println("[SkyReview] Kuudra Down detected from component raw text: " + cleanRaw);
                        sendKuudraEndReminder(true);
                    }
                } else if (isDefeatTrigger(cleanRaw, rawLower)) {
                    if (com.skyblockrating.config.ModConfig.isDungeonEndReminder()) {
                        boolean isKuudra = "kuudra".equals(activeCaptureCategory) || (lastKuudraActivityTimestamp > lastDungeonActivityTimestamp && (now - lastKuudraActivityTimestamp < 600000L));
                        if (isKuudra) {
                            if (now - lastKuudraEndReminderTimestamp > 10000L) {
                                lastKuudraEndReminderTimestamp = now;
                                System.out.println("[SkyReview] Kuudra Defeat detected from component raw text: " + cleanRaw);
                                sendKuudraEndReminder(false);
                            }
                        } else {
                            if (now - lastDungeonEndReminderTimestamp > DUNGEON_END_COOLDOWN_MS) {
                                lastDungeonEndReminderTimestamp = now;
                                System.out.println("[SkyReview] Dungeon Defeat detected from component raw text: " + cleanRaw);
                                sendDungeonEndReminder();
                            }
                        }
                    }
                } else if (isSafariEndTrigger(rawLower)) {
                    if (com.skyblockrating.config.ModConfig.isDungeonEndReminder() && (now - lastSafariEndReminderTimestamp > 10000L)) {
                        lastSafariEndReminderTimestamp = now;
                        System.out.println("[SkyReview] Safari end detected from component raw text: " + cleanRaw);
                        sendSafariEndReminder();
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 2. Full visible text (preserving all player names and siblings)
        String visibleText = component.getString();
        return handleIncomingChatMessage(visibleText);
    }

    /**
     * Intercepts incoming chat messages.
     * Returns true if message should be shown in Chat HUD, or false to HIDE the message (used for /p list interception).
     */
    public static boolean handleIncomingChatMessage(String messageText) {
        if (messageText == null || messageText.isEmpty()) return true;

        // Strip section signs for clean matching
        String cleanText = messageText.replaceAll("(?i)§[0-9a-z]", "").replaceAll("(?i)&[0-9a-z]", "");
        String lower = cleanText.toLowerCase();
        long now = System.currentTimeMillis();

        if (lower.contains("kuudra") || lower.contains("elle:")) {
            lastKuudraActivityTimestamp = now;
        }
        if (lower.contains("catacombs") || lower.contains("mort:")) {
            lastDungeonActivityTimestamp = now;
        }

        // Check for Blacklisted players joining party and party leadership
        BlacklistManager.handleChatMessage(cleanText, lower);

        // Scan for player class mentions in chat
        try {
            scanTextForClassAndPlayer(cleanText);

            Matcher c1 = CHAT_CLASS_PATTERN.matcher(cleanText);
            while (c1.find()) {
                String ign = c1.group(1);
                String cls = c1.group(2);
                recordPlayerDungeonClass(ign, cls);
            }
            Matcher c2 = CHAT_CLASS_PREFIX_PATTERN.matcher(cleanText);
            while (c2.find()) {
                String cls = c2.group(1);
                String ign = c2.group(2);
                recordPlayerDungeonClass(ign, cls);
            }
        } catch (Throwable ignored) {}

        // 1. Dungeons Run Start Trigger ("mort:" && "good luck")
        if (isDungeonRunStartTrigger(lower)) {
            System.out.println("[SkyReview] Mort Dungeon run start detected: " + cleanText);
            triggerPartyListCheck("dungeons");
            return true;
        }

        // 2. Kuudra Run Start Trigger ("[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!")
        if (isKuudraRunStartTrigger(lower)) {
            System.out.println("[SkyReview] Elle Kuudra run start detected: " + cleanText);
            triggerPartyListCheck("kuudra");
            return true;
        }

        // 3. Safari Run Start Trigger (Leader / Member start messages)
        if (isSafariRunStartTrigger(lower)) {
            System.out.println("[SkyReview] Safari run start detected: " + cleanText);
            triggerPartyListCheck("safari");
            return true;
        }

        // 4. Check for /p list Response Lines to HIDE & PARSE
        if (expectingPartyListResponse && (now - lastPartyListSentTimestamp < 7000L)) {
            boolean isSeparator = cleanText.trim().matches("^-{10,}$") || cleanText.trim().matches("^={10,}$");
            boolean isHeader = lower.contains("party leader:") || lower.contains("party members") || lower.contains("party moderators:");

            if (isSeparator) {
                if (!capturingPartyListLines) {
                    // Top separator of /p list!
                    capturingPartyListLines = true;
                    partyListCaptureStartTime = now;
                    synchronized (bufferedPartyListLines) {
                        bufferedPartyListLines.clear();
                    }
                    return false; // Hide top separator
                } else {
                    // Bottom separator of /p list!
                    capturingPartyListLines = false;
                    expectingPartyListResponse = false;
                    finalizePartyListCapture();
                    return false; // Hide bottom separator
                }
            } else if (capturingPartyListLines) {
                // Any line inside the /p list output block (headers, members, wrapped continuation lines)
                synchronized (bufferedPartyListLines) {
                    bufferedPartyListLines.add(cleanText);
                }
                return false; // Hide from chat HUD
            } else if (isHeader) {
                // In case top separator was missed or delayed
                capturingPartyListLines = true;
                partyListCaptureStartTime = now;
                synchronized (bufferedPartyListLines) {
                    bufferedPartyListLines.add(cleanText);
                }
                return false; // Hide header line
            }
        } else {
            if (capturingPartyListLines) {
                capturingPartyListLines = false;
                expectingPartyListResponse = false;
                finalizePartyListCapture();
            }
            expectingPartyListResponse = false;
        }

        // 5. Dungeons Run End Trigger (Universal detection: Score, Defeated Boss, Extra Stats, Cata EXP, Requeue)
        if (isDungeonEndTrigger(cleanText, lower)) {
            if (com.skyblockrating.config.ModConfig.isDungeonEndReminder() && (now - lastDungeonEndReminderTimestamp > DUNGEON_END_COOLDOWN_MS)) {
                lastDungeonEndReminderTimestamp = now;
                System.out.println("[SkyReview] Dungeon end detected from messageText: " + cleanText);
                sendDungeonEndReminder();
            }
            return true;
        }

        // 6. Kuudra Run End Trigger ("KUUDRA DOWN!")
        if (isKuudraDownTrigger(lower)) {
            lastKuudraActivityTimestamp = now;
            activeCaptureCategory = "kuudra";
            if (com.skyblockrating.config.ModConfig.isDungeonEndReminder() && (now - lastKuudraEndReminderTimestamp > 10000L)) {
                lastKuudraEndReminderTimestamp = now;
                System.out.println("[SkyReview] Kuudra Down detected from messageText: " + cleanText);
                sendKuudraEndReminder(true);
            }
            return true;
        }

        // 7. Defeat Trigger ("DEFEAT")
        if (isDefeatTrigger(cleanText, lower)) {
            if (com.skyblockrating.config.ModConfig.isDungeonEndReminder()) {
                boolean isKuudra = "kuudra".equals(activeCaptureCategory) || (lastKuudraActivityTimestamp > lastDungeonActivityTimestamp && (now - lastKuudraActivityTimestamp < 600000L));
                if (isKuudra) {
                    if (now - lastKuudraEndReminderTimestamp > 10000L) {
                        lastKuudraEndReminderTimestamp = now;
                        System.out.println("[SkyReview] Kuudra Defeat detected from messageText: " + cleanText);
                        sendKuudraEndReminder(false);
                    }
                } else {
                    if (now - lastDungeonEndReminderTimestamp > DUNGEON_END_COOLDOWN_MS) {
                        lastDungeonEndReminderTimestamp = now;
                        System.out.println("[SkyReview] Dungeon Defeat detected from messageText: " + cleanText);
                        sendDungeonEndReminder();
                    }
                }
            }
            return true;
        }

        // 8. Safari Run End Trigger
        if (isSafariEndTrigger(lower)) {
            if (com.skyblockrating.config.ModConfig.isDungeonEndReminder() && (now - lastSafariEndReminderTimestamp > 10000L)) {
                lastSafariEndReminderTimestamp = now;
                System.out.println("[SkyReview] Safari end detected from messageText: " + cleanText);
                sendSafariEndReminder();
            }
            return true;
        }

        return true;
    }

    public static void processChatMessage(String messageText) {
        handleIncomingChatMessage(messageText);
    }

    public static boolean isDungeonEndTrigger(String lower) {
        return isDungeonEndTrigger(lower, lower);
    }

    public static boolean isDungeonEndTrigger(String cleanText, String lower) {
        if (lower == null || cleanText == null) return false;

        String trimmed = cleanText.trim();
        String trimmedLower = lower.trim();

        // 0. Ignore player chat messages (Party >, Guild >, Officer >, Co-op >, From, To, or [VIP]/[MVP])
        if (trimmedLower.startsWith("party >")
                || trimmedLower.startsWith("guild >")
                || trimmedLower.startsWith("officer >")
                || trimmedLower.startsWith("co-op >")
                || trimmedLower.startsWith("from ")
                || trimmedLower.startsWith("to ")
                || trimmedLower.contains("[vip")
                || trimmedLower.contains("[mvp")) {
            return false;
        }

        // 1. Score Trigger (Vanilla: "Team Score: 0 (D)", "Team Score: 302 (S+)" | Modded: "Score: 294 (S)", "Score: 300 (S+)")
        if (DUNGEON_SCORE_PATTERN.matcher(trimmedLower).find()) {
            return true;
        }

        // 2. Modded Boss Defeat Trigger (e.g. "Defeated Maxor, Storm, Goldor, and Necron in 09m 33s", "Defeated Bonzo in 01m 20s")
        if (trimmedLower.startsWith("defeated ") && (
                trimmedLower.contains(" in ") ||
                trimmedLower.contains("maxor") ||
                trimmedLower.contains("necron") ||
                trimmedLower.contains("sadan") ||
                trimmedLower.contains("livid") ||
                trimmedLower.contains("thorn") ||
                trimmedLower.contains("professor") ||
                trimmedLower.contains("scarf") ||
                trimmedLower.contains("bonzo")
        )) {
            return true;
        }

        // 3. Vanilla Extra Stats Banner (e.g. "> EXTRA STATS <", "> EXTRA STATS")
        if (trimmedLower.equals("> extra stats <") || trimmedLower.equals("> extra stats")
                || trimmedLower.startsWith("> extra stats") || trimmedLower.endsWith("extra stats <")) {
            return true;
        }

        // 4. Experience / Milestone Trigger (e.g. "+754,110 Cata EXP", "+0 Experience (No Class Milestone Reached)", "+28,000 Experience (+28,000 Catacombs)")
        if (trimmedLower.startsWith("+") && (
                trimmedLower.contains("cata exp") ||
                trimmedLower.contains("catacombs") ||
                trimmedLower.contains("no class milestone reached") ||
                trimmedLower.contains("class milestone")
        )) {
            return true;
        }

        // 5. Party Leader Re-queue Message (Fallback for party leaders)
        boolean hasRequeue = trimmedLower.contains("re-queue") || trimmedLower.contains("requeue");
        boolean hasCatacombs = trimmedLower.contains("catacombs");
        if ((hasRequeue && hasCatacombs) || trimmedLower.contains("click here to re-queue") || trimmedLower.contains("click to re-queue")) {
            return true;
        }

        return false;
    }

    private static boolean isKuudraDownTrigger(String lower) {
        if (lower == null) return false;
        return lower.contains("kuudra down");
    }

    private static boolean isDefeatTrigger(String cleanText, String lower) {
        if (cleanText == null) return false;
        // Strip non-letter characters and extra spaces
        String trimmed = cleanText.trim();
        if (cleanText.contains(":")) {
            return false; // Ignore player or NPC chat messages e.g. "[MVP+] Steve: defeat"
        }
        return trimmed.equalsIgnoreCase("defeat") || trimmed.equalsIgnoreCase("defeat!")
                || trimmed.equalsIgnoreCase("> defeat <") || trimmed.equalsIgnoreCase("defeat.");
    }

    private static boolean isSafariEndTrigger(String lower) {
        if (lower == null) return false;
        // Matches:
        // "[NPC] Safari Manager: Would you like to leave the Critter Safari?"
        // "Select an option: [LEAVE]"
        return (lower.contains("safari manager") && lower.contains("would you like to leave"))
                || lower.contains("would you like to leave the critter safari")
                || (lower.contains("select an option") && lower.contains("[leave]"))
                || lower.contains("option: [leave]");
    }

    public static void recordCurrentDungeonPlayersIfMissing() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) return;
        String myUser = (client.getUser() != null && client.getUser().getName() != null)
                ? client.getUser().getName().trim().toLowerCase() : "";

        List<String> currentPlayers = new ArrayList<>();
        for (net.minecraft.client.multiplayer.PlayerInfo info : client.getConnection().getOnlinePlayers()) {
            if (info == null || info.getProfile() == null) continue;
            String ign = info.getProfile().name();
            if (!isValidPlayerIgn(ign)) continue;
            if (ign.equalsIgnoreCase(myUser)) continue;
            currentPlayers.add(ign);
        }
        if (!currentPlayers.isEmpty() && currentPlayers.size() <= 4) {
            recordPartyListMembers(currentPlayers, "dungeons");
        }
    }

    public static void sendDungeonEndReminder() {
        recordCurrentDungeonPlayersIfMissing();
        sendRunCompletionReminder("§aDungeon Completed!", "dungeons");
    }

    public static void sendKuudraEndReminder(boolean won) {
        if (won) {
            sendRunCompletionReminder("§aKuudra Completed!", "kuudra");
        } else {
            sendRunCompletionReminder("§cKuudra Run Ended (Defeat)!", "kuudra");
        }
    }

    public static void sendSafariEndReminder() {
        sendRunCompletionReminder("§aCritter Safari", "safari");
    }

    public static void sendRunCompletionReminder(String headerTitle, String category) {
        reminderScheduler.schedule(() -> {
            Minecraft client = Minecraft.getInstance();
            if (client == null) return;
            scanTabAndScoreboard(); // Force instant scan of Tab & Scoreboard right before reminder
            client.execute(() -> {
                if (client.player == null) return;
                client.player.sendSystemMessage(Component.literal("§6§m-----------------------------------------------------"));
                client.player.sendSystemMessage(Component.literal("§6§lSky§e§lReview §8| " + headerTitle));
                client.player.sendSystemMessage(Component.literal("§7You can rate your teammates using §e/sr rate <player>"));
                client.player.sendSystemMessage(Component.literal("§7or click a player below:"));

                List<String> teammates = getLatestRunTeammates(category);
                if (teammates.isEmpty()) {
                    List<TeammateRunInfo> recent = getRecentTeammatesLastThreeRuns(category);
                    Set<String> seen = new HashSet<>();
                    for (TeammateRunInfo info : recent) {
                        if (seen.add(info.getName().toLowerCase())) {
                            teammates.add(info.getName());
                            if (teammates.size() >= 4) break;
                        }
                    }
                }

                if (!teammates.isEmpty()) {
                    for (String mate : teammates) {
                        String dungeonClass = "dungeons".equalsIgnoreCase(category) ? getPlayerDungeonClass(mate) : null;
                        client.player.sendSystemMessage(createClickableTeammate(mate, category, dungeonClass));
                    }
                } else {
                    client.player.sendSystemMessage(Component.literal("  §eDon't forget to rate your teammates: §b/sr history"));
                }

                client.player.sendSystemMessage(Component.literal("§8(Disable this reminder in /sr settings)"));
                client.player.sendSystemMessage(Component.literal("§6§m-----------------------------------------------------"));
            });
        }, 1500, TimeUnit.MILLISECONDS);
    }

    public static List<String> getLatestRunTeammates(String category) {
        String cat = normalizeCategory(category);
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        Minecraft client = Minecraft.getInstance();
        String myUser = (client != null && client.getUser() != null && client.getUser().getName() != null)
                ? client.getUser().getName().trim().toLowerCase() : "";

        LinkedList<RunRecord> list = categoryRecentRuns.get(cat);
        if (list != null) {
            synchronized (list) {
                if (!list.isEmpty()) {
                    RunRecord latest = list.getFirst();
                    if (latest.getMembers() != null) {
                        for (String m : latest.getMembers()) {
                            if (m == null) continue;
                            String clean = m.trim();
                            if (!isValidPlayerIgn(clean)) continue;
                            String lower = clean.toLowerCase();
                            if (lower.equals(myUser)) continue;
                            if (seen.add(lower)) {
                                result.add(clean);
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    private static Component createClickableTeammate(String name, String category, String dungeonClass) {
        if ("dungeons".equalsIgnoreCase(category) && dungeonClass != null && !dungeonClass.isEmpty()) {
            String color = getClassColor(dungeonClass);
            return Component.literal("  §6★ §7(" + color + "[" + dungeonClass + "] §b§n" + name + "§7)§r")
                    .withStyle(style -> style
                            .withClickEvent(new ClickEvent.RunCommand("/sr rate " + name))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to rate §b" + name + "§e!")))
                    );
        }
        return Component.literal("  §6★ §b§n" + name + "§r")
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent.RunCommand("/sr rate " + name))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to rate §b" + name + "§e!")))
                );
    }

    private static boolean isDungeonRunStartTrigger(String lower) {
        return (lower.contains("mort:") && (lower.contains("good luck") || lower.contains("adventurers") || lower.contains("here") || lower.contains("dungeon")))
                || lower.contains("dungeon starts in")
                || lower.contains("entering the catacombs")
                || lower.contains("the catacombs - floor")
                || lower.contains("the catacombs - master mode")
                || (lower.contains("catacombs") && lower.contains("floor"));
    }

    private static boolean isKuudraRunStartTrigger(String lower) {
        return (lower.contains("elle:") || lower.contains("okay adventurers")) && lower.contains("fish up kuudra");
    }

    private static boolean isSafariRunStartTrigger(String lower) {
        return (lower.contains("safari manager:") || lower.contains("safari manager") || lower.contains("safari")) &&
               (lower.contains("show me their tickets") || lower.contains("looks good to me") || lower.contains("have fun out there"));
    }

    public static void triggerPartyListCheck(String category) {
        long now = System.currentTimeMillis();
        if (now - lastRunStartDetectedTimestamp < 25000L) {
            return; // Cooldown 25s between run start triggers so initial + retry run cleanly
        }
        lastRunStartDetectedTimestamp = now;
        activeCaptureCategory = normalizeCategory(category);

        // 1. First /p list immediately upon run start
        executePartyListCommand();

        // 2. Scheduled follow-up /p list after 15 seconds for guaranteed capture
        reminderScheduler.schedule(() -> {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.player != null) {
                client.execute(() -> {
                    System.out.println("[SkyReview] Executing scheduled follow-up /p list for category [" + activeCaptureCategory + "] (15s retry)...");
                    executePartyListCommand();
                });
            }
        }, 15, TimeUnit.SECONDS);
    }

    private static void executePartyListCommand() {
        lastPartyListSentTimestamp = System.currentTimeMillis();
        expectingPartyListResponse = true;
        capturingPartyListLines = false;
        synchronized (bufferedPartyListLines) {
            bufferedPartyListLines.clear();
        }

        Minecraft client = Minecraft.getInstance();
        if (client != null && client.player != null && client.player.connection != null) {
            client.execute(() -> {
                try {
                    System.out.println("[SkyReview] Executing automatic /p list for category [" + activeCaptureCategory + "]...");
                    client.player.connection.sendCommand("p list");
                } catch (Throwable t) {
                    try {
                        client.player.connection.sendChat("/p list");
                    } catch (Throwable ignored) {}
                }
            });
        }
    }

    private static boolean isPartyListMessage(String rawText, String lower) {
        if (lower.contains("party leader:") || lower.contains("party members") ||
            lower.contains("party moderators:") || lower.contains("party members (")) {
            return true;
        }
        // Hypixel party list separator lines e.g. "-----------------------------------------------------"
        if (rawText.trim().matches("^-{10,}$") || rawText.trim().matches("^={10,}$")) {
            return true;
        }
        return false;
    }

    private static void finalizePartyListCapture() {
        List<String> linesToParse;
        synchronized (bufferedPartyListLines) {
            if (bufferedPartyListLines.isEmpty()) return;
            linesToParse = new ArrayList<>(bufferedPartyListLines);
            bufferedPartyListLines.clear();
        }

        List<String> parsedMembers = new ArrayList<>();
        Minecraft client = Minecraft.getInstance();
        String myName = (client != null && client.getUser() != null && client.getUser().getName() != null)
                ? client.getUser().getName().trim().toLowerCase() : "";

        for (String rawLine : linesToParse) {
            if (rawLine == null) continue;
            String line = rawLine.replaceAll("(?i)§[0-9a-z]", "").replaceAll("(?i)&[0-9a-z]", "");
            String lower = line.toLowerCase();

            // Check if player is party leader
            if (lower.contains("party leader:")) {
                int colonIdx = line.indexOf(":");
                String after = (colonIdx != -1) ? line.substring(colonIdx + 1) : line;
                after = after.replaceAll("\\[[^\\]]+\\]", " ").replaceAll("[●○•]", " ");
                for (String token : after.split("[\\s,]+")) {
                    String clean = token.replaceAll("[^a-zA-Z0-9_]", "").trim().toLowerCase();
                    if (!clean.isEmpty() && clean.equals(myName)) {
                        BlacklistManager.setPartyLeader(true);
                    }
                }
            }

            // Extract members: take content after colon if header exists, or whole line if wrapped continuation
            int colonIdx = line.indexOf(":");
            String content = (colonIdx != -1) ? line.substring(colonIdx + 1) : line;

            // Remove rank tags like [VIP], [MVP+], [MVP++], [ADMIN], etc.
            content = content.replaceAll("\\[[^\\]]+\\]", " ");
            // Remove bullets and symbols
            content = content.replaceAll("[●○•]", " ");

            for (String token : content.split("[\\s,]+")) {
                String cleanToken = token.replaceAll("[^a-zA-Z0-9_]", "").trim();
                if (isValidPlayerIgn(cleanToken)) {
                    parsedMembers.add(cleanToken);
                }
            }
        }

        if (client != null && client.getUser() != null && client.getUser().getName() != null) {
            parsedMembers.add(client.getUser().getName());
        }

        System.out.println("[SkyReview] /p list captured members for [" + activeCaptureCategory + "]: " + parsedMembers);
        recordPartyListMembers(parsedMembers, activeCaptureCategory);
    }

    private static void parseAndRecordPartyList(String rawLine) {
        if (rawLine == null) return;
        synchronized (bufferedPartyListLines) {
            bufferedPartyListLines.add(rawLine);
        }
        finalizePartyListCapture();
    }

    public static void scanTabAndScoreboard() {
        long now = System.currentTimeMillis();

        // Flush any pending buffered /p list lines if timeout exceeded
        if (capturingPartyListLines && (now - partyListCaptureStartTime > 3000L)) {
            capturingPartyListLines = false;
            expectingPartyListResponse = false;
            finalizePartyListCapture();
        }

        // Scan Tab list and scoreboard teams for player dungeon classes
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.getConnection() != null) {
            try {
                List<String> dungeonTeammatesInInstance = new ArrayList<>();
                String myUser = (client.getUser() != null && client.getUser().getName() != null)
                        ? client.getUser().getName().trim().toLowerCase() : "";
                int classesFoundCount = 0;

                for (net.minecraft.client.multiplayer.PlayerInfo info : client.getConnection().getOnlinePlayers()) {
                    if (info == null) continue;

                    // 1. Scan text of every Tab entry (handles fake player slots and real players)
                    if (info.getTabListDisplayName() != null) {
                        scanTextForClassAndPlayer(info.getTabListDisplayName().getString());
                    }
                    if (info.getTeam() != null) {
                        if (info.getTeam().getPlayerPrefix() != null) {
                            scanTextForClassAndPlayer(info.getTeam().getPlayerPrefix().getString());
                        }
                        if (info.getTeam().getPlayerSuffix() != null) {
                            scanTextForClassAndPlayer(info.getTeam().getPlayerSuffix().getString());
                        }
                        if (info.getProfile() != null && info.getProfile().name() != null) {
                            String teamPrefix = info.getTeam().getPlayerPrefix() != null ? info.getTeam().getPlayerPrefix().getString() : "";
                            String teamSuffix = info.getTeam().getPlayerSuffix() != null ? info.getTeam().getPlayerSuffix().getString() : "";
                            scanTextForClassAndPlayer(teamPrefix + " " + info.getProfile().name() + " " + teamSuffix);
                        }
                    }

                    if (info.getProfile() == null) continue;
                    String ign = info.getProfile().name();
                    if (!isValidPlayerIgn(ign)) continue;

                    if (info.getTabListDisplayName() != null) {
                        detectAndRecordClassFromText(ign, info.getTabListDisplayName().getString());
                    }
                    if (info.getTeam() != null) {
                        if (info.getTeam().getPlayerPrefix() != null) {
                            detectAndRecordClassFromText(ign, info.getTeam().getPlayerPrefix().getString());
                        }
                        if (info.getTeam().getPlayerSuffix() != null) {
                            detectAndRecordClassFromText(ign, info.getTeam().getPlayerSuffix().getString());
                        }
                    }

                    if (!ign.equalsIgnoreCase(myUser)) {
                        dungeonTeammatesInInstance.add(ign);
                        if (playerDungeonClassMap.containsKey(ign.toLowerCase())) {
                            classesFoundCount++;
                        }
                    }
                }

                // 2. Scan Scoreboard Sidebar lines if in world
                if (client.level != null && client.level.getScoreboard() != null) {
                    try {
                        net.minecraft.world.scores.Scoreboard sb = client.level.getScoreboard();
                        net.minecraft.world.scores.Objective obj = sb.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR);
                        if (obj != null) {
                            for (net.minecraft.world.scores.ScoreHolder holder : sb.getTrackedPlayers()) {
                                if (holder == null) continue;
                                String holderName = holder.getScoreboardName();
                                net.minecraft.world.scores.PlayerTeam team = sb.getPlayersTeam(holderName);
                                String line = holderName;
                                if (team != null) {
                                    String p = team.getPlayerPrefix() != null ? team.getPlayerPrefix().getString() : "";
                                    String s = team.getPlayerSuffix() != null ? team.getPlayerSuffix().getString() : "";
                                    line = p + holderName + s;
                                }
                                scanTextForClassAndPlayer(line);
                            }
                        }
                    } catch (Throwable ignored) {}
                }

                // If in a dungeon instance (1-4 other players, and at least 1 player has a class detected)
                if (!dungeonTeammatesInInstance.isEmpty() && dungeonTeammatesInInstance.size() <= 4 && classesFoundCount >= 1) {
                    recordPartyListMembers(dungeonTeammatesInInstance, "dungeons");
                }
            } catch (Throwable ignored) {}
        }
    }

    private static String normalizeCategory(String category) {
        if (category == null) return "dungeons";
        String cat = category.toLowerCase().trim();
        if (cat.contains("kuudra")) return "kuudra";
        if (cat.contains("safari")) return "safari";
        if (cat.contains("other") || cat.contains("general")) return "other";
        return "dungeons";
    }

    private static Map<String, Set<String>> getCategoryMap(String category) {
        String cat = normalizeCategory(category);
        if (cat.equals("kuudra")) return kuudraTeammatesMap;
        if (cat.equals("safari")) return safariTeammatesMap;
        return dungeonsTeammatesMap;
    }

    private static File getCategoryFile(String category) {
        String cat = normalizeCategory(category);
        if (cat.equals("kuudra")) return TEAMMATES_KUUDRA_FILE;
        if (cat.equals("safari")) return TEAMMATES_SAFARI_FILE;
        return TEAMMATES_DUNGEONS_FILE;
    }

    private static void loadAllCategoryTeammates() {
        loadCategoryFile(TEAMMATES_DUNGEONS_FILE, dungeonsTeammatesMap);
        loadCategoryFile(TEAMMATES_KUUDRA_FILE, kuudraTeammatesMap);
        loadCategoryFile(TEAMMATES_SAFARI_FILE, safariTeammatesMap);

        // Migration from legacy teammates.json if present
        if (LEGACY_TEAMMATES_FILE.exists() && dungeonsTeammatesMap.isEmpty()) {
            Minecraft client = Minecraft.getInstance();
            String myUser = (client != null && client.getUser() != null) ? client.getUser().getName().toLowerCase() : "player";
            try (FileReader reader = new FileReader(LEGACY_TEAMMATES_FILE)) {
                Type setType = new TypeToken<Set<String>>() {}.getType();
                Set<String> set = GSON.fromJson(reader, setType);
                if (set != null) {
                    Set<String> targetSet = dungeonsTeammatesMap.computeIfAbsent(myUser, k -> ConcurrentHashMap.newKeySet());
                    for (String name : set) {
                        if (isValidPlayerIgn(name)) {
                            targetSet.add(name.toLowerCase().trim());
                        }
                    }
                    saveCategoryTeammatesAsync("dungeons");
                }
            } catch (Throwable ignored) {}
        }
    }

    private static void loadCategoryFile(File file, Map<String, Set<String>> targetMap) {
        if (!file.exists()) return;
        try (FileReader reader = new FileReader(file)) {
            Type mapType = new TypeToken<Map<String, Set<String>>>() {}.getType();
            Map<String, Set<String>> map = GSON.fromJson(reader, mapType);
            if (map != null) {
                for (Map.Entry<String, Set<String>> entry : map.entrySet()) {
                    String userKey = entry.getKey().toLowerCase().trim();
                    Set<String> set = targetMap.computeIfAbsent(userKey, k -> ConcurrentHashMap.newKeySet());
                    if (entry.getValue() != null) {
                        for (String tm : entry.getValue()) {
                            if (isValidPlayerIgn(tm)) {
                                set.add(tm.toLowerCase().trim());
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void saveCategoryTeammatesAsync(String category) {
        String cat = normalizeCategory(category);
        File file = getCategoryFile(cat);
        Map<String, Set<String>> targetMap = getCategoryMap(cat);

        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                File dir = file.getParentFile();
                if (!dir.exists()) dir.mkdirs();
                try (FileWriter writer = new FileWriter(file)) {
                    GSON.toJson(targetMap, writer);
                }
            } catch (Throwable ignored) {}
        });
    }

    public static void recordRecentRun(String category, List<String> members) {
        if (members == null || members.isEmpty()) return;
        String cat = normalizeCategory(category);
        long now = System.currentTimeMillis();
        LinkedList<RunRecord> list = categoryRecentRuns.computeIfAbsent(cat, k -> new LinkedList<>());
        synchronized (list) {
            // Batch with previous run if received within 30 seconds (multi-line or repeated /p list retry)
            if (!list.isEmpty() && (now - list.getFirst().getTimestamp() < 30000L)) {
                RunRecord current = list.getFirst();
                for (String m : members) {
                    boolean found = false;
                    for (String existing : current.getMembers()) {
                        if (existing.equalsIgnoreCase(m)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        current.getMembers().add(m);
                    }
                    if ("dungeons".equals(cat)) {
                        String cls = playerDungeonClassMap.get(m.toLowerCase());
                        if (cls != null && current.getPlayerClass(m) == null) {
                            current.setPlayerClass(m, cls);
                        }
                    }
                }
            } else {
                RunRecord newRun = new RunRecord(now, cat, members);
                if ("dungeons".equals(cat)) {
                    for (String m : members) {
                        String cls = playerDungeonClassMap.get(m.toLowerCase());
                        if (cls != null) {
                            newRun.setPlayerClass(m, cls);
                        }
                    }
                }
                list.addFirst(newRun);
                while (list.size() > 3) {
                    list.removeLast();
                }
            }
        }
        saveRecentRunsAsync();
    }

    public static List<TeammateRunInfo> getRecentTeammatesLastThreeRuns() {
        return getRecentTeammatesLastThreeRuns("dungeons");
    }

    public static List<TeammateRunInfo> getRecentTeammatesLastThreeRuns(String category) {
        String cat = normalizeCategory(category);
        List<TeammateRunInfo> result = new ArrayList<>();
        Set<String> seenIgnLower = new HashSet<>();

        Minecraft client = Minecraft.getInstance();
        String myUser = (client != null && client.getUser() != null && client.getUser().getName() != null)
                ? client.getUser().getName().trim().toLowerCase() : "";

        LinkedList<RunRecord> list = categoryRecentRuns.get(cat);
        if (list != null) {
            synchronized (list) {
                int runNum = 1;
                for (RunRecord run : list) {
                    if (run.getMembers() != null) {
                        for (String m : run.getMembers()) {
                            if (m == null) continue;
                            String clean = m.trim();
                            if (!isValidPlayerIgn(clean)) continue;
                            String lower = clean.toLowerCase();
                            if (lower.equals(myUser)) continue;

                            String dungeonClass = "dungeons".equals(cat) ? run.getPlayerClass(clean) : null;
                            if (dungeonClass == null && "dungeons".equals(cat)) {
                                dungeonClass = playerDungeonClassMap.get(lower);
                            }

                            // Deduplication: each nickname appears at most once across the 3 runs
                            if (seenIgnLower.add(lower)) {
                                result.add(new TeammateRunInfo(clean, cat, run.getTimestamp(), runNum, dungeonClass));
                            }
                        }
                    }
                    runNum++;
                    if (runNum > 3) break;
                }
            }
        }

        return result;
    }

    public static void clearRecentRuns() {
        clearRecentRuns("dungeons");
    }

    public static void clearRecentRuns(String category) {
        String cat = normalizeCategory(category);
        LinkedList<RunRecord> list = categoryRecentRuns.get(cat);
        if (list != null) {
            synchronized (list) {
                list.clear();
            }
        }
        saveRecentRunsAsync();
    }

    private static void saveRecentRunsAsync() {
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                File dir = RECENT_RUNS_FILE.getParentFile();
                if (!dir.exists()) dir.mkdirs();
                try (FileWriter writer = new FileWriter(RECENT_RUNS_FILE)) {
                    synchronized (categoryRecentRuns) {
                        GSON.toJson(categoryRecentRuns, writer);
                    }
                }
            } catch (Throwable ignored) {}
        });
    }

    private static void loadRecentRuns() {
        if (!RECENT_RUNS_FILE.exists()) return;
        try (FileReader reader = new FileReader(RECENT_RUNS_FILE)) {
            Type mapType = new TypeToken<Map<String, LinkedList<RunRecord>>>() {}.getType();
            Map<String, LinkedList<RunRecord>> map = GSON.fromJson(reader, mapType);
            if (map != null) {
                categoryRecentRuns.clear();
                categoryRecentRuns.putAll(map);
                for (LinkedList<RunRecord> list : categoryRecentRuns.values()) {
                    while (list.size() > 3) {
                        list.removeLast();
                    }
                }
            }
        } catch (Throwable ignored) {}
    }
}
