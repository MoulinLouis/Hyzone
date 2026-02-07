# Help & Tutorial System (Ascend)

## Overview

The Help system lets players access tutorial guides from `/ascend help` or the Help item in their hotbar (Ingredient_Bolt_Shadoweave). It opens a **Help hub page** listing available tutorials. Each tutorial is a multi-step guided walkthrough with images, text, and navigation.

## How it works

1. **Help hub** (`/ascend help`) → shows a list of available tutorials
2. Player clicks a tutorial → opens **step 1** of that tutorial
3. Player navigates with **Next/Back** buttons between steps
4. On the last step, "Got It!" closes the tutorial
5. "Back" on step 1 returns to the Help hub

## Key files

### Help hub
- **UI**: `hyvexa-parkour-ascend/src/main/resources/Common/UI/Custom/Pages/Ascend_Help.ui`
- **Java**: `io.hyvexa.ascend.ui.AscendHelpPage` — binds each tutorial entry button, opens the corresponding tutorial page on click

### Welcome Tutorial (example to follow for new tutorials)
- **UI**: `Pages/Ascend_Welcome.ui` — single .ui file shared by all steps. Uses element IDs like `#StepImage1`, `#MainTitle`, `#Feature1Label` etc. that get their values injected from Java
- **Java**: `io.hyvexa.ascend.ui.AscendWelcomePage` — takes a `step` parameter (0-indexed). In `build()`, it appends the .ui file then uses `cmd.set()` to inject the right text, image visibility, progress bar value, and button label for the current step. Next/Back buttons open a **new instance** of the same page with `step +/- 1`

### Images
- Located in `Common/UI/Custom/Textures/help/`
- Convention: `{tutorial}_icon.png` (square, for the hub entry), `{tutorial}_step{N}.png` (for each step)
- In the .ui file, each step image is a separate Group with `Visible: false` by default. Java toggles visibility to show the right one

### Item & Interaction
- Item: `Ingredient_Bolt_Shadoweave` (name "Help", quality Developer, slot 3 in hotbar)
- Item JSON: `Server/Item/Items/Ingredient/Bolt/Ingredient_Bolt_Shadoweave.json`
- Interaction: `io.hyvexa.ascend.interaction.AscendDevShadoweaveInteraction` → opens `AscendHelpPage`
- Registered in `ParkourAscendPlugin.registerInteractionCodecs()`

### Command
- `/ascend help` → handled in `AscendCommand` switch, calls `openHelpPage()` which opens `AscendHelpPage`

## Adding a new tutorial

1. **Create images**: add `{name}_icon.png` and `{name}_step1.png`, `{name}_step2.png`... in `Textures/help/`
2. **Create the .ui file**: copy `Ascend_Welcome.ui` as a template. Adapt the layout if needed, but keep the same ID pattern (`#StepImage1/2/3`, `#MainTitle`, `#Description`, `#Feature1Label`, `#StepProgress`, `#NextButton`, `#BackButton`)
3. **Create the Java page**: copy `AscendWelcomePage.java`. Update the step content arrays (titles, descriptions, features, image paths). Update `TOTAL_STEPS`. Make sure Back on step 0 returns to `AscendHelpPage`
4. **Add an entry in the Help hub**: in `Ascend_Help.ui`, add a new clickable Group entry (copy the Welcome entry pattern). In `AscendHelpPage.java`, bind the new button and open the new tutorial page on click

## Design notes

- The tutorial UI uses a **split panel layout**: left panel (220px) shows the step image + progress bar, right panel shows text content
- Left panel image ratio: **11:16 portrait** fills the panel perfectly (220×320px area)
- Dynamic Background changes don't work in Hytale — use **visibility toggling** on multiple Groups instead
- Step navigation works by opening a **new page instance** with the next step number (not sendUpdate)
- The .ui file is shared across all steps — Java injects different values via `cmd.set()` in `build()`
