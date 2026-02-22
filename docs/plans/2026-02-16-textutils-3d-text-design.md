# TextUtils 3D Text System Design

## Summary

Integrate TextUtils-1.2.1 mod to create colored 3D text displays. Coexists with existing Hylograms system. Primary use case: colored leaderboards, designed to be reusable for future use cases.

## Architecture

### Layer 1: TextUtilsBridge (hyvexa-core)

Reflection bridge in `hyvexa-core/src/main/java/io/hyvexa/common/util/TextUtilsBridge.java`.

Same pattern as `HylogramsBridge` — resolves TextUtils classloader, caches Method objects, wraps `TextManager` static methods.

**Methods:**
- `isAvailable()` — check if TextUtils mod is loaded
- `spawn(Vector3d pos, Vector3f rot, World world, String id, String text, String font, float size)`
- `editContent(String id, World world, Store<EntityStore> store, String text)`
- `editLine(String id, World world, Store<EntityStore> store, String text, int line)`
- `remove(String id, World world, Store<EntityStore> store)`
- `resize(String id, World world, Store<EntityStore> store, float size)`
- `move(String id, World world, Store<EntityStore> store, Vector3d position)`
- `rotate(String id, World world, Store<EntityStore> store, Vector3f rotation)`
- `setVisibility(String id, World world, Store<EntityStore> store, boolean visible)`
- `changeFont(String id, World world, Store<EntityStore> store, String fontName)`
- `exists(String id)` — check TextUtilsHologramRegistry
- `listNames()` — list all registered text IDs

### Layer 2: Text3D Builder API (hyvexa-core)

Ergonomic builder in `hyvexa-core/src/main/java/io/hyvexa/common/util/Text3D.java`.

```java
// Create
Text3D.create("parkour_leaderboard")
    .at(new Vector3d(10, 5, 10))
    .rotation(new Vector3f(0, 90, 0))
    .font("default")
    .size(1.0f)
    .text("{yellow}#1{/yellow} - {white}Playfade{/white} - {green}42{/green}")
    .spawn(world);

// Update content
Text3D.get("parkour_leaderboard")
    .text("new content")
    .update(world);

// Update single line
Text3D.get("parkour_leaderboard")
    .editLine(0, "{yellow}#1{/yellow} - ...")
    .update(world);

// Operations
Text3D.get("id").resize(2.0f, world);
Text3D.get("id").move(pos, world);
Text3D.get("id").hide(world);
Text3D.get("id").show(world);
Text3D.get("id").remove(world);

// Color helper
Text3D.color("yellow", "#1")  // -> "{yellow}#1{/yellow}"

// Availability
Text3D.isAvailable()
Text3D.exists("id")
```

### Layer 3: TextLeaderboardManager (hyvexa-parkour)

Consumer in `hyvexa-parkour/src/main/java/io/hyvexa/manager/TextLeaderboardManager.java`.

Renders colored leaderboards using Text3D builder. Color scheme:
- Rank 1: `{yellow}` (gold)
- Rank 2: `{grey}` (silver)
- Rank 3: `{brown}` (bronze)
- Rank 4+: `{white}`
- Player names: `{white}`
- Values: `{green}`

Format: `{color}#1{/color} - {white}Playfade{/white} - {green}42{/green}`

Same data sources as existing `LeaderboardHologramManager` (ProgressStore completion counts + map times).

## TextUtils Color Reference

16 named colors: yellow, light_blue, blue, dark_blue, white, aqua, lime, red, orange, purple, pink, green, brown, beige, grey, black.

Syntax: `{colorname}text{/colorname}`
Newlines: `\n`

## Dependencies

- TextUtils mod identifier: `TextUtils:TextUtils`
- Added as **OptionalDependencies** in both parkour and ascend manifests
- No compile-time dependency — reflection only

## Files

| File | Module |
|------|--------|
| `hyvexa-core/.../util/TextUtilsBridge.java` | core |
| `hyvexa-core/.../util/Text3D.java` | core |
| `hyvexa-parkour/.../manager/TextLeaderboardManager.java` | parkour |
| Manifest updates (OptionalDependencies) | parkour, ascend |
