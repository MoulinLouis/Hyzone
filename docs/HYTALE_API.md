# Hytale API Reference

## HytaleServer.jar Location

```
/mnt/c/Users/User/AppData/Roaming/Hytale/install/release/package/game/latest/Server/HytaleServer.jar
```

Use `jar tf` to list classes and `javap -c -p` to decompile for API exploration.

## API Gotchas

### Vector3d Has NO Accessor Methods

You cannot read x, y, z values back from a Vector3d:

```java
// WRONG - will not compile:
Vector3d pos = new Vector3d(1, 2, 3);
double x = pos.x();  // ERROR: no x() method
double y = pos.y();  // ERROR: no y() method

// CORRECT - use double[] arrays when you need to read/compare coordinates:
double[] pos = new double[]{1, 2, 3};
double x = pos[0];
double y = pos[1];
double z = pos[2];

// Convert to Vector3d only when calling Hytale APIs:
Vector3d vec = new Vector3d(pos[0], pos[1], pos[2]);
store.addComponent(ref, Teleport.getComponentType(), new Teleport(world, vec, rotation));
```

### Position Tracking Pattern

```java
// Store positions as double[] for computation
private volatile double[] previousPosition;

// When calculating (e.g., direction/rotation):
double dx = targetPos[0] - previousPos[0];
double dz = targetPos[2] - previousPos[2];
float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));

// Convert to Vector3d only for Hytale API calls
Vector3d targetVec = new Vector3d(targetPos[0], targetPos[1], targetPos[2]);
```

### Singleton Components

Some components use singleton patterns - check for `INSTANCE` or `get()`:

```java
// Correct
store.addComponent(entityRef, Invulnerable.getComponentType(), Invulnerable.INSTANCE);
store.addComponent(entityRef, Frozen.getComponentType(), Frozen.get());

// Wrong - don't use constructor if singleton exists
store.addComponent(entityRef, Invulnerable.getComponentType(), new Invulnerable());
```

### Entity Removal

Use the proper API, not reflection:

```java
store.removeEntity(entityRef, RemoveReason.REMOVE);
```

### World Thread Requirement

Entity/world operations must run on the world thread:

```java
CompletableFuture.runAsync(() -> {
    store.addComponent(ref, Teleport.getComponentType(), teleport);
}, world);

// Or
world.execute(() -> {
    // entity operations
});
```

### Common Gotchas Summary

1. **Entity refs expire** - Always check `ref.isValid()` before use
2. **World thread required** - Use `CompletableFuture.runAsync(..., world)` for entity/world ops
3. **UI paths** - Code uses `"Pages/MyPage.ui"`, files go in `Common/UI/Custom/Pages/`
4. **Inventory access** - Need `Player` component, not just `PlayerRef`
5. **Event registration** - Use `getEventRegistry().registerGlobal(...)` in `setup()`
6. **UI page lifecycle** - Override `onDismiss()` for cleanup, never call `close()` from it (see below)

### UI Page Lifecycle

Understanding the CustomUIPage lifecycle is critical for pages with background tasks (scheduled refreshes, animations, etc.).

**Lifecycle callbacks:**

```java
public abstract class CustomUIPage {
    // Called once when page is first opened
    public abstract void build(Ref<EntityStore> ref, UICommandBuilder cmd,
                               UIEventBuilder evt, Store<EntityStore> store);

    // Called when user manually closes the page OR when replaced by another page
    protected void close();

    // Called when page is dismissed/replaced (including by external UIs like NPCDialog)
    public void onDismiss(Ref<EntityStore> ref, Store<EntityStore> store);
}
```

**Call sequences:**

| Scenario | Methods Called (in order) |
|----------|--------------------------|
| User closes page manually | `onDismiss()` → `close()` |
| Your plugin opens new page | `onDismiss()` (old page) → `close()` (old page) → `build()` (new page) |
| External UI opens (NPCDialog) | `onDismiss()` only (your page) |
| External UI closes | (your page is NOT restored) |

**CRITICAL - Recursion trap:**

```java
// ❌ WRONG - causes StackOverflowError:
@Override
public void onDismiss(Ref<EntityStore> ref, Store<EntityStore> store) {
    this.close();  // close() calls PageManager.setPage()
                   // which calls onDismiss() again → infinite loop!
}

// ✅ CORRECT - separate cleanup method:
@Override
public void onDismiss(Ref<EntityStore> ref, Store<EntityStore> store) {
    stopBackgroundTasks();  // Clean up without calling close()
    super.onDismiss(ref, store);
}

@Override
public void close() {
    stopBackgroundTasks();  // Same cleanup for manual close
    super.close();
}

protected void stopBackgroundTasks() {
    if (refreshTask != null) {
        refreshTask.cancel(false);
        refreshTask = null;
    }
}
```

**Why both callbacks exist:**
- `close()` - For manual cleanup when you explicitly close the page
- `onDismiss()` - For automatic cleanup when Hytale closes/replaces your page (including external UIs)

**Best practice:** Implement cleanup logic in a separate method (`stopBackgroundTasks()`) called by both `close()` and `onDismiss()`.

**See `CODE_PATTERNS.md`** for complete example with base page pattern.

## API Discovery

### Key Packages to Explore

| Package | Purpose |
|---------|---------|
| `com.hypixel.hytale.server.core.entity` | Entity components, refs, stores |
| `com.hypixel.hytale.server.core.entity.component` | Built-in components (Player, Transform, Teleport) |
| `com.hypixel.hytale.server.core.world` | World operations, block access |
| `com.hypixel.hytale.server.core.command` | Command framework (AbstractAsyncCommand) |
| `com.hypixel.hytale.server.core.ui` | UI pages, HUD management |
| `com.hypixel.hytale.server.core.codec` | Serialization codecs |
| `com.hypixel.hytale.server.core.event` | Event system |

### Decompilation Tools

- **IntelliJ IDEA** - Built-in decompiler (just open the JAR)
- **jadx** - GUI decompiler with search
- **cfr** - Command-line decompiler

### Discovered APIs in This Project

| API Class | Purpose |
|-----------|---------|
| `AbstractAsyncCommand` | Async command execution |
| `InteractiveCustomUIPage` | Interactive UI framework |
| `Teleport` component | Player teleportation |
| `TransformComponent` | Position/rotation access |
| `HudManager` | Custom HUD attachment |
| `HitboxCollision` | Player collision control |
| `MovementSettings` | Speed multiplier adjustments |
| `Nameplate` | Player nameplate customization |

### Hylograms API

Hylograms classes are isolated in their own plugin classloader. Use `HylogramsBridge`:

```java
HylogramsBridge.Hologram holo = HylogramsBridge.create("example", store)
        .inWorld("Parkour")
        .at(0, 64, 0)
        .addLine("Header")
        .spawn();

HylogramsBridge.updateHologramLines("example", List.of("Title", "1. Player"), store);
```

## Game Assets

Location: `/mnt/c/Users/User/AppData/Roaming/Hytale/install/release/package/game/latest/Assets.zip`

Use `unzip -l Assets.zip | grep <pattern>` to list, `unzip -p Assets.zip <path>` to read contents.

### Camera System (via packets)

Control player camera via `SetServerCamera` packet:

```java
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.protocol.packets.camera.*;

// Switch to 3rd person
ServerCameraSettings s = new ServerCameraSettings();
s.positionLerpSpeed = 0.15f;    // smooth position transition
s.rotationLerpSpeed = 0.15f;    // smooth rotation transition
s.distance = 8f;                 // distance from player
s.isFirstPerson = false;
s.eyeOffset = true;
s.positionDistanceOffsetType = PositionDistanceOffsetType.DistanceOffsetRaycast;
packetHandler.writeNoCache(new SetServerCamera(ClientCameraView.Custom, false, s));

// Lock camera (disable mouse look + movement)
s.applyLookType = ApplyLookType.Rotation;
s.lookMultiplier = new Vector2f(0f, 0f);
s.skipCharacterPhysics = true;
s.rotationType = RotationType.Custom;
s.rotation = new Direction(yawRadians, pitchRadians, 0f);
packetHandler.writeNoCache(new SetServerCamera(ClientCameraView.Custom, true, s));

// Reset camera
CameraManager camMgr = store.getComponent(ref, CameraManager.getComponentType());
camMgr.resetCamera(playerRef);

// Camera shake
packetHandler.writeNoCache(new CameraShakeEffect(0, intensity, AccumulationMode.Set));
```

Key enums: `ClientCameraView` (FirstPerson, ThirdPerson, Custom), `RotationType` (AttachedToPlusOffset, Custom), `PositionDistanceOffsetType` (DistanceOffset, DistanceOffsetRaycast, None), `AttachedToType` (LocalPlayer, EntityId, None).

### Particles (via packets)

Spawn particles at a world position:

```java
import com.hypixel.hytale.protocol.packets.world.SpawnParticleSystem;

packetHandler.writeNoCache(new SpawnParticleSystem(
    "Spell/Fireworks/Firework_GS",  // particleSystemId (path under Server/Particles/, without .particlesystem)
    new Position(x, y, z),
    new Direction(0f, 0f, 0f),
    1.0f,  // scale
    new Color((byte)255, (byte)255, (byte)255)
));
```

#### Interesting Particle IDs (for cinematic use)

| Category | ID | Description |
|----------|----|-------------|
| **Cinematic** | `_Test/Cinematic/Cinematic/Cinematic_Fireworks_Red_XL` | Large red fireworks |
| **Cinematic** | `_Test/Cinematic/Cinematic/Cinematic_Portal_Appear` | Portal appearing |
| **Cinematic** | `_Test/Cinematic/Cinematic/Cinematic_Portal_Appear_XXL` | Large portal |
| **Cinematic** | `_Test/Cinematic/Cinematic/Cinematic_Pink_Smoke` | Pink smoke |
| **Spells** | `Spell/Fireworks/Firework_GS` | Firework burst |
| **Spells** | `Spell/Fireworks/Firework_Mix2` | Multi-color fireworks |
| **Spells** | `Spell/Azure_Spiral/Azure_Spiral` | Blue spiral |
| **Spells** | `Spell/Teleport/Teleport` | Teleport effect |
| **Spells** | `Spell/Portal/MagicPortal` | Magic portal |
| **Spells** | `Spell/Portal/PlayerSpawn_Spawn` | Player spawn effect |
| **Spells** | `Spell/Beam/Beam_Lightning2` | Lightning beam |
| **Spells** | `Spell/Rings/Rings_Rings` | Magic rings |
| **Status** | `Status_Effect/Heal/Aura_Heal` | Healing aura |
| **Status** | `Status_Effect/Heal/Aura_Sphere` | Healing sphere |
| **Status** | `Status_Effect/Heal/Effect_Heal` | Heal effect |
| **Status** | `Status_Effect/Crown_Gold/Effect_Crown_Gold` | Golden crown |
| **Status** | `Status_Effect/Shield/E_Sphere` | Shield sphere |
| **Status** | `Status_Effect/Fire/Effect_Fire` | Fire status |
| **Weather** | `Weather/Firefly/Fireflies_GS` | Fireflies |
| **Weather** | `Weather/Magic_Sparks/Magic_Sparks_GS` | Magic sparks |
| **Weather** | `Weather/Magic_Sparks/Magic_Sparks_Heavy_GS` | Heavy magic sparks |
| **Items** | `Item/Torch/Torch_Fire` | Torch flame |
| **Items** | `Item/Fire_Green/Fire_Green` | Green fire |
| **Items** | `Item/Fire_Teal/Fire_Teal` | Teal fire |
| **Drops** | `Drop/Legendary/Drop_Legendary` | Legendary drop glow |
| **Drops** | `Drop/Epic/Drop_Epic` | Epic drop glow |
| **Combat** | `Combat/Impact/Critical/Impact_Critical` | Critical hit |
| **Test** | `_Example/Example_Vertical_Buff` | Vertical buff effect |
| **Test** | `_Example/Example_Spiral` | Spiral |
| **Test** | `_Test/MagicRnD/Buff/Test_Cast_Buff` | Buff cast |
| **Test** | `_Test/Sticks/Nature_Buff` | Nature buff |
| **Test** | `_Test/Sticks/Nature_Buff_Spawn` | Nature buff spawn |
| **Memories** | `Memories/MemoryUnlock` | Memory unlock effect |

### Sounds (via packets)

Play sounds at a position or as 2D (global):

```java
import com.hypixel.hytale.protocol.packets.world.*;

// Look up index from string ID
int index = SoundEvent.getAssetMap().getIndex("SFX_Divine_Respawn");

// Play at position
packetHandler.writeNoCache(new PlaySoundEvent3D(index, SoundCategory.SFX, new Position(x, y, z), 1.0f, 1.0f));

// Play as 2D (no position, global to player)
packetHandler.writeNoCache(new PlaySoundEvent2D(index, SoundCategory.SFX, 1.0f, 1.0f));
```

#### Interesting Sound IDs

| ID | Description |
|----|-------------|
| `SFX_Divine_Respawn` | Divine respawn (epic) |
| `SFX_Avatar_Powers_Enable` | Avatar powers activate |
| `SFX_Avatar_Powers_Disable` | Avatar powers deactivate |
| `SFX_Portal_Neutral_Open` | Portal opening |
| `SFX_Portal_Neutral_Teleport_Local` | Teleport through portal |
| `SFX_Memories_Unlock_Local` | Memory unlock |
| `SFX_Chest_Legendary_FirstOpen_Player` | Legendary chest open |
| `SFX_Stamina_Potion_Success` | Potion success |

Sound categories: `Music`, `Ambient`, `SFX`, `UI`.

### Entity Light (via packet, deprecated)

```java
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolSetEntityLight;

int networkId = player.getNetworkId(); // deprecated but only option
packetHandler.writeNoCache(new BuilderToolSetEntityLight(networkId, new ColorLight((byte)radius, (byte)r, (byte)g, (byte)b)));
```

Note: `getNetworkId()` is deprecated. Entity light may only work for builder tool entities, not players.
