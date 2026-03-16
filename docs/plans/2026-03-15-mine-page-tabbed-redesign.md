# Mine Page Tabbed Redesign

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the flat mine upgrade window (pickaxe right-click) with a tabbed page mimicking `/ascend`'s layout — two tabs "Miner" and "Upgrade" — where "Miner" shows per-mine cards with miner purchase/upgrade and "Upgrade" shows global pickaxe upgrades.

**Architecture:** Create a new `Ascend_MinePage.ui` with in-page tab switching (visibility toggle of two content groups). A new `MinePage.java` handles both tabs, routing button events for miner actions and global upgrades. The pickaxe interaction registration is updated to open this new page instead of `MineUpgradePage`.

**Tech Stack:** Hytale Custom UI (.ui files), Java (BaseAscendPage pattern), UICommandBuilder visibility toggling for tab switching.

---

## Current State

- **Pickaxe right-click** → `MineUpgradePage.java` → `Ascend_MineUpgrade.ui`
  - Flat list mixing global upgrades (Mining Speed, Bag Capacity, Multi-Break) + per-mine miner entries
  - No tabs, no visual hierarchy between mine-specific and global upgrades
- **MineSelectPage** exists separately (for mine teleport/unlock) — not touched by this plan
- **Interaction chain:** `Mine_Pickaxe_Upgrade.json` → `Mine_Open_Upgrade.json` → `"Mine_Upgrades_Interaction"` → `MineUpgradePage`

## Target State

```
+----------------------------------------------+
| Mine                                  [Close] |
|==============================================|
| [ Miner ]  [ Upgrade ]          ← tabs       |
|----------------------------------------------|
| Crystals: 12,450                              |
|                                               |
| (Tab: Miner — shows 5 mine cards)            |
| +------------------------------------------+ |
| | Stone Quarry                              | |
| | Miner: Speed Lv 12 | Star 2              | |
| | 4.2 blocks/min                            | |
| |                         [Upgrade Speed]   | |
| +------------------------------------------+ |
| | Iron Depths              [Buy - 1,000]    | |
| | Miner: Not purchased                      | |
| +------------------------------------------+ |
| | ...                                       | |
|                                               |
| (Tab: Upgrade — shows global upgrades)        |
| Same as current Ascend_MineUpgradeEntry.ui    |
| Mining Speed, Bag Capacity, Multi-Break       |
+----------------------------------------------+
```

## Design Decisions

1. **In-page tab switching** (not separate pages like /ascend). Reason: both tabs share the same crystal balance and the content is small enough to live in one page. Tab clicks send a button event → Java toggles visibility of `#MinerContent` / `#UpgradeContent` groups.

2. **Tab styling**: Use the overlay-based tab pattern from `Ascend_Leaderboard.ui` — each tab has `ActiveBg`/`InactiveBg` overlays + `AccentActive`/`AccentInactive` groups, toggled via visibility. No dynamic `Background` changes (those don't work in Hytale). Tab switching helper follows `AscendLeaderboardPage.setTabActive()` pattern.

3. **Miner entry card**: New `Ascend_MinePageMinerEntry.ui` — taller than map cards (80px), shows mine name, miner status line, production rate, and contextual action button (Buy / Upgrade Speed / Evolve / MAXED).

4. **Upgrade tab content**: Reuses existing `Ascend_MineUpgradeEntry.ui` component — no changes needed to that file.

5. **All entry points updated**: Both the pickaxe interaction (`ParkourAscendPlugin.java:935`) and the `/mine upgrades` command (`MineCommand.java:100`) are updated to use `MinePage`. `MineUpgradePage.java` and `Ascend_MineUpgrade.ui` remain in the codebase but are no longer referenced.

6. **Window size**: 500×560 (slightly larger than current 460×480 to accommodate tabs + richer cards).

---

### Task 1: Create the tabbed Mine page UI

**Files:**
- Create: `hyvexa-parkour-ascend/src/main/resources/Common/UI/Custom/Pages/Ascend_MinePage.ui`

**Step 1: Create the UI file**

```
$C = "../Common.ui";

$C.@PageOverlay {
  SceneBlur {}

  Group #MinePageWindow {
    Anchor: (Width: 500, Height: 560);
    LayoutMode: Top;
    Background: #0d1620(0.95);
    OutlineColor: #ffffff(0.06);
    OutlineSize: 1;

    // === HEADER ===
    Group #Header {
      Anchor: (Left: 0, Right: 0, Height: 52);
      LayoutMode: Full;
      Background: #0a1018;

      Group {
        Anchor: (Left: 0, Right: 0, Top: 0, Height: 2);
        Background: #f59e0b;
      }

      Label {
        Anchor: (Left: 20, Top: 16, Width: 200, Height: 24);
        Style: (FontSize: 18, TextColor: #f0f4f8, RenderBold: true);
        Text: "Mine";
      }

      Group {
        Anchor: (Right: 16, Top: 10, Width: 72, Height: 32);

        TextButton #CloseButton {
          Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0);
          Text: "Close";
          Style: TextButtonStyle(
            Default: (
              Background: #ffffff(0.06),
              LabelStyle: (FontSize: 13, TextColor: #cfd7dc, HorizontalAlignment: Center, VerticalAlignment: Center)
            ),
            Hovered: (
              Background: #ffffff(0.1),
              LabelStyle: (FontSize: 13, TextColor: #f0f4f8, HorizontalAlignment: Center, VerticalAlignment: Center)
            ),
            Pressed: (
              Background: #ffffff(0.04),
              LabelStyle: (FontSize: 13, TextColor: #cfd7dc, HorizontalAlignment: Center, VerticalAlignment: Center)
            ),
            Sounds: $C.@ButtonSounds,
          );
        }
      }
    }

    // === CONTENT ===
    Group #Content {
      Anchor: (Left: 0, Right: 0);
      FlexWeight: 1;
      LayoutMode: Top;
      Padding: (Left: 16, Right: 16, Top: 6, Bottom: 12);

      // --- Tabs Row ---
      Group #TabsRow {
        Anchor: (Left: 0, Right: 0, Height: 40);
        LayoutMode: Left;

        // Tab 1: Miner (active by default)
        // Uses overlay pattern from Ascend_Leaderboard.ui — no dynamic Background changes
        Group #TabMinerWrap {
          Anchor: (Height: 36);
          FlexWeight: 1;
          OutlineColor: #ffffff(0.04);
          OutlineSize: 1;

          Group #TabMinerInactiveBg {
            Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0);
            Background: #142029;
            Visible: false;
          }

          Group #TabMinerActiveBg {
            Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0);
            Background: #2d3f50;
            Visible: true;
          }

          Group #TabMinerAccent {
            Anchor: (Left: 0, Right: 0, Top: 0, Height: 3);

            Group #TabMinerAccentInactive {
              Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0);
              Background: #142029;
              Visible: false;
            }

            Group #TabMinerAccentActive {
              Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0);
              Background: #f59e0b;
              Visible: true;
            }
          }

          Label #TabMinerLabel {
            Anchor: (Left: 0, Right: 0, Top: 6, Bottom: 0);
            Style: (FontSize: 14, RenderBold: true, TextColor: #f0f4f8, Alignment: Center, VerticalAlignment: Center);
            Text: "Miner";
          }

          TextButton #TabMiner {
            Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0);
            Text: "";
            Style: TextButtonStyle(
              Default: (Background: #000000(0)),
              Hovered: (Background: #ffffff(0.05)),
              Pressed: (Background: #ffffff(0.1)),
              Sounds: $C.@ButtonSounds,
            );
          }
        }

        Group { Anchor: (Width: 6); }

        // Tab 2: Upgrade (inactive by default)
        Group #TabUpgradeWrap {
          Anchor: (Height: 36);
          FlexWeight: 1;
          OutlineColor: #ffffff(0.04);
          OutlineSize: 1;

          Group #TabUpgradeInactiveBg {
            Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0);
            Background: #142029;
            Visible: true;
          }

          Group #TabUpgradeActiveBg {
            Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0);
            Background: #2d3f50;
            Visible: false;
          }

          Group #TabUpgradeAccent {
            Anchor: (Left: 0, Right: 0, Top: 0, Height: 3);

            Group #TabUpgradeAccentInactive {
              Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0);
              Background: #142029;
              Visible: true;
            }

            Group #TabUpgradeAccentActive {
              Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0);
              Background: #f59e0b;
              Visible: false;
            }
          }

          Label #TabUpgradeLabel {
            Anchor: (Left: 0, Right: 0, Top: 6, Bottom: 0);
            Style: (FontSize: 14, RenderBold: true, TextColor: #9fb0ba, Alignment: Center, VerticalAlignment: Center);
            Text: "Upgrade";
          }

          TextButton #TabUpgrade {
            Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0);
            Text: "";
            Style: TextButtonStyle(
              Default: (Background: #000000(0)),
              Hovered: (Background: #ffffff(0.05)),
              Pressed: (Background: #ffffff(0.1)),
              Sounds: $C.@ButtonSounds,
            );
          }
        }
      }

      // Crystals card (shared between tabs)
      Group #CardCrystals {
        Anchor: (Left: 0, Right: 0, Height: 36, Top: 8);
        LayoutMode: Full;
        Background: #ffffff(0.03);
        OutlineColor: #ffffff(0.04);
        OutlineSize: 1;

        Group {
          Anchor: (Left: 0, Top: 0, Bottom: 0, Width: 4);
          Background: #a78bfa;
        }

        Label {
          Anchor: (Left: 16, Top: 0, Bottom: 0, Width: 100);
          Style: (FontSize: 13, TextColor: #9fb0ba, VerticalAlignment: Center);
          Text: "Crystals";
        }

        Label #CrystalsValue {
          Anchor: (Left: 120, Right: 16, Top: 0, Bottom: 0);
          Style: (FontSize: 15, TextColor: #a78bfa, RenderBold: true, VerticalAlignment: Center);
          Text: "0";
        }
      }

      // === MINER TAB CONTENT (visible by default) ===
      Group #MinerContent {
        Anchor: (Left: 0, Right: 0, Top: 8);
        FlexWeight: 1;
        LayoutMode: TopScrolling;
        ScrollbarStyle: $C.@DefaultScrollbarStyle;

        Group #MinerEntries {
          LayoutMode: Top;
        }
      }

      // === UPGRADE TAB CONTENT (hidden by default) ===
      Group #UpgradeContent {
        Anchor: (Left: 0, Right: 0, Top: 8);
        FlexWeight: 1;
        LayoutMode: TopScrolling;
        ScrollbarStyle: $C.@DefaultScrollbarStyle;
        Visible: false;

        Group #UpgradeItems {
          LayoutMode: Top;
        }
      }

      // OP-only reset button (hidden by default)
      Group #ResetWrap {
        Anchor: (Left: 0, Right: 0, Height: 36, Top: 8);
        Visible: false;

        TextButton #ResetButton {
          Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0);
          Text: "Reset All Upgrades (OP)";
          Style: TextButtonStyle(
            Default: (
              Background: #dc2626(0.6),
              LabelStyle: (FontSize: 13, TextColor: #fca5a5, HorizontalAlignment: Center, VerticalAlignment: Center)
            ),
            Hovered: (
              Background: #dc2626(0.8),
              LabelStyle: (FontSize: 13, TextColor: #ffffff, HorizontalAlignment: Center, VerticalAlignment: Center)
            ),
            Pressed: (
              Background: #dc2626(0.4),
              LabelStyle: (FontSize: 13, TextColor: #fca5a5, HorizontalAlignment: Center, VerticalAlignment: Center)
            ),
            Sounds: $C.@ButtonSounds,
          );
        }
      }
    }
  }
}
```

**Step 2: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/resources/Common/UI/Custom/Pages/Ascend_MinePage.ui
git commit -m "feat(mine): add tabbed mine page UI layout with Miner/Upgrade tabs"
```

---

### Task 2: Create the miner entry card UI

**Files:**
- Create: `hyvexa-parkour-ascend/src/main/resources/Common/UI/Custom/Pages/Ascend_MinePageMinerEntry.ui`

**Step 1: Create the entry UI**

Each mine gets a card showing: mine name, miner status, production rate, and action button.

```
$C = "../Common.ui";

Group {
  Anchor: (Left: 0, Right: 0, Top: 6, Height: 80);
  LayoutMode: Full;
  Background: #ffffff(0.03);
  OutlineColor: #ffffff(0.04);
  OutlineSize: 1;

  // Left accent bar
  Group #AccentBar {
    Anchor: (Left: 0, Top: 0, Bottom: 0, Width: 4);
    Background: #f59e0b;
  }

  // Mine name
  Label #MineName {
    Anchor: (Left: 16, Top: 10, Width: 250, Height: 20);
    Style: (FontSize: 15, TextColor: #f0f4f8, RenderBold: true);
    Text: "Mine";
  }

  // Miner status line (e.g. "Speed Lv 12 | Star 2" or "Not purchased")
  Label #MinerStatus {
    Anchor: (Left: 16, Top: 32, Width: 220, Height: 18);
    Style: (FontSize: 12, TextColor: #9fb0ba);
    Text: "";
  }

  // Production rate (e.g. "4.2 blocks/min")
  Label #ProductionRate {
    Anchor: (Left: 16, Top: 50, Width: 220, Height: 18);
    Style: (FontSize: 12, TextColor: #f59e0b);
    Text: "";
  }

  // Cost label
  Label #CostText {
    Anchor: (Right: 110, Top: 10, Width: 120, Height: 18);
    Style: (FontSize: 12, TextColor: #a78bfa, HorizontalAlignment: End);
    Text: "";
  }

  // Action button (Buy / Upgrade Speed / Evolve)
  Group #ActionWrap {
    Anchor: (Right: 8, Top: 18, Width: 100, Height: 44);

    TextButton #ActionButton {
      Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0);
      Text: "Buy";
      Style: TextButtonStyle(
        Default: (
          Background: #3d6b5a,
          LabelStyle: (FontSize: 13, TextColor: #e0fef0, RenderBold: true, HorizontalAlignment: Center, VerticalAlignment: Center)
        ),
        Hovered: (
          Background: #4d8f7a,
          LabelStyle: (FontSize: 13, TextColor: #ffffff, RenderBold: true, HorizontalAlignment: Center, VerticalAlignment: Center)
        ),
        Pressed: (
          Background: #2d5a4a,
          LabelStyle: (FontSize: 13, TextColor: #e0fef0, RenderBold: true, HorizontalAlignment: Center, VerticalAlignment: Center)
        ),
        Sounds: $C.@ButtonSounds,
      );
    }
  }

  // Maxed label (hidden by default)
  Label #MaxedLabel {
    Anchor: (Right: 20, Top: 18, Width: 100, Height: 44);
    Style: (FontSize: 14, TextColor: #4ade80, RenderBold: true, HorizontalAlignment: Center, VerticalAlignment: Center);
    Text: "MAXED";
    Visible: false;
  }

  // Locked overlay (shown when mine not unlocked)
  Group #LockedOverlay {
    Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0);
    Background: #0a1018(0.7);
    Visible: false;

    Label #LockedLabel {
      Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0);
      Style: (FontSize: 13, TextColor: #4a5568, RenderBold: true, Alignment: Center, VerticalAlignment: Center);
      Text: "Mine Locked";
    }
  }
}
```

**Step 2: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/resources/Common/UI/Custom/Pages/Ascend_MinePageMinerEntry.ui
git commit -m "feat(mine): add miner entry card UI for tabbed mine page"
```

---

### Task 3: Create the MinePage Java class

**Files:**
- Create: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/mine/ui/MinePage.java`

**Step 1: Create the class**

This class combines the tab switching logic with content from both MineUpgradePage (global upgrades) and per-mine miner management. Reference patterns:
- Tab switching: visibility toggle via `UICommandBuilder.set()` (see `AscendMapSelectPage.java:116-131`)
- Miner actions: copy from `MineUpgradePage.java:108-165` (miner section) and `:235-432` (handlers)
- Global upgrades: copy from `MineUpgradePage.java:69-106` (upgrade section) and `:235-266` (buy handler)

```java
package io.hyvexa.ascend.mine.ui;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.mine.data.Mine;
import io.hyvexa.ascend.mine.data.MineConfigStore;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.mine.data.MineUpgradeType;
import io.hyvexa.ascend.mine.robot.MineRobotManager;
import io.hyvexa.ascend.mine.robot.MinerRobotState;
import io.hyvexa.ascend.ui.BaseAscendPage;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.PermissionUtils;

import java.util.List;

/**
 * Tabbed mine page opened by pickaxe right-click.
 * Tab "Miner": per-mine miner cards (buy, upgrade speed, evolve).
 * Tab "Upgrade": global pickaxe upgrades (mining speed, bag capacity, multi-break).
 */
public class MinePage extends BaseAscendPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_TAB_MINER = "TabMiner";
    private static final String BUTTON_TAB_UPGRADE = "TabUpgrade";
    private static final String BUTTON_BUY_UPGRADE_PREFIX = "BuyUpgrade_";
    private static final String BUTTON_BUY_MINER_PREFIX = "BuyMiner_";
    private static final String BUTTON_MINER_SPEED_PREFIX = "MinerSpeed_";
    private static final String BUTTON_MINER_EVOLVE_PREFIX = "MinerEvolve_";
    private static final String BUTTON_RESET = "ResetAll";

    private static final int MINER_MAX_SPEED_PER_STAR = 25;
    private static final int MINER_MAX_STARS = 5;

    private static final String[] UPGRADE_DISPLAY_NAMES = {
        "Mining Speed",
        "Bag Capacity",
        "Multi-Break"
    };

    private final MinePlayerProgress mineProgress;
    private final PlayerRef playerRef;
    private boolean minerTabActive = true;

    public MinePage(@Nonnull PlayerRef playerRef, MinePlayerProgress mineProgress) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.playerRef = playerRef;
        this.mineProgress = mineProgress;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/Ascend_MinePage.ui");

        // Close button
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);

        // Tab buttons
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TabMiner",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TAB_MINER), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TabUpgrade",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TAB_UPGRADE), false);

        // OP reset
        if (PermissionUtils.isOp(playerRef.getUuid())) {
            cmd.set("#ResetWrap.Visible", true);
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#ResetButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_RESET), false);
        }

        populateCrystals(cmd);
        populateMinerTab(cmd, evt);
        populateUpgradeTab(cmd, evt);
    }

    // ==================== Content Population ====================

    private void populateCrystals(UICommandBuilder cmd) {
        cmd.set("#CrystalsValue.Text", String.valueOf(mineProgress.getCrystals()));
    }

    private void populateMinerTab(UICommandBuilder cmd, UIEventBuilder evt) {
        MineConfigStore configStore = ParkourAscendPlugin.getInstance().getMineConfigStore();
        if (configStore == null) return;

        List<Mine> mines = configStore.listMinesSorted();
        for (int i = 0; i < mines.size(); i++) {
            Mine mine = mines.get(i);
            cmd.append("#MinerEntries", "Pages/Ascend_MinePageMinerEntry.ui");
            String sel = "#MinerEntries[" + i + "]";

            cmd.set(sel + " #MineName.Text", mine.getName());

            boolean unlocked = mineProgress.getMineSnapshot(mine.getId()).unlocked();
            if (!unlocked) {
                cmd.set(sel + " #LockedOverlay.Visible", true);
                cmd.set(sel + " #ActionWrap.Visible", false);
                cmd.set(sel + " #CostText.Visible", false);
                continue;
            }

            MinePlayerProgress.MinerProgressSnapshot minerState = mineProgress.getMinerSnapshot(mine.getId());

            if (!minerState.hasMiner()) {
                cmd.set(sel + " #MinerStatus.Text", "Not purchased");
                cmd.set(sel + " #CostText.Text", "Cost: " + getMinerBuyCost());
                cmd.set(sel + " #ActionButton.Text", "Buy");
                evt.addEventBinding(CustomUIEventBindingType.Activating,
                    sel + " #ActionButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BUY_MINER_PREFIX + mine.getId()), false);
            } else {
                int speedLevel = minerState.speedLevel();
                int stars = minerState.stars();
                boolean fullyMaxed = stars >= MINER_MAX_STARS && speedLevel >= MINER_MAX_SPEED_PER_STAR;

                cmd.set(sel + " #MinerStatus.Text", "Speed Lv " + speedLevel + " | Star " + stars);
                double blocksPerMin = MinerRobotState.getProductionRate(speedLevel, stars);
                cmd.set(sel + " #ProductionRate.Text", String.format("%.1f", blocksPerMin) + " blocks/min");

                if (fullyMaxed) {
                    cmd.set(sel + " #CostText.Visible", false);
                    cmd.set(sel + " #ActionWrap.Visible", false);
                    cmd.set(sel + " #MaxedLabel.Visible", true);
                } else if (speedLevel >= MINER_MAX_SPEED_PER_STAR) {
                    cmd.set(sel + " #CostText.Text", "Cost: " + getMinerEvolveCost(stars));
                    cmd.set(sel + " #ActionButton.Text", "Evolve");
                    evt.addEventBinding(CustomUIEventBindingType.Activating,
                        sel + " #ActionButton",
                        EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_MINER_EVOLVE_PREFIX + mine.getId()), false);
                } else {
                    cmd.set(sel + " #CostText.Text", "Cost: " + getMinerSpeedCost(speedLevel, stars));
                    cmd.set(sel + " #ActionButton.Text", "Upgrade");
                    evt.addEventBinding(CustomUIEventBindingType.Activating,
                        sel + " #ActionButton",
                        EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_MINER_SPEED_PREFIX + mine.getId()), false);
                }
            }
        }
    }

    private void populateUpgradeTab(UICommandBuilder cmd, UIEventBuilder evt) {
        MineUpgradeType[] types = MineUpgradeType.values();
        for (int i = 0; i < types.length; i++) {
            MineUpgradeType type = types[i];
            int level = mineProgress.getUpgradeLevel(type);
            int maxLevel = type.getMaxLevel();
            boolean maxed = level >= maxLevel;

            cmd.append("#UpgradeItems", "Pages/Ascend_MineUpgradeEntry.ui");
            String sel = "#UpgradeItems[" + i + "]";

            cmd.set(sel + " #UpgradeName.Text", UPGRADE_DISPLAY_NAMES[i]);
            cmd.set(sel + " #LevelText.Text", "Lv " + level + " / " + maxLevel);
            cmd.set(sel + " #EffectText.Text", getEffectDescription(type, level));

            if (maxed) {
                cmd.set(sel + " #CostText.Visible", false);
                cmd.set(sel + " #BuyWrap.Visible", false);
                cmd.set(sel + " #MaxedLabel.Visible", true);
            } else {
                cmd.set(sel + " #CostText.Text", "Cost: " + type.getCost(level));
                cmd.set(sel + " #MaxedLabel.Visible", false);
                evt.addEventBinding(CustomUIEventBindingType.Activating,
                    sel + " #BuyButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BUY_UPGRADE_PREFIX + type.name()), false);
            }
        }
    }

    // ==================== Tab Switching ====================
    // Uses overlay visibility pattern from AscendLeaderboardPage.setTabActive()

    private void switchToMinerTab() {
        if (minerTabActive) return;
        minerTabActive = true;

        UICommandBuilder cmd = new UICommandBuilder();
        setTabActive(cmd, "TabMiner", true);
        setTabActive(cmd, "TabUpgrade", false);
        cmd.set("#MinerContent.Visible", true);
        cmd.set("#UpgradeContent.Visible", false);

        this.sendUpdate(cmd, new UIEventBuilder(), false);
    }

    private void switchToUpgradeTab() {
        if (!minerTabActive) return;
        minerTabActive = false;

        UICommandBuilder cmd = new UICommandBuilder();
        setTabActive(cmd, "TabMiner", false);
        setTabActive(cmd, "TabUpgrade", true);
        cmd.set("#MinerContent.Visible", false);
        cmd.set("#UpgradeContent.Visible", true);

        this.sendUpdate(cmd, new UIEventBuilder(), false);
    }

    private void setTabActive(UICommandBuilder cmd, String tabId, boolean active) {
        String wrapPath = "#" + tabId + "Wrap";
        String accentPath = "#" + tabId + "Accent";
        cmd.set(wrapPath + " #" + tabId + "ActiveBg.Visible", active);
        cmd.set(wrapPath + " #" + tabId + "InactiveBg.Visible", !active);
        cmd.set(wrapPath + " " + accentPath + " #" + tabId + "AccentActive.Visible", active);
        cmd.set(wrapPath + " " + accentPath + " #" + tabId + "AccentInactive.Visible", !active);
    }

    // ==================== Event Handling ====================

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ButtonEventData data) {
        super.handleDataEvent(ref, store, data);
        String button = data.getButton();
        if (button == null) return;

        switch (button) {
            case BUTTON_CLOSE -> this.close();
            case BUTTON_TAB_MINER -> switchToMinerTab();
            case BUTTON_TAB_UPGRADE -> switchToUpgradeTab();
            case BUTTON_RESET -> handleResetAll(ref, store);
            default -> handleActionButton(ref, store, button);
        }
    }

    private void handleActionButton(Ref<EntityStore> ref, Store<EntityStore> store, String button) {
        if (button.startsWith(BUTTON_BUY_MINER_PREFIX)) {
            handleBuyMiner(ref, store, button.substring(BUTTON_BUY_MINER_PREFIX.length()));
        } else if (button.startsWith(BUTTON_MINER_SPEED_PREFIX)) {
            handleMinerSpeedUpgrade(ref, store, button.substring(BUTTON_MINER_SPEED_PREFIX.length()));
        } else if (button.startsWith(BUTTON_MINER_EVOLVE_PREFIX)) {
            handleMinerEvolve(ref, store, button.substring(BUTTON_MINER_EVOLVE_PREFIX.length()));
        } else if (button.startsWith(BUTTON_BUY_UPGRADE_PREFIX)) {
            handleBuyUpgrade(ref, store, button.substring(BUTTON_BUY_UPGRADE_PREFIX.length()));
        }
    }

    // ==================== Miner Handlers (from MineUpgradePage) ====================

    private void handleBuyMiner(Ref<EntityStore> ref, Store<EntityStore> store, String mineId) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        long cost = getMinerBuyCost();
        MinePlayerProgress.MinerPurchaseResult result = mineProgress.purchaseMiner(mineId, cost);
        if (result == MinePlayerProgress.MinerPurchaseResult.ALREADY_OWNED) {
            player.sendMessage(Message.raw("Already own this miner!"));
            return;
        }
        if (result == MinePlayerProgress.MinerPurchaseResult.INSUFFICIENT_CRYSTALS) {
            player.sendMessage(Message.raw("Not enough crystals!"));
            return;
        }

        markDirty();
        syncBoughtMiner(store, mineId);

        player.sendMessage(Message.raw("Bought miner for " + getMineName(mineId) + "!"));
        sendRefresh(ref, store);
    }

    private void handleMinerSpeedUpgrade(Ref<EntityStore> ref, Store<EntityStore> store, String mineId) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        MinePlayerProgress.MinerProgressSnapshot minerState = mineProgress.getMinerSnapshot(mineId);
        int speedLevel = minerState.speedLevel();

        long cost = getMinerSpeedCost(speedLevel, minerState.stars());
        MinePlayerProgress.MinerSpeedUpgradeResult result =
            mineProgress.upgradeMinerSpeed(mineId, cost, MINER_MAX_SPEED_PER_STAR);
        if (result == MinePlayerProgress.MinerSpeedUpgradeResult.NO_MINER) return;
        if (result == MinePlayerProgress.MinerSpeedUpgradeResult.SPEED_MAXED) {
            player.sendMessage(Message.raw("Speed maxed! Evolve to continue."));
            return;
        }
        if (result == MinePlayerProgress.MinerSpeedUpgradeResult.INSUFFICIENT_CRYSTALS) {
            player.sendMessage(Message.raw("Not enough crystals!"));
            return;
        }

        markDirty();
        syncMinerSpeed(mineId);

        player.sendMessage(Message.raw(getMineName(mineId) + " Miner speed -> Lv " + (speedLevel + 1) + "!"));
        sendRefresh(ref, store);
    }

    private void handleMinerEvolve(Ref<EntityStore> ref, Store<EntityStore> store, String mineId) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        MinePlayerProgress.MinerProgressSnapshot minerState = mineProgress.getMinerSnapshot(mineId);
        int stars = minerState.stars();

        long cost = getMinerEvolveCost(stars);
        MinePlayerProgress.MinerEvolutionResult result =
            mineProgress.evolveMiner(mineId, cost, MINER_MAX_SPEED_PER_STAR, MINER_MAX_STARS);
        if (result == MinePlayerProgress.MinerEvolutionResult.NO_MINER) return;
        if (result == MinePlayerProgress.MinerEvolutionResult.SPEED_NOT_MAXED) {
            player.sendMessage(Message.raw("Max speed first before evolving!"));
            return;
        }
        if (result == MinePlayerProgress.MinerEvolutionResult.STAR_MAXED) {
            player.sendMessage(Message.raw("Already max stars!"));
            return;
        }
        if (result == MinePlayerProgress.MinerEvolutionResult.INSUFFICIENT_CRYSTALS) {
            player.sendMessage(Message.raw("Not enough crystals!"));
            return;
        }

        markDirty();
        syncMinerEvolution(store, mineId);

        player.sendMessage(Message.raw(getMineName(mineId) + " Miner evolved to Star " + (stars + 1) + "!"));
        sendRefresh(ref, store);
    }

    // ==================== Upgrade Handlers (from MineUpgradePage) ====================

    private void handleBuyUpgrade(Ref<EntityStore> ref, Store<EntityStore> store, String typeName) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        MineUpgradeType type;
        try {
            type = MineUpgradeType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            return;
        }

        boolean success = mineProgress.purchaseUpgrade(type);
        if (!success) {
            int level = mineProgress.getUpgradeLevel(type);
            if (level >= type.getMaxLevel()) {
                player.sendMessage(Message.raw("Already maxed!"));
            } else {
                player.sendMessage(Message.raw("Not enough crystals!"));
            }
            return;
        }

        markDirty();

        int displayIndex = type.ordinal();
        player.sendMessage(Message.raw("Upgraded " + UPGRADE_DISPLAY_NAMES[displayIndex] + " to Lv " + mineProgress.getUpgradeLevel(type) + "!"));
        sendRefresh(ref, store);
    }

    private void handleResetAll(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        if (!PermissionUtils.isOp(playerRef.getUuid())) return;

        for (MineUpgradeType type : MineUpgradeType.values()) {
            mineProgress.setUpgradeLevel(type, 0);
        }

        markDirty();
        player.sendMessage(Message.raw("All upgrades have been reset."));
        sendRefresh(ref, store);
    }

    // ==================== Helpers ====================

    private void sendRefresh(Ref<EntityStore> ref, Store<EntityStore> store) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();
        cmd.clear("#MinerEntries");
        cmd.clear("#UpgradeItems");
        populateCrystals(cmd);
        populateMinerTab(cmd, evt);
        populateUpgradeTab(cmd, evt);
        this.sendUpdate(cmd, evt, false);
    }

    private void markDirty() {
        MinePlayerStore mineStore = ParkourAscendPlugin.getInstance().getMinePlayerStore();
        if (mineStore != null) {
            mineStore.markDirty(playerRef.getUuid());
        }
    }

    private String getEffectDescription(MineUpgradeType type, int level) {
        double effect = type.getEffect(level);
        return switch (type) {
            case MINING_SPEED -> "Speed: " + String.format("%.1f", effect) + "x";
            case BAG_CAPACITY -> "Capacity: " + (int) effect + " blocks";
            case MULTI_BREAK -> "Chance: " + (int) effect + "%";
        };
    }

    private String getMineName(String mineId) {
        MineConfigStore configStore = ParkourAscendPlugin.getInstance().getMineConfigStore();
        if (configStore != null) {
            Mine mine = configStore.getMine(mineId);
            if (mine != null) return mine.getName();
        }
        return mineId;
    }

    private static long getMinerBuyCost() {
        return 1000L;
    }

    private static long getMinerSpeedCost(int speedLevel, int stars) {
        int totalLevel = stars * MINER_MAX_SPEED_PER_STAR + speedLevel;
        return Math.round(50 * Math.pow(1.15, totalLevel));
    }

    private static long getMinerEvolveCost(int stars) {
        return Math.round(5000 * Math.pow(3, stars));
    }

    private void syncBoughtMiner(Store<EntityStore> store, String mineId) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) return;
        MineRobotManager robotManager = plugin.getMineRobotManager();
        if (robotManager == null || store.getExternalData() == null) return;
        robotManager.syncPurchasedMiner(playerRef.getUuid(), mineId, store.getExternalData().getWorld());
    }

    private void syncMinerSpeed(String mineId) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) return;
        MineRobotManager robotManager = plugin.getMineRobotManager();
        if (robotManager == null) return;
        MinePlayerProgress.MinerProgressSnapshot minerState = mineProgress.getMinerSnapshot(mineId);
        robotManager.syncMinerSpeed(playerRef.getUuid(), mineId, minerState.speedLevel());
    }

    private void syncMinerEvolution(Store<EntityStore> store, String mineId) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) return;
        MineRobotManager robotManager = plugin.getMineRobotManager();
        if (robotManager == null || store.getExternalData() == null) return;
        MinePlayerProgress.MinerProgressSnapshot minerState = mineProgress.getMinerSnapshot(mineId);
        robotManager.syncMinerEvolution(
            playerRef.getUuid(), mineId,
            minerState.speedLevel(), minerState.stars(),
            store.getExternalData().getWorld()
        );
    }
}
```

**Step 2: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/mine/ui/MinePage.java
git commit -m "feat(mine): add MinePage with tabbed Miner/Upgrade views"
```

---

### Task 4: Wire all entry points to new MinePage

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ParkourAscendPlugin.java:935` (interaction registration)
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/mine/command/MineCommand.java:100` (`/mine upgrades` command)

**Step 1: Update the interaction registration in ParkourAscendPlugin.java**

Find the `"Mine_Upgrades_Interaction"` registration (line 935). Two changes:
1. `MineUpgradePage` → `MinePage`
2. Add `getMineConfigStore() != null` to the activation predicate (currently only checks `getMinePlayerStore()`, but MinePage's Miner tab depends on MineConfigStore)

```java
// Before (line 935-942):
registry.register("Mine_Upgrades_Interaction",
    AscendDevInteraction.class, AscendDevInteraction.codec(() -> new AscendDevInteraction(
        (ref, store, playerRef, plugin) -> {
            MinePlayerProgress progress = plugin.getMinePlayerStore().getOrCreatePlayer(playerRef.getUuid());
            return new io.hyvexa.ascend.mine.ui.MineUpgradePage(playerRef, progress);
        },
        (plugin, player) -> plugin.getMinePlayerStore() != null,
        true, true)));

// After:
registry.register("Mine_Upgrades_Interaction",
    AscendDevInteraction.class, AscendDevInteraction.codec(() -> new AscendDevInteraction(
        (ref, store, playerRef, plugin) -> {
            MinePlayerProgress progress = plugin.getMinePlayerStore().getOrCreatePlayer(playerRef.getUuid());
            return new io.hyvexa.ascend.mine.ui.MinePage(playerRef, progress);
        },
        (plugin, player) -> plugin.getMinePlayerStore() != null && plugin.getMineConfigStore() != null,
        true, true)));
```

**Step 2: Update `/mine upgrades` command in MineCommand.java**

Find the `"upgrades"` case (line 99-101). Change `MineUpgradePage` → `MinePage`:

```java
// Before (line 99-101):
case "upgrades" -> {
    MineUpgradePage page = new MineUpgradePage(playerRef, progress);
    player.getPageManager().openCustomPage(ref, store, page);
}

// After:
case "upgrades" -> {
    MinePage page = new MinePage(playerRef, progress);
    player.getPageManager().openCustomPage(ref, store, page);
}
```

Also update the import at line 20: `MineUpgradePage` → `MinePage`.

**Step 3: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ParkourAscendPlugin.java \
       hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/mine/command/MineCommand.java
git commit -m "feat(mine): wire pickaxe interaction and /mine upgrades to tabbed MinePage"
```

---

---

### Task 5: Smoke test

**Step 1: Build the project**

```bash
cmd.exe /c "gradlew.bat build"
```

Verify: no compilation errors. If `.ui` parsing errors appear in test output, check for invalid element IDs (no underscores), invalid enum values, or syntax issues in the two new `.ui` files.

**Step 2: Manual verification checklist**

After deploying to a test server:
- [ ] Right-click pickaxe → new tabbed Mine page opens
- [ ] "Miner" tab active by default, shows mine cards
- [ ] Click "Upgrade" tab → content switches, tab styling updates
- [ ] Click "Miner" tab → switches back
- [ ] `/mine upgrades` → opens same tabbed page
- [ ] Buy miner / upgrade speed / evolve actions work
- [ ] Buy global upgrade works from Upgrade tab
- [ ] Crystal balance updates after purchases
- [ ] Locked mines show "Mine Locked" overlay
- [ ] Close button works

---

## Notes

1. **Old files**: `MineUpgradePage.java` and `Ascend_MineUpgrade.ui` remain in the codebase but are no longer referenced by any entry point. Can be cleaned up in a follow-up.

2. **5 mines assumption**: The Miner tab dynamically iterates `configStore.listMinesSorted()`, so it handles any number of mines. The "5 mines" from the request is the expected initial count.
