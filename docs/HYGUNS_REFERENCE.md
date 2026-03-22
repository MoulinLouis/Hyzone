# Hyguns 3.6.1 - Reference

Modpack location: `run/mods/Hyguns/` | Plugin: `HygunsPlugin-3.6.1.jar` | Dependency: `MultipleHUD-1.0.6.jar`

## Migration Note (3.3.0 -> 3.6.1)

The 3.6.1 modpack originally named all weapon files `Weapon_*.json` and ammo files `Ammo_*.json`. We **renamed them back** to preserve the item IDs that our Java code and player database use:

- `Weapon_AK47.json` -> `AK47.json` (item ID remains `AK47`)
- `Ammo_Bullet_Rifle.json` -> `Bullet_Rifle.json` (item ID becomes `Bullet_Rifle`)
- `Ammo_Bullet_Base.json` -> `Bullet.json` (item ID becomes `Bullet`)
- `Ammo_Fuel_Tank.json` -> `Fuel_Tank.json` (item ID preserved)

Art asset filenames (models, textures, icons in `Common/`) were NOT renamed - they still use `Weapon_*` / `Ammo_*` names where the modpack shipped them that way.

## Package Structure (v3.6.1)

All classes were reorganized into sub-packages in v3.6.1:
- `com.thescar.hygunsplugin.core.registry.GunRegistry` (was `com.thescar.hygunsplugin.GunRegistry`)
- `com.thescar.hygunsplugin.core.util.ItemStackUtils` (was `com.thescar.hygunsplugin.ItemStackUtils`)

**Removed API**: `GunRegistry.isGunItem(itemId)` — use `GunRegistry.getDefaultMaxAmmo(itemId) != null` instead.

**New ammo system** (informational): v3.6.1 adds `AmmoRegistry`, `AmmoDefinition`, `LoadedAmmoState` for ammo families and weapon classes. Not used by Hyvexa currently.

Metadata keys (`Hyguns_Ammo`, `Hyguns_MaxAmmo`) and JSON interaction names (`Hyguns_Shoot`, `Hyguns_Reload`) are unchanged.

## Integration Status

- MultiHudBridge in `hyvexa-core` routes all HUDs through MultipleHUD API (coexists with Hyguns ammo HUD)
- All 4 modules (Parkour, Ascend, Hub, Purge) use the bridge

## Arsenal (15 weapons + 1 grenade)

| Weapon | Type | Dmg | Mag | Fire Rate (s) | Reload (s) | Spread | Pellets | Quality | Anim Set |
|--------|------|-----|-----|---------------|-----------|--------|---------|---------|----------|
| Glock-18 | Pistol | 6 | 20 | 0.25 | 1.3 | 0 | 1 | Uncommon | Handgun |
| ColtRevolver | Pistol | 24 | 6 | 1.0 | 2.2 | 0 | 1 | Uncommon | Handgun |
| Desert Eagle | Pistol | 25 | 7 | 1.0 | 2.13 | 0 | 1 | Rare | Handgun |
| FiveSeven | Pistol | 8 | 20 | 0.25 | 1.3 | 0 | 1 | Uncommon | Handgun |
| USP-S | Pistol | 10 | 20 | 0.4 | 1.3 | 0 | 1 | Uncommon | Handgun |
| Mac-10 | SMG | 2 | 30 | 0.05 | 1.5 | 0.05 | 1 | Uncommon | Rifle |
| MP9 | SMG | 3 | 30 | 0.075 | 1.8 | 0.02 | 1 | Uncommon | Rifle |
| Thompson | SMG | 3 | 50 | 0.075 | 2.13 | 0.05 | 1 | Rare | Rifle |
| P90 | SMG | 5 | 50 | 0.05 | 3.0 | 0.05 | 1 | Rare | Rifle |
| AK-47 | Rifle | 17 | 30 | 0.1 | 2.43 | 0.02 | 1 | Epic | Rifle |
| M4A1s | Rifle | 14 | 25 | 0.09 | 2.5 | 0.01 | 1 | Epic | Rifle |
| Barret .50 | Sniper | 60 | 10 | 2.5 | 2.3 | 0 | 1 | Epic | Rifle |
| AWP | Sniper | 100 | 5 | 3.0 | 3.7 | 0 | 1 | Legendary | Rifle |
| Double Barrel | Shotgun | 3 | 2 | 1.0 | 1.2 | 0.12 | 20 | Rare | Rifle |
| Flamethrower | Utility | DoT | 100 | 0.05 | 3.5 | 0 | 1 | Epic | Flamethrower |
| Frag Grenade | Thrown | 50 | - | 1.0 | - | - | - | Rare | - |

### Ammo Classes (v3.6.1)

v3.6.1 uses class-based ammo families. Each weapon has a `HyGuns.Settings.Ammo.WeaponClass` that determines compatible ammo:

| Weapon Class | Ammo Item ID | Weapons |
|-------------|-------------|---------|
| Rifle | `Bullet_Rifle` | AK47, M4A1s, Barret50, AWP |
| Pistol | `Bullet` | Glock18, ColtRevolver, DesertEagle, FiveSeven, USPS |
| SMG/Pistol | `Bullet` | MP9, Mac10, Thompson, P90 |
| Shotgun | `Bullet_Shotgun` | DoubleBarrel |
| Flamethrower | `Fuel_Tank` | Flamethrower |

**Java mapping**: `PurgeWeaponConfigManager.WEAPON_AMMO_MAP` maps weapon IDs to ammo item IDs. `getAmmoItemId(weaponId)` returns the correct ammo for any weapon.

**Ammo variants**: Each base ammo type has special variants (Incendiary, Poison, Teleport, Mark, Scout) accessible via the in-game ammo selection UI.

### Skins

Weapon skins are separate item JSONs in `Server/Item/Items/Weapons/` with the same stats as the base weapon but a different texture and no recipe.

| Weapon | Skin | Item ID |
|--------|------|---------|
| AK-47 | Asimov | `AK47_Asimov` |
| AK-47 | Blossom | `AK47_Blossom` |
| AK-47 | Cyberpunk Neon | `AK47_CyberpunkNeon` |
| AK-47 | Frozen Voltage | `AK47_FrozenVoltage` |

Skin item IDs are produced by `PurgeSkinDefinition` as `weaponId + "_" + skinId`, matching the JSON filenames.

## Custom Interactions (Hyguns API)

Two custom interactions for creating weapons: `Hyguns_Shoot` and `Hyguns_Reload`.

### v3.6.1 JSON Format (HyGuns.Settings)

The 3.6.1 format moves weapon config into a top-level `HyGuns.Settings` block instead of inline interaction fields:

```json
{
  "HyGuns": {
    "Settings": {
      "WeaponIcon": "Weapons/AK47.png",
      "Ammo": {
        "Family": "Bullet",
        "WeaponClass": ["Rifle"],
        "Capacity": 30,
        "Reload": { "Time": 2.43, "Amount": 30 }
      },
      "Fire": { "Cooldown": 0.1 },
      "Projectiles": {
        "ConfigId": "Hyguns_Projectile_Config_Bullet",
        "Spread": 0.02,
        "Count": 1,
        "Damage": 17
      }
    }
  }
}
```

Interactions (`Hyguns_Shoot`, `Hyguns_Reload`) remain in the item JSON but only reference sound/animation effects — the plugin reads stats from `HyGuns.Settings`.

### Shoot Fail (empty clip)
Reference `Gun_Shoot_Fail` or `Flamethrower_Shoot_Fail` in the `FailInteractionId` field.

## JSON Customization

### Creating a custom weapon
1. Copy an existing item JSON from `run/mods/Hyguns/Server/Item/Items/Weapons/` (e.g., `AK47.json`)
2. Modify the `HyGuns.Settings` block (damage, fire rate, ammo capacity, etc.)
3. Change the model/texture in `Common/Items/Weapons/`
4. Optional: create a custom sound in `Common/Sounds/` + sound event in `Server/Audio/SoundEvents/`
5. The plugin also provides 4 weapon templates in the asset editor

### What can be changed without modifying the plugin
- **Combat stats**: damage, fire rate, spread, mag size, reload time (all in `HyGuns.Settings`)
- **Visuals**: model .blockymodel, texture .png
- **Sounds**: fire/reload/equip sounds (sound event JSONs + .ogg files)
- **Camera**: shake intensity per weapon (CameraEffect JSON)
- **Particles**: particle systems on fire (muzzle flash, smoke, shell casings)
- **Projectiles**: speed, gravity, explosion radius/damage/falloff (for Frag/Flamethrower)
- **Animations**: each weapon references an animation set (.blockyanim)
- **Crafting recipes**: via the `Recipe` field in the item JSON
- **Quality/Rarity**: `Quality` field (Common/Uncommon/Rare/Epic/Legendary)

### Scoping / Zoom (Barret .50, AWP)
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
      Items/
        Weapons/             # WEAPON DEFINITIONS (renamed from Weapon_*.json)
        Ammo/                # AMMO DEFINITIONS (renamed from Ammo_*.json)
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
