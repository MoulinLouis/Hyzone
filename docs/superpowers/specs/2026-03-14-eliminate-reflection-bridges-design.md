# Eliminate Unnecessary Reflection Bridges (TECH_DEBT 2.6)

## Problem

5 files use reflection to call across the parkour/runorfall module boundary:

| File | Module | Reflection Target |
|------|--------|-------------------|
| `LeaderboardInteraction` | parkour | `RunOrFallLeaderboardPage` constructor + `RunOrFallPlugin.getStatsStore()` |
| `StatsInteraction` | parkour | `RunOrFallStatsPage` constructor + `RunOrFallPlugin.getStatsStore()` |
| `ToggleFlyInteraction` | parkour | `RunOrFallPlugin.getGameManager().leaveLobby(UUID, boolean)` |
| `RunOrFallJoinBridgeInteraction` | parkour | `RunOrFallPlugin.getGameManager().joinLobby(UUID, World)` |
| `RunOrFallFeatherBridge` | runorfall | `FeatherStore.getInstance()` + 4 methods |

Parkour and runorfall both depend on `hyvexa-core` but not on each other, so direct imports are impossible. However, `RunOrFallFeatherBridge` reflects into `FeatherStore` which is already in core — no bridge needed at all.

## Solution

### Part 1: RunOrFallFeatherBridge — direct FeatherStore calls

`FeatherStore` is a public singleton in `hyvexa-core`. Runorfall already has `implementation: hyvexa-core` in its build.gradle. Replace all reflection with direct calls.

**Before** (~130 LOC): volatile flags, cached Method objects, double-checked locking `ensureResolved()`, reflection `invoke()` on every call.

**After** (~40 LOC): direct `FeatherStore.getInstance()` calls with null guard.

The `RunOrFallFeatherBridge` class is retained as a thin facade — 6 call sites across `HyvexaRunOrFallPlugin` and `RunOrFallGameManager` reference it by name.

**Return type note:** `RunOrFallFeatherBridge.addFeathers()` returns `boolean` (success/failure), while `FeatherStore.addFeathers()` returns `long` (new balance). The simplified bridge wraps the call: returns `true` if the call completes without exception, `false` on exception — preserving the existing contract.

Changes:
- Delete: `resolved`, `available`, `featherStoreInstance`, all `Method` fields, `ensureResolved()`
- Delete: all `import java.lang.reflect.*`
- Add: `import io.hyvexa.core.economy.FeatherStore`
- Each method body becomes a direct call, with try-catch on `addFeathers` to preserve boolean return

### Part 2: 4 parkour interactions — GameModeBridge handlers

Reuse the existing `GameModeBridge` registry pattern. Its `InteractionHandler` signature `(Ref<EntityStore>, boolean, float, InteractionType, InteractionContext)` provides all the context needed — handlers extract Player, PlayerRef, UUID, and World from the ref/store internally.

#### GameModeBridge changes

Add 4 key constants (no signature or structural changes):

```java
public static final String RUNORFALL_OPEN_LEADERBOARD = "runorfall:open_leaderboard";
public static final String RUNORFALL_OPEN_STATS = "runorfall:open_stats";
public static final String RUNORFALL_JOIN_LOBBY = "runorfall:join_lobby";
public static final String RUNORFALL_LEAVE_LOBBY = "runorfall:leave_lobby";
```

#### Runorfall side — register handlers on startup

In `HyvexaRunOrFallPlugin` (or a dedicated registration method called from `onEnable`), register 4 handlers:

1. **`RUNORFALL_OPEN_LEADERBOARD`**: Extract Player + PlayerRef from store. Create `RunOrFallLeaderboardPage(playerRef, statsStore)`. Call `player.getPageManager().openCustomPage()`.

2. **`RUNORFALL_OPEN_STATS`**: Same pattern with `RunOrFallStatsPage`.

3. **`RUNORFALL_JOIN_LOBBY`**: Extract PlayerRef UUID and World from store. Dispatch `gameManager.joinLobby(uuid, world)` via `CompletableFuture.runAsync(..., world)`.

4. **`RUNORFALL_LEAVE_LOBBY`**: Extract PlayerRef UUID and World from store. Dispatch `gameManager.leaveLobby(uuid, true)` via `CompletableFuture.runAsync(..., world)`.

**Threading:** The handler is responsible for dispatching to the world thread. The parkour-side `GameModeBridge.invoke()` call is synchronous — handlers for join/leave wrap their logic in `CompletableFuture.runAsync(..., world)` internally.

#### Parkour side — replace reflection with GameModeBridge.invoke()

Each interaction file replaces its `tryOpenRunOrFall*()` or reflection block with a single call:

```java
if (ModeGate.isRunOrFallWorld(world)) {
    GameModeBridge.invoke(GameModeBridge.RUNORFALL_OPEN_LEADERBOARD,
        ref, firstRun, time, type, interactionContext);
    return;
}
```

Remove from each file:
- `Class.forName` / `getMethod` / `invoke` reflection blocks
- `import java.lang.reflect.*`
- Private helper methods (`tryOpenRunOrFallLeaderboard`, `tryOpenRunOrFallStats`, `leaveRunOrFallLobby`, `joinRunOrFallLobby`)
- `RUN_OR_FALL_PLUGIN_CLASS` and `RUN_OR_FALL_*_PAGE_CLASS` constants

Add to each file:
- `import io.hyvexa.core.bridge.GameModeBridge`

### Part 3: TECH_DEBT.md updates

- Mark 1.1 as done (already uses `GameModeBridge.invoke()` — just doc not updated)
- Mark 2.6 as done

## Files Changed

| File | Change |
|------|--------|
| `hyvexa-core/.../bridge/GameModeBridge.java` | Add 4 key constants |
| `hyvexa-runorfall/.../util/RunOrFallFeatherBridge.java` | Replace reflection with direct FeatherStore calls |
| `hyvexa-runorfall/.../HyvexaRunOrFallPlugin.java` | Register 4 GameModeBridge handlers |
| `hyvexa-parkour/.../interaction/LeaderboardInteraction.java` | Replace reflection with GameModeBridge.invoke() |
| `hyvexa-parkour/.../interaction/StatsInteraction.java` | Replace reflection with GameModeBridge.invoke() |
| `hyvexa-parkour/.../interaction/ToggleFlyInteraction.java` | Replace reflection with GameModeBridge.invoke() |
| `hyvexa-parkour/.../interaction/RunOrFallJoinBridgeInteraction.java` | Replace reflection with GameModeBridge.invoke() |
| `docs/TECH_DEBT.md` | Mark 1.1 and 2.6 as done |

## Net impact

- ~200 LOC of reflection deleted
- 0 new files, 0 new interfaces
- All 5 reflection bridges eliminated
- Graceful degradation preserved (GameModeBridge logs warning if handler not registered)
