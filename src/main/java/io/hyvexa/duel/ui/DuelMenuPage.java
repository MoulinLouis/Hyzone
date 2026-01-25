package io.hyvexa.duel.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.HyvexaPlugin;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.duel.DuelConstants;
import io.hyvexa.duel.DuelTracker;
import io.hyvexa.duel.DuelMatch;
import io.hyvexa.duel.data.DuelPreferenceStore;
import io.hyvexa.duel.data.DuelPreferenceStore.DuelCategory;
import io.hyvexa.parkour.tracker.RunTracker;
import io.hyvexa.parkour.ui.BaseParkourPage;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

public class DuelMenuPage extends BaseParkourPage {

    private static final String BUTTON_QUEUE = "Queue";
    private static final String BUTTON_OPPONENT = "Opponent";
    private static final String BUTTON_CATEGORY_PREFIX = "Category:";
    private static final String BUTTON_ACTIVE_MATCHES = "ActiveMatches";
    private static final String BUTTON_LEADERBOARD = "Leaderboard";

    public DuelMenuPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Duel_Menu.ui");
        applyQueueState(ref, store, uiCommandBuilder);
        applyOpponentState(ref, store, uiCommandBuilder);
        applyCategoryState(ref, store, uiCommandBuilder);
        applyAdminState(ref, store, uiCommandBuilder);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#QueueButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_QUEUE), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#OpponentButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_OPPONENT), false);
        bindCategory(uiEventBuilder, "#EasyToggleButton", DuelCategory.EASY);
        bindCategory(uiEventBuilder, "#MediumToggleButton", DuelCategory.MEDIUM);
        bindCategory(uiEventBuilder, "#HardToggleButton", DuelCategory.HARD);
        bindCategory(uiEventBuilder, "#InsaneToggleButton", DuelCategory.INSANE);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ActiveMatchesButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_ACTIVE_MATCHES), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#LeaderboardButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_LEADERBOARD), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ButtonEventData data) {
        super.handleDataEvent(ref, store, data);
        if (data.getButton() == null) {
            return;
        }
        if (BUTTON_QUEUE.equals(data.getButton())) {
            handleQueue(ref, store);
            this.close();
            return;
        }
        if (BUTTON_OPPONENT.equals(data.getButton())) {
            handleOpponentToggle(ref, store);
            return;
        }
        if (data.getButton().startsWith(BUTTON_CATEGORY_PREFIX)) {
            handleCategoryToggle(ref, store, data.getButton());
            return;
        }
        if (BUTTON_ACTIVE_MATCHES.equals(data.getButton())) {
            handleActiveMatches(ref, store);
            return;
        }
        if (BUTTON_LEADERBOARD.equals(data.getButton())) {
            handleLeaderboard(ref, store);
            return;
        }
    }

    private void handleQueue(Ref<EntityStore> ref, Store<EntityStore> store) {
        HyvexaPlugin plugin = HyvexaPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        DuelTracker duelTracker = plugin.getDuelTracker();
        RunTracker runTracker = plugin.getRunTracker();
        if (duelTracker == null) {
            return;
        }
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef == null || player == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        if (duelTracker.isQueued(playerId)) {
            boolean left = duelTracker.dequeue(playerId);
            if (left) {
                player.sendMessage(Message.raw(DuelConstants.MSG_QUEUE_LEFT));
            }
            refreshQueueState(ref, store);
            return;
        }
        if (duelTracker.isInMatch(playerId)) {
            player.sendMessage(Message.raw(DuelConstants.MSG_IN_MATCH));
            return;
        }
        if (runTracker != null && runTracker.getActiveMapId(playerId) != null) {
            player.sendMessage(Message.raw(DuelConstants.MSG_IN_PARKOUR));
            return;
        }
        if (!duelTracker.hasAvailableMaps(playerId)) {
            player.sendMessage(Message.raw(DuelConstants.MSG_NO_MAPS));
            return;
        }
        boolean joined = duelTracker.enqueue(playerId);
        if (!joined) {
            int pos = duelTracker.getQueuePosition(playerId);
            player.sendMessage(Message.raw(String.format(DuelConstants.MSG_QUEUE_ALREADY, pos)));
            return;
        }
        int pos = duelTracker.getQueuePosition(playerId);
        DuelPreferenceStore prefs = plugin.getDuelPreferenceStore();
        String categories = prefs != null ? prefs.formatEnabledLabel(playerId) : "Easy/Medium/Hard/Insane";
        player.sendMessage(Message.raw(String.format(DuelConstants.MSG_QUEUE_JOINED, categories, pos)));
        duelTracker.tryMatch();
        refreshQueueState(ref, store);
    }

    private void refreshQueueState(Ref<EntityStore> ref, Store<EntityStore> store) {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        applyQueueState(ref, store, commandBuilder);
        applyOpponentState(ref, store, commandBuilder);
        applyCategoryState(ref, store, commandBuilder);
        applyAdminState(ref, store, commandBuilder);
        UIEventBuilder eventBuilder = new UIEventBuilder();
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#QueueButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_QUEUE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#OpponentButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_OPPONENT), false);
        bindCategory(eventBuilder, "#EasyToggleButton", DuelCategory.EASY);
        bindCategory(eventBuilder, "#MediumToggleButton", DuelCategory.MEDIUM);
        bindCategory(eventBuilder, "#HardToggleButton", DuelCategory.HARD);
        bindCategory(eventBuilder, "#InsaneToggleButton", DuelCategory.INSANE);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ActiveMatchesButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_ACTIVE_MATCHES), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#LeaderboardButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_LEADERBOARD), false);
        sendUpdate(commandBuilder, eventBuilder, false);
    }

    private void bindCategory(UIEventBuilder eventBuilder, String selector, DuelCategory category) {
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, selector,
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CATEGORY_PREFIX + category.key()), false);
    }

    private void applyAdminState(Ref<EntityStore> ref, Store<EntityStore> store, UICommandBuilder commandBuilder) {
        Player player = store.getComponent(ref, Player.getComponentType());
        boolean isOp = player != null && PermissionUtils.isOp(player);
        commandBuilder.set("#ActiveMatchesButton.Visible", isOp);
    }

    private void applyQueueState(Ref<EntityStore> ref, Store<EntityStore> store, UICommandBuilder commandBuilder) {
        HyvexaPlugin plugin = HyvexaPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        DuelTracker duelTracker = plugin.getDuelTracker();
        if (duelTracker == null) {
            return;
        }
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        if (duelTracker.isQueued(playerId)) {
            int pos = duelTracker.getQueuePosition(playerId);
            commandBuilder.set("#QueueButton.Text", "Leave Queue");
            commandBuilder.set("#QueueStatus.Text", "Queued. Position: #" + pos);
            return;
        }
        commandBuilder.set("#QueueButton.Text", "Queue");
        commandBuilder.set("#QueueStatus.Text", "Not queued.");
    }

    private void applyOpponentState(Ref<EntityStore> ref, Store<EntityStore> store, UICommandBuilder commandBuilder) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        boolean hidden = io.hyvexa.parkour.util.PlayerSettingsStore.isDuelOpponentHidden(playerRef.getUuid());
        commandBuilder.set("#OpponentButton.Text", hidden ? "Show Opponent" : "Hide Opponent");
        commandBuilder.set("#OpponentStatus.Text", hidden ? "Opponent hidden." : "Opponent visible.");
    }

    private void applyCategoryState(Ref<EntityStore> ref, Store<EntityStore> store, UICommandBuilder commandBuilder) {
        HyvexaPlugin plugin = HyvexaPlugin.getInstance();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (plugin == null || plugin.getDuelPreferenceStore() == null || playerRef == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        DuelPreferenceStore prefs = plugin.getDuelPreferenceStore();
        setCategoryToggle(commandBuilder, playerId, prefs, DuelCategory.EASY, "#EasyToggleButton");
        setCategoryToggle(commandBuilder, playerId, prefs, DuelCategory.MEDIUM, "#MediumToggleButton");
        setCategoryToggle(commandBuilder, playerId, prefs, DuelCategory.HARD, "#HardToggleButton");
        setCategoryToggle(commandBuilder, playerId, prefs, DuelCategory.INSANE, "#InsaneToggleButton");
    }

    private void setCategoryToggle(UICommandBuilder commandBuilder, UUID playerId, DuelPreferenceStore prefs,
                                   DuelCategory category, String selector) {
        boolean enabled = prefs.isEnabled(playerId, category);
        commandBuilder.set(selector + ".Text", enabled ? "ON" : "OFF");
    }

    private void handleOpponentToggle(Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        boolean hidden = io.hyvexa.parkour.util.PlayerSettingsStore.toggleDuelOpponentHidden(playerRef.getUuid());
        HyvexaPlugin plugin = HyvexaPlugin.getInstance();
        if (plugin != null && plugin.getDuelTracker() != null) {
            plugin.getDuelTracker().refreshOpponentVisibility(playerRef.getUuid());
        }
        refreshQueueState(ref, store);
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.sendMessage(Message.raw(hidden ? "Opponent hidden." : "Opponent visible."));
        }
    }

    private void handleCategoryToggle(Ref<EntityStore> ref, Store<EntityStore> store, String button) {
        HyvexaPlugin plugin = HyvexaPlugin.getInstance();
        if (plugin == null || plugin.getDuelPreferenceStore() == null || plugin.getDuelTracker() == null) {
            return;
        }
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef == null || player == null) {
            return;
        }
        DuelCategory category = parseCategory(button);
        if (category == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        plugin.getDuelPreferenceStore().toggle(playerId, category);
        if (plugin.getDuelTracker().isQueued(playerId) && !plugin.getDuelTracker().hasAvailableMaps(playerId)) {
            plugin.getDuelTracker().dequeue(playerId);
            player.sendMessage(Message.raw("No duel maps match your selected categories. You left the queue."));
        } else {
            plugin.getDuelTracker().tryMatch();
        }
        refreshQueueState(ref, store);
        boolean enabled = plugin.getDuelPreferenceStore().isEnabled(playerId, category);
        player.sendMessage(Message.raw(categoryLabel(category) + ": " + (enabled ? "ON" : "OFF")));
    }

    private DuelCategory parseCategory(String button) {
        if (button == null || !button.startsWith(BUTTON_CATEGORY_PREFIX)) {
            return null;
        }
        String key = button.substring(BUTTON_CATEGORY_PREFIX.length()).toLowerCase();
        return switch (key) {
            case "easy" -> DuelCategory.EASY;
            case "medium" -> DuelCategory.MEDIUM;
            case "hard" -> DuelCategory.HARD;
            case "insane" -> DuelCategory.INSANE;
            default -> null;
        };
    }

    private String categoryLabel(DuelCategory category) {
        return switch (category) {
            case EASY -> "Easy";
            case MEDIUM -> "Medium";
            case HARD -> "Hard";
            case INSANE -> "Insane";
        };
    }

    private void handleActiveMatches(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        if (!PermissionUtils.isOp(player)) {
            player.sendMessage(Message.raw("You must be OP to view active matches."));
            return;
        }
        HyvexaPlugin plugin = HyvexaPlugin.getInstance();
        if (plugin == null || plugin.getDuelTracker() == null) {
            return;
        }
        List<DuelMatch> matches = plugin.getDuelTracker().getActiveMatches();
        if (matches.isEmpty()) {
            player.sendMessage(Message.raw("No active matches."));
            return;
        }
        player.sendMessage(Message.raw("Active matches (" + matches.size() + "):"));
        for (DuelMatch match : matches) {
            String p1 = resolveName(match.getPlayer1());
            String p2 = resolveName(match.getPlayer2());
            player.sendMessage(Message.raw("- " + match.getMatchId() + ": " + p1 + " vs " + p2
                    + " on " + match.getMapId() + " (" + match.getState() + ")"));
        }
    }

    private void handleLeaderboard(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store, new DuelLeaderboardPage(playerRef));
    }

    private String resolveName(UUID playerId) {
        var ref = com.hypixel.hytale.server.core.universe.Universe.get().getPlayer(playerId);
        return ref != null ? ref.getUsername() : playerId.toString();
    }
}
