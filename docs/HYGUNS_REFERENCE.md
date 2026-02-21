# Hyguns 3.3.0 - Reference

Modpack location: `run/mods/Hyguns/` | Plugin: `HygunsPlugin-3.0.0.jar` | Dependency: `MultipleHUD-1.0.6.jar`

## Integration Status

- MultiHudBridge in `hyvexa-core` routes all HUDs through MultipleHUD API (coexists with Hyguns ammo HUD)
- All 4 modules (Parkour, Ascend, Hub, Purge) use the bridge

## Arsenal (11 weapons + 1 grenade)

| Weapon | Type | Dmg | Mag | Fire Rate (s) | Reload (s) | Spread | Pellets | Quality | Anim Set |
|--------|------|-----|-----|---------------|-----------|--------|---------|---------|----------|
| Glock-18 | Pistol | 6 | 20 | 0.25 | 1.3 | 0 | 1 | Uncommon | Handgun |
| ColtRevolver | Pistol | 24 | 6 | 1.0 | 2.2 | 0 | 1 | Uncommon | Handgun |
| Desert Eagle | Pistol | 25 | 7 | 1.0 | 2.13 | 0 | 1 | Rare | Handgun |
| Mac-10 | SMG | 2 | 30 | 0.05 | 1.5 | 0.05 | 1 | Uncommon | Rifle |
| MP9 | SMG | 3 | 30 | 0.075 | 1.8 | 0.02 | 1 | Uncommon | Rifle |
| Thompson | SMG | 3 | 50 | 0.075 | 2.13 | 0.05 | 1 | Rare | Rifle |
| AK-47 | Rifle | 17 | 30 | 0.1 | 2.43 | 0.02 | 1 | Epic | Rifle |
| M4A1s | Rifle | 14 | 25 | 0.09 | 2.5 | 0.01 | 1 | Epic | Rifle |
| Barret .50 | Sniper | 60 | 10 | 2.5 | 2.3 | 0 | 1 | Epic | Rifle |
| Double Barrel | Shotgun | 3 | 2 | 1.0 | 1.2 | 0.12 | 20 | Rare | Rifle |
| Flamethrower | Utility | DoT | 100 | 0.05 | 3.5 | 0 | 1 | Epic | Flamethrower |
| Frag Grenade | Thrown | 50 | - | 1.0 | - | - | - | Rare | - |

### Ammo Types
- **Bullet** (`Bullet`) - universal for all guns, max stack 500
- **Fuel Tank** (`Fuel_Tank`) - Flamethrower only, max stack 500

## Custom Interactions (Hyguns API)

Two custom interactions for creating weapons: `Hyguns_Shoot` and `Hyguns_Reload`.

### Hyguns_Shoot (Primary fire)
```json
"Type": "Hyguns_Shoot",
"Hyguns_Damage": 17,
"Hyguns_NumProjectiles": 1,
"Hyguns_Spread": 0.02,
"Hyguns_MaxAmmo": 30,
"Hyguns_ProjectileConfigId": "Hyguns_Projectile_Config_Bullet",
"Cooldown": { "Cooldown": 0.1 }
```

| Field | Description |
|-------|-------------|
| `Hyguns_Damage` | Damage per projectile |
| `Hyguns_NumProjectiles` | Number of projectiles per shot (1 = normal, 20 = shotgun) |
| `Hyguns_Spread` | Spread (0 = perfectly straight, 0.12 = shotgun) |
| `Hyguns_MaxAmmo` | Magazine size |
| `Hyguns_ProjectileConfigId` | Projectile config: `Hyguns_Projectile_Config_Bullet` (built-in, no file needed) |
| `Hyguns_ProjectileId` | Alternative: custom projectile entity (e.g., `Flamethrower_Projectile`) |

### Hyguns_Reload
```json
"Type": "Hyguns_Reload",
"Hyguns_ReloadAmountPerInteraction": 30,
"Hyguns_MaxAmmo": 30,
"Hyguns_ReloadTime": 2.43,
"Hyguns_AmmoItemType": "Bullet"
```

| Field | Description |
|-------|-------------|
| `Hyguns_ReloadAmountPerInteraction` | Ammo restored per reload |
| `Hyguns_MaxAmmo` | Must match the value in Hyguns_Shoot |
| `Hyguns_ReloadTime` | Reload duration in seconds |
| `Hyguns_AmmoItemType` | Item consumed (`Bullet` or `Fuel_Tank`) |

### Shoot Fail (empty clip)
Reference `Gun_Shoot_Fail` or `Flamethrower_Shoot_Fail` in the `FailInteractionId` field.

## JSON Customization

### Creating a custom weapon
1. Copy an existing item JSON from `run/mods/Hyguns/Server/Item/Items/` (e.g., `AK47.json`)
2. Modify the stats (damage, fire rate, ammo, etc.)
3. Change the model/texture in `Common/Items/Weapons/`
4. Optional: create a custom sound in `Common/Sounds/` + sound event in `Server/Audio/SoundEvents/`
5. The plugin also provides 4 weapon templates in the asset editor

### What can be changed without modifying the plugin
- **Combat stats**: damage, fire rate, spread, mag size, reload time
- **Visuals**: model .blockymodel, texture .png (the DesertEagle already has 3 skins: default, Gold, Ivory)
- **Sounds**: fire/reload/equip sounds (sound event JSONs + .ogg files)
- **Camera**: shake intensity per weapon (CameraEffect JSON)
- **Particles**: particle systems on fire (muzzle flash, smoke, shell casings)
- **Projectiles**: speed, gravity, explosion radius/damage/falloff (for Frag/Flamethrower)
- **Animations**: each weapon references an animation set (.blockyanim), some are custom (Flamethrower, DesertEagle, Barret50, Mac10)
- **Crafting recipes**: via the `Recipe` field in the item JSON
- **Quality/Rarity**: `Quality` field (Common/Uncommon/Rare/Epic)

### Scoping / Zoom (Barret .50 only)
```json
"Secondary": {
  "Type": "Scope_Zoom",
  "OverlayTexturePath": "Scope/sniper.png",
  "MaxDistance": 30.0,
  "MinDistance": 1.0,
  "DefaultZoomMultiplier": 1.0,
  "MaxZoomMultiplier": 3.0,
  "ZoomMultiplierStep": 1.0
}
```
Can be added to any weapon. `Ability1`/`Ability2` for zoom in/out.

## Frag Grenade (details)

- **Fuse**: 4.5s after throw (or on impact)
- **Explosion**: 50 dmg, radius 7, falloff 0.6
- **Physics**: gravity 20, 3 bounces, bounciness 0.5, rolling
- **Note**: sounds are currently muted (`AudioCat_Frag` volume: 0)

## Flamethrower (details)

- Fires `Flamethrower_Projectile` entities (not a raycast)
- Direct damage: 0 — applies `Lava_Burn` (DoT) on contact
- Projectile speed: 50, gravity: 5, explosion radius: 4
- Attached particle: `FlamethrowerShooting` (smoke + sparks)
- Idle: `Torch_Fire` particle on the `Flame` node (scale 0.15)

## Projectile Configs

| Config | Usage | Speed | Gravity | Explosion |
|--------|-------|-------|---------|-----------|
| `Hyguns_Projectile_Config_Bullet` | All firearms | built-in | built-in | no |
| `Flamethrower` | Flamethrower | 50 | 5 | yes (radius 4, 10 dmg) |
| `Frag` | Grenade | force 25 | 20 | yes (radius 7, 50 dmg) |

## File Structure

```
run/mods/Hyguns/
  manifest.json
  Common/
    Blocks/Benches/          # Gunsmith bench model/texture
    Characters/Animations/   # Per-weapon player animations (.blockyanim)
      Items/Dual_Handed/     # Barret50, DesertEagle, Flamethrower, Mac10
      Items/Main_Handed/     # ColtRevolver, Glock18
    Icons/                   # Item icons, crafting category icons
    Items/                   # 3D models (.blockymodel) + textures (.png)
      Ammo/Bullet/           # Bullet model
      Ammo/Fuel_Tank/        # Fuel Tank model
      Weapons/<Name>/        # Per-weapon model + texture(s)
    Sounds/<Name>/           # .ogg audio files per weapon
    UI/Custom/               # Just a mod icon PNG
  Server/
    Audio/SoundEvents/       # Sound event definitions (JSON)
    Camera/                  # CameraEffect + CameraShake profiles
    Item/
      Animations/            # Per-weapon animation set mappings (JSON)
      Interactions/          # Reusable interactions (Gun_Shoot_Fail, Frag_Throw, etc.)
      Items/                 # WEAPON DEFINITIONS (the main files to edit)
    Languages/en-US/         # Display names
    Models/                  # Entity models (Frag, Flamethrower_Projectile)
    Particles/Weapon/        # Particle systems + spawners
    ProjectileConfigs/       # Flamethrower + Frag projectile physics
    Projectiles/             # Flamethrower_Projectile entity definition
```

## HUD (ammo display)

- The plugin manages an ammo HUD via `AmmoUI` (element `#AmmoValue`)
- Uses MultipleHUD to coexist with other custom HUDs
- Our `MultiHudBridge` ensures compatibility

## Item JSON Structure (AK-47 example)

```json
{
  "Name": "AK47",
  "MaxStack": 1,
  "Quality": "Epic",
  "MaxDurability": 600,
  "Model": "Items/Weapons/AK47/AK47.blockymodel",
  "Texture": "Items/Weapons/AK47/AK47.png",
  "Icon": "Icons/ItemsGenerated/AK47.png",
  "AnimationSetId": "AK47",
  "Actions": {
    "Primary": {
      "Type": "Hyguns_Shoot",
      "Hyguns_Damage": 17,
      "Hyguns_NumProjectiles": 1,
      "Hyguns_Spread": 0.02,
      "Hyguns_MaxAmmo": 30,
      "Hyguns_ProjectileConfigId": "Hyguns_Projectile_Config_Bullet",
      "Cooldown": { "Cooldown": 0.1 },
      "FailInteractionId": "Gun_Shoot_Fail",
      "Effects": {
        "Particles": [{ "SystemId": "RifleShooting", "PositionOffset": {...} }],
        "CameraEffectId": "Handgun_Shoot",
        "WorldSoundEventId": "World_AK47_Fire",
        "LocalSoundEventId": "Local_AK47_Fire",
        "ImpactParticles": [{ "SystemId": "RifleShooting_Impact" }]
      }
    },
    "Secondary": {
      "Type": "Hyguns_Reload",
      "Hyguns_ReloadAmountPerInteraction": 30,
      "Hyguns_MaxAmmo": 30,
      "Hyguns_ReloadTime": 2.43,
      "Hyguns_AmmoItemType": "Bullet",
      "Effects": { "WorldSoundEventId": "AK47_Reload", ... }
    }
  },
  "Recipe": {
    "Items": [...],
    "BenchType": "Bench_Gunsmith",
    "BenchCategory": "rifles",
    "BenchCategoryOrder": 0,
    "UnlockedByDefault": true
  }
}
```
