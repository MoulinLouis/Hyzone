package io.hyvexa.ascend.mine.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.mine.MineManager;
import io.hyvexa.ascend.mine.data.Mine;
import io.hyvexa.ascend.mine.data.MineHierarchyStore;
import io.hyvexa.ascend.mine.data.MinerConfigStore;
import io.hyvexa.ascend.mine.data.MineZone;
import io.hyvexa.ascend.mine.data.MineZoneLayer;
import io.hyvexa.ascend.mine.data.MinerDefinition;
import io.hyvexa.ascend.mine.data.MinerRarity;
import io.hyvexa.ascend.mine.data.MinerVariant;
import io.hyvexa.ascend.ui.AscendAdminNavigator;
import io.hyvexa.common.ui.AccentOverlayUtils;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class MinerDefAdminPage extends InteractiveCustomUIPage<MinerDefAdminPage.MinerDefData> {

    private static final String[] BANNER_IDS = {"BannerGray", "BannerGreen", "BannerBlue", "BannerPurple", "BannerOrange"};

    // Cycleable portraits (excludes PortraitEmpty)
    private static final List<String> CYCLEABLE_PORTRAITS;
    static {
        CYCLEABLE_PORTRAITS = new ArrayList<>();
        for (String id : MinerVariant.ALL_PORTRAIT_IDS) {
            if (!id.equals("PortraitEmpty")) {
                CYCLEABLE_PORTRAITS.add(id);
            }
        }
    }

    private final PlayerRef playerRef;
    private final MineHierarchyStore hierarchyStore;
    private final MinerConfigStore minerConfigStore;
    private final MineManager mineManager;
    private final AscendAdminNavigator adminNavigator;
    private final String mineId;

    private String selectedLayerId = "";
    // Current portrait index per rarity (in CYCLEABLE_PORTRAITS)
    private final int[] portraitIndex = new int[MinerRarity.values().length];
    // Cached name field values per rarity
    private final String[] cachedNames = new String[MinerRarity.values().length];

    public MinerDefAdminPage(@Nonnull PlayerRef playerRef,
                             MineHierarchyStore hierarchyStore,
                             MinerConfigStore minerConfigStore,
                             MineManager mineManager,
                             AscendAdminNavigator adminNavigator,
                             String mineId) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, MinerDefData.CODEC);
        this.playerRef = playerRef;
        this.hierarchyStore = hierarchyStore;
        this.minerConfigStore = minerConfigStore;
        this.mineManager = mineManager;
        this.adminNavigator = adminNavigator;
        this.mineId = mineId;
        Arrays.fill(cachedNames, "");
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/Ascend_MinerDefAdmin.ui");

        Mine mine = hierarchyStore.getMine(mineId);
        String mineName = mine != null ? mine.getName() : mineId;
        cmd.set("#HeaderTitle.Text", "Miner Definitions - " + mineName);

        bindEvents(evt);
        buildLayerList(cmd, evt);
        buildRarityCards(cmd, evt);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull MinerDefData data) {
        super.handleDataEvent(ref, store, data);

        // Cache name fields
        if (data.nameCommon != null) cachedNames[MinerRarity.COMMON.ordinal()] = data.nameCommon.trim();
        if (data.nameUncommon != null) cachedNames[MinerRarity.UNCOMMON.ordinal()] = data.nameUncommon.trim();
        if (data.nameRare != null) cachedNames[MinerRarity.RARE.ordinal()] = data.nameRare.trim();
        if (data.nameEpic != null) cachedNames[MinerRarity.EPIC.ordinal()] = data.nameEpic.trim();
        if (data.nameLegendary != null) cachedNames[MinerRarity.LEGENDARY.ordinal()] = data.nameLegendary.trim();

        if (data.button == null) return;

        if (data.button.equals(MinerDefData.BUTTON_BACK)) {
            handleBack(ref, store);
            return;
        }
        if (data.button.startsWith(MinerDefData.BUTTON_SELECT_LAYER_PREFIX)) {
            handleSelectLayer(data.button.substring(MinerDefData.BUTTON_SELECT_LAYER_PREFIX.length()), ref, store);
            return;
        }
        if (data.button.startsWith(MinerDefData.BUTTON_SAVE_PREFIX)) {
            handleSave(data.button.substring(MinerDefData.BUTTON_SAVE_PREFIX.length()), ref, store);
            return;
        }
        if (data.button.startsWith(MinerDefData.BUTTON_PREV_PREFIX)) {
            handlePrevPortrait(data.button.substring(MinerDefData.BUTTON_PREV_PREFIX.length()), ref, store);
            return;
        }
        if (data.button.startsWith(MinerDefData.BUTTON_NEXT_PREFIX)) {
            handleNextPortrait(data.button.substring(MinerDefData.BUTTON_NEXT_PREFIX.length()), ref, store);
            return;
        }
    }

    private void handleBack(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef pRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || pRef == null) return;
        MineAdminPage page = adminNavigator.createMineAdminPage(pRef);
        if (page != null) {
            player.getPageManager().openCustomPage(ref, store, page);
        }
    }

    private void handleSelectLayer(String layerId, Ref<EntityStore> ref, Store<EntityStore> store) {
        selectedLayerId = layerId;
        // Load current definitions for this layer into portrait indices + cached names
        Map<MinerRarity, MinerDefinition> defs = minerConfigStore.getMinerDefinitions(layerId);
        for (MinerRarity rarity : MinerRarity.values()) {
            int ord = rarity.ordinal();
            MinerDefinition def = defs.get(rarity);
            if (def != null) {
                cachedNames[ord] = def.displayName();
                int idx = CYCLEABLE_PORTRAITS.indexOf(def.portraitId());
                portraitIndex[ord] = idx >= 0 ? idx : 0;
            } else {
                cachedNames[ord] = MinerVariant.getDefaultDisplayName(rarity);
                int idx = CYCLEABLE_PORTRAITS.indexOf(MinerVariant.getDefaultPortraitId(rarity));
                portraitIndex[ord] = idx >= 0 ? idx : 0;
            }
        }
        sendRefresh(ref, store);
    }

    private void handleSave(String rarityName, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (selectedLayerId.isEmpty()) return;
        MinerRarity rarity = MinerRarity.fromName(rarityName);
        if (rarity == null) return;
        int ord = rarity.ordinal();
        String name = cachedNames[ord];
        if (name.isEmpty()) name = MinerVariant.getDefaultDisplayName(rarity);
        String portraitId = CYCLEABLE_PORTRAITS.get(portraitIndex[ord]);
        minerConfigStore.saveMinerDefinition(new MinerDefinition(selectedLayerId, rarity, name, portraitId));
        sendRefresh(ref, store);
    }

    private void handlePrevPortrait(String rarityName, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (selectedLayerId.isEmpty()) return;
        MinerRarity rarity = MinerRarity.fromName(rarityName);
        if (rarity == null) return;
        int ord = rarity.ordinal();
        portraitIndex[ord] = (portraitIndex[ord] - 1 + CYCLEABLE_PORTRAITS.size()) % CYCLEABLE_PORTRAITS.size();
        // Save immediately on portrait cycle
        String name = cachedNames[ord];
        if (name.isEmpty()) name = MinerVariant.getDefaultDisplayName(rarity);
        String portraitId = CYCLEABLE_PORTRAITS.get(portraitIndex[ord]);
        minerConfigStore.saveMinerDefinition(new MinerDefinition(selectedLayerId, rarity, name, portraitId));
        sendRefresh(ref, store);
    }

    private void handleNextPortrait(String rarityName, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (selectedLayerId.isEmpty()) return;
        MinerRarity rarity = MinerRarity.fromName(rarityName);
        if (rarity == null) return;
        int ord = rarity.ordinal();
        portraitIndex[ord] = (portraitIndex[ord] + 1) % CYCLEABLE_PORTRAITS.size();
        // Save immediately on portrait cycle
        String name = cachedNames[ord];
        if (name.isEmpty()) name = MinerVariant.getDefaultDisplayName(rarity);
        String portraitId = CYCLEABLE_PORTRAITS.get(portraitIndex[ord]);
        minerConfigStore.saveMinerDefinition(new MinerDefinition(selectedLayerId, rarity, name, portraitId));
        sendRefresh(ref, store);
    }

    // ---- UI building ----

    private void bindEvents(UIEventBuilder evt) {
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
            EventData.of(MinerDefData.KEY_BUTTON, MinerDefData.BUTTON_BACK), false);
    }

    private void buildLayerList(UICommandBuilder cmd, UIEventBuilder evt) {
        cmd.clear("#LayerList");

        Mine mine = hierarchyStore.getMine(mineId);
        if (mine == null) return;

        List<MineZoneLayer> allLayers = new ArrayList<>();
        for (MineZone zone : mine.getZones()) {
            allLayers.addAll(zone.getLayers());
        }

        int index = 0;
        for (MineZoneLayer layer : allLayers) {
            cmd.append("#LayerList", "Pages/Ascend_MineAdminEntry.ui");
            String sel = "#LayerList[" + index + "]";
            String label = layer.getDisplayName() != null && !layer.getDisplayName().isEmpty()
                ? layer.getDisplayName() : layer.getId();
            boolean isSelected = layer.getId().equals(selectedLayerId);
            if (isSelected) {
                cmd.set(sel + " #SelectedOverlay.Visible", true);
                cmd.set(sel + " #AccentBar.Visible", true);
            }
            cmd.set(sel + " #MineName.Text", label);
            cmd.set(sel + " #MineStatus.Text", "Y: " + layer.getMinY() + "-" + layer.getMaxY());
            evt.addEventBinding(CustomUIEventBindingType.Activating, sel,
                EventData.of(MinerDefData.KEY_BUTTON, MinerDefData.BUTTON_SELECT_LAYER_PREFIX + layer.getId()), false);
            index++;
        }
    }

    private void buildRarityCards(UICommandBuilder cmd, UIEventBuilder evt) {
        cmd.clear("#RarityCards");

        if (selectedLayerId.isEmpty()) {
            cmd.set("#SelectedLayerLabel.Text", "Select a layer");
            return;
        }

        MineZoneLayer layer = hierarchyStore.getLayerById(selectedLayerId);
        String layerLabel = layer != null && layer.getDisplayName() != null && !layer.getDisplayName().isEmpty()
            ? layer.getDisplayName() : selectedLayerId;
        cmd.set("#SelectedLayerLabel.Text", "Layer: " + layerLabel);

        for (MinerRarity rarity : MinerRarity.values()) {
            int ord = rarity.ordinal();
            cmd.append("#RarityCards", "Pages/Ascend_MinerDefRarityCard.ui");
            String sel = "#RarityCards[" + ord + "]";

            // Rarity banner
            AccentOverlayUtils.applyAccent(cmd, sel + " #CardBorder",
                rarity.getColor(), AccentOverlayUtils.RARITY_ACCENTS);
            String bannerTarget = BANNER_IDS[ord];
            for (String bid : BANNER_IDS) {
                cmd.set(sel + " #RarityBanner #" + bid + ".Visible", bid.equals(bannerTarget));
            }
            cmd.set(sel + " #RarityLabel.Text", rarity.getDisplayName());
            cmd.set(sel + " #RarityLabel.Style.TextColor", rarity.getColor());

            // Portrait
            String portraitId = CYCLEABLE_PORTRAITS.get(portraitIndex[ord]);
            MinerVariant.applyPortraitById(cmd, sel + " #PortraitZone", portraitId);

            // Name
            cmd.set(sel + " #NameLabel.Text", cachedNames[ord]);
            cmd.set(sel + " #NameField.Value", cachedNames[ord]);

            // Button bindings
            evt.addEventBinding(CustomUIEventBindingType.Activating, sel + " #PrevButton",
                EventData.of(MinerDefData.KEY_BUTTON, MinerDefData.BUTTON_PREV_PREFIX + rarity.name()), false);
            evt.addEventBinding(CustomUIEventBindingType.Activating, sel + " #NextButton",
                EventData.of(MinerDefData.KEY_BUTTON, MinerDefData.BUTTON_NEXT_PREFIX + rarity.name()), false);
            evt.addEventBinding(CustomUIEventBindingType.Activating, sel + " #SaveButton",
                EventData.of(MinerDefData.KEY_BUTTON, MinerDefData.BUTTON_SAVE_PREFIX + rarity.name()), false);

            // Name field ValueChanged — bound here because cards only exist after append
            String nameKey = switch (rarity) {
                case COMMON -> MinerDefData.KEY_NAME_COMMON;
                case UNCOMMON -> MinerDefData.KEY_NAME_UNCOMMON;
                case RARE -> MinerDefData.KEY_NAME_RARE;
                case EPIC -> MinerDefData.KEY_NAME_EPIC;
                case LEGENDARY -> MinerDefData.KEY_NAME_LEGENDARY;
            };
            evt.addEventBinding(CustomUIEventBindingType.ValueChanged, sel + " #NameField",
                EventData.of(nameKey, sel + " #NameField.Value"), false);
        }
    }

    private void sendRefresh(Ref<EntityStore> ref, Store<EntityStore> store) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();
        bindEvents(evt);
        buildLayerList(cmd, evt);
        buildRarityCards(cmd, evt);
        this.sendUpdate(cmd, evt, false);
    }

    // ---- Codec data class ----

    public static class MinerDefData {
        static final String KEY_BUTTON = "Button";
        static final String KEY_NAME_COMMON = "@NameCommon";
        static final String KEY_NAME_UNCOMMON = "@NameUncommon";
        static final String KEY_NAME_RARE = "@NameRare";
        static final String KEY_NAME_EPIC = "@NameEpic";
        static final String KEY_NAME_LEGENDARY = "@NameLegendary";
        static final String BUTTON_BACK = "Back";
        static final String BUTTON_SELECT_LAYER_PREFIX = "SelectLayer:";
        static final String BUTTON_SAVE_PREFIX = "Save:";
        static final String BUTTON_PREV_PREFIX = "PrevPortrait:";
        static final String BUTTON_NEXT_PREFIX = "NextPortrait:";

        public static final BuilderCodec<MinerDefData> CODEC = BuilderCodec.<MinerDefData>builder(MinerDefData.class, MinerDefData::new)
            .addField(new KeyedCodec<>(KEY_BUTTON, Codec.STRING), (d, v) -> d.button = v, d -> d.button)
            .addField(new KeyedCodec<>(KEY_NAME_COMMON, Codec.STRING), (d, v) -> d.nameCommon = v, d -> d.nameCommon)
            .addField(new KeyedCodec<>(KEY_NAME_UNCOMMON, Codec.STRING), (d, v) -> d.nameUncommon = v, d -> d.nameUncommon)
            .addField(new KeyedCodec<>(KEY_NAME_RARE, Codec.STRING), (d, v) -> d.nameRare = v, d -> d.nameRare)
            .addField(new KeyedCodec<>(KEY_NAME_EPIC, Codec.STRING), (d, v) -> d.nameEpic = v, d -> d.nameEpic)
            .addField(new KeyedCodec<>(KEY_NAME_LEGENDARY, Codec.STRING), (d, v) -> d.nameLegendary = v, d -> d.nameLegendary)
            .build();

        private String button;
        private String nameCommon;
        private String nameUncommon;
        private String nameRare;
        private String nameEpic;
        private String nameLegendary;
    }
}
