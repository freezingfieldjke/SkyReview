package com.skyblockrating.gui;

import com.skyblockrating.config.OwnerLock;
import com.skyblockrating.storage.RatingStorage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ModDisabledScreen extends Screen {
    private static final String DISCORD_INVITE = "https://discord.gg/KpVndhhNDr";
    private final Screen parentScreen;
    private Button copyDiscordBtn;
    private String statusMessage = "";

    public ModDisabledScreen(Screen parentScreen) {
        super(Component.literal("SkyReview Maintenance"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();

        int modalWidth = 360;
        int modalHeight = 200;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        int btnY = modalY + modalHeight - 32;

        // Button to copy Discord server link
        int discordBtnW = 140;
        this.copyDiscordBtn = this.addRenderableWidget(
            Button.builder(Component.literal("📋 Copy Discord"), btn -> {
                if (this.minecraft != null) {
                    this.minecraft.keyboardHandler.setClipboard(DISCORD_INVITE);
                    btn.setMessage(Component.literal("§a✔ Link Copied!"));
                    this.statusMessage = "§aDiscord link copied to clipboard!";
                    if (this.minecraft.player != null) {
                        this.minecraft.player.sendSystemMessage(Component.literal("§a[SkyReview] Discord invite copied: §b" + DISCORD_INVITE));
                    }
                }
            })
            .bounds(modalX + 16, btnY, discordBtnW, 20)
            .build()
        );

        if (OwnerLock.isOwner()) {
            int adminBtnW = 80;
            this.addRenderableWidget(
                Button.builder(Component.literal("§cAdmin"), btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new AdminControlScreen(this.parentScreen));
                    }
                })
                .bounds(modalX + 162, btnY, adminBtnW, 20)
                .build()
            );

            this.addRenderableWidget(
                Button.builder(Component.literal("Close"), btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(this.parentScreen);
                    }
                })
                .bounds(modalX + modalWidth - 76, btnY, 60, 20)
                .build()
            );
        } else {
            this.addRenderableWidget(
                Button.builder(Component.literal("Close"), btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(this.parentScreen);
                    }
                })
                .bounds(modalX + modalWidth - 100, btnY, 84, 20)
                .build()
            );
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, GuiHelper.BG_OVERLAY);

        int modalWidth = 360;
        int modalHeight = 200;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        GuiHelper.renderPanel(graphics, modalX, modalY, modalWidth, modalHeight, 30, GuiHelper.HEADER_ACCENT_ADMIN);

        boolean isOutdated = RatingStorage.isModOutdated();
        String title = isOutdated ? "§c§lSky§4§lReview §8| §cUpdate Required" : "§c§lSky§4§lReview §8| §cTemporarily Disabled";
        String tag = isOutdated ? "§c[OUTDATED]" : "§c[MAINTENANCE]";
        int tagOffset = isOutdated ? 80 : 95;

        graphics.text(this.font, Component.literal(title), modalX + 14, modalY + 9, GuiHelper.TEXT_TITLE, true);
        graphics.text(this.font, Component.literal(tag), modalX + modalWidth - tagOffset, modalY + 9, GuiHelper.COLOR_RED, true);

        // Content
        int textY = modalY + 42;
        String alertText = isOutdated ? "§c⚠ Your mod version is outdated & blocked for security" : "§c⚠ Mod is temporarily disabled for maintenance";
        graphics.text(this.font, Component.literal(alertText), modalX + 16, textY, GuiHelper.COLOR_RED, true);
        textY += 18;

        String reason = RatingStorage.getDisabledReason();
        graphics.text(this.font, Component.literal("§7Status: §f" + reason), modalX + 16, textY, GuiHelper.TEXT_PRIMARY, false);
        textY += 16;
        String tipText = isOutdated ? "§eTip: §7Please download the latest update from Discord!" : "§eTip: §7Use §e/sr reload §7to re-check if maintenance ended.";
        graphics.text(this.font, Component.literal(tipText), modalX + 16, textY, GuiHelper.COLOR_YELLOW, false);
        textY += 16;
        String linkPrompt = isOutdated ? "§8Download the new version on our Discord:" : "§8Join our Discord for news and server status updates:";
        graphics.text(this.font, Component.literal(linkPrompt), modalX + 16, textY, GuiHelper.TEXT_MUTED, false);
        textY += 14;
        String currentUpdateUrl = RatingStorage.getUpdateUrl();
        graphics.text(this.font, Component.literal("§9🔗 " + currentUpdateUrl), modalX + 16, textY, 0xFF5865F2, false);

        if (!this.statusMessage.isEmpty()) {
            graphics.text(this.font, Component.literal(this.statusMessage), modalX + 16, modalY + modalHeight - 48, GuiHelper.COLOR_GREEN, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

