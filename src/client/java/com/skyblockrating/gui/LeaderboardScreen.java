package com.skyblockrating.gui;

import com.skyblockrating.config.GuiSize;
import com.skyblockrating.config.ModConfig;
import com.skyblockrating.config.OwnerLock;
import com.skyblockrating.data.ReviewEntry;
import com.skyblockrating.storage.RatingStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class LeaderboardScreen extends Screen {
    private static final List<PlayerRatingEntry> allPlayers = new ArrayList<>();
    private static boolean dataLoaded = false;
    private List<PlayerRatingEntry> filteredPlayers = new ArrayList<>();

    private EditBox searchBox;
    private String selectedCategory = "Dungeons";
    private String selectedOtherSubTag = "All";
    private String statusMessage = "";

    // Mouse Wheel Scrolling
    private double scrollOffset = 0;
    private static final int ROW_HEIGHT = 24;

    // Sorting controls
    public enum SortColumn { RATING, PLAYER, REVIEWS }
    private SortColumn activeSortColumn = SortColumn.RATING;
    private boolean sortAscending = false;

    public LeaderboardScreen() {
        super(Component.literal("SkyReview Leaderboard"));
        reloadFromStorage();
        updateFilteredList();
        RatingStorage.fetchOnlineDataAsync(players -> {
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                client.execute(() -> {
                    allPlayers.clear();
                    if (players != null) allPlayers.addAll(players);
                    recalculateAllRanks();
                    updateFilteredList();
                });
            }
        });
    }

    public static List<PlayerRatingEntry> getAllPlayers() {
        return allPlayers;
    }

    public static void reloadFromStorage() {
        if (allPlayers.isEmpty()) {
            allPlayers.addAll(RatingStorage.loadDataFromLocalFile());
            recalculateAllRanks();
        }
    }

    public static void submitPlayerReview(String author, String targetPlayer, String category, String subTag, double stars, String reviewText) {
        submitPlayerReview(author, null, targetPlayer, null, category, subTag, stars, reviewText);
    }

    public static void submitPlayerReview(String author, String authorUuid, String targetPlayer, String targetUuid, String category, String subTag, double stars, String reviewText) {
        final String cleanTargetUuid = (targetUuid != null) ? targetUuid.replace("-", "").toLowerCase() : null;

        Optional<PlayerRatingEntry> existing = allPlayers.stream()
            .filter(p -> {
                if (!p.getCategory().equalsIgnoreCase(category)) return false;
                if (cleanTargetUuid != null && p.getUuid() != null) {
                    return p.getUuid().equalsIgnoreCase(cleanTargetUuid);
                }
                return p.getName().equalsIgnoreCase(targetPlayer);
            })
            .findFirst();

        ReviewEntry newReview = new ReviewEntry(author, authorUuid, targetPlayer, cleanTargetUuid, category, subTag, stars, reviewText);
        RatingStorage.sendReviewAsync(newReview);

        if (existing.isPresent()) {
            PlayerRatingEntry entry = existing.get();
            if (cleanTargetUuid != null && entry.getUuid() == null) {
                entry.setUuid(cleanTargetUuid);
            }
            entry.setName(targetPlayer);
            entry.addOrUpdateReview(newReview);
        } else {
            PlayerRatingEntry entry = new PlayerRatingEntry(0, cleanTargetUuid, targetPlayer, category, stars, 0, null);
            entry.addOrUpdateReview(newReview);
            allPlayers.add(entry);
        }
        recalculateAllRanks();
    }

    public static void recalculateAllRanks() {
        allPlayers.removeIf(p -> p.getReviewsCount() == 0 || p.getRating() <= 0.0);
        allPlayers.sort(Comparator.comparingDouble(PlayerRatingEntry::getRating).reversed());

        for (String cat : new String[]{"Dungeons", "Kuudra", "Safari", "Other"}) {
            List<PlayerRatingEntry> catList = allPlayers.stream()
                .filter(p -> p.getCategory().equalsIgnoreCase(cat))
                .collect(Collectors.toList());
            for (int i = 0; i < catList.size(); i++) {
                catList.get(i).setRank(i + 1);
            }
        }
        RatingStorage.saveData(allPlayers);
    }

    private void updateFilteredList() {
        String query = searchBox != null ? searchBox.getValue().toLowerCase().trim() : "";
        filteredPlayers = allPlayers.stream()
            .filter(p -> p.getCategory().equalsIgnoreCase(selectedCategory))
            .filter(p -> query.isEmpty() || p.getName().toLowerCase().contains(query))
            .filter(p -> {
                if (!selectedCategory.equalsIgnoreCase("Other") || selectedOtherSubTag.equalsIgnoreCase("All")) {
                    return true;
                }
                return p.getReviews() != null && p.getReviews().stream().anyMatch(r -> r.getSubTag() != null && r.getSubTag().equalsIgnoreCase(selectedOtherSubTag));
            })
            .collect(Collectors.toList());

        // Apply column sorting
        Comparator<PlayerRatingEntry> comparator;
        if (activeSortColumn == SortColumn.PLAYER) {
            comparator = Comparator.comparing(PlayerRatingEntry::getName, String.CASE_INSENSITIVE_ORDER);
        } else if (activeSortColumn == SortColumn.REVIEWS) {
            comparator = Comparator.comparingInt(PlayerRatingEntry::getReviewsCount);
        } else {
            comparator = Comparator.comparingDouble(PlayerRatingEntry::getRating);
        }

        if (!sortAscending) {
            comparator = comparator.reversed();
        }

        filteredPlayers.sort(comparator);
        clampScroll();
    }

    private void clampScroll() {
        int panelHeight = getPanelHeight();
        int tableHeight = panelHeight - 114;
        int maxScroll = Math.max(0, filteredPlayers.size() * ROW_HEIGHT - tableHeight);
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
        if (scrollOffset < 0) {
            scrollOffset = 0;
        }
    }

    private void toggleSortColumn(SortColumn column) {
        if (activeSortColumn == column) {
            sortAscending = !sortAscending;
        } else {
            activeSortColumn = column;
            sortAscending = (column == SortColumn.PLAYER);
        }
        scrollOffset = 0;
        updateFilteredList();
    }

    private int getPanelWidth() {
        GuiSize size = ModConfig.getGuiSize();
        if (size == GuiSize.LARGE) return Math.min(580, this.width - 20);
        if (size == GuiSize.FULLSCREEN) return Math.max(500, this.width - 30);
        return 500; // NORMAL
    }

    private int getPanelHeight() {
        GuiSize size = ModConfig.getGuiSize();
        if (size == GuiSize.LARGE) return Math.min(380, this.height - 20);
        if (size == GuiSize.FULLSCREEN) return Math.max(300, this.height - 30);
        return 300; // NORMAL
    }

    @Override
    protected void init() {
        super.init();

        int panelWidth = getPanelWidth();
        int panelHeight = getPanelHeight();
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;

        // Top-right Header Buttons
        int rightHeaderX = panelX + panelWidth - 75;

        // 1. Settings Button
        this.addRenderableWidget(
            Button.builder(Component.literal("⚙ Settings"), button -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new SettingsScreen(this));
                }
            })
            .bounds(rightHeaderX, panelY + 6, 65, 18)
            .build()
        );

        rightHeaderX -= 85;

        // 2. Your Profile Button (opens profile even if player has 0 reviews)
        this.addRenderableWidget(
            Button.builder(Component.literal("👤 Your Profile"), button -> {
                if (this.minecraft != null) {
                    String selfName = (this.minecraft.getUser() != null) ? this.minecraft.getUser().getName() : "Player";
                    PlayerRatingEntry selfEntry = null;
                    for (PlayerRatingEntry p : allPlayers) {
                        if (p.getName().equalsIgnoreCase(selfName)) {
                            selfEntry = p;
                            break;
                        }
                    }
                    if (selfEntry == null) {
                        selfEntry = new PlayerRatingEntry(0, selfName, "General", 0.0, 0, null);
                    }
                    this.minecraft.setScreen(new ProfileScreen(this, selfEntry));
                }
            })
            .bounds(rightHeaderX, panelY + 6, 80, 18)
            .build()
        );

        rightHeaderX -= 70;

        // 3. Refresh Data Button
        this.addRenderableWidget(
            Button.builder(Component.literal("🔄 Refresh"), button -> {
                reloadFromStorage();
                updateFilteredList();
                this.statusMessage = "§aUpdated!";
            })
            .bounds(rightHeaderX, panelY + 6, 65, 18)
            .build()
        );

        if (OwnerLock.isOwner()) {
            int pendingCount = RatingStorage.getPendingReports().size();
            String adminLabel = "🛡 Admin" + (pendingCount > 0 ? " §c(" + pendingCount + ")" : "");
            int btnW = pendingCount > 0 ? 86 : 70;
            rightHeaderX -= (btnW + 5);
            this.addRenderableWidget(
                Button.builder(Component.literal(adminLabel), button -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new AdminControlScreen(this));
                    }
                })
                .bounds(rightHeaderX, panelY + 6, btnW, 18)
                .build()
            );
        }

        // Category Tab Buttons
        String[] categories = {"Dungeons", "Kuudra", "Safari", "Other", "Citadel"};
        int tabCount = categories.length;
        int tabTotalWidth = panelWidth - 28;
        int tabWidth = Math.min(84, tabTotalWidth / tabCount);
        int tabStartX = panelX + 14;

        for (int i = 0; i < tabCount; i++) {
            final String cat = categories[i];
            boolean isSelected = cat.equalsIgnoreCase(selectedCategory);
            String label = (isSelected ? "§6§l" : "§7") + cat;

            this.addRenderableWidget(
                Button.builder(Component.literal(label), button -> {
                    this.selectedCategory = cat;
                    this.scrollOffset = 0;
                    this.rebuildWidgets();
                })
                .bounds(tabStartX + i * (tabWidth + 4), panelY + 34, tabWidth, 19)
                .build()
            );
        }

        // Search Bar & Filter Controls Row
        int searchY = panelY + 58;
        int searchW = 140;
        this.searchBox = new EditBox(this.font, panelX + 14, searchY, searchW, 18, Component.literal("Search"));
        this.searchBox.setHint(Component.literal("Search player..."));
        this.searchBox.setResponder(text -> {
            scrollOffset = 0;
            updateFilteredList();
        });
        this.addRenderableWidget(this.searchBox);

        // SubTag filters for "Other" category
        if (selectedCategory.equalsIgnoreCase("Other")) {
            String[] subTags = {"All", "General", "Carry", "Helping"};
            int subX = panelX + 14 + searchW + 8;
            for (String sub : subTags) {
                boolean active = sub.equalsIgnoreCase(selectedOtherSubTag);
                String txt = (active ? "§a" : "§8") + sub;
                this.addRenderableWidget(
                    Button.builder(Component.literal(txt), b -> {
                        selectedOtherSubTag = sub;
                        scrollOffset = 0;
                        this.rebuildWidgets();
                    })
                    .bounds(subX, searchY, 48, 18)
                    .build()
                );
                subX += 52;
            }
        }

        // Bottom Action Bar Buttons
        int bottomY = panelY + panelHeight - 26;

        // Rate Player Button
        this.addRenderableWidget(
            Button.builder(Component.literal("⭐ Rate Player"), button -> {
                if (this.minecraft != null) {
                    if (RatingStorage.isCurrentClientBlocked()) {
                        if (this.minecraft.player != null) {
                            this.minecraft.player.sendSystemMessage(Component.literal("§c[SkyReview] Your account is restricted from posting reviews. If you disagree with this block, please write to us on Discord!"));
                        }
                        return;
                    }
                    this.minecraft.setScreen(new RatePlayerPromptScreen(this));
                }
            })
            .bounds(panelX + 14, bottomY, 100, 20)
            .build()
        );

        // Close Button
        this.addRenderableWidget(
            Button.builder(Component.literal("✕ Close"), button -> this.onClose())
            .bounds(panelX + panelWidth - 84, bottomY, 70, 20)
            .build()
        );

        updateFilteredList();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int panelWidth = getPanelWidth();
        int panelHeight = getPanelHeight();
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;
        int tableY = panelY + 80;
        int tableHeight = panelHeight - 114;

        if (mouseX >= panelX + 14 && mouseX <= panelX + panelWidth - 14 && mouseY >= tableY && mouseY <= tableY + tableHeight + 20) {
            scrollOffset -= verticalAmount * 24;
            clampScroll();
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, GuiHelper.BG_OVERLAY);

        int panelWidth = getPanelWidth();
        int panelHeight = getPanelHeight();
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;

        // Frame
        GuiHelper.renderPanel(graphics, panelX, panelY, panelWidth, panelHeight, 30, GuiHelper.HEADER_ACCENT);

        // Title Header
        graphics.text(this.font, Component.literal("§6§lSky§e§lReview §8| §fLeaderboard"), panelX + 14, panelY + 9, GuiHelper.TEXT_TITLE, true);

        if (selectedCategory.equalsIgnoreCase("Citadel")) {
            // Citadel Placeholder
            int csX = panelX + (panelWidth - this.font.width("🏛 Citadel Ratings coming soon...")) / 2;
            int csY = panelY + 120;
            graphics.text(this.font, Component.literal("🏛 Citadel Ratings coming soon..."), csX, csY, GuiHelper.TEXT_SECONDARY, true);
        } else {
            // Table Header Bar
            int tableY = panelY + 80;
            int tableW = panelWidth - 28;
            int tableHeight = panelHeight - 114;

            graphics.fill(panelX + 14, tableY, panelX + 14 + tableW, tableY + 18, GuiHelper.HEADER_BG);
            graphics.fill(panelX + 14, tableY + 17, panelX + 14 + tableW, tableY + 18, GuiHelper.PANEL_BORDER);

            // Column Header Titles with Direction Indicators
            String rankHeader = "#";
            String playerHeader = "Player" + (activeSortColumn == SortColumn.PLAYER ? (sortAscending ? " ▲" : " ▼") : "");
            String ratingHeader = "Rating" + (activeSortColumn == SortColumn.RATING ? (sortAscending ? " ▲" : " ▼") : "");
            String reviewsHeader = "Reviews" + (activeSortColumn == SortColumn.REVIEWS ? (sortAscending ? " ▲" : " ▼") : "");

            int playerColor = activeSortColumn == SortColumn.PLAYER ? GuiHelper.COLOR_GOLD : GuiHelper.TEXT_SECONDARY;
            int ratingColor = activeSortColumn == SortColumn.RATING ? GuiHelper.COLOR_GOLD : GuiHelper.TEXT_SECONDARY;
            int reviewsColor = activeSortColumn == SortColumn.REVIEWS ? GuiHelper.COLOR_GOLD : GuiHelper.TEXT_SECONDARY;

            int colRankX = panelX + 20;
            int colPlayerX = panelX + 50;
            int colRatingX = panelX + panelWidth - 235;
            int colReviewsX = panelX + panelWidth - 130;
            int colActionX = panelX + panelWidth - 72;

            graphics.text(this.font, Component.literal(rankHeader), colRankX, tableY + 5, GuiHelper.TEXT_MUTED, false);
            graphics.text(this.font, Component.literal(playerHeader), colPlayerX, tableY + 5, playerColor, activeSortColumn == SortColumn.PLAYER);
            graphics.text(this.font, Component.literal(ratingHeader), colRatingX, tableY + 5, ratingColor, activeSortColumn == SortColumn.RATING);
            graphics.text(this.font, Component.literal(reviewsHeader), colReviewsX, tableY + 5, reviewsColor, activeSortColumn == SortColumn.REVIEWS);
            graphics.text(this.font, Component.literal("Action"), colActionX + 4, tableY + 5, GuiHelper.TEXT_MUTED, false);

            // Table Content Area with clipping / offset calculation
            int startRowY = tableY + 20;
            int maxVisibleRows = tableHeight / ROW_HEIGHT;
            int startIndex = Math.max(0, (int) (scrollOffset / ROW_HEIGHT));
            int endIndex = Math.min(filteredPlayers.size(), startIndex + maxVisibleRows + 2);

            for (int i = startIndex; i < endIndex; i++) {
                PlayerRatingEntry p = filteredPlayers.get(i);
                int rankNum = i + 1;

                int rowY = startRowY + (i * ROW_HEIGHT) - (int) scrollOffset;
                if (rowY + ROW_HEIGHT <= startRowY || rowY >= startRowY + tableHeight) {
                    continue;
                }

                boolean hovered = mouseX >= panelX + 14 && mouseX <= panelX + 14 + tableW && mouseY >= rowY && mouseY < rowY + (ROW_HEIGHT - 2);
                int rowBg = hovered ? GuiHelper.CARD_HOVER : ((i % 2 == 0) ? GuiHelper.CARD_BG : GuiHelper.CARD_BG_ALT);

                graphics.fill(panelX + 14, rowY, panelX + 14 + tableW, rowY + (ROW_HEIGHT - 2), rowBg);

                // Rank Medal
                String rankStr;
                int rankColor;
                if (rankNum == 1) {
                    rankStr = "§6§l#1";
                    rankColor = GuiHelper.COLOR_GOLD;
                } else if (rankNum == 2) {
                    rankStr = "§f§l#2";
                    rankColor = 0xFFE2E8F0;
                } else if (rankNum == 3) {
                    rankStr = "§c§l#3";
                    rankColor = 0xFFF97316;
                } else {
                    rankStr = "§7#" + rankNum;
                    rankColor = GuiHelper.TEXT_MUTED;
                }
                graphics.text(this.font, Component.literal(rankStr), colRankX, rowY + 6, rankColor, rankNum <= 3);

                // Player Name & Prefix
                GuiHelper.renderPlayerWithPrefix(graphics, this.font, p.getName(), RatingStorage.getUserPrefix(p.getName()), colPlayerX, rowY + 6, false);

                // Star Rating
                String starsFormatted = GuiHelper.getFormattedStars(p.getRating());
                graphics.text(this.font, Component.literal(starsFormatted), colRatingX, rowY + 6, GuiHelper.COLOR_YELLOW, false);

                // Reviews count
                graphics.text(this.font, Component.literal("§7" + p.getReviewsCount() + " rev"), colReviewsX, rowY + 6, GuiHelper.TEXT_SECONDARY, false);

                // Profile action button pill - neatly sized and centered inside the row
                int btnW = 54;
                int btnH = 14;
                int btnY = rowY + (ROW_HEIGHT - 2 - btnH) / 2;
                boolean actionHovered = mouseX >= colActionX && mouseX <= colActionX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
                int actionBg = actionHovered ? 0xFF0284C7 : 0xFF1E293B;
                int actionBorder = actionHovered ? 0xFF38BDF8 : GuiHelper.PANEL_BORDER;

                graphics.fill(colActionX - 1, btnY - 1, colActionX + btnW + 1, btnY + btnH + 1, actionBorder);
                graphics.fill(colActionX, btnY, colActionX + btnW, btnY + btnH, actionBg);
                graphics.text(this.font, Component.literal("Profile →"), colActionX + 5, btnY + 3, 0xFFFFFFFF, false);
            }

            if (filteredPlayers.isEmpty()) {
                String msg = "No players found in '" + selectedCategory + "'";
                int msgX = panelX + (panelWidth - this.font.width(msg)) / 2;
                graphics.text(this.font, Component.literal(msg), msgX, tableY + 50, GuiHelper.TEXT_MUTED, false);
            } else {
                // Scrollbar indicator if scrollable
                int maxScroll = Math.max(0, filteredPlayers.size() * ROW_HEIGHT - tableHeight);
                if (maxScroll > 0) {
                    int scrollbarX = panelX + panelWidth - 10;
                    int scrollbarY = startRowY;
                    int scrollbarH = tableHeight;
                    int thumbH = Math.max(16, (int) ((float) tableHeight / (filteredPlayers.size() * ROW_HEIGHT) * tableHeight));
                    int thumbY = scrollbarY + (int) ((scrollOffset / maxScroll) * (scrollbarH - thumbH));

                    graphics.fill(scrollbarX, scrollbarY, scrollbarX + 3, scrollbarY + scrollbarH, 0x50000000);
                    graphics.fill(scrollbarX, thumbY, scrollbarX + 3, thumbY + thumbH, 0xFFAAAAAA);
                }
            }
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (selectedCategory.equalsIgnoreCase("Citadel")) {
            return super.mouseClicked(event, doubleClick);
        }

        double mouseX = event.x();
        double mouseY = event.y();

        boolean inSearch = (this.searchBox != null && mouseX >= this.searchBox.getX() && mouseX <= this.searchBox.getX() + this.searchBox.getWidth()
                && mouseY >= this.searchBox.getY() && mouseY <= this.searchBox.getY() + this.searchBox.getHeight());

        if (inSearch && this.searchBox.isFocused()) {
            this.searchBox.setFocused(false);
            this.setFocused(null);
            return true;
        }

        if (!inSearch && this.searchBox != null && this.searchBox.isFocused()) {
            this.searchBox.setFocused(false);
            if (this.getFocused() == this.searchBox) {
                this.setFocused(null);
            }
        }

        int panelWidth = getPanelWidth();
        int panelHeight = getPanelHeight();
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;
        int tableY = panelY + 80;
        int tableHeight = panelHeight - 114;

        int colPlayerX = panelX + 50;
        int colRatingX = panelX + panelWidth - 235;
        int colReviewsX = panelX + panelWidth - 130;

        // Check Header Column Clicks
        if (mouseY >= tableY && mouseY <= tableY + 18) {
            if (mouseX >= colPlayerX && mouseX < colRatingX) {
                toggleSortColumn(SortColumn.PLAYER);
                return true;
            }
            if (mouseX >= colRatingX && mouseX < colReviewsX) {
                toggleSortColumn(SortColumn.RATING);
                return true;
            }
            if (mouseX >= colReviewsX && mouseX < panelX + panelWidth - 70) {
                toggleSortColumn(SortColumn.REVIEWS);
                return true;
            }
        }

        // Table Rows Clicks
        int startRowY = tableY + 20;
        int maxVisibleRows = tableHeight / ROW_HEIGHT;
        int startIndex = Math.max(0, (int) (scrollOffset / ROW_HEIGHT));
        int endIndex = Math.min(filteredPlayers.size(), startIndex + maxVisibleRows + 2);

        for (int i = startIndex; i < endIndex; i++) {
            int rowY = startRowY + (i * ROW_HEIGHT) - (int) scrollOffset;
            if (rowY + ROW_HEIGHT <= startRowY || rowY >= startRowY + tableHeight) {
                continue;
            }

            if (mouseX >= panelX + 14 && mouseX <= panelX + panelWidth - 14 && mouseY >= rowY && mouseY < rowY + (ROW_HEIGHT - 2)) {
                PlayerRatingEntry p = filteredPlayers.get(i);
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new ProfileScreen(this, p));
                }
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
