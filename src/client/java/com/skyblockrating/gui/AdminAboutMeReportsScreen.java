package com.skyblockrating.gui;

import com.skyblockrating.config.OwnerLock;
import com.skyblockrating.data.ReportEntry;
import com.skyblockrating.storage.RatingStorage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.*;

public class AdminAboutMeReportsScreen extends Screen {
    private final Screen parentScreen;
    private EditBox searchBox;
    private double scrollOffset = 0;
    private static final int ITEM_HEIGHT = 86;
    private String statusMessage = "";

    public static class GroupedAboutMeReport {
        private final String targetPlayer;
        private final String reportedBio;
        private final String reportedDiscord;
        private final List<ReportEntry> reports = new ArrayList<>();

        public GroupedAboutMeReport(ReportEntry initial) {
            this.targetPlayer = initial.getTargetPlayer();
            this.reportedBio = initial.getReportedBio();
            this.reportedDiscord = initial.getReportedDiscord();
            this.reports.add(initial);
        }

        public void addReport(ReportEntry rep) {
            this.reports.add(rep);
        }

        public String getTargetPlayer() {
            return targetPlayer;
        }

        public String getReportedBio() {
            com.skyblockrating.data.UserProfileEntry p = RatingStorage.getUserProfile(targetPlayer);
            if (p != null) return p.getBio();
            return reportedBio != null ? reportedBio : "";
        }

        public String getReportedDiscord() {
            com.skyblockrating.data.UserProfileEntry p = RatingStorage.getUserProfile(targetPlayer);
            if (p != null) return p.getDiscordTag();
            return reportedDiscord != null ? reportedDiscord : "";
        }

        public List<ReportEntry> getReports() {
            return reports;
        }

        public int getReportCount() {
            return reports.size();
        }

        public boolean matches(String query) {
            if (query == null || query.trim().isEmpty()) return true;
            String q = query.toLowerCase().trim();
            if (targetPlayer != null && targetPlayer.toLowerCase().contains(q)) return true;
            if (reportedBio != null && reportedBio.toLowerCase().contains(q)) return true;
            if (reportedDiscord != null && reportedDiscord.toLowerCase().contains(q)) return true;
            for (ReportEntry r : reports) {
                if (r.getReporter() != null && r.getReporter().toLowerCase().contains(q)) return true;
                if (r.getReason() != null && r.getReason().toLowerCase().contains(q)) return true;
            }
            return false;
        }
    }

    public AdminAboutMeReportsScreen(Screen parentScreen) {
        super(Component.literal("About Me Reports - Admin"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();

        int modalWidth = 560;
        int modalHeight = 330;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        int controlY = modalY + 34;
        String prevSearch = (this.searchBox != null) ? this.searchBox.getValue() : "";
        this.searchBox = new EditBox(this.font, modalX + 14, controlY, 200, 18, Component.literal("Search"));
        this.searchBox.setHint(Component.literal("Search player, reporter, reason..."));
        if (!prevSearch.isEmpty()) this.searchBox.setValue(prevSearch);
        this.searchBox.setResponder(txt -> {
            this.scrollOffset = 0;
            clampScroll();
        });
        this.addRenderableWidget(this.searchBox);

        // Action Buttons
        int buttonY = modalY + modalHeight - 26;

        this.addRenderableWidget(
            Button.builder(Component.literal("← Back"), button -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(this.parentScreen);
                }
            })
            .bounds(modalX + 14, buttonY, 80, 20)
            .build()
        );

        this.addRenderableWidget(
            Button.builder(Component.literal("✕ Close"), button -> this.onClose())
            .bounds(modalX + modalWidth - 84, buttonY, 70, 20)
            .build()
        );
    }

    private List<GroupedAboutMeReport> getGroupedReports() {
        List<ReportEntry> all = RatingStorage.getPendingReports();
        Map<String, GroupedAboutMeReport> map = new LinkedHashMap<>();

        for (ReportEntry r : all) {
            if (!r.isAboutMe()) continue;
            String key = (r.getTargetPlayer() != null) ? r.getTargetPlayer().toLowerCase().trim() : "";
            if (key.isEmpty()) continue;

            if (map.containsKey(key)) {
                map.get(key).addReport(r);
            } else {
                map.put(key, new GroupedAboutMeReport(r));
            }
        }

        String q = (searchBox != null) ? searchBox.getValue() : "";
        List<GroupedAboutMeReport> result = new ArrayList<>();
        for (GroupedAboutMeReport g : map.values()) {
            if (g.matches(q)) {
                result.add(g);
            }
        }
        return result;
    }

    private void clampScroll() {
        List<GroupedAboutMeReport> list = getGroupedReports();
        int visibleH = 330 - 78;
        int maxScroll = Math.max(0, list.size() * ITEM_HEIGHT - visibleH);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        if (scrollOffset < 0) scrollOffset = 0;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int modalWidth = 560;
        int modalHeight = 330;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        int listX = modalX + 14;
        int listY = modalY + 56;
        int listW = modalWidth - 28;
        int visibleH = modalHeight - 88;

        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + visibleH) {
            scrollOffset -= verticalAmount * 24;
            clampScroll();
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, GuiHelper.BG_OVERLAY);

        int modalWidth = 560;
        int modalHeight = 330;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        GuiHelper.renderPanel(graphics, modalX, modalY, modalWidth, modalHeight, 30, GuiHelper.HEADER_ACCENT_ADMIN);

        // Header Title
        graphics.text(this.font, Component.literal("§c§lSky§4§lReview §8| §fAbout Me Reports"), modalX + 14, modalY + 9, GuiHelper.TEXT_TITLE, true);

        List<GroupedAboutMeReport> groups = getGroupedReports();
        int rawCount = groups.size();
        String badge = rawCount > 0 ? "§c" + rawCount + " Pending" : "§a0 Pending";
        graphics.text(this.font, Component.literal(badge), modalX + modalWidth - 14 - this.font.width(badge), modalY + 9, GuiHelper.COLOR_RED, true);

        if (!OwnerLock.isOwner()) {
            graphics.text(this.font, Component.literal("§cACCESS DENIED: Owner Privileges Required"), modalX + 120, modalY + 140, GuiHelper.COLOR_RED, true);
            super.extractRenderState(graphics, mouseX, mouseY, delta);
            return;
        }

        int listX = modalX + 14;
        int listY = modalY + 56;
        int listW = modalWidth - 28;
        int visibleH = modalHeight - 88;

        GuiHelper.renderCard(graphics, listX, listY, listW, visibleH, false);

        if (groups.isEmpty()) {
            graphics.text(this.font, Component.literal("§a✔ No pending About Me reports! All clear."), listX + 16, listY + 30, 0xFF34D399, false);
        } else {
            int startIndex = Math.max(0, (int) (scrollOffset / ITEM_HEIGHT));
            int endIndex = Math.min(groups.size(), startIndex + (visibleH / ITEM_HEIGHT) + 2);

            for (int i = startIndex; i < endIndex; i++) {
                GroupedAboutMeReport group = groups.get(i);
                int itemY = listY + 4 + (i * ITEM_HEIGHT) - (int) scrollOffset;

                if (itemY + ITEM_HEIGHT - 4 <= listY || itemY >= listY + visibleH) {
                    continue;
                }

                boolean hovered = mouseX >= listX + 4 && mouseX <= listX + listW - 4 && mouseY >= itemY && mouseY <= itemY + ITEM_HEIGHT - 4;
                graphics.fill(listX + 4, itemY, listX + listW - 4, itemY + ITEM_HEIGHT - 4, hovered ? GuiHelper.CARD_HOVER : GuiHelper.CARD_BG_ALT);

                // Target Player Header
                graphics.text(this.font, Component.literal("§e👤 @" + group.getTargetPlayer()), listX + 10, itemY + 5, GuiHelper.COLOR_YELLOW, true);
                String repCountBadge = "§c[" + group.getReportCount() + " " + (group.getReportCount() == 1 ? "report" : "reports") + "]";
                graphics.text(this.font, Component.literal(repCountBadge), listX + 140, itemY + 5, GuiHelper.COLOR_RED, true);

                if (!group.getReportedDiscord().isEmpty()) {
                    graphics.text(this.font, Component.literal("§9Discord: §b" + group.getReportedDiscord()), listX + 220, itemY + 5, 0xFF60A5FA, false);
                }

                // Bio preview (truncate if long)
                String bioStr = group.getReportedBio().replace("\n", " ");
                if (bioStr.length() > 65) bioStr = bioStr.substring(0, 62) + "...";
                graphics.text(this.font, Component.literal("§7Bio: §f\"" + (bioStr.isEmpty() ? "No bio" : bioStr) + "\""), listX + 10, itemY + 22, GuiHelper.TEXT_PRIMARY, false);

                // Reporters & Reasons
                StringBuilder repSb = new StringBuilder("§8Reported by: ");
                for (int rIdx = 0; rIdx < Math.min(3, group.getReports().size()); rIdx++) {
                    ReportEntry r = group.getReports().get(rIdx);
                    if (rIdx > 0) repSb.append(", ");
                    repSb.append("§c@").append(r.getReporter()).append(" §8(§7").append(r.getReason()).append("§8)");
                }
                if (group.getReports().size() > 3) {
                    repSb.append(" +").append(group.getReports().size() - 3).append(" more");
                }
                graphics.text(this.font, Component.literal(repSb.toString()), listX + 10, itemY + 38, GuiHelper.TEXT_SECONDARY, false);

                // Action Buttons at bottom right of item
                // 1. Clear Bio Button (clears ONLY text bio)
                int btnClearBioX = listX + listW - 325;
                int btnClearBioY = itemY + 56;
                boolean hClearBio = mouseX >= btnClearBioX && mouseX <= btnClearBioX + 76 && mouseY >= btnClearBioY && mouseY <= btnClearBioY + 18;
                graphics.fill(btnClearBioX, btnClearBioY, btnClearBioX + 76, btnClearBioY + 18, hClearBio ? 0xFFDC2626 : 0xFF991B1B);
                graphics.text(this.font, Component.literal("🗑 Clear Bio"), btnClearBioX + 6, btnClearBioY + 5, 0xFFFFFFFF, false);

                // 2. Clear Discord Button (clears ONLY discord tag)
                int btnClearDiscX = listX + listW - 245;
                int btnClearDiscY = itemY + 56;
                boolean hClearDisc = mouseX >= btnClearDiscX && mouseX <= btnClearDiscX + 80 && mouseY >= btnClearDiscY && mouseY <= btnClearDiscY + 18;
                graphics.fill(btnClearDiscX, btnClearDiscY, btnClearDiscX + 80, btnClearDiscY + 18, hClearDisc ? 0xFFDC2626 : 0xFF7F1D1D);
                graphics.text(this.font, Component.literal("✂ Clear Disc"), btnClearDiscX + 6, btnClearDiscY + 5, 0xFFFFFFFF, false);

                // 3. Block User Button
                int btnBlockX = listX + listW - 160;
                int btnBlockY = itemY + 56;
                boolean hBlock = mouseX >= btnBlockX && mouseX <= btnBlockX + 75 && mouseY >= btnBlockY && mouseY <= btnBlockY + 18;
                graphics.fill(btnBlockX, btnBlockY, btnBlockX + 75, btnBlockY + 18, hBlock ? 0xFFB45309 : 0xFF78350F);
                graphics.text(this.font, Component.literal("🚫 Block"), btnBlockX + 14, btnBlockY + 5, 0xFFFFFFFF, false);

                // 4. Dismiss Report Button
                int btnDismissX = listX + listW - 80;
                int btnDismissY = itemY + 56;
                boolean hDismiss = mouseX >= btnDismissX && mouseX <= btnDismissX + 72 && mouseY >= btnDismissY && mouseY <= btnDismissY + 18;
                graphics.fill(btnDismissX, btnDismissY, btnDismissX + 72, btnDismissY + 18, hDismiss ? 0xFF059669 : 0xFF065F46);
                graphics.text(this.font, Component.literal("✔ Dismiss"), btnDismissX + 8, btnDismissY + 5, 0xFFFFFFFF, false);
            }
        }

        // Status Message
        if (!statusMessage.isEmpty()) {
            graphics.text(this.font, Component.literal(statusMessage), modalX + 105, modalY + modalHeight - 20, GuiHelper.TEXT_PRIMARY, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!OwnerLock.isOwner()) return super.mouseClicked(event, doubleClick);

        double mouseX = event.x();
        double mouseY = event.y();

        int modalWidth = 560;
        int modalHeight = 330;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        int listX = modalX + 14;
        int listY = modalY + 56;
        int listW = modalWidth - 28;
        int visibleH = modalHeight - 88;

        List<GroupedAboutMeReport> groups = getGroupedReports();
        int startIndex = Math.max(0, (int) (scrollOffset / ITEM_HEIGHT));
        int endIndex = Math.min(groups.size(), startIndex + (visibleH / ITEM_HEIGHT) + 2);

        for (int i = startIndex; i < endIndex; i++) {
            GroupedAboutMeReport group = groups.get(i);
            int itemY = listY + 4 + (i * ITEM_HEIGHT) - (int) scrollOffset;

            if (itemY + ITEM_HEIGHT - 4 <= listY || itemY >= listY + visibleH) {
                continue;
            }

            // 1. Clear Bio Button (clears ONLY text bio)
            int btnClearBioX = listX + listW - 325;
            int btnClearBioY = itemY + 56;
            if (mouseX >= btnClearBioX && mouseX <= btnClearBioX + 76 && mouseY >= btnClearBioY && mouseY <= btnClearBioY + 18) {
                this.statusMessage = "§bClearing bio of @" + group.getTargetPlayer() + "...";
                RatingStorage.clearUserBioAsync(group.getTargetPlayer(), success -> {
                    if (success) {
                        this.statusMessage = "§aCleared bio of @" + group.getTargetPlayer() + "! (Discord kept)";
                        rebuildWidgets();
                    } else {
                        this.statusMessage = "§cFailed to clear bio.";
                    }
                });
                return true;
            }

            // 2. Clear Discord Button (clears ONLY discord tag)
            int btnClearDiscX = listX + listW - 245;
            int btnClearDiscY = itemY + 56;
            if (mouseX >= btnClearDiscX && mouseX <= btnClearDiscX + 80 && mouseY >= btnClearDiscY && mouseY <= btnClearDiscY + 18) {
                this.statusMessage = "§bClearing Discord of @" + group.getTargetPlayer() + "...";
                RatingStorage.clearUserDiscordAsync(group.getTargetPlayer(), success -> {
                    if (success) {
                        this.statusMessage = "§aCleared Discord tag of @" + group.getTargetPlayer() + "! (Bio kept)";
                        rebuildWidgets();
                    } else {
                        this.statusMessage = "§cFailed to clear Discord tag.";
                    }
                });
                return true;
            }

            // 3. Block User Button
            int btnBlockX = listX + listW - 160;
            int btnBlockY = itemY + 56;
            if (mouseX >= btnBlockX && mouseX <= btnBlockX + 75 && mouseY >= btnBlockY && mouseY <= btnBlockY + 18) {
                this.statusMessage = "§bBlocking @" + group.getTargetPlayer() + "...";
                RatingStorage.blockUserAsync(group.getTargetPlayer(), "About Me profile violation", success -> {
                    if (success) {
                        this.statusMessage = "§aRestricted @" + group.getTargetPlayer() + " from reviewing/profiles!";
                        rebuildWidgets();
                    } else {
                        this.statusMessage = "§cFailed to block user.";
                    }
                });
                return true;
            }

            // 4. Dismiss Report Button
            int btnDismissX = listX + listW - 80;
            int btnDismissY = itemY + 56;
            if (mouseX >= btnDismissX && mouseX <= btnDismissX + 72 && mouseY >= btnDismissY && mouseY <= btnDismissY + 18) {
                this.statusMessage = "§bDismissing reports for @" + group.getTargetPlayer() + "...";
                for (ReportEntry r : group.getReports()) {
                    RatingStorage.deleteReportAsync(r.getReportId());
                }
                RatingStorage.getPendingReports().removeIf(r -> r.isAboutMe() && group.getTargetPlayer().equalsIgnoreCase(r.getTargetPlayer()));
                this.statusMessage = "§aDismissed reports for @" + group.getTargetPlayer() + "!";
                rebuildWidgets();
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
