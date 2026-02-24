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

### Plugin NPC Variant Templates Are Broken

**Plugin-defined Variant role templates that reference vanilla Abstract templates fail builder validation.** This is a Hytale engine limitation — the `BuilderRoleVariant.validate()` delegates to the parent Abstract template's validate, which runs in a context plugin variants cannot satisfy.

```json
// ❌ This in a plugin's Server/NPC/Roles/ will ALWAYS fail validation:
{
  "Type": "Variant",
  "Reference": "Template_Aggressive_Zombies",
  "Modify": { "MaxHealth": 49 }
}
// Error: "Builder MyCustomZombie failed validation!"
```

**Workaround:** Spawn vanilla NPC types directly and apply customizations in code:
```java
// Spawn vanilla zombie
Object result = npcPlugin.spawnNPC(store, "Zombie", "", position, rotation);

// Override HP via EntityStatMap modifier (public API)
EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
statMap.putModifier(healthIndex, "my_hp_mod",
    new StaticModifier(Modifier.ModifierTarget.MAX,
        StaticModifier.CalculationType.MULTIPLICATIVE, 0.5f));

// Override walk speed via Unsafe on maxHorizontalSpeed (final field)
// NOTE: horizontalSpeedMultiplier is RESET TO 1.0 every tick by the engine — useless.
// Must modify maxHorizontalSpeed directly via Unsafe.
long offset = unsafe.objectFieldOffset(controllerClass.getDeclaredField("maxHorizontalSpeed"));
double baseSpeed = unsafe.getDouble(controller, offset);
unsafe.putDouble(controller, offset, baseSpeed * 0.5); // half speed

// Override drop list via reflection (no public API, field is final)
Field dropField = npcEntity.getRole().getClass().getDeclaredField("dropListId");
dropField.setAccessible(true);
dropField.set(npcEntity.getRole(), "Empty");

// Override damage via DamageEventSystem (intercept and setAmount)
```

**Vanilla NPC types that work with `spawnNPC()`:** `"Zombie"`, `"Zombie_Burnt"`, `"Zombie_Frost"`, `"Zombie_Sand"`, `"Kweebec_Seedling"`, etc. — any concrete (non-Abstract) role from `Assets.zip`.

### NPC State Machine — Do NOT Force States

**Never use `stateSupport.setState()` to force NPC behavior.** State names returned by `getStateIndex()` come from a **global registry**, not the NPC's template. Forcing a state that doesn't exist in the template paralyzes the NPC — it plays the animation but has **zero movement** because no behavior instructions are mapped to that state.

The engine logs: `State 'Angry.' in 'Zombie' does not exist and was set by an external call`

```java
// ❌ WRONG — paralyzes the NPC:
stateSupport.setState(entityRef, "Angry", "", store);  // phantom state, no movement

// ✅ CORRECT — let the natural AI handle state transitions:
npcEntity.getRole().setMarkedTarget("LockedTarget", playerRef);  // set who to target
// + boost sensor detection range so the NPC detects the player from far away
```

### NPC Instruction Tree — Sensor Range Boosting

NPC behavior is driven by an instruction tree with sensors, body motions, and actions. You can boost sensor detection range via Unsafe to make NPCs detect players from further away.

**Critical rule: skip sensors with original range < 5 blocks.** These are proximity/melee checks (e.g. `SensorEntity` at 0.6 blocks) often used inside NOT wrappers. Boosting them to 80 inverts the logic and breaks the behavior tree (NPC stops moving).

```java
double original = unsafe.getDouble(sensor, rangeOffset);
if (original < 5.0) return; // melee/proximity check — don't touch
unsafe.putDouble(sensor, rangeOffset, 80.0); // boost detection range
```

**Instruction tree structure (Zombie template):**
- `Instruction` nodes contain: `sensor`, `bodyMotion`, `headMotion`, `actions`, `instructionList` (children)
- Sensor types: `NullSensor`, `SensorState`, `SensorEval`, `SensorEntity`, `SensorTarget`, `SensorPlayer`, `SensorLeash`, `SensorBeacon`
- BodyMotion types: `BodyMotionTimer` (wraps `BodyMotionWander`), `BodyMotionSequence` (wraps `Motion[]`), `BodyMotionFind` (has `abortDistance`, `switchToSteeringDistance`)
- `BodyMotionTimer` has a `motion` field — must recurse into it to find nested motions

### Player Base HP Is 100

Player base HP in Hytale is **100** (not 20 like Minecraft). Don't add HP modifiers to reach 100 — it's already the default.

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

### Cosmetic System (EntityEffect + ModelVFX)

Apply visual effects (glows, auras) to players via `EffectControllerComponent`:

```java
EffectControllerComponent ctrl = store.getComponent(ref, EffectControllerComponent.getComponentType());
EntityEffect effect = EntityEffect.getAssetMap().get("Effects/Drop/Drop_Legendary");

// CRITICAL: slot must be the asset index, NOT 0
int effectIndex = EntityEffect.getAssetMap().getIndex(effect.getId());
ctrl.addInfiniteEffect(ref, effectIndex, effect, store);

// Or use auto-index overloads:
ctrl.addEffect(ref, effect, store);  // uses effect's built-in duration/infinite
ctrl.addEffect(ref, effect, duration, OverlapBehavior.OVERWRITE, store);
```

**Self-sync required:** The entity tracker only syncs effect updates to OTHER players. The player won't see their own effect changes unless you manually send the packet:

```java
EntityEffectUpdate[] updates = ctrl.createInitUpdates();
ComponentUpdate cu = new ComponentUpdate();
cu.type = ComponentUpdateType.EntityEffects;
cu.entityEffectUpdates = updates;
EntityUpdate eu = new EntityUpdate(player.getNetworkId(), null, new ComponentUpdate[]{cu});
ph.writeNoCache(new EntityUpdates(null, new EntityUpdate[]{eu}));
```

**Best ModelVFX for cosmetics (glow effects):**

| Effect | Color | Notes |
|--------|-------|-------|
| `Drop_Legendary` | Gold `#ffdb91` | Clean gold glow, bloom, looping |
| `Drop_Epic` | Purple `#e07dff` | Elegant purple glow |
| `Crown_Gold` | Gold `#ffdb91` | Intense (thickness 10), BottomUp pulse |
| `Sword_Signature_Status` | Cyan `#94f9ff` | Blue-white glow |

**Warning:** Status effects (`Burn`, `Freeze`, `Poison`) have `DamageCalculator`/`MovementEffects` — they cause gameplay side-effects. Use Drop/Weapon effects for pure cosmetics or create custom ones in `Server/Entity/ModelVFX/` + `Server/Entity/Effects/`.

Test command: `/cosmetic` (registered in HyvexaPlugin).

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

**Camera gotchas:**
- `Direction(yaw, pitch, roll)` — all values in **radians**, not degrees
- `isLocked` flag in `SetServerCamera` does NOT lock mouse input — it only controls server-side camera retention
- First-person via `ServerCameraSettings` (`isFirstPerson = true`) is glitchy (camera at feet level) — use `resetCamera()` instead
- No way to remove spawned particles — fire-and-forget only, accept fade time
- Entity light (`BuilderToolSetEntityLight`) has no visible effect on players

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

**Particle ID formats:** Full paths (e.g., `"Spell/Fireworks/Firework_GS"`) work for `SpawnParticleSystem`. Short names (e.g., `"Firework_GS"`) also work in some contexts. For `SpawnModelParticles` (entity-attached), use short names only.

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
