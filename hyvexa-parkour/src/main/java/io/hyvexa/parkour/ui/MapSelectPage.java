package io.hyvexa.parkour.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.parkour.util.InventoryUtils;
import io.hyvexa.common.util.SystemMessageUtils;
import io.hyvexa.parkour.data.Map;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.Medal;
import io.hyvexa.parkour.data.MedalStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.tracker.RunTracker;
import io.hyvexa.parkour.util.ParkourUtils;

import java.util.Set;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MapSelectPage extends BaseParkourPage {

    private final MapStore mapStore;
    private final ProgressStore progressStore;
    private final RunTracker runTracker;
    private final String category;
    private final boolean hideCompleted;
    private static final String BUTTON_BACK = "Back";
    private static final String BUTTON_TOGGLE_HIDE_COMPLETED = "ToggleHideCompleted";
    private static final String BUTTON_SELECT_PREFIX = "Select:";
    private static final String HIDE_COMPLETED_BUTTON_SELECTOR = "#HideCompletedToggle";
    private static final String HIDE_COMPLETED_CHECK_SELECTOR = "#HideCompletedCheckboxMark";

    public MapSelectPage(@Nonnull PlayerRef playerRef, MapStore mapStore,
                                   ProgressStore progressStore, RunTracker runTracker, String category) {
        this(playerRef, mapStore, progressStore, runTracker, category, false);
    }

    public MapSelectPage(@Nonnull PlayerRef playerRef, MapStore mapStore,
                                   ProgressStore progressStore, RunTracker runTracker, String category,
                                   boolean hideCompleted) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.mapStore = mapStore;
        this.progressStore = progressStore;
        this.runTracker = runTracker;
        this.category = category;
        this.hideCompleted = hideCompleted;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Parkour_MapSelect.ui");
        uiCommandBuilder.set(HIDE_COMPLETED_CHECK_SELECTOR + ".Visible", hideCompleted);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BACK), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, HIDE_COMPLETED_BUTTON_SELECTOR,
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TOGGLE_HIDE_COMPLETED), false);
        buildMapList(ref, store, uiCommandBuilder, uiEventBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ButtonEventData data) {
        super.handleDataEvent(ref, store, data);
        if (data.getButton() == null) {
            return;
        }
        if (BUTTON_BACK.equals(data.getButton())) {
            Player player = store.getComponent(ref, Player.getComponentType());
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (player != null && playerRef != null) {
                player.getPageManager().openCustomPage(ref, store,
                        new CategorySelectPage(playerRef, mapStore, progressStore, runTracker));
            }
            return;
        }
        if (BUTTON_TOGGLE_HIDE_COMPLETED.equals(data.getButton())) {
            Player player = store.getComponent(ref, Player.getComponentType());
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (player != null && playerRef != null) {
                player.getPageManager().openCustomPage(ref, store,
                        new MapSelectPage(playerRef, mapStore, progressStore, runTracker, category, !hideCompleted));
            }
            return;
        }
        if (!data.getButton().startsWith(BUTTON_SELECT_PREFIX)) {
            return;
        }
        String mapId = data.getButton().substring(BUTTON_SELECT_PREFIX.length());
        Map map = mapStore.getMap(mapId);
        if (map == null) {
            sendMessage(store, ref, "Map not found.");
            return;
        }
        if (map.getStart() == null) {
            sendMessage(store, ref, "Map '" + mapId + "' has no start set.");
            return;
        }
        if (!map.isActive()) {
            Player p = store.getComponent(ref, Player.getComponentType());
            if (p != null) {
                p.sendMessage(SystemMessageUtils.parkourWarn("This map is currently unavailable."));
            }
            return;
        }
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef == null || player == null) {
            return;
        }
        runTracker.setActiveMap(playerRef.getUuid(), mapId, map.getStart());
        World world = store.getExternalData().getWorld();
        Vector3d position = new Vector3d(map.getStart().getX(), map.getStart().getY(), map.getStart().getZ());
        Vector3f rotation = new Vector3f(map.getStart().getRotX(), map.getStart().getRotY(),
                map.getStart().getRotZ());
        store.addComponent(ref, Teleport.getComponentType(), new Teleport(world, position, rotation));
        String mapName = map.getName() != null && !map.getName().isBlank() ? map.getName() : map.getId();
        player.sendMessage(SystemMessageUtils.withParkourPrefix(
                Message.raw("Run started: ").color(SystemMessageUtils.SECONDARY),
                Message.raw(mapName != null ? mapName : "Map").color(SystemMessageUtils.PRIMARY_TEXT),
                Message.raw(".").color(SystemMessageUtils.SECONDARY)
        ));
        InventoryUtils.giveRunItems(player, map, false);
        this.close();
    }

    private void buildMapList(Ref<EntityStore> ref, Store<EntityStore> store, UICommandBuilder commandBuilder,
                              UIEventBuilder eventBuilder) {
        commandBuilder.clear("#MapCards");
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        List<Map> maps = new ArrayList<>(mapStore.listMaps());
        maps.sort(Comparator.comparingInt((Map map) -> {
                    int difficulty = map.getDifficulty();
                    return difficulty <= 0 ? Integer.MAX_VALUE : difficulty;
                })
                .thenComparing(map -> map.getName() != null ? map.getName() : map.getId(),
                        String.CASE_INSENSITIVE_ORDER));
        String accentColor = UIColorUtils.getCategoryAccentColor(category);
        int index = 0;
        for (Map map : maps) {
            if (!FormatUtils.normalizeCategory(map.getCategory()).equalsIgnoreCase(category)) {
                continue;
            }
            boolean completed = progressStore.isMapCompleted(playerRef.getUuid(), map.getId());
            if (hideCompleted && completed) {
                continue;
            }
            commandBuilder.append("#MapCards", "Pages/Parkour_MapSelectEntry.ui");
            commandBuilder.set("#MapCards[" + index + "] #AccentBar.Background", accentColor);
            Long bestTime = progressStore.getBestTimeMs(playerRef.getUuid(), map.getId());
            String status = completed ? "Completed" : "Not completed";
            if (bestTime != null) {
                status += " | Best: " + FormatUtils.formatDuration(bestTime);
            }
            commandBuilder.set("#MapCards[" + index + "] #MapStatus.Text", status);
            String mapName = ParkourUtils.formatMapName(map);
            if (!completed) {
                long rewardXp = Math.max(0L, map.getFirstCompletionXp());
                mapName += " | +" + rewardXp + " XP";
            }
            commandBuilder.set("#MapCards[" + index + "] #MapName.Text", mapName);
            // Medal display
            boolean hasMedalTimes = map.getBronzeTimeMs() != null || map.getSilverTimeMs() != null
                    || map.getGoldTimeMs() != null;
            if (hasMedalTimes) {
                String prefix = "#MapCards[" + index + "] ";
                commandBuilder.set(prefix + "#MedalRow.Visible", true);
                Set<Medal> earned = MedalStore.getInstance().getEarnedMedals(playerRef.getUuid(), map.getId());
                buildMedalLabel(commandBuilder, prefix, "Bronze", map.getBronzeTimeMs(),
                        earned.contains(Medal.BRONZE));
                buildMedalLabel(commandBuilder, prefix, "Silver", map.getSilverTimeMs(),
                        earned.contains(Medal.SILVER));
                buildMedalLabel(commandBuilder, prefix, "Gold", map.getGoldTimeMs(),
                        earned.contains(Medal.GOLD));
            }
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    "#MapCards[" + index + "] #SelectButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SELECT_PREFIX + map.getId()), false);
            index++;
        }
    }

    private static final String MEDAL_UNEARNED_COLOR = "#5a6a78";

    private void buildMedalLabel(UICommandBuilder commandBuilder, String prefix, String tier,
                                  Long thresholdMs, boolean earned) {
        if (thresholdMs == null || thresholdMs <= 0L) {
            commandBuilder.set(prefix + "#" + tier + "MedalText.Text", "");
            commandBuilder.set(prefix + "#" + tier + "MedalIcon.Visible", false);
            return;
        }
        commandBuilder.set(prefix + "#" + tier + "MedalText.Text",
                "< " + FormatUtils.formatDuration(thresholdMs));
        if (earned) {
            commandBuilder.set(prefix + "#" + tier + "MedalText.Style.RenderBold", true);
        } else {
            commandBuilder.set(prefix + "#" + tier + "MedalText.Style.TextColor", MEDAL_UNEARNED_COLOR);
        }
    }

    private void sendMessage(Store<EntityStore> store, Ref<EntityStore> ref, String text) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.sendMessage(Message.raw(text));
        }
    }

}
