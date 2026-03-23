# Phase 2 — Interface Propagation

Parent plan: [singleton-decoupling.md](singleton-decoupling.md)

## Goal

Type all consumers against core interfaces (`CurrencyStore`, `PlayerAnalytics`, `ConnectionProvider`) instead of concrete singleton classes. This enables mocking in tests and decouples modules from implementation details.

## Status — Updated 2026-03-23

**COMPLETE.** All non-composition-root consumers now use interfaces instead of concrete singleton classes.

| Interface | Implemented by | Status |
|-----------|---------------|--------|
| `CurrencyStore` | `VexaStore`, `FeatherStore` (via `CachedCurrencyStore`) | **Done** — all gameplay consumers injected. 25 `VexaStore.getInstance()` + 11 `FeatherStore.getInstance()` remain, all in plugin `setup()` methods |
| `PlayerAnalytics` | `AnalyticsStore` | **Done** — all gameplay consumers injected. 6 `AnalyticsStore.getInstance()` remain, all in plugin `setup()` methods |
| `ConnectionProvider` | `DatabaseManager` | **Done** (Phase 3) — all stores migrated |

## Task 1 — Propagate `PlayerAnalytics`

Replace `AnalyticsStore.getInstance()` with injected `PlayerAnalytics` in gameplay consumers.

**Current `AnalyticsStore.getInstance()` call sites (19 files):**

| Module | File | Priority |
|--------|------|----------|
| parkour-ascend | `AscendPlayerStore` | Medium |
| parkour-ascend | `AchievementManager` | Medium |
| parkour-ascend | `AscensionManager` | Medium |
| parkour-ascend | `ChallengeManager` | Medium |
| parkour-ascend | `TranscendenceManager` | Medium |
| parkour-ascend | `AscendMapSelectPage` | Low |
| parkour-ascend | `ParkourAscendPlugin` | Composition root — wire here |
| parkour | `RunTracker` | Medium |
| parkour | `DuelTracker` | Low |
| parkour | `ProgressStore` | Medium |
| parkour | `HyvexaPlugin` | Composition root — wire here |
| parkour | `AnalyticsCommand` | Low (admin tool) |
| purge | `HyvexaPurgePlugin` | Composition root — wire here |
| hub | `HubRouter` | Low |
| hub | `HyvexaHubPlugin` | Composition root — wire here |
| core | `AnalyticsStore` (self) | Skip |
| core | `CosmeticStore` | Low |
| core | `DiscordLinkStore` | Low |
| core | `WardrobeBridge` | Low |

**Approach:**
1. Start with Ascend (already has injection patterns from Phase 1) — inject `PlayerAnalytics` into the 5 managers from `ParkourAscendPlugin.setup()`
2. Do parkour: inject into `RunTracker`, `DuelTracker`, `ProgressStore` from `HyvexaPlugin.setup()`
3. Do remaining modules: wire at each plugin's composition root
4. Core stores that self-use `AnalyticsStore` internally are low priority — they're implementation details

## Task 2 — Propagate `CurrencyStore`

Replace `VexaStore.getInstance()` and `FeatherStore.getInstance()` with injected `CurrencyStore`.

**Current call sites (22 files across all modules):**

The existing `CurrencyBridge` / `CachedCurrencyStore` pattern is the target architecture. Consumers should receive a `CurrencyStore` (vexa or feather) via constructor.

**Key consumers to migrate:**
- `HudManager`, `AscendHudManager` — display balance
- `ParkourCommand`, `FeatherCommand`, `VexaCommand` — admin currency commands
- `RunValidator` — reward payouts
- `PurgeSkinShopPage`, `PurgeHudManager` — purge currency display/spend
- `ShopPage` (wardrobe) — cosmetic purchases
- `RunOrFallFeatherBridge` — feather rewards
- `VoteManager` — vote rewards

**Approach:**
1. Each plugin's `setup()` creates the appropriate `CurrencyStore` instances and injects them
2. Classes that need both vexa and feather get two `CurrencyStore` parameters (named `vexaStore`, `featherStore`)
3. `VexaStore.getInstance()` / `FeatherStore.getInstance()` remain accessible only from composition roots

## Task 3 — Clean up remaining minor interfaces

Low priority — do after Tasks 1-2:
- Extract read interface from `CosmeticStore` if test coverage is desired
- Extract interface from `DiscordLinkStore` if cross-module mocking needed
- Neither is urgent since these are low-coupling stores

## Completion Criteria

- Zero `AnalyticsStore.getInstance()` outside composition roots and the store itself
- Zero `VexaStore.getInstance()` / `FeatherStore.getInstance()` outside composition roots and the stores themselves
- All gameplay consumers typed against interfaces, not concrete classes
