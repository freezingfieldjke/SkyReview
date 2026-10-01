package com.skyblockrating.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class LinksModalScreen extends Screen {
    private final Screen parentScreen;
    private String statusMessage = "";

    private static final String DISCORD_URL = "https://discord.gg/KpVndhhNDr";
    private static final String GITHUB_URL = "https://github.com/freezingfieldjke";

    public LinksModalScreen(Screen parentScreen) {
        super(Component.literal("SkyReview Links"));
        this.parentScreen = parentScreen;
    }

    private void openWebUrl(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
            } else {
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    Runtime.getRuntime().exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", url});
                } else if (os.contains("mac")) {
                    Runtime.getRuntime().exec(new String[]{"open", url});
                } else {
                    Runtime.getRuntime().exec(new String[]{"xdg-open", url});
                }
            }
            this.statusMessage = "§aOpened link in browser!";
        } catch (Throwable t) {
            this.statusMessage = "§cFailed to open browser.";
        }

        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.sendSystemMessage(Component.literal("§a[SkyReview] Link: §b" + url));
        }
    }

    private void copyToClipboard(String url, String name) {
        if (this.minecraft != null) {
            this.minecraft.keyboardHandler.setClipboard(url);
            this.statusMessage = "§a" + name + " link copied to clipboard!";
            if (this.minecraft.player != null) {
                this.minecraft.player.sendSystemMessage(Component.literal("§a[SkyReview] Copied " + name + " link to clipboard: §b" + url));
            }
        }
    }

    @Override
    protected void init() {
        super.init();

        int modalWidth = 360;
        int modalHeight = 200;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        int startY = modalY + 42;

        // --- Card 1: Discord ---
        int card1Y = startY;
        this.addRenderableWidget(
            Button.builder(Component.literal("🌐 Open"), button -> openWebUrl(DISCORD_URL))
            .bounds(modalX + modalWidth - 156, card1Y + 9, 66, 20)
            .build()
        );
        this.addRenderableWidget(
            Button.builder(Component.literal("📋 Copy"), button -> copyToClipboard(DISCORD_URL, "Discord"))
            .bounds(modalX + modalWidth - 84, card1Y + 9, 66, 20)
            .build()
        );

        // --- Card 2: GitHub ---
        int card2Y = startY + 48;
        this.addRenderableWidget(
            Button.builder(Component.literal("🌐 Open"), button -> openWebUrl(GITHUB_URL))
            .bounds(modalX + modalWidth - 156, card2Y + 9, 66, 20)
            .build()
        );
        this.addRenderableWidget(
            Button.builder(Component.literal("📋 Copy"), button -> copyToClipboard(GITHUB_URL, "GitHub"))
            .bounds(modalX + modalWidth - 84, card2Y + 9, 66, 20)
            .build()
        );

        // Back Button
        this.addRenderableWidget(
            Button.builder(Component.literal("← Back"), button -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(this.parentScreen);
                }
            })
            .bounds(modalX + (modalWidth - 100) / 2, modalY + modalHeight - 26, 100, 20)
            .build()
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, GuiHelper.BG_OVERLAY);

        int modalWidth = 360;
        int modalHeight = 200;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        GuiHelper.renderPanel(graphics, modalX, modalY, modalWidth, modalHeight, 30, GuiHelper.COLOR_GOLD);

        // Header Title
        graphics.text(this.font, Component.literal("§6§lSky§e§lReview §8| §fLinks"), modalX + 14, modalY + 9, GuiHelper.TEXT_TITLE, true);

        int startY = modalY + 42;

        // Card 1: Discord (Clean, centered vertically, no description, no emoji)
        int card1Y = startY;
        GuiHelper.renderCard(graphics, modalX + 14, card1Y, modalWidth - 28, 38, false);
        graphics.text(this.font, Component.literal("§9§lDiscord Server"), modalX + 26, card1Y + 14, 0xFF5865F2, true);

        // Card 2: GitHub (Clean, centered vertically, no description, no emoji)
        int card2Y = startY + 48;
        GuiHelper.renderCard(graphics, modalX + 14, card2Y, modalWidth - 28, 38, false);
        graphics.text(this.font, Component.literal("§f§lGitHub Profile"), modalX + 26, card2Y + 14, 0xFFFFFFFF, true);

        // Status Toast Message
        if (!statusMessage.isEmpty()) {
            int msgW = this.font.width(statusMessage);
            graphics.text(this.font, Component.literal(statusMessage), modalX + (modalWidth - msgW) / 2, modalY + modalHeight - 40, GuiHelper.COLOR_GREEN, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
