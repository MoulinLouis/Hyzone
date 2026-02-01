# Parkour - ECS per-server-tick run detection

Goal: replace the 200ms polling (`tickMapDetection`) with an ECS
`EntityTickingSystem` that runs every server tick to improve trigger accuracy
(start/checkpoint/finish).

## Current flow
- Run tracking happens in `RunTracker.checkPlayer(ref, store)`.
- `HyvexaPlugin.tickMapDetection()` runs every 200ms via the scheduled executor.
- Movement is processed in ECS systems (ex: `PlayerProcessMovementSystem`) and
  positions are exposed via `TransformComponent`.

## Approach
1. Create an `EntityTickingSystem<EntityStore>` that runs every server tick
   and calls `RunTracker.checkPlayer(...)` per player.
2. Use a strict component query (Player + PlayerRef + TransformComponent) to
   reduce the candidate set.
3. Apply the same guards as `tickMapDetection` (Parkour world, duel state,
   perks handling), so it does not run in other worlds.
4. Disable the 200ms schedule (or keep it only for HUD if preferred).

## Technical implementation (proposed)

### 1) New ECS system
Create:
`hyvexa-parkour/src/main/java/io/hyvexa/parkour/system/RunTrackerTickSystem.java`

Responsibilities:
- run every server tick (ECS)
- skip non-Parkour worlds
- delegate to `RunTracker.checkPlayer(ref, store)`

Pseudo-structure:
```java
public class RunTrackerTickSystem extends EntityTickingSystem<EntityStore> {
    private final RunTracker runTracker;
    private final HudManager hudManager;
    private final PlayerPerksManager perksManager;
    private final DuelTracker duelTracker;
    private final HyvexaPlugin plugin;
    private volatile Query<EntityStore> query;

    public RunTrackerTickSystem(HyvexaPlugin plugin,
                                RunTracker runTracker,
                                HudManager hudManager,
                                PlayerPerksManager perksManager,
                                DuelTracker duelTracker) {
        this.plugin = plugin;
        this.runTracker = runTracker;
        this.hudManager = hudManager;
        this.perksManager = perksManager;
        this.duelTracker = duelTracker;
    }

    @Override
    public void tick(float delta, int entityId, ArchetypeChunk<EntityStore> chunk,
                     Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        if (runTracker == null || plugin == null) {
            return;
        }
        PlayerRef playerRef = chunk.getComponent(entityId, PlayerRef.getComponentType());
        Player player = chunk.getComponent(entityId, Player.getComponentType());
        TransformComponent transform = chunk.getComponent(entityId, TransformComponent.getComponentType());
        if (playerRef == null || player == null || transform == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        if (!plugin.isParkourWorld(world)) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        if (!plugin.shouldApplyParkourMode(playerId, world)) {
            return;
        }

        if (duelTracker != null && duelTracker.isInMatch(playerId)) {
            return;
        }

        // Perks gating (same as the 200ms tick).
        if (perksManager != null) {
            String activeMapId = runTracker.getActiveMapId(playerId);
            Ref<EntityStore> ref = chunk.getReferenceTo(entityId);
            if (activeMapId != null) {
                perksManager.disableVipSpeedBoost(ref, store, playerRef);
            } else if (perksManager.shouldDisableVipSpeedForStartTrigger(store, ref, playerRef)) {
                perksManager.disableVipSpeedBoost(ref, store, playerRef);
            }
        }

        Ref<EntityStore> ref = chunk.getReferenceTo(entityId);
        runTracker.checkPlayer(ref, store);

        if (hudManager != null) {
            hudManager.ensureRunHudNow(ref, store, playerRef);
            hudManager.updateRunHud(ref, store);
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        Query<EntityStore> current = query;
        if (current != null) {
            return current;
        }
        var playerType = Player.getComponentType();
        var playerRefType = PlayerRef.getComponentType();
        var transformType = TransformComponent.getComponentType();
        if (playerType == null || playerRefType == null || transformType == null) {
            return Query.any();
        }
        current = Archetype.of(playerType, playerRefType, transformType);
        query = current;
        return current;
    }
}
```

Notes:
- `chunk.getReferenceTo(entityId)` yields a valid `Ref<EntityStore>` on the
  world thread.
- `isParkourWorld` and `shouldApplyParkourMode` are currently `private` in
  `HyvexaPlugin` and may need a package-private accessor.
- If HUD should not update every tick, remove the `hudManager.updateRunHud`
  call and keep the 100ms scheduled HUD task.

### 2) Ordering (optional but recommended)
To ensure updated positions:
```java
if (EntityStore.REGISTRY.hasSystemClass(PlayerProcessMovementSystem.class)) {
    return Set.of(new SystemDependency<>(Order.AFTER, PlayerProcessMovementSystem.class));
}
```

### 3) Register the system
In `HyvexaPlugin` (setup or `registerDeferredSystems`):
```java
private void registerRunTrackerTickSystem() {
    var registry = EntityStore.REGISTRY;
    if (!registry.hasSystemClass(RunTrackerTickSystem.class)) {
        registry.registerSystem(new RunTrackerTickSystem(this, runTracker, hudManager, perksManager, duelTracker));
    }
}
```
Call `registerRunTrackerTickSystem()` alongside other ECS systems.

### 4) Disable 200ms polling
In `scheduleTick(...)`:
- Remove `mapDetectionTask = scheduleTick("map detection", ...)`, or
- Gate it behind a feature flag when the ECS system is active.

## Validation
- Confirm start/checkpoint/finish triggers fire on every server tick.
- Measure max timing error before/after on a short map.
- Watch CPU impact (system runs for all online players).

## Risks / mitigation
- Higher CPU usage (20 ticks/sec): mitigate via strict query + world gating.
- HUD update frequency too high: keep HUD on the 100ms schedule.
- Access to private helpers in `HyvexaPlugin`: add package-private wrappers.

