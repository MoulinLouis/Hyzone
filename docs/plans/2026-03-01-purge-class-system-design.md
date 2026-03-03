# Purge Class System - Implementation Plan

## Overview

Add a class system to Purge mode where players unlock classes with scraps. Each class provides passive stat boosts and a unique perk that alters gameplay. Classes are a persistent loadout — players select one from their unlocked classes and it stays active across sessions until changed.

---

## Class Definitions

### Scavenger

| Aspect | Detail |
|--------|--------|
| **Stat boost** | +30% scrap earned per wave |
| **Unique perk** | Kill streak scrap bonus: earn +5 bonus scrap per kill while on a streak (kills within 5s of each other) |
| **Unlock cost** | 500 scrap |
| **Identity** | The farmer. Accelerates scrap income for faster weapon upgrades |

**Implementation notes:**
- Scrap multiplier applied in `PurgeSessionManager.persistResults()` where wave scrap is awarded
- Kill streak bonus uses existing `killStreak` / `lastKillTimeMs` tracking in `PurgeSessionPlayerState`
- Bonus scrap accumulated in session state, awarded at session end alongside wave scrap

### Tank

| Aspect | Detail |
|--------|--------|
| **Stat boost** | +40% max HP, -15% movement speed |
| **Unique perk** | 20% damage reduction from all zombie hits |
| **Unlock cost** | 500 scrap |
| **Identity** | The wall. Survives longer at the cost of mobility |

**Implementation notes:**
- HP boost via `EntityStatMap` modifier (same pattern as `PurgeUpgradeManager.applyHp()`)
- Speed penalty via `MovementSettings` modifier (same pattern as `PurgeUpgradeManager.applySpeed()`)
- Damage reduction applied in `PurgeDamageModifierSystem.handle()` when target is a Tank player
- Class modifiers stack additively with upgrade modifiers (applied as separate modifier IDs)

### Assault

| Aspect | Detail |
|--------|--------|
| **Stat boost** | +20% weapon damage, +10% movement speed |
| **Unique perk** | Kill streak damage ramp: +5% damage per active streak level (up to +45% at streak 9). Resets when streak breaks |
| **Unlock cost** | 750 scrap |
| **Identity** | The glass cannon. High damage output, rewards aggressive play |

**Implementation notes:**
- Base damage multiplier applied in `PurgeDamageModifierSystem.handle()` — multiply final damage by 1.2
- Speed boost via `MovementSettings` modifier
- Streak damage uses existing `killStreak` field: `damageMultiplier = 1.2 + (killStreak * 0.05)`
- Streak resets already tracked (>5s since last kill in `PurgeSessionPlayerState`)

### Medic

| Aspect | Detail |
|--------|--------|
| **Stat boost** | Passive HP regeneration: +2 HP every 3 seconds |
| **Unique perk** | Heal on kill: restore 5 HP per zombie killed |
| **Unlock cost** | 750 scrap |
| **Identity** | The survivor. Sustains through attrition in long runs |

**Implementation notes:**
- Regen via scheduled task (runs on wave tick at 200ms, counter-based to fire every 3s)
- Heal on kill triggered in `PurgeDamageModifierSystem` or `PurgeWaveManager` when zombie death is detected
- Healing capped at current max HP (read from `EntityStatMap`)
- Uses `EntityStatMap` health stat to apply healing

---

## Database Schema

### New table: `purge_player_classes`

```sql
CREATE TABLE IF NOT EXISTS purge_player_classes (
    uuid         VARCHAR(36) NOT NULL,
    class_id     VARCHAR(32) NOT NULL,
    PRIMARY KEY (uuid, class_id)
);
```

Stores which classes a player has unlocked. One row per unlocked class.

### New table: `purge_player_selected_class`

```sql
CREATE TABLE IF NOT EXISTS purge_player_selected_class (
    uuid             VARCHAR(36) NOT NULL PRIMARY KEY,
    selected_class   VARCHAR(32) DEFAULT NULL
);
```

Stores the player's currently active class. `NULL` = no class selected.

---

## New Files

### 1. `PurgeClass.java` (enum)

**Path:** `hyvexa-purge/src/main/java/io/hyvexa/purge/data/PurgeClass.java`

```java
public enum PurgeClass {
    SCAVENGER("Scavenger", 500,  "#22c55e"),
    TANK     ("Tank",      500,  "#3b82f6"),
    ASSAULT  ("Assault",   750,  "#ef4444"),
    MEDIC    ("Medic",     750,  "#f59e0b");

    private final String displayName;
    private final long unlockCost;
    private final String color;
    // constructor, getters
}
```

### 2. `PurgeClassStore.java` (persistence)

**Path:** `hyvexa-purge/src/main/java/io/hyvexa/purge/data/PurgeClassStore.java`

Singleton store following existing patterns (`PurgeScrapStore`, `PurgeWeaponUpgradeStore`):
- In-memory cache: `Map<UUID, Set<PurgeClass>>` for unlocked classes
- In-memory cache: `Map<UUID, PurgeClass>` for selected class
- Lazy-load on first access per player
- Methods: `getUnlockedClasses(uuid)`, `getSelectedClass(uuid)`, `unlockClass(uuid, class)`, `selectClass(uuid, class)`, `isUnlocked(uuid, class)`
- Purchase method: checks scrap balance via `PurgeScrapStore`, deducts, persists unlock

### 3. `PurgeClassManager.java` (runtime effects)

**Path:** `hyvexa-purge/src/main/java/io/hyvexa/purge/manager/PurgeClassManager.java`

Manages applying/reverting class effects during Purge sessions:

- `applyClassEffects(player, playerState)` — called on session start
  - Tank: add HP modifier + speed penalty
  - Assault: add speed boost
  - Medic: start regen tick
- `revertClassEffects(player, playerState)` — called on session end
  - Remove all class-applied modifiers
  - Cancel regen task
- `getScrapMultiplier(playerState)` — returns 1.3 for Scavenger, 1.0 otherwise
- `getDamageMultiplier(playerState)` — returns Assault multiplier (base + streak bonus)
- `getDamageReduction(playerState)` — returns 0.8 for Tank, 1.0 otherwise
- `onZombieKill(player, playerState)` — handles Medic heal-on-kill + Scavenger streak scrap

### 4. `PurgeClassCommand.java` (command)

**Path:** `hyvexa-purge/src/main/java/io/hyvexa/purge/command/PurgeClassCommand.java`

Subcommand under `/purge class`:
- `/purge class` — open class selection UI
- `/purge class select <name>` — select a class (must be unlocked)
- `/purge class info <name>` — show class details in chat

---

## Integration Points (Existing File Changes)

### 1. `HyvexaPurgePlugin.java`
- Initialize `PurgeClassStore` and `PurgeClassManager` on startup
- Register `/purge class` subcommand
- Add getters for class manager/store

### 2. `PurgeSessionManager.java`
- On session start: call `classManager.applyClassEffects()` after granting loadout
- On session end: call `classManager.revertClassEffects()` before cleanup
- On scrap award: multiply by `classManager.getScrapMultiplier()`
- On zombie kill: call `classManager.onZombieKill()` for Scavenger bonus scrap + Medic heal

### 3. `PurgeDamageModifierSystem.java`
- When calculating zombie damage (player shooting zombie): multiply by `classManager.getDamageMultiplier()` for Assault
- When calculating player damage (zombie hitting player): multiply by `classManager.getDamageReduction()` for Tank

### 4. `PurgeWaveManager.java`
- On wave tick: run Medic regen check (counter-based, every 3s worth of ticks)
- On zombie death detection: trigger `classManager.onZombieKill()`

### 5. `PurgeDatabaseSetup.java`
- Add `CREATE TABLE` statements for `purge_player_classes` and `purge_player_selected_class`

### 6. `PurgeSessionPlayerState.java`
- Add field: `PurgeClass activeClass` (set from store on session start)
- Add field: `int bonusScrapFromClass` (accumulated kill streak scrap for Scavenger)

### 7. `PurgeHudManager.java`
- Show active class name/icon on run HUD (optional, can defer)

---

## Modifier IDs

To avoid conflicts with existing upgrade modifiers, class modifiers use distinct IDs:

| Modifier | ID | Existing pattern reference |
|----------|----|---------------------------|
| Class HP boost | `PURGE_CLASS_HP_MODIFIER` | `PURGE_HP_UPGRADE_MODIFIER` |
| Class speed modifier | `PURGE_CLASS_SPEED_MODIFIER` | Uses `MovementSettings` |
| Class damage multiplier | Applied in code (not a stat modifier) | `PurgeDamageModifierSystem` |
| Class damage reduction | Applied in code (not a stat modifier) | `PurgeDamageModifierSystem` |

---

## Implementation Order

### Step 1: Data layer
1. Create `PurgeClass` enum
2. Create `PurgeClassStore` with DB tables
3. Add table creation to `PurgeDatabaseSetup`
4. Add `activeClass` field to `PurgeSessionPlayerState`

### Step 2: Class effects engine
5. Create `PurgeClassManager` with apply/revert logic
6. Integrate into `PurgeSessionManager` (apply on start, revert on end)

### Step 3: Combat integration
7. Add damage multiplier (Assault) to `PurgeDamageModifierSystem`
8. Add damage reduction (Tank) to `PurgeDamageModifierSystem`
9. Add scrap multiplier (Scavenger) to session scrap award
10. Add kill event handling (Medic heal, Scavenger streak scrap)

### Step 4: Medic regen
11. Add regen tick to `PurgeWaveManager` wave tick loop

### Step 5: Commands + UI
12. Create `/purge class` command for selection and info
13. Add class unlock purchase flow (scrap check + deduct)

### Step 6: HUD (optional, can defer)
14. Show active class on Purge run HUD

---

## Balance Considerations

- **Scavenger** is the most impactful for long-term progression (faster scrap = faster weapon upgrades), making it a strong first purchase despite being cheaper
- **Tank's** speed penalty (-15%) is meaningful but not crippling — it stacks with Speed upgrades picked between waves, so Tank players can offset it
- **Assault's** streak damage ramp (up to +65% total at streak 9) is high-risk/high-reward — streak breaks reset it, encouraging aggressive positioning
- **Medic's** sustain (2 HP/3s + 5 HP/kill) matters most in late waves where zombie damage ramps up; early waves are easy enough that Medic's advantage is minimal
- All classes interact with existing upgrade picks — no class makes any upgrade type useless
- No class is mandatory for any wave threshold — all are viable solo and in parties

---

## What This Plan Does NOT Include

- Class-specific UI pages (class selection done via chat command for now)
- Class-specific visual effects or particles
- Class leveling or prestige
- Party role synergies (e.g., Medic healing teammates)
- Class-restricted weapons
