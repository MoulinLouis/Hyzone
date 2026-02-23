package io.hyvexa.purge.manager;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.purge.data.PurgeSession;
import io.hyvexa.purge.data.PurgeSessionPlayerState;
import io.hyvexa.purge.data.PurgeUpgradeOffer;
import io.hyvexa.purge.data.PurgeUpgradeRarity;
import io.hyvexa.purge.data.PurgeUpgradeState;
import io.hyvexa.purge.data.PurgeUpgradeType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class PurgeUpgradeManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String PURGE_HP_UPGRADE_MODIFIER = "purge_upgrade_hp";
    private static final short SLOT_WEAPON = 0;

    public List<PurgeUpgradeOffer> selectRandomOffers(int count, int luck) {
        List<PurgeUpgradeType> pool = new ArrayList<>(List.of(PurgeUpgradeType.values()));
        Collections.shuffle(pool);
        List<PurgeUpgradeType> picked = pool.subList(0, Math.min(count, pool.size()));

        List<PurgeUpgradeOffer> offers = new ArrayList<>(picked.size());
        for (PurgeUpgradeType type : picked) {
            PurgeUpgradeRarity rarity = rollRarity(luck);
            offers.add(new PurgeUpgradeOffer(type, rarity));
        }
        return offers;
    }

    private PurgeUpgradeRarity rollRarity(int luck) {
        int wCommon = Math.max(5, 50 - 5 * luck);
        int wUncommon = 25;
        int wRare = 15 + 2 * luck;
        int wEpic = 8 + 2 * luck;
        int wLegendary = 2 + luck;

        int total = wCommon + wUncommon + wRare + wEpic + wLegendary;
        int roll = ThreadLocalRandom.current().nextInt(total);

        if (roll < wCommon) return PurgeUpgradeRarity.COMMON;
        roll -= wCommon;
        if (roll < wUncommon) return PurgeUpgradeRarity.UNCOMMON;
        roll -= wUncommon;
        if (roll < wRare) return PurgeUpgradeRarity.RARE;
        roll -= wRare;
        if (roll < wEpic) return PurgeUpgradeRarity.EPIC;
        return PurgeUpgradeRarity.LEGENDARY;
    }

    public void applyUpgrade(PurgeSession session, UUID playerId, PurgeUpgradeOffer offer,
                              Ref<EntityStore> ref, Store<EntityStore> store) {
        PurgeUpgradeState state = session.getUpgradeState(playerId);
        if (state == null) return;

        int value = offer.value();
        state.addValue(offer.type(), value);

        switch (offer.type()) {
            case HP -> applyHp(ref, store, state.getAccumulated(PurgeUpgradeType.HP));
            case AMMO -> applyAmmo(session, playerId, ref, store, state.getAccumulated(PurgeUpgradeType.AMMO));
            case SPEED -> applySpeed(ref, store, state.getAccumulated(PurgeUpgradeType.SPEED));
            case LUCK -> {} // Read at rarity-roll time
        }
    }

    public void revertPlayerUpgrades(PurgeSession session, UUID playerId,
                                      Ref<EntityStore> ref, Store<EntityStore> store) {
        PurgeUpgradeState state = session.getUpgradeState(playerId);
        if (state == null) return;

        if (state.getAccumulated(PurgeUpgradeType.SPEED) > 0) {
            revertSpeed(ref, store);
        }
        if (state.getAccumulated(PurgeUpgradeType.HP) > 0) {
            revertHp(ref, store);
        }
    }

    // --- HP ---

    private void applyHp(Ref<EntityStore> ref, Store<EntityStore> store, int totalFlatHp) {
        if (ref == null || !ref.isValid()) return;
        EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap == null) return;

        int healthIndex = DefaultEntityStatTypes.getHealth();
        statMap.putModifier(healthIndex, PURGE_HP_UPGRADE_MODIFIER,
                new StaticModifier(Modifier.ModifierTarget.MAX,
                        StaticModifier.CalculationType.ADDITIVE, (float) totalFlatHp));
        statMap.update();
        statMap.maximizeStatValue(healthIndex);
        statMap.update();
    }

    private void revertHp(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ref == null || !ref.isValid()) return;
        EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap == null) return;

        int healthIndex = DefaultEntityStatTypes.getHealth();
        statMap.removeModifier(healthIndex, PURGE_HP_UPGRADE_MODIFIER);
        statMap.update();
    }

    // --- AMMO ---

    /**
     * Re-applies the accumulated ammo upgrade to the player's current weapon.
     * Call this after any operation that replaces the weapon ItemStack (revive, weapon switch).
     */
    public void reapplyAmmoUpgrade(PurgeSession session, UUID playerId,
                                    Ref<EntityStore> ref, Store<EntityStore> store) {
        PurgeUpgradeState state = session.getUpgradeState(playerId);
        if (state == null) return;
        int totalBonusAmmo = state.getAccumulated(PurgeUpgradeType.AMMO);
        if (totalBonusAmmo <= 0) return;
        applyAmmo(session, playerId, ref, store, totalBonusAmmo);
    }

    private void applyAmmo(PurgeSession session, UUID playerId,
                            Ref<EntityStore> ref, Store<EntityStore> store, int totalBonusAmmo) {
        if (ref == null || !ref.isValid()) return;
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        Inventory inventory = player.getInventory();
        if (inventory == null || inventory.getHotbar() == null) return;

        ItemStack weapon = inventory.getHotbar().getItemStack(SLOT_WEAPON);
        if (weapon == null || weapon.isEmpty()) return;

        // Use the base weapon ID from session state for the GunRegistry lookup.
        // getItemId() may return a skinned variant (e.g. "AK47_Asimov") that isn't in the registry.
        PurgeSessionPlayerState ps = session.getPlayerState(playerId);
        String baseWeaponId = (ps != null && ps.getCurrentWeaponId() != null)
                ? ps.getCurrentWeaponId()
                : weapon.getItemId();
        Integer defaultMax = com.thescar.hygunsplugin.GunRegistry.getDefaultMaxAmmo(baseWeaponId);
        if (defaultMax == null) return;

        int newMax = defaultMax + totalBonusAmmo;

        // Update Hyguns custom data on the weapon ItemStack
        weapon = com.thescar.hygunsplugin.ItemStackUtils.setCustomInt(weapon, "Hyguns_MaxAmmo", newMax);
        // Also fill magazine to the new max
        weapon = com.thescar.hygunsplugin.ItemStackUtils.setCustomInt(weapon, "Hyguns_Ammo", newMax);

        inventory.getHotbar().setItemStackForSlot(SLOT_WEAPON, weapon, false);
    }

    // --- SPEED ---

    private void applySpeed(Ref<EntityStore> ref, Store<EntityStore> store, int totalSpeedPercent) {
        if (ref == null || !ref.isValid()) return;
        MovementManager movementManager = store.getComponent(ref, MovementManager.getComponentType());
        if (movementManager == null) return;

        movementManager.refreshDefaultSettings(ref, store);
        movementManager.applyDefaultSettings();
        MovementSettings settings = movementManager.getSettings();
        if (settings == null) return;

        float multiplier = 1.0f + totalSpeedPercent / 100.0f;
        settings.maxSpeedMultiplier *= multiplier;
        settings.forwardRunSpeedMultiplier *= multiplier;
        settings.backwardRunSpeedMultiplier *= multiplier;
        settings.strafeRunSpeedMultiplier *= multiplier;
        settings.forwardSprintSpeedMultiplier *= multiplier;

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef != null) {
            var packetHandler = playerRef.getPacketHandler();
            if (packetHandler != null) {
                movementManager.update(packetHandler);
            }
        }
    }

    private void revertSpeed(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ref == null || !ref.isValid()) return;
        MovementManager movementManager = store.getComponent(ref, MovementManager.getComponentType());
        if (movementManager == null) return;

        movementManager.refreshDefaultSettings(ref, store);
        movementManager.applyDefaultSettings();

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef != null) {
            var packetHandler = playerRef.getPacketHandler();
            if (packetHandler != null) {
                movementManager.update(packetHandler);
            }
        }
    }
}
