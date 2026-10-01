package com.skyblockrating.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.Vec3;

public class FakePlayerMannequin extends ClientMannequin {
    private final ResolvableProfile profile;
    private final Component customDisplayName;
    private volatile PlayerSkin currentSkin = DEFAULT_SKIN;

    public FakePlayerMannequin(GameProfile gameProfile, Component customDisplayName) {
        super(Minecraft.getInstance().level != null ? Minecraft.getInstance().level : null, Minecraft.getInstance().playerSkinRenderCache());
        this.customDisplayName = customDisplayName;
        
        ResolvableProfile resProfile = ResolvableProfile.createResolved(gameProfile);
        try {
            if (Minecraft.getInstance().services() != null && Minecraft.getInstance().services().profileResolver() != null) {
                resProfile.resolveProfile(Minecraft.getInstance().services().profileResolver());
            }
        } catch (Throwable ignored) {}
        this.profile = resProfile;

        try {
            this.getEntityData().set(DATA_PROFILE, this.profile);
        } catch (Throwable ignored) {}

        try {
            if (Minecraft.getInstance().playerSkinRenderCache() != null) {
                Minecraft.getInstance().playerSkinRenderCache().lookup(this.profile).whenComplete((skinOpt, err) -> {
                    if (skinOpt != null && skinOpt.isPresent()) {
                        this.currentSkin = skinOpt.get().playerSkin();
                    }
                });
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public boolean shouldShowName() {
        return false;
    }

    @Override
    public ResolvableProfile getProfile() {
        return this.profile;
    }

    @Override
    public Component getDisplayName() {
        return this.customDisplayName;
    }

    @Override
    public PlayerSkin getSkin() {
        return this.currentSkin;
    }

    @Override
    public boolean isModelPartShown(PlayerModelPart part) {
        return part != PlayerModelPart.CAPE;
    }

    @Override
    public Vec3 position() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            Entity cam = mc.getCameraEntity();
            if (cam != null) {
                return cam.position();
            }
        }
        return super.position();
    }
}
