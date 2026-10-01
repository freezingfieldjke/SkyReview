package com.skyblockrating.gui;

import com.skyblockrating.data.PrefixTemplateEntry;
import com.skyblockrating.storage.RatingStorage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class AdminPrefixCreateScreen extends Screen {
    private final Screen parentScreen;

    private EditBox templateNameBox;
    private EditBox customColorBox;

    private String templateNameText = "";
    private String customColorHex = "";
    private String baseColorCode = "§c";
    private String baseColorName = "Red";
    private boolean isBold = false;
    private boolean isRgbWave = false;

    private String statusMessage = "";

    private static final String[][] MINECRAFT_COLORS = {
        {"§0", "Black"}, {"§1", "Dark Blue"}, {"§2", "Dark Green"}, {"§3", "Dark Aqua"},
        {"§4", "Dark Red"}, {"§5", "Dark Purple"}, {"§6", "Gold"}, {"§7", "Gray"},
        {"§8", "Dark Gray"}, {"§9", "Blue"}, {"§a", "Green"}, {"§b", "Aqua"},
        {"§c", "Red"}, {"§d", "Light Purple"}, {"§e", "Yellow"}, {"§f", "White"}
    };

    public AdminPrefixCreateScreen(Screen parentScreen) {
        super(Component.literal("SkyReview - Create Prefix Template"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        this.clearWidgets();

        int modalWidth = 500;
        int modalHeight = 310;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        int currentY = modalY + 40;

        // 1. Template Text Input (empty by default, hint "Text")
        this.templateNameBox = new EditBox(this.font, modalX + 16, currentY, 150, 20, Component.literal("Prefix Text"));
        this.templateNameBox.setMaxLength(24);
        this.templateNameBox.setHint(Component.literal("Text"));
        this.templateNameBox.setValue(this.templateNameText);
        this.templateNameBox.setResponder(val -> this.templateNameText = val);
        this.addRenderableWidget(this.templateNameBox);

        // 2. Custom Color / HEX Input
        this.customColorBox = new EditBox(this.font, modalX + 172, currentY, 120, 20, Component.literal("Color Hex"));
        this.customColorBox.setMaxLength(16);
        this.customColorBox.setHint(Component.literal("#HEX or Code"));
        this.customColorBox.setValue(this.customColorHex);
        this.customColorBox.setResponder(val -> this.customColorHex = val);
        this.addRenderableWidget(this.customColorBox);

        // Save Template Button (Top Right)
        this.addRenderableWidget(
            Button.builder(Component.literal("✔ Save Template"), btn -> {
                String rawName = this.templateNameBox.getValue().trim();
                if (rawName.isEmpty()) {
                    this.statusMessage = "§cTemplate text cannot be empty!";
                    return;
                }
                if (!rawName.startsWith("[")) rawName = "[" + rawName;
                if (!rawName.endsWith("]")) rawName = rawName + "]";

                String finalColor = getResolvedColorCode();
                final String finalName = rawName;

                this.statusMessage = "Saving template...";
                RatingStorage.savePrefixTemplateAsync(null, finalName, finalColor, false, success -> {
                    if (success) {
                        this.statusMessage = "§aTemplate " + finalColor + finalName + " §asaved!";
                        RatingStorage.loadData();
                        rebuildWidgets();
                    } else {
                        this.statusMessage = "§cFailed to save template.";
                    }
                });
            })
            .bounds(modalX + 300, currentY, modalWidth - 316, 20)
            .build()
        );

        currentY += 28;

        // 3. 16-Color Dropdown Button (Opens Selector Popup like Dungeons floor selector)
        String[] colorOptions = new String[MINECRAFT_COLORS.length];
        for (int i = 0; i < MINECRAFT_COLORS.length; i++) {
            colorOptions[i] = MINECRAFT_COLORS[i][0] + " " + MINECRAFT_COLORS[i][1];
        }

        this.addRenderableWidget(
            Button.builder(Component.literal("Color: " + baseColorCode + baseColorName + " ▼"), btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new SelectorPopupScreen(this, "Select Prefix Color", colorOptions, selected -> {
                        for (String[] mc : MINECRAFT_COLORS) {
                            if (selected.contains(mc[1])) {
                                this.baseColorCode = mc[0];
                                this.baseColorName = mc[1];
                                this.isRgbWave = false;
                                this.customColorHex = this.baseColorCode + (this.isBold ? "§l" : "");
                                break;
                            }
                        }
                        rebuildWidgets();
                    }));
                }
            })
            .bounds(modalX + 16, currentY, 150, 20)
            .build()
        );

        // 4. Bold Toggle Button
        this.addRenderableWidget(
            Button.builder(Component.literal("Bold: " + (isBold ? "§a[ON]" : "§c[OFF]")), btn -> {
                isBold = !isBold;
                updateColorInput();
                rebuildWidgets();
            })
            .bounds(modalX + 172, currentY, 70, 20)
            .build()
        );

        // 5. RGB Chroma Wave Toggle Button
        this.addRenderableWidget(
            Button.builder(Component.literal("RGB: " + (isRgbWave ? "§a[ON]" : "§c[OFF]")), btn -> {
                isRgbWave = !isRgbWave;
                updateColorInput();
                rebuildWidgets();
            })
            .bounds(modalX + 248, currentY, 70, 20)
            .build()
        );

        // Back Button (Bottom Left)
        this.addRenderableWidget(
            Button.builder(Component.literal("← Back"), btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(this.parentScreen);
                }
            })
            .bounds(modalX + 16, modalY + modalHeight - 26, 80, 20)
            .build()
        );
    }

    private void updateColorInput() {
        if (isRgbWave) {
            this.customColorHex = "§z" + (isBold ? "§l" : "");
        } else {
            this.customColorHex = baseColorCode + (isBold ? "§l" : "");
        }
        if (customColorBox != null) {
            customColorBox.setValue(this.customColorHex);
        }
    }

    private String getResolvedColorCode() {
        if (customColorHex != null && !customColorHex.trim().isEmpty()) {
            return customColorHex.trim();
        }
        if (isRgbWave) {
            return "§z" + (isBold ? "§l" : "");
        }
        return baseColorCode + (isBold ? "§l" : "");
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        int modalWidth = 500;
        int modalHeight = 310;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;
        int listY = modalY + 130;

        List<PrefixTemplateEntry> templates = RatingStorage.getPrefixTemplates();
        if (mouseX >= modalX + 16 && mouseX <= modalX + modalWidth - 16 && mouseY >= listY + 16 && mouseY <= listY + 115) {
            int clickIdx = (int) ((mouseY - (listY + 16)) / 22);
            if (clickIdx >= 0 && clickIdx < Math.min(templates.size(), 4)) {
                PrefixTemplateEntry t = templates.get(clickIdx);
                // Check if clicking delete button on right
                if (mouseX >= modalX + modalWidth - 75) {
                    this.statusMessage = "Deleting template...";
                    RatingStorage.deletePrefixTemplateAsync(t.getId(), success -> {
                        if (success) {
                            this.statusMessage = "§aDeleted template " + t.getName();
                            RatingStorage.getPrefixTemplates().removeIf(item -> item.getId().equals(t.getId()));
                            rebuildWidgets();
                        } else {
                            this.statusMessage = "§cFailed to delete template.";
                        }
                    });
                    return true;
                } else {
                    // Clicked item to edit
                    if (templateNameBox != null) templateNameBox.setValue(t.getName());
                    if (customColorBox != null) customColorBox.setValue(t.getColor());
                    this.statusMessage = "§aLoaded template " + t.getFormattedPrefix();
                    return true;
                }
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, GuiHelper.BG_OVERLAY);

        int modalWidth = 500;
        int modalHeight = 310;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        GuiHelper.renderPanel(graphics, modalX, modalY, modalWidth, modalHeight, 30, GuiHelper.HEADER_ACCENT_ADMIN);

        graphics.text(this.font, Component.literal("§c§lSky§4§lReview §8| §fCreate Prefix Template"), modalX + 14, modalY + 9, GuiHelper.TEXT_TITLE, true);

        // Section 1: Template Settings & Live Preview
        String pName = (templateNameBox != null && !templateNameBox.getValue().trim().isEmpty()) ? templateNameBox.getValue().trim() : "Text";
        if (!pName.startsWith("[")) pName = "[" + pName;
        if (!pName.endsWith("]")) pName = pName + "]";

        String pColor = getResolvedColorCode();
        int previewY = modalY + 102;
        graphics.text(this.font, Component.literal("§7Live Preview: "), modalX + 18, previewY, GuiHelper.TEXT_MUTED, false);
        GuiHelper.renderColoredString(graphics, this.font, pName, pColor, modalX + 90, previewY, isBold);

        // Section 2: Saved Templates List Card
        int listY = modalY + 125;
        GuiHelper.renderCard(graphics, modalX + 16, listY, modalWidth - 32, 115, false);
        graphics.text(this.font, Component.literal("§7Saved Templates (Click to edit or delete):"), modalX + 22, listY + 5, GuiHelper.TEXT_SECONDARY, false);

        List<PrefixTemplateEntry> templates = RatingStorage.getPrefixTemplates();
        int itemY = listY + 18;
        if (templates.isEmpty()) {
            graphics.text(this.font, Component.literal("§8No templates created yet. Use inputs above to create one!"), modalX + 22, itemY + 6, GuiHelper.TEXT_MUTED, false);
        } else {
            for (int i = 0; i < Math.min(templates.size(), 4); i++) {
                PrefixTemplateEntry t = templates.get(i);
                boolean itemHover = mouseX >= modalX + 20 && mouseX <= modalX + modalWidth - 20 && mouseY >= itemY && mouseY < itemY + 20;
                boolean delHover = mouseX >= modalX + modalWidth - 75 && mouseX <= modalX + modalWidth - 24 && mouseY >= itemY + 2 && mouseY < itemY + 18;

                graphics.fill(modalX + 20, itemY, modalX + modalWidth - 20, itemY + 20, itemHover ? GuiHelper.CARD_HOVER : GuiHelper.CARD_BG_ALT);

                // Draw prefix template sample
                GuiHelper.renderColoredString(graphics, this.font, t.getName(), t.getColor(), modalX + 26, itemY + 6, false);

                // Draw Delete Button
                graphics.fill(modalX + modalWidth - 72, itemY + 2, modalX + modalWidth - 24, itemY + 18, delHover ? 0xFFEF4444 : 0xFFDC2626);
                graphics.text(this.font, Component.literal("Delete"), modalX + modalWidth - 62, itemY + 5, 0xFFFFFFFF, false);

                itemY += 22;
            }
        }

        // Status Message Bar Footer
        graphics.text(this.font, Component.literal(this.statusMessage), modalX + 105, modalY + modalHeight - 20, GuiHelper.TEXT_SECONDARY, false);

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }
}
