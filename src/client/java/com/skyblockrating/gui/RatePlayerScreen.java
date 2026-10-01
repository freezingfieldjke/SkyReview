package com.skyblockrating.gui;

import com.skyblockrating.config.AdminManager;
import com.skyblockrating.util.PlayerResolver;
import com.skyblockrating.util.TeammateHistoryManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class RatePlayerScreen extends Screen {
    private String targetPlayerName;
    private int selectedStars = 5;
    private String selectedCategory = "Dungeons";
    private String selectedSubTag = "";
    private String savedReviewText = "";

    private final String[] dungeonFloors = {"F1", "F2", "F3", "F4", "F5", "F6", "F7", "M1", "M2", "M3", "M4", "M5", "M6", "M7"};
    private final String[] kuudraTiers = {"Basic", "Hot", "Burning", "Fiery", "Infernal"};
    private final String[] otherSubTags = {"General", "Carry", "Helping"};

    private EditBox reviewBox;
    private Button subTagButton;
    private final Button[] categoryButtons = new Button[4];
    private final Button[] starButtons = new Button[5];
    private String warningMessage = "";

    public RatePlayerScreen(String targetPlayerName) {
        this(targetPlayerName, "Dungeons");
    }

    public RatePlayerScreen(String targetPlayerName, String initialCategory) {
        super(Component.literal("Rate Player: " + targetPlayerName));
        this.targetPlayerName = targetPlayerName;
        if (initialCategory != null && !initialCategory.trim().isEmpty()) {
            String cat = initialCategory.trim().toLowerCase();
            if (cat.contains("safari")) this.selectedCategory = "Safari";
            else if (cat.contains("kuudra")) this.selectedCategory = "Kuudra";
            else if (cat.contains("other")) this.selectedCategory = "Other";
            else this.selectedCategory = "Dungeons";
        }
    }

    @Override
    protected void init() {
        super.init();

        int cardWidth = 340;
        int cardHeight = 290;
        int cardX = (this.width - cardWidth) / 2;
        int cardY = (this.height - cardHeight) / 2;

        int fieldWidth = cardWidth - 36;
        int currentY = cardY + 40;

        // Category Pills (Dungeons, Kuudra, Safari, Other)
        String[] categories = {"Dungeons", "Kuudra", "Safari", "Other"};
        int catBtnW = (fieldWidth - 9) / 4;
        for (int i = 0; i < 4; i++) {
            final String cat = categories[i];
            final int idx = i;
            boolean isSel = cat.equalsIgnoreCase(selectedCategory);
            String label = (isSel ? "§6§l" : "§7") + cat;

            categoryButtons[i] = Button.builder(Component.literal(label), button -> {
                saveCurrentText();
                selectedCategory = cat;
                selectedSubTag = "";
                updateCategoryButtons();
                updateSubTagButtonText();
            })
            .bounds(cardX + 18 + i * (catBtnW + 3), currentY, catBtnW, 19)
            .build();
            this.addRenderableWidget(categoryButtons[i]);
        }

        currentY += 27;

        // SubTag Popup Selector Button
        this.subTagButton = Button.builder(
            Component.literal(getSubTagDefaultPrompt()),
            button -> {
                if (this.minecraft == null) return;
                saveCurrentText();
                if (selectedCategory.equals("Dungeons")) {
                    this.minecraft.setScreen(new SelectorPopupScreen(this, "Select Dungeons Floor", dungeonFloors, opt -> {
                        this.selectedSubTag = opt;
                        updateSubTagButtonText();
                    }));
                } else if (selectedCategory.equals("Kuudra")) {
                    this.minecraft.setScreen(new SelectorPopupScreen(this, "Select Kuudra Tier", kuudraTiers, opt -> {
                        this.selectedSubTag = opt;
                        updateSubTagButtonText();
                    }));
                } else if (selectedCategory.equals("Other")) {
                    this.minecraft.setScreen(new SelectorPopupScreen(this, "Select Sub-Category", otherSubTags, opt -> {
                        this.selectedSubTag = opt;
                        updateSubTagButtonText();
                    }));
                }
            }
        )
        .bounds(cardX + 18, currentY, fieldWidth, 20)
        .build();
        this.addRenderableWidget(this.subTagButton);
        updateSubTagButtonText();

        currentY += 29;

        // Star Rating Label & Buttons
        int starBtnWidth = (fieldWidth - 16) / 5;
        for (int i = 0; i < 5; i++) {
            final int starValue = i + 1;
            boolean active = starValue <= selectedStars;
            String starLabel = (active ? "§6§l" : "§8") + starValue + " ★";

            starButtons[i] = Button.builder(
                Component.literal(starLabel),
                button -> {
                    this.selectedStars = starValue;
                    updateStarButtons();
                }
            )
            .bounds(cardX + 18 + i * (starBtnWidth + 4), currentY, starBtnWidth, 22)
            .build();
            this.addRenderableWidget(starButtons[i]);
        }

        currentY += 46;

        // Review Text Box (Max 150 Chars) - preserving entered text!
        this.reviewBox = new EditBox(this.font, cardX + 18, currentY, fieldWidth, 20, Component.literal("Write Review"));
        this.reviewBox.setMaxLength(150);
        this.reviewBox.setHint(Component.literal("Write your constructive review here..."));
        this.reviewBox.setValue(this.savedReviewText);
        this.reviewBox.setResponder(t -> this.savedReviewText = t);
        this.addRenderableWidget(this.reviewBox);
        this.setInitialFocus(this.reviewBox);

        // Action Buttons at bottom
        int buttonY = cardY + cardHeight - 28;

        // Submit Button
        this.addRenderableWidget(
            Button.builder(Component.literal("✔ Submit Review"), button -> {
                saveCurrentText();
                String reviewText = this.savedReviewText.trim();

                if (!selectedCategory.equalsIgnoreCase("Safari") && (selectedSubTag == null || selectedSubTag.trim().isEmpty())) {
                    if (selectedCategory.equals("Dungeons")) {
                        this.warningMessage = "§cPlease select a floor!";
                    } else if (selectedCategory.equals("Kuudra")) {
                        this.warningMessage = "§cPlease select a tier!";
                    } else {
                        this.warningMessage = "§cPlease select a category!";
                    }
                    return;
                }

                if (reviewText.isEmpty()) {
                    this.warningMessage = "§cReview text is required!";
                    return;
                }

                if (!AdminManager.canSubmitReview()) {
                    long sec = AdminManager.getRemainingCooldownSeconds();
                    long min = sec / 60;
                    long remSec = sec % 60;
                    this.warningMessage = String.format("§eCooldown! Wait %dm %ds", min, remSec);
                    return;
                }

                String author = (this.minecraft != null && this.minecraft.getUser() != null) ? this.minecraft.getUser().getName() : "Player";
                if (!com.skyblockrating.auth.SessionAuthManager.isLicensedUser()) {
                    if (this.minecraft != null && this.minecraft.player != null) {
                        this.minecraft.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(com.skyblockrating.auth.SessionAuthManager.AUTH_REQUIRED_MSG));
                    }
                    this.warningMessage = "§cLicense required! Run /sr reload";
                    return;
                }
                if (com.skyblockrating.storage.RatingStorage.isCurrentClientBlocked()) {
                    if (this.minecraft != null && this.minecraft.player != null) {
                        this.minecraft.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c[SkyReview] Your account is restricted from posting reviews. If you disagree with this block, please write to us on Discord!"));
                    }
                    this.warningMessage = "§cAccount restricted from reviewing!";
                    return;
                }

                if (targetPlayerName.equalsIgnoreCase(author)) {
                    this.warningMessage = "§cr u srsly?";
                    return;
                }

                if (!selectedCategory.equalsIgnoreCase("Other")) {
                    if (!TeammateHistoryManager.hasPlayedWith(targetPlayerName, selectedCategory)) {
                        this.warningMessage = "§cMust have played " + selectedCategory + " with " + targetPlayerName + "!";
                        return;
                    }
                }

                this.warningMessage = "§bVerifying player IGN...";
                PlayerResolver.resolvePlayerAsync(targetPlayerName, identity -> {
                    if (identity == null || identity.getName() == null) {
                        this.warningMessage = "§cPlayer does not exist!";
                        return;
                    }

                    String officialIgn = identity.getName();
                    String targetUuid = identity.getUuid();

                    if (officialIgn.equalsIgnoreCase(author)) {
                        this.warningMessage = "§cr u srsly?";
                        return;
                    }

                    this.targetPlayerName = officialIgn;

                    if (this.minecraft != null) {
                        this.minecraft.execute(() -> {
                            String authorUuid = (this.minecraft.getUser() != null && this.minecraft.getUser().getProfileId() != null)
                                    ? this.minecraft.getUser().getProfileId().toString().replace("-", "").toLowerCase()
                                    : null;
                            LeaderboardScreen.submitPlayerReview(author, authorUuid, officialIgn, targetUuid, selectedCategory, selectedSubTag, selectedStars, reviewText);
                            AdminManager.recordReviewSubmission();
                            this.minecraft.setScreen(new LeaderboardScreen());
                        });
                    }
                });
            })
            .bounds(cardX + 18, buttonY, 145, 20)
            .build()
        );

        // Cancel Button
        this.addRenderableWidget(
            Button.builder(Component.literal("✕ Cancel"), button -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new LeaderboardScreen());
                }
            })
            .bounds(cardX + cardWidth - 18 - 110, buttonY, 110, 20)
            .build()
        );
    }

    private void saveCurrentText() {
        if (this.reviewBox != null) {
            this.savedReviewText = this.reviewBox.getValue();
        }
    }

    private void updateCategoryButtons() {
        String[] categories = {"Dungeons", "Kuudra", "Safari", "Other"};
        for (int i = 0; i < 4; i++) {
            if (categoryButtons[i] != null) {
                boolean isSel = categories[i].equalsIgnoreCase(selectedCategory);
                categoryButtons[i].setMessage(Component.literal((isSel ? "§6§l" : "§7") + categories[i]));
            }
        }
    }

    private String getSubTagDefaultPrompt() {
        if (selectedSubTag != null && !selectedSubTag.isEmpty()) {
            if (selectedCategory.equals("Dungeons")) return "Floor: " + selectedSubTag + " ▼";
            if (selectedCategory.equals("Kuudra")) return "Kuudra Tier: " + selectedSubTag + " ▼";
            if (selectedCategory.equals("Other")) return "Category: " + selectedSubTag + " ▼";
            return selectedSubTag + " ▼";
        }
        if (selectedCategory.equals("Dungeons")) return "Select floor ▼";
        if (selectedCategory.equals("Kuudra")) return "Select tier ▼";
        if (selectedCategory.equals("Other")) return "Select category ▼";
        return "Select ▼";
    }

    private void updateSubTagButtonText() {
        if (subTagButton == null) return;
        if (selectedCategory.equals("Safari")) {
            subTagButton.visible = false;
        } else {
            subTagButton.visible = true;
            subTagButton.setMessage(Component.literal(getSubTagDefaultPrompt()));
        }
    }

    private void updateStarButtons() {
        for (int i = 0; i < 5; i++) {
            if (starButtons[i] != null) {
                int val = i + 1;
                boolean active = val <= selectedStars;
                starButtons[i].setMessage(Component.literal((active ? "§6§l" : "§8") + val + " ★"));
            }
        }
    }

    private String getStarRatingDescription(int stars) {
        return switch (stars) {
            case 1 -> "§c1 Star - poop";
            case 2 -> "§c2 Stars - bad";
            case 3 -> "§e3 Stars - mid";
            case 4 -> "§a4 Stars - good";
            case 5 -> "§65 Stars - goat";
            default -> "";
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, GuiHelper.BG_OVERLAY);

        int cardWidth = 340;
        int cardHeight = 290;
        int cardX = (this.width - cardWidth) / 2;
        int cardY = (this.height - cardHeight) / 2;

        GuiHelper.renderPanel(graphics, cardX, cardY, cardWidth, cardHeight, 30, GuiHelper.COLOR_GOLD);

        // Title
        graphics.text(this.font, Component.literal("§6§lRate Player: §f@" + targetPlayerName), cardX + 14, cardY + 9, GuiHelper.TEXT_TITLE, true);

        // Star description label
        String starDesc = getStarRatingDescription(selectedStars);
        graphics.text(this.font, Component.literal(starDesc), cardX + 18, cardY + 124, GuiHelper.TEXT_PRIMARY, false);

        // Review character count label
        int charCount = reviewBox != null ? reviewBox.getValue().length() : savedReviewText.length();
        String charStr = "§8(" + charCount + "/150)";
        graphics.text(this.font, Component.literal("Review comment " + charStr + ":"), cardX + 18, cardY + 145, GuiHelper.TEXT_SECONDARY, false);

        if (!warningMessage.isEmpty()) {
            graphics.text(this.font, Component.literal(warningMessage), cardX + 18, cardY + cardHeight - 44, GuiHelper.COLOR_RED, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        boolean inBox = (this.reviewBox != null && mouseX >= reviewBox.getX() && mouseX <= reviewBox.getX() + reviewBox.getWidth()
                && mouseY >= reviewBox.getY() && mouseY <= reviewBox.getY() + reviewBox.getHeight());

        if (inBox && this.reviewBox.isFocused()) {
            // Clicking on an active input line deactivates it
            this.reviewBox.setFocused(false);
            this.setFocused(null);
            return true;
        }

        boolean handled = super.mouseClicked(event, doubleClick);

        if (!inBox && this.reviewBox != null && this.reviewBox.isFocused()) {
            // Clicking anywhere outside deactivates it
            this.reviewBox.setFocused(false);
            if (this.getFocused() == this.reviewBox) {
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
