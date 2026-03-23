package io.hyvexa.purge;

import com.hypixel.hytale.server.core.entity.entities.Player;
import io.hyvexa.purge.data.PurgeSessionPlayerState;

/**
 * Encapsulates loadout and weapon operations extracted from HyvexaPurgePlugin.
 * Consumed by managers, pages, and other components that need loadout capabilities
 * without depending on the plugin singleton.
 */
public interface PurgeLoadoutService {

    void grantLoadout(Player player, PurgeSessionPlayerState state);

    void removeLoadout(Player player);

    void giveWaitingLoadout(Player player);

    void switchWeapon(Player player, PurgeSessionPlayerState state, String newWeaponId);

    void switchMeleeWeapon(Player player, PurgeSessionPlayerState state, String newMeleeId);

    void grantLootbox(Player player, int count);
}
