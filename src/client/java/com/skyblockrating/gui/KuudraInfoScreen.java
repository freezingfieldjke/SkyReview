package com.skyblockrating.gui;

import com.skyblockrating.api.HypixelApiFetcher;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class KuudraInfoScreen extends Screen {
    private final Screen parentScreen;
    private final String playerName;
    private final HypixelApiFetcher.HypixelStats stats;

    public KuudraInfoScreen(Screen parentScreen, String playerName, HypixelApiFetcher.HypixelStats stats) {
        super(Component.literal("Kuudra Tier Info - " + playerName));
        this.parentScreen = parentScreen;
        this.playerName = playerName;
        this.stats = stats;
    }

    @Override
    protected void init() {
        super.init();

        int modalWidth = 320;
        int modalHeight = 230;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        this.addRenderableWidget(
            Button.builder(Component.literal("← Back"), button -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(this.parentScreen);
                }
            })
            .bounds(modalX + (modalWidth - 90) / 2, modalY + modalHeight - 26, 90, 20)
            .build()
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, GuiHelper.BG_OVERLAY);

        int modalWidth = 320;
        int modalHeight = 230;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        GuiHelper.renderPanel(graphics, modalX, modalY, modalWidth, modalHeight, 30, GuiHelper.COLOR_ORANGE);

        graphics.text(this.font, Component.literal("§6§lKuudra Tiers: §f@" + playerName), modalX + 14, modalY + 9, GuiHelper.TEXT_TITLE, true);

        // Header Table
        int tableY = modalY + 38;
        int tableW = modalWidth - 28;
        graphics.fill(modalX + 14, tableY, modalX + 14 + tableW, tableY + 16, GuiHelper.HEADER_BG);
        graphics.text(this.font, Component.literal("§7Tier"), modalX + 22, tableY + 4, GuiHelper.TEXT_MUTED, false);
        graphics.text(this.font, Component.literal("§7Completed Runs"), modalX + 180, tableY + 4, GuiHelper.TEXT_MUTED, false);

        String[] tiers = {"Basic", "Hot", "Burning", "Fiery", "Infernal"};
        int rowY = tableY + 18;

        for (int i = 0; i < tiers.length; i++) {
            String tier = tiers[i];
            int runs = (stats != null && stats.kuudraTierRuns != null && stats.kuudraTierRuns.containsKey(tier)) ? stats.kuudraTierRuns.get(tier) : 0;

            boolean hovered = mouseX >= modalX + 14 && mouseX <= modalX + 14 + tableW && mouseY >= rowY && mouseY < rowY + 20;
            int bg = hovered ? GuiHelper.CARD_HOVER : ((i % 2 == 0) ? GuiHelper.CARD_BG : GuiHelper.CARD_BG_ALT);

            graphics.fill(modalX + 14, rowY, modalX + 14 + tableW, rowY + 20, bg);

            String tierColor = switch (tier) {
                case "Basic" -> "§a";
                case "Hot" -> "§e";
                case "Burning" -> "§6";
                case "Fiery" -> "§c";
                case "Infernal" -> "§4§l";
                default -> "§f";
            };

            graphics.text(this.font, Component.literal(tierColor + tier), modalX + 22, rowY + 5, GuiHelper.TEXT_PRIMARY, false);
            graphics.text(this.font, Component.literal("§f" + String.format("%,d runs", runs)), modalX + 180, rowY + 5, GuiHelper.COLOR_GREEN, false);

            rowY += 22;
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
