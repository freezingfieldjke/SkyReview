package com.skyblockrating.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class PlayerResolver {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .executor(Executors.newCachedThreadPool())
        .build();

    public static boolean isValidFormat(String ign) {
        return ign != null && ign.trim().matches("^[a-zA-Z0-9_]{2,16}$");
    }

    public static boolean isValidPlayer(String ign) {
        return isValidFormat(ign);
    }

    public static class PlayerIdentity {
        private final String name;
        private final String uuid;

        public PlayerIdentity(String name, String uuid) {
            this.name = name;
            this.uuid = (uuid != null) ? uuid.replace("-", "").toLowerCase() : null;
        }

        public String getName() {
            return name;
        }

        public String getUuid() {
            return uuid;
        }
    }

    public static void resolvePlayerAsync(String ign, Consumer<PlayerIdentity> callback) {
        if (!isValidFormat(ign)) {
            callback.accept(null);
            return;
        }

        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                String uuidUrl = "https://api.mojang.com/users/profiles/minecraft/" + ign.trim();
                HttpRequest uuidReq = HttpRequest.newBuilder()
                    .uri(URI.create(uuidUrl))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) SkyReview/1.0")
                    .GET()
                    .build();

                HttpResponse<String> response = HTTP_CLIENT.send(uuidReq, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    if (json.has("id") && json.has("name")) {
                        String officialName = json.get("name").getAsString();
                        String uuid = json.get("id").getAsString();
                        callback.accept(new PlayerIdentity(officialName, uuid));
                        return;
                    }
                }
                
                // Only reject if Mojang explicitly returns 404 (Not Found).
                // If 429 rate limit or timeout occurs, fallback to ign.trim() so reviews are never blocked!
                if (response.statusCode() == 404) {
                    callback.accept(null);
                } else {
                    callback.accept(new PlayerIdentity(ign.trim(), null));
                }
            } catch (Exception e) {
                callback.accept(isValidFormat(ign) ? new PlayerIdentity(ign.trim(), null) : null);
            }
        });
    }

    public static void validateSkyBlockPlayerAsync(String ign, Consumer<String> callback) {
        resolvePlayerAsync(ign, identity -> {
            if (identity != null) {
                callback.accept(identity.getName());
            } else {
                callback.accept(null);
            }
        });
    }
}
