# Wardrobe Module

Quick reference for `hyvexa-wardrobe`.

## Scope

Wardrobe is the cosmetics shop module. It provides the `/shop` command and manages cosmetic purchases using vexa (global currency) and feathers (parkour currency). It integrates with the third-party Wardrobe mod for visual cosmetic rendering and with PurgeSkinStore for weapon skin purchases.

The module does not handle cosmetic rendering -- that is done by the Wardrobe mod via permission-based locking. This module handles the economy side: browsing, purchasing, and permission granting.

Entry point: `hyvexa-wardrobe/src/main/java/io/hyvexa/wardrobe/WardrobePlugin.java`

## Key Classes

| Class | Purpose |
|-------|---------|
| `WardrobePlugin` | Plugin entry point, store init, shop tab registration, event handlers |
| `WardrobeShopTab` | Main cosmetics shop tab (wardrobe cosmetics purchasable with vexa) |
| `EffectsShopTab` | Effects/particles shop tab |
| `PurgeSkinShopTab` | Purge weapon skins shop tab |
| `ShopConfigTab` | Admin shop configuration tab |
| `ShopPage` | UI page rendering for the `/shop` command |
| `WardrobeShopUiUtils` | Shared UI helpers for shop rendering |

### Core stores (in `hyvexa-core`):

| Store | Purpose |
|-------|---------|
| `VexaStore` | Global vexa currency |
| `FeatherStore` | Parkour feather currency |
| `CosmeticStore` | Player cosmetic ownership (`player_cosmetics` table) |
| `CosmeticShopConfigStore` | Admin-configurable shop item visibility/pricing (`cosmetic_shop_config` table) |
| `CosmeticManager` | Cosmetic application/removal, permission granting via `WardrobeBridge` |
| `PurgeSkinStore` | Weapon skin selections (`purge_weapon_skins` table) |
| `WardrobeBridge` | Bridge to the Wardrobe mod API for permission grants |

## Runtime Flow

1. `WardrobePlugin.setup()` initializes stores: DB, Vexa, Feather, Cosmetic, PurgeSkin, CosmeticShopConfig.
2. Four shop tabs are created and registered with `ShopTabRegistry`: wardrobe cosmetics, effects, purge skins, and admin config.
3. Commands are registered: `/shop`, `/wardrobebuy`, `/wardrobereset`.
4. `PlayerReadyEvent`: re-grants wardrobe permissions via `WardrobeBridge` and reapplies active cosmetics via `CosmeticManager`.
5. `PlayerDisconnectEvent`: evicts all per-player caches (shop tabs, cosmetic manager, stores).

## Commands

Player-facing:
- `/shop` -- opens the shop page with tabbed navigation

Admin/staff:
- `/wardrobebuy <cosmeticId> [player]` -- force-grant a cosmetic
- `/wardrobereset <player>` -- reset a player's cosmetic state

## Owned Tables

The wardrobe module uses tables defined in `hyvexa-core`:
- `player_cosmetics` -- cosmetic ownership per player
- `cosmetic_shop_config` -- shop item configuration (pricing, visibility)
- `purge_weapon_skins` -- weapon skin selections

## Key Files

- Plugin entry: `hyvexa-wardrobe/src/main/java/io/hyvexa/wardrobe/WardrobePlugin.java`
- Shop page: `hyvexa-wardrobe/src/main/java/io/hyvexa/wardrobe/ui/ShopPage.java`
- Shop tabs: `hyvexa-wardrobe/src/main/java/io/hyvexa/wardrobe/WardrobeShopTab.java`, `EffectsShopTab.java`, `PurgeSkinShopTab.java`
- Commands: `hyvexa-wardrobe/src/main/java/io/hyvexa/wardrobe/command/`
- Cosmetic resources: `hyvexa-wardrobe/src/main/resources/` (Common + Server/Wardrobe/Cosmetics)

## Related Docs

- Wardrobe integration guide: `docs/WARDROBE_MOD.md` (cosmetic asset structure, JSON format, variant system)
- Architecture overview: `docs/ARCHITECTURE.md`
- Database schema: `docs/DATABASE.md`
