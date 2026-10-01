package com.skyblockrating.gui;

import com.skyblockrating.config.OwnerLock;
import com.skyblockrating.data.ReportEntry;
import com.skyblockrating.data.ReviewEntry;
import com.skyblockrating.storage.RatingStorage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.stream.Collectors;

public class AdminReportsScreen extends Screen {
    private final Screen parentScreen;
    private EditBox searchBox;
    private double reportScrollOffset = 0;
    private static final int REPORT_ITEM_HEIGHT = 86;

    // Sorting & Filtering
    public enum SortMode { TOP_REPORTED, NEWEST, OLDEST }
    private SortMode currentSort = SortMode.TOP_REPORTED;
    private String selectedReasonFilter = "All";
    private String statusMessage = "";

    private final String[] reasonFilterOpts = {"All", "Spam", "Toxic", "Fake"};

    public static class GroupedReport {
        private final ReviewEntry review;
        private final String targetPlayer;
        private final List<ReportEntry> reports = new ArrayList<>();

        public GroupedReport(ReportEntry initial) {
            this.review = initial.getReview();
            this.targetPlayer = initial.getTargetPlayer();
            this.reports.add(initial);
        }

        public void addReport(ReportEntry rep) {
            this.reports.add(rep);
        }

        public ReviewEntry getReview() {
            return review;
        }

        public String getTargetPlayer() {
            return targetPlayer;
        }

        public List<ReportEntry> getReports() {
            return reports;
        }

        public int getReportCount() {
            return reports.size();
        }

        public long getLatestTimestamp() {
            long max = 0;
            for (ReportEntry r : reports) {
                if (r.getTimestamp() > max) max = r.getTimestamp();
            }
            return max;
        }

        public boolean matchesFilter(String query, String reasonFilter) {
            // Reason filter
            if (!reasonFilter.equalsIgnoreCase("All")) {
                boolean hasReason = false;
                for (ReportEntry r : reports) {
                    if (r.getReason() != null && r.getReason().toLowerCase().contains(reasonFilter.toLowerCase())) {
                        hasReason = true;
                        break;
                    }
                }
                if (!hasReason) return false;
            }

            // Search query filter
            if (query.isEmpty()) return true;
            String q = query.toLowerCase().trim();

            if (targetPlayer != null && targetPlayer.toLowerCase().contains(q)) return true;
            if (review != null) {
                if (review.getAuthor() != null && review.getAuthor().toLowerCase().contains(q)) return true;
                if (review.getComment() != null && review.getComment().toLowerCase().contains(q)) return true;
                if (review.getCategory() != null && review.getCategory().toLowerCase().contains(q)) return true;
            }

            for (ReportEntry r : reports) {
                if (r.getReporter() != null && r.getReporter().toLowerCase().contains(q)) return true;
                if (r.getReason() != null && r.getReason().toLowerCase().contains(q)) return true;
            }

            return false;
        }
    }

    public AdminReportsScreen(Screen parentScreen) {
        super(Component.literal("Reports Moderation - Admin"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();

        int modalWidth = 560;
        int modalHeight = 330;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        // 1. Search Box
        int controlY = modalY + 34;
        this.searchBox = new EditBox(this.font, modalX + 14, controlY, 150, 18, Component.literal("Search"));
        this.searchBox.setHint(Component.literal("Search player/reason..."));
        this.searchBox.setResponder(txt -> {
            this.reportScrollOffset = 0;
            clampReportScroll();
        });
        this.addRenderableWidget(this.searchBox);

        // 2. Sort Button
        this.addRenderableWidget(
            Button.builder(Component.literal(getSortButtonText()), button -> {
                if (currentSort == SortMode.TOP_REPORTED) currentSort = SortMode.NEWEST;
                else if (currentSort == SortMode.NEWEST) currentSort = SortMode.OLDEST;
                else currentSort = SortMode.TOP_REPORTED;
                this.reportScrollOffset = 0;
                button.setMessage(Component.literal(getSortButtonText()));
            })
            .bounds(modalX + 170, controlY, 130, 18)
            .build()
        );

        // 3. Reason Filter Button
        this.addRenderableWidget(
            Button.builder(Component.literal("Filter: " + selectedReasonFilter + " ▼"), button -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new SelectorPopupScreen(this, "Filter Reports by Reason", reasonFilterOpts, opt -> {
                        this.selectedReasonFilter = opt;
                        this.reportScrollOffset = 0;
                        this.rebuildWidgets();
                    }));
                }
            })
            .bounds(modalX + 306, controlY, 120, 18)
            .build()
        );

        // 4. Dismiss All Button
        this.addRenderableWidget(
            Button.builder(Component.literal("🧹 Dismiss All"), button -> {
                List<ReportEntry> reports = RatingStorage.getPendingReports();
                if (!reports.isEmpty()) {
                    for (ReportEntry r : new ArrayList<>(reports)) {
                        RatingStorage.removeReport(r.getReportId());
                    }
                    this.statusMessage = "§7Dismissed all pending reports.";
                    this.reportScrollOffset = 0;
                    this.rebuildWidgets();
                }
            })
            .bounds(modalX + 432, controlY, 114, 18)
            .build()
        );

        // Bottom Action Bar Buttons
        int buttonHeight = 20;
        int buttonY = modalY + modalHeight - 26;

        // Back button
        this.addRenderableWidget(
            Button.builder(Component.literal("← Back"), button -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(this.parentScreen);
                }
            })
            .bounds(modalX + 14, buttonY, 80, buttonHeight)
            .build()
        );

        // Close button
        this.addRenderableWidget(
            Button.builder(Component.literal("✕ Close"), button -> this.onClose())
            .bounds(modalX + modalWidth - 84, buttonY, 70, buttonHeight)
            .build()
        );
    }

    private String getSortButtonText() {
        return switch (currentSort) {
            case TOP_REPORTED -> "Sort: 🔥 Top Reports";
            case NEWEST -> "Sort: 🕒 Newest";
            case OLDEST -> "Sort: ⌛ Oldest";
        };
    }

    private List<GroupedReport> getFilteredGroupedReports() {
        List<ReportEntry> rawReports = RatingStorage.getPendingReports();
        Map<String, GroupedReport> map = new LinkedHashMap<>();

        for (ReportEntry rep : rawReports) {
            if (rep.isAboutMe() || rep.getReview() == null) continue;
            String key = rep.getReview().getId();
            if (key == null || key.isEmpty()) {
                key = rep.getTargetPlayer() + "_" + rep.getReview().getAuthor() + "_" + rep.getReview().getComment();
            }

            if (map.containsKey(key)) {
                map.get(key).addReport(rep);
            } else {
                map.put(key, new GroupedReport(rep));
            }
        }

        String query = searchBox != null ? searchBox.getValue() : "";
        List<GroupedReport> result = map.values().stream()
            .filter(g -> g.matchesFilter(query, selectedReasonFilter))
            .collect(Collectors.toList());

        // Sort
        if (currentSort == SortMode.TOP_REPORTED) {
            result.sort(Comparator.comparingInt(GroupedReport::getReportCount).reversed().thenComparing(Comparator.comparingLong(GroupedReport::getLatestTimestamp).reversed()));
        } else if (currentSort == SortMode.NEWEST) {
            result.sort(Comparator.comparingLong(GroupedReport::getLatestTimestamp).reversed());
        } else {
            result.sort(Comparator.comparingLong(GroupedReport::getLatestTimestamp));
        }

        return result;
    }

    private void clampReportScroll() {
        List<GroupedReport> list = getFilteredGroupedReports();
        int visibleH = 330 - 95;
        int maxScroll = Math.max(0, list.size() * REPORT_ITEM_HEIGHT - visibleH);
        if (reportScrollOffset > maxScroll) reportScrollOffset = maxScroll;
        if (reportScrollOffset < 0) reportScrollOffset = 0;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int modalWidth = 560;
        int modalHeight = 330;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        int cardX = modalX + 14;
        int cardY = modalY + 58;
        int cardW = modalWidth - 28;
        int visibleH = modalHeight - 92;

        if (mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= cardY && mouseY <= cardY + visibleH) {
            reportScrollOffset -= verticalAmount * 24;
            clampReportScroll();
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, GuiHelper.BG_OVERLAY);

        int modalWidth = 560;
        int modalHeight = 330;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        GuiHelper.renderPanel(graphics, modalX, modalY, modalWidth, modalHeight, 30, GuiHelper.HEADER_ACCENT_ADMIN);

        // Header Title
        graphics.text(this.font, Component.literal("§c§lSky§4§lReview §8| §fReview Reports"), modalX + 14, modalY + 9, GuiHelper.TEXT_TITLE, true);

        int rawCount = (int) RatingStorage.getPendingReports().stream().filter(r -> !r.isAboutMe()).count();
        String badge = rawCount > 0 ? "§c" + rawCount + " Pending" : "§a0 Pending";
        graphics.text(this.font, Component.literal(badge), modalX + modalWidth - 14 - this.font.width(badge), modalY + 9, GuiHelper.COLOR_RED, true);

        if (!OwnerLock.isOwner()) {
            graphics.text(this.font, Component.literal("§cACCESS DENIED: Owner Privileges Required"), modalX + 140, modalY + 140, GuiHelper.COLOR_RED, true);
            super.extractRenderState(graphics, mouseX, mouseY, delta);
            return;
        }

        List<GroupedReport> groupedList = getFilteredGroupedReports();

        if (groupedList.isEmpty()) {
            String emptyMsg = rawCount == 0 ? "§a✔ No pending review reports." : "§7No reports match current search / filter.";
            int emX = modalX + (modalWidth - this.font.width(emptyMsg)) / 2;
            graphics.text(this.font, Component.literal(emptyMsg), emX, modalY + 140, GuiHelper.COLOR_GREEN, true);
        } else {
            int cardX = modalX + 14;
            int cardY = modalY + 58;
            int cardW = modalWidth - 28;
            int cardH = 80;
            int visibleH = modalHeight - 92;

            int startIndex = Math.max(0, (int) (reportScrollOffset / REPORT_ITEM_HEIGHT));
            int endIndex = Math.min(groupedList.size(), startIndex + (visibleH / REPORT_ITEM_HEIGHT) + 2);

            for (int i = startIndex; i < endIndex; i++) {
                GroupedReport group = groupedList.get(i);
                ReviewEntry r = group.getReview();
                int itemY = cardY + (i * REPORT_ITEM_HEIGHT) - (int) reportScrollOffset;

                if (itemY + cardH <= cardY || itemY >= cardY + visibleH) {
                    continue;
                }

                boolean hovered = mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= itemY && mouseY <= itemY + cardH;
                GuiHelper.renderCard(graphics, cardX, itemY, cardW, cardH, hovered);

                // Row 1: Target Player & Author
                String targetInfo = "§7Target: §6@" + group.getTargetPlayer() + " §8[" + r.getCategory() + " " + r.getSubTag() + "]  §8•  §7Reviewer: §f@" + r.getAuthor();
                graphics.text(this.font, Component.literal(targetInfo), cardX + 10, itemY + 6, GuiHelper.TEXT_PRIMARY, false);

                // Right Badge: Report Count & Stars
                String stars = GuiHelper.getFormattedStars(r.getStars());
                String countBadge = group.getReportCount() > 1 ? ("§c§l🔥 x" + group.getReportCount() + " Reports") : ("§e§l🚩 1 Report");
                int badgeW = this.font.width(countBadge);
                int starsW = this.font.width(stars);

                graphics.text(this.font, Component.literal(stars), cardX + cardW - starsW - badgeW - 16, itemY + 6, GuiHelper.COLOR_GOLD, false);
                graphics.text(this.font, Component.literal(countBadge), cardX + cardW - badgeW - 10, itemY + 6, GuiHelper.COLOR_RED, true);

                // Row 2: Review Comment
                String commentStr = "§7Review: §f\"" + r.getComment() + "\"";
                graphics.text(this.font, Component.literal(commentStr), cardX + 10, itemY + 22, GuiHelper.TEXT_SECONDARY, false);

                // Row 3: Reporters & Reasons summary
                StringBuilder repSummary = new StringBuilder("§cReporters: ");
                for (int repIdx = 0; repIdx < Math.min(group.getReports().size(), 3); repIdx++) {
                    ReportEntry rep = group.getReports().get(repIdx);
                    if (repIdx > 0) repSummary.append(", ");
                    repSummary.append("§b@").append(rep.getReporter()).append(" §8(").append(rep.getReason()).append(")");
                }
                if (group.getReports().size() > 3) {
                    repSummary.append(" §7+").append(group.getReports().size() - 3).append(" more");
                }
                graphics.text(this.font, Component.literal(repSummary.toString()), cardX + 10, itemY + 38, GuiHelper.COLOR_RED, false);

                // Row 4: Action Buttons
                // Button 1: 🗑 Delete Review
                int delX = cardX + 10;
                int delY = itemY + 54;
                boolean hDel = mouseX >= delX && mouseX <= delX + 90 && mouseY >= delY && mouseY <= delY + 18;
                int delBg = hDel ? 0xFFDC2626 : 0xFF7F1D1D;
                graphics.fill(delX - 1, delY - 1, delX + 91, delY + 19, hDel ? 0xFFEF4444 : GuiHelper.PANEL_BORDER);
                graphics.fill(delX, delY, delX + 90, delY + 18, delBg);
                graphics.text(this.font, Component.literal("🗑 Delete"), delX + 6, delY + 5, 0xFFFFFFFF, false);

                // Button 2: ✔ Dismiss Reports
                int disX = cardX + 108;
                int disY = itemY + 54;
                boolean hDis = mouseX >= disX && mouseX <= disX + 80 && mouseY >= disY && mouseY <= disY + 18;
                int disBg = hDis ? 0xFF334155 : 0xFF1E293B;
                graphics.fill(disX - 1, disY - 1, disX + 81, disY + 19, hDis ? 0xFF94A3B8 : GuiHelper.PANEL_BORDER);
                graphics.fill(disX, disY, disX + 80, disY + 18, disBg);
                graphics.text(this.font, Component.literal("✔ Dismiss (" + group.getReportCount() + ")"), disX + 5, disY + 5, 0xFFFFFFFF, false);

                // Button 3: 👤 View Profile
                int viewX = cardX + 196;
                int viewY = itemY + 54;
                boolean hView = mouseX >= viewX && mouseX <= viewX + 75 && mouseY >= viewY && mouseY <= viewY + 18;
                int viewBg = hView ? 0xFF0284C7 : 0xFF0369A1;
                graphics.fill(viewX - 1, viewY - 1, viewX + 76, viewY + 19, hView ? 0xFF38BDF8 : GuiHelper.PANEL_BORDER);
                graphics.fill(viewX, viewY, viewX + 75, viewY + 18, viewBg);
                graphics.text(this.font, Component.literal("👤 Profile"), viewX + 6, viewY + 5, 0xFFFFFFFF, false);

                // Button 4: 🚫 Block Author
                int blkX = cardX + 279;
                int blkY = itemY + 54;
                boolean hBlk = mouseX >= blkX && mouseX <= delX + 370 && mouseY >= blkY && mouseY <= blkY + 18;
                int blkBg = hBlk ? 0xFF991B1B : 0xFF450A0A;
                graphics.fill(blkX - 1, blkY - 1, blkX + 96, blkY + 19, hBlk ? 0xFFDC2626 : GuiHelper.PANEL_BORDER);
                graphics.fill(blkX, blkY, blkX + 95, blkY + 18, blkBg);
                graphics.text(this.font, Component.literal("🚫 Block Author"), blkX + 5, blkY + 5, 0xFFFF8888, false);
            }

            // Scrollbar indicator
            int maxScroll = Math.max(0, groupedList.size() * REPORT_ITEM_HEIGHT - visibleH);
            if (maxScroll > 0) {
                int scrollbarX = cardX + cardW + 2;
                int scrollbarY = cardY;
                int thumbH = Math.max(16, (int) ((float) visibleH / (groupedList.size() * REPORT_ITEM_HEIGHT) * visibleH));
                int thumbY = scrollbarY + (int) ((reportScrollOffset / maxScroll) * (visibleH - thumbH));

                graphics.fill(scrollbarX, scrollbarY, scrollbarX + 2, scrollbarY + visibleH, 0x50000000);
                graphics.fill(scrollbarX, thumbY, scrollbarX + 2, thumbY + thumbH, 0xFFAAAAAA);
            }
        }

        // Status message at bottom
        if (!statusMessage.isEmpty()) {
            graphics.text(this.font, Component.literal(statusMessage), modalX + 105, modalY + modalHeight - 20, GuiHelper.TEXT_PRIMARY, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!OwnerLock.isOwner()) return super.mouseClicked(event, doubleClick);

        double mouseX = event.x();
        double mouseY = event.y();

        int modalWidth = 560;
        int modalHeight = 330;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        List<GroupedReport> groupedList = getFilteredGroupedReports();
        int cardX = modalX + 14;
        int cardY = modalY + 58;
        int cardH = 80;
        int visibleH = modalHeight - 92;

        int startIndex = Math.max(0, (int) (reportScrollOffset / REPORT_ITEM_HEIGHT));
        int endIndex = Math.min(groupedList.size(), startIndex + (visibleH / REPORT_ITEM_HEIGHT) + 2);

        for (int i = startIndex; i < endIndex; i++) {
            GroupedReport group = groupedList.get(i);
            ReviewEntry r = group.getReview();
            int itemY = cardY + (i * REPORT_ITEM_HEIGHT) - (int) reportScrollOffset;

            if (itemY + cardH <= cardY || itemY >= cardY + visibleH) {
                continue;
            }

            // 1. Delete Review Button Click
            int delX = cardX + 10;
            int delY = itemY + 54;
            if (mouseX >= delX && mouseX <= delX + 90 && mouseY >= delY && mouseY <= delY + 18) {
                String reviewId = r.getId();
                for (PlayerRatingEntry p : LeaderboardScreen.getAllPlayers()) {
                    if (p.getName().equalsIgnoreCase(group.getTargetPlayer())) {
                        p.removeReview(reviewId);
                    }
                }
                LeaderboardScreen.recalculateAllRanks();

                // Remove all reports associated with this review
                for (ReportEntry rep : group.getReports()) {
                    RatingStorage.removeReport(rep.getReportId());
                }
                RatingStorage.deleteReviewAsync(reviewId);

                this.statusMessage = "§aDeleted review & cleared " + group.getReportCount() + " report(s)!";
                clampReportScroll();
                return true;
            }

            // 2. Dismiss Button Click
            int disX = cardX + 108;
            int disY = itemY + 54;
            if (mouseX >= disX && mouseX <= disX + 80 && mouseY >= disY && mouseY <= disY + 18) {
                for (ReportEntry rep : group.getReports()) {
                    RatingStorage.removeReport(rep.getReportId());
                }
                this.statusMessage = "§7Dismissed " + group.getReportCount() + " report(s).";
                clampReportScroll();
                return true;
            }

            // 3. View Profile Button Click
            int viewX = cardX + 196;
            int viewY = itemY + 54;
            if (mouseX >= viewX && mouseX <= viewX + 75 && mouseY >= viewY && mouseY <= viewY + 18) {
                PlayerRatingEntry targetEntry = null;
                for (PlayerRatingEntry p : LeaderboardScreen.getAllPlayers()) {
                    if (p.getName().equalsIgnoreCase(group.getTargetPlayer())) {
                        targetEntry = p;
                        break;
                    }
                }
                if (targetEntry == null) {
                    targetEntry = new PlayerRatingEntry(0, group.getTargetPlayer(), r.getCategory(), r.getStars(), 1, null);
                }
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new ProfileScreen(this, targetEntry));
                }
                return true;
            }

            // 4. Block Author Button Click
            int blkX = cardX + 279;
            int blkY = itemY + 54;
            if (mouseX >= blkX && mouseX <= blkX + 96 && mouseY >= blkY && mouseY <= blkY + 18) {
                String authorName = r.getAuthor();
                String reviewId = r.getId();

                for (PlayerRatingEntry p : LeaderboardScreen.getAllPlayers()) {
                    if (p.getName().equalsIgnoreCase(group.getTargetPlayer())) {
                        p.removeReview(reviewId);
                    }
                }
                LeaderboardScreen.recalculateAllRanks();

                for (ReportEntry rep : group.getReports()) {
                    RatingStorage.removeReport(rep.getReportId());
                }
                RatingStorage.deleteReviewAsync(reviewId);

                this.statusMessage = "§bRestricting @" + authorName + "...";
                RatingStorage.blockUserAsync(authorName, "Review report violation", success -> {
                    if (success) {
                        this.statusMessage = "§aRestricted @" + authorName + " from posting reviews!";
                    } else {
                        this.statusMessage = "§cDeleted review, but failed to restrict user on server.";
                    }
                });
                clampReportScroll();
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
