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
import io.hyvexa.purge.data.WeaponXpStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class PurgeUpgradeManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String PURGE_HP_UPGRADE_MODIFIER = "purge_upgrade_hp";
    private static final short SLOT_WEAPON = 0;

    private final WeaponXpStore weaponXpStore;

    public PurgeUpgradeManager(WeaponXpStore weaponXpStore) {
        this.weaponXpStore = weaponXpStore;
    }

    public List<PurgeUpgradeOffer> selectRandomOffers(int count, int luck, PurgeUpgradeState state) {
        List<PurgeUpgradeType> pool = new ArrayList<>(List.of(PurgeUpgradeType.values()));
        if (state != null) {
            pool.removeIf(state::isAtCap);
        }
        Collections.shuffle(pool);
        List<PurgeUpgradeType> picked = pool.subList(0, Math.min(count, pool.size()));

        List<PurgeUpgradeOffer> offers = new ArrayList<>(picked.size());
        for (PurgeUpgradeType type : picked) {
            PurgeUpgradeRarity rarity = rollRarity(luck);
            int rawValue = type.getBaseValue() * rarity.getMultiplier();
            int remaining = state != null ? state.getRemaining(type) : type.getMaxAccumulated();
            int value = Math.min(rawValue, remaining);
            if (value > 0) {
                offers.add(new PurgeUpgradeOffer(type, rarity, value));
            }
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
                              Ref<EntityStore> ref, Store<EntityStore> store, Player player) {
        PurgeUpgradeState state = session.getUpgradeState(playerId);
        if (state == null) return;

        int value = offer.value();
        state.addValue(offer.type(), value);

        switch (offer.type()) {
            case HP -> applyHp(ref, store, state.getAccumulated(PurgeUpgradeType.HP));
            case AMMO -> {
                PurgeSessionPlayerState ps = session.getPlayerState(playerId);
                String weaponId = ps != null ? ps.getCurrentWeaponId() : null;
                applyAmmoToPlayer(player, state.getAccumulated(PurgeUpgradeType.AMMO), playerId, weaponId);
            }
            case SPEED -> {
                PurgeSessionPlayerState speedPs = session.getPlayerState(playerId);
                float classFactor = speedPs != null ? speedPs.getClassSpeedFactor() : 1.0f;
                applySpeed(ref, store, state.getAccumulated(PurgeUpgradeType.SPEED), classFactor);
            }
            case LUCK -> {} // Read at rarity-roll time
        }
    }

    /**
     * Applies combined upgrade + class speed modifiers. Used by PurgeClassManager
     * to apply class speed when no upgrades have been picked yet.
     */
    public void applySpeedForClass(PurgeSession session, UUID playerId,
                                    Ref<EntityStore> ref, Store<EntityStore> store) {
        PurgeUpgradeState upgradeState = session.getUpgradeState(playerId);
        int totalSpeed = upgradeState != null ? upgradeState.getAccumulated(PurgeUpgradeType.SPEED) : 0;
        PurgeSessionPlayerState ps = session.getPlayerState(playerId);
        float classFactor = ps != null ? ps.getClassSpeedFactor() : 1.0f;
        applySpeed(ref, store, totalSpeed, classFactor);
    }

    public void revertPlayerUpgrades(PurgeSession session, UUID playerId,
                                      Ref<EntityStore> ref, Store<EntityStore> store) {
        PurgeUpgradeState state = session.getUpgradeState(playerId);
        if (state == null) return;

        PurgeSessionPlayerState ps = session.getPlayerState(playerId);
        boolean hasClassSpeed = ps != null && ps.getClassSpeedFactor() != 1.0f;
        if (state.getAccumulated(PurgeUpgradeType.SPEED) > 0 || hasClassSpeed) {
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
    public void reapplyAmmoUpgrade(PurgeSession session, UUID playerId, Player player) {
        PurgeUpgradeState state = session.getUpgradeState(playerId);
        if (state == null) return;
        int totalBonusAmmo = state.getAccumulated(PurgeUpgradeType.AMMO);
        PurgeSessionPlayerState ps = session.getPlayerState(playerId);
        String weaponId = ps != null ? ps.getCurrentWeaponId() : null;
        applyAmmoToPlayer(player, totalBonusAmmo, playerId, weaponId);
    }

    /**
     * Sets max ammo on the weapon in slot 0, same mechanism as /setammo command.
     * Applies weapon XP ammo multiplier to base magazine before adding upgrade bonus.
     */
    private void applyAmmoToPlayer(Player player, int bonusAmmo, UUID playerId, String weaponId) {
        if (player == null) return;

        Inventory inventory = player.getInventory();
        if (inventory == null || inventory.getHotbar() == null) return;

        ItemStack weapon = inventory.getHotbar().getItemStack(SLOT_WEAPON);
        if (weapon == null || weapon.isEmpty()) return;

        String itemId = weapon.getItemId();
        Integer defaultMax = com.thescar.hygunsplugin.core.registry.GunRegistry.getDefaultMaxAmmo(itemId);
        if (defaultMax == null) return;

        // Apply weapon XP ammo multiplier to base magazine size
        int effectiveBase = defaultMax;
        if (playerId != null && weaponId != null) {
            int xpLevel = weaponXpStore.getXpData(playerId, weaponId)[1];
            effectiveBase = (int) (defaultMax * (1.0 + 0.05 * xpLevel));
        }
        int newMax = effectiveBase + bonusAmmo;

        weapon = com.thescar.hygunsplugin.core.util.ItemStackUtils.setCustomInt(weapon, "Hyguns_MaxAmmo", newMax);
        weapon = com.thescar.hygunsplugin.core.util.ItemStackUtils.setCustomInt(weapon, "Hyguns_Ammo", newMax);

        inventory.getHotbar().setItemStackForSlot(SLOT_WEAPON, weapon, false);
    }

    // --- SPEED ---

    private void applySpeed(Ref<EntityStore> ref, Store<EntityStore> store,
                            int totalSpeedPercent, float classSpeedFactor) {
        if (ref == null || !ref.isValid()) return;
        MovementManager movementManager = store.getComponent(ref, MovementManager.getComponentType());
        if (movementManager == null) return;

        movementManager.refreshDefaultSettings(ref, store);
        movementManager.applyDefaultSettings();
        MovementSettings settings = movementManager.getSettings();
        if (settings == null) return;

        float multiplier = (1.0f + totalSpeedPercent / 100.0f) * classSpeedFactor;
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
