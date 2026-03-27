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
import io.hyvexa.ascend.mine.MineBlockRegistry;
import io.hyvexa.ascend.mine.MineManager;
import io.hyvexa.ascend.mine.data.BlockConfigStore;
import io.hyvexa.ascend.mine.data.MineHierarchyStore;
import io.hyvexa.ascend.ui.AscendAdminNavigator;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

public class MineBlockPickerPage extends InteractiveCustomUIPage<MineBlockPickerPage.PickerData> {

    private static final String SELECT_PREFIX = "Select:";

    private final MineHierarchyStore hierarchyStore;
    private final BlockConfigStore blockConfigStore;
    private final MineManager mineManager;
    private final String selectedZoneId;
    private final String selectedLayerId;
    private final String currentBlockId;
    private final AscendAdminNavigator adminNavigator;

    public MineBlockPickerPage(@Nonnull PlayerRef playerRef, MineHierarchyStore hierarchyStore,
                               BlockConfigStore blockConfigStore, MineManager mineManager,
                               String selectedZoneId, String selectedLayerId, String currentBlockId,
                               AscendAdminNavigator adminNavigator) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PickerData.CODEC);
        this.hierarchyStore = hierarchyStore;
        this.blockConfigStore = blockConfigStore;
        this.mineManager = mineManager;
        this.selectedZoneId = selectedZoneId;
        this.selectedLayerId = selectedLayerId != null ? selectedLayerId : "";
        this.currentBlockId = currentBlockId != null ? currentBlockId : "";
        this.adminNavigator = adminNavigator;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/Ascend_MineBlockPicker.ui");

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
            EventData.of("Button", "Back"), false);

        int index = 0;
        for (Map.Entry<String, List<MineBlockRegistry.BlockDef>> category : MineBlockRegistry.getByCategory().entrySet()) {
            cmd.append("#BlockEntries", "Pages/Ascend_MineBlockPickerHeader.ui");
            cmd.set("#BlockEntries[" + index + "] #CategoryName.Text", category.getKey());
            index++;

            for (MineBlockRegistry.BlockDef block : category.getValue()) {
                cmd.append("#BlockEntries", "Pages/Ascend_MineBlockPickerEntry.ui");
                String sel = "#BlockEntries[" + index + "]";
                cmd.set(sel + " #BlockIcon.ItemId", block.blockTypeId);
                cmd.set(sel + " #BlockName.Text", block.displayName);
                evt.addEventBinding(CustomUIEventBindingType.Activating, sel,
                    EventData.of("Button", SELECT_PREFIX + block.blockTypeId), false);
                index++;
            }
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull PickerData data) {
        super.handleDataEvent(ref, store, data);
        if (data.button == null) return;

        if (data.button.equals("Back")) {
            openZoneAdmin(ref, store, currentBlockId);
            return;
        }

        if (data.button.startsWith(SELECT_PREFIX)) {
            String selectedBlock = data.button.substring(SELECT_PREFIX.length());
            openZoneAdmin(ref, store, selectedBlock);
        }
    }

    private void openZoneAdmin(Ref<EntityStore> ref, Store<EntityStore> store, String selectedBlockId) {
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) return;

        String mineId = hierarchyStore.getMineId();
        if (mineId == null) return;
        MineZoneAdminPage page = new MineZoneAdminPage(
            playerRef, hierarchyStore, blockConfigStore, mineManager, adminNavigator, mineId);
        page.setSelectedZoneId(selectedZoneId);
        page.setSelectedLayerId(selectedLayerId);
        page.setSelectedBlockId(selectedBlockId);
        player.getPageManager().openCustomPage(ref, store, page);
    }

    public static class PickerData {
        public static final BuilderCodec<PickerData> CODEC = BuilderCodec.<PickerData>builder(PickerData.class, PickerData::new)
            .addField(new KeyedCodec<>("Button", Codec.STRING), (data, value) -> data.button = value, data -> data.button)
            .build();

        private String button;
    }
}
