# Hub Menu Redesign Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add hover effects and descriptive text to the Hub mode selection menu.

**Architecture:** UI-only change to `Hub_Menu.ui`. Each mode card gets a description band below the image and a TextButton overlay with hover states. No Java changes - button IDs are preserved.

**Tech Stack:** Hytale Custom UI (.ui files)

---

### Task 1: Update window and container dimensions

**Files:**
- Modify: `hyvexa-hub/src/main/resources/Common/UI/Custom/Pages/Hub_Menu.ui`

**Reference:** `docs/hytale-custom-ui/` for UI element docs

**Step 1: Adjust window height and ModeButtons container**

Change these dimension values:
- `#HubMenuWindow` height: `430` -> `486`
- `#ModeButtons` height: `262` -> `318`
- `#TopModeRow` height: `122` -> `150`
- `#BottomModeRow` height: `122` -> `150`

**Step 2: Commit**

```bash
git add hyvexa-hub/src/main/resources/Common/UI/Custom/Pages/Hub_Menu.ui
git commit -m "Hub menu: adjust dimensions for description bands"
```

---

### Task 2: Redesign ParkourCard with description + hover

**Files:**
- Modify: `hyvexa-hub/src/main/resources/Common/UI/Custom/Pages/Hub_Menu.ui`

**Step 1: Replace ParkourCard contents**

Replace the current `#ParkourCard` block (lines 82-93) with:

```ui
Group #ParkourCard {
  Anchor: (Width: 280, Height: 144);
  OutlineColor: #ffffff(0.06);
  OutlineSize: 1;

  Group {
    Anchor: (Left: 0, Right: 0, Top: 0, Height: 116);
    Background: (TexturePath: "../Textures/parkour.png");
  }

  Group {
    Anchor: (Left: 0, Right: 0, Bottom: 0, Height: 28);
    Background: #ffffff(0.03);
    Label {
      Anchor: (Left: 10, Right: 10, Top: 0, Bottom: 0);
      Style: (FontSize: 11, TextColor: #8a9bae, HorizontalAlignment: Center, VerticalAlignment: Center);
      Text: "Jump, run and climb through challenging obstacle courses";
    }
  }

  TextButton #ParkourButton {
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

**Step 2: Commit**

```bash
git add hyvexa-hub/src/main/resources/Common/UI/Custom/Pages/Hub_Menu.ui
git commit -m "Hub menu: redesign Parkour card with description + hover"
```

---

### Task 3: Redesign AscendCard with description + hover

**Files:**
- Modify: `hyvexa-hub/src/main/resources/Common/UI/Custom/Pages/Hub_Menu.ui`

**Step 1: Replace AscendCard contents**

Same pattern as Task 2 but with:
- Texture: `"../Textures/ascend.png"`
- Description: `"Build your robot army and rise through the ascensions"`
- Button ID: `#AscendButton`

**Step 2: Commit**

```bash
git add hyvexa-hub/src/main/resources/Common/UI/Custom/Pages/Hub_Menu.ui
git commit -m "Hub menu: redesign Ascend card with description + hover"
```

---

### Task 4: Redesign PurgeCard with description + hover

**Files:**
- Modify: `hyvexa-hub/src/main/resources/Common/UI/Custom/Pages/Hub_Menu.ui`

**Step 1: Replace PurgeCard contents**

Same pattern as Task 2 but with:
- Texture: `"../Textures/purge.png"`
- Description: `"Fight waves of zombies and loot powerful weapons"`
- Button ID: `#PurgeButton`

**Step 2: Commit**

```bash
git add hyvexa-hub/src/main/resources/Common/UI/Custom/Pages/Hub_Menu.ui
git commit -m "Hub menu: redesign Purge card with description + hover"
```

---

### Task 5: Redesign RunOrFallCard with description + hover

**Files:**
- Modify: `hyvexa-hub/src/main/resources/Common/UI/Custom/Pages/Hub_Menu.ui`

**Step 1: Replace RunOrFallCard contents**

Same pattern as Task 2 but with:
- Texture: `"../Textures/runorfall.png"`
- Description: `"Race against others on crumbling platforms"`
- Button ID: `#RunOrFallButton`

**Step 2: Commit**

```bash
git add hyvexa-hub/src/main/resources/Common/UI/Custom/Pages/Hub_Menu.ui
git commit -m "Hub menu: redesign RunOrFall card with description + hover"
```

---

### Verification Checklist

- [ ] All 4 card IDs preserved: `#ParkourButton`, `#AscendButton`, `#PurgeButton`, `#RunOrFallButton`
- [ ] All 4 card group IDs preserved: `#ParkourCard`, `#AscendCard`, `#PurgeCard`, `#RunOrFallCard`
- [ ] `#BottomModeRow` ID preserved (used by Java to toggle visibility)
- [ ] No underscores in new element IDs
- [ ] No Java changes needed (HubMenuPage.java untouched)
- [ ] Window height increased to accommodate description bands
