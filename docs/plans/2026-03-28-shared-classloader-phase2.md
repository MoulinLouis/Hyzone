# Plan: Shared Classloader Phase 2 — Consolidation

**Date:** 2026-03-28
**Status:** Implemented
**Depends on:** `2026-03-28-shared-classloader.md` (implemented and verified)
**Goal:** Exploit the shared classloader to eliminate redundant work, simplify non-core plugins, and clean up dead code

## Overview

Phase 1 established bridge classloader delegation. All plugins now share one copy of hyvexa-core static fields. This phase consolidates the codebase to take full advantage: centralizing disconnect cleanup, removing dead initialization code, and tuning the shared connection pool.

## Step 1: Centralize shared-store disconnect cleanup

### Problem

Every plugin registers its own `PlayerDisconnectEvent` handler and redundantly evicts from shared stores. Current duplication:

| Store | Evicted by |
|-------|-----------|
| VexaStore | Parkour, Hub, Wardrobe, Purge, RunOrFall, Ascend (all 6) |
| DiscordLinkStore | Parkour, Hub, Purge, Ascend (4/6) |
| CosmeticStore | Parkour, Wardrobe (2/6) |
| FeatherStore | Parkour, Wardrobe (2/6) |
| PurgeSkinStore | Purge, Wardrobe (2/6) |
| MultiHudBridge | Hub, Purge, RunOrFall, Ascend (4/6) |
| CosmeticManager | Wardrobe (1/6) |

With shared classloader, the first eviction wins and the rest are harmless no-ops — but it's wasted work, error-prone (some plugins forget some stores), and makes non-core plugins unnecessarily coupled to core internals.

### Solution

Create a `SharedStoreCleanup` utility in hyvexa-core that registers a **single global** `PlayerDisconnectEvent` handler in `HyvexaPlugin.setup()`. It evicts from all shared stores. Non-core plugins remove their shared-store eviction calls and keep only local cleanup.

### Implementation

#### 1a. Create `SharedStoreCleanup` in hyvexa-core

```java
package io.hyvexa.common.util;

// in hyvexa-core/src/main/java/io/hyvexa/common/util/SharedStoreCleanup.java

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.common.skin.PurgeSkinStore;
import io.hyvexa.core.cosmetic.CosmeticManager;
import io.hyvexa.core.cosmetic.CosmeticStore;
import io.hyvexa.core.discord.DiscordLinkStore;
import io.hyvexa.core.economy.FeatherStore;
import io.hyvexa.core.economy.VexaStore;

import java.util.UUID;

/**
 * Centralized disconnect cleanup for all shared stores.
 * Called once per disconnect from the core plugin.
 */
public final class SharedStoreCleanup {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private SharedStoreCleanup() {}

    public static void evictPlayer(UUID playerId) {
        if (playerId == null) return;
        evictSafe(() -> VexaStore.get().evictPlayer(playerId), "VexaStore");
        evictSafe(() -> FeatherStore.get().evictPlayer(playerId), "FeatherStore");
        evictSafe(() -> CosmeticStore.get().evictPlayer(playerId), "CosmeticStore");
        evictSafe(() -> DiscordLinkStore.get().evictPlayer(playerId), "DiscordLinkStore");
        evictSafe(() -> CosmeticManager.get().cleanupOnDisconnect(playerId), "CosmeticManager");
        evictSafe(() -> MultiHudBridge.evictPlayer(playerId), "MultiHudBridge");
        if (PurgeSkinStore.isInitialized()) {
            evictSafe(() -> PurgeSkinStore.get().evictPlayer(playerId), "PurgeSkinStore");
        }
    }

    private static void evictSafe(Runnable action, String name) {
        try {
            action.run();
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Disconnect cleanup: " + name);
        }
    }
}
```

#### 1b. Register in HyvexaPlugin (core)

In `HyvexaPlugin.setup()`, register the global disconnect handler **before** the `eventRouter.registerAll(this.getEventRegistry())` call. This ensures shared stores are evicted before ParkourEventRouter's handler runs (handlers fire in registration order):

```java
// Register BEFORE eventRouter.registerAll() so shared stores are evicted first
this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
    PlayerRef playerRef = event.getPlayerRef();
    if (playerRef == null) return;
    UUID playerId = playerRef.getUuid();
    if (playerId == null) return;
    SharedStoreCleanup.evictPlayer(playerId);
});
// ... then later:
this.eventRouter.registerAll(this.getEventRegistry());
```

This runs for EVERY disconnect regardless of which world the player is in.

#### 1c. Remove shared-store evictions from non-core plugins

For each non-core plugin's disconnect handler, remove calls to shared stores:

**HyvexaHubPlugin** — remove:
- `MultiHudBridge.evictPlayer(playerId)`
- `vexaStore.evictPlayer(playerId)`
- `discordLinkStore.evictPlayer(playerId)`

Keep: `hubHudLifecycles.remove(playerId)` (local)

**WardrobePlugin** — remove from PlayerCleanupHelper:
- `cosmeticManager.cleanupOnDisconnect(id)` → now in SharedStoreCleanup
- `cosmeticStore.evictPlayer(id)`
- `vexaStore.evictPlayer(id)`
- `featherStore.evictPlayer(id)`
- `purgeSkinStore.evictPlayer(id)`

Keep: `wardrobeShopTab.evictPlayer(id)`, `shopConfigTab.evictPlayer(id)`, `effectsShopTab.evictPlayer(id)`, `purgeSkinShopTab.evictPlayer(id)` (all local)

**HyvexaPurgePlugin** — remove from PlayerCleanupHelper:
- `VexaStore.get().evictPlayer(id)`
- `discordLinkStore.evictPlayer(id)`
- `purgeSkinStore.evictPlayer(id)`
- `MultiHudBridge.evictPlayer(id)`

Keep: `sessionManager.cleanupPlayer(id)`, `partyManager.cleanupPlayer(id)`, `hudManager.removePlayer(id)`, `playerStore.evict(id)`, `scrapStore.evictPlayer(id)`, `weaponUpgradeStore.evictPlayer(id)`, `weaponXpStore.evictPlayer(id)`, `classStore.evictPlayer(id)`, `missionStore.evictPlayer(id)` (all local)

**HyvexaRunOrFallPlugin** — remove:
- `MultiHudBridge.evictPlayer(playerId)`
- `VexaStore.get().evictPlayer(playerId)`

Keep: `gameManager.handleDisconnect(id)`, `cleanupHudForPlayer(id)`, `hiddenHudPlayers.remove(id)`, `RunOrFallAdminPage.clearSelection(id)`, `RunOrFallMusicPage.clearPlayer(id)`, `featherBridge.evictPlayer(id)`, `runOrFallCommand.clearSelection(id)` (all local)

**ParkourAscendPlugin** — remove:
- `MultiHudBridge.evictPlayer(playerId)`
- `VexaStore.get().evictPlayer(playerId)`
- `discordLinkStore.evictPlayer(playerId)`

Keep: all local cleanup (playersInAscendWorld, cleanupAscendState, eventHandler, playerStore, minePlayerStore, blockDamageTracker, blockVisualHelper, mineBreakSystem, mineDamageSystem, mineAchievementTracker, mineGateChecker, eggRoulette)

**ParkourEventRouter** (core) — remove:
- `vexaStore.evictPlayer(playerId)`
- `featherStore.evictPlayer(playerId)`
- `discordLinkStore.evictPlayer(playerId)`
- `cosmeticStore.evictPlayer(playerId)`

Keep: `duelTracker.handleDisconnect(id)`, `cleanupManager.handleDisconnect(ref)`, `medalStore.evictPlayer(id)`, `trailManager.stopTrail(id)`, `petManager.despawnPet(id)`, `voteManager.unregisterPlayer(id)`, `voteStore.evictPlayer(id)`, `removeHudPlayer(id)`, analytics logging (all local/core-specific)

**Note on analytics logging:** The `player_leave` analytics event and `updatePlayerTimestamps` call in ParkourEventRouter should stay — they're not simple evictions, they're data writes that happen to be in the disconnect handler. Only the core should log session analytics anyway.

### Verification

After this step:
- `grep -r "evictPlayer" --include="*.java" | grep -E "VexaStore|FeatherStore|CosmeticStore|DiscordLinkStore"` should only match `SharedStoreCleanup.java`
- `grep -r "MultiHudBridge.evictPlayer" --include="*.java"` should only match `SharedStoreCleanup.java`
- Boot server, connect + disconnect, verify no "not yet initialized" errors in logs

---

## Step 2: Clean up HyvexaPurgePlugin initialization

### Problem

HyvexaPurgePlugin still has the pre-shared-classloader initialization pattern:
- `DatabaseManager.get().initialize()` — redundant, core already initialized it
- `VexaStore.get().initialize()` — redundant
- `DiscordLinkStore.get(); discordLinkStore.initialize()` — redundant
- `AnalyticsStore.get().initialize()` — redundant

Only the local Purge stores actually need initialization.

### Implementation

Replace the two `StoreInitializer.initialize(...)` blocks in `HyvexaPurgePlugin.setup()`:

**Before:**
```java
// Block 1: database init
StoreInitializer.initialize(LOGGER,
        () -> DatabaseManager.get().initialize()
);

PurgeDatabaseSetup.ensureTables();

// Store creation (6 stores — these lines stay unchanged)
var db = DatabaseManager.get();
scrapStore = PurgeScrapStore.createAndRegister(db);
weaponUpgradeStore = PurgeWeaponUpgradeStore.createAndRegister(db, scrapStore);
classStore = PurgeClassStore.createAndRegister(db, scrapStore);
weaponXpStore = WeaponXpStore.createAndRegister(db);
missionStore = PurgeMissionStore.createAndRegister(db);
playerStore = PurgePlayerStore.createAndRegister(db);

// Block 2: store initialization (shared + local mixed together)
StoreInitializer.initialize(LOGGER,
        () -> VexaStore.get().initialize(),
        () -> { discordLinkStore = DiscordLinkStore.get(); discordLinkStore.initialize(); },
        () -> AnalyticsStore.get().initialize(),
        () -> scrapStore.initialize(),
        () -> weaponUpgradeStore.initialize(),
        () -> { purgeSkinStore = PurgeSkinStore.createAndRegister(db); purgeSkinStore.initialize(); },
        () -> weaponXpStore.initialize(),
        () -> classStore.initialize(),
        () -> missionStore.initialize()
);
```

**After:**
```java
// No database init — core already initialized it

PurgeDatabaseSetup.ensureTables();

// Store creation (6 stores — unchanged)
var db = DatabaseManager.get();
scrapStore = PurgeScrapStore.createAndRegister(db);
weaponUpgradeStore = PurgeWeaponUpgradeStore.createAndRegister(db, scrapStore);
classStore = PurgeClassStore.createAndRegister(db, scrapStore);
weaponXpStore = WeaponXpStore.createAndRegister(db);
missionStore = PurgeMissionStore.createAndRegister(db);
playerStore = PurgePlayerStore.createAndRegister(db);

// Get shared store references (no initialize — core did that)
discordLinkStore = DiscordLinkStore.get();

// PurgeSkinStore: created by Purge (not shared), so createAndRegister outside StoreInitializer
// for immediate reference, but initialize() stays wrapped for error resilience
purgeSkinStore = PurgeSkinStore.createAndRegister(db);

// Initialize local Purge stores only (all wrapped for error resilience)
StoreInitializer.initialize(LOGGER,
        () -> purgeSkinStore.initialize(),
        () -> scrapStore.initialize(),
        () -> weaponUpgradeStore.initialize(),
        () -> weaponXpStore.initialize(),
        () -> classStore.initialize(),
        () -> missionStore.initialize()
);
```

Key changes:
- Remove `DatabaseManager.get().initialize()` block entirely — core handles it
- Remove `VexaStore.get().initialize()`, `DiscordLinkStore.initialize()`, `AnalyticsStore.get().initialize()` — core handles these
- `PurgeSkinStore.createAndRegister()` pulled out of StoreInitializer so it can be referenced immediately (used later in setup), but `purgeSkinStore.initialize()` stays inside StoreInitializer for error resilience
- All 6 store creation lines between the two blocks stay unchanged
- Local Purge stores still use StoreInitializer for error resilience

### Also: Remove DatabaseManager.shutdown() from Purge

Check if HyvexaPurgePlugin.shutdown() calls `DatabaseManager.get().shutdown()` — if so, remove it (same pattern as the other plugins).

---

## Step 3: Delete StoreInitializer if unused

### Check

After Step 2, verify remaining usages:
```bash
grep -r "StoreInitializer" --include="*.java" -l
```

If only `HyvexaPurgePlugin.java` and `StoreInitializer.java` remain, and Purge still uses it for local store init, **keep it**. It's a useful error-resilience utility.

If nothing uses it anymore, delete `StoreInitializer.java`.

---

## Step 4: Connection pool considerations

### Current settings (DatabaseManager.java)
```
maxPoolSize = 10
minIdle = 2
idleTimeout = 300000 (5 min)
connectionTimeout = 10000 (10 sec)
maxLifetime = 1800000 (30 min)
```

### Assessment

Before: 6 pools × 10 = 60 potential connections. Now: 1 pool × 10 = 10.

The current settings are likely fine. 10 connections serving all plugins simultaneously should handle the load for a single-server setup. However:

- **Monitor under load**: If you see `connectionTimeout` warnings in logs, bump `maxPoolSize` to 15-20
- **No code change needed now** — just an awareness item

---

## Step 5: Remove unused imports and dead code

After steps 1-3, clean up:
- Remove `VexaStore`, `FeatherStore`, `CosmeticStore`, `DiscordLinkStore` imports from non-core plugins that no longer reference them
- Remove `MultiHudBridge` imports from plugins that no longer call it directly
- Remove field declarations for shared stores that are no longer referenced (e.g. `private VexaStore vexaStore` in Hub if only used for eviction)
- If `PlayerCleanupHelper` usage shrinks to only local stores, consider inlining the remaining calls

---

## What we're NOT doing (and why)

### InteractionBridge classes (ParkourInteractionBridge, etc.)
These 4 bridges exist because **Hytale's codec system requires no-arg constructors** for interaction handlers. This is a Hytale engine limitation, not a classloader issue. The shared classloader doesn't change anything here — these bridges would be needed even with a single JAR.

### CurrencyBridge / GameModeBridge / WardrobeBridge
These serve real architectural purposes (loose coupling between modules). They work correctly with shared classloader and don't need simplification.

### getOrCreate() audit
The 97 `getOrCreate` calls in the codebase are almost entirely `getOrCreatePlayer()` / `getOrCreateMapProgress()` patterns in game stores — data structure creation, not classloader compensation. `SharedInstance.getOrCreate()` is only used for PurgeSkinStore (genuinely optional). No cleanup needed.

---

## Files to modify

### New files
- `hyvexa-core/src/main/java/io/hyvexa/common/util/SharedStoreCleanup.java`

### Modified files (Step 1 — disconnect centralization)
- `hyvexa-parkour/src/main/java/io/hyvexa/HyvexaPlugin.java` — add global disconnect handler
- `hyvexa-parkour/src/main/java/io/hyvexa/ParkourEventRouter.java` — remove shared store evictions
- `hyvexa-hub/src/main/java/io/hyvexa/hub/HyvexaHubPlugin.java` — remove shared store evictions
- `hyvexa-wardrobe/src/main/java/io/hyvexa/wardrobe/WardrobePlugin.java` — remove shared store evictions
- `hyvexa-purge/src/main/java/io/hyvexa/purge/HyvexaPurgePlugin.java` — remove shared store evictions
- `hyvexa-runorfall/src/main/java/io/hyvexa/runorfall/HyvexaRunOrFallPlugin.java` — remove shared store evictions
- `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ParkourAscendPlugin.java` — remove shared store evictions

### Modified files (Step 2 — Purge init cleanup)
- `hyvexa-purge/src/main/java/io/hyvexa/purge/HyvexaPurgePlugin.java` — remove redundant shared store init

### Documentation
- `docs/CODE_PATTERNS.md` — document SharedStoreCleanup pattern

## Load order guarantee

The centralized handler in HyvexaPlugin runs for ALL disconnect events because Hytale's event system is global (not world-scoped). The core plugin loads first (manifest Dependencies guarantee this), so its handler is registered first. Event handlers fire in registration order, so shared stores are evicted before any non-core plugin's handler runs. This means non-core plugins can safely assume shared stores are already cleaned up.
