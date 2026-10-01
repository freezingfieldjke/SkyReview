package com.skyblockrating.gui;

import com.skyblockrating.data.PrefixTemplateEntry;
import com.skyblockrating.data.UserPrefixEntry;
import com.skyblockrating.storage.RatingStorage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class AdminPrefixAssignScreen extends Screen {
    private final Screen parentScreen;

    // Steps: 1 = Enter IGN, 2 = Choose Nick Color & Prefix Template
    private int step = 1;
    private String targetPlayerName = "";

    // Step 1 Widgets
    private EditBox playerIgnBox;

    // Step 2 Widgets (Nick Color & Prefix)
    private EditBox nickHexBox;
    private String customNickHex = "";
    private String nickBaseColorCode = "§f";
    private String nickBaseColorName = "White";
    private boolean isNickBold = false;
    private boolean isNickRgb = false;

    private PrefixTemplateEntry selectedTemplate = null;

    private String statusMessage = "";

    private static final String[][] MINECRAFT_COLORS = {
        {"§0", "Black"}, {"§1", "Dark Blue"}, {"§2", "Dark Green"}, {"§3", "Dark Aqua"},
        {"§4", "Dark Red"}, {"§5", "Dark Purple"}, {"§6", "Gold"}, {"§7", "Gray"},
        {"§8", "Dark Gray"}, {"§9", "Blue"}, {"§a", "Green"}, {"§b", "Aqua"},
        {"§c", "Red"}, {"§d", "Light Purple"}, {"§e", "Yellow"}, {"§f", "White"}
    };

    public AdminPrefixAssignScreen(Screen parentScreen) {
        super(Component.literal("SkyReview - Assign Prefix"));
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

        int modalWidth = 520;
        int modalHeight = 330;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        if (step == 1) {
            // ================= STEP 1: SELECT PLAYER IGN =================
            int currentY = modalY + 45;

            // IGN Input Box (placeholder/hint "IGN")
            this.playerIgnBox = new EditBox(this.font, modalX + 16, currentY, 180, 20, Component.literal("Player IGN"));
            this.playerIgnBox.setMaxLength(16);
            this.playerIgnBox.setHint(Component.literal("IGN"));
            this.playerIgnBox.setValue(this.targetPlayerName);
            this.addRenderableWidget(this.playerIgnBox);

            // Select Player Button (Moves to Step 2)
            this.addRenderableWidget(
                Button.builder(Component.literal("Select Player →"), btn -> {
                    String ign = this.playerIgnBox.getValue().trim();
                    if (ign.isEmpty()) {
                        this.statusMessage = "§cPlease enter player IGN first!";
                        return;
                    }
                    this.targetPlayerName = ign;
                    loadExistingPlayerPrefixSettings(ign);
                    this.step = 2;
                    this.statusMessage = "Configuring prefix for " + ign;
                    rebuildWidgets();
                })
                .bounds(modalX + 204, currentY, 130, 20)
                .build()
            );

            // Clear Prefix Button (Removes prefix & custom nick color)
            this.addRenderableWidget(
                Button.builder(Component.literal("🗑 Clear Prefix"), btn -> {
                    String ign = this.playerIgnBox.getValue().trim();
                    if (ign.isEmpty()) {
                        this.statusMessage = "§cEnter player IGN to clear prefix!";
                        return;
                    }
                    this.statusMessage = "Clearing prefix for " + ign + "...";
                    RatingStorage.removeUserPrefixAsync(ign, success -> {
                        if (success) {
                            this.statusMessage = "§aCleared prefix & color for " + ign + "!";
                            RatingStorage.loadData();
                            rebuildWidgets();
                        } else {
                            this.statusMessage = "§cFailed to clear prefix.";
                        }
                    });
                })
                .bounds(modalX + 342, currentY, modalWidth - 358, 20)
                .build()
            );

            // Back Button
            this.addRenderableWidget(
                Button.builder(Component.literal("← Back"), btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(this.parentScreen);
                    }
                })
                .bounds(modalX + 16, modalY + modalHeight - 26, 80, 20)
                .build()
            );

        } else {
            // ================= STEP 2: CONFIGURE NICK COLOR & PREFIX =================
            int currentY = modalY + 36;

            // 1. Nick Color Selection Section (Top)
            this.nickHexBox = new EditBox(this.font, modalX + 16, currentY + 14, 110, 20, Component.literal("Nick Hex"));
            this.nickHexBox.setMaxLength(16);
            this.nickHexBox.setHint(Component.literal("#HEX or Code"));
            this.nickHexBox.setValue(this.customNickHex);
            this.nickHexBox.setResponder(val -> this.customNickHex = val);
            this.addRenderableWidget(this.nickHexBox);

            // 16-Color Dropdown Button for Nick Color
            String[] colorOptions = new String[MINECRAFT_COLORS.length];
            for (int i = 0; i < MINECRAFT_COLORS.length; i++) {
                colorOptions[i] = MINECRAFT_COLORS[i][0] + " " + MINECRAFT_COLORS[i][1];
            }

            this.addRenderableWidget(
                Button.builder(Component.literal("Nick Color: " + nickBaseColorCode + nickBaseColorName + " ▼"), btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new SelectorPopupScreen(this, "Select Nickname Color", colorOptions, selected -> {
                            for (String[] mc : MINECRAFT_COLORS) {
                                if (selected.contains(mc[1])) {
                                    this.nickBaseColorCode = mc[0];
                                    this.nickBaseColorName = mc[1];
                                    this.isNickRgb = false;
                                    this.customNickHex = this.nickBaseColorCode + (this.isNickBold ? "§l" : "");
                                    break;
                                }
                            }
                            rebuildWidgets();
                        }));
                    }
                })
                .bounds(modalX + 132, currentY + 14, 150, 20)
                .build()
            );

            // Nick Bold Toggle Button
            this.addRenderableWidget(
                Button.builder(Component.literal("Bold: " + (isNickBold ? "§a[ON]" : "§c[OFF]")), btn -> {
                    isNickBold = !isNickBold;
                    updateNickColorInput();
                    rebuildWidgets();
                })
                .bounds(modalX + 288, currentY + 14, 70, 20)
                .build()
            );

            // Nick RGB Wave Toggle Button
            this.addRenderableWidget(
                Button.builder(Component.literal("RGB: " + (isNickRgb ? "§a[ON]" : "§c[OFF]")), btn -> {
                    isNickRgb = !isNickRgb;
                    updateNickColorInput();
                    rebuildWidgets();
                })
                .bounds(modalX + 364, currentY + 14, 70, 20)
                .build()
            );

            // Bottom Buttons
            int btnY = modalY + modalHeight - 26;

            // Apply Button
            this.addRenderableWidget(
                Button.builder(Component.literal("✔ Apply Prefix & Nick Color"), btn -> {
                    if (targetPlayerName.isEmpty()) {
                        this.statusMessage = "§cTarget player is missing!";
                        return;
                    }

                    String pId = (selectedTemplate != null) ? selectedTemplate.getId() : "custom";
                    String pName = (selectedTemplate != null) ? selectedTemplate.getName() : "";
                    String pColor = (selectedTemplate != null) ? selectedTemplate.getColor() : "§f";
                    String finalNickColor = getResolvedNickColorCode();

                    this.statusMessage = "Applying prefix...";
                    RatingStorage.assignUserPrefixAsync(targetPlayerName, pId, pName, pColor, finalNickColor, false, success -> {
                        if (success) {
                            this.statusMessage = "§aApplied to " + targetPlayerName + "!";
                            RatingStorage.loadData();
                            this.step = 1;
                            rebuildWidgets();
                        } else {
                            this.statusMessage = "§cFailed to apply prefix.";
                        }
                    });
                })
                .bounds(modalX + 160, btnY, 210, 20)
                .build()
            );

            // Change Player Button (Goes back to Step 1)
            this.addRenderableWidget(
                Button.builder(Component.literal("← Change Player"), btn -> {
                    this.step = 1;
                    rebuildWidgets();
                })
                .bounds(modalX + 16, btnY, 110, 20)
                .build()
            );
        }
    }

    private void loadExistingPlayerPrefixSettings(String username) {
        UserPrefixEntry existing = RatingStorage.getUserPrefix(username);
        if (existing != null) {
            String nColor = existing.getNameColor();
            this.customNickHex = nColor;
            if (nColor.contains("§z")) {
                this.isNickRgb = true;
            } else {
                this.isNickRgb = false;
                for (String[] mc : MINECRAFT_COLORS) {
                    if (nColor.contains(mc[0])) {
                        this.nickBaseColorCode = mc[0];
                        this.nickBaseColorName = mc[1];
                        break;
                    }
                }
            }
            this.isNickBold = nColor.contains("§l");

            // Match template
            List<PrefixTemplateEntry> templates = RatingStorage.getPrefixTemplates();
            for (PrefixTemplateEntry t : templates) {
                if (t.getId().equals(existing.getPrefixId()) || t.getName().equalsIgnoreCase(existing.getPrefixName())) {
                    this.selectedTemplate = t;
                    break;
                }
            }
        } else {
            this.selectedTemplate = null;
            this.nickBaseColorCode = "§f";
            this.nickBaseColorName = "White";
            this.isNickBold = false;
            this.isNickRgb = false;
            this.customNickHex = "";
        }
    }

    private void updateNickColorInput() {
        if (isNickRgb) {
            this.customNickHex = "§z" + (isNickBold ? "§l" : "");
        } else {
            this.customNickHex = nickBaseColorCode + (isNickBold ? "§l" : "");
        }
        if (nickHexBox != null) {
            nickHexBox.setValue(this.customNickHex);
        }
    }

    private String getResolvedNickColorCode() {
        if (customNickHex != null && !customNickHex.trim().isEmpty()) {
            return customNickHex.trim();
        }
        if (isNickRgb) {
            return "§z" + (isNickBold ? "§l" : "");
        }
        return nickBaseColorCode + (isNickBold ? "§l" : "");
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        int modalWidth = 520;
        int modalHeight = 330;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        if (step == 1) {
            // Click assigned player list in Step 1
            int listY = modalY + 75;
            int itemStartY = listY + 18;
            List<UserPrefixEntry> assignedList = new ArrayList<>(RatingStorage.getUserPrefixes().values());
            int totalItems = Math.min(assignedList.size(), 6);
            if (mouseX >= modalX + 20 && mouseX <= modalX + modalWidth - 20 && mouseY >= itemStartY && mouseY <= itemStartY + totalItems * 22) {
                int clickIdx = (int) ((mouseY - itemStartY) / 22);
                if (clickIdx >= 0 && clickIdx < totalItems) {
                    UserPrefixEntry up = assignedList.get(clickIdx);
                    final String pName = up.getUsername();
                    if (mouseX >= modalX + modalWidth - 75) {
                        // Clicked Remove button
                        this.statusMessage = "Removing prefix from " + pName + "...";
                        RatingStorage.removeUserPrefixAsync(pName, success -> {
                            if (success) {
                                this.statusMessage = "§aRemoved prefix from " + pName + "!";
                                RatingStorage.loadData();
                                rebuildWidgets();
                            } else {
                                this.statusMessage = "§cFailed to remove prefix.";
                            }
                        });
                        return true;
                    } else {
                        // Select player for editing
                        this.targetPlayerName = pName;
                        if (playerIgnBox != null) playerIgnBox.setValue(pName);
                        loadExistingPlayerPrefixSettings(pName);
                        this.step = 2;
                        rebuildWidgets();
                        return true;
                    }
                }
            }
        } else {
            // Click template table in Step 2 (exact 1:1 match with tableY = modalY + 80, itemY = tableY + 18)
            int tableY = modalY + 80;
            int itemStartY = tableY + 18;
            List<PrefixTemplateEntry> templates = RatingStorage.getPrefixTemplates();
            int totalItems = 1 + Math.min(templates.size(), 4);
            if (mouseX >= modalX + 20 && mouseX <= modalX + modalWidth - 20 && mouseY >= itemStartY && mouseY <= itemStartY + totalItems * 22) {
                int clickIdx = (int) ((mouseY - itemStartY) / 22);
                if (clickIdx == 0) {
                    // Option 0: None / No Prefix
                    this.selectedTemplate = null;
                    this.statusMessage = "§7Selected: [No Prefix]";
                    return true;
                } else if (clickIdx - 1 >= 0 && clickIdx - 1 < templates.size()) {
                    this.selectedTemplate = templates.get(clickIdx - 1);
                    this.statusMessage = "§aSelected: " + this.selectedTemplate.getFormattedPrefix();
                    return true;
                }
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, GuiHelper.BG_OVERLAY);

        int modalWidth = 520;
        int modalHeight = 330;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        GuiHelper.renderPanel(graphics, modalX, modalY, modalWidth, modalHeight, 30, GuiHelper.HEADER_ACCENT_ADMIN);

        if (step == 1) {
            // ================= STEP 1 RENDER =================
            graphics.text(this.font, Component.literal("§c§lSky§4§lReview §8| §fAssign Prefix to Player"), modalX + 14, modalY + 9, GuiHelper.TEXT_TITLE, true);
            graphics.text(this.font, Component.literal("§7Enter player IGN to assign prefix or clear existing:"), modalX + 16, modalY + 32, GuiHelper.TEXT_SECONDARY, false);

            // Assigned Players Card
            int listY = modalY + 75;
            GuiHelper.renderCard(graphics, modalX + 16, listY, modalWidth - 32, 160, false);
            graphics.text(this.font, Component.literal("§7Currently Assigned Players (Click to edit or remove):"), modalX + 22, listY + 5, GuiHelper.TEXT_SECONDARY, false);

            List<UserPrefixEntry> assignedList = new ArrayList<>(RatingStorage.getUserPrefixes().values());
            int itemY = listY + 18;
            if (assignedList.isEmpty()) {
                graphics.text(this.font, Component.literal("§8No players have assigned prefixes yet."), modalX + 22, itemY + 6, GuiHelper.TEXT_MUTED, false);
            } else {
                for (int i = 0; i < Math.min(assignedList.size(), 6); i++) {
                    UserPrefixEntry up = assignedList.get(i);
                    boolean itemHover = mouseX >= modalX + 20 && mouseX <= modalX + modalWidth - 20 && mouseY >= itemY && mouseY < itemY + 20;
                    boolean delHover = mouseX >= modalX + modalWidth - 75 && mouseX <= modalX + modalWidth - 24 && mouseY >= itemY + 2 && mouseY < itemY + 18;

                    graphics.fill(modalX + 20, itemY, modalX + modalWidth - 20, itemY + 20, itemHover ? GuiHelper.CARD_HOVER : GuiHelper.CARD_BG_ALT);

                    // Render player name with prefix and nick color
                    GuiHelper.renderCustomPrefixAndPlayer(graphics, this.font, up.getPrefixName(), up.getPrefixColor(), up.getNameColor(), up.getUsername(), modalX + 26, itemY + 6, false);

                    // Remove Button
                    graphics.fill(modalX + modalWidth - 72, itemY + 2, modalX + modalWidth - 24, itemY + 18, delHover ? 0xFFEF4444 : 0xFFDC2626);
                    graphics.text(this.font, Component.literal("Remove"), modalX + modalWidth - 64, itemY + 5, 0xFFFFFFFF, false);

                    itemY += 22;
                }
            }

            // Status message
            graphics.text(this.font, Component.literal(this.statusMessage), modalX + 105, modalY + modalHeight - 20, GuiHelper.TEXT_SECONDARY, false);

        } else {
            // ================= STEP 2 RENDER =================
            graphics.text(this.font, Component.literal("§c§lSky§4§lReview §8| §fAssigning to: §6" + targetPlayerName), modalX + 14, modalY + 9, GuiHelper.TEXT_TITLE, true);

            // 1. Nick Color Header
            graphics.text(this.font, Component.literal("§61. Choose Nickname Color (Цвет ника):"), modalX + 16, modalY + 34, GuiHelper.COLOR_GOLD, false);

            // 2. Prefix Template Table Header
            int tableY = modalY + 80;
            graphics.text(this.font, Component.literal("§62. Select Prefix Template (Выбор шаблона префикса):"), modalX + 16, tableY, GuiHelper.COLOR_GOLD, false);

            GuiHelper.renderCard(graphics, modalX + 16, tableY + 14, modalWidth - 32, 125, false);

            List<PrefixTemplateEntry> templates = RatingStorage.getPrefixTemplates();
            int itemY = tableY + 18;

            // Option 0: [None / No Prefix]
            boolean noneSelected = (selectedTemplate == null);
            boolean noneHover = mouseX >= modalX + 20 && mouseX <= modalX + modalWidth - 20 && mouseY >= itemY && mouseY < itemY + 20;
            graphics.fill(modalX + 20, itemY, modalX + modalWidth - 20, itemY + 20, noneSelected ? 0xFF0369A1 : (noneHover ? GuiHelper.CARD_HOVER : GuiHelper.CARD_BG_ALT));
            graphics.text(this.font, Component.literal("§8[No Prefix - Only Custom Nick Color]"), modalX + 26, itemY + 6, GuiHelper.TEXT_MUTED, false);
            if (noneSelected) {
                graphics.text(this.font, Component.literal("§a✓ Selected"), modalX + modalWidth - 90, itemY + 6, 0xFF55FF55, false);
            }
            itemY += 22;

            // Template options
            for (int i = 0; i < Math.min(templates.size(), 4); i++) {
                PrefixTemplateEntry t = templates.get(i);
                boolean isSelected = (selectedTemplate != null && selectedTemplate.getId().equals(t.getId()));
                boolean itemHover = mouseX >= modalX + 20 && mouseX <= modalX + modalWidth - 20 && mouseY >= itemY && mouseY < itemY + 20;

                graphics.fill(modalX + 20, itemY, modalX + modalWidth - 20, itemY + 20, isSelected ? 0xFF0369A1 : (itemHover ? GuiHelper.CARD_HOVER : GuiHelper.CARD_BG_ALT));

                GuiHelper.renderColoredString(graphics, this.font, t.getName(), t.getColor(), modalX + 26, itemY + 6, false);

                if (isSelected) {
                    graphics.text(this.font, Component.literal("§a✓ Selected"), modalX + modalWidth - 90, itemY + 6, 0xFF55FF55, false);
                }

                itemY += 22;
            }

            // 3. Live Preview Bar
            int previewY = modalY + 230;
            GuiHelper.renderCard(graphics, modalX + 16, previewY, modalWidth - 32, 28, false);
            graphics.text(this.font, Component.literal("§7Live Preview: "), modalX + 24, previewY + 10, GuiHelper.TEXT_MUTED, false);

            String pText = (selectedTemplate != null) ? selectedTemplate.getName() : "";
            String pColor = (selectedTemplate != null) ? selectedTemplate.getColor() : "§f";
            String nColor = getResolvedNickColorCode();

            GuiHelper.renderCustomPrefixAndPlayer(graphics, this.font, pText, pColor, nColor, targetPlayerName, modalX + 105, previewY + 10, isNickBold);

            // Status message
            graphics.text(this.font, Component.literal(this.statusMessage), modalX + 16, modalY + modalHeight - 42, GuiHelper.TEXT_SECONDARY, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }
}
