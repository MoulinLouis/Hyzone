# Purge Weapon Upgrade — Single-Page Redesign

## Problem

Current flow: weapon list page → click → separate weapon detail/upgrade page. Two page transitions for a simple action. UX feels sluggish.

## Design

Merge both pages into one. Weapon grid stays at top, detail panel appears below the selected weapon.

### Layout

```
+-------------------------------------------+
|  [orange accent bar]                      |
|  Weapon Upgrade                        [X]|
+-------------------------------------------+
|  Choose a weapon to upgrade.              |
|                                           |
|  [AK-47]  [M4A1]  [Shotgun]  [Sniper]   |
|  [RPG]                                    |
|                                           |
+-------------------------------------------+
|  [icon]  AK-47      ★★★☆☆               |
|          23 -> 25 dmg    175 scrap        |
|          [   UPGRADE - 175 scrap   ]      |
+-------------------------------------------+
```

### Behavior

- Detail panel (`#DetailPanel`) starts hidden (`Visible: false`)
- Clicking a weapon card → `sendUpdate()` shows `#DetailPanel`, populates name/icon/stars/damage/cost/upgrade button
- Selected card gets highlighted outline (`#ffffff(0.15)`) to indicate active weapon
- Upgrade button click → processes upgrade → `sendUpdate()` refreshes detail panel + star bar on the card
- Clicking different weapon → detail panel swaps to new weapon data
- Max level → upgrade button hidden, shows "MAX LEVEL" in gold
- Not enough scrap → upgrade button hidden, shows "Not enough scrap (X/Y)" in red
- OP-only reset button appears below upgrade button when applicable
- ADMIN mode unchanged — still opens separate `PurgeWeaponAdminPage`

### Detail Panel Layout (horizontal)

- Left: weapon icon (48x48)
- Center column: weapon name (bold, 16px) + star rating (28px stars) + damage line ("23 -> 25 dmg")
- Right: upgrade button (or status text)

### Files Changed

| File | Action |
|------|--------|
| `Purge_WeaponSelect.ui` | Rewrite — add `#DetailPanel` below `#WeaponList` |
| `Purge_WeaponSelectEntry.ui` | Keep as-is |
| `PurgeWeaponSelectPage.java` | Rewrite — absorb upgrade logic, track selected weapon, handle upgrade/reset events |
| `PurgeWeaponUpgradePage.java` | Delete (merged) |
| `Purge_WeaponUpgrade.ui` | Delete (merged) |

### State Tracking

`PurgeWeaponSelectPage` gains:
- `String selectedWeaponId` — currently selected weapon (null = none selected)
- Upgrade/reset event handlers (moved from `PurgeWeaponUpgradePage`)
- `sendRefresh()` method that updates both the detail panel and the selected card's star bar

### Event IDs

- `Select:<weaponId>` — weapon card clicked
- `Upgrade` — upgrade button clicked
- `Reset` — reset button clicked (OP only)
- `Back` — close button clicked
