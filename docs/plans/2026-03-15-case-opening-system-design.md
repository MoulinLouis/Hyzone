# Case Opening System Design

**Date:** 2026-03-15
**Status:** Approved
**Scope:** Replace direct wardrobe cosmetic purchases with a case opening (gacha) system + daily rotation shop

## Overview

The current wardrobe shop allows direct purchase of cosmetics with feathers/vexa. This redesign replaces it with:
1. **Case opening** — 3 tiers of cases costing feathers, giving random cosmetics
2. **Daily rotation shop** — 4 random cosmetics available for direct purchase at a premium markup

Effects (glows/trails) and Weapon Skins tabs remain unchanged.

---

## Rarity System

### 3 Tiers

| Rarity | Color | Default Probability | Default Dupe Value (feathers) |
|--------|-------|--------------------|-----------------------------|
| Common | Grey/White | 70% | 50 |
| Rare | Blue | 25% | 150 |
| Legendary | Gold | 5% | 500 |

- Every wardrobe cosmetic is assigned a rarity by the admin in-game
- Unassigned cosmetics default to Common
- Probabilities and dupe values are globally configurable by admin in-game
- With ~110 items, rough distribution: ~77 Common, ~28 Rare, ~5 Legendary (admin decides)

---

## Cases

### 3 Case Tiers

| Case | Default Price (feathers) | Pool | Probability Redistribution |
|------|-------------------------|------|---------------------------|
| Basic | 100 | Common + Rare only | Common 74%, Rare 26% |
| Premium | 300 | All rarities | Common 70%, Rare 25%, Legendary 5% |
| Legendary | 800 | Rare + Legendary only | Rare 83%, Legendary 17% |

- Case prices are configurable by admin in-game
- Pool is determined by which rarities are included (not manually curated)
- Probabilities auto-redistribute proportionally when a tier is excluded
- Only cosmetics marked as "included in case pool" by admin are eligible

### Opening Flow

1. Player selects a case, sees price + rarities included
2. Clicks "Open", feathers verified and deducted
3. Roulette animation plays (items scroll, decelerate, land on result)
4. Result revealed with rarity color
5. **If new cosmetic:** "New cosmetic unlocked!" + added to collection
6. **If duplicate:** "Duplicate! +X feathers" + dupe value refunded

### Duplicate Handling

When a player rolls a cosmetic they already own:
- Automatically converted to feathers based on the tier's dupe value
- Dupe values configurable by admin per tier (not per cosmetic)

---

## Daily Rotation Shop

### Design

- **4 cosmetics** available for direct purchase, refreshed daily
- Selection is **fully random** — no forced rarity distribution (can be 4 Common, 4 Legendary, or any mix)
- Reset at midnight (server time)
- Seed based on date so all players see the same items
- Recalculated on server startup from current date (not persisted)
- A cosmetic cannot appear two days in a row
- Cosmetics the player already owns still display (with "Owned" badge)

### Pricing

- **Price = dupe value of tier x rotation multiplier**
- Default multiplier: **x5**
- Default prices: Common 250, Rare 750, Legendary 2500 feathers
- Multiplier configurable by admin in-game
- Intentionally expensive — cases are the better deal, rotation is for "I want THIS specific item"

---

## Admin Config (In-Game)

The existing Shop Config tab (`/shop` -> admin tab, OP only) is extended.

### Per-Cosmetic Config

- **Rarity** — Common / Rare / Legendary (dropdown or cycle button)
- **Included in case pool** — Yes/No toggle to exclude specific cosmetics
- Existing search bar preserved

### Global Config (New Section)

| Setting | Default | Description |
|---------|---------|-------------|
| Common probability | 70% | Base probability for Common tier |
| Rare probability | 25% | Base probability for Rare tier |
| Legendary probability | 5% | Base probability for Legendary tier |
| Basic case price | 100 | Feathers cost for Basic case |
| Premium case price | 300 | Feathers cost for Premium case |
| Legendary case price | 800 | Feathers cost for Legendary case |
| Common dupe value | 50 | Feathers refund for Common duplicate |
| Rare dupe value | 150 | Feathers refund for Rare duplicate |
| Legendary dupe value | 500 | Feathers refund for Legendary duplicate |
| Rotation multiplier | 5 | Price multiplier for daily shop items |

All editable in-game, persisted to DB, effective immediately.

---

## Shop Tab Structure

| Tab | Content | Status |
|-----|---------|--------|
| Cases | 3 cases (Basic, Premium, Legendary) with open button | **New** |
| Daily Shop | 4 rotation cosmetics with buy button + reset timer | **New** |
| Effects | Glows/Trails direct purchase (vexa/feathers) | **Unchanged** |
| Weapon Skins | Purge skins rotation (OP only) | **Unchanged** |
| Admin Config | Existing config + rarity assignment + global case config | **Extended** |

The current `WardrobeShopTab` (direct cosmetic purchase grid) is removed and replaced by Cases + Daily Shop.

---

## Database Changes

### New Table: `case_config`

```sql
CREATE TABLE case_config (
  config_key VARCHAR(64) NOT NULL PRIMARY KEY,
  config_value VARCHAR(255) NOT NULL
) ENGINE=InnoDB;
```

Stores all global config (probabilities, prices, dupe values, multiplier) as key-value pairs.

### New Table: `case_opening_log`

```sql
CREATE TABLE case_opening_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  player_uuid VARCHAR(36) NOT NULL,
  case_type VARCHAR(16) NOT NULL,
  cosmetic_id VARCHAR(64) NOT NULL,
  rarity VARCHAR(16) NOT NULL,
  was_duplicate BOOLEAN NOT NULL DEFAULT FALSE,
  opened_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_player (player_uuid),
  INDEX idx_opened_at (opened_at)
) ENGINE=InnoDB;
```

Analytics and anti-abuse tracking.

### Modified Table: `cosmetic_shop_config`

```sql
ALTER TABLE cosmetic_shop_config
  ADD COLUMN rarity VARCHAR(16) NOT NULL DEFAULT 'COMMON',
  ADD COLUMN in_case_pool BOOLEAN NOT NULL DEFAULT TRUE;
```

Existing `price`, `currency`, `available` columns remain (used by Effects tab, and as fallback).

---

## Implementation Components

### Core Module (hyvexa-core)

| Component | Type | Purpose |
|-----------|------|---------|
| `CaseType` | Enum | Basic, Premium, Legendary — defines which rarities are included |
| `Rarity` | Enum | Common, Rare, Legendary — with color, default probability |
| `CaseConfigStore` | Singleton | Persist/load global case config from `case_config` table |
| `CaseOpeningService` | Service | Roll logic: select tier by probability -> select random cosmetic from tier -> handle dupe |
| `CaseOpeningLog` | Store | Write opening history to `case_opening_log` |
| `DailyRotationService` | Service | Compute 4 daily items from date seed, calculate prices |
| `CosmeticShopConfigStore` | Modified | Add rarity + in_case_pool fields |

### Wardrobe Module (hyvexa-wardrobe)

| Component | Type | Purpose |
|-----------|------|---------|
| `CaseShopTab` | ShopTab | Display 3 cases, handle open flow, trigger animation |
| `DailyShopTab` | ShopTab | Display 4 daily items, handle direct purchase, show timer |
| `ShopConfigTab` | Modified | Add rarity dropdown per cosmetic + global config section |
| `WardrobeShopTab` | Removed | Replaced by CaseShopTab + DailyShopTab |
| `Shop_Case.ui` | UI | Case selection card |
| `Shop_CaseOpening.ui` | UI | Roulette animation + result reveal overlay |
| `Shop_DailyItem.ui` | UI | Daily rotation item card |

---

## Roulette Animation (Deferred)

The case opening animation will simulate a CS:GO-style roulette with rapid UI updates that decelerate to reveal the result. Exact implementation approach TBD — will be iterated on during development based on Hytale UI system capabilities.

---

## Out of Scope

- Effects (glows/trails) — remain direct purchase
- Weapon Skins — remain as-is
- Vexa packs — unchanged
- Multi-open (open 10 cases at once) — potential future feature
- Pity system / guaranteed legendary after N opens — not planned
