# Cat Easter Egg Design

**Date:** 2026-02-18
**Module:** hyvexa-parkour-ascend

## Overview

Hidden easter egg: 5 cat NPCs scattered across the Ascend world. Players interact with them via NPC dialog buttons that silently execute `/cat <token>`. Finding all 5 unlocks the "Cat Collector" achievement in a new "Easter Eggs" category.

## Architecture

### Data — `AscendPlayerProgress`

- New field: `Set<String> foundCats = ConcurrentHashMap.newKeySet()`
- Methods: `getFoundCats()`, `addFoundCat(String token)`, `getFoundCatCount()`
- Tokens stored as `String` (not enum) — fixed, arbitrary values

### Persistence — `AscendPlayerPersistence`

- New table: `ascend_player_cats`
  - `player_uuid VARCHAR(36)`
  - `cat_token VARCHAR(16)`
  - `PRIMARY KEY (player_uuid, cat_token)`
- Load at login, save at flush (same pattern as skills/achievements)
- Delete cascade on data purge

### Command — `CatCommand`

- Syntax: `/cat <token>`
- Validation: ref valid → store loaded → AscendModeGate
- Token map (5 entries):
  | Token | Cat Name |
  |-------|----------|
  | WHK | Whiskers |
  | PUR | Shadow |
  | MRW | Marble |
  | FLF | Fluffball |
  | NKO | Neko |
- Invalid token → silent ignore (easter egg, no error)
- Already found → toast "You already found this cat!"
- New find → add to set, toast "Cat found! (X/5)", markDirty
- All 5 found → trigger `checkAndUnlockAchievements`

### Achievement

- New category: `EASTER_EGGS("Easter Eggs")` in `AchievementCategory` enum
- New type: `CAT_COLLECTOR("Cat Collector", "Find all 5 hidden cats", AchievementCategory.EASTER_EGGS, true)` — hidden
- `isAchievementEarned`: `progress.getFoundCatCount() >= 5`
- `getProgress`: `current = foundCatCount, required = 5`

## Not Included

- No gem rewards
- No cosmetic rewards
- No NPC spawning in code (placed manually via Hytale NPC dialog system)
- No custom UI (uses existing achievements page counter)

## NPC Dialog Setup (Manual)

Each cat NPC gets a dialog button configured to execute:
- Whiskers: `/cat WHK`
- Shadow: `/cat PUR`
- Marble: `/cat MRW`
- Fluffball: `/cat FLF`
- Neko: `/cat NKO`
