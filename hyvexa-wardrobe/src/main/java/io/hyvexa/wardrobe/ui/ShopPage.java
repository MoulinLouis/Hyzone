package io.hyvexa.wardrobe.ui;

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
import io.hyvexa.common.shop.ShopTab;
import io.hyvexa.common.shop.ShopTabRegistry;
import io.hyvexa.common.shop.ShopTabResult;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.core.economy.VexaStore;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ShopPage extends InteractiveCustomUIPage<ButtonEventData> {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_CONFIRM = "ShopConfirm";
    private static final String BUTTON_CANCEL = "ShopCancel";
    private static final String BUTTON_VEXA_PACKS = "VexaPacks";
    private static final String BUTTON_PACK = "Pack";
    private static final String STORE_URL = "https://store.hyvexa.com";
    private static final String TAB_PREFIX = "Tab:";

    private final UUID playerId;
    private String activeTabId;
    private boolean showingVexaPacks;
    private String pendingConfirmKey;
    private String pendingConfirmTabId;

    public ShopPage(@Nonnull PlayerRef playerRef, UUID playerId) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ButtonEventData.CODEC);
        this.playerId = playerId;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/Shop.ui");

        long vexa = VexaStore.getInstance().getVexa(playerId);
        cmd.set("#VexaBalance.Text", String.valueOf(vexa));

        // Close button
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);

        // Confirm/Cancel buttons
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ShopConfirmButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CONFIRM), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ShopCancelButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CANCEL), false);

        // Vexa packs button
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#VexaPacksButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_VEXA_PACKS), false);

        List<ShopTab> tabs = getVisibleTabs();
        if (tabs.isEmpty()) return;

        ShopTab activeTab = resolveActiveTab(tabs);

        buildTabBar(cmd, evt, tabs);

        // Build content: vexa packs or active tab
        if (showingVexaPacks) {
            buildVexaPacksContent(cmd, evt);
        } else {
            if (activeTab != null) {
                activeTab.buildContent(cmd, evt, playerId, vexa);
            }
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ButtonEventData data) {
        super.handleDataEvent(ref, store, data);
        String button = data.getButton();
        if (button == null) return;

        if (BUTTON_CLOSE.equals(button)) {
            close();
            return;
        }

        if (BUTTON_CONFIRM.equals(button)) {
            handleConfirm(ref, store);
            return;
        }

        if (BUTTON_CANCEL.equals(button)) {
            handleCancel();
            return;
        }

        if (BUTTON_VEXA_PACKS.equals(button)) {
            showingVexaPacks = true;
            pendingConfirmKey = null;
            pendingConfirmTabId = null;
            sendFullRefresh();
            return;
        }

        if (BUTTON_PACK.equals(button)) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.sendMessage(Message.raw(""));
                player.sendMessage(Message.join(
                        Message.raw("(NOT AVAILABLE) Continue your purchase here: ").color("#94a3b8"),
                        Message.raw("store.hyvexa.com").color("#8ab4f8").link(STORE_URL)
                ));
                player.sendMessage(Message.raw(""));
            }
            close();
            return;
        }

        // Tab switch
        if (button.startsWith(TAB_PREFIX)) {
            String tabId = button.substring(TAB_PREFIX.length());
            if (getVisibleTab(tabId) == null) {
                return;
            }
            if (showingVexaPacks || !tabId.equals(activeTabId)) {
                activeTabId = tabId;
                showingVexaPacks = false;
                pendingConfirmKey = null;
                pendingConfirmTabId = null;
                sendFullRefresh();
            }
            return;
        }

        // Route to active tab: button format is "tabId:action"
        int colonIdx = button.indexOf(':');
        if (colonIdx > 0) {
            String tabId = button.substring(0, colonIdx);
            String tabButton = button.substring(colonIdx + 1);
            ShopTab tab = getVisibleTab(tabId);
            if (tab != null) {
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player == null) return;
                ShopTabResult result = tab.handleEvent(tabButton, ref, store, player, playerId);
                handleResult(result, tab);
            }
        }
    }

    private void handleResult(ShopTabResult result, ShopTab tab) {
        switch (result.getType()) {
            case NONE -> {}
            case REFRESH -> sendFullRefresh();
            case SHOW_CONFIRM -> showConfirmOverlay(tab, result.getConfirmKey());
            case HIDE_CONFIRM -> hideConfirmOverlay();
        }
    }

    private void handleConfirm(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (pendingConfirmKey == null || pendingConfirmTabId == null) return;
        ShopTab tab = getVisibleTab(pendingConfirmTabId);
        if (tab == null) return;

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        String key = pendingConfirmKey;
        pendingConfirmKey = null;
        pendingConfirmTabId = null;

        boolean handled = tab.handleConfirm(key, ref, store, player, playerId);
        if (handled) {
            sendFullRefresh();
        } else {
            hideConfirmOverlay();
        }
    }

    private void handleCancel() {
        pendingConfirmKey = null;
        pendingConfirmTabId = null;
        hideConfirmOverlay();
    }

    private void sendFullRefresh() {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();

        long vexa = VexaStore.getInstance().getVexa(playerId);
        cmd.set("#VexaBalance.Text", String.valueOf(vexa));
        cmd.set("#ConfirmOverlay.Visible", false);

        cmd.clear("#TabBar");
        cmd.clear("#TabContent");

        // Close button
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ShopConfirmButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CONFIRM), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ShopCancelButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CANCEL), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#VexaPacksButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_VEXA_PACKS), false);

        List<ShopTab> tabs = getVisibleTabs();
        ShopTab activeTab = resolveActiveTab(tabs);
        buildTabBar(cmd, evt, tabs);

        if (showingVexaPacks) {
            buildVexaPacksContent(cmd, evt);
        } else {
            if (activeTab != null) {
                activeTab.buildContent(cmd, evt, playerId, vexa);
            }
        }

        this.sendUpdate(cmd, evt, false);
    }

    private void buildTabBar(UICommandBuilder cmd, UIEventBuilder evt, List<ShopTab> tabs) {
        for (int i = 0; i < tabs.size(); i++) {
            ShopTab tab = tabs.get(i);
            boolean active = !showingVexaPacks && tab.getId().equals(activeTabId);

            cmd.append("#TabBar", "Pages/Shop_TabEntry.ui");
            String root = "#TabBar[" + i + "] ";

            cmd.set(root + "#TabLabel.Text", tab.getLabel());
            if (active) {
                cmd.set(root + "#TabLabel.Style.TextColor", "#f0f4f8");
                cmd.set(root + "#TabLabel.Style.RenderBold", true);
                cmd.set(root + "#TabAccent.Visible", true);
                cmd.set(root + "#TabAccent.Background", tab.getAccentColor());
            }

            evt.addEventBinding(CustomUIEventBindingType.Activating,
                    root + "#TabButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, TAB_PREFIX + tab.getId()), false);
        }
    }

    private void buildVexaPacksContent(UICommandBuilder cmd, UIEventBuilder evt) {
        cmd.append("#TabContent", "Pages/Shop_VexaPacks.ui");
        String root = "#TabContent[0] ";
        evt.addEventBinding(CustomUIEventBindingType.Activating, root + "#PackBtnA",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_PACK), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, root + "#PackBtnB",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_PACK), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, root + "#PackBtnC",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_PACK), false);
    }

    private void showConfirmOverlay(ShopTab tab, String confirmKey) {
        pendingConfirmKey = confirmKey;
        pendingConfirmTabId = tab.getId();

        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#ConfirmOverlay.Visible", true);
        tab.populateConfirmOverlay(cmd, confirmKey);
        this.sendUpdate(cmd, new UIEventBuilder(), false);
    }

    private void hideConfirmOverlay() {
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#ConfirmOverlay.Visible", false);
        this.sendUpdate(cmd, new UIEventBuilder(), false);
    }

    private List<ShopTab> getVisibleTabs() {
        return ShopTabRegistry.getTabs().stream()
                .filter(tab -> tab.isVisibleTo(playerId))
                .collect(Collectors.toList());
    }

    private ShopTab getVisibleTab(String tabId) {
        ShopTab tab = ShopTabRegistry.getTab(tabId);
        if (tab == null || !tab.isVisibleTo(playerId)) {
            return null;
        }
        return tab;
    }

    private ShopTab resolveActiveTab(List<ShopTab> tabs) {
        if (tabs.isEmpty()) {
            activeTabId = null;
            return null;
        }

        ShopTab activeTab = getVisibleTab(activeTabId);
        if (activeTab == null) {
            activeTab = tabs.get(0);
            activeTabId = activeTab.getId();
        }
        return activeTab;
    }


}
