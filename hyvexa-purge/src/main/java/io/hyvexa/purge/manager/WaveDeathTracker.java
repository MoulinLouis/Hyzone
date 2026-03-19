package io.hyvexa.purge.manager;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.WorldConstants;
import io.hyvexa.purge.HyvexaPurgePlugin;
import io.hyvexa.purge.data.PurgeScrapStore;
import io.hyvexa.purge.data.PurgeSession;
import io.hyvexa.purge.data.PurgeSessionPlayerState;
import io.hyvexa.purge.data.PurgeVariantConfig;
import io.hyvexa.purge.hud.PurgeHud;
import io.hyvexa.purge.hud.PurgeHudManager;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.hypixel.hytale.server.core.entity.entities.Player;

public class WaveDeathTracker {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final PurgeVariantConfigManager variantConfigManager;
    private final PurgeHudManager hudManager;
    private PurgeManagerRegistry registry;

    public WaveDeathTracker(PurgeVariantConfigManager variantConfigManager, PurgeHudManager hudManager) {
        this.variantConfigManager = variantConfigManager;
        this.hudManager = hudManager;
    }

    void initRegistry(PurgeManagerRegistry registry) {
        this.registry = registry;
    }

    public void checkZombieDeaths(PurgeSession session, Store<EntityStore> store) {
        Set<Ref<EntityStore>> dead = null;
        int healthIndex = DefaultEntityStatTypes.getHealth();
        for (Ref<EntityStore> ref : session.getAliveZombies()) {
            if (ref == null || !ref.isValid()) {
                if (dead == null) {
                    dead = new LinkedHashSet<>();
                }
                dead.add(ref);
            }
        }
        for (Ref<EntityStore> ref : session.getPendingZombieDeathsSnapshot()) {
            if (ref == null) {
                continue;
            }
            if (!session.getAliveZombies().contains(ref)) {
                session.clearZombiePendingDeath(ref);
                continue;
            }
            if (!ref.isValid()) {
                if (dead == null) {
                    dead = new LinkedHashSet<>();
                }
                dead.add(ref);
                session.clearZombiePendingDeath(ref);
                continue;
            }
            try {
                EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
                if (statMap == null) {
                    continue;
                }
                EntityStatValue health = statMap.get(healthIndex);
                if (health != null && health.get() <= 0f) {
                    if (dead == null) {
                        dead = new LinkedHashSet<>();
                    }
                    dead.add(ref);
                    session.clearZombiePendingDeath(ref);
                }
            } catch (Exception e) {
                LOGGER.atFine().log("Failed to read zombie health for pending death check: " + e.getMessage());
            }
        }
        if (dead == null || dead.isEmpty()) {
            return;
        }
        // Collect scrap rewards per variant before removeAliveZombie clears variant keys
        int totalScrapPerKill = 0;
        for (Ref<EntityStore> deadRef : dead) {
            String variantKey = session.getZombieVariantKey(deadRef);
            if (variantKey != null) {
                PurgeVariantConfig vc = variantConfigManager.getVariant(variantKey);
                if (vc != null) {
                    totalScrapPerKill += vc.scrapReward();
                }
            }
        }

        for (Ref<EntityStore> deadRef : dead) {
            if (deadRef != null && deadRef.isValid()) {
                try {
                    Nameplate nameplate = store.getComponent(deadRef, Nameplate.getComponentType());
                    if (nameplate != null) {
                        nameplate.setText("");
                    }
                } catch (Exception e) {
                    LOGGER.atFine().log("Failed to clear dead zombie nameplate: " + e.getMessage());
                }
            }
            session.removeAliveZombie(deadRef);
        }
        // Shared kills: all alive connected players get +1 per zombie death
        for (int i = 0; i < dead.size(); i++) {
            session.forEachAliveConnectedPlayerState(PurgeSessionPlayerState::incrementKills);
        }

        // Scrap reward per zombie kill based on variant config + weapon XP bonus
        {
            int baseScrapReward = totalScrapPerKill;
            int deadCount = dead.size();
            session.forEachAliveConnectedPlayerState(ps -> {
                UUID playerId = ps.getPlayerId();
                int bonusScrap = registry.getWeaponXpManager().getBonusScrap(playerId, ps.getCurrentWeaponId()) * deadCount;
                int reward = baseScrapReward + bonusScrap;
                if (reward > 0) {
                    PurgeScrapStore.getInstance().addScrap(playerId, reward);
                    PurgeHud hud = hudManager.getHud(playerId);
                    if (hud != null) {
                        hud.updateScrap(PurgeScrapStore.getInstance().getScrap(playerId));
                    }
                }
            });
        }

        // Lootbox drop: configurable % chance per dead zombie per alive player
        HyvexaPurgePlugin plugin = HyvexaPurgePlugin.getInstance();
        if (plugin != null) {
            double dropChance = plugin.getWeaponConfigManager().getLootboxDropChance();
            World world = getPurgeWorld();
            if (world != null && dropChance > 0) {
                for (int i = 0; i < dead.size(); i++) {
                    session.forEachAliveConnectedPlayerState(ps -> {
                        if (ThreadLocalRandom.current().nextDouble() < dropChance) {
                            Ref<EntityStore> pRef = ps.getPlayerRef();
                            if (pRef != null && pRef.isValid()) {
                                world.execute(() -> {
                                    try {
                                        Store<EntityStore> playerStore = pRef.getStore();
                                        Player player = playerStore.getComponent(pRef, Player.getComponentType());
                                        if (player != null) {
                                            plugin.grantLootbox(player, 1);
                                        }
                                    } catch (Exception e) {
                                        LOGGER.atFine().log("Failed to grant lootbox: " + e.getMessage());
                                    }
                                });
                            }
                        }
                    });
                }
            }
        }
    }

    private World getPurgeWorld() {
        try {
            return Universe.get().getWorld(WorldConstants.WORLD_PURGE);
        } catch (Exception e) {
            return null;
        }
    }
}
