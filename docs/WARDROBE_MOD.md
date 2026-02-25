# Wardrobe Integration Guide for Hyvexa

## Overview

Wardrobe is a cosmetics framework for Hytale.

- Adds customizable cosmetics without creating new armor items
- Does not include cosmetics by default
- Cosmetics are added through Asset Packs (no code required)
- Accessible via:
  - `/wardrobe`
  - Cosmetics Mirror block

It recreates and extends Hytale’s native cosmetic system.

---

## Current Hyvexa Setup

- Violet cosmetics are integrated directly in `hyvexa-wardrobe/src/main/resources/` (Common + Server/Wardrobe/Cosmetics).
- Mobstar capes are integrated directly in the same module resources.
- All imported cosmetics use permission-based locking (`Properties.PermissionNode`) and `Visibility: Always`.
- Result: players see these cosmetics in `/wardrobe` as locked until permission is granted.
- `/shop` is intentionally separate and currently exposes only cosmetics declared in `WardrobeBridge`.

---

## 1. Add Wardrobe to Your Modpack

- Add the Wardrobe mod to your server
- Ensure it is included in the client modpack
- Verify both server and clients load it correctly

---

## 2. Create a Hyvexa Asset Pack

Example structure:


HyvexaCosmetics/
├── Server/
│ └── Wardrobe/
│ └── Cosmetics/
│ ├── purge_void_mask.json
│ ├── ascend_wings.json
│ └── ...
│
└── Common/
├── Models/
├── BlockyModels/
├── Textures/
└── Animations/


### Important Paths

Cosmetic JSON files:

Server/Wardrobe/Cosmetics/<cosmetic_id>.json


Models and textures:

Common/


Wardrobe automatically loads cosmetics from these folders.

---

## 3. Cosmetic Types

### Model Attachments (Recommended)

- Attach a blockymodel to the player
- Supports:
  - Texture variants
  - Gradient colors
  - Appearance variants
  - Armor-aware appearances
  - Cosmetic-aware appearances

Ideal for:
- Purge weapon cosmetics
- Ascend effects
- Masks, hats, wings, auras

---

### Player Model Cosmetics (Full Model Override)

- Replaces entire player model
- Uses ModelAsset from `Server/Models/`
- Supports custom:
  - Animations
  - Hitboxes
  - Camera settings

Important:
- Should use the `BodyCharacteristic` slot
- Only one full model should be active
- Does not support armor-aware or cosmetic-aware appearances

Use for:
- Zombie transformations
- Ascend evolved forms
- Full creature skins

---

## 4. Variants System (For Monetization)

### Texture Variants
Same model, multiple textures.

Example:
- Void AK skin
- Ember AK skin
- Neon AK skin

---

### Gradient Sets
Grayscale texture + built-in color palettes.

Great for:
- Sellable recolors
- Tier rewards
- Rank-based cosmetics

---

### Appearance Variants
Different models under one cosmetic.

Example:
- Wings: small / medium / large
- Helmet: damaged / clean / glowing

---

## 5. Connecting Wardrobe to Your Plugin

Wardrobe only handles visuals.

To integrate with Hyvexa systems like:
- Vexa currency
- Ascend progression
- Rank unlocks
- Mode-specific cosmetics

Use permission-based locking.

Example permission:

hyvexa.cosmetic.purge.voidmask


Your plugin:
- Grants permission on purchase
- Removes permission if revoked

That connects Wardrobe to your economy system.

---

## 6. Interaction with Default Hytale Cosmetics

- Wardrobe overrides default cosmetics in the same slot
- Players can hide default cosmetics
- Some slots cannot be hidden:
  - Face
  - Eyes
  - Ears
  - Underwear
  - Body characteristics

For full model cosmetics:
- Use `HiddenCosmeticSlots` in JSON

---

## 7. Suggested Hyvexa Cosmetic Structure

### Categories
- Hyvexa
  - Purge
  - Ascend
  - Seasonal
  - Founder

### Examples

**Purge**
- Tactical helmets
- Blood aura
- Glowing eyes
- Weapon back attachments

**Ascend**
- Energy wings
- Prestige glow
- Floating aura

**Founder**
- Animated badge
- Exclusive gradient cloak

---

## 8. Technical Notes

- Uses Hytale Asset Store system
- Supports live reload
- Fully JSON-driven
- No code required to add cosmetics
- API available for advanced custom cosmetic types

---

## 9. Minimal Integration Checklist

1. Add Wardrobe to modpack
2. Create HyvexaCosmetics asset pack
3. Place JSON files in:

Server/Wardrobe/Cosmetics/

4. Place models/textures in:

Common/

5. Add permission-based locking
6. Handle unlock logic in your plugin
7. Test via Wardrobe Menu
