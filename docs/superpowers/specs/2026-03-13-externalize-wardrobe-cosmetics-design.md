# Externalize WardrobeBridge Cosmetics to JSON

**Tech Debt Item:** 2.4
**Module:** core/wardrobe
**Date:** 2026-03-13

## Problem

Shop cosmetics are hardcoded in `WardrobeBridge.COSMETICS` as a `List.of(...)` spanning ~130 lines (160 entries). Adding or removing cosmetics requires recompilation.

## Solution

Load cosmetic definitions from `mods/Parkour/cosmetics.json` at startup. Generate a default file from the current hardcoded list on first run.

## Design

### New Class: `CosmeticConfigLoader`

**Package:** `io.hyvexa.core.wardrobe`
**Pattern:** Follows `DatabaseConfig` — Gson deserialize from `mods/Parkour/cosmetics.json`.

Responsibilities:
- `load()`: Read and parse JSON file, return `List<WardrobeCosmeticDef>`
- `generateDefault()`: Write current cosmetics as default JSON if file missing
- Validate on load: reject duplicate IDs, reject blank required fields (fileName, displayName, permissionNode, category)
- Log warning for entries with null `iconKey` (valid — most entries have null)

### JSON Format

Flat array. The `"WD_"` id prefix is applied automatically during parsing (matching the existing `wd()` convention), keeping the JSON clean:

```json
[
  {
    "fileName": "Badge_Hyvexa",
    "displayName": "Hyvexa Badge",
    "permissionNode": "hyvexa.cosmetic.badge_hyvexa",
    "category": "Badge",
    "iconKey": "BadgeHyvexa",
    "iconPath": "Icons/Wardrobe/Cosmetics/Badges/Hyvexa.png"
  }
]
```

Fields map 1:1 to `WardrobeCosmeticDef` record fields:
- `fileName` → `id` (with `"WD_"` prefix applied)
- `displayName` → `displayName`
- `permissionNode` → `permissionNode`
- `category` → `category`
- `iconKey` → `iconKey` (nullable)
- `iconPath` → `iconPath`

### Changes to `WardrobeBridge`

- Replace `private static final List<WardrobeCosmeticDef> COSMETICS = List.of(...)` with `private List<WardrobeCosmeticDef> cosmetics = List.of()` (instance field, initialized empty)
- Add `initialize()` method that calls `CosmeticConfigLoader.load()` and stores the result
- Remove the `wd()` helper method (no longer needed)
- Update `getAllCosmetics()`, `findById()`, `getCosmeticsByCategory()`, `regrantPermissions()`, `resetAll()` to use instance field instead of static constant
- Keep `CATEGORY_GROUPS`, `GROUP_ORDER` hardcoded (structural UI concern, rarely changes)
- Keep `WardrobeCosmeticDef` record unchanged
- Keep all public API method signatures unchanged

### Changes to `WardrobePlugin`

- Call `WardrobeBridge.getInstance().initialize()` in `onSetup()` before `StoreInitializer.initialize()`

### Error Handling

- **File missing:** Generate default, log info, load from generated file
- **Malformed JSON:** Log error, cosmetics list stays empty (shop non-functional, server stays up)
- **Duplicate IDs:** Log error with the duplicate ID, skip the duplicate entry
- **Blank required fields:** Log error with the entry details, skip the entry

### What Stays Hardcoded

- `CATEGORY_GROUPS` map (fine-grained → broad group mapping)
- `GROUP_ORDER` list (display order of broad groups)
- `WardrobeCosmeticDef` record definition
- All purchase/permission logic

## Files Touched

| File | Action | Est. LOC |
|------|--------|----------|
| `CosmeticConfigLoader.java` | New | ~60 |
| `WardrobeBridge.java` | Edit — replace static list, add `initialize()`, remove `wd()` | Net -100 |
| `WardrobePlugin.java` | Edit — add `initialize()` call | +1 |

## Consumers (no changes needed)

- `WardrobeShopTab` — uses `WardrobeBridge.getInstance().getCategories()`, `getCosmeticsByCategory()`, `findById()`, `purchase()`
- `ShopConfigTab` — uses `getCategories()`, `getCosmeticsByCategory()`
- `WardrobeBuyCommand` — uses `getAllCosmetics()`, `purchase()`
- `WardrobePlugin` — uses `regrantPermissions()`

All access cosmetics through `WardrobeBridge` public API which is unchanged.
