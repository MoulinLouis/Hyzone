package io.hyvexa.core.vote;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.core.economy.FeatherStore;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class VoteManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new Gson();
    private static final VoteManager INSTANCE = new VoteManager();

    private VoteConfig config;
    private HttpClient httpClient;
    private final Map<UUID, String> onlinePlayers = new ConcurrentHashMap<>();

    private VoteManager() {}

    public static VoteManager getInstance() {
        return INSTANCE;
    }

    public void initialize(VoteConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        if (config.getSecretKey().isEmpty()) {
            LOGGER.atWarning().log("Vote secret key is empty -- vote rewards will not work");
        } else {
            LOGGER.atInfo().log("VoteManager initialized");
        }
    }

    public void shutdown() {
        onlinePlayers.clear();
    }

    public int getRewardPerVote() {
        return config != null ? config.getRewardPerVote() : 0;
    }

    public int getPollIntervalSeconds() {
        return config != null ? config.getPollIntervalSeconds() : 300;
    }

    public void registerPlayer(UUID playerId, String username) {
        if (playerId != null && username != null) {
            onlinePlayers.put(playerId, username);
        }
    }

    public void unregisterPlayer(UUID playerId) {
        if (playerId != null) {
            onlinePlayers.remove(playerId);
        }
    }

    public CompletableFuture<Integer> checkAndRewardAsync(UUID playerId) {
        if (playerId == null || config == null || config.getSecretKey().isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        String username = onlinePlayers.get(playerId);
        if (username == null) {
            return CompletableFuture.completedFuture(0);
        }
        return CompletableFuture.supplyAsync(() -> checkAndReward(playerId, username), HytaleServer.SCHEDULED_EXECUTOR);
    }

    public void pollAllPlayers() {
        if (config == null || config.getSecretKey().isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, String> entry : onlinePlayers.entrySet()) {
            try {
                int rewarded = checkAndReward(entry.getKey(), entry.getValue());
                if (rewarded > 0) {
                    sendRewardMessage(entry.getKey(), rewarded);
                }
            } catch (Exception e) {
                LOGGER.atWarning().log("Vote poll failed for " + entry.getValue() + ": " + e.getMessage());
            }
        }
    }

    private int checkAndReward(UUID playerId, String username) {
        try {
            JsonArray votes = fetchUnclaimedVotes(username);
            if (votes == null || votes.isEmpty()) {
                return 0;
            }

            int claimed = 0;
            for (JsonElement element : votes) {
                JsonObject vote = element.getAsJsonObject();
                String voteId = vote.has("id") ? vote.get("id").getAsString() : null;
                if (voteId == null) {
                    continue;
                }
                if (claimVote(voteId)) {
                    FeatherStore.getInstance().addFeathers(playerId, config.getRewardPerVote());
                    claimed++;
                }
            }
            return claimed;
        } catch (Exception e) {
            LOGGER.atWarning().log("Vote check failed for " + username + ": " + e.getMessage());
            return 0;
        }
    }

    private JsonArray fetchUnclaimedVotes(String username) {
        try {
            String url = config.getBaseUrl() + "/check"
                    + "?username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                    + "&secret_key=" + URLEncoder.encode(config.getSecretKey(), StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                LOGGER.atWarning().log("Vote check HTTP " + response.statusCode() + " for " + username);
                return null;
            }

            JsonObject root = GSON.fromJson(response.body(), JsonObject.class);
            if (root == null || !root.has("pending_votes")) {
                return null;
            }
            return root.getAsJsonArray("pending_votes");
        } catch (Exception e) {
            LOGGER.atWarning().log("Vote check request failed for " + username + ": " + e.getMessage());
            return null;
        }
    }

    private boolean claimVote(String voteId) {
        try {
            String url = config.getBaseUrl() + "/claim"
                    + "?vote_id=" + URLEncoder.encode(voteId, StandardCharsets.UTF_8)
                    + "&secret_key=" + URLEncoder.encode(config.getSecretKey(), StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return true;
            }
            LOGGER.atWarning().log("Vote claim HTTP " + response.statusCode() + " for vote_id=" + voteId);
            return false;
        } catch (Exception e) {
            LOGGER.atWarning().log("Vote claim request failed for vote_id=" + voteId + ": " + e.getMessage());
            return false;
        }
    }

    private void sendRewardMessage(UUID playerId, int voteCount) {
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            if (playerRef == null || !playerId.equals(playerRef.getUuid())) {
                continue;
            }
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                return;
            }
            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData() != null ? store.getExternalData().getWorld() : null;
            if (world == null) {
                return;
            }
            CompletableFuture.runAsync(() -> {
                if (!ref.isValid()) {
                    return;
                }
                Player player = ref.getStore().getComponent(ref, Player.getComponentType());
                if (player != null) {
                    int totalFeathers = voteCount * config.getRewardPerVote();
                    String suffix = voteCount > 1 ? " (x" + voteCount + ")" : "";
                    player.sendMessage(Message.join(
                            Message.raw("You received ").color("#a3e635"),
                            Message.raw(totalFeathers + " feathers").color("#4ade80").bold(true),
                            Message.raw(" for voting!" + suffix).color("#a3e635")
                    ));
                }
            }, world).exceptionally(ex -> {
                LOGGER.atWarning().log("Failed to send vote reward message: " + ex.getMessage());
                return null;
            });
            return;
        }
    }
}
