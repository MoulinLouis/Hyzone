# Code Cleanup — hyvexa-core Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement all 17 actionable findings from `docs/audits/CODE_CLEANUP_CORE.md` (CORE-18 deferred per audit).

**Architecture:** Safe, incremental refactoring — extract shared abstractions, eliminate dead code, add utilities. All public APIs preserved or made backward-compatible. No behavioral changes.

**Tech Stack:** Java 17, Hytale Server API, MySQL (HikariCP)

**Concurrency note:** Other agents may be editing files in purge/wardrobe/runorfall modules concurrently. Minimize cross-module file touches; prefer creating new utility classes in core.

---

## Group 1: Trivial Cleanups (core-only, no API changes)

### Task 1: CORE-3 — CommandUtils dead code removal

**Files:**
- Modify: `hyvexa-core/src/main/java/io/hyvexa/common/util/CommandUtils.java`

**Step 1: Remove dead branch and redundant method**

In `tokenize()` at line 27, `tokens.length == 0` is unreachable after `split("\\s+")` on a non-empty trimmed string (split always returns >= 1 element). Remove this dead branch.

Delete `getArgs()` method (lines 44-52) and rename `tokenize()` to `getArgs()` so the 20+ callers of `getArgs` keep working. Then add a `tokenize()` that delegates to `getArgs()` for backward compat with the 5 `tokenize` callers.

```java
public static String[] getArgs(CommandContext ctx) {
    String input = ctx.getInputString();
    if (input == null || input.trim().isEmpty()) {
        return new String[0];
    }
    String[] tokens = input.trim().split("\\s+");
    String first = tokens[0];
    if (first.startsWith("/")) {
        first = first.substring(1);
    }
    String commandName = ctx.getCalledCommand().getName();
    if (first.equalsIgnoreCase(commandName)) {
        if (tokens.length == 1) {
            return new String[0];
        }
        return Arrays.copyOfRange(tokens, 1, tokens.length);
    }
    return tokens;
}

/** @deprecated Use {@link #getArgs(CommandContext)} instead. */
@Deprecated
public static String[] tokenize(CommandContext ctx) {
    return getArgs(ctx);
}
```

**Step 2: Commit**

```
fix: remove dead branch and consolidate CommandUtils methods
```

---

### Task 2: CORE-6 — FormatUtils cleanup (3 issues)

**Files:**
- Modify: `hyvexa-core/src/main/java/io/hyvexa/common/util/FormatUtils.java`

**Step 1: Fix all 3 issues**

1. **Line 123:** `trimmed.isEmpty()` is unreachable after `category.isBlank()` check. Remove lines 122-124 (the `trimmed` variable and its empty check):

```java
public static String normalizeCategory(String category) {
    if (category == null || category.isBlank()) {
        return "Beginner";
    }
    String trimmed = category.trim();
    return trimmed.substring(0, 1).toUpperCase(Locale.ROOT) + trimmed.substring(1);
}
```

2. **Line 154:** `String safeRank = rank` is pointless — `rank` is guaranteed to be `"VexaGod"` at that point. Use `rank` directly:

```java
public static Message getRankMessage(String rank) {
    if (!"VexaGod".equals(rank)) {
        return Message.raw(rank != null ? rank : "").color(getRankColor(rank));
    }
    String[] colors = {
            "#ff4d4d", "#ffa94d", "#ffe66d", "#4cd964",
            "#5ac8fa", "#5e5ce6", "#b76cff"
    };
    Message[] parts = new Message[rank.length()];
    for (int i = 0; i < rank.length(); i++) {
        String letter = String.valueOf(rank.charAt(i));
        parts[i] = Message.raw(letter).color(colors[i % colors.length]);
    }
    return Message.join(parts);
}
```

3. **Null guard:** The audit mentions `rank` could be null despite partial handling. However, `!"VexaGod".equals(rank)` already handles null correctly (returns `true` when rank is null, taking the early return path). The existing null-safe `rank != null ? rank : ""` in the early return handles it. No change needed — the code is already safe.

**Step 2: Commit**

```
fix: remove unreachable code in FormatUtils
```

---

### Task 3: CORE-15 — DatabaseManager exception documentation

**Files:**
- Modify: `hyvexa-core/src/main/java/io/hyvexa/core/db/DatabaseManager.java`

**Step 1: Add comment documenting intentional exception strategy**

At `initPool()` method (line 93), add a brief comment before the catch block explaining the init-is-fatal pattern:

```java
        } catch (Exception e) {
            // Intentional: pool init failure is fatal — wrapped as RuntimeException to
            // prevent the plugin from starting with a broken database connection.
            LOGGER.atSevere().withCause(e).log("Failed to initialize database connection pool");
            throw new RuntimeException("Database initialization failed", e);
        }
```

**Step 2: Commit**

```
docs: document intentional exception strategy in DatabaseManager.initPool
```

---

### Task 4: CORE-16 — CosmeticStore NONE_EQUIPPED documentation

**Files:**
- Modify: `hyvexa-core/src/main/java/io/hyvexa/core/cosmetic/CosmeticStore.java`

**Step 1: Document the sentinel convention**

Expand the existing comment on line 31-32:

```java
/**
 * Sentinel: cache contains null = not yet loaded, "" = loaded but nothing equipped,
 * "some-id" = loaded with equipped cosmetic. This avoids re-loading from DB
 * when a player genuinely has no cosmetic equipped.
 */
private static final String NONE_EQUIPPED = "";
```

**Step 2: Commit**

```
docs: document NONE_EQUIPPED sentinel convention in CosmeticStore
```

---

### Task 5: CORE-17 — Trim verbose Javadoc on AscendWhitelistManager

**Files:**
- Modify: `hyvexa-core/src/main/java/io/hyvexa/common/whitelist/AscendWhitelistManager.java`

**Step 1: Remove redundant Javadoc**

Remove all method-level Javadoc that simply restates the method name. Keep only the class-level doc and any Javadoc that adds non-obvious info.

Methods to strip Javadoc from (they are self-documenting):
- `add(String username)` — remove lines 39-43
- `remove(String username)` — remove lines 57-61
- `contains(String username)` — remove lines 75-79
- `list()` — remove lines 87-90
- `isEnabled()` — remove lines 95-98
- `isPublicMode()` — remove lines 114-118

Keep Javadoc on `setEnabled()` (lines 103-108) and `setPublicMode()` (lines 123-127) — they document non-obvious behavior (OPs-only default, public mode semantics).

**Step 2: Commit**

```
cleanup: trim redundant Javadoc from AscendWhitelistManager
```

---

## Group 2: Core Refactoring (same public APIs)

### Task 6: CORE-1 — Extract AbstractTrailManager from TrailManager/ModelParticleTrailManager

**Files:**
- Create: `hyvexa-core/src/main/java/io/hyvexa/core/trail/AbstractTrailManager.java`
- Modify: `hyvexa-core/src/main/java/io/hyvexa/core/trail/TrailManager.java`
- Modify: `hyvexa-core/src/main/java/io/hyvexa/core/trail/ModelParticleTrailManager.java`

**Context:** Both managers share ~200 lines of identical scheduling, tick loop, viewer collection, and movement detection code. They differ only in: (1) packet construction, (2) TrailState fields (ModelParticle adds offsets), (3) stopTrail behavior (ModelParticle sends clear packet).

**Callers:** `TrailManager.getInstance()` used in `CosmeticManager` (4 calls) and `HyvexaPlugin` (1 call). `ModelParticleTrailManager.getInstance()` used in `CosmeticManager` (4 calls). All callers use the concrete class name — no changes needed to callers.

**Step 1: Create AbstractTrailManager**

```java
package io.hyvexa.core.trail;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Base class for trail managers. Provides shared scheduling, tick loop,
 * viewer collection, and movement detection. Subclasses provide packet
 * construction and trail-specific state.
 */
public abstract class AbstractTrailManager<TState extends AbstractTrailManager.BaseTrailState> {

    private static final long SCHEDULER_INTERVAL_MS = 50L;
    protected static final double MOVEMENT_THRESHOLD_SQ = 0.0009d;

    protected final ConcurrentHashMap<UUID, TState> activeTrails = new ConcurrentHashMap<>();
    private volatile ScheduledFuture<?> tickTask;

    protected abstract HytaleLogger logger();

    public boolean hasTrail(UUID playerId) {
        return activeTrails.containsKey(playerId);
    }

    public void stopTrail(UUID playerId) {
        activeTrails.remove(playerId);
    }

    public void shutdown() {
        ScheduledFuture<?> task = tickTask;
        if (task != null) {
            task.cancel(false);
            tickTask = null;
        }
        activeTrails.clear();
    }

    protected synchronized void ensureTickTask() {
        if (tickTask != null && !tickTask.isCancelled() && !tickTask.isDone()) {
            return;
        }
        tickTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(
                this::tickTrails, 0L, SCHEDULER_INTERVAL_MS, TimeUnit.MILLISECONDS
        );
    }

    private void tickTrails() {
        try {
            if (activeTrails.isEmpty()) {
                return;
            }
            long now = System.currentTimeMillis();
            Map<World, List<PlayerRef>> viewersByWorld = collectWorldViewers();
            for (TState state : activeTrails.values()) {
                if (state == null || now < state.nextEmissionAtMs) {
                    continue;
                }
                state.nextEmissionAtMs = now + Math.max(1L, state.intervalMs);
                tickTrail(state, viewersByWorld.getOrDefault(state.world, List.of()));
            }
        } catch (Exception e) {
            logger().atWarning().withCause(e).log("Trail scheduler tick failed");
        }
    }

    protected Map<World, List<PlayerRef>> collectWorldViewers() {
        Map<World, List<PlayerRef>> viewersByWorld = new HashMap<>();
        for (PlayerRef viewer : Universe.get().getPlayers()) {
            if (viewer == null || !viewer.isValid()) {
                continue;
            }
            Ref<EntityStore> viewerRef = viewer.getReference();
            if (viewerRef == null || !viewerRef.isValid()) {
                continue;
            }
            Store<EntityStore> viewerStore = viewerRef.getStore();
            World viewerWorld = viewerStore.getExternalData().getWorld();
            if (viewerWorld == null) {
                continue;
            }
            viewersByWorld.computeIfAbsent(viewerWorld, ignored -> new ArrayList<>()).add(viewer);
        }
        return viewersByWorld;
    }

    private void tickTrail(TState state, List<PlayerRef> viewers) {
        try {
            if (state.ref == null || !state.ref.isValid()) {
                stopTrail(state.playerId);
                return;
            }
            state.world.execute(() -> emitTrailOnWorldThread(state, viewers));
        } catch (Exception e) {
            logger().atWarning().withCause(e).log("Trail schedule error for " + state.playerId);
        }
    }

    private void emitTrailOnWorldThread(TState state, List<PlayerRef> viewers) {
        try {
            if (activeTrails.get(state.playerId) != state) {
                return;
            }
            if (!state.ref.isValid()) {
                stopTrail(state.playerId);
                return;
            }
            World currentWorld = state.store.getExternalData().getWorld();
            if (currentWorld != state.world) {
                stopTrail(state.playerId);
                return;
            }
            TransformComponent transform = state.store.getComponent(
                    state.ref, TransformComponent.getComponentType());
            if (transform == null || transform.getPosition() == null) {
                return;
            }
            var pos = transform.getPosition();
            if (Double.isNaN(state.lastPos[0])) {
                state.lastPos[0] = pos.getX();
                state.lastPos[1] = pos.getY();
                state.lastPos[2] = pos.getZ();
                return;
            }
            double dx = pos.getX() - state.lastPos[0];
            double dy = pos.getY() - state.lastPos[1];
            double dz = pos.getZ() - state.lastPos[2];
            double distSq = (dx * dx) + (dy * dy) + (dz * dz);
            state.lastPos[0] = pos.getX();
            state.lastPos[1] = pos.getY();
            state.lastPos[2] = pos.getZ();
            if (distSq <= MOVEMENT_THRESHOLD_SQ) {
                return;
            }
            emitPacket(state, pos, viewers);
        } catch (Exception e) {
            logger().atWarning().withCause(e).log("Trail tick error for " + state.playerId);
        }
    }

    /**
     * Construct and broadcast the trail packet. Called on the world thread after
     * movement detection passes.
     */
    protected abstract void emitPacket(TState state,
                                        com.hypixel.hytale.math.vector.Vector3d position,
                                        List<PlayerRef> viewers);

    protected void broadcastToViewers(World world, List<PlayerRef> viewers, Object packet) {
        for (PlayerRef viewer : viewers) {
            if (viewer == null || !viewer.isValid()) {
                continue;
            }
            Ref<EntityStore> viewerRef = viewer.getReference();
            if (viewerRef == null || !viewerRef.isValid()) {
                continue;
            }
            Store<EntityStore> viewerStore = viewerRef.getStore();
            if (viewerStore.getExternalData().getWorld() != world) {
                continue;
            }
            PacketHandler packetHandler = viewer.getPacketHandler();
            if (packetHandler == null) {
                continue;
            }
            packetHandler.writeNoCache(packet);
        }
    }

    public static class BaseTrailState {
        public final UUID playerId;
        public final Ref<EntityStore> ref;
        public final Store<EntityStore> store;
        public final World world;
        public final String particleId;
        public final float scale;
        public final long intervalMs;
        public final double[] lastPos = new double[]{Double.NaN, 0d, 0d};
        public volatile long nextEmissionAtMs;

        protected BaseTrailState(UUID playerId, Ref<EntityStore> ref, Store<EntityStore> store,
                                  World world, String particleId, float scale, long intervalMs) {
            this.playerId = playerId;
            this.ref = ref;
            this.store = store;
            this.world = world;
            this.particleId = particleId;
            this.scale = scale;
            this.intervalMs = intervalMs;
            this.nextEmissionAtMs = 0L;
        }
    }
}
```

**Step 2: Refactor TrailManager to extend AbstractTrailManager**

Replace TrailManager with a thin subclass. Keep the INSTANCE, getInstance(), and startTrail() signatures identical.

```java
package io.hyvexa.core.trail;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.packets.world.SpawnParticleSystem;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;
import java.util.UUID;

public class TrailManager extends AbstractTrailManager<AbstractTrailManager.BaseTrailState> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final TrailManager INSTANCE = new TrailManager();

    private TrailManager() {}

    public static TrailManager getInstance() {
        return INSTANCE;
    }

    @Override
    protected HytaleLogger logger() {
        return LOGGER;
    }

    public void startTrail(UUID playerId, Ref<EntityStore> ref, Store<EntityStore> store,
                           World world, String particleId, float scale, long intervalMs) {
        stopTrail(playerId);
        if (playerId == null || ref == null || store == null || world == null
                || particleId == null || particleId.isBlank()) {
            return;
        }
        activeTrails.put(playerId, new BaseTrailState(
                playerId, ref, store, world, particleId, scale, intervalMs));
        ensureTickTask();
    }

    @Override
    protected void emitPacket(BaseTrailState state,
                               com.hypixel.hytale.math.vector.Vector3d pos,
                               List<PlayerRef> viewers) {
        SpawnParticleSystem packet = new SpawnParticleSystem(
                state.particleId,
                new Position(pos.getX(), pos.getY() + 0.1, pos.getZ()),
                new Direction(0f, 0f, 0f),
                state.scale,
                new Color((byte) 255, (byte) 255, (byte) 255)
        );
        broadcastToViewers(state.world, viewers, packet);
    }
}
```

**Step 3: Refactor ModelParticleTrailManager to extend AbstractTrailManager**

Similar thin subclass. Keep INSTANCE, getInstance(), startTrail(), sendClearPacket(). Override stopTrail to send clear packet.

```java
package io.hyvexa.core.trail;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.EntityPart;
import com.hypixel.hytale.protocol.ModelParticle;
import com.hypixel.hytale.protocol.Vector3f;
import com.hypixel.hytale.protocol.packets.entities.SpawnModelParticles;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;
import java.util.UUID;

public class ModelParticleTrailManager extends AbstractTrailManager<ModelParticleTrailManager.ModelTrailState> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final ModelParticleTrailManager INSTANCE = new ModelParticleTrailManager();

    private ModelParticleTrailManager() {}

    public static ModelParticleTrailManager getInstance() {
        return INSTANCE;
    }

    @Override
    protected HytaleLogger logger() {
        return LOGGER;
    }

    public void startTrail(UUID playerId, Ref<EntityStore> ref, Store<EntityStore> store, World world,
                           String particleId, float scale, long intervalMs,
                           float xOffset, float yOffset, float zOffset) {
        stopTrail(playerId);
        if (playerId == null || ref == null || store == null || world == null
                || particleId == null || particleId.isBlank()) {
            return;
        }
        activeTrails.put(playerId, new ModelTrailState(
                playerId, ref, store, world, particleId, scale, intervalMs,
                xOffset, yOffset, zOffset));
        ensureTickTask();
    }

    @Override
    public void stopTrail(UUID playerId) {
        ModelTrailState removed = activeTrails.remove(playerId);
        sendClearPacket(removed);
    }

    @Override
    protected void emitPacket(ModelTrailState state,
                               com.hypixel.hytale.math.vector.Vector3d position,
                               List<PlayerRef> viewers) {
        Player source = state.store.getComponent(state.ref, Player.getComponentType());
        if (source == null) {
            stopTrail(state.playerId);
            return;
        }
        ModelParticle particle = new ModelParticle(
                state.particleId, state.scale, null, EntityPart.Entity, null,
                new Vector3f(state.xOffset, state.yOffset, state.zOffset), null, false
        );
        SpawnModelParticles packet = new SpawnModelParticles(
                source.getNetworkId(), new ModelParticle[]{particle}
        );
        broadcastToViewers(state.world, viewers, packet);
    }

    private void sendClearPacket(ModelTrailState state) {
        if (state == null || state.ref == null || !state.ref.isValid()) {
            return;
        }
        World world = state.store.getExternalData().getWorld();
        Player source = state.store.getComponent(state.ref, Player.getComponentType());
        if (world == null || source == null) {
            return;
        }
        SpawnModelParticles clearPacket = new SpawnModelParticles(
                source.getNetworkId(), new ModelParticle[0]
        );
        broadcastToViewers(world, collectWorldViewers().getOrDefault(world, List.of()), clearPacket);
    }

    static final class ModelTrailState extends BaseTrailState {
        final float xOffset;
        final float yOffset;
        final float zOffset;

        ModelTrailState(UUID playerId, Ref<EntityStore> ref, Store<EntityStore> store, World world,
                        String particleId, float scale, long intervalMs,
                        float xOffset, float yOffset, float zOffset) {
            super(playerId, ref, store, world, particleId, scale, intervalMs);
            this.xOffset = xOffset;
            this.yOffset = yOffset;
            this.zOffset = zOffset;
        }
    }
}
```

**Step 4: Verify no compile errors by checking all callers**

Callers in `CosmeticManager` use `TrailManager.getInstance().startTrail(...)` and `ModelParticleTrailManager.getInstance().startTrail(...)` — signatures unchanged. No callers reference the inner `TrailState` classes.

**Step 5: Commit**

```
refactor: extract AbstractTrailManager to deduplicate trail scheduling
```

---

### Task 7: CORE-4 — Merge AbstractGhostStore and GhostStore

**Files:**
- Modify: `hyvexa-core/src/main/java/io/hyvexa/common/ghost/AbstractGhostStore.java`
- Delete: `hyvexa-core/src/main/java/io/hyvexa/common/ghost/GhostStore.java`

**Context:** GhostStore is the only subclass of AbstractGhostStore. It overrides 10 abstract methods with trivial one-liners delegating to GhostSample/GhostRecording. AbstractGhostStore already uses these types indirectly through generics.

**Callers:** GhostStore is used in HyvexaPlugin (parkour), ParkourAscendPlugin, GhostRecorder (both modules), GhostNpcManager, RobotManager, StatsPage, MapSelectPage, etc. — about 35 references. All use the concrete `GhostStore` type.

**Step 1: Replace AbstractGhostStore with concrete GhostStore**

Replace the abstract class with a concrete class that uses `GhostSample` and `GhostRecording` directly:

```java
package io.hyvexa.common.ghost;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;

// ... same imports as AbstractGhostStore ...

/**
 * MySQL + in-memory cache for ghost recordings.
 * Configured via constructor params for table name and mode label.
 */
public class GhostStore {

    public static final int MAX_SAMPLES = 12000;

    private final HytaleLogger logger = HytaleLogger.forEnclosingClass();
    private final Map<String, GhostRecording> cache = new ConcurrentHashMap<>();
    private final String tableName;
    private final String modeLabel;

    public GhostStore(String tableName, String modeLabel) {
        this.tableName = tableName;
        this.modeLabel = modeLabel;
    }

    // Move ALL methods from AbstractGhostStore here, replacing:
    //   logger()        → this.logger
    //   tableName()     → this.tableName
    //   modeLabel()     → this.modeLabel
    //   getSamples(r)   → r.getSamples()
    //   getCompletionTimeMs(r) → r.getCompletionTimeMs()
    //   createRecording(s,t)   → new GhostRecording(s, t)
    //   createSample(x,y,z,yaw,ts) → new GhostSample(x, y, z, yaw, ts)
    //   sampleX(s)      → s.x()
    //   sampleY(s)      → s.y()
    //   etc.

    // Public methods remain: syncLoad(), saveRecording(), getRecording(), deleteRecording()
    // Unchanged signatures, unchanged behavior
}
```

Keep the `GhostStore(String tableName, String modeLabel)` constructor signature identical. All 35 callers continue to work. Delete `AbstractGhostStore.java`.

Also remove the `GhostStore.MAX_SAMPLES` re-export (`public static final int MAX_SAMPLES = AbstractGhostStore.MAX_SAMPLES`) since it's now defined directly. If any callers use `AbstractGhostStore.MAX_SAMPLES` (none found), they'd need updating — but no callers do, so safe.

**Step 2: Check for imports of AbstractGhostStore**

Only `GhostStore.java` imports `AbstractGhostStore`. No external references. Safe to delete.

**Step 3: Commit**

```
refactor: merge AbstractGhostStore into GhostStore (single implementation)
```

---

### Task 8: CORE-2 — Extract CachedCurrencyStore from VexaStore/FeatherStore

**Files:**
- Create: `hyvexa-core/src/main/java/io/hyvexa/core/economy/CachedCurrencyStore.java`
- Modify: `hyvexa-core/src/main/java/io/hyvexa/core/economy/VexaStore.java`
- Modify: `hyvexa-core/src/main/java/io/hyvexa/core/economy/FeatherStore.java`

**Context:** Both stores share identical cache management, TTL, modify*, evictPlayer, and refreshFromDatabaseAsync logic (~150 lines each). VexaStore uses version-based stale detection (better), FeatherStore uses timestamp-based (weaker). The abstract class uses VexaStore's version approach for both — this is an improvement, not a breaking change.

**Key difference:** VexaStore has `migratePlayerGemsToVexa()` in its `initialize()`. This stays in VexaStore. Each store's `initialize()`, `loadFromDatabase()`, and `persistToDatabase()` remain store-specific.

**Step 1: Create CachedCurrencyStore abstract class**

```java
package io.hyvexa.core.economy;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import io.hyvexa.core.db.DatabaseManager;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongUnaryOperator;

/**
 * Base class for currency stores with lazy-load cache, TTL expiration,
 * and version-safe async refresh.
 */
public abstract class CachedCurrencyStore {

    protected static final long CACHE_TTL_MS = 5_000;

    private final ConcurrentHashMap<UUID, CachedBalance> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> refreshInFlight = new ConcurrentHashMap<>();
    private final AtomicLong versionCounter = new AtomicLong(0);

    protected abstract HytaleLogger logger();

    protected abstract long loadFromDatabase(UUID playerId);

    protected abstract boolean persistToDatabase(UUID playerId, long amount);

    public long getBalance(UUID playerId) {
        if (playerId == null) return 0;
        CachedBalance cached = cache.get(playerId);
        if (cached == null) return loadAndCacheBalance(playerId);
        if (cached.isStale()) refreshFromDatabaseAsync(playerId);
        return cached.value;
    }

    public void setBalance(UUID playerId, long amount) {
        if (playerId == null) return;
        long safe = Math.max(0, amount);
        CachedBalance previous = cache.get(playerId);
        cache.put(playerId, new CachedBalance(safe, versionCounter.incrementAndGet()));
        if (!persistToDatabase(playerId, safe)) {
            if (previous == null) cache.remove(playerId);
            else cache.put(playerId, previous);
        }
    }

    public long addBalance(UUID playerId, long amount) {
        if (playerId == null) return 0;
        return modifyBalance(playerId, current -> current + amount);
    }

    public long removeBalance(UUID playerId, long amount) {
        if (playerId == null) return 0;
        return modifyBalance(playerId, current -> Math.max(0, current - amount));
    }

    public void evictPlayer(UUID playerId) {
        if (playerId != null) {
            cache.remove(playerId);
            refreshInFlight.remove(playerId);
        }
    }

    protected long modifyBalance(UUID playerId, LongUnaryOperator compute) {
        long[] result = new long[1];
        CachedBalance previous = cache.get(playerId);
        cache.compute(playerId, (uuid, cached) -> {
            long current = (cached != null && !cached.isStale()) ? cached.value : loadFromDatabase(uuid);
            long newTotal = Math.max(0, compute.applyAsLong(current));
            result[0] = newTotal;
            return new CachedBalance(newTotal, versionCounter.incrementAndGet());
        });
        if (!persistToDatabase(playerId, result[0])) {
            if (previous == null) cache.remove(playerId);
            else cache.put(playerId, previous);
            return previous != null ? previous.value : 0;
        }
        return result[0];
    }

    private long loadAndCacheBalance(UUID playerId) {
        long fromDb = loadFromDatabase(playerId);
        cache.put(playerId, new CachedBalance(fromDb, versionCounter.incrementAndGet()));
        return fromDb;
    }

    private void refreshFromDatabaseAsync(UUID playerId) {
        if (playerId == null || refreshInFlight.putIfAbsent(playerId, Boolean.TRUE) != null) {
            return;
        }
        CachedBalance snapshot = cache.get(playerId);
        long snapshotVersion = snapshot != null ? snapshot.version : -1;
        CompletableFuture.supplyAsync(() -> loadFromDatabase(playerId), HytaleServer.SCHEDULED_EXECUTOR)
                .handle((value, throwable) -> {
                    try {
                        if (throwable != null) {
                            logger().atWarning().withCause(throwable).log("Failed to refresh cache for " + playerId);
                            return null;
                        }
                        cache.compute(playerId, (uuid, current) -> {
                            if (current == null) return null;
                            if (current.version != snapshotVersion) return current;
                            return new CachedBalance(value, versionCounter.incrementAndGet());
                        });
                        return null;
                    } finally {
                        refreshInFlight.remove(playerId);
                    }
                });
    }

    private static final class CachedBalance {
        final long value;
        final long cachedAt;
        final long version;

        CachedBalance(long value, long version) {
            this.value = value;
            this.cachedAt = System.currentTimeMillis();
            this.version = version;
        }

        boolean isStale() {
            return System.currentTimeMillis() - cachedAt > CACHE_TTL_MS;
        }
    }
}
```

**Step 2: Refactor VexaStore to extend CachedCurrencyStore**

Keep VexaStore's public API identical — add delegate methods:

```java
public class VexaStore extends CachedCurrencyStore {
    // Keep: INSTANCE, getInstance(), initialize() (with migration + CurrencyBridge)
    // Keep: loadFromDatabase(), persistToDatabase() (implement abstract)
    // Delegate existing public methods to base:
    //   getVexa()    → getBalance()
    //   setVexa()    → setBalance()
    //   addVexa()    → addBalance()
    //   removeVexa() → removeBalance()
    //   evictPlayer() inherited from base
    // Remove: cache, refreshInFlight, versionCounter, CachedBalance (now in base)
    // Remove: modifyVexa, loadAndCacheBalance, refreshFromDatabaseAsync (now in base)

    public long getVexa(UUID playerId) { return getBalance(playerId); }
    public void setVexa(UUID playerId, long vexa) { setBalance(playerId, vexa); }
    public long addVexa(UUID playerId, long amount) { return addBalance(playerId, amount); }
    public long removeVexa(UUID playerId, long amount) { return removeBalance(playerId, amount); }
}
```

**Step 3: Refactor FeatherStore to extend CachedCurrencyStore**

Same pattern. FeatherStore gains version-based refresh (improvement). Keep delegate methods:

```java
public class FeatherStore extends CachedCurrencyStore {
    // Same pattern as VexaStore
    public long getFeathers(UUID playerId) { return getBalance(playerId); }
    public void setFeathers(UUID playerId, long feathers) { setBalance(playerId, feathers); }
    public long addFeathers(UUID playerId, long amount) { return addBalance(playerId, amount); }
    public long removeFeathers(UUID playerId, long amount) { return removeBalance(playerId, amount); }
}
```

**Step 4: Verify callers**

All 24 VexaStore callers use `.getVexa()`, `.addVexa()`, `.removeVexa()`, `.evictPlayer()` — all preserved as delegates. All 11 FeatherStore callers use `.getFeathers()`, `.addFeathers()`, `.removeFeathers()`, `.evictPlayer()` — all preserved.

**Step 5: Commit**

```
refactor: extract CachedCurrencyStore to deduplicate VexaStore/FeatherStore
```

---

## Group 3: New Utilities and Cross-Module Updates

### Task 9: CORE-10 — Create DailyResetUtils

**Files:**
- Create: `hyvexa-core/src/main/java/io/hyvexa/common/util/DailyResetUtils.java`
- Modify: `hyvexa-core/src/main/java/io/hyvexa/common/skin/DailyShopRotation.java`
- Modify: `hyvexa-purge/src/main/java/io/hyvexa/purge/mission/DailyMissionRotation.java`

**Step 1: Create DailyResetUtils**

```java
package io.hyvexa.common.util;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

public final class DailyResetUtils {

    private DailyResetUtils() {}

    public static long getSecondsUntilReset() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
        return java.time.Duration.between(now, midnight).getSeconds();
    }

    public static String formatTimeRemaining(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return hours + "h " + minutes + "m";
    }
}
```

**Step 2: Update DailyShopRotation to delegate**

Replace the method bodies with calls to DailyResetUtils. Keep the methods for backward compat (callers: PurgeSkinShopTab, PurgeSkinShopPage, tests):

```java
public static long getSecondsUntilReset() {
    return DailyResetUtils.getSecondsUntilReset();
}

public static String formatTimeRemaining(long seconds) {
    return DailyResetUtils.formatTimeRemaining(seconds);
}
```

**Step 3: Update DailyMissionRotation to delegate**

Same pattern. Callers: PurgeHudManager.

**Step 4: Commit**

```
refactor: extract DailyResetUtils to deduplicate daily reset logic
```

---

### Task 10: CORE-12 — Replace RunOrFallUtils.formatDuration

**Files:**
- Modify: `hyvexa-runorfall/src/main/java/io/hyvexa/runorfall/util/RunOrFallUtils.java`
- Modify: `hyvexa-runorfall/src/main/java/io/hyvexa/runorfall/ui/RunOrFallStatsPage.java`
- Modify: `hyvexa-runorfall/src/main/java/io/hyvexa/runorfall/ui/RunOrFallLeaderboardPage.java`

**Context:** RunOrFallUtils.formatDuration uses `%02d` for minutes (zero-padded) and `Locale.US`. FormatUtils.formatDuration uses `%d` (no padding) and `Locale.ROOT`. The RunOrFall version zero-pads minutes. Since RunOrFall displays in a fixed-width UI context, keeping the zero-padding matters.

**Step 1: Add zero-padded variant to FormatUtils**

Add to `FormatUtils.java`:

```java
/**
 * Format as MM:SS.CC with zero-padded minutes (for fixed-width displays).
 */
public static String formatDurationPadded(long durationMs) {
    long totalMs = Math.max(0L, durationMs);
    long totalSeconds = totalMs / 1000L;
    long centis = (totalMs % 1000L) / 10L;
    long minutes = totalSeconds / 60L;
    long seconds = totalSeconds % 60L;
    return String.format(Locale.ROOT, "%02d:%02d.%02d", minutes, seconds, centis);
}
```

**Step 2: Update RunOrFall callers**

In `RunOrFallStatsPage.java` and `RunOrFallLeaderboardPage.java`, replace:
```java
RunOrFallUtils.formatDuration(millis)
```
with:
```java
FormatUtils.formatDurationPadded(millis)
```

Add import `io.hyvexa.common.util.FormatUtils` and remove unused `RunOrFallUtils` import if it becomes unused.

**Step 3: Delete RunOrFallUtils.formatDuration**

Remove the method from `RunOrFallUtils.java`.

**Step 4: Commit**

```
refactor: consolidate duration formatting into FormatUtils
```

---

### Task 11: CORE-5 — Remove dead price field from WardrobeCosmeticDef

**Files:**
- Modify: `hyvexa-core/src/main/java/io/hyvexa/core/wardrobe/WardrobeBridge.java`
- Modify: `hyvexa-wardrobe/src/main/java/io/hyvexa/wardrobe/command/WardrobeBuyCommand.java`

**Context:** The `price` field on `WardrobeCosmeticDef` is always `0`. Actual prices come from `CosmeticShopConfigStore`. One caller uses `.price()`: `WardrobeBuyCommand:45` in debug output `d.id() + " (" + d.price() + " vexa)"`.

**Step 1: Remove price from record and wd() helper**

In `WardrobeBridge.java`:

```java
// Before
public record WardrobeCosmeticDef(String id, String displayName, int price,
                                   String permissionNode, String category, String iconKey,
                                   String iconPath) {}

private static WardrobeCosmeticDef wd(...) {
    return new WardrobeCosmeticDef("WD_" + fileName, displayName, 0, permissionNode, ...);
}

// After
public record WardrobeCosmeticDef(String id, String displayName,
                                   String permissionNode, String category, String iconKey,
                                   String iconPath) {}

private static WardrobeCosmeticDef wd(...) {
    return new WardrobeCosmeticDef("WD_" + fileName, displayName, permissionNode, ...);
}
```

**Step 2: Fix WardrobeBuyCommand caller**

In `WardrobeBuyCommand.java:44-45`, replace `.price()` with the actual price from config:

```java
// Before
.map(d -> d.id() + " (" + d.price() + " vexa)")

// After
.map(d -> d.id())
```

(The debug listing doesn't need prices — they're dynamic per cosmetic and per currency, not always vexa.)

**Step 3: Commit**

```
cleanup: remove dead price field from WardrobeCosmeticDef
```

---

### Task 12: CORE-9 — Extract DB migration helpers to DatabaseManager

**Files:**
- Modify: `hyvexa-core/src/main/java/io/hyvexa/core/db/DatabaseManager.java`
- Modify: `hyvexa-parkour/src/main/java/io/hyvexa/parkour/data/MapStore.java`
- Modify: `hyvexa-parkour/src/main/java/io/hyvexa/parkour/data/MedalRewardStore.java`
- Modify: `hyvexa-runorfall/src/main/java/io/hyvexa/runorfall/manager/RunOrFallStatsStore.java`

**Step 1: Add migration helpers to DatabaseManager**

Add these public static methods to `DatabaseManager.java`:

```java
public static boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
    try (ResultSet rs = conn.getMetaData().getColumns(conn.getCatalog(), null, tableName, columnName)) {
        return rs.next();
    }
}

public static void addColumnIfMissing(Connection conn, String table, String column,
                                       String definition, HytaleLogger logger) {
    try {
        if (columnExists(conn, table, column)) return;
        try (PreparedStatement stmt = conn.prepareStatement(
                "ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition)) {
            applyQueryTimeout(stmt);
            stmt.executeUpdate();
            logger.atInfo().log("Added column " + table + "." + column);
        }
    } catch (SQLException e) {
        logger.atWarning().withCause(e).log("Failed to add column " + table + "." + column);
    }
}

public static void renameColumnIfExists(Connection conn, String table, String oldColumn,
                                          String newColumn, String definition, HytaleLogger logger) {
    try {
        if (!columnExists(conn, table, oldColumn)) return;
        try (PreparedStatement stmt = conn.prepareStatement(
                "ALTER TABLE " + table + " CHANGE " + oldColumn + " " + newColumn + " " + definition)) {
            applyQueryTimeout(stmt);
            stmt.executeUpdate();
            logger.atInfo().log("Renamed column " + table + "." + oldColumn + " -> " + newColumn);
        }
    } catch (SQLException e) {
        logger.atWarning().withCause(e).log("Failed to rename column " + table + "." + oldColumn);
    }
}
```

Note: `DatabaseManager` already has private `columnExists` (used in VexaStore migration at line 274). Make it `public static` and consolidate.

**Step 2: Update MapStore, MedalRewardStore, RunOrFallStatsStore**

Replace their private `renameColumnIfExists`, `addColumnIfMissing`, `columnExists`, `ensureColumnExists` methods with calls to `DatabaseManager.columnExists()`, `DatabaseManager.addColumnIfMissing()`, `DatabaseManager.renameColumnIfExists()`. Delete the private copies.

**Step 3: Commit**

```
refactor: extract DB migration helpers to DatabaseManager
```

---

### Task 13: CORE-13 — Fix MultiHudBridge memory leak

**Files:**
- Modify: `hyvexa-core/src/main/java/io/hyvexa/common/util/MultiHudBridge.java`

**Context:** `compositeCache` grows unbounded if disconnect skips `evictPlayer()`. Callers already call `evictPlayer` on disconnect (3 callers found: HyvexaHubPlugin, HyvexaPurgePlugin, PlayerCleanupManager). The risk is edge cases where disconnect is missed.

**Step 1: Add a periodic sweep for offline players**

Add a static cleanup method that can be called from the tick loop or scheduled executor. The simplest safe approach: add a bounded-size check.

Actually, the simplest fix is to use a weak-reference approach or just document + trust the existing disconnect pattern. Given other agents may be adding more `evictPlayer` calls from their cleanup audits, the best approach is minimal: add a `size()` accessor for monitoring and trust the existing pattern, since all 3 plugin disconnect handlers already call it.

Add to MultiHudBridge:

```java
/**
 * Returns the number of cached composites. Useful for monitoring.
 * Should stay near the online player count; growth indicates a leak.
 */
public static int cacheSize() {
    return compositeCache.size();
}
```

This is the minimal fix. If the leak is real, the CORE-8 PlayerCleanupHelper (if implemented) would centralize all evictions and prevent missed calls.

**Step 2: Commit**

```
fix: add monitoring accessor for MultiHudBridge composite cache
```

---

### Task 14: CORE-11 — Add generic denyIfNot to ModeGate

**Files:**
- Modify: `hyvexa-core/src/main/java/io/hyvexa/common/util/ModeGate.java`
- Modify: `hyvexa-parkour/src/main/java/io/hyvexa/parkour/util/ParkourModeGate.java`
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/util/AscendModeGate.java`

**Step 1: Add generic denyIfNot to core ModeGate**

```java
public static boolean denyIfNot(CommandContext context, World world, String expectedWorldName, Message denyMessage) {
    if (!isWorld(world, expectedWorldName)) {
        if (context != null) {
            context.sendMessage(denyMessage);
        }
        return true;
    }
    return false;
}
```

Add necessary imports for `CommandContext` and `Message` to ModeGate.

**Step 2: Simplify ParkourModeGate and AscendModeGate**

Both become thin delegators (keep for backward compat, since 8+ parkour callers and 11+ ascend callers reference them):

```java
// ParkourModeGate
public static boolean denyIfNotParkour(CommandContext context, World world) {
    return ModeGate.denyIfNot(context, world, WorldConstants.WORLD_PARKOUR, ModeMessages.MESSAGE_ENTER_PARKOUR);
}

// AscendModeGate
public static boolean denyIfNotAscend(CommandContext context, World world) {
    return ModeGate.denyIfNot(context, world, WorldConstants.WORLD_ASCEND, ModeMessages.MESSAGE_ENTER_ASCEND);
}
```

Remove `resolvePlayerId()` from both (it just delegates to `PlayerUtils.resolvePlayerId()` — callers can call PlayerUtils directly). **Wait** — check callers first. If `resolvePlayerId` is called from these wrappers, keep it. Actually, looking at the callers found: no callers use `ParkourModeGate.resolvePlayerId()` or `AscendModeGate.resolvePlayerId()` in the search results. But to be safe, keep the methods and just have them delegate.

**Step 3: Commit**

```
refactor: add generic denyIfNot to core ModeGate
```

---

### Task 15: CORE-7 — Create StoreInitializer utility

**Files:**
- Create: `hyvexa-core/src/main/java/io/hyvexa/common/util/StoreInitializer.java`
- Modify: `hyvexa-purge/src/main/java/io/hyvexa/purge/HyvexaPurgePlugin.java`
- Modify: `hyvexa-wardrobe/src/main/java/io/hyvexa/wardrobe/WardrobePlugin.java`
- Modify: `hyvexa-runorfall/src/main/java/io/hyvexa/runorfall/HyvexaRunOrFallPlugin.java`

**Caution:** Other agents may be editing these plugin files. Create the utility class first; update callers only if no conflicts.

**Step 1: Create StoreInitializer**

```java
package io.hyvexa.common.util;

import com.hypixel.hytale.logger.HytaleLogger;

public final class StoreInitializer {

    private StoreInitializer() {}

    /**
     * Run each initializer, logging and continuing on failure.
     */
    public static void initialize(HytaleLogger logger, Runnable... initializers) {
        for (Runnable init : initializers) {
            try {
                init.run();
            } catch (Exception e) {
                logger.atWarning().withCause(e).log("Store initialization failed");
            }
        }
    }
}
```

**Step 2: Update plugin setup() methods**

Replace repetitive try-catch blocks. Example for HyvexaPurgePlugin:

```java
// Before (lines 129-152, ~8 try-catch blocks):
try { DatabaseManager.getInstance().initialize(); }
catch (Exception e) { LOGGER.atWarning().withCause(e).log("..."); }
try { VexaStore.getInstance().initialize(); }
catch (Exception e) { LOGGER.atWarning().withCause(e).log("..."); }
// ... 6 more ...

// After:
StoreInitializer.initialize(LOGGER,
        () -> DatabaseManager.getInstance().initialize(),
        () -> VexaStore.getInstance().initialize(),
        // ... remaining stores ...
);
```

Apply same pattern to WardrobePlugin and HyvexaRunOrFallPlugin.

**Step 3: Commit**

```
refactor: extract StoreInitializer to reduce plugin setup boilerplate
```

---

### Task 16: CORE-8 — Create PlayerCleanupHelper

**Files:**
- Create: `hyvexa-core/src/main/java/io/hyvexa/common/util/PlayerCleanupHelper.java`
- Modify: `hyvexa-purge/src/main/java/io/hyvexa/purge/HyvexaPurgePlugin.java`
- Modify: `hyvexa-wardrobe/src/main/java/io/hyvexa/wardrobe/WardrobePlugin.java`
- Modify: `hyvexa-runorfall/src/main/java/io/hyvexa/runorfall/HyvexaRunOrFallPlugin.java`

**Caution:** Same concern as Task 15 — other agents may be editing plugin files.

**Step 1: Create PlayerCleanupHelper**

```java
package io.hyvexa.common.util;

import com.hypixel.hytale.logger.HytaleLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Centralized player disconnect cleanup. Stores register eviction callbacks;
 * disconnect handlers call cleanupPlayer() once.
 */
public final class PlayerCleanupHelper {

    private static final List<Consumer<UUID>> callbacks = new ArrayList<>();

    private PlayerCleanupHelper() {}

    public static synchronized void register(Consumer<UUID> evictCallback) {
        callbacks.add(evictCallback);
    }

    public static void cleanupPlayer(UUID playerId, HytaleLogger logger) {
        if (playerId == null) return;
        for (Consumer<UUID> callback : callbacks) {
            try {
                callback.accept(playerId);
            } catch (Exception e) {
                logger.atWarning().withCause(e).log("Disconnect cleanup failed for " + playerId);
            }
        }
    }

    public static synchronized void clear() {
        callbacks.clear();
    }
}
```

**Step 2: Update plugin disconnect handlers**

Replace repetitive try-catch evictPlayer() blocks with single `PlayerCleanupHelper.cleanupPlayer(playerId, LOGGER)` calls. Register cleanup callbacks during initialization.

Example for HyvexaPurgePlugin:

```java
// In setup():
PlayerCleanupHelper.register(pid -> VexaStore.getInstance().evictPlayer(pid));
PlayerCleanupHelper.register(pid -> FeatherStore.getInstance().evictPlayer(pid));
// ... more stores ...

// In disconnect handler (replaces 8 try-catch blocks):
PlayerCleanupHelper.cleanupPlayer(playerId, LOGGER);
```

**Step 3: Also register MultiHudBridge cleanup (fixes CORE-13 properly)**

```java
PlayerCleanupHelper.register(MultiHudBridge::evictPlayer);
```

**Step 4: Commit**

```
refactor: centralize player disconnect cleanup with PlayerCleanupHelper
```

---

### Task 17: CORE-14 — Extract OrphanedEntityCleanup helper

**Files:**
- Create: `hyvexa-core/src/main/java/io/hyvexa/common/util/OrphanedEntityCleanup.java`
- Modify: `hyvexa-parkour/src/main/java/io/hyvexa/parkour/ghost/GhostNpcManager.java`
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/robot/RobotManager.java`

**Context:** Both managers implement identical UUID file I/O: save set of UUIDs to file on shutdown, load on boot, clean up orphaned entities. This is sensitive shutdown code.

**Step 1: Create OrphanedEntityCleanup**

```java
package io.hyvexa.common.util;

import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class OrphanedEntityCleanup {

    private OrphanedEntityCleanup() {}

    public static Set<UUID> loadOrphanedUuids(Path path, HytaleLogger logger) {
        Set<UUID> result = new HashSet<>();
        if (!Files.exists(path)) return result;
        try {
            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                try {
                    result.add(UUID.fromString(trimmed));
                } catch (IllegalArgumentException ignored) {}
            }
            Files.delete(path);
        } catch (IOException e) {
            logger.atWarning().log("Failed to load orphaned UUIDs from " + path + ": " + e.getMessage());
        }
        return result;
    }

    public static void saveOrphanedUuids(Path path, Set<UUID> uuids, HytaleLogger logger) {
        if (uuids.isEmpty()) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {}
            return;
        }
        try {
            Files.createDirectories(path.getParent());
            List<String> lines = uuids.stream().map(UUID::toString).collect(Collectors.toList());
            Files.write(path, lines);
        } catch (IOException e) {
            logger.atWarning().log("Failed to save orphaned UUIDs to " + path + ": " + e.getMessage());
        }
    }
}
```

**Step 2: Update GhostNpcManager**

Replace `loadOrphanedGhostUuids()` and `saveGhostUuidsForCleanup()` with calls to `OrphanedEntityCleanup`. Keep the logic that collects active ghost UUIDs into the set before saving — that's GhostNpcManager-specific.

**Step 3: Update RobotManager**

Same pattern — replace the file I/O with OrphanedEntityCleanup calls.

**Step 4: Commit**

```
refactor: extract OrphanedEntityCleanup helper for shared UUID file I/O
```

---

## Deferred

- **CORE-18:** WardrobeBridge JSON config — deferred per audit ("Plan separately")

---

## Execution Order

Tasks are ordered to minimize conflict risk and maximize independence:

1. **Tasks 1-5** (Group 1): Trivial, independent, core-only. Can be done in parallel.
2. **Tasks 6-8** (Group 2): Core refactors. Task 6 and 7 are independent. Task 8 depends on nothing.
3. **Tasks 9-17** (Group 3): Utilities + cross-module. Task 9-10 are independent. Tasks 15-16 touch plugin files (conflict risk with other agents).

## Testing

Per CLAUDE.md: only pure-logic classes with zero Hytale imports are testable. Most changes here involve Hytale API types and can't be unit tested.

**Testable:**
- DailyResetUtils.formatTimeRemaining() — pure logic, existing test in DailyShopRotationTest can be expanded
- FormatUtils.formatDurationPadded() — pure logic, add to existing FormatUtilsTest
- OrphanedEntityCleanup — pure file I/O (but uses java.nio, not Hytale)

Run tests after completing all tasks: `cmd.exe /c "gradlew.bat test"`
