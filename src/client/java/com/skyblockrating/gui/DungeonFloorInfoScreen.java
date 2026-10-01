package com.skyblockrating.gui;

import com.skyblockrating.api.HypixelApiFetcher;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class DungeonFloorInfoScreen extends Screen {
    private final Screen parentScreen;
    private final String playerName;
    private final HypixelApiFetcher.HypixelStats stats;

    public DungeonFloorInfoScreen(Screen parentScreen, String playerName, HypixelApiFetcher.HypixelStats stats) {
        super(Component.literal("Dungeon Floor Info - " + playerName));
        this.parentScreen = parentScreen;
        this.playerName = playerName;
        this.stats = stats;
    }

    @Override
    protected void init() {
        super.init();

        int modalWidth = 360;
        int modalHeight = 270;
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

        int modalWidth = 360;
        int modalHeight = 270;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        GuiHelper.renderPanel(graphics, modalX, modalY, modalWidth, modalHeight, 30, GuiHelper.COLOR_ORANGE);

        graphics.text(this.font, Component.literal("§6§lDungeon Floor Stats: §f@" + playerName), modalX + 14, modalY + 9, GuiHelper.TEXT_TITLE, true);

        // Column Titles
        graphics.text(this.font, Component.literal("§6§lNormal Catacombs"), modalX + 18, modalY + 36, GuiHelper.COLOR_ORANGE, false);
        graphics.text(this.font, Component.literal("§c§lMaster Catacombs"), modalX + 190, modalY + 36, GuiHelper.COLOR_RED, false);

        int startY = modalY + 50;
        String[] normalFloors = {"F1", "F2", "F3", "F4", "F5", "F6", "F7"};
        String[] masterFloors = {"M1", "M2", "M3", "M4", "M5", "M6", "M7"};

        for (int i = 0; i < 7; i++) {
            int y = startY + i * 25;

            // Normal Floor (Orange)
            String fNorm = normalFloors[i];
            HypixelApiFetcher.DungeonFloorData normData = (stats != null && stats.floorMap.containsKey(fNorm)) ? stats.floorMap.get(fNorm) : new HypixelApiFetcher.DungeonFloorData();
            boolean hNorm = mouseX >= modalX + 16 && mouseX <= modalX + 172 && mouseY >= y && mouseY <= y + 22;
            GuiHelper.renderCard(graphics, modalX + 16, y, 156, 22, hNorm);

            graphics.text(this.font, Component.literal("§6" + fNorm), modalX + 22, y + 6, GuiHelper.COLOR_ORANGE, true);
            graphics.text(this.font, Component.literal(normData.runs + " runs"), modalX + 46, y + 6, GuiHelper.TEXT_PRIMARY, false);
            graphics.text(this.font, Component.literal("§aPB " + normData.pbFormatted), modalX + 104, y + 6, GuiHelper.COLOR_GREEN, false);

            // Master Floor (Red)
            String fMaster = masterFloors[i];
            HypixelApiFetcher.DungeonFloorData masterData = (stats != null && stats.floorMap.containsKey(fMaster)) ? stats.floorMap.get(fMaster) : new HypixelApiFetcher.DungeonFloorData();
            boolean hMaster = mouseX >= modalX + 188 && mouseX <= modalX + 344 && mouseY >= y && mouseY <= y + 22;
            GuiHelper.renderCard(graphics, modalX + 188, y, 156, 22, hMaster);

            graphics.text(this.font, Component.literal("§c" + fMaster), modalX + 194, y + 6, GuiHelper.COLOR_RED, true);
            graphics.text(this.font, Component.literal(masterData.runs + " runs"), modalX + 218, y + 6, GuiHelper.TEXT_PRIMARY, false);
            graphics.text(this.font, Component.literal("§aPB " + masterData.pbFormatted), modalX + 276, y + 6, GuiHelper.COLOR_GREEN, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
