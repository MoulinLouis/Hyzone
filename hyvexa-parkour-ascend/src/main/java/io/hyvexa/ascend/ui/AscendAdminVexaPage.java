package io.hyvexa.ascend.ui;

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
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.ascension.AscensionManager;
import io.hyvexa.ascend.ascension.ChallengeManager;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.data.AscendSettingsStore;
import io.hyvexa.common.math.BigNumber;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.common.util.SystemMessageUtils;

import javax.annotation.Nonnull;
import java.util.UUID;

public class AscendAdminVexaPage extends InteractiveCustomUIPage<AscendAdminVexaPage.VexaData> {

    private String amountInput = "";
    private String skillPointsInput = "";
    private String voidYThresholdInput = "";
    private int resetAllClickCount = 0;

    public AscendAdminVexaPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, VexaData.CODEC);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Ascend_AdminVexa.ui");
        bindEvents(eventBuilder);
        populateFields(commandBuilder, store, ref);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull VexaData data) {
        if (data.amount != null) {
            amountInput = data.amount;
        }
        if (data.skillPointsAmount != null) {
            skillPointsInput = data.skillPointsAmount;
        }
        if (data.voidYThreshold != null) {
            voidYThresholdInput = data.voidYThreshold;
        }
        if (data.button == null) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        switch (data.button) {
            case VexaData.BUTTON_CLOSE -> this.close();
            case VexaData.BUTTON_BACK -> player.getPageManager().openCustomPage(ref, store, new AscendAdminPanelPage(playerRef));
            case VexaData.BUTTON_ADD -> applyVexa(player, playerRef, store, true);
            case VexaData.BUTTON_REMOVE -> applyVexa(player, playerRef, store, false);
            case VexaData.BUTTON_RESET_PROGRESS -> resetProgress(player, playerRef, store);
            case VexaData.BUTTON_RESET_ALL -> resetAllPlayers(player);
            case VexaData.BUTTON_SET_SPAWN_LOCATION -> setSpawnLocation(ref, store, player);
            case VexaData.BUTTON_SET_NPC_LOCATION -> setNpcLocation(ref, store, player);
            case VexaData.BUTTON_ADD_SKILL_POINTS -> applySkillPoints(player, playerRef, true);
            case VexaData.BUTTON_REMOVE_SKILL_POINTS -> applySkillPoints(player, playerRef, false);
            case VexaData.BUTTON_SAVE_VOID_Y -> saveVoidYThreshold(player);
            case VexaData.BUTTON_CLEAR_VOID_Y -> clearVoidYThreshold(player);
            case VexaData.BUTTON_SIMULATE_ASCENSION -> simulateAscension(player, playerRef);
            case VexaData.BUTTON_ENDGAME -> endgame(player, playerRef);
            default -> {
            }
        }
    }

    private void bindEvents(UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#VexaAmountField",
            EventData.of(VexaData.KEY_AMOUNT, "#VexaAmountField.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#AddButton",
            EventData.of(VexaData.KEY_BUTTON, VexaData.BUTTON_ADD), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RemoveButton",
            EventData.of(VexaData.KEY_BUTTON, VexaData.BUTTON_REMOVE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ResetProgressButton",
            EventData.of(VexaData.KEY_BUTTON, VexaData.BUTTON_RESET_PROGRESS), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ResetAllPlayersButton",
            EventData.of(VexaData.KEY_BUTTON, VexaData.BUTTON_RESET_ALL), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetSpawnLocationButton",
            EventData.of(VexaData.KEY_BUTTON, VexaData.BUTTON_SET_SPAWN_LOCATION), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetNpcLocationButton",
            EventData.of(VexaData.KEY_BUTTON, VexaData.BUTTON_SET_NPC_LOCATION), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
            EventData.of(VexaData.KEY_BUTTON, VexaData.BUTTON_BACK), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of(VexaData.KEY_BUTTON, VexaData.BUTTON_CLOSE), false);
        // Skill points bindings
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#SkillPointsAmountField",
            EventData.of(VexaData.KEY_SKILL_POINTS_AMOUNT, "#SkillPointsAmountField.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#AddSkillPointsButton",
            EventData.of(VexaData.KEY_BUTTON, VexaData.BUTTON_ADD_SKILL_POINTS), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RemoveSkillPointsButton",
            EventData.of(VexaData.KEY_BUTTON, VexaData.BUTTON_REMOVE_SKILL_POINTS), false);
        // Void Y threshold bindings
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#VoidYThresholdField",
            EventData.of(VexaData.KEY_VOID_Y_THRESHOLD, "#VoidYThresholdField.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SaveVoidYButton",
            EventData.of(VexaData.KEY_BUTTON, VexaData.BUTTON_SAVE_VOID_Y), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ClearVoidYButton",
            EventData.of(VexaData.KEY_BUTTON, VexaData.BUTTON_CLEAR_VOID_Y), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SimulateAscensionButton",
            EventData.of(VexaData.KEY_BUTTON, VexaData.BUTTON_SIMULATE_ASCENSION), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#EndgameButton",
            EventData.of(VexaData.KEY_BUTTON, VexaData.BUTTON_ENDGAME), false);
    }

    private void resetProgress(Player player, PlayerRef playerRef, Store<EntityStore> store) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        AscendPlayerStore playerStore = plugin != null ? plugin.getPlayerStore() : null;
        AscendMapStore mapStore = plugin != null ? plugin.getMapStore() : null;
        if (playerStore == null) {
            player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading."));
            return;
        }
        playerStore.resetPlayerProgress(playerRef.getUuid());

        // Unlock the first map (lowest displayOrder) like a new player would have
        if (mapStore != null) {
            AscendMap firstMap = null;
            for (AscendMap map : mapStore.listMaps()) {
                if (firstMap == null || map.getDisplayOrder() < firstMap.getDisplayOrder()) {
                    firstMap = map;
                }
            }
            if (firstMap != null) {
                playerStore.setMapUnlocked(playerRef.getUuid(), firstMap.getId(), true);
            }
        }

        player.sendMessage(Message.raw("[Ascend] Your Ascend progress has been reset (first map unlocked).")
            .color(SystemMessageUtils.SECONDARY));
        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#CurrentVexaValue.Text", "0");
        commandBuilder.set("#CurrentSkillPointsValue.Text", "0");
        sendUpdate(commandBuilder, null, false);
    }

    private void resetAllPlayers(Player player) {
        resetAllClickCount++;
        int remaining = 3 - resetAllClickCount;

        if (remaining > 0) {
            player.sendMessage(Message.raw("[Ascend] Click " + remaining + " more time" + (remaining > 1 ? "s" : "") + " to confirm reset ALL players.")
                .color(SystemMessageUtils.WARN));
            return;
        }

        resetAllClickCount = 0;

        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        AscendPlayerStore playerStore = plugin != null ? plugin.getPlayerStore() : null;
        if (playerStore == null) {
            player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading."));
            return;
        }
        playerStore.resetAllPlayersProgress();
        player.sendMessage(Message.raw("[Ascend] ALL player progress has been wiped. Reconnecting players will start fresh.")
            .color(SystemMessageUtils.SECONDARY));

        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#CurrentVexaValue.Text", "0");
        commandBuilder.set("#CurrentSkillPointsValue.Text", "0");
        sendUpdate(commandBuilder, null, false);
    }

    private void simulateAscension(Player player, PlayerRef playerRef) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        AscendPlayerStore playerStore = plugin != null ? plugin.getPlayerStore() : null;
        AscensionManager ascensionManager = plugin != null ? plugin.getAscensionManager() : null;
        if (playerStore == null || ascensionManager == null) {
            player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading."));
            return;
        }

        UUID playerId = playerRef.getUuid();

        // Bypass vexa threshold for admin simulation
        BigNumber vexa = playerStore.getVexa(playerId);
        if (vexa.lt(AscendConstants.ASCENSION_VEXA_THRESHOLD)) {
            AscendPlayerProgress progress = playerStore.getOrCreatePlayer(playerId);
            progress.setVexa(AscendConstants.ASCENSION_VEXA_THRESHOLD);
        }

        // Despawn all robots before resetting data to prevent completions with pre-reset multipliers
        if (plugin.getRobotManager() != null) {
            plugin.getRobotManager().despawnRobotsForPlayer(playerId);
        }

        // Complete active challenge if in one (same routing as normal ascension)
        ChallengeManager challengeManager = plugin.getChallengeManager();
        if (challengeManager != null && challengeManager.isInChallenge(playerId)) {
            AscendConstants.ChallengeType type = challengeManager.getActiveChallenge(playerId);
            long elapsedMs = challengeManager.completeChallenge(playerId);
            if (elapsedMs >= 0) {
                String timeStr = FormatUtils.formatDurationLong(elapsedMs);
                player.sendMessage(Message.raw(
                    "[Challenge] " + (type != null ? type.getDisplayName() : "Challenge") + " completed in " + timeStr + "!")
                    .color(SystemMessageUtils.SUCCESS));
                player.sendMessage(Message.raw(
                    "[Challenge] Your progress has been restored. Permanent reward unlocked!")
                    .color(SystemMessageUtils.SUCCESS));
            }
            UICommandBuilder commandBuilder = new UICommandBuilder();
            commandBuilder.set("#CurrentVexaValue.Text", FormatUtils.formatBigNumber(playerStore.getVexa(playerId)));
            commandBuilder.set("#CurrentSkillPointsValue.Text", String.valueOf(playerStore.getSkillTreePoints(playerId)));
            sendUpdate(commandBuilder, null, false);
            return;
        }

        int newCount = ascensionManager.performAscension(playerId);
        if (newCount < 0) {
            player.sendMessage(Message.raw("[Ascend] Ascension simulation failed."));
            return;
        }

        player.sendMessage(Message.raw("[Ascend] Ascension simulated! (x" + newCount + ")")
            .color(SystemMessageUtils.SUCCESS));

        if (plugin.getAchievementManager() != null) {
            plugin.getAchievementManager().checkAndUnlockAchievements(playerId, player);
        }

        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#CurrentVexaValue.Text", "0");
        int skillPoints = playerStore.getSkillTreePoints(playerId);
        commandBuilder.set("#CurrentSkillPointsValue.Text", String.valueOf(skillPoints));
        sendUpdate(commandBuilder, null, false);
    }

    private void endgame(Player player, PlayerRef playerRef) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        AscendPlayerStore playerStore = plugin != null ? plugin.getPlayerStore() : null;
        if (playerStore == null) {
            player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading."));
            return;
        }

        UUID playerId = playerRef.getUuid();
        AscendPlayerProgress progress = playerStore.getOrCreatePlayer(playerId);

        // Unlock all skill tree nodes and grant enough points to cover costs
        int totalCost = 0;
        for (AscendConstants.SkillTreeNode node : AscendConstants.SkillTreeNode.values()) {
            totalCost += node.getCost();
            progress.unlockSkillNode(node);
        }
        int needed = totalCost - progress.getSkillTreePoints();
        if (needed > 0) {
            progress.addSkillTreePoints(needed);
        }

        // Complete all challenges
        for (AscendConstants.ChallengeType type : AscendConstants.ChallengeType.values()) {
            progress.addChallengeReward(type);
        }

        playerStore.markDirty(playerId);

        player.sendMessage(Message.raw("[Ascend] Endgame activated: all skill nodes unlocked, all challenges completed.")
            .color(SystemMessageUtils.SUCCESS));

        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#CurrentSkillPointsValue.Text", String.valueOf(progress.getSkillTreePoints()));
        sendUpdate(commandBuilder, null, false);
    }

    private void populateFields(UICommandBuilder commandBuilder, Store<EntityStore> store, Ref<EntityStore> ref) {
        commandBuilder.set("#VexaAmountField.Value", amountInput != null ? amountInput : "");
        commandBuilder.set("#SkillPointsAmountField.Value", skillPointsInput != null ? skillPointsInput : "");
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef != null) {
            AscendPlayerStore playerStore = ParkourAscendPlugin.getInstance().getPlayerStore();
            BigNumber vexa = playerStore != null ? playerStore.getVexa(playerRef.getUuid()) : BigNumber.ZERO;
            int skillPoints = playerStore != null ? playerStore.getSkillTreePoints(playerRef.getUuid()) : 0;
            commandBuilder.set("#CurrentVexaValue.Text", FormatUtils.formatBigNumber(vexa));
            commandBuilder.set("#CurrentSkillPointsValue.Text", String.valueOf(skillPoints));
        }

        // Void Y threshold
        AscendSettingsStore settingsStore = ParkourAscendPlugin.getInstance().getSettingsStore();
        if (settingsStore != null) {
            Double voidY = settingsStore.getVoidYThreshold();
            String voidLabel = voidY != null ? String.format("%.2f", voidY) : "Not set";
            commandBuilder.set("#VoidYThresholdValue.Text", voidLabel);
            commandBuilder.set("#VoidYThresholdField.Value", voidYThresholdInput);
        }

        // Location statuses
        if (settingsStore != null && settingsStore.hasSpawnPosition()) {
            Vector3d pos = settingsStore.getSpawnPosition();
            String status = String.format("%.1f, %.1f, %.1f", pos.getX(), pos.getY(), pos.getZ());
            commandBuilder.set("#SpawnLocationStatus.Text", status);
        } else {
            commandBuilder.set("#SpawnLocationStatus.Text", "Not set");
        }
        if (settingsStore != null && settingsStore.hasNpcPosition()) {
            Vector3d pos = settingsStore.getNpcPosition();
            String status = String.format("%.1f, %.1f, %.1f", pos.getX(), pos.getY(), pos.getZ());
            commandBuilder.set("#NpcLocationStatus.Text", status);
        } else {
            commandBuilder.set("#NpcLocationStatus.Text", "Not set");
        }
    }

    private void applyVexa(Player player, PlayerRef playerRef, Store<EntityStore> store, boolean add) {
        BigNumber amount = parseAmount(player);
        if (amount == null) {
            return;
        }
        AscendPlayerStore playerStore = ParkourAscendPlugin.getInstance().getPlayerStore();
        if (playerStore == null) {
            player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading."));
            return;
        }
        String formatted = FormatUtils.formatBigNumber(amount);
        if (add) {
            playerStore.addVexa(playerRef.getUuid(), amount);
            playerStore.addSummitAccumulatedVexa(playerRef.getUuid(), amount);
            playerStore.addElevationAccumulatedVexa(playerRef.getUuid(), amount);
            player.sendMessage(Message.raw("[Ascend] Added " + formatted + " vexa (counts toward Summit & Elevation).")
                .color(SystemMessageUtils.SUCCESS));
        } else {
            playerStore.addVexa(playerRef.getUuid(), amount.negate());
            player.sendMessage(Message.raw("[Ascend] Removed " + formatted + " vexa.")
                .color(SystemMessageUtils.SECONDARY));
        }
        UICommandBuilder commandBuilder = new UICommandBuilder();
        BigNumber vexa = playerStore.getVexa(playerRef.getUuid());
        commandBuilder.set("#CurrentVexaValue.Text", FormatUtils.formatBigNumber(vexa));
        sendUpdate(commandBuilder, null, false);
    }

    private void applySkillPoints(Player player, PlayerRef playerRef, boolean add) {
        int amount = parseSkillPointsAmount(player);
        if (amount <= 0) {
            return;
        }
        AscendPlayerStore playerStore = ParkourAscendPlugin.getInstance().getPlayerStore();
        if (playerStore == null) {
            player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading."));
            return;
        }
        if (add) {
            playerStore.addSkillTreePoints(playerRef.getUuid(), amount);
            player.sendMessage(Message.raw("[Ascend] Added " + amount + " AP.")
                .color(SystemMessageUtils.SUCCESS));
        } else {
            playerStore.addSkillTreePoints(playerRef.getUuid(), -amount);
            player.sendMessage(Message.raw("[Ascend] Removed " + amount + " AP.")
                .color(SystemMessageUtils.SECONDARY));
        }
        UICommandBuilder commandBuilder = new UICommandBuilder();
        int skillPoints = playerStore.getSkillTreePoints(playerRef.getUuid());
        commandBuilder.set("#CurrentSkillPointsValue.Text", String.valueOf(skillPoints));
        sendUpdate(commandBuilder, null, false);
    }

    private int parseSkillPointsAmount(Player player) {
        String raw = skillPointsInput != null ? skillPointsInput.trim() : "";
        if (raw.isEmpty()) {
            player.sendMessage(Message.raw("Enter an AP amount."));
            return -1;
        }
        try {
            int value = Integer.parseInt(raw);
            if (value <= 0) {
                player.sendMessage(Message.raw("Amount must be positive."));
                return -1;
            }
            return value;
        } catch (NumberFormatException e) {
            player.sendMessage(Message.raw("Amount must be a number."));
            return -1;
        }
    }

    private BigNumber parseAmount(Player player) {
        String raw = amountInput != null ? amountInput.trim() : "";
        if (raw.isEmpty()) {
            player.sendMessage(Message.raw("Enter a vexa amount."));
            return null;
        }
        try {
            BigNumber value = BigNumber.fromBigDecimal(new java.math.BigDecimal(raw));
            if (!value.gt(BigNumber.ZERO)) {
                player.sendMessage(Message.raw("Amount must be positive."));
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            player.sendMessage(Message.raw("Amount must be a number."));
            return null;
        }
    }

    private void saveVoidYThreshold(Player player) {
        AscendSettingsStore settingsStore = ParkourAscendPlugin.getInstance().getSettingsStore();
        if (settingsStore == null) {
            player.sendMessage(Message.raw("[Ascend] Settings store not available."));
            return;
        }
        String raw = voidYThresholdInput != null ? voidYThresholdInput.trim() : "";
        if (raw.isEmpty()) {
            player.sendMessage(Message.raw("[Ascend] Enter a Y value."));
            return;
        }
        double value;
        try {
            value = Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            player.sendMessage(Message.raw("[Ascend] Enter a valid number."));
            return;
        }
        if (!Double.isFinite(value)) {
            player.sendMessage(Message.raw("[Ascend] Enter a finite number."));
            return;
        }
        settingsStore.setVoidYThreshold(value);
        player.sendMessage(Message.raw("[Ascend] Void Y threshold set to " + String.format("%.2f", value) + ".")
            .color(SystemMessageUtils.SUCCESS));

        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#VoidYThresholdValue.Text", String.format("%.2f", value));
        sendUpdate(commandBuilder, null, false);
    }

    private void clearVoidYThreshold(Player player) {
        AscendSettingsStore settingsStore = ParkourAscendPlugin.getInstance().getSettingsStore();
        if (settingsStore == null) {
            player.sendMessage(Message.raw("[Ascend] Settings store not available."));
            return;
        }
        settingsStore.setVoidYThreshold(null);
        player.sendMessage(Message.raw("[Ascend] Void Y threshold disabled.")
            .color(SystemMessageUtils.SECONDARY));

        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#VoidYThresholdValue.Text", "Not set");
        sendUpdate(commandBuilder, null, false);
    }

    private void setSpawnLocation(Ref<EntityStore> ref, Store<EntityStore> store, Player player) {
        AscendSettingsStore settingsStore = ParkourAscendPlugin.getInstance().getSettingsStore();
        if (settingsStore == null) {
            player.sendMessage(Message.raw("[Ascend] Settings store not available."));
            return;
        }

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            player.sendMessage(Message.raw("[Ascend] Unable to read player position."));
            return;
        }

        Vector3d pos = transform.getPosition();
        Vector3f bodyRot = transform.getRotation();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Vector3f headRot = playerRef != null ? playerRef.getHeadRotation() : null;
        Vector3f rot = headRot != null ? headRot : bodyRot;

        settingsStore.setSpawnPosition(pos.getX(), pos.getY(), pos.getZ(), rot.getX(), rot.getY(), rot.getZ());

        player.sendMessage(Message.raw("[Ascend] Spawn location set!")
            .color(SystemMessageUtils.SUCCESS));
        player.sendMessage(Message.raw("  Pos: " + String.format("%.2f, %.2f, %.2f", pos.getX(), pos.getY(), pos.getZ())));

        // Update UI
        UICommandBuilder commandBuilder = new UICommandBuilder();
        String status = String.format("%.1f, %.1f, %.1f", pos.getX(), pos.getY(), pos.getZ());
        commandBuilder.set("#SpawnLocationStatus.Text", status);
        sendUpdate(commandBuilder, null, false);
    }

    private void setNpcLocation(Ref<EntityStore> ref, Store<EntityStore> store, Player player) {
        AscendSettingsStore settingsStore = ParkourAscendPlugin.getInstance().getSettingsStore();
        if (settingsStore == null) {
            player.sendMessage(Message.raw("[Ascend] Settings store not available."));
            return;
        }

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            player.sendMessage(Message.raw("[Ascend] Unable to read player position."));
            return;
        }

        Vector3d pos = transform.getPosition();
        Vector3f bodyRot = transform.getRotation();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Vector3f headRot = playerRef != null ? playerRef.getHeadRotation() : null;
        Vector3f rot = headRot != null ? headRot : bodyRot;

        settingsStore.setNpcPosition(pos.getX(), pos.getY(), pos.getZ(), rot.getX(), rot.getY(), rot.getZ());

        player.sendMessage(Message.raw("[Ascend] NPC location set!")
            .color(SystemMessageUtils.SUCCESS));
        player.sendMessage(Message.raw("  Pos: " + String.format("%.2f, %.2f, %.2f", pos.getX(), pos.getY(), pos.getZ())));

        // Update UI
        UICommandBuilder commandBuilder = new UICommandBuilder();
        String status = String.format("%.1f, %.1f, %.1f", pos.getX(), pos.getY(), pos.getZ());
        commandBuilder.set("#NpcLocationStatus.Text", status);
        sendUpdate(commandBuilder, null, false);
    }

    public static class VexaData {
        static final String KEY_BUTTON = "Button";
        static final String KEY_AMOUNT = "@VexaAmount";
        static final String KEY_SKILL_POINTS_AMOUNT = "@SkillPointsAmount";
        static final String KEY_VOID_Y_THRESHOLD = "@VoidYThreshold";
        static final String BUTTON_ADD = "AddVexa";
        static final String BUTTON_REMOVE = "RemoveVexa";
        static final String BUTTON_RESET_PROGRESS = "ResetProgress";
        static final String BUTTON_SET_SPAWN_LOCATION = "SetSpawnLocation";
        static final String BUTTON_SET_NPC_LOCATION = "SetNpcLocation";
        static final String BUTTON_ADD_SKILL_POINTS = "AddSkillPoints";
        static final String BUTTON_REMOVE_SKILL_POINTS = "RemoveSkillPoints";
        static final String BUTTON_SAVE_VOID_Y = "SaveVoidY";
        static final String BUTTON_CLEAR_VOID_Y = "ClearVoidY";
        static final String BUTTON_RESET_ALL = "ResetAllPlayers";
        static final String BUTTON_SIMULATE_ASCENSION = "SimulateAscension";
        static final String BUTTON_ENDGAME = "Endgame";
        static final String BUTTON_BACK = "Back";
        static final String BUTTON_CLOSE = "Close";

        public static final BuilderCodec<VexaData> CODEC = BuilderCodec.<VexaData>builder(VexaData.class, VexaData::new)
            .addField(new KeyedCodec<>(KEY_BUTTON, Codec.STRING), (data, value) -> data.button = value, data -> data.button)
            .addField(new KeyedCodec<>(KEY_AMOUNT, Codec.STRING), (data, value) -> data.amount = value, data -> data.amount)
            .addField(new KeyedCodec<>(KEY_SKILL_POINTS_AMOUNT, Codec.STRING), (data, value) -> data.skillPointsAmount = value, data -> data.skillPointsAmount)
            .addField(new KeyedCodec<>(KEY_VOID_Y_THRESHOLD, Codec.STRING), (data, value) -> data.voidYThreshold = value, data -> data.voidYThreshold)
            .build();

        private String button;
        private String amount;
        private String skillPointsAmount;
        private String voidYThreshold;
    }
}
