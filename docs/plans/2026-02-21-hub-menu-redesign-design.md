# Hub Menu Redesign - Design

## Goal

Improve the Hub mode selection menu with hover effects and descriptive text for each mode.

## Current State

- 4 mode cards in 2x2 grid (280x116px each) with texture backgrounds
- Invisible `Button` overlays for click (no hover feedback)
- No description text - players rely solely on images
- Purge/RunOrFall hidden for non-staff

## Changes

### 1. Add description band below each mode image

Each card grows from 116px to 144px:
- 116px image (unchanged)
- 28px description band (`Background: #ffffff(0.03)`, `FontSize: 11`, `TextColor: #8a9bae`)

Descriptions (English):
| Mode | Text |
|------|------|
| Parkour | Jump, run and climb through challenging obstacle courses |
| Ascend | Build your robot army and rise through the ascensions |
| Purge | Fight waves of zombies and loot powerful weapons |
| RunOrFall | Race against others on crumbling platforms |

### 2. Replace `Button` with `TextButton` for hover states

Current invisible `Button` -> `TextButton` with styled states:
- **Default**: `Background: #ffffff(0.0)` (transparent)
- **Hovered**: `Background: #ffffff(0.06)` (light overlay)
- **Pressed**: `Background: #ffffff(0.03)` (subtle press)

The TextButton overlay covers the entire card (image + description).

### 3. Add outline accent on hover

Each card Group gets `OutlineColor: #ffffff(0.06)`, `OutlineSize: 1` by default.
On hover, the TextButton's overlay creates the visual feedback. The outline stays static (Hytale UI can't dynamically change outline on hover).

### 4. Adjust dimensions

- Card height: 116 -> 144 (+28px)
- Row heights: 122 -> 150 (+28px each)
- ModeButtons height: 262 -> 318 (+56px)
- Window height: 430 -> 486 (+56px)

## Files Changed

| File | Change |
|------|--------|
| `Hub_Menu.ui` | Card structure, descriptions, TextButton hover, dimensions |
| `HubMenuPage.java` | No changes needed (button IDs stay the same) |

## Card Structure (per mode)

```
Group #XCard {
  Anchor: (Width: 280, Height: 144);
  OutlineColor: #ffffff(0.06);
  OutlineSize: 1;

  // Image
  Group {
    Anchor: (Left: 0, Right: 0, Top: 0, Height: 116);
    Background: (TexturePath: "../Textures/x.png");
  }

  // Description band
  Group {
    Anchor: (Left: 0, Right: 0, Bottom: 0, Height: 28);
    Background: #ffffff(0.03);
    Label {
      Anchor: (Left: 10, Right: 10, Top: 0, Bottom: 0);
      Style: (FontSize: 11, TextColor: #8a9bae, HorizontalAlignment: Center, VerticalAlignment: Center);
      Text: "Description here";
    }
  }

  // Hover + click overlay
  TextButton #XButton {
    Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0);
    Text: "";
    Style: TextButtonStyle(
      Default: (Background: #ffffff(0.0)),
      Hovered: (Background: #ffffff(0.06)),
      Pressed: (Background: #ffffff(0.03)),
      Sounds: $C.@ButtonSounds,
    );
  }
}
```
