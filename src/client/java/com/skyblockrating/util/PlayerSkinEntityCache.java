package com.skyblockrating.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.ProfileResolver;
import net.minecraft.world.entity.LivingEntity;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlayerSkinEntityCache {
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Map<String, FakePlayerMannequin> MANNEQUIN_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Long> FETCH_TIMESTAMPS = new ConcurrentHashMap<>();

    public static void clearCache() {
        MANNEQUIN_CACHE.clear();
        FETCH_TIMESTAMPS.clear();
    }

    public static LivingEntity getOrFetchMannequin(String username) {
        if (username == null || username.trim().isEmpty()) {
            Minecraft client = Minecraft.getInstance();
            return client != null ? client.player : null;
        }

        String cleanName = username.trim();
        Minecraft client = Minecraft.getInstance();
        if (client == null) return null;

        // 1. If local player -> return clean FakePlayerMannequin WITHOUT armor!
        if (client.getUser() != null && client.getUser().getName().equalsIgnoreCase(cleanName)) {
            FakePlayerMannequin cachedSelf = MANNEQUIN_CACHE.get(cleanName.toLowerCase());
            if (cachedSelf != null) {
                return cachedSelf;
            }
            GameProfile selfProfile = (client.player != null) ? client.player.getGameProfile() : new GameProfile(client.getUser().getProfileId(), client.getUser().getName());
            FakePlayerMannequin selfMannequin = new FakePlayerMannequin(selfProfile, Component.literal(cleanName));
            MANNEQUIN_CACHE.put(cleanName.toLowerCase(), selfMannequin);
            return selfMannequin;
        }

        // 2. If online in current lobby/world tablist -> copy profile & render clean mannequin without armor
        if (client.getConnection() != null) {
            net.minecraft.client.multiplayer.PlayerInfo info = client.getConnection().getPlayerInfo(cleanName);
            if (info != null && info.getProfile() != null) {
                FakePlayerMannequin cached = MANNEQUIN_CACHE.get(cleanName.toLowerCase());
                if (cached != null) return cached;

                FakePlayerMannequin onlineMannequin = new FakePlayerMannequin(info.getProfile(), Component.literal(cleanName));
                MANNEQUIN_CACHE.put(cleanName.toLowerCase(), onlineMannequin);
                return onlineMannequin;
            }
        }

        // 3. If already cached from Mojang ProfileResolver
        FakePlayerMannequin cached = MANNEQUIN_CACHE.get(cleanName.toLowerCase());
        if (cached != null) {
            return cached;
        }

        // 4. Create initial placeholder mannequin with offline profile
        UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + cleanName).getBytes(StandardCharsets.UTF_8));
        GameProfile initialProfile = new GameProfile(offlineUuid, cleanName);
        FakePlayerMannequin placeholder = new FakePlayerMannequin(initialProfile, Component.literal(cleanName));
        MANNEQUIN_CACHE.put(cleanName.toLowerCase(), placeholder);

        // 5. Fetch official GameProfile from Mojang using Minecraft's built-in ProfileResolver
        long now = System.currentTimeMillis();
        Long lastFetch = FETCH_TIMESTAMPS.get(cleanName.toLowerCase());
        if (lastFetch == null || now - lastFetch > 30000) {
            FETCH_TIMESTAMPS.put(cleanName.toLowerCase(), now);
            fetchGameProfileAsync(cleanName);
        }

        return placeholder;
    }

    private static void fetchGameProfileAsync(String username) {
        CompletableFuture.runAsync(() -> {
            try {
                Minecraft client = Minecraft.getInstance();
                if (client == null || client.services() == null) return;
                ProfileResolver resolver = client.services().profileResolver();
                if (resolver == null) return;

                Optional<GameProfile> optProfile = resolver.fetchByName(username);
                if (optProfile.isPresent()) {
                    GameProfile officialProfile = optProfile.get();
                    client.execute(() -> {
                        FakePlayerMannequin resolvedMannequin = new FakePlayerMannequin(officialProfile, Component.literal(username));
                        MANNEQUIN_CACHE.put(username.toLowerCase(), resolvedMannequin);
                    });
                }
            } catch (Throwable ignored) {}
        }, EXECUTOR);
    }
}
