# Code Patterns

Common patterns used throughout the Hyvexa codebase.

## Commands

```java
public class MyCommand extends AbstractAsyncCommand {
    @Override
    public CompletableFuture<Void> executeAsync(CommandContext context) {
        // Implementation
        return CompletableFuture.completedFuture(null);
    }
}
// Register: getCommandRegistry().registerCommand(new MyCommand());
```

## UI Pages

```java
public class MyPage extends InteractiveCustomUIPage {
    public MyPage(PlayerRef playerRef) {
        super("Common/UI/Custom/Pages/MyPage.ui");  // Forward slashes
    }
}
// Open: CustomUI.open(playerRef, new MyPage(playerRef));
```

## Data Stores

```java
// Memory-first with MySQL persistence
private final ConcurrentHashMap<UUID, Data> cache = new ConcurrentHashMap<>();
public void syncLoad() { /* Load from MySQL on startup */ }
public void save(Data d) { cache.put(d.id, d); /* + MySQL write */ }
```

## Threading

```java
// Entity/world ops require world thread
CompletableFuture.runAsync(() -> {
    store.addComponent(ref, Teleport.getComponentType(), teleport);
}, world);
```

## Thread Safety

```java
// Use AtomicLong/AtomicInteger for counters accessed from multiple threads
private final AtomicLong counter = new AtomicLong(0);
counter.incrementAndGet();

// Use volatile for simple flags
private volatile boolean initialized = false;

// Use synchronized for initialization
synchronized (INIT_LOCK) {
    if (initialized) return;
    // ... initialize ...
    initialized = true;
}

// Use computeIfAbsent instead of check-then-put
map.computeIfAbsent(key, k -> new Value());
```

## ECS Access

```java
Ref<EntityStore> ref = playerRef.getReference();
if (ref == null || !ref.isValid()) return;
Store<EntityStore> store = ref.getStore();
Player player = store.getComponent(ref, Player.getComponentType());
```

## NPC/Entity Spawning

```java
// Spawn NPC using NPCPlugin (returns Pair, extract first element for entity ref)
NPCPlugin npcPlugin = NPCPlugin.get();
Object result = npcPlugin.spawnNPC(store, "Skeleton", "DisplayName", position, rotation);
// Use reflection to extract Ref from Pair (getFirst, getLeft, etc.)

// Entity removal - use proper API, not reflection guessing
store.removeEntity(entityRef, RemoveReason.REMOVE);

// Singleton components - use INSTANCE or get(), not constructor
store.addComponent(entityRef, Invulnerable.getComponentType(), Invulnerable.INSTANCE);
store.addComponent(entityRef, Frozen.getComponentType(), Frozen.get());  // Disables AI

// Teleport entity
store.addComponent(entityRef, Teleport.getComponentType(), new Teleport(world, position, rotation));

// Calculate yaw rotation for entity to face movement direction
double dx = targetX - previousX;
double dz = targetZ - previousZ;
float yaw = (float) (Math.toDegrees(Math.atan2(dx, dz)) + 180.0);  // +180 to face forward
Vector3f rotation = new Vector3f(0, yaw, 0);
```

## NPC Management

```java
// Track online players - only spawn NPCs for connected players
private final Set<UUID> onlinePlayers = ConcurrentHashMap.newKeySet();

// Prevent duplicate spawns with spawning flag
private volatile boolean spawning;
if (state.isSpawning()) return; // Skip if spawn in progress
state.setSpawning(true);
try {
    // spawn logic
} finally {
    state.setSpawning(false);
}
```

**Important:** See `HYTALE_API.md` (in docs/) for Vector3d limitations when working with positions.
