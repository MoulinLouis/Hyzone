# Black Asset Rendering Investigation

## Scope

Investigated likely causes for reports where some players see images as black rectangles and HUD/UI art as black shapes while text still renders.

This report focuses on causes that are visible from this repository and the local runtime logs. It does not claim a single proven root cause, but there are several strong candidates.

## Production Log Confirmation

I re-checked three real production logs:

- `docs/prod_logs/2026-03-18_19-38-11_server.log`
- `docs/prod_logs/2026-03-20_12-29-28_server.log`
- `docs/prod_logs/2026-03-22_14-20-29_server.log`

Important distinction:

- The production logs do not explicitly log "textures rendered black".
- They do confirm that real players are hitting Custom UI document/selector mismatches in production.
- They also confirm the same mixed asset-pack environment and `MultipleHUD` version mismatch seen locally.

Most important production finding:

- Three real player crashes in production were caused by:
  - `Selected element in CustomUI command was not found. Selector: #LeaderboardCards[0] #Completion.Text`
- Seen at:
  - `docs/prod_logs/2026-03-18_19-38-11_server.log`
  - `docs/prod_logs/2026-03-20_12-29-28_server.log`

This maps to a concrete code bug:

- `hyvexa-parkour/src/main/java/io/hyvexa/duel/ui/DuelLeaderboardPage.java`
  - appends `Pages/Parkour_LeaderboardEntry.ui`
  - then writes `#Completion.Text`
- `hyvexa-parkour/src/main/resources/Common/UI/Custom/Pages/Parkour_LeaderboardEntry.ui`
  - does not contain `#Completion`

That exact bug is unrelated to the "all assets are black" symptom by itself, but it proves that production currently has real UI document mismatch bugs, so plugin-side UI issues are not theoretical.

Latest production observation for the still-affected player `Onakar` (`32727072-beb0-428f-8798-c9b3d3cb2544`):

- The `1.1.0` deployment is definitely live:
  - `HyvexaParkour-1.1.0.jar`
  - `HyvexaRunOrFall-1.1.0.jar`
  - `HyvexaHub-1.1.0.jar`
  - `HyvexaVotifier-1.1.0.jar`
  - `HyvexaWardrobe-1.1.0.jar`
  - `HyvexaParkourAscend-1.1.0.jar`
  - `HyvexaPurge-1.1.0.jar`
- Onakar successfully completes setup and receives assets:
  - `Request Assets took 441ms`
  - `Send Common Assets took 582ms`
  - `Send Config Assets took 85ms`
  - `Player Options took 8sec 787ms`
- Onakar joins `Parkour`, stays connected, and leaves normally:
  - `Disconnect - Player leave`
- There is no server-side crash, no `CustomUI` selector failure, and no missing document error for that session.

What this changes:

- The Hyvexa `1.1.0` version bump did deploy correctly.
- A stale Hyvexa client asset cache is therefore less convincing as the sole remaining explanation for Onakar specifically.
- The server does not currently see a concrete UI/document failure for his session.
- The remaining stronger suspects become:
  - third-party asset packs that were not version-bumped and still target older server versions
  - client-side rendering/graphics issues that do not surface as server log errors
  - a transport/setup issue that is subtle enough to let login succeed but still leave the client in a bad visual state

One weak but interesting signal:

- In `docs/prod_logs/2026-03-22_14-20-29_server.log`, Onakar has the slowest `Player Options took ...` time in that short sample, and also the slowest `Send Common Assets took ...` time.
- That does not prove the black-render issue, but it suggests his client may be slower than others at processing the login/setup phase.

One thing that does **not** look useful as a differentiator:

- `Added future for ClientReady packet?` and the paired `ClientReady@9da7` / `ClientReady@9da1` sequence appear for many players in production, not just Onakar.
- Based on the logs available so far, that looks like a noisy global pattern rather than a player-specific clue.

## Most Likely Causes

### 1. Stale or out-of-sync client asset packs

This is the strongest explanation I found.

Why:

- All Hyvexa module manifests still ship with `Version: "1.0.0"`:
  - `hyvexa-parkour/src/main/resources/manifest.json`
  - `hyvexa-parkour-ascend/src/main/resources/manifest.json`
  - `hyvexa-hub/src/main/resources/manifest.json`
  - `hyvexa-purge/src/main/resources/manifest.json`
  - `hyvexa-runorfall/src/main/resources/manifest.json`
  - `hyvexa-wardrobe/src/main/resources/manifest.json`
  - `hyvexa-votifier/src/main/resources/manifest.json`
- The Gradle project version is also fixed at `1.0.0` in `gradle.properties`.
- The staged runtime JAR names are fixed as `HyvexaParkour-1.0.0.jar`, `HyvexaParkourAscend-1.0.0.jar`, etc.

Why that matters:

- If clients cache mod asset packs by plugin identity and version, then UI/layout/texture changes can be deployed server-side without forcing a clean client asset refresh.
- That matches the observed pattern very well:
  - only some players are affected
  - reinstalling the game does not reliably solve it
  - the server can send UI commands for selectors/documents that exist in current source, while some clients behave as if they do not exist

Concrete evidence:

- Current source contains the selectors/documents below, but logs still show clients missing them:
  - `Ascend_RunHud.ui` contains `#TopBar`
  - `Ascend_MineHud.ui` contains `#CrystalLabel`
  - `MultipleHUD-1.0.6.jar` contains `Common/UI/Custom/HUD/MultipleHUD.ui`
- Runtime logs show client crashes caused by those assets/selectors being missing at runtime:
  - `run/logs/2026-03-19_18-57-41_server.log`
    - `Crash - Selected element in CustomUI command was not found. Selector: #CrystalLabel.Text`
  - `run/logs/2026-03-19_21-07-14_server.log`
    - `Crash - Selected element in CustomUI command was not found. Selector: #TopBar.Visible`
  - `run/logs/2026-03-20_16-41-06_server.log`
    - `Crash - Could not find document HUD/MultipleHUD.ui for Custom UI Append command`

Assessment:

- High confidence that stale client-side asset/UI caches were a real risk before the `1.1.0` bump.
- Lower confidence that this is the sole remaining cause for Onakar after the latest production deployment.

### 2. `MultipleHUD` conflict or asset-sync failure

Hyvexa integrates heavily with `MultipleHUD` through `io.hyvexa.common.util.MultiHudBridge`.

Relevant files:

- `hyvexa-core/src/main/java/io/hyvexa/common/util/MultiHudBridge.java`
- `hyvexa-parkour/src/main/java/io/hyvexa/manager/HudManager.java`
- `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/hud/AscendHudManager.java`
- `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/mine/hud/MineHudManager.java`
- `hyvexa-hub/src/main/java/io/hyvexa/hub/HyvexaHubPlugin.java`
- `hyvexa-runorfall/src/main/java/io/hyvexa/runorfall/HyvexaRunOrFallPlugin.java`
- `hyvexa-purge/src/main/java/io/hyvexa/purge/hud/PurgeHudManager.java`

Why it is suspicious:

- Hyvexa attaches custom HUDs in multiple modules and depends on `MultipleHUD` to append/composite them.
- Production logs confirm `MultipleHUD` is enabled on the real server and also targeting a different server version:
  - `docs/prod_logs/2026-03-18_19-38-11_server.log`
  - `docs/prod_logs/2026-03-20_12-29-28_server.log`
- A real log entry shows a client crashing because the `MultipleHUD` UI document could not be found:
  - `run/logs/2026-03-20_16-41-06_server.log`
  - `Crash - Could not find document HUD/MultipleHUD.ui for Custom UI Append command`
- The installed `MultipleHUD` plugin is a third-party runtime mod and the logs report version mismatch warnings for it.

Important detail:

- The JAR does contain `Common/UI/Custom/HUD/MultipleHUD.ui`, so this is not a simple "file is absent in the jar" issue.
- That points more toward one of these:
  - client did not receive the right asset pack
  - stale cached `MultipleHUD` assets on the client
  - a timing/composition issue while appending HUD documents during login/world changes

Assessment:

- High confidence that `MultipleHUD` is a major contributor, especially for HUD-only black/missing UI states.
- Higher priority now that the Hyvexa cache-bust has already been deployed and the issue still persists for at least one player.

### 3. Ascend HUD state mismatch or update timing bug

There is direct evidence that Hyvexa sometimes sends UI updates to elements that are not present in the active document.

Relevant code:

- `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/hud/AscendHudManager.java`
  - `setMineMode(...)` updates `#TopBar.Visible`
- `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/mine/hud/MineHudManager.java`
  - `updateCrystals(...)` updates `#CrystalLabel.Text`
- `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ParkourAscendPlugin.java`
  - on `PlayerReadyEvent`, Hyvexa attaches the normal Ascend HUD, may immediately enable mine mode, and may then attach the mine HUD

Why this matters:

- The logs show exact crashes for `#TopBar.Visible` and `#CrystalLabel.Text`.
- Those selectors do exist in the current UI files, so the failure is likely not a typo in the current files.
- The likely failure mode is that Hyvexa is updating a HUD document that the client has not attached yet, no longer has attached, or has from a stale cached version.

Assessment:

- High confidence this is a real plugin-side cause for some client UI failures.
- It does not fully explain world textures turning black, but it does explain broken HUD/UI behavior.

### 4. Production-confirmed leaderboard UI mismatch bug

There is at least one confirmed production bug where Hyvexa sends updates to a selector that does not exist in the appended UI entry.

Concrete evidence:

- Production crashes:
  - `docs/prod_logs/2026-03-18_19-38-11_server.log`
  - `docs/prod_logs/2026-03-20_12-29-28_server.log`
  - all show:
    - `Selected element in CustomUI command was not found. Selector: #LeaderboardCards[0] #Completion.Text`
- Source bug:
  - `hyvexa-parkour/src/main/java/io/hyvexa/duel/ui/DuelLeaderboardPage.java`
    - appends `Pages/Parkour_LeaderboardEntry.ui`
    - writes `#Completion.Text`
  - `hyvexa-parkour/src/main/resources/Common/UI/Custom/Pages/Parkour_LeaderboardEntry.ui`
    - has `#Rank`, `#PlayerName`, `#BronzeCount`, `#SilverCount`, `#GoldCount`, `#EmeraldCount`, `#InsaneCount`, `#TotalScore`
    - does not have `#Completion`

Why this matters:

- This is not just "a suspicious pattern". It is a proven production UI mismatch.
- It does not directly explain black world textures, but it increases confidence that some client-side visual breakage may come from Hyvexa sending UI updates against the wrong document shape.

Assessment:

- High confidence as a real production UI bug.
- Medium confidence as a contributor to the reported black-asset symptom specifically.

## Additional Plausible Causes

### 5. Cross-module UI texture dependencies in `hyvexa-wardrobe`

The wardrobe UI pages reference textures that are not present inside the wardrobe module's own `Common/UI/Custom/Textures/` directory.

Examples:

- `hyvexa-wardrobe/src/main/resources/Common/UI/Custom/Pages/Shop.ui`
  - references `../Textures/vexa.png`
  - references `../Textures/feather.png`
- `hyvexa-wardrobe/src/main/resources/Common/UI/Custom/Pages/Purge_SkinShopEntry.ui`
  - references `AK47_*_Preview.png`

But those files are actually provided by other modules:

- `vexa.png` exists in parkour/hub/ascend/purge/runorfall
- `feather.png` exists in parkour/runorfall
- `AK47_*_Preview.png` exists in `hyvexa-purge`

Why this is risky:

- `hyvexa-wardrobe` has no explicit manifest dependency on `hyvexa-purge` or `hyvexa-parkour`, yet its UI depends on those assets being globally available.
- If asset merge order, sync state, or per-client cache state differs, those images can resolve incorrectly or fail to resolve at all, which can appear as black rectangles.

Assessment:

- Medium confidence.
- Strongly suggests brittle asset-pack coupling even if it is not the primary cause of the global issue.

### 6. Asset path collisions between Hyvexa modules

I found at least one duplicated global resource path with different file contents:

- `Common/UI/Custom/Textures/lock.png`
  - present in `hyvexa-parkour`
  - present in `hyvexa-parkour-ascend`
  - present in `hyvexa-runorfall`
  - the `hyvexa-parkour` copy differs from the other two

Why this is risky:

- Hyvexa modules all ship asset packs into the same global `Common/...` namespace.
- If two modules provide different files at the same path, final behavior depends on pack ordering/override rules.
- This is unlikely to make every asset black by itself, but it proves the asset namespace is not isolated cleanly.

Assessment:

- Medium confidence as a contributing factor.
- Lower confidence as the main cause of the "everything is black" symptom.

### 7. Third-party mods and asset packs target older/different server versions

Runtime logs show compatibility warnings for several installed mods and asset packs:

- `MultipleHUD`
- `HygunsPlugin`
- `NPC Dialog`
- `Pets+`
- `Wardrobe`
- asset-pack warnings from `AssetModule`

Concrete evidence:

- `run/logs/2026-03-19_20-03-24_server.log`
  - warns that one or more plugins target a different server version
  - warns that one or more asset packs target an older server version

Why this matters:

- This server is running a mixed plugin stack with global asset packs from Hyvexa plus third-party mods.
- If only some players are failing, version-sensitive client asset loading is a plausible trigger.
- `Wardrobe`, `Hyguns`, `PetsPlus`, `Hylograms`, and `MultipleHUD` all increase the chance of global asset conflicts or stale caches.
- Production also adds more runtime surface area than local dev:
  - `HyFixes`
  - `Optimizer`
  - `SimpleProtect`
  - `Tebex-Hytale`
  - `Portal Hub`
  - `HGAME - Player Count`
  - `HyQuery`

Assessment:

- Medium confidence.
- Stronger when combined with Cause 1 and Cause 2.
- For the remaining unresolved cases after `1.1.0`, this category is now more suspicious than before.

### 8. Asset transfer/network instability during player setup

The production logs show repeated asset-request/setup activity and many QUIC protocol errors during connection handling.

Concrete evidence:

- `docs/prod_logs/2026-03-20_12-29-28_server.log`
  - many `Request Assets took ...`
  - many `Send Common Assets took ...`
  - many `Send Config Assets took ...`
  - repeated `QuicException: QuicTransportError{code=10, name='PROTOCOL_VIOLATION'}: QUICHE_ERR_INVALID_PACKET`

Why this matters:

- This does not prove incomplete asset delivery by itself.
- It does show that production player setup is happening under noisier transport conditions than local dev.
- If the actual black-render reports are concentrated among players with unstable connections, this becomes more plausible as an amplifier for stale/incomplete asset state.

Assessment:

- Low to medium confidence.
- Better treated as a contributing condition than a root cause.
- For Onakar specifically, this remains only a weak signal because his session completed successfully.

## What I Did Not Find

These findings make some other explanations less likely:

- I did not find obvious Hyvexa UI parser-killer mistakes such as:
  - `LabelAlignment: Left/Right`
  - underscores in UI element IDs
- I did not find duplicate UI page paths across Hyvexa modules.
- I did not find evidence that `MultipleHUD.ui` is missing from the installed `MultipleHUD` jar. The file is present.
- The production logs I checked did not explicitly log black-texture rendering or shader/material failures.

## Practical Conclusion

The most likely real root cause is not "one bad PNG". It is the combination of:

1. client asset packs not being forced to refresh because Hyvexa versions stay at `1.0.0`
2. `MultipleHUD` and other third-party packs operating on older/different server versions
3. Hyvexa sending HUD updates that assume the expected UI document is already attached and current
4. at least one confirmed production UI mismatch bug in `DuelLeaderboardPage`
5. cross-module asset coupling inside the Hyvexa UI packs

If players are seeing black rectangles and missing UI art, the first things I would suspect are stale client mod caches and `MultipleHUD`/Ascend HUD composition, not database or gameplay logic.

After the `1.1.0` rollout, the unresolved cases look different:

1. stale Hyvexa cache alone is no longer a sufficient explanation
2. third-party packs that still target older versions (`MultipleHUD`, `Wardrobe`, `Hyguns`) become relatively more suspicious
3. the remaining issue may now be primarily client-side and invisible to server logs

The next high-value test is not another log scrape. It is a controlled reproduction with Onakar on a staging server where third-party asset-pack mods are removed in batches, starting with:

1. `MultipleHUD`
2. `Wardrobe`
3. `Hyguns`

If the issue disappears in that environment, the problem is probably outside the Hyvexa `1.1.0` asset packs themselves.
