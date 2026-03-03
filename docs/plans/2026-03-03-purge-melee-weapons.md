# Purge Mode - Melee Weapons System

## Context

The Purge mode currently only supports ranged weapons (Hyguns). Players need a secondary melee weapon in slot 1 for close-combat gameplay. This adds 3 vanilla melee weapons (Weapon_Sword_Wood, Weapon_Longsword_Katana, Weapon_Longsword_Scarab) with the same upgrade/config system as guns: damage levels 0-10, upgrade costs, XP mastery, admin configuration.

**New slot layout:**
| Slot | Item |
|------|------|
| 0 | Gun (primary ranged) |
| 1 | Melee weapon |
| 2 | Lootbox (unchanged) |
| 3 | Ammo (moved from slot 1) |
| 8 | Quit Orb (unchanged) |

**Lootbox** can now roll melee OR gun weapons. Accepting a gun replaces slot 0, accepting a melee replaces slot 1.

---

## Step 1: Add melee weapon defaults to PurgeWeaponConfigManager

**File:** `hyvexa-purge/src/main/java/io/hyvexa/purge/manager/PurgeWeaponConfigManager.java`

- Add 3 melee weapons to `buildDefaultWeapons()`:
  ```
  WoodSword    -> "Wooden Sword"    -> 15, 17, 19, 21, 23, 26, 29, 32, 35, 38, 42
  Katana       -> "Katana"          -> 25, 28, 31, 34, 38, 42, 47, 52, 57, 63, 70
  ScarabSword  -> "Scarab Sword"    -> 35, 39, 43, 48, 53, 59, 65, 72, 80, 88, 98
  ```
  Note: weaponId keys (`WoodSword`, `Katana`, `ScarabSword`) are internal identifiers. The actual Hytale item IDs (`Weapon_Sword_Wood`, etc.) are mapped separately.

- Add melee weapon registry (item ID <-> weapon ID mapping):
  ```java
  private static final Map<String, String> MELEE_ITEM_TO_WEAPON = Map.of(
      "Weapon_Sword_Wood", "WoodSword",
      "Weapon_Longsword_Katana", "Katana",
      "Weapon_Longsword_Scarab", "ScarabSword"
  );
  private static final Map<String, String> MELEE_WEAPON_TO_ITEM = /* reverse map */;
  ```

- Add helper methods:
  - `isMeleeWeapon(String weaponId)` - checks if weaponId is a melee weapon
  - `getMeleeItemId(String weaponId)` - returns vanilla item ID for a melee weapon ID
  - `getMeleeWeaponId(String itemId)` - returns weapon ID from a vanilla item ID
  - `getSessionMeleeWeaponId()` / `setSessionMeleeWeapon(String)` - default starting melee (similar to `sessionWeaponId` for guns)

- Add `sessionMeleeWeaponId` field (default: `"WoodSword"`) with DB persistence in `purge_weapon_defaults` via a settings row or a new `session_melee_weapon` column.

- Seed melee weapon defaults in `seedDefaults()` and `seedWeaponDefaults()`.

---

## Step 2: Update slot constants and loadout methods in HyvexaPurgePlugin

**File:** `hyvexa-purge/src/main/java/io/hyvexa/purge/HyvexaPurgePlugin.java`

**Constants:**
```java
// Change:
private static final short SLOT_PRIMARY_AMMO = 1;  ->  3
// Add:
private static final short SLOT_MELEE_WEAPON = 1;
```

**Loadout methods:**
- `grantLoadout(Player, PurgeSessionPlayerState)` - add `giveStartingMeleeWeapon(player, state)` call
- Add `giveStartingMeleeWeapon(Player, PurgeSessionPlayerState)`:
  - Get melee weapon ID from `state.getCurrentMeleeWeaponId()` or fallback to `weaponConfigManager.getSessionMeleeWeaponId()`
  - Resolve item ID via `weaponConfigManager.getMeleeItemId(weaponId)`
  - Set in slot `SLOT_MELEE_WEAPON`
- Add `switchMeleeWeapon(Player, PurgeSessionPlayerState, String)`:
  - Update `state.setCurrentMeleeWeaponId(newMeleeId)`
  - Set new item in `SLOT_MELEE_WEAPON`
  - Update weapon XP HUD

---

## Step 3: Add melee weapon tracking to PurgeSessionPlayerState

**File:** `hyvexa-purge/src/main/java/io/hyvexa/purge/data/PurgeSessionPlayerState.java`

- Add field: `private volatile String currentMeleeWeaponId;`
- Add getter/setter: `getCurrentMeleeWeaponId()` / `setCurrentMeleeWeaponId(String)`

---

## Step 4: Update session start to initialize melee weapon

**File:** `hyvexa-purge/src/main/java/io/hyvexa/purge/manager/PurgeSessionManager.java`

In `startSession()`, after setting `ps.setCurrentWeaponId(...)` (line ~174), add:
```java
ps.setCurrentMeleeWeaponId(plugin.getWeaponConfigManager().getSessionMeleeWeaponId());
```

---

## Step 5: Update damage system for melee detection

**File:** `hyvexa-purge/src/main/java/io/hyvexa/purge/system/PurgeDamageModifierSystem.java`

In `applyPlayerDamageOverride()`:
1. Get the Player component from the source entity
2. Read the item in slot 1 (melee slot) to get its itemId
3. Check `weaponConfigManager.isMeleeWeapon(itemId)` — if the player's held slot matches slot 1, it's a melee attack
4. **Detection approach**: Check `inventory.getHotbar().getItemStack(SLOT_MELEE)` to get the melee item ID. Then check if the Damage event's base amount matches vanilla melee damage (heuristic). OR more reliably: explore `Hotbar.getSelectedSlot()` API in HytaleServer.jar during implementation.

**Recommended approach:** Use the held item / selected slot to determine weapon type. If selected slot = 1 (melee), use `playerState.getCurrentMeleeWeaponId()` for damage calc. Otherwise, use `playerState.getCurrentWeaponId()` (gun).

**Fallback** if no selected-slot API exists: ALL player->zombie damage events currently go through `applyPlayerDamageOverride`. Since Hyguns guns have their own internal damage handling that fires separately, the damage events that reach our system might already be only melee attacks. We need to verify this during implementation by testing.

Key change to `applyPlayerDamageOverride`:
```java
String weaponId;
boolean isMelee = /* detection logic */;
if (isMelee) {
    weaponId = (playerState != null && playerState.getCurrentMeleeWeaponId() != null)
            ? playerState.getCurrentMeleeWeaponId()
            : weaponConfigManager.getSessionMeleeWeaponId();
} else {
    weaponId = (playerState != null && playerState.getCurrentWeaponId() != null)
            ? playerState.getCurrentWeaponId()
            : weaponConfigManager.getSessionWeaponId();
}
// Rest of damage calc uses weaponId (unchanged)
```

Also update `handleKillXp()` to pass the correct weaponId (melee or gun based on detection).

---

## Step 6: Update PurgeUpgradeManager ammo slot

**File:** `hyvexa-purge/src/main/java/io/hyvexa/purge/manager/PurgeUpgradeManager.java`

- Update `SLOT_WEAPON` constant from 0 to 0 (unchanged - it reads the gun from slot 0)
- Update ammo slot reference: the ammo ItemStack is now in slot 3 instead of slot 1
- `reapplyAmmoUpgrade()` reads from `SLOT_WEAPON` (0) and writes ammo props. Verify it doesn't reference slot 1 directly for ammo. If it does, update to slot 3.

---

## Step 7: Update lootbox to support melee weapons

### PurgeLootboxInteraction
**File:** `hyvexa-purge/src/main/java/io/hyvexa/purge/interaction/PurgeLootboxInteraction.java`

- Build candidates from all owned weapons (guns + melee)
- Exclude BOTH `currentWeaponId` (gun) AND `currentMeleeWeaponId` (melee)
- Pass weapon type info to PurgeLootboxRollPage (add `boolean isMelee` or let the page detect it)

### PurgeLootboxRollPage
**File:** `hyvexa-purge/src/main/java/io/hyvexa/purge/ui/PurgeLootboxRollPage.java`

- Update `ICON_WEAPON_IDS` list to include melee weapon IDs
- In `handleAccept()`: check if rolled weapon is melee
  - If melee: call `plugin.switchMeleeWeapon(player, playerState, rolledWeaponId)`
  - If gun: call `plugin.switchWeapon(player, playerState, rolledWeaponId)` (current behavior)
- Update `updateWeaponIcon()` to handle melee weapon icon display

---

## Step 8: Update weapon select UI for unified list

### PurgeWeaponSelectPage
**File:** `hyvexa-purge/src/main/java/io/hyvexa/purge/ui/PurgeWeaponSelectPage.java`

- Update `ICON_WEAPON_IDS` to include melee weapon IDs
- In LOADOUT mode, melee weapons appear alongside guns in owned/unowned sections
- Add visual indicator: append " [Melee]" to display name or use a different text color for melee weapons
- Handle weapon selection:
  - If melee weapon selected during a session: call `switchMeleeWeapon()` instead of `switchWeapon()`
  - Upgrade/purchase flow unchanged (same DB tables, same methods)
- `updateWeaponIcon()` needs melee weapon icon support (visibility toggle pattern for melee weapon textures)

### Purge_WeaponSelect.ui
**File:** `hyvexa-purge/src/main/resources/Common/UI/Custom/Pages/Purge_WeaponSelect.ui`

- Add icon elements for the 3 melee weapons (same visibility-toggle pattern as existing gun icons)

### Purge_LootboxRoll.ui
**File:** `hyvexa-purge/src/main/resources/Common/UI/Custom/Pages/Purge_LootboxRoll.ui`

- Add icon elements for the 3 melee weapons

---

## Step 9: Update PurgeWeaponUpgradeStore initialization

**File:** `hyvexa-purge/src/main/java/io/hyvexa/purge/data/PurgeWeaponUpgradeStore.java`

- `initializeDefaults()` already uses `defaultUnlocked` set from PurgeWeaponConfigManager
- Ensure melee weapons are added to `defaultUnlocked` set in config (if desired as default)
- No other changes needed - store is weaponId-agnostic

---

## Step 10: Update PurgeWaveManager kill/loot handling

**File:** `hyvexa-purge/src/main/java/io/hyvexa/purge/manager/PurgeWaveManager.java`

- When awarding kill XP, the damage system already determines which weapon was used
- Lootbox drop grants remain unchanged (slot 2)
- Revive flow (`revivePlayersDownedThisWave`): already calls `grantLoadout()` which will now include melee weapon

---

## Step 11: Update SetAmmoCommand slot reference

**File:** `hyvexa-purge/src/main/java/io/hyvexa/purge/command/SetAmmoCommand.java`

- Verify `SLOT_WEAPON` constant points to slot 0 (unchanged)
- This command reads the gun ItemStack from slot 0 and modifies ammo properties - should be unaffected

---

## Step 12: Update admin page for melee weapon config

**File:** `hyvexa-purge/src/main/java/io/hyvexa/purge/ui/PurgeWeaponAdminPage.java` (if exists, or within PurgeWeaponSelectPage ADMIN mode)

- Add `sessionMeleeWeaponId` config option alongside existing `sessionWeaponId`
- Melee weapons appear in admin list automatically (loaded from DB)

---

## Files Modified (summary)

| File | Changes |
|------|---------|
| `PurgeWeaponConfigManager.java` | Add 3 melee weapons, melee registry, sessionMeleeWeaponId |
| `HyvexaPurgePlugin.java` | Update slot constants, add melee loadout methods |
| `PurgeSessionPlayerState.java` | Add `currentMeleeWeaponId` field |
| `PurgeSessionManager.java` | Initialize melee weapon on session start |
| `PurgeDamageModifierSystem.java` | Detect melee vs gun, apply correct damage |
| `PurgeUpgradeManager.java` | Update ammo slot reference if needed |
| `PurgeLootboxInteraction.java` | Include melee in candidates, exclude both current weapons |
| `PurgeLootboxRollPage.java` | Handle melee weapon accept (slot 1), add melee icons |
| `PurgeWeaponSelectPage.java` | Add melee to unified list, handle melee selection |
| `Purge_WeaponSelect.ui` | Add melee weapon icon elements |
| `Purge_LootboxRoll.ui` | Add melee weapon icon elements |
| `SetAmmoCommand.java` | Verify slot references still correct |

---

## Technical Risk: Melee vs Gun Damage Detection

The `PurgeDamageModifierSystem` intercepts ALL player->zombie damage. We need to determine if the damage came from a melee swing or a gun shot. Options explored during implementation:

1. **Check `Hotbar.getSelectedSlot()`** - Preferred. Verify this API exists in HytaleServer.jar.
2. **Check Damage source type** - May have `DamageType` or different Source subclasses.
3. **Heuristic**: Hyguns may handle gun damage independently (never reaching our DamageEventSystem for the gun shots). If so, ALL events in `applyPlayerDamageOverride` are melee -> simplest solution, just detect player has melee and always apply melee damage. Needs testing.

This will be investigated at implementation time by checking the Hytale JAR API.

---

## Verification

1. **Session start**: Player spawns with gun (slot 0), melee (slot 1), lootbox (slot 2), ammo (slot 3), quit orb (slot 8)
2. **Melee damage**: Swinging melee weapon at zombies deals configured melee damage (not gun damage)
3. **Gun damage**: Shooting gun at zombies deals configured gun damage (unchanged)
4. **Lootbox**: Can roll melee or gun. Accept melee -> replaces slot 1. Accept gun -> replaces slot 0.
5. **/loadout**: Shows all weapons (guns + melee) in unified list. Can upgrade melee weapons.
6. **XP mastery**: Melee kills grant XP to the melee weapon used. Gun kills grant XP to the gun used.
7. **Revive**: After revive, player gets both weapons back
8. **Admin**: Can configure melee weapon damage/costs, set default session melee weapon
