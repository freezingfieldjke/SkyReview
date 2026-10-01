package com.skyblockrating.gui;

import com.skyblockrating.config.ReportRateLimitManager;
import com.skyblockrating.data.ReportEntry;
import com.skyblockrating.storage.RatingStorage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class ReportAboutMeDialogScreen extends Screen {
    private final Screen parentScreen;
    private final String targetPlayer;
    private final String reportedBio;
    private final String reportedDiscord;
    private EditBox reasonBox;
    private String statusMessage = "";

    public ReportAboutMeDialogScreen(Screen parentScreen, String targetPlayer, String reportedBio, String reportedDiscord) {
        super(Component.literal("Report About Me"));
        this.parentScreen = parentScreen;
        this.targetPlayer = targetPlayer;
        this.reportedBio = reportedBio != null ? reportedBio : "";
        this.reportedDiscord = reportedDiscord != null ? reportedDiscord : "";
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
            Button.builder(Component.literal("Offensive"), button -> {
                if (this.reasonBox != null) this.reasonBox.setValue("Inappropriate / Offensive bio");
            })
            .bounds(cardX + 16, presetY, btnW, 18)
            .build()
        );

        this.addRenderableWidget(
            Button.builder(Component.literal("Scam"), button -> {
                if (this.reasonBox != null) this.reasonBox.setValue("Scam / Phishing link");
            })
            .bounds(cardX + 91, presetY, btnW, 18)
            .build()
        );

        this.addRenderableWidget(
            Button.builder(Component.literal("Spam/Ad"), button -> {
                if (this.reasonBox != null) this.reasonBox.setValue("Spam / Discord advertisement");
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
                long cdSec = ReportRateLimitManager.getRemainingCooldownSeconds();
                if (cdSec > 0) {
                    this.statusMessage = String.format("§eCooldown! Please wait %ds before reporting again.", cdSec);
                    return;
                }

                // 2. Check 24-hour limit (8 reports max)
                if (!ReportRateLimitManager.canSubmitReport()) {
                    long waitMs = ReportRateLimitManager.getTimeUntilNextAvailableReportMs();
                    long hours = waitMs / (1000 * 60 * 60);
                    long mins = (waitMs / (1000 * 60)) % 60;
                    this.statusMessage = String.format("§cLimit reached (8/8)! Next in %dh %dm.", hours, mins);
                    return;
                }

                if (!com.skyblockrating.auth.SessionAuthManager.isLicensedUser()) {
                    this.statusMessage = com.skyblockrating.auth.SessionAuthManager.AUTH_REQUIRED_MODAL;
                    if (this.minecraft != null && this.minecraft.player != null) {
                        this.minecraft.player.sendSystemMessage(Component.literal(com.skyblockrating.auth.SessionAuthManager.AUTH_REQUIRED_MSG));
                    }
                    return;
                }

                String reporter = (this.minecraft != null && this.minecraft.getUser() != null) ? this.minecraft.getUser().getName() : "Unknown";
                ReportEntry newRep = new ReportEntry(reporter, targetPlayer, reportedBio, reportedDiscord, reason);

                this.statusMessage = "§bSending report...";
                button.active = false;

                RatingStorage.sendReportAsync(newRep, () -> {
                    ReportRateLimitManager.recordReportSubmission();
                    if (this.minecraft != null && this.minecraft.player != null) {
                        this.minecraft.player.sendSystemMessage(Component.literal("§a[SkyReview] Report submitted successfully for @" + targetPlayer + "'s profile!"));
                    }
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(this.parentScreen);
                    }
                }, errorMsg -> {
                    button.active = true;
                    this.statusMessage = "§c" + errorMsg;
                });
            })
            .bounds(cardX + 16, buttonY, 140, 20)
            .build()
        );

        // Cancel Button
        this.addRenderableWidget(
            Button.builder(Component.literal("Cancel"), button -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(this.parentScreen);
                }
            })
            .bounds(cardX + cardWidth - 96, buttonY, 80, 20)
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

        GuiHelper.renderPanel(graphics, cardX, cardY, cardWidth, cardHeight, 28, 0xFFDC2626);

        // Title
        graphics.text(this.font, Component.literal("§c§lReport About Me §8| §e@" + targetPlayer), cardX + 14, cardY + 9, GuiHelper.TEXT_TITLE, true);

        // Quick prompt instructions
        graphics.text(this.font, Component.literal("§7Select a reason or enter custom text:"), cardX + 16, cardY + 38, GuiHelper.TEXT_SECONDARY, false);

        // Preview of reported profile
        String previewBio = reportedBio.replace("\n", " ");
        if (previewBio.length() > 38) previewBio = previewBio.substring(0, 35) + "...";
        graphics.text(this.font, Component.literal("§8Target: §f" + (previewBio.isEmpty() ? (reportedDiscord.isEmpty() ? "Empty profile" : reportedDiscord) : previewBio)), cardX + 16, cardY + 112, GuiHelper.TEXT_MUTED, false);

        // Cooldown and Daily Limits info
        long cdSec = ReportRateLimitManager.getRemainingCooldownSeconds();
        int used = ReportRateLimitManager.getDailyReportCount();
        String limitStr = (cdSec > 0)
                ? String.format("§eCooldown: %ds §8| Today: %d/8", cdSec, used)
                : String.format("§8Limit: §7%d/8 today", used);
        graphics.text(this.font, Component.literal(limitStr), cardX + 16, cardY + 130, GuiHelper.TEXT_MUTED, false);

        // Status Message
        if (!statusMessage.isEmpty()) {
            graphics.text(this.font, Component.literal(statusMessage), cardX + 16, cardY + 155, GuiHelper.TEXT_PRIMARY, false);
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
