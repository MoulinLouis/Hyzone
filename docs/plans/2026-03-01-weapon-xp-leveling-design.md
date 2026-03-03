# Weapon XP Leveling System — Design Document

**Date:** 2026-03-01
**Module:** hyvexa-purge
**Status:** Draft

## Overview

A persistent, per-weapon XP leveling system for Purge mode. Each zombie kill earns XP for the equipped weapon, unlocking passive stat boosts as the weapon levels up. This is a **separate parallel track** from the existing Scrap-bought damage upgrades (levels 0–10), which remain unchanged.

## Design Goals

- Give players long-term per-weapon progression beyond Scrap upgrades
- Reward consistent weapon use with meaningful stat boosts
- Keep it simple — no choices, no trees, just play and earn

## XP & Leveling

| Property | Value |
|----------|-------|
| XP source | 1 XP per zombie kill with the weapon |
| Max level | 20 |
| XP per level | `25 * level` (Level 1 = 25 XP, Level 2 = 50, ..., Level 20 = 500) |
| Total XP to max | 5,250 kills |
| Sessions to max (est.) | ~35–100 sessions per weapon |

### XP Table

| Level | XP Required | Cumulative XP |
|-------|-------------|---------------|
| 1 | 25 | 25 |
| 2 | 50 | 75 |
| 3 | 75 | 150 |
| 4 | 100 | 250 |
| 5 | 125 | 375 |
| 6 | 150 | 525 |
| 7 | 175 | 700 |
| 8 | 200 | 900 |
| 9 | 225 | 1,125 |
| 10 | 250 | 1,375 |
| 11 | 275 | 1,650 |
| 12 | 300 | 1,950 |
| 13 | 325 | 2,275 |
| 14 | 350 | 2,625 |
| 15 | 375 | 3,000 |
| 16 | 400 | 3,400 |
| 17 | 425 | 3,825 |
| 18 | 450 | 4,275 |
| 19 | 475 | 4,750 |
| 20 | 500 | 5,250 |

### Level Calculation

```
xpForLevel(n) = 25 * n
cumulativeXp(n) = 25 * n * (n + 1) / 2
levelFromXp(xp) = floor((-1 + sqrt(1 + 8 * xp / 25)) / 2)
```

## Stat Boosts

All 3 boosts scale linearly per level. No selection — all apply passively.

| Stat | Per Level | At Level 20 | How It's Applied |
|------|-----------|-------------|------------------|
| **Damage bonus** | +1.5% | +30% | Multiplier on weapon base damage (after Scrap upgrade) |
| **Scrap on kill** | +0.5 | +10 per kill | Flat bonus scrap added per zombie kill |
| **Ammo capacity** | +5% | +100% (2x) | Extra rounds per magazine on equip/reload |

### Formulas

```
effectiveDamage = configDamage(weaponId, upgradeLevel) * (1.0 + 0.015 * xpLevel)
bonusScrap      = floor(0.5 * xpLevel)
effectiveAmmo   = baseAmmo * (1.0 + 0.05 * xpLevel)
```

- `configDamage` = existing weapon damage from `purge_weapon_levels` (Scrap upgrade system)
- Damage bonus is a post-multiplier — it stacks on top of whatever the Scrap upgrade gives
- Scrap bonus is per-kill, not per-session
- Ammo capacity rounds down to nearest integer

## Database

### New Table: `purge_weapon_xp`

```sql
CREATE TABLE IF NOT EXISTS purge_weapon_xp (
    uuid       VARCHAR(36)  NOT NULL,
    weapon_id  VARCHAR(32)  NOT NULL,
    xp         INT          NOT NULL DEFAULT 0,
    level      INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (uuid, weapon_id)
);
```

- `level` is stored (not recalculated) and updated only on level-up
- Follows existing Purge data pattern: in-memory cache + MySQL persistence
- Loaded on player join, evicted on disconnect

## Code Changes

### New Files

| File | Purpose |
|------|---------|
| `WeaponXpStore.java` | DB persistence + `ConcurrentHashMap` cache. CRUD for `purge_weapon_xp`. Loaded per-player on join, evicted on disconnect. |
| `WeaponXpManager.java` | XP grant logic, level-up detection, boost value computation. Exposes `addKillXp(uuid, weaponId)`, `getDamageMultiplier(uuid, weaponId)`, `getBonusScrap(uuid, weaponId)`, `getAmmoMultiplier(uuid, weaponId)`. |

### Modified Files

| File | Change |
|------|--------|
| `PurgeDamageModifierSystem.java` | On lethal zombie hit: call `WeaponXpManager.addKillXp()`. Apply damage multiplier from XP level when calculating weapon damage. |
| `HyvexaPurgePlugin.java` | Initialize `WeaponXpStore` and `WeaponXpManager`. Register in lifecycle (player join/disconnect). |
| Scrap reward logic | Add `WeaponXpManager.getBonusScrap()` to per-kill scrap grants. |
| Ammo system | Apply `WeaponXpManager.getAmmoMultiplier()` when setting magazine size on equip/reload. |

### Integration Flow

```
Zombie killed by player
        |
        v
PurgeDamageModifierSystem.handle()
    - detects lethal hit
    - identifies equipped weapon
        |
        +---> WeaponXpManager.addKillXp(uuid, weaponId)
        |         |
        |         +---> WeaponXpStore.incrementXp(uuid, weaponId)
        |         |         - update in-memory xp
        |         |         - UPDATE purge_weapon_xp SET xp = xp + 1
        |         |
        |         +---> check if xp >= cumulativeXp(currentLevel + 1)
        |                   |
        |                   +-- yes --> level up
        |                   |            - update stored level
        |                   |            - send chat message to player
        |                   |
        |                   +-- no  --> done
        |
        +---> scrapManager.addScrap(uuid, bonusScrap)
```

### Damage Calculation (Updated)

```
Current:  damage = weaponConfigManager.getDamage(weaponId, upgradeLevel)

New:      baseDamage = weaponConfigManager.getDamage(weaponId, upgradeLevel)
          damage = baseDamage * weaponXpManager.getDamageMultiplier(uuid, weaponId)
```

## UI: Weapon XP in Detail Panel (Purge_WeaponSelect.ui)

Add an XP progress section to the existing `#DetailPanel` in the weapon shop. This shows when a player clicks a weapon they own.

### Layout (inside `#DetailPanel`, below `#DetailInfo`)

```
┌─────────────────────────────────────────────────────────────────────────┐
│ [Icon]  AK-47              ★★★☆☆           [Skins] [Upgrade: 100 ⚙]  │
│         Damage: 25                                                     │
│         ─────────────────────────────────────────────────────────────── │
│         Mastery Lv 7    ████████████░░░░░░░░  142 / 200 XP             │
│         +10.5% DMG   +3 Scrap/kill   +35% Ammo                        │
└─────────────────────────────────────────────────────────────────────────┘
```

### New UI Elements (added to `#DetailPanel`)

```
Group #XpSection {
  Anchor: (Left: 132, Right: 200, Bottom: 6, Height: 36);
  LayoutMode: Top;

  // Top row: level label + XP bar + XP text
  Group #XpRow {
    Anchor: (Left: 0, Right: 0, Height: 16);
    LayoutMode: Left;

    Label #XpLevelLabel {
      Anchor: (Width: 90, Height: 16);
      Style: (FontSize: 13, TextColor: #ffd44d, RenderBold: true, VerticalAlignment: Center);
      Text: "Mastery Lv 0";
    }

    ProgressBar #XpBar {
      Anchor: (Left: 4, Height: 10, Top: 3);
      FlexWeight: 1;
      Value: 0;
      Bar: #ffd44d;
      Background: #ffd44d(0.12);
    }

    Label #XpText {
      Anchor: (Left: 8, Width: 86, Height: 16);
      Style: (FontSize: 12, TextColor: #9eb7d4, VerticalAlignment: Center);
      Text: "0 / 25 XP";
    }
  }

  // Bottom row: boost summary
  Label #XpBoosts {
    Anchor: (Left: 0, Right: 0, Top: 2, Height: 14);
    Style: (FontSize: 11, TextColor: #8b9bb0);
    Text: "+0% DMG   +0 Scrap/kill   +0% Ammo";
  }
}
```

### Java Changes (PurgeWeaponSelectPage.java)

When populating the detail panel for a selected weapon:
- Look up `WeaponXpManager.getXpData(uuid, weaponId)` → (xp, level)
- Calculate `xpInCurrentLevel` and `xpNeededForNext` for the progress bar
- Set `#XpLevelLabel` text: `"Mastery Lv " + level` (or `"MAX"` at 20)
- Set `#XpBar` value: `xpInCurrentLevel / xpNeededForNext` (1.0 at max)
- Set `#XpText`: `xpInCurrentLevel + " / " + xpNeededForNext + " XP"` (or `"MAX"`)
- Set `#XpBoosts`: formatted boost values for current level

## UI: Weapon XP Bar on HUD (Purge_RunHud.ui)

Add a compact XP bar for the currently equipped weapon on the in-game HUD, positioned above the scrap counter at bottom-left.

### Layout

```
┌──────────────────────────────┐
│  AK-47 Lv 7                 │
│  ████████████░░░░░░  142/200 │
└──────────────────────────────┘
┌──────────────────────────────────────┐
│  [⚙]  4,200                         │   <-- existing scrap counter
└──────────────────────────────────────┘
```

### New UI Elements (added to `#HudLayer`, above `#ScrapHudRoot`)

```
Group #WeaponXpHud {
  Anchor: (Left: 26, Bottom: 140, Width: 260, Height: 42);
  LayoutMode: Top;
  Background: #0d1620(0.88);
  OutlineColor: #ffffff(0.06);
  OutlineSize: 1;
  Padding: (Left: 12, Right: 12, Top: 6, Bottom: 6);

  Group #WxpTopRow {
    Anchor: (Left: 0, Right: 0, Height: 16);
    LayoutMode: Left;

    Label #WxpWeaponName {
      Anchor: (Height: 16);
      Style: (FontSize: 13, TextColor: #ffd44d, RenderBold: true, VerticalAlignment: Center);
      Text: "AK-47 Lv 0";
    }

    Group { FlexWeight: 1; }

    Label #WxpXpText {
      Anchor: (Height: 16);
      Style: (FontSize: 12, TextColor: #9eb7d4, VerticalAlignment: Center);
      Text: "0 / 25";
    }
  }

  ProgressBar #WxpBar {
    Anchor: (Left: 0, Right: 0, Top: 4, Height: 8);
    Value: 0;
    Bar: #ffd44d;
    Background: #ffd44d(0.12);
  }
}
```

### Java Changes (PurgeHud.java / PurgeHudManager.java)

- On session start / weapon equip: populate `#WxpWeaponName`, `#WxpXpText`, `#WxpBar`
- On kill (XP granted): update `#WxpXpText` and `#WxpBar` value in real-time
- On level-up: flash the bar or briefly change color, update level text
- On weapon switch: refresh all fields for the new weapon
- `#WeaponXpHud` position may need adjusting if it overlaps with `#UpgradeTracker` (currently at Bottom: 140) — shift `#WeaponXpHud` to `Bottom: 196` or stack above upgrades

### HUD Position Note

The existing HUD layout from bottom up:
- `#ScrapHudRoot` at Bottom: 28 (h: 104) → top edge at ~132
- `#UpgradeTracker` at Bottom: 140 (h: 48) → top edge at ~188
- `#WeaponXpHud` should sit at **Bottom: 196** (h: 42) → top edge at ~238

This avoids overlap with both existing elements.

## Player Feedback

- **Level-up message:** Chat message on level-up (e.g., `"AK-47 reached Level 5! +7.5% damage, +2 scrap/kill, +25% ammo"`)
- **HUD update:** XP bar updates in real-time on each kill; flashes on level-up
- **Weapon shop:** Full XP progress visible when inspecting any owned weapon
- **Kill XP:** No per-kill chat notification (too spammy)

## Scope Exclusions

These are explicitly **not** part of this implementation:

- No prestige/reset system
- No XP multipliers or boosters
- No XP from non-kill sources
- No weapon-specific special perks (piercing, lifesteal, etc.)
- No changes to the existing Scrap upgrade system

## Implementation Order

1. **Database** — Create `purge_weapon_xp` table via `PurgeDatabaseSetup`
2. **WeaponXpStore** — Cache + CRUD operations
3. **WeaponXpManager** — XP logic, level calc, boost getters
4. **Plugin wiring** — Initialize store/manager in `HyvexaPurgePlugin`, hook lifecycle
5. **Damage integration** — Apply damage multiplier in `PurgeDamageModifierSystem`
6. **XP grant** — Call `addKillXp` on lethal hit in damage system
7. **Scrap integration** — Add bonus scrap on kill
8. **Ammo integration** — Apply ammo multiplier on equip/reload
9. **Level-up notification** — Chat message on level-up
10. **Weapon shop UI** — Add `#XpSection` to `Purge_WeaponSelect.ui` detail panel + Java wiring
11. **HUD UI** — Add `#WeaponXpHud` to `Purge_RunHud.ui` + real-time update logic
