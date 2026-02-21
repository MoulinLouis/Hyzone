package io.hyvexa.purge.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.purge.data.PurgeWeaponUpgradeStore;
import io.hyvexa.purge.manager.PurgeInstanceManager;
import io.hyvexa.purge.manager.PurgeWaveConfigManager;
import io.hyvexa.purge.manager.PurgeWeaponConfigManager;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PurgeWeaponSelectPage extends InteractiveCustomUIPage<PurgeWeaponSelectPage.PurgeWeaponSelectData> {

    public enum Mode { ADMIN, PLAYER, LOADOUT }

    private static final String BUTTON_BACK = "Back";
    private static final String BUTTON_SELECT_PREFIX = "Select:";
    private static final String BUTTON_CLOSE_DETAIL = "CloseDetail";
    private static final String BUTTON_UPGRADE = "Upgrade";
    private static final String BUTTON_PURCHASE = "Purchase";
    private static final List<String> ICON_WEAPON_IDS = List.of(
            "AK47",
            "Barret50",
            "ColtRevolver",
            "DesertEagle",
            "DoubleBarrel",
            "Flamethrower",
            "Glock18",
            "M4A1s",
            "MP9",
            "Mac10",
            "Thompson"
    );

    private final Mode mode;
    private final UUID playerId;
    private final PurgeWeaponConfigManager weaponConfigManager;
    private final PurgeWaveConfigManager waveConfigManager;
    private final PurgeInstanceManager instanceManager;

    // Track selected weapon for inline detail panel
    private String selectedWeaponId;
    // Ordered list of weapon IDs matching their card index in #WeaponList
    private List<String> weaponIdOrder;
    // LOADOUT mode: maps weaponId -> UI root path (e.g. "#OwnedList[0]")
    private Map<String, String> weaponRootPaths;

    public PurgeWeaponSelectPage(@Nonnull PlayerRef playerRef,
                                 Mode mode,
                                 UUID playerId,
                                 PurgeWeaponConfigManager weaponConfigManager,
                                 PurgeWaveConfigManager waveConfigManager,
                                 PurgeInstanceManager instanceManager) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PurgeWeaponSelectData.CODEC);
        this.mode = mode;
        this.playerId = playerId;
        this.weaponConfigManager = weaponConfigManager;
        this.waveConfigManager = waveConfigManager;
        this.instanceManager = instanceManager;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder,
                      @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Purge_WeaponSelect.ui");

        if (mode == Mode.ADMIN) {
            uiCommandBuilder.set("#Title.Text", "Weapon Configuration");
            uiCommandBuilder.set("#Subtitle.Text", "Select a weapon to configure.");
        } else if (mode == Mode.LOADOUT) {
            uiCommandBuilder.set("#Title.Text", "Weapon Loadout");
            uiCommandBuilder.set("#Subtitle.Text", "View and unlock weapons.");
        }

        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BACK), false);

        buildWeaponList(uiCommandBuilder, uiEventBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull PurgeWeaponSelectData data) {
        super.handleDataEvent(ref, store, data);
        String button = data.getButton();
        if (button == null) {
            return;
        }

        if (BUTTON_BACK.equals(button)) {
            handleBack(ref, store);
            return;
        }
        if (button.startsWith(BUTTON_SELECT_PREFIX)) {
            String weaponId = button.substring(BUTTON_SELECT_PREFIX.length());
            handleSelect(weaponId, ref, store);
            return;
        }
        if (BUTTON_CLOSE_DETAIL.equals(button)) {
            handleCloseDetail();
            return;
        }
        if (BUTTON_UPGRADE.equals(button)) {
            handleUpgrade(ref, store);
            return;
        }
        if (BUTTON_PURCHASE.equals(button)) {
            handlePurchase(ref, store);
        }
    }

    private void handleBack(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (mode == Mode.ADMIN) {
            Player player = store.getComponent(ref, Player.getComponentType());
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (player != null && playerRef != null) {
                player.getPageManager().openCustomPage(ref, store,
                        new PurgeAdminIndexPage(playerRef, waveConfigManager, instanceManager, weaponConfigManager));
            }
        } else {
            close();
        }
    }

    private void handlePurchase(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (selectedWeaponId == null) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        long unlockCost = weaponConfigManager.getUnlockCost(selectedWeaponId);
        PurgeWeaponUpgradeStore.PurchaseResult result =
                PurgeWeaponUpgradeStore.getInstance().purchaseWeapon(playerId, selectedWeaponId, unlockCost);

        String displayName = weaponConfigManager.getDisplayName(selectedWeaponId);
        if (player != null) {
            switch (result) {
                case SUCCESS -> player.sendMessage(Message.raw("Unlocked " + displayName + "!"));
                case ALREADY_OWNED -> player.sendMessage(Message.raw(displayName + " is already owned."));
                case NOT_ENOUGH_SCRAP -> player.sendMessage(Message.raw("Not enough scrap to unlock."));
            }
        }
        // Clear selection so the weapon moves to its new section cleanly
        if (result == PurgeWeaponUpgradeStore.PurchaseResult.SUCCESS) {
            this.selectedWeaponId = null;
        }
        sendRefresh();
    }

    private void handleSelect(String weaponId, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (mode == Mode.ADMIN) {
            Player player = store.getComponent(ref, Player.getComponentType());
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (player == null || playerRef == null) {
                return;
            }
            player.getPageManager().openCustomPage(ref, store,
                    new PurgeWeaponAdminPage(playerRef, weaponId, weaponConfigManager, waveConfigManager, instanceManager));
        } else {
            // PLAYER/LOADOUT mode: toggle inline detail panel
            if (weaponId.equals(this.selectedWeaponId)) {
                this.selectedWeaponId = null;
            } else {
                this.selectedWeaponId = weaponId;
            }
            sendRefresh();
        }
    }

    private void handleCloseDetail() {
        if (mode != Mode.PLAYER && mode != Mode.LOADOUT) {
            return;
        }
        this.selectedWeaponId = null;
        sendRefresh();
    }

    private void handleUpgrade(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (selectedWeaponId == null) {
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        PurgeWeaponUpgradeStore.UpgradeResult result =
                PurgeWeaponUpgradeStore.getInstance().tryUpgrade(playerId, selectedWeaponId, weaponConfigManager);

        String displayName = weaponConfigManager.getDisplayName(selectedWeaponId);
        if (player != null) {
            switch (result) {
                case SUCCESS -> {
                    int newLevel = PurgeWeaponUpgradeStore.getInstance().getLevel(playerId, selectedWeaponId);
                    String stars = weaponConfigManager.getStarDisplay(newLevel);
                    int dmg = weaponConfigManager.getDamage(selectedWeaponId, newLevel);
                    player.sendMessage(Message.raw(displayName + " upgraded to " + stars + " star (" + dmg + " dmg)!"));
                }
                case MAX_LEVEL -> player.sendMessage(Message.raw(displayName + " is already at max level!"));
                case NOT_ENOUGH_SCRAP -> player.sendMessage(Message.raw("Not enough scrap to upgrade."));
                case NOT_OWNED -> player.sendMessage(Message.raw("You don't own " + displayName + "."));
            }
        }
        sendRefresh();
    }

    private void sendRefresh() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();

        // Re-bind back button
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BACK), false);

        // Rebuild weapon list and bindings to avoid stale child indexes.
        buildWeaponList(commandBuilder, eventBuilder);

        // Hidden by default; shown only when a weapon is selected.
        commandBuilder.set("#DetailPanel.Visible", false);

        // Highlight selected card
        if (mode == Mode.LOADOUT && weaponRootPaths != null) {
            for (Map.Entry<String, String> entry : weaponRootPaths.entrySet()) {
                commandBuilder.set(entry.getValue() + " #SelectedOverlay.Visible",
                        entry.getKey().equals(selectedWeaponId));
            }
        } else if (mode == Mode.PLAYER && weaponIdOrder != null) {
            for (int i = 0; i < weaponIdOrder.size(); i++) {
                String root = "#WeaponList[" + i + "]";
                commandBuilder.set(root + " #SelectedOverlay.Visible",
                        weaponIdOrder.get(i).equals(selectedWeaponId));
            }
        }

        // Populate detail panel
        if (selectedWeaponId != null && (mode == Mode.PLAYER || mode == Mode.LOADOUT)) {
            populateDetailPanel(commandBuilder, eventBuilder);
        }

        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    private void populateDetailPanel(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.set("#DetailPanel.Visible", true);
        commandBuilder.set("#DetailName.Text", weaponConfigManager.getDisplayName(selectedWeaponId));
        updateDetailIconVariant(commandBuilder, selectedWeaponId);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#DetailCloseButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE_DETAIL), false);

        int currentLevel = PurgeWeaponUpgradeStore.getInstance().getLevel(playerId, selectedWeaponId);
        int maxLevel = weaponConfigManager.getMaxLevel();

        if (mode == Mode.LOADOUT && currentLevel < 1) {
            // Unowned weapon in LOADOUT mode: show purchase option
            int baseDmg = weaponConfigManager.getDamage(selectedWeaponId, 1);
            long unlockCost = weaponConfigManager.getUnlockCost(selectedWeaponId);
            updateDetailStarDisplay(commandBuilder, 0);
            commandBuilder.set("#DetailDamage.Text", baseDmg + " dmg (base)");
            commandBuilder.set("#DetailDamage.Style.TextColor", "#8b9bb0");
            commandBuilder.set("#DetailUpgradeGroup.Visible", true);
            commandBuilder.set("#UpgradeCostValue.Text", String.valueOf(unlockCost));
            commandBuilder.set("#DetailStatus.Text", "");
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#UpgradeButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_PURCHASE), false);
        } else if (mode == Mode.LOADOUT) {
            // Owned weapon in LOADOUT mode: info only
            int currentDmg = weaponConfigManager.getDamage(selectedWeaponId, currentLevel);
            updateDetailStarDisplay(commandBuilder, currentLevel);
            commandBuilder.set("#DetailDamage.Text", currentDmg + " dmg");
            commandBuilder.set("#DetailDamage.Style.TextColor", "#9fb0ba");
            commandBuilder.set("#DetailUpgradeGroup.Visible", false);
            commandBuilder.set("#DetailStatus.Text", "Owned");
            commandBuilder.set("#DetailStatus.Style.TextColor", "#4ade80");
        } else {
            // PLAYER mode: upgrade flow
            int currentDmg = weaponConfigManager.getDamage(selectedWeaponId, currentLevel);
            updateDetailStarDisplay(commandBuilder, currentLevel);

            if (currentLevel >= maxLevel) {
                commandBuilder.set("#DetailDamage.Text", currentDmg + " dmg - MAX LEVEL");
                commandBuilder.set("#DetailDamage.Style.TextColor", "#fbbf24");
                commandBuilder.set("#DetailUpgradeGroup.Visible", false);
                commandBuilder.set("#DetailStatus.Text", "Max level reached!");
                commandBuilder.set("#DetailStatus.Style.TextColor", "#fbbf24");
            } else {
                int nextLevel = currentLevel + 1;
                int nextDmg = weaponConfigManager.getDamage(selectedWeaponId, nextLevel);
                long nextCost = weaponConfigManager.getCost(selectedWeaponId, nextLevel);
                commandBuilder.set("#DetailDamage.Text", currentDmg + " -> " + nextDmg + " dmg");
                commandBuilder.set("#DetailDamage.Style.TextColor", "#9fb0ba");
                commandBuilder.set("#DetailUpgradeGroup.Visible", true);
                commandBuilder.set("#UpgradeCostValue.Text", String.valueOf(nextCost));
                commandBuilder.set("#DetailStatus.Text", "");
                commandBuilder.set("#DetailStatus.Style.TextColor", "#9eb7d4");
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#UpgradeButton",
                        EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_UPGRADE), false);
            }
        }
    }

    private void buildWeaponList(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        if (mode == Mode.LOADOUT) {
            buildLoadoutSectionedList(commandBuilder, eventBuilder);
            return;
        }

        commandBuilder.clear("#WeaponList");
        weaponIdOrder = new ArrayList<>();

        List<String> weaponIds = new ArrayList<>(weaponConfigManager.getWeaponIds());
        weaponIds.sort(String::compareTo);

        int index = 0;
        for (String weaponId : weaponIds) {
            int playerLevel = 0;
            if (playerId != null && mode == Mode.PLAYER) {
                playerLevel = PurgeWeaponUpgradeStore.getInstance().getLevel(playerId, weaponId);
            }

            // PLAYER mode: only show owned weapons
            if (mode == Mode.PLAYER && playerLevel < 1) {
                continue;
            }

            String root = "#WeaponList[" + index + "]";
            commandBuilder.append("#WeaponList", "Pages/Purge_WeaponSelectEntry.ui");
            updateCardIconVariant(commandBuilder, root, weaponId);

            if (mode == Mode.ADMIN) {
                commandBuilder.set(root + " #WeaponName.Visible", true);
                String displayName = weaponConfigManager.getDisplayName(weaponId);
                String suffix = "";
                if (weaponConfigManager.isDefaultUnlocked(weaponId)) {
                    suffix += " [D]";
                }
                if (weaponConfigManager.isSessionWeapon(weaponId)) {
                    suffix += " [S]";
                }
                commandBuilder.set(root + " #WeaponName.Text", displayName + suffix);
            } else {
                // PLAYER mode
                commandBuilder.set(root + " #StarBar.Visible", true);
                updateCardStarDisplay(commandBuilder, root, playerLevel);
            }

            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    root + " #SelectButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SELECT_PREFIX + weaponId), false);

            weaponIdOrder.add(weaponId);
            index++;
        }
    }

    private void buildLoadoutSectionedList(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        // Hide flat list, show sectioned layout
        commandBuilder.set("#WeaponList.Visible", false);
        commandBuilder.set("#SectionedList.Visible", true);
        commandBuilder.clear("#OwnedList");
        commandBuilder.clear("#UnownedList");
        weaponRootPaths = new HashMap<>();
        weaponIdOrder = new ArrayList<>();

        List<String> weaponIds = new ArrayList<>(weaponConfigManager.getWeaponIds());
        weaponIds.sort(String::compareTo);

        // Partition into owned / unowned
        List<String> owned = new ArrayList<>();
        List<String> unowned = new ArrayList<>();
        for (String weaponId : weaponIds) {
            int level = playerId != null
                    ? PurgeWeaponUpgradeStore.getInstance().getLevel(playerId, weaponId)
                    : 0;
            if (level >= 1) {
                owned.add(weaponId);
            } else {
                unowned.add(weaponId);
            }
        }

        // Update section header counts
        commandBuilder.set("#OwnedHeader.Text", "Owned (" + owned.size() + ")");
        commandBuilder.set("#UnownedHeader.Text", "Unowned (" + unowned.size() + ")");

        // Build owned cards
        for (int i = 0; i < owned.size(); i++) {
            String weaponId = owned.get(i);
            String root = "#OwnedList[" + i + "]";
            commandBuilder.append("#OwnedList", "Pages/Purge_WeaponSelectEntry.ui");
            updateCardIconVariant(commandBuilder, root, weaponId);

            int playerLevel = PurgeWeaponUpgradeStore.getInstance().getLevel(playerId, weaponId);
            commandBuilder.set(root + " #StarBar.Visible", true);
            updateCardStarDisplay(commandBuilder, root, playerLevel);

            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    root + " #SelectButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SELECT_PREFIX + weaponId), false);

            weaponRootPaths.put(weaponId, root);
            weaponIdOrder.add(weaponId);
        }

        // Hide unowned section if empty
        commandBuilder.set("#UnownedHeader.Visible", !unowned.isEmpty());
        commandBuilder.set("#UnownedList.Visible", !unowned.isEmpty());

        // Hide owned header if empty (edge case: no weapons owned yet)
        commandBuilder.set("#OwnedHeader.Visible", !owned.isEmpty());

        // Build unowned cards
        for (int i = 0; i < unowned.size(); i++) {
            String weaponId = unowned.get(i);
            String root = "#UnownedList[" + i + "]";
            commandBuilder.append("#UnownedList", "Pages/Purge_WeaponSelectEntry.ui");
            updateCardIconVariant(commandBuilder, root, weaponId);

            commandBuilder.set(root + " #LockedLabel.Visible", true);
            commandBuilder.set(root + " #LockedOverlay.Visible", true);

            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    root + " #SelectButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SELECT_PREFIX + weaponId), false);

            weaponRootPaths.put(weaponId, root);
            weaponIdOrder.add(weaponId);
        }
    }

    private void updateCardIconVariant(UICommandBuilder commandBuilder, String root, String weaponId) {
        String normalized = normalizeIconWeaponId(weaponId);
        for (String iconWeaponId : ICON_WEAPON_IDS) {
            commandBuilder.set(root + " #Icon" + iconWeaponId + ".Visible", false);
        }
        commandBuilder.set(root + " #Icon" + normalized + ".Visible", true);
    }

    private void updateDetailIconVariant(UICommandBuilder commandBuilder, String weaponId) {
        String normalized = normalizeIconWeaponId(weaponId);
        for (String iconWeaponId : ICON_WEAPON_IDS) {
            commandBuilder.set("#DIcon" + iconWeaponId + ".Visible", false);
        }
        commandBuilder.set("#DIcon" + normalized + ".Visible", true);
    }

    private String normalizeIconWeaponId(String weaponId) {
        if (weaponId != null && ICON_WEAPON_IDS.contains(weaponId)) {
            return weaponId;
        }
        return "AK47";
    }

    private void updateCardStarDisplay(UICommandBuilder commandBuilder, String root, int level) {
        int fullStars = level / 2;
        boolean hasHalf = level % 2 == 1;
        for (int p = 0; p < 5; p++) {
            commandBuilder.set(root + " #S" + p + "F.Visible", p < fullStars);
            commandBuilder.set(root + " #S" + p + "H.Visible", p == fullStars && hasHalf);
            commandBuilder.set(root + " #S" + p + "E.Visible", p >= fullStars && !(p == fullStars && hasHalf));
        }
    }

    private void updateDetailStarDisplay(UICommandBuilder commandBuilder, int level) {
        int fullStars = level / 2;
        boolean hasHalf = level % 2 == 1;
        for (int p = 0; p < 5; p++) {
            commandBuilder.set("#DS" + p + "F.Visible", p < fullStars);
            commandBuilder.set("#DS" + p + "H.Visible", p == fullStars && hasHalf);
            commandBuilder.set("#DS" + p + "E.Visible", p >= fullStars && !(p == fullStars && hasHalf));
        }
    }

    public static class PurgeWeaponSelectData extends ButtonEventData {
        public static final BuilderCodec<PurgeWeaponSelectData> CODEC =
                BuilderCodec.<PurgeWeaponSelectData>builder(PurgeWeaponSelectData.class, PurgeWeaponSelectData::new)
                        .addField(new KeyedCodec<>(ButtonEventData.KEY_BUTTON, Codec.STRING),
                                (data, value) -> data.button = value, data -> data.button)
                        .build();

        private String button;

        @Override
        public String getButton() {
            return button;
        }
    }
}
