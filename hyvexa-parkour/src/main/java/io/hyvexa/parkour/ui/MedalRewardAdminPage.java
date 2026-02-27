package io.hyvexa.parkour.ui;

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
import io.hyvexa.HyvexaPlugin;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.MedalRewardStore;
import io.hyvexa.parkour.data.ProgressStore;

import javax.annotation.Nonnull;

public class MedalRewardAdminPage extends InteractiveCustomUIPage<MedalRewardAdminPage.RewardData> {

    private static final String[] CATEGORIES = {"easy", "medium", "hard", "insane"};
    private static final String[] CATEGORY_PREFIXES = {"Easy", "Medium", "Hard", "Insane"};

    private final MapStore mapStore;
    private final ProgressStore progressStore;

    // 4 categories x 4 tiers (bronze/silver/gold/emerald)
    private final String[][] values = new String[4][4];
    // Separate insane completion reward (only for insane category)
    private String insaneRewardValue = "0";

    public MedalRewardAdminPage(@Nonnull PlayerRef playerRef, MapStore mapStore, ProgressStore progressStore) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, RewardData.CODEC);
        this.mapStore = mapStore;
        this.progressStore = progressStore;
        loadCurrentValues();
    }

    private void loadCurrentValues() {
        MedalRewardStore store = MedalRewardStore.getInstance();
        for (int i = 0; i < CATEGORIES.length; i++) {
            MedalRewardStore.MedalRewards r = store.getRewards(CATEGORIES[i]);
            if (r != null) {
                values[i][0] = String.valueOf(r.bronze);
                values[i][1] = String.valueOf(r.silver);
                values[i][2] = String.valueOf(r.gold);
                values[i][3] = String.valueOf(r.emerald);
            } else {
                values[i][0] = "0";
                values[i][1] = "0";
                values[i][2] = "0";
                values[i][3] = "0";
            }
        }
        // Load insane reward from the insane category
        MedalRewardStore.MedalRewards insaneRewards = store.getRewards("insane");
        insaneRewardValue = insaneRewards != null ? String.valueOf(insaneRewards.insane) : "0";
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Parkour_MedalRewardAdmin.ui");
        populateFields(uiCommandBuilder);
        bindEvents(uiEventBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull RewardData data) {
        super.handleDataEvent(ref, store, data);
        // Update cached values from UI
        for (int i = 0; i < CATEGORIES.length; i++) {
            if (data.values[i][0] != null) values[i][0] = data.values[i][0].trim();
            if (data.values[i][1] != null) values[i][1] = data.values[i][1].trim();
            if (data.values[i][2] != null) values[i][2] = data.values[i][2].trim();
            if (data.values[i][3] != null) values[i][3] = data.values[i][3].trim();
        }
        if (data.insaneReward != null) {
            insaneRewardValue = data.insaneReward.trim();
        }
        if (data.button == null) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        if ("Back".equals(data.button)) {
            HyvexaPlugin plugin = HyvexaPlugin.getInstance();
            if (plugin != null) {
                player.getPageManager().openCustomPage(ref, store,
                        new AdminIndexPage(playerRef, mapStore, progressStore, plugin.getSettingsStore(),
                                plugin.getPlayerCountStore()));
            }
            return;
        }
        if ("Save".equals(data.button)) {
            handleSave(player, ref, store);
        }
    }

    private void handleSave(Player player, Ref<EntityStore> ref, Store<EntityStore> store) {
        MedalRewardStore rewardStore = MedalRewardStore.getInstance();
        int insaneReward = parseNonNegative(insaneRewardValue);
        if (insaneReward < 0) {
            player.sendMessage(Message.raw("Insane completion reward must be a non-negative integer."));
            return;
        }
        for (int i = 0; i < CATEGORIES.length; i++) {
            int bronze = parseNonNegative(values[i][0]);
            int silver = parseNonNegative(values[i][1]);
            int gold = parseNonNegative(values[i][2]);
            int emerald = parseNonNegative(values[i][3]);
            if (bronze < 0 || silver < 0 || gold < 0 || emerald < 0) {
                player.sendMessage(Message.raw(CATEGORY_PREFIXES[i] + ": values must be non-negative integers."));
                return;
            }
            // Only the insane category gets the insane completion reward
            int insane = CATEGORIES[i].equals("insane") ? insaneReward : 0;
            rewardStore.setRewards(CATEGORIES[i], bronze, silver, gold, emerald, insane);
        }
        player.sendMessage(Message.raw("Medal rewards saved."));
        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#StatusLabel.Text", "Saved!");
        UIEventBuilder eventBuilder = new UIEventBuilder();
        populateFields(commandBuilder);
        bindEvents(eventBuilder);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    private int parseNonNegative(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return 0;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value >= 0 ? value : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private void populateFields(UICommandBuilder commandBuilder) {
        for (int i = 0; i < CATEGORIES.length; i++) {
            commandBuilder.set("#" + CATEGORY_PREFIXES[i] + "Bronze.Value", values[i][0]);
            commandBuilder.set("#" + CATEGORY_PREFIXES[i] + "Silver.Value", values[i][1]);
            commandBuilder.set("#" + CATEGORY_PREFIXES[i] + "Gold.Value", values[i][2]);
            commandBuilder.set("#" + CATEGORY_PREFIXES[i] + "Emerald.Value", values[i][3]);
        }
        commandBuilder.set("#InsaneRewardField.Value", insaneRewardValue);
    }

    private void bindEvents(UIEventBuilder uiEventBuilder) {
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(RewardData.KEY_BUTTON, "Back"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SaveButton",
                EventData.of(RewardData.KEY_BUTTON, "Save"), false);
        for (int i = 0; i < CATEGORIES.length; i++) {
            String prefix = CATEGORY_PREFIXES[i];
            uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#" + prefix + "Bronze",
                    EventData.of(RewardData.KEYS[i][0], "#" + prefix + "Bronze.Value"), false);
            uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#" + prefix + "Silver",
                    EventData.of(RewardData.KEYS[i][1], "#" + prefix + "Silver.Value"), false);
            uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#" + prefix + "Gold",
                    EventData.of(RewardData.KEYS[i][2], "#" + prefix + "Gold.Value"), false);
            uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#" + prefix + "Emerald",
                    EventData.of(RewardData.KEYS[i][3], "#" + prefix + "Emerald.Value"), false);
        }
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#InsaneRewardField",
                EventData.of(RewardData.KEY_INSANE_REWARD, "#InsaneRewardField.Value"), false);
    }

    public static class RewardData {
        static final String KEY_BUTTON = "Button";
        static final String KEY_INSANE_REWARD = "@InsaneReward";
        static final String[][] KEYS = {
                {"@EasyBronze", "@EasySilver", "@EasyGold", "@EasyEmerald"},
                {"@MediumBronze", "@MediumSilver", "@MediumGold", "@MediumEmerald"},
                {"@HardBronze", "@HardSilver", "@HardGold", "@HardEmerald"},
                {"@InsaneBronze", "@InsaneSilver", "@InsaneGold", "@InsaneEmerald"}
        };

        String button;
        String insaneReward;
        final String[][] values = new String[4][4];

        public static final BuilderCodec<RewardData> CODEC;

        static {
            var builder = BuilderCodec.<RewardData>builder(RewardData.class, RewardData::new)
                    .addField(new KeyedCodec<>(KEY_BUTTON, Codec.STRING),
                            (data, value) -> data.button = value, data -> data.button)
                    .addField(new KeyedCodec<>(KEY_INSANE_REWARD, Codec.STRING),
                            (data, value) -> data.insaneReward = value, data -> data.insaneReward);
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    final int ci = i;
                    final int cj = j;
                    builder.addField(new KeyedCodec<>(KEYS[i][j], Codec.STRING),
                            (data, value) -> data.values[ci][cj] = value,
                            data -> data.values[ci][cj]);
                }
            }
            CODEC = builder.build();
        }
    }
}
