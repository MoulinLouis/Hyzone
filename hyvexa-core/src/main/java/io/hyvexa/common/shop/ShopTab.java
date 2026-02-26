package io.hyvexa.common.shop;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;

public interface ShopTab {

    String getId();

    String getLabel();

    String getAccentColor();

    int getOrder();

    default boolean isVisibleTo(UUID playerId) {
        return true;
    }

    void buildContent(UICommandBuilder cmd, UIEventBuilder evt, UUID playerId, long vexa);

    ShopTabResult handleEvent(String button, Ref<EntityStore> ref, Store<EntityStore> store,
                              Player player, UUID playerId);

    default ShopTabResult handleSearch(String searchText, UUID playerId) {
        return ShopTabResult.NONE;
    }

    default void evictPlayer(UUID playerId) {}

    default void populateConfirmOverlay(UICommandBuilder cmd, String confirmKey) {}

    default boolean handleConfirm(String confirmKey, Ref<EntityStore> ref, Store<EntityStore> store,
                                  Player player, UUID playerId) {
        return false;
    }
}
