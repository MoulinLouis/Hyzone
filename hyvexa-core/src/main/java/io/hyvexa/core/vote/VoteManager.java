package io.hyvexa.core.vote;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class VoteManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new Gson();
    private static final VoteManager INSTANCE = new VoteManager();
    private static final int IO_THREAD_COUNT = 4;
    private static final int REWARD_THREAD_COUNT = 2;
    private static final int MAX_POLL_CONCURRENCY = 4;
    private static final int FAILURE_BACKOFF_THRESHOLD = 3;
    private static final long INITIAL_BACKOFF_MS = 5_000L;
    private static final long MAX_BACKOFF_MS = 60_000L;
    private static final AtomicInteger IO_THREAD_ID = new AtomicInteger(1);
    private static final AtomicInteger REWARD_THREAD_ID = new AtomicInteger(1);

    private VoteConfig config;
    private HttpClient httpClient;
    private volatile ExecutorService ioExecutor;
    private volatile ExecutorService rewardExecutor;
    private VoteStore voteStore;
    private final Map<UUID, String> onlinePlayers = new ConcurrentHashMap<>();
    private final AtomicBoolean pollInProgress = new AtomicBoolean(false);
    private final AtomicInteger consecutiveBackendFailures = new AtomicInteger();
    private volatile long pollBackoffUntilMs;

    private VoteManager() {
    }

    public static VoteManager getInstance() {
        return INSTANCE;
    }

    public synchronized void initialize(VoteConfig config) {
        VoteStore defaultVoteStore = new VoteStore();
        defaultVoteStore.initialize();
        initialize(config, defaultVoteStore);
    }

    public synchronized void initialize(VoteConfig config, VoteStore voteStore) {
        shutdownExecutors();
        this.config = config;
        this.voteStore = voteStore;
        this.ioExecutor = Executors.newFixedThreadPool(IO_THREAD_COUNT, runnable -> {
            Thread t = new Thread(runnable, "VoteIO-" + IO_THREAD_ID.getAndIncrement());
            t.setDaemon(true);
            return t;
        });
        this.rewardExecutor = Executors.newFixedThreadPool(REWARD_THREAD_COUNT, runnable -> {
            Thread t = new Thread(runnable, "VoteReward-" + REWARD_THREAD_ID.getAndIncrement());
            t.setDaemon(true);
            return t;
        });
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .executor(this.ioExecutor)
                .build();
        pollInProgress.set(false);
        consecutiveBackendFailures.set(0);
        pollBackoffUntilMs = 0L;
        if (config.getSecretKey().isEmpty()) {
            LOGGER.atWarning().log("Vote secret key is empty -- vote rewards will not work");
        } else {
            LOGGER.atInfo().log("VoteManager initialized");
        }
    }

    public void shutdown() {
        onlinePlayers.clear();
        pollInProgress.set(false);
        consecutiveBackendFailures.set(0);
        pollBackoffUntilMs = 0L;
        httpClient = null;
        voteStore = null;
        shutdownExecutors();
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
        VoteConfig activeConfig = config;
        if (playerId == null || activeConfig == null || activeConfig.getSecretKey().isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        String username = onlinePlayers.get(playerId);
        if (username == null) {
            return CompletableFuture.completedFuture(0);
        }
        return checkAndRewardAsync(playerId, username, false);
    }

    public void pollAllPlayers() {
        VoteConfig activeConfig = config;
        if (activeConfig == null || activeConfig.getSecretKey().isEmpty() || isPollBackoffActive()) {
            return;
        }
        if (!pollInProgress.compareAndSet(false, true)) {
            return;
        }
        List<Map.Entry<UUID, String>> snapshot = new ArrayList<>(onlinePlayers.entrySet());
        if (snapshot.isEmpty()) {
            pollInProgress.set(false);
            return;
        }
        int workerCount = Math.min(MAX_POLL_CONCURRENCY, snapshot.size());
        AtomicInteger nextIndex = new AtomicInteger();
        AtomicInteger workersRemaining = new AtomicInteger(workerCount);
        for (int i = 0; i < workerCount; i++) {
            schedulePollStep(snapshot, nextIndex, workersRemaining);
        }
    }

    private CompletableFuture<Integer> checkAndRewardAsync(UUID playerId, String username, boolean scheduledPoll) {
        VoteConfig activeConfig = config;
        if (playerId == null || username == null || activeConfig == null || activeConfig.getSecretKey().isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        if (scheduledPoll && isPollBackoffActive()) {
            return CompletableFuture.completedFuture(0);
        }
        return fetchUnclaimedVotesAsync(username)
                .thenCompose(votes -> claimVotesAndRewardAsync(playerId, username, votes))
                .exceptionally(ex -> {
                    LOGGER.atWarning().withCause(ex).log("Vote check failed for " + username);
                    return 0;
                });
    }

    private CompletableFuture<Integer> claimVotesAndRewardAsync(UUID playerId, String username, JsonArray votes) {
        if (votes == null || votes.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        CompletableFuture<Integer> claimedCountFuture = CompletableFuture.completedFuture(0);
        for (JsonElement element : votes) {
            String voteId = extractVoteId(element);
            if (voteId == null) {
                continue;
            }
            claimedCountFuture = claimedCountFuture.thenCompose(claimedCount ->
                    claimVoteAsync(voteId).thenApply(claimed -> {
                        if (claimed && voteStore != null) {
                            voteStore.recordVote(playerId, username, "hytale.game");
                        }
                        return claimed ? claimedCount + 1 : claimedCount;
                    }));
        }
        return claimedCountFuture.thenCompose(claimedCount ->
                persistRewardAsync(playerId, claimedCount).thenApply(ignored -> {
                    if (claimedCount > 0) {
                        broadcastVoteMessage(username, "hytale.game");
                    }
                    return claimedCount;
                }))
                .exceptionally(ex -> {
                    LOGGER.atWarning().withCause(ex).log("Vote reward flow failed for " + username);
                    return 0;
                });
    }

    private CompletableFuture<JsonArray> fetchUnclaimedVotesAsync(String username) {
        VoteConfig activeConfig = config;
        HttpClient client = httpClient;
        if (activeConfig == null || client == null) {
            return CompletableFuture.completedFuture(new JsonArray());
        }
        String url = activeConfig.getBaseUrl() + "/check"
                + "?username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&secret_key=" + URLEncoder.encode(activeConfig.getSecretKey(), StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((response, throwable) -> {
                    if (throwable != null) {
                        recordBackendFailure("check request", username, throwable);
                        return new JsonArray();
                    }
                    if (response.statusCode() != 200) {
                        recordBackendFailure("check HTTP " + response.statusCode(), username, null);
                        return new JsonArray();
                    }
                    try {
                        JsonObject root = GSON.fromJson(response.body(), JsonObject.class);
                        JsonArray pendingVotes = (root != null && root.has("pending_votes") && root.get("pending_votes").isJsonArray())
                                ? root.getAsJsonArray("pending_votes")
                                : new JsonArray();
                        recordBackendSuccess();
                        return pendingVotes;
                    } catch (Exception e) {
                        recordBackendFailure("check parse", username, e);
                        return new JsonArray();
                    }
                });
    }

    private CompletableFuture<Boolean> claimVoteAsync(String voteId) {
        VoteConfig activeConfig = config;
        HttpClient client = httpClient;
        if (activeConfig == null || client == null) {
            return CompletableFuture.completedFuture(false);
        }
        String url = activeConfig.getBaseUrl() + "/claim"
                + "?vote_id=" + URLEncoder.encode(voteId, StandardCharsets.UTF_8)
                + "&secret_key=" + URLEncoder.encode(activeConfig.getSecretKey(), StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((response, throwable) -> {
                    if (throwable != null) {
                        recordBackendFailure("claim request", voteId, throwable);
                        return false;
                    }
                    if (response.statusCode() != 200) {
                        recordBackendFailure("claim HTTP " + response.statusCode(), voteId, null);
                        return false;
                    }
                    recordBackendSuccess();
                    return true;
                });
    }

    private CompletableFuture<Void> persistRewardAsync(UUID playerId, int voteCount) {
        VoteConfig activeConfig = config;
        ExecutorService executor = rewardExecutor;
        if (playerId == null || voteCount <= 0 || activeConfig == null || executor == null) {
            return CompletableFuture.completedFuture(null);
        }
        long feathers = (long) voteCount * activeConfig.getRewardPerVote();
        try {
            return CompletableFuture.runAsync(() -> FeatherStore.getInstance().addFeathers(playerId, feathers), executor)
                    .exceptionally(ex -> {
                        LOGGER.atWarning().withCause(ex).log("Vote reward persistence failed for " + playerId);
                        return null;
                    });
        } catch (RejectedExecutionException e) {
            LOGGER.atWarning().withCause(e).log("Vote reward executor rejected task for " + playerId);
            return CompletableFuture.completedFuture(null);
        }
    }

    private void schedulePollStep(List<Map.Entry<UUID, String>> snapshot, AtomicInteger nextIndex,
                                  AtomicInteger workersRemaining) {
        ExecutorService executor = ioExecutor;
        if (executor == null) {
            finishPollWorker(workersRemaining);
            return;
        }
        try {
            executor.execute(() -> pollNextPlayer(snapshot, nextIndex, workersRemaining));
        } catch (RejectedExecutionException e) {
            LOGGER.atWarning().withCause(e).log("Vote poll executor rejected work");
            finishPollWorker(workersRemaining);
        }
    }

    private void pollNextPlayer(List<Map.Entry<UUID, String>> snapshot, AtomicInteger nextIndex,
                                AtomicInteger workersRemaining) {
        int index = nextIndex.getAndIncrement();
        if (index >= snapshot.size()) {
            finishPollWorker(workersRemaining);
            return;
        }
        Map.Entry<UUID, String> entry = snapshot.get(index);
        checkAndRewardAsync(entry.getKey(), entry.getValue(), true)
                .whenComplete((rewarded, throwable) -> {
                    try {
                        if (throwable != null) {
                            LOGGER.atWarning().withCause(throwable).log("Vote poll failed for " + entry.getValue());
                        } else if (rewarded != null && rewarded > 0) {
                            sendRewardMessage(entry.getKey(), rewarded);
                        }
                    } finally {
                        schedulePollStep(snapshot, nextIndex, workersRemaining);
                    }
                });
    }

    private void finishPollWorker(AtomicInteger workersRemaining) {
        if (workersRemaining.decrementAndGet() == 0) {
            pollInProgress.set(false);
        }
    }

    private boolean isPollBackoffActive() {
        return pollBackoffUntilMs > System.currentTimeMillis();
    }

    private void recordBackendSuccess() {
        consecutiveBackendFailures.set(0);
        pollBackoffUntilMs = 0L;
    }

    private void recordBackendFailure(String operation, String context, Throwable throwable) {
        if (throwable != null) {
            LOGGER.atWarning().withCause(throwable).log("Vote " + operation + " failed for " + context);
        } else {
            LOGGER.atWarning().log("Vote " + operation + " failed for " + context);
        }
        int failures = consecutiveBackendFailures.incrementAndGet();
        if (failures < FAILURE_BACKOFF_THRESHOLD) {
            return;
        }
        int exponent = Math.min(failures - FAILURE_BACKOFF_THRESHOLD, 4);
        long delayMs = Math.min(MAX_BACKOFF_MS, INITIAL_BACKOFF_MS * (1L << exponent));
        long backoffUntil = System.currentTimeMillis() + delayMs;
        if (backoffUntil > pollBackoffUntilMs) {
            pollBackoffUntilMs = backoffUntil;
            LOGGER.atWarning().log("Vote polling backing off for " + delayMs + " ms after repeated backend failures");
        }
    }

    private String extractVoteId(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject vote = element.getAsJsonObject();
        return vote.has("id") ? vote.get("id").getAsString() : null;
    }

    private synchronized void shutdownExecutors() {
        ExecutorService io = ioExecutor;
        ioExecutor = null;
        if (io != null) {
            io.shutdownNow();
        }
        ExecutorService rewards = rewardExecutor;
        rewardExecutor = null;
        if (rewards != null) {
            rewards.shutdownNow();
        }
    }

    private void sendRewardMessage(UUID playerId, int voteCount) {
        VoteConfig activeConfig = config;
        int rewardPerVote = activeConfig != null ? activeConfig.getRewardPerVote() : 0;
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
                    int totalFeathers = voteCount * rewardPerVote;
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

    private void broadcastVoteMessage(String username, String source) {
        Message msg = Message.join(
                Message.raw(username).color("#f97316"),
                Message.raw(" voted on ").color("#9ca3af"),
                Message.raw(source).color("#f97316"),
                Message.raw("!").color("#9ca3af")
        );
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            if (playerRef == null) {
                continue;
            }
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }
            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData() != null ? store.getExternalData().getWorld() : null;
            if (world == null) {
                continue;
            }
            CompletableFuture.runAsync(() -> {
                if (!ref.isValid()) {
                    return;
                }
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player != null) {
                    player.sendMessage(msg);
                }
            }, world).exceptionally(ex -> null);
        }
    }
}
