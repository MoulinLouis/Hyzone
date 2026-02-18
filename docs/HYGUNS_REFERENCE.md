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

Deux interactions custom pour creer des armes : `Hyguns_Shoot` et `Hyguns_Reload`.

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

| Champ | Description |
|-------|-------------|
| `Hyguns_Damage` | Degats par projectile |
| `Hyguns_NumProjectiles` | Nombre de projectiles par tir (1 = normal, 20 = shotgun) |
| `Hyguns_Spread` | Dispersion (0 = parfaitement droit, 0.12 = shotgun) |
| `Hyguns_MaxAmmo` | Taille du chargeur |
| `Hyguns_ProjectileConfigId` | Config projectile : `Hyguns_Projectile_Config_Bullet` (builtin, pas de fichier) |
| `Hyguns_ProjectileId` | Alternative : projectile entity custom (ex: `Flamethrower_Projectile`) |

### Hyguns_Reload
```json
"Type": "Hyguns_Reload",
"Hyguns_ReloadAmountPerInteraction": 30,
"Hyguns_MaxAmmo": 30,
"Hyguns_ReloadTime": 2.43,
"Hyguns_AmmoItemType": "Bullet"
```

| Champ | Description |
|-------|-------------|
| `Hyguns_ReloadAmountPerInteraction` | Munitions restaurees par reload |
| `Hyguns_MaxAmmo` | Doit matcher la valeur dans Hyguns_Shoot |
| `Hyguns_ReloadTime` | Duree du reload en secondes |
| `Hyguns_AmmoItemType` | Item consomme (`Bullet` ou `Fuel_Tank`) |

### Shoot Fail (clip vide)
Reference `Gun_Shoot_Fail` ou `Flamethrower_Shoot_Fail` dans le champ `FailInteractionId`.

## Customisation possible via JSON

### Creer une arme custom
1. Copier un item JSON existant depuis `run/mods/Hyguns/Server/Item/Items/` (ex: `AK47.json`)
2. Modifier les stats (damage, fire rate, ammo, etc.)
3. Changer le modele/texture dans `Common/Items/Weapons/`
4. Optionnel : creer un son custom dans `Common/Sounds/` + sound event dans `Server/Audio/SoundEvents/`
5. Le plugin fournit aussi 4 templates d'armes dans l'asset editor

### Ce qu'on peut changer sans toucher au plugin
- **Stats de combat** : damage, fire rate, spread, mag size, reload time
- **Visuels** : modele .blockymodel, texture .png (le DesertEagle a deja 3 skins: default, Gold, Ivory)
- **Sons** : fire/reload/equip sounds (sound events JSON + fichiers .ogg)
- **Camera** : shake intensity par arme (CameraEffect JSON)
- **Particules** : systeme de particules au tir (muzzle flash, fumee, douilles)
- **Projectiles** : vitesse, gravite, explosion radius/damage/falloff (pour Frag/Flamethrower)
- **Animations** : chaque arme reference un set d'anims (.blockyanim), certains sont custom (Flamethrower, DesertEagle, Barret50, Mac10)
- **Recettes craft** : via le champ `Recipe` dans l'item JSON
- **Qualite/Rarete** : champ `Quality` (Common/Uncommon/Rare/Epic)

### Scoping / Zoom (Barret .50 uniquement)
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
Peut etre ajoute a n'importe quelle arme. `Ability1`/`Ability2` pour zoom in/out.

## Frag Grenade (detail)

- **Fuse** : 4.5s apres lancer (ou impact)
- **Explosion** : 50 dmg, rayon 7, falloff 0.6
- **Physique** : gravite 20, 3 rebonds, bounciness 0.5, rolling
- **Note** : sons actuellement mutes (`AudioCat_Frag` volume: 0)

## Flamethrower (detail)

- Tire des entites `Flamethrower_Projectile` (pas un raycast)
- Damage direct: 0 — applique `Lava_Burn` (DoT) au contact
- Vitesse projectile: 50, gravite: 5, rayon explosion: 4
- Particule attachee : `FlamethrowerShooting` (fumee + etincelles)
- Idle : particule `Torch_Fire` sur le noeud `Flame` (scale 0.15)

## Projectile Configs

| Config | Usage | Vitesse | Gravite | Explosion |
|--------|-------|---------|---------|-----------|
| `Hyguns_Projectile_Config_Bullet` | Toutes les armes a feu | builtin | builtin | non |
| `Flamethrower` | Flamethrower | 50 | 5 | oui (rayon 4, 10 dmg) |
| `Frag` | Grenade | force 25 | 20 | oui (rayon 7, 50 dmg) |

## Structure des fichiers

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

- Le plugin gere un HUD ammo via `AmmoUI` (element `#AmmoValue`)
- Utilise MultipleHUD pour coexister avec d'autres HUDs custom
- Notre `MultiHudBridge` assure la compatibilite

## Item JSON Structure (exemple AK-47)

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
