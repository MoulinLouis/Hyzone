# Black Textures Investigation

**Date**: 2026-03-22
**Issue**: Some players see all UI textures/images as black rectangles. Text remains visible on top. Problem persists across game restarts, server restarts, and even game reinstalls. Affects only a subset of players — others (including the server owner) never experience it.

---

## Important Context

- **Wardrobe-0.3.2 is NOT a conflict** — it provides the `/wardrobe` command needed for players to equip cosmetics. HyvexaWardrobe handles the shop UI and purchases. They are complementary, not competing.
- **MultipleHUD crash logs are misleading** — when any UI bug occurs (missing selector, bad asset, etc.), the crash message surfaces the HUD document name because that's the active document. MultipleHUD is a symptom reporter, not the cause.

## Summary of Findings

Three high-probability causes were identified, ranked by likelihood:

| # | Cause | Likelihood | Scope | Fix Difficulty |
|---|-------|-----------|-------|----------------|
| 1 | **Massive duplicate asset count (932-1170)** from multiple asset packs | **High** | Server-wide, but may manifest inconsistently per client | Medium |
| 2 | **Wildly inconsistent client asset caching** — some clients get 0 bytes, others 600ms+ of data | **High** | Player-specific | Medium (version bump) |
| 3 | **Client-side GPU/driver issue** (Hytale engine texture pipeline) | **Medium** | Player-specific | Out of our control |

Additionally, several lower-probability contributing factors were found. Details below.

---

## 1. Duplicate Asset Overload (932-1179 duplicates)

### What the logs say

Server logs consistently report massive duplicate asset counts during startup:

```
CommonAssetModule: Duplicated Asset Count: 932
CommonAssetModule: Duplicated Asset Count: 1030
CommonAssetModule: Duplicated Asset Count: 1179
```

The count increases as each plugin's asset pack loads, meaning plugins are registering assets that conflict with each other or with Hytale's base assets.

### Why this matters

- **6 Hyvexa plugins** all declare `"IncludesAssetPack": true` in their manifests
- Each plugin bundles its own `Common/UI/Custom/Textures/` and `Common/UI/Custom/Pages/` directories
- When Hytale loads these, assets with identical relative paths collide
- The engine's behavior on collision is undefined — it may:
  - Silently overwrite textures (last-loaded wins)
  - Corrupt internal texture atlas references
  - Leave some clients with stale/broken texture cache entries
- This could explain why only some players are affected: depending on when they joined, what's cached client-side, or what load order the engine resolved for their session

### How to investigate further

1. Check if any two plugins ship textures with the **same filename** at the same relative path
2. Check if any plugin textures conflict with Hytale base game asset paths
3. Try temporarily reducing to a single plugin and see if the problem disappears

---

## 2. Client Asset Caching Inconsistency (Confirmed in Prod Logs)

### Evidence from production logs (2026-03-18 and 2026-03-20)

The `LoginTiming` logs reveal extreme variation in asset transfer during player login:

| Player | Send Common Assets | Player Options (loading screen) | Notes |
|--------|-------------------|--------------------------------|-------|
| **Playfade** (owner) | **170us - 247us** | 0.9 - 1.5s | Client has everything cached — essentially 0 bytes sent |
| **chlletmaniac666** | 8ms | 1.2s | Nearly fully cached |
| **0H0** | 1.9ms - 596ms | 2 - 20s | Variable — sometimes cached, sometimes not |
| **BlackZky72** | 566ms - 612ms | **44 - 46 seconds** | Always receives full asset payload. 46s loading screen. |
| **H3nR153** | 784ms | **59 seconds** | Full payload + longest loading screen observed |
| **Skyfall41** | 703ms | **52 seconds** | Full payload, long loading |
| **Most first-timers** | 500-800ms | 10-20s | Typical for new clients |

### What this means

- **Playfade never sees the bug** because his client has a stable, complete cache from dev iterations. The server sends essentially nothing (`170 microseconds` = 0 bytes = "you already have everything").
- **Players with 500-800ms Send time** receive the full asset payload each connection. This takes **44-59 seconds** on the loading screen for clients with slow hardware.
- **All manifests are frozen at `Version: 1.0.0`**, meaning Hytale's asset caching has no reliable invalidation signal. The client decides what to request based on hashes or version matching. This can lead to:
  - Stale cached assets being kept when they should be updated
  - Incomplete asset packs if the connection interrupted during the ~600ms transfer
  - Hash collisions if two plugins ship different files at the same relative path

### Why some players get black textures

If the 600ms asset transfer is interrupted (network drop, timeout, slow connection) or the 44-59 second client-side processing phase fails partway through:
1. The client has a **partially loaded** asset pack
2. Textures that failed to load render as **black rectangles**
3. Text still renders because glyphs use a different pipeline (font atlas, not texture atlas)
4. The corrupted cache state **persists** because the version is still `1.0.0` — the client thinks it has the assets and doesn't re-download

### decodeJson error at server boot

Both logs show this at startup (line 3):
```
[SEVERE] [SERR] decodeJson: class com.hypixel.hytale.codec.DocumentContainingCodec
```
This JSON parsing failure during asset loading could mean a malformed asset file is being loaded. If this affects the asset index sent to clients, it could cause the client to build an incorrect asset map.

---

## 3. Client-Side GPU/Driver Issues

### Why this is a possibility

The problem's characteristics point partly to client-side rendering:

- **Player-specific**: same server, same mods, but only some players affected
- **Persists across reinstalls**: suggests hardware-dependent behavior
- **All textures black, text visible**: classic GPU texture upload/decompression failure — text is rendered differently (glyph-based) than textures (sampled from atlas)

### Common causes

- **Outdated GPU drivers** — especially Intel integrated graphics (common on laptops)
- **Unsupported texture format** — if any texture uses a format the player's GPU can't decompress
- **VRAM exhaustion** — with 6 asset packs totaling ~120 MB of textures, older GPUs may fail to allocate texture memory, falling back to black
- **Texture atlas corruption** — Hytale may build a combined texture atlas at runtime; if it exceeds GPU max texture size (e.g., 4096x4096 on older hardware), textures display as black

### How to investigate

- Ask affected players for their GPU model and driver version
- Ask if they have other Hytale servers where textures work fine (isolates server-specific vs hardware-specific)
- Ask affected players to check their Hytale graphics settings (texture quality, resolution)

---

## 4. Additional Findings (Lower Priority)

### 4a. 19 Orphaned Asset Pack Directories in `run/mods/`

Both prod logs show **19 directories** being skipped due to missing manifests:

```
old, Purge, Hytale_Shop, Hyronix_NPC Dialog, HytaleOne_OneQuery, HOS_Status,
HyQuery, Hyvote_HytaleVotifier, RunOrFall, Hyvexa_HyvexaVotifier, hyfixes,
Mods_SimpleProtect, Tebex_Tebex-Hytale, Parkour, Hytaled_Optimizer, Hub,
_NPC Dialog, hgame
```

These don't directly cause black textures (they're skipped), but they contribute to the messy mod directory and could confuse the client-side asset cache if they were once valid asset packs that the client still has cached.

### 4b. Cosmetic Icon Keys Missing

Logs show many cosmetics with `null iconKey`:

```
[CosmeticConfigLoader] Cosmetic WD_Badge_Pride has null iconKey
[CosmeticConfigLoader] Cosmetic WD_Cloak_Chippy has null iconKey
... (dozens more)
```

This means the wardrobe UI tries to load icons for these cosmetics but has no valid asset path. The fallback behavior when `iconPath` is null is to hide the element (`cmd.set(elementId + ".Visible", false)`), but if the fallback is incomplete, it could leave blank/black image containers.

### 4c. Dynamic Background Property Violations

The codebase contains **~500 instances** of dynamically setting `.Background` on UI elements at runtime:

```java
cmd.set(selector + ".Background", colorHexValue);
```

The project's own documentation (CLAUDE.md) states: *"Never use dynamic Background property changes on UI elements — they don't work in Hytale's UI system."*

However, these set **colors** (hex values), not textures. While this is a known limitation that can cause visual glitches, it would typically affect **all players** equally, not just some. This is more likely the cause of individual UI elements appearing wrong rather than the global "all textures black" issue.

**Note**: This is still worth addressing separately as it causes visual bugs for everyone.

### 4d. Cosmetic Effect Race Condition

The cosmetic system has a timing vulnerability when players rapidly switch cosmetics:

1. First cosmetic triggers `clearCosmeticChannels()` (clears effects immediately)
2. Second cosmetic overwrites `pendingCosmeticId`
3. First cosmetic's deferred apply task (100ms delay) sees the ID changed, exits without applying
4. If the second task's packet send fails, the player sees no cosmetic at all

This is a real bug but would cause **missing cosmetic effects**, not **all textures being black**.

### 4e. Leaderboard UI Crash (Confirmed in Prod Logs)

Two players crashed with the same error on 2026-03-18/19:
```
Crash - Selected element in CustomUI command was not found. Selector: #LeaderboardCards[0] #Completion.Text
```
- `AdrielLV` (20:29:38) and `tomikel` (16:50:50)

This means the leaderboard page tries to update `#LeaderboardCards[0] #Completion.Text` when no cards exist (index 0 is out of bounds). This is a real plugin bug (not related to black textures) but contributes to player crashes.

### 4f. Dynamic Background Set with Alpha Notation (Concrete Bug)

`hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/mine/ui/MinePage.java` line 151:
```java
cmd.set(sel + " #AssignBtnBg.Background", "#ef4444(0.85)");
```

Per CODE_PATTERNS.md: `commandBuilder.set()` does **not** support alpha notation like `#color(alpha)`. This causes an **error texture (white + red cross)** on the "Remove" button in the mine slot UI. This is not the primary black-rendering cause (it produces white+red, not black), but it is a concrete rendering bug affecting all players who open this UI.

### 4f. Camera State Not Guaranteed to Reset on Disconnect

Multiple code paths send `SetServerCamera` packets to manipulate player cameras:

- `AscensionCinematic.java` — full cinematic camera sequences during ascension events
- `CinematicTestCommand.java` — test command with complex camera movement choreography
- `SpectatorCommand.java` — 3rd-person spectator camera
- `CamTestCommand.java` (Purge) — camera testing

If a player **disconnects mid-cinematic** (e.g., during an ascension sequence or while spectating), the camera state may not be properly reset. The camera reset relies on either:
- The cinematic completing normally and calling `resetCamera()`
- The player executing a command to reset

If the client reconnects with a stale `ServerCameraSettings` state (especially with `skipCharacterPhysics = true` or unusual `lookMultiplier`), this could cause rendering anomalies. Hytale's handling of leftover custom camera state after reconnect is unknown.

This is unlikely to cause "all textures black" on its own, but could compound with other rendering issues.

### 4g. EntityEffect Self-Sync Packet Construction

`CosmeticManager.sendEffectSyncToSelf()` constructs raw `EntityUpdates` packets to force the client to see its own cosmetic effects. If the packet is malformed (e.g., `createInitUpdates()` returns inconsistent data after a rapid clear+reapply), the client's effect rendering pipeline could enter a bad state.

The code has a guard for null updates (falls back to empty array), but doesn't guard against updates referencing effect indices that no longer exist after `clearEffects()`. In theory, a stale effect index in a sync packet could cause the client to reference an invalid texture/shader slot.

---

## Recommended Debugging Steps

### Quick wins (try first)

1. **Bump manifest versions** — change `Version: "1.0.0"` to `"1.1.0"` (or use build timestamps) in all 6 Hyvexa module manifests + `gradle.properties`. This forces ALL clients to re-download assets on next connect, clearing stale caches. This is the single most impactful test.
2. **Clean up `run/mods/`** — remove the 19 orphaned directories that don't have valid manifests. This reduces noise and potential stale client cache references.
3. **Ask an affected player to delete their Hytale cache** — typically in `%AppData%/Hytale/` or similar. If the game doesn't have a "clear cache" option, a full `AppData` cleanup (not just reinstall) may be needed.

### Medium effort

4. **Audit asset paths across all plugin JARs** — extract each JAR and diff the `Common/` directory trees to find exact path collisions contributing to the 1170 duplicate count. Deduplicate where possible (e.g., shared textures like `star.png`, `user.png`, `vexa.png` should live in one module only).
5. **Investigate the `decodeJson` SEVERE error** — `decodeJson: class com.hypixel.hytale.codec.DocumentContainingCodec` appears at boot in both logs. Find which asset file is malformed. This could be corrupting the asset index sent to clients.
6. **Reduce active plugins temporarily** — disable plugins one by one and have an affected player test each configuration.

### Information gathering

7. **Collect from affected players**:
   - GPU model and driver version (Intel integrated GPUs are most suspect)
   - Whether the issue exists on other Hytale modded servers
   - Whether it started at a specific date (correlate with a plugin update)
   - Screenshot of the black textures (to confirm ALL textures vs. specific ones)
   - Their `Player Options` loading time (if they have long loading screens >30s, that correlates)

8. **Check Hytale community** — search Hytale forums/Discord for similar reports with high asset pack counts. If other servers with 1000+ duplicate assets see this, it's an engine limitation.

---

## Cross-Reference

See also `docs/BLACK_ASSET_RENDER_INVESTIGATION.md` for a complementary investigation focusing on:
- Stale client asset caches due to frozen `Version: "1.0.0"` across all module manifests
- `MultipleHUD` document-not-found crashes (confirmed in server logs)
- HUD update timing: server sends `#TopBar.Visible` and `#CrystalLabel.Text` before the client has the HUD document attached
- Cross-module texture dependencies (wardrobe UI referencing textures from parkour/purge modules)

## Files Referenced

| File | Relevance |
|------|-----------|
| `run/mods/` | Mod JARs including duplicate Wardrobe mods |
| `run/logs/2026-03-21_00-44-10_server.log` | Duplicate asset count, skipped packs, icon key warnings |
| `hyvexa-*/src/main/resources/manifest.json` | All 6 plugins declare `IncludesAssetPack: true` |
| `hyvexa-core/.../CosmeticManager.java` | Cosmetic effect application with race condition |
| `hyvexa-core/.../CosmeticConfigLoader.java` | Null iconKey warnings |
| `hyvexa-wardrobe/.../WardrobeShopUiUtils.java` | AssetPath fallback when icon is null |
