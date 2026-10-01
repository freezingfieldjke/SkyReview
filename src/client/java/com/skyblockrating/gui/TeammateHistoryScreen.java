package com.skyblockrating.gui;

import com.skyblockrating.storage.RatingStorage;
import com.skyblockrating.util.TeammateHistoryManager;
import com.skyblockrating.util.TeammateHistoryManager.TeammateRunInfo;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class TeammateHistoryScreen extends Screen {
    private final Screen parentScreen;
    private String activeCategory = "dungeons";
    private List<TeammateRunInfo> teammates = new ArrayList<>();
    private double scrollOffset = 0;
    private static final int ROW_HEIGHT = 28;

    private Button btnDungeons;
    private Button btnKuudra;
    private Button btnSafari;

    public TeammateHistoryScreen(Screen parentScreen) {
        this(parentScreen, "dungeons");
    }

    public TeammateHistoryScreen(Screen parentScreen, String initialCategory) {
        super(Component.literal("SkyReview - Recent Teammates"));
        this.parentScreen = parentScreen;
        if (initialCategory != null && !initialCategory.trim().isEmpty()) {
            String cat = initialCategory.toLowerCase().trim();
            if (cat.contains("kuudra")) this.activeCategory = "kuudra";
            else if (cat.contains("safari")) this.activeCategory = "safari";
            else this.activeCategory = "dungeons";
        }
    }

    @Override
    protected void init() {
        super.init();
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        this.clearWidgets();
        loadTeammates();

        int modalWidth = 520;
        int modalHeight = 320;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        int tabsY = modalY + 34;

        // Category Tab Buttons
        boolean isDungeons = activeCategory.equals("dungeons");
        this.btnDungeons = this.addRenderableWidget(
            Button.builder(Component.literal((isDungeons ? "§6§l" : "§7") + "⚔ Dungeons"), button -> selectCategory("dungeons"))
            .bounds(modalX + 14, tabsY, 86, 18)
            .build()
        );

        boolean isKuudra = activeCategory.equals("kuudra");
        this.btnKuudra = this.addRenderableWidget(
            Button.builder(Component.literal((isKuudra ? "§6§l" : "§7") + "🌋 Kuudra"), button -> selectCategory("kuudra"))
            .bounds(modalX + 104, tabsY, 86, 18)
            .build()
        );

        boolean isSafari = activeCategory.equals("safari");
        this.btnSafari = this.addRenderableWidget(
            Button.builder(Component.literal((isSafari ? "§6§l" : "§7") + "🌿 Safari"), button -> selectCategory("safari"))
            .bounds(modalX + 194, tabsY, 86, 18)
            .build()
        );

        // Bottom Controls
        int bottomY = modalY + modalHeight - 27;

        // 1. Back button
        this.addRenderableWidget(
            Button.builder(Component.literal("← Back"), button -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(this.parentScreen != null ? this.parentScreen : new LeaderboardScreen());
                }
            })
            .bounds(modalX + 14, bottomY, 70, 19)
            .build()
        );

        // 2. Refresh button
        this.addRenderableWidget(
            Button.builder(Component.literal("🔄 Refresh"), button -> {
                loadTeammates();
            })
            .bounds(modalX + 90, bottomY, 75, 19)
            .build()
        );

        // 3. Clear History button
        this.addRenderableWidget(
            Button.builder(Component.literal("🗑 Clear"), button -> {
                TeammateHistoryManager.clearRecentRuns(activeCategory);
                loadTeammates();
            })
            .bounds(modalX + modalWidth - 84, bottomY, 70, 19)
            .build()
        );
    }

    private void selectCategory(String category) {
        this.activeCategory = category;
        this.scrollOffset = 0;
        rebuildWidgets();
    }

    private void loadTeammates() {
        this.teammates = TeammateHistoryManager.getRecentTeammatesLastThreeRuns(activeCategory);
        clampScroll();
    }

    private void clampScroll() {
        int modalHeight = 320;
        int visibleH = modalHeight - 92;
        int maxScroll = Math.max(0, teammates.size() * ROW_HEIGHT - visibleH);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        if (scrollOffset < 0) scrollOffset = 0;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int modalWidth = 520;
        int modalHeight = 320;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;
        int tableY = modalY + 56;
        int visibleH = modalHeight - 92;

        if (mouseX >= modalX && mouseX <= modalX + modalWidth && mouseY >= tableY && mouseY <= tableY + visibleH) {
            scrollOffset -= verticalAmount * 22;
            clampScroll();
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // Dark background overlay
        graphics.fill(0, 0, this.width, this.height, GuiHelper.BG_OVERLAY);

        int modalWidth = 520;
        int modalHeight = 320;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        // Outer container
        GuiHelper.renderPanel(graphics, modalX, modalY, modalWidth, modalHeight, 30, GuiHelper.HEADER_ACCENT);

        // Header
        graphics.text(this.font, Component.literal("§6§lSky§e§lReview §8| §fRecent Teammates"), modalX + 14, modalY + 9, GuiHelper.TEXT_TITLE, true);

        String categoryTitle = activeCategory.substring(0, 1).toUpperCase() + activeCategory.substring(1);
        String countBadge = "§7" + categoryTitle + ": §e" + teammates.size() + " players §8(3 runs)";
        int countW = this.font.width(countBadge);
        graphics.text(this.font, Component.literal(countBadge), modalX + modalWidth - countW - 14, modalY + 38, GuiHelper.TEXT_MUTED, false);

        // Content Area
        int tableX = modalX + 14;
        int tableY = modalY + 56;
        int tableW = modalWidth - 28;
        int tableH = modalHeight - 92;

        // Inner table background container
        graphics.fill(tableX - 1, tableY - 1, tableX + tableW + 1, tableY + tableH + 1, GuiHelper.PANEL_BORDER);
        graphics.fill(tableX, tableY, tableX + tableW, tableY + tableH, GuiHelper.CARD_BG_ALT);

        if (teammates.isEmpty()) {
            String msg1 = "No recent " + categoryTitle + " teammates";
            String msg2 = "Party members from your last 3 " + categoryTitle + " runs appear here.";
            String msg3 = "Complete a " + categoryTitle + " run to record your team!";

            int m1W = this.font.width(msg1);
            int m2W = this.font.width(msg2);
            int m3W = this.font.width(msg3);

            int centerY = tableY + (tableH / 2) - 24;
            graphics.text(this.font, Component.literal("§e" + msg1), tableX + (tableW - m1W) / 2, centerY, GuiHelper.COLOR_YELLOW, true);
            graphics.text(this.font, Component.literal("§7" + msg2), tableX + (tableW - m2W) / 2, centerY + 18, GuiHelper.TEXT_SECONDARY, false);
            graphics.text(this.font, Component.literal("§8" + msg3), tableX + (tableW - m3W) / 2, centerY + 32, GuiHelper.TEXT_MUTED, false);
        } else {
            graphics.enableScissor(tableX, tableY, tableX + tableW, tableY + tableH);

            int maxVisibleRows = tableH / ROW_HEIGHT;
            int startIndex = Math.max(0, (int) (scrollOffset / ROW_HEIGHT));
            int endIndex = Math.min(teammates.size(), startIndex + maxVisibleRows + 2);

            for (int i = startIndex; i < endIndex; i++) {
                TeammateRunInfo tm = teammates.get(i);
                int rowY = tableY + (i * ROW_HEIGHT) - (int) scrollOffset;

                if (rowY + ROW_HEIGHT <= tableY || rowY >= tableY + tableH) {
                    continue;
                }

                boolean hovered = mouseX >= tableX && mouseX <= tableX + tableW && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2;
                int rowBg = hovered ? GuiHelper.CARD_HOVER : ((i % 2 == 0) ? GuiHelper.CARD_BG : GuiHelper.CARD_BG_ALT);

                graphics.fill(tableX, rowY, tableX + tableW, rowY + ROW_HEIGHT - 2, rowBg);

                // Run badge
                String runBadge = "§eR#" + tm.getRunNumber();
                int badgeColor = tm.getRunNumber() == 1 ? GuiHelper.COLOR_GOLD : (tm.getRunNumber() == 2 ? GuiHelper.COLOR_BLUE : GuiHelper.TEXT_MUTED);
                graphics.text(this.font, Component.literal(runBadge), tableX + 8, rowY + 7, badgeColor, tm.getRunNumber() == 1);

                // Player IGN & Custom Prefix & Dungeon Class (dungeons only)
                int nameStartX = tableX + 42;
                if (activeCategory.equals("dungeons") && tm.getDungeonClass() != null && !tm.getDungeonClass().isEmpty()) {
                    String classBadge = TeammateHistoryManager.getClassColor(tm.getDungeonClass()) + "[" + tm.getDungeonClass() + "] ";
                    graphics.text(this.font, Component.literal(classBadge), nameStartX, rowY + 7, 0xFFFFFFFF, false);
                    nameStartX += this.font.width(classBadge);
                }
                GuiHelper.renderPlayerWithPrefix(graphics, this.font, tm.getName(), RatingStorage.getUserPrefix(tm.getName()), nameStartX, rowY + 7, false);

                // Rating stars & reviews count
                PlayerRatingEntry entry = RatingStorage.getPlayerEntry(tm.getName());
                String starsStr;
                if (entry != null && entry.getReviewsCount() > 0) {
                    starsStr = GuiHelper.getFormattedStars(entry.getRating()) + " §7(" + entry.getReviewsCount() + " rev)";
                } else {
                    starsStr = "§8☆☆☆☆☆ §7(No rev)";
                }
                graphics.text(this.font, Component.literal(starsStr), tableX + 225, rowY + 7, GuiHelper.COLOR_YELLOW, false);

                // Action 1: Rate Button Pill
                int rateW = 54;
                int rateH = 16;
                int rateX = tableX + tableW - 126;
                int rateY = rowY + (ROW_HEIGHT - 2 - rateH) / 2;
                boolean hRate = mouseX >= rateX && mouseX <= rateX + rateW && mouseY >= rateY && mouseY <= rateY + rateH;
                int rateBg = hRate ? 0xFF0284C7 : 0xFF1E293B;
                graphics.fill(rateX - 1, rateY - 1, rateX + rateW + 1, rateY + rateH + 1, hRate ? 0xFF38BDF8 : GuiHelper.PANEL_BORDER);
                graphics.fill(rateX, rateY, rateX + rateW, rateY + rateH, rateBg);
                graphics.text(this.font, Component.literal("⭐ Rate"), rateX + 8, rateY + 4, 0xFFFFFFFF, false);

                // Action 2: Profile Button Pill
                int profW = 56;
                int profH = 16;
                int profX = tableX + tableW - 64;
                int profY = rowY + (ROW_HEIGHT - 2 - profH) / 2;
                boolean hProf = mouseX >= profX && mouseX <= profX + profW && mouseY >= profY && mouseY <= profY + profH;
                int profBg = hProf ? 0xFF475569 : 0xFF1E293B;
                graphics.fill(profX - 1, profY - 1, profX + profW + 1, profY + profH + 1, hProf ? 0xFF94A3B8 : GuiHelper.PANEL_BORDER);
                graphics.fill(profX, profY, profX + profW, profY + profH, profBg);
                graphics.text(this.font, Component.literal("Profile →"), profX + 5, profY + 4, 0xFFFFFFFF, false);
            }

            graphics.disableScissor();
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        int modalWidth = 520;
        int modalHeight = 320;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;
        int tableX = modalX + 14;
        int tableY = modalY + 56;
        int tableW = modalWidth - 28;
        int tableH = modalHeight - 92;

        if (mouseX >= tableX && mouseX <= tableX + tableW && mouseY >= tableY && mouseY <= tableY + tableH) {
            int maxVisibleRows = tableH / ROW_HEIGHT;
            int startIndex = Math.max(0, (int) (scrollOffset / ROW_HEIGHT));
            int endIndex = Math.min(teammates.size(), startIndex + maxVisibleRows + 2);

            for (int i = startIndex; i < endIndex; i++) {
                TeammateRunInfo tm = teammates.get(i);
                int rowY = tableY + (i * ROW_HEIGHT) - (int) scrollOffset;

                if (rowY + ROW_HEIGHT <= tableY || rowY >= tableY + tableH) {
                    continue;
                }

                int rateW = 54;
                int rateH = 16;
                int rateX = tableX + tableW - 126;
                int rateY = rowY + (ROW_HEIGHT - 2 - rateH) / 2;

                int profW = 56;
                int profH = 16;
                int profX = tableX + tableW - 64;
                int profY = rowY + (ROW_HEIGHT - 2 - profH) / 2;

                // Rate button click
                if (mouseX >= rateX && mouseX <= rateX + rateW && mouseY >= rateY && mouseY <= rateY + rateH) {
                    if (this.minecraft != null) {
                        if (RatingStorage.isCurrentClientBlocked()) {
                            if (this.minecraft.player != null) {
                                this.minecraft.player.sendSystemMessage(Component.literal("§c[SkyReview] Your account is restricted from posting reviews."));
                            }
                            return true;
                        }
                        String selfName = (this.minecraft.getUser() != null) ? this.minecraft.getUser().getName() : "";
                        if (!selfName.isEmpty() && tm.getName().equalsIgnoreCase(selfName)) {
                            if (this.minecraft.player != null) {
                                this.minecraft.player.sendSystemMessage(Component.literal("§c[SkyReview] r u srsly?"));
                            }
                            return true;
                        }
                        String rateCat = activeCategory.equalsIgnoreCase("kuudra") ? "Kuudra" : (activeCategory.equalsIgnoreCase("safari") ? "Safari" : "Dungeons");
                        this.minecraft.setScreen(new RatePlayerScreen(tm.getName(), rateCat));
                    }
                    return true;
                }

                // Profile button click or row click
                if ((mouseX >= profX && mouseX <= profX + profW && mouseY >= profY && mouseY <= profY + profH) ||
                    (mouseX >= tableX && mouseX <= tableX + tableW && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2)) {
                    if (this.minecraft != null) {
                        PlayerRatingEntry entry = RatingStorage.getPlayerEntry(tm.getName());
                        if (entry == null) {
                            String rateCat = activeCategory.equalsIgnoreCase("kuudra") ? "Kuudra" : (activeCategory.equalsIgnoreCase("safari") ? "Safari" : "Dungeons");
                            entry = new PlayerRatingEntry(0, tm.getName(), rateCat, 0.0, 0, null);
                        }
                        this.minecraft.setScreen(new ProfileScreen(this, entry));
                    }
                    return true;
                }
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
