package com.skyblockrating.gui;

import com.skyblockrating.data.ReviewEntry;
import com.skyblockrating.storage.RatingStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MyReviewsScreen extends Screen {
    private final Screen parentScreen;
    private List<ReviewEntry> allReviews = new ArrayList<>();
    private List<ReviewEntry> displayedReviews = new ArrayList<>();
    private double scrollOffset = 0;

    private static final int GRID_COLS = 3;
    private static final int GAP_X = 8;
    private static final int GAP_Y = 8;
    private static final int CARD_HEIGHT = 44;
    private static final int ROW_SPACING = CARD_HEIGHT + GAP_Y; // 52px

    private static final String[] CATEGORIES = {"All", "Dungeons", "Kuudra", "Safari", "Other"};
    private String selectedCategory = "All";

    public enum SortMode {
        NEWEST("Sort: Newest"),
        TOP_RATED("Sort: Top (5★→1★)"),
        LOWEST_RATED("Sort: Lowest (1★→5★)"),
        MOST_LIKED("Sort: Most Liked"),
        OLDEST("Sort: Oldest");

        private final String label;
        SortMode(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    private SortMode currentSort = SortMode.NEWEST;
    private String statusMessage = "";

    public MyReviewsScreen(Screen parentScreen) {
        super(Component.literal("My Reviews"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();
        loadReviews();

        int modalWidth = 540;
        int modalHeight = 320;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        // Back button (bottom left)
        int backBtnY = modalY + modalHeight - 27;
        this.addRenderableWidget(
            Button.builder(Component.literal("← Back"), btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(this.parentScreen);
                }
            })
            .bounds(modalX + 16, backBtnY, 80, 20)
            .build()
        );

        // Sort Mode Toggle Button (top right header)
        int sortBtnW = 125;
        int sortBtnH = 18;
        int sortBtnX = modalX + modalWidth - 16 - sortBtnW;
        int sortBtnY = modalY + 6;

        this.addRenderableWidget(
            Button.builder(Component.literal(currentSort.getLabel()), button -> {
                currentSort = switch (currentSort) {
                    case NEWEST -> SortMode.TOP_RATED;
                    case TOP_RATED -> SortMode.LOWEST_RATED;
                    case LOWEST_RATED -> SortMode.MOST_LIKED;
                    case MOST_LIKED -> SortMode.OLDEST;
                    case OLDEST -> SortMode.NEWEST;
                };
                button.setMessage(Component.literal(currentSort.getLabel()));
                sortReviews();
                scrollOffset = 0;
            })
            .bounds(sortBtnX, sortBtnY, sortBtnW, sortBtnH)
            .build()
        );

        // Category Tab Buttons (below header)
        int tabStartX = modalX + 16;
        int tabW = 76;
        int tabGap = 6;
        int tabY = modalY + 34;

        for (int i = 0; i < CATEGORIES.length; i++) {
            final String cat = CATEGORIES[i];
            boolean isSelected = cat.equalsIgnoreCase(selectedCategory);
            String label = (isSelected ? "§6§l" : "§7") + cat;

            this.addRenderableWidget(
                Button.builder(Component.literal(label), button -> {
                    this.selectedCategory = cat;
                    this.scrollOffset = 0;
                    applyFilterAndSort();
                    this.rebuildWidgets();
                })
                .bounds(tabStartX + i * (tabW + tabGap), tabY, tabW, 18)
                .build()
            );
        }
    }

    private void loadReviews() {
        this.allReviews = new ArrayList<>(RatingStorage.getMyReviews());
        applyFilterAndSort();
    }

    private void applyFilterAndSort() {
        this.displayedReviews = new ArrayList<>();
        for (ReviewEntry r : allReviews) {
            if (selectedCategory.equalsIgnoreCase("All")) {
                displayedReviews.add(r);
            } else if (r.getCategory() != null && r.getCategory().equalsIgnoreCase(selectedCategory)) {
                displayedReviews.add(r);
            } else if (selectedCategory.equalsIgnoreCase("Other")) {
                if (r.getCategory() == null || r.getCategory().equalsIgnoreCase("Other")
                        || (!r.getCategory().equalsIgnoreCase("Dungeons")
                            && !r.getCategory().equalsIgnoreCase("Kuudra")
                            && !r.getCategory().equalsIgnoreCase("Safari"))) {
                    displayedReviews.add(r);
                }
            }
        }
        sortReviews();
    }

    private void sortReviews() {
        if (displayedReviews == null || displayedReviews.isEmpty()) return;
        switch (currentSort) {
            case TOP_RATED -> displayedReviews.sort((a, b) -> {
                int cmp = Double.compare(b.getStars(), a.getStars());
                if (cmp != 0) return cmp;
                int likeCmp = Integer.compare(b.getLikes(), a.getLikes());
                if (likeCmp != 0) return likeCmp;
                return Long.compare(b.getTimestamp(), a.getTimestamp());
            });
            case LOWEST_RATED -> displayedReviews.sort((a, b) -> {
                int cmp = Double.compare(a.getStars(), b.getStars());
                if (cmp != 0) return cmp;
                int likeCmp = Integer.compare(b.getLikes(), a.getLikes());
                if (likeCmp != 0) return likeCmp;
                return Long.compare(b.getTimestamp(), a.getTimestamp());
            });
            case MOST_LIKED -> displayedReviews.sort(Comparator.comparingInt(ReviewEntry::getLikes).reversed()
                    .thenComparing(Comparator.comparingLong(ReviewEntry::getTimestamp).reversed()));
            case OLDEST -> displayedReviews.sort(Comparator.comparingLong(ReviewEntry::getTimestamp));
            default -> displayedReviews.sort(Comparator.comparingLong(ReviewEntry::getTimestamp).reversed());
        }
        clampScroll();
    }

    private void clampScroll() {
        int visibleH = 320 - 89;
        int totalRows = (displayedReviews.size() + GRID_COLS - 1) / GRID_COLS;
        int maxScroll = Math.max(0, totalRows * ROW_SPACING - visibleH);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        if (scrollOffset < 0) scrollOffset = 0;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int modalWidth = 540;
        int modalHeight = 320;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        int listX = modalX + 16;
        int listY = modalY + 56;
        int listW = modalWidth - 32;
        int listH = modalHeight - 89;

        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
            scrollOffset -= verticalAmount * 24;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        int modalWidth = 540;
        int modalHeight = 320;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        int listX = modalX + 16;
        int listY = modalY + 56;
        int listW = modalWidth - 32;
        int visibleH = modalHeight - 89;
        int cardWidth = (listW - (GRID_COLS - 1) * GAP_X) / GRID_COLS;

        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + visibleH) {
            for (int i = 0; i < displayedReviews.size(); i++) {
                int col = i % GRID_COLS;
                int row = i / GRID_COLS;
                int cardX = listX + col * (cardWidth + GAP_X);
                int cardY = listY + row * ROW_SPACING - (int) scrollOffset;

                if (cardY + CARD_HEIGHT <= listY || cardY >= listY + visibleH) continue;

                if (mouseX >= cardX && mouseX <= cardX + cardWidth && mouseY >= cardY && mouseY <= cardY + CARD_HEIGHT) {
                    openTargetProfile(displayedReviews.get(i));
                    return true;
                }
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    private void openTargetProfile(ReviewEntry review) {
        if (this.minecraft == null || review == null) return;
        String target = review.getTargetPlayer();
        String targetUuid = review.getTargetUuid();
        String cat = review.getCategory();

        PlayerRatingEntry entry = RatingStorage.getPlayerEntry(target, cat);
        if (entry == null && targetUuid != null && !targetUuid.isEmpty()) {
            entry = RatingStorage.getPlayerEntry(targetUuid, cat);
        }
        if (entry == null) {
            entry = new PlayerRatingEntry(0, targetUuid, target, (cat != null ? cat : "General"), review.getStars(), 1, null);
        }
        this.minecraft.setScreen(new ProfileScreen(this, entry, review.getId()));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, GuiHelper.BG_OVERLAY);

        int modalWidth = 540;
        int modalHeight = 320;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        GuiHelper.renderPanel(graphics, modalX, modalY, modalWidth, modalHeight, 30, GuiHelper.HEADER_ACCENT);

        // Header Title
        graphics.text(this.font, Component.literal("§6§lSky§e§lReview §8| §fMy Reviews"), modalX + 16, modalY + 9, GuiHelper.TEXT_TITLE, true);

        int sortBtnW = 125;
        int sortBtnX = modalX + modalWidth - 16 - sortBtnW;
        String countStr = "§7Total: §e" + displayedReviews.size();
        if (!selectedCategory.equalsIgnoreCase("All")) {
            countStr += " §8(" + allReviews.size() + ")";
        }
        int countW = this.font.width(countStr);
        graphics.text(this.font, Component.literal(countStr), sortBtnX - countW - 10, modalY + 11, GuiHelper.TEXT_SECONDARY, false);

        int listX = modalX + 16;
        int listY = modalY + 56;
        int listW = modalWidth - 32;
        int visibleH = modalHeight - 89;
        int cardWidth = (listW - (GRID_COLS - 1) * GAP_X) / GRID_COLS;

        if (displayedReviews.isEmpty()) {
            int emptyY = listY + 70;
            String msg1 = selectedCategory.equalsIgnoreCase("All")
                    ? "You haven't written any reviews yet!"
                    : "No reviews written in category '" + selectedCategory + "'!";
            String msg2 = "Rate teammates using /sr rate <player> after completing runs.";
            graphics.text(this.font, Component.literal("§7" + msg1), modalX + (modalWidth - this.font.width(msg1)) / 2, emptyY, GuiHelper.TEXT_MUTED, false);
            graphics.text(this.font, Component.literal("§8" + msg2), modalX + (modalWidth - this.font.width(msg2)) / 2, emptyY + 16, GuiHelper.TEXT_MUTED, false);
        } else {
            graphics.enableScissor(listX, listY, listX + listW, listY + visibleH);

            for (int i = 0; i < displayedReviews.size(); i++) {
                int col = i % GRID_COLS;
                int row = i / GRID_COLS;
                int cardX = listX + col * (cardWidth + GAP_X);
                int cardY = listY + row * ROW_SPACING - (int) scrollOffset;

                if (cardY + CARD_HEIGHT <= listY || cardY >= listY + visibleH) continue;

                boolean cardHovered = mouseX >= cardX && mouseX <= cardX + cardWidth && mouseY >= cardY && mouseY <= cardY + CARD_HEIGHT && mouseY >= listY && mouseY <= listY + visibleH;
                GuiHelper.renderCard(graphics, cardX, cardY, cardWidth, CARD_HEIGHT, cardHovered);

                if (cardHovered) {
                    // Subtle glowing gold bottom accent on hover
                    graphics.fill(cardX + 2, cardY + CARD_HEIGHT - 2, cardX + cardWidth - 2, cardY + CARD_HEIGHT - 1, 0xFFFFAA00);
                }

                ReviewEntry r = displayedReviews.get(i);

                // --- Row 1: Target Player Name & Category Tag ---
                String cat = r.getCategory();
                if (r.getSubTag() != null && !r.getSubTag().isEmpty()) cat = r.getSubTag();
                String catBadge = "§8[" + cat + "]";
                int catW = this.font.width(catBadge);
                graphics.text(this.font, Component.literal(catBadge), cardX + cardWidth - catW - 6, cardY + 7, GuiHelper.TEXT_MUTED, false);

                // Player Name with prefix on top left
                GuiHelper.renderPlayerWithPrefix(graphics, this.font, r.getTargetPlayer(), RatingStorage.getUserPrefix(r.getTargetPlayer()), cardX + 8, cardY + 7, true);

                // --- Row 2: Star Rating, Likes & Date ---
                String stars = GuiHelper.getFormattedStars(r.getStars());
                graphics.text(this.font, Component.literal(stars), cardX + 8, cardY + 24, GuiHelper.COLOR_GOLD, false);

                int starsW = this.font.width(stars);
                if (r.getLikes() > 0) {
                    String likes = "§b👍" + r.getLikes();
                    graphics.text(this.font, Component.literal(likes), cardX + 8 + starsW + 4, cardY + 24, 0xFF38BDF8, false);
                }

                String timeStr = formatTime(r.getTimestamp());
                if (!timeStr.isEmpty()) {
                    int timeW = this.font.width(timeStr);
                    graphics.text(this.font, Component.literal("§8" + timeStr), cardX + cardWidth - timeW - 6, cardY + 24, GuiHelper.TEXT_MUTED, false);
                }
            }

            graphics.disableScissor();

            // Scrollbar indicator
            int totalRows = (displayedReviews.size() + GRID_COLS - 1) / GRID_COLS;
            int totalContentHeight = totalRows * ROW_SPACING;
            if (totalContentHeight > visibleH) {
                int scrollbarX = listX + listW + 4;
                int scrollbarW = 3;
                graphics.fill(scrollbarX, listY, scrollbarX + scrollbarW, listY + visibleH, 0x33000000);
                float scrollRatio = (float) scrollOffset / (totalContentHeight - visibleH);
                int thumbH = Math.max(20, (int) ((float) visibleH / totalContentHeight * visibleH));
                int thumbY = listY + (int) (scrollRatio * (visibleH - thumbH));
                graphics.fill(scrollbarX, thumbY, scrollbarX + scrollbarW, thumbY + thumbH, 0x88FFAA00);
            }
        }

        // Status message if any
        if (!statusMessage.isEmpty()) {
            graphics.text(this.font, Component.literal(statusMessage), modalX + 110, modalY + modalHeight - 22, GuiHelper.COLOR_YELLOW, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private static String formatTime(long timestamp) {
        if (timestamp <= 0) return "";
        long diff = System.currentTimeMillis() - timestamp;
        if (diff < 0) diff = 0;
        if (diff < 60_000L) {
            return "just now";
        } else if (diff < 3600_000L) {
            return (diff / 60_000L) + "m ago";
        } else if (diff < 86400_000L) {
            return (diff / 3600_000L) + "h ago";
        } else if (diff < 7L * 86400_000L) {
            return (diff / 86400_000L) + "d ago";
        } else {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yy");
            return sdf.format(new java.util.Date(timestamp));
        }
    }
}
