package io.hyvexa.manager;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.SystemMessageUtils;
import io.hyvexa.parkour.tracker.RunHud;
import io.hyvexa.parkour.data.GlobalMessageStore;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Handles HUD announcements and scheduled chat broadcast messages. */
public class AnnouncementManager {

    @FunctionalInterface
    public interface TickScheduler {
        ScheduledFuture<?> schedule(String name, Runnable task, long initialDelay, long period, TimeUnit unit);
    }

    private static final int ANNOUNCEMENT_MAX_LINES = 3;
    private static final long ANNOUNCEMENT_DURATION_SECONDS = 10L;
    private static final String CHAT_LINK_PLACEHOLDER = "{link}";
    private static final String CHAT_STORE_PLACEHOLDER = "{store}";
    private static final String CHAT_LINK_LABEL = "click here";
    private static final String CHAT_STORE_LABEL = "store.hyvexa.com";
    private static final String DISCORD_URL = "https://discord.gg/2PAygkyFnK";
    private static final String STORE_URL = "https://store.hyvexa.com";

    private final ConcurrentHashMap<UUID, ConcurrentLinkedDeque<Announcement>> announcements =
            new ConcurrentHashMap<>();
    private final Object chatAnnouncementLock = new Object();
    private List<Message> chatAnnouncements = List.of();
    private int chatAnnouncementIndex = 0;
    private ScheduledFuture<?> chatAnnouncementTask;

    private final GlobalMessageStore globalMessageStore;
    private final HudManager hudManager;
    private final TickScheduler scheduler;
    private final Consumer<ScheduledFuture<?>> canceller;

    public AnnouncementManager(GlobalMessageStore globalMessageStore, HudManager hudManager, TickScheduler scheduler,
                               Consumer<ScheduledFuture<?>> canceller) {
        this.globalMessageStore = globalMessageStore;
        this.hudManager = hudManager;
        this.scheduler = scheduler;
        this.canceller = canceller;
    }

    public void broadcastAnnouncement(String message, PlayerRef sender) {
        if (message == null) {
            return;
        }
        String trimmed = message.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        Message chatMessage = SystemMessageUtils.adminAnnouncement(trimmed);
        if (sender != null) {
            queueAnnouncement(sender, trimmed);
        }
        for (PlayerRef target : Universe.get().getPlayers()) {
            if (sender != null && sender.equals(target)) {
                continue;
            }
            queueAnnouncement(target, trimmed);
            target.sendMessage(chatMessage);
        }
    }

    public void updateAnnouncementHud(PlayerRef playerRef) {
        if (playerRef == null || hudManager == null || hudManager.isRunHudHidden(playerRef.getUuid())) {
            return;
        }
        var ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }
            if (hudManager.isRunHudHidden(playerRef.getUuid())) {
                return;
            }
            RunHud hud = hudManager.getActiveHud(playerRef);
            if (hud == null) {
                hud = hudManager.getOrCreateHud(playerRef, false);
                hudManager.attachHud(playerRef, player, hud, false);
            }
            hud.updateAnnouncements(getAnnouncementLines(playerRef.getUuid()));
        }, world);
    }

    public void refreshChatAnnouncements() {
        List<Message> rebuilt = buildChatAnnouncements();
        synchronized (chatAnnouncementLock) {
            chatAnnouncements = rebuilt;
            chatAnnouncementIndex = 0;
        }
        rescheduleChatAnnouncements();
    }

    public void shutdown() {
        if (canceller != null) {
            canceller.accept(chatAnnouncementTask);
        }
        chatAnnouncementTask = null;
    }

    public void clearPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        announcements.remove(playerId);
    }

    public void sweepStalePlayers(Set<UUID> onlinePlayers) {
        announcements.keySet().removeIf(id -> !onlinePlayers.contains(id));
    }

    private void queueAnnouncement(PlayerRef playerRef, String message) {
        if (playerRef == null) {
            return;
        }
        Announcement entry = new Announcement(message);
        announcements.compute(playerRef.getUuid(), (key, queue) -> {
            ConcurrentLinkedDeque<Announcement> target = queue != null ? queue : new ConcurrentLinkedDeque<>();
            target.addLast(entry);
            return target;
        });
        updateAnnouncementHud(playerRef);
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            announcements.computeIfPresent(playerRef.getUuid(), (key, queue) -> {
                queue.remove(entry);
                return queue.isEmpty() ? null : queue;
            });
            updateAnnouncementHud(playerRef);
        }, ANNOUNCEMENT_DURATION_SECONDS, TimeUnit.SECONDS);
    }

    private void rescheduleChatAnnouncements() {
        if (canceller != null) {
            canceller.accept(chatAnnouncementTask);
        }
        chatAnnouncementTask = null;
        long intervalMinutes = globalMessageStore != null
                ? globalMessageStore.getIntervalMinutes()
                : GlobalMessageStore.DEFAULT_INTERVAL_MINUTES;
        long intervalSeconds = Math.max(60L, intervalMinutes * 60L);
        if (!chatAnnouncements.isEmpty() && scheduler != null) {
            chatAnnouncementTask = scheduler.schedule("chat announcements", this::tickChatAnnouncements, 60,
                    intervalSeconds, TimeUnit.SECONDS);
        }
    }

    private void tickChatAnnouncements() {
        Message message;
        synchronized (chatAnnouncementLock) {
            if (chatAnnouncements.isEmpty()) {
                return;
            }
            if (chatAnnouncementIndex >= chatAnnouncements.size()) {
                chatAnnouncementIndex = 0;
            }
            message = chatAnnouncements.get(chatAnnouncementIndex);
            chatAnnouncementIndex = (chatAnnouncementIndex + 1) % chatAnnouncements.size();
        }
        var players = Universe.get().getPlayers();
        if (players.isEmpty()) {
            return;
        }
        for (PlayerRef playerRef : players) {
            playerRef.sendMessage(message);
        }
    }

    private List<Message> buildChatAnnouncements() {
        if (globalMessageStore == null) {
            return List.of();
        }
        List<String> messages = globalMessageStore.getMessages();
        if (messages.isEmpty()) {
            return List.of();
        }
        List<Message> built = new ArrayList<>();
        for (String message : messages) {
            Message formatted = buildChatAnnouncementMessage(message);
            if (formatted != null) {
                built.add(formatted);
            }
        }
        return List.copyOf(built);
    }

    private Message buildChatAnnouncementMessage(String template) {
        if (template == null) {
            return null;
        }
        String trimmed = template.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        Message discordLink = Message.raw(CHAT_LINK_LABEL).color("#8ab4f8").link(DISCORD_URL);
        Message storeLink = Message.raw(CHAT_STORE_LABEL).color("#8ab4f8").link(STORE_URL);
        boolean hasDiscordPlaceholder = trimmed.contains(CHAT_LINK_PLACEHOLDER);
        boolean hasStorePlaceholder = trimmed.contains(CHAT_STORE_PLACEHOLDER);
        if (!hasDiscordPlaceholder && !hasStorePlaceholder) {
            return Message.join(
                    Message.raw(trimmed),
                    Message.raw(" ("),
                    discordLink,
                    Message.raw(").")
            );
        }
        List<Message> parts = new ArrayList<>();
        int index = 0;
        while (index < trimmed.length()) {
            int discordIndex = trimmed.indexOf(CHAT_LINK_PLACEHOLDER, index);
            int storeIndex = trimmed.indexOf(CHAT_STORE_PLACEHOLDER, index);
            int next = -1;
            Message replacement = null;
            int placeholderLength = 0;
            if (discordIndex >= 0 && (storeIndex < 0 || discordIndex < storeIndex)) {
                next = discordIndex;
                replacement = discordLink;
                placeholderLength = CHAT_LINK_PLACEHOLDER.length();
            } else if (storeIndex >= 0) {
                next = storeIndex;
                replacement = storeLink;
                placeholderLength = CHAT_STORE_PLACEHOLDER.length();
            }
            if (next < 0) {
                String tail = trimmed.substring(index);
                if (!tail.isEmpty()) {
                    parts.add(Message.raw(tail));
                }
                break;
            }
            if (next > index) {
                parts.add(Message.raw(trimmed.substring(index, next)));
            }
            parts.add(replacement);
            index = next + placeholderLength;
        }
        if (parts.isEmpty()) {
            return hasStorePlaceholder ? storeLink : discordLink;
        }
        return Message.join(parts.toArray(new Message[0]));
    }

    private List<String> getAnnouncementLines(UUID playerId) {
        Deque<Announcement> queue = announcements.get(playerId);
        if (queue == null) {
            return List.of();
        }
        ArrayDeque<String> lines = new ArrayDeque<>(ANNOUNCEMENT_MAX_LINES);
        for (Announcement entry : queue) {
            if (lines.size() == ANNOUNCEMENT_MAX_LINES) {
                lines.removeFirst();
            }
            lines.addLast(entry.message);
        }
        if (lines.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(lines);
    }

    private static class Announcement {
        private final String message;

        private Announcement(String message) {
            this.message = message;
        }
    }
}
