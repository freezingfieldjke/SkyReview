package com.skyblockrating.gui;

import com.skyblockrating.data.ReportEntry;
import com.skyblockrating.data.ReviewEntry;
import com.skyblockrating.storage.RatingStorage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class ReportDialogScreen extends Screen {
    private final Screen parentScreen;
    private final String targetPlayer;
    private final ReviewEntry review;
    private EditBox reasonBox;
    private String statusMessage = "";

    public ReportDialogScreen(Screen parentScreen, String targetPlayer, ReviewEntry review) {
        super(Component.literal("Report Review"));
        this.parentScreen = parentScreen;
        this.targetPlayer = targetPlayer;
        this.review = review;
    }

    @Override
    protected void init() {
        super.init();

        int cardWidth = 330;
        int cardHeight = 220;
        int cardX = (this.width - cardWidth) / 2;
        int cardY = (this.height - cardHeight) / 2;

        int presetY = cardY + 54;
        int btnW = 72;

        // Preset Quick Reasons Buttons
        this.addRenderableWidget(
            Button.builder(Component.literal("Spam/Ad"), button -> {
                if (this.reasonBox != null) this.reasonBox.setValue("Spam / Advertisement");
            })
            .bounds(cardX + 16, presetY, btnW, 18)
            .build()
        );

        this.addRenderableWidget(
            Button.builder(Component.literal("Toxic"), button -> {
                if (this.reasonBox != null) this.reasonBox.setValue("Insults / Toxicity");
            })
            .bounds(cardX + 91, presetY, btnW, 18)
            .build()
        );

        this.addRenderableWidget(
            Button.builder(Component.literal("Fake"), button -> {
                if (this.reasonBox != null) this.reasonBox.setValue("Fake / False review");
            })
            .bounds(cardX + 166, presetY, btnW, 18)
            .build()
        );

        this.addRenderableWidget(
            Button.builder(Component.literal("Clear"), button -> {
                if (this.reasonBox != null) this.reasonBox.setValue("");
            })
            .bounds(cardX + 241, presetY, 73, 18)
            .build()
        );

        // EditBox
        this.reasonBox = new EditBox(this.font, cardX + 16, cardY + 80, cardWidth - 32, 20, Component.literal("Reason"));
        this.reasonBox.setMaxLength(150);
        this.reasonBox.setHint(Component.literal("Reason for report (Max 150 chars)..."));
        this.addRenderableWidget(this.reasonBox);
        this.setInitialFocus(this.reasonBox);

        int buttonY = cardY + cardHeight - 28;

        // Submit Button
        this.addRenderableWidget(
            Button.builder(Component.literal("🚩 Submit Report"), button -> {
                String reason = this.reasonBox.getValue().trim();
                if (reason.isEmpty()) {
                    this.statusMessage = "§cPlease specify a reason!";
                    return;
                }

                // 1. Check cooldown (1 minute)
                long cdSec = com.skyblockrating.config.ReportRateLimitManager.getRemainingCooldownSeconds();
                if (cdSec > 0) {
                    this.statusMessage = String.format("§eCooldown! Please wait %ds before reporting again.", cdSec);
                    return;
                }

                // 2. Check 24-hour limit (8 reports max)
                if (!com.skyblockrating.config.ReportRateLimitManager.canSubmitReport()) {
                    long waitMs = com.skyblockrating.config.ReportRateLimitManager.getTimeUntilNextAvailableReportMs();
                    long hours = waitMs / (60 * 60 * 1000L);
                    long mins = (waitMs % (60 * 60 * 1000L)) / (60 * 1000L);
                    this.statusMessage = String.format("§cDaily limit reached (8/8)! Next in %dh %dm.", hours, mins);
                    return;
                }

                if (!com.skyblockrating.auth.SessionAuthManager.isLicensedUser()) {
                    this.statusMessage = com.skyblockrating.auth.SessionAuthManager.AUTH_REQUIRED_MODAL;
                    if (this.minecraft != null && this.minecraft.player != null) {
                        this.minecraft.player.sendSystemMessage(Component.literal(com.skyblockrating.auth.SessionAuthManager.AUTH_REQUIRED_MSG));
                    }
                    return;
                }

                String reporter = (this.minecraft != null && this.minecraft.getUser() != null) ? this.minecraft.getUser().getName() : "Player";

                ReportEntry newReport = new ReportEntry(reporter, targetPlayer, review, reason);
                if (!RatingStorage.getPendingReports().contains(newReport)) {
                    RatingStorage.getPendingReports().add(newReport);
                }

                this.statusMessage = "§bSending report...";

                RatingStorage.sendReportAsync(
                    newReport,
                    () -> {
                        com.skyblockrating.config.ReportRateLimitManager.recordReportSubmission();
                        int remaining = com.skyblockrating.config.ReportRateLimitManager.getRemainingDailyReports();
                        this.statusMessage = "§aReport submitted! §7(" + remaining + "/8 left today)";
                    },
                    errDetail -> {
                        this.statusMessage = "§c" + errDetail;
                    }
                );
            })
            .bounds(cardX + 16, buttonY, 140, 20)
            .build()
        );

        // Cancel Button
        this.addRenderableWidget(
            Button.builder(Component.literal("✕ Cancel"), button -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(this.parentScreen);
                }
            })
            .bounds(cardX + cardWidth - 16 - 110, buttonY, 110, 20)
            .build()
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, GuiHelper.BG_OVERLAY);

        int cardWidth = 330;
        int cardHeight = 220;
        int cardX = (this.width - cardWidth) / 2;
        int cardY = (this.height - cardHeight) / 2;

        GuiHelper.renderPanel(graphics, cardX, cardY, cardWidth, cardHeight, 30, GuiHelper.COLOR_ORANGE);

        graphics.text(this.font, Component.literal("§6§lReport Review: §f@" + review.getAuthor()), cardX + 14, cardY + 9, GuiHelper.TEXT_TITLE, true);
        boolean isSafari = review.getCategory() != null && review.getCategory().equalsIgnoreCase("Safari");
        String tagDisplay = isSafari ? "[Safari]" : "[" + review.getCategory() + (!review.getSubTag().isEmpty() ? " " + review.getSubTag() : "") + "]";
        graphics.text(this.font, Component.literal("§7Target: §e@" + targetPlayer + " §8" + tagDisplay), cardX + 16, cardY + 38, GuiHelper.TEXT_SECONDARY, false);

        int remainingToday = com.skyblockrating.config.ReportRateLimitManager.getRemainingDailyReports();
        graphics.text(this.font, Component.literal("§8Daily reports left: " + remainingToday + "/8"), cardX + cardWidth - 120, cardY + 38, GuiHelper.TEXT_MUTED, false);

        if (!statusMessage.isEmpty()) {
            graphics.text(this.font, Component.literal(statusMessage), cardX + 16, cardY + 114, GuiHelper.TEXT_PRIMARY, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        boolean inBox = (this.reasonBox != null && mouseX >= this.reasonBox.getX() && mouseX <= this.reasonBox.getX() + this.reasonBox.getWidth()
                && mouseY >= this.reasonBox.getY() && mouseY <= this.reasonBox.getY() + this.reasonBox.getHeight());

        if (inBox && this.reasonBox.isFocused()) {
            this.reasonBox.setFocused(false);
            this.setFocused(null);
            return true;
        }

        boolean handled = super.mouseClicked(event, doubleClick);

        if (!inBox && this.reasonBox != null && this.reasonBox.isFocused()) {
            this.reasonBox.setFocused(false);
            if (this.getFocused() == this.reasonBox) {
                this.setFocused(null);
            }
        }

        return handled;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
