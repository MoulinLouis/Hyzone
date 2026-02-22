# Purge Weapon Upgrade Single-Page Redesign — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Merge the two-page weapon upgrade flow (weapon list → weapon detail) into a single page with an inline detail panel below the weapon grid.

**Architecture:** Rewrite `Purge_WeaponSelect.ui` to include a hidden `#DetailPanel` section below `#WeaponList`. Merge all upgrade logic from `PurgeWeaponUpgradePage` into `PurgeWeaponSelectPage`. Delete the old upgrade page/UI files. ADMIN mode is unchanged (still navigates to `PurgeWeaponAdminPage`).

**Tech Stack:** Hytale Custom UI (.ui files), Java (InteractiveCustomUIPage)

---

### Task 1: Rewrite `Purge_WeaponSelect.ui` with detail panel

**Files:**
- Modify: `hyvexa-purge/src/main/resources/Common/UI/Custom/Pages/Purge_WeaponSelect.ui`

**Step 1: Rewrite the UI file**

Replace the entire file with this layout — weapon grid on top, detail panel below (hidden by default):

```
$C = "../Common.ui";

$C.@PageOverlay {
  SceneBlur {}

  Group #WeaponSelectWindow {
    Anchor: (Width: 420, Height: 380);
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

      Label #Title {
        Anchor: (Left: 20, Top: 16, Width: 300, Height: 24);
        Style: (FontSize: 18, TextColor: #f0f4f8, RenderBold: true);
        Text: "Weapon Upgrade";
      }

      TextButton #BackButton {
        Anchor: (Right: 12, Top: 12, Width: 28, Height: 28);
        Text: "X";
        Style: TextButtonStyle(
          Default: (Background: #3b1219, LabelStyle: (FontSize: 14, TextColor: #ef4444, RenderBold: true, HorizontalAlignment: Center, VerticalAlignment: Center)),
          Hovered: (Background: #5c1d28, LabelStyle: (FontSize: 14, TextColor: #f87171, RenderBold: true, HorizontalAlignment: Center, VerticalAlignment: Center)),
          Pressed: (Background: #2d0f14, LabelStyle: (FontSize: 14, TextColor: #ef4444, RenderBold: true, HorizontalAlignment: Center, VerticalAlignment: Center)),
          Sounds: $C.@ButtonSounds,
        );
      }
    }

    // === WEAPON LIST ===
    Group #Content {
      Anchor: (Left: 0, Right: 0);
      FlexWeight: 1;
      LayoutMode: Top;
      Padding: (Left: 14, Right: 14, Top: 10, Bottom: 10);

      Label #Subtitle {
        Style: (FontSize: 14, TextColor: #9fb0ba);
        Text: "Choose a weapon to upgrade.";
      }

      Group #WeaponList {
        Anchor: (Left: 0, Right: 0, Top: 10);
        FlexWeight: 1;
        LayoutMode: LeftCenterWrap;
      }
    }

    // === DETAIL PANEL (hidden until weapon selected) ===
    Group #DetailPanel {
      Anchor: (Left: 0, Right: 0, Height: 110);
      LayoutMode: Full;
      Background: #0a1018;
      Visible: false;

      // Top separator line
      Group {
        Anchor: (Left: 0, Right: 0, Top: 0, Height: 1);
        Background: #ffffff(0.06);
      }

      // Icon on the left
      Group #DetailIcon {
        Anchor: (Left: 16, Top: 16, Width: 48, Height: 48);
        Background: (TexturePath: "../Textures/golem_icon.png");
      }

      // Info column (name + stars + damage)
      Group #DetailInfo {
        Anchor: (Left: 76, Top: 12, Width: 200, Height: 80);
        LayoutMode: Top;

        Label #DetailName {
          Style: (FontSize: 16, RenderBold: true, TextColor: #f0f4f8);
          Text: "";
        }

        // Star rating
        Group #DetailStars {
          Anchor: (Top: 4, Height: 20);
          LayoutMode: Left;

          Group {
            Anchor: (Width: 20, Height: 20);
            LayoutMode: Full;
            Group #DS0F { Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0); Background: (TexturePath: "../Textures/star.png"); Visible: false; }
            Group #DS0H { Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0); Background: (TexturePath: "../Textures/half_star.png"); Visible: false; }
            Group #DS0E { Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0); Background: (TexturePath: "../Textures/empty_star.png"); }
          }

          Group {
            Anchor: (Left: 2, Width: 20, Height: 20);
            LayoutMode: Full;
            Group #DS1F { Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0); Background: (TexturePath: "../Textures/star.png"); Visible: false; }
            Group #DS1H { Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0); Background: (TexturePath: "../Textures/half_star.png"); Visible: false; }
            Group #DS1E { Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0); Background: (TexturePath: "../Textures/empty_star.png"); }
          }

          Group {
            Anchor: (Left: 2, Width: 20, Height: 20);
            LayoutMode: Full;
            Group #DS2F { Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0); Background: (TexturePath: "../Textures/star.png"); Visible: false; }
            Group #DS2H { Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0); Background: (TexturePath: "../Textures/half_star.png"); Visible: false; }
            Group #DS2E { Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0); Background: (TexturePath: "../Textures/empty_star.png"); }
          }

          Group {
            Anchor: (Left: 2, Width: 20, Height: 20);
            LayoutMode: Full;
            Group #DS3F { Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0); Background: (TexturePath: "../Textures/star.png"); Visible: false; }
            Group #DS3H { Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0); Background: (TexturePath: "../Textures/half_star.png"); Visible: false; }
            Group #DS3E { Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0); Background: (TexturePath: "../Textures/empty_star.png"); }
          }

          Group {
            Anchor: (Left: 2, Width: 20, Height: 20);
            LayoutMode: Full;
            Group #DS4F { Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0); Background: (TexturePath: "../Textures/star.png"); Visible: false; }
            Group #DS4H { Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0); Background: (TexturePath: "../Textures/half_star.png"); Visible: false; }
            Group #DS4E { Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0); Background: (TexturePath: "../Textures/empty_star.png"); }
          }
        }

        Label #DetailDamage {
          Anchor: (Top: 4);
          Style: (FontSize: 13, TextColor: #9fb0ba);
          Text: "";
        }
      }

      // Right side: upgrade button + status
      Group #DetailActions {
        Anchor: (Right: 16, Top: 16, Width: 140, Height: 80);
        LayoutMode: Top;

        Group #DetailUpgradeGroup {
          Anchor: (Left: 0, Right: 0, Height: 36);

          TextButton #UpgradeButton {
            Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0);
            Text: "UPGRADE";
            Style: TextButtonStyle(
              Default: (Background: #0c2416, LabelStyle: (FontSize: 13, RenderBold: true, TextColor: #86efac, HorizontalAlignment: Center, VerticalAlignment: Center)),
              Hovered: (Background: #124026, LabelStyle: (FontSize: 13, RenderBold: true, TextColor: #bbf7d0, HorizontalAlignment: Center, VerticalAlignment: Center)),
              Pressed: (Background: #091a10, LabelStyle: (FontSize: 13, RenderBold: true, TextColor: #86efac, HorizontalAlignment: Center, VerticalAlignment: Center)),
              Sounds: $C.@ButtonSounds,
            );
          }
        }

        Label #DetailStatus {
          Anchor: (Top: 6);
          Style: (FontSize: 12, TextColor: #6b7b8a, HorizontalAlignment: Center);
          Text: "";
        }

        // Reset button (OP only)
        Group #DetailResetGroup {
          Anchor: (Top: 6, Left: 0, Right: 0, Height: 28);
          Visible: false;

          TextButton #ResetButton {
            Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0);
            Text: "RESET";
            Style: TextButtonStyle(
              Default: (Background: #3b1219, LabelStyle: (FontSize: 11, RenderBold: true, TextColor: #ef4444, HorizontalAlignment: Center, VerticalAlignment: Center)),
              Hovered: (Background: #5c1d28, LabelStyle: (FontSize: 11, RenderBold: true, TextColor: #f87171, HorizontalAlignment: Center, VerticalAlignment: Center)),
              Pressed: (Background: #2d0f14, LabelStyle: (FontSize: 11, RenderBold: true, TextColor: #ef4444, HorizontalAlignment: Center, VerticalAlignment: Center)),
              Sounds: $C.@ButtonSounds,
            );
          }
        }
      }
    }
  }
}
```

Key points:
- `#DetailPanel` starts with `Visible: false` — window is 380px but detail takes 110px when visible
- Detail star IDs use `#DS0F`/`#DS0H`/`#DS0E` prefix (D for Detail) to avoid clashing with card star IDs `#S0F` etc.
- `#DetailUpgradeGroup` wraps the button (same Group+TextButton pattern as existing)
- `#DetailResetGroup` hidden by default (OP only)

**Step 2: Commit**

```bash
git add hyvexa-purge/src/main/resources/Common/UI/Custom/Pages/Purge_WeaponSelect.ui
git commit -m "Rewrite Purge_WeaponSelect.ui with inline detail panel"
```

---

### Task 2: Rewrite `PurgeWeaponSelectPage.java` with merged upgrade logic

**Files:**
- Modify: `hyvexa-purge/src/main/java/io/hyvexa/purge/ui/PurgeWeaponSelectPage.java`

**Step 1: Rewrite the Java page class**

The new class must:
1. Keep existing `Mode.ADMIN` / `Mode.PLAYER` enum
2. Add `String selectedWeaponId` field (null = no weapon selected)
3. Add `boolean isOp` field
4. In PLAYER mode: `handleSelect()` no longer opens a new page — instead calls `sendRefresh()` to update `#DetailPanel` inline
5. In ADMIN mode: `handleSelect()` still navigates to `PurgeWeaponAdminPage` (unchanged)
6. Add `BUTTON_UPGRADE = "Upgrade"` and `BUTTON_RESET = "Reset"` constants
7. Move `handleUpgrade()` and `handleReset()` from `PurgeWeaponUpgradePage` into this class
8. `sendRefresh()` method: creates new `UICommandBuilder` + `UIEventBuilder`, populates detail panel + re-binds events, calls `this.sendUpdate()`
9. `populateDetailPanel()` method: sets `#DetailPanel.Visible: true`, populates `#DetailName`, detail stars (`#DS0F`–`#DS4E`), `#DetailDamage`, upgrade button text/visibility, status text, reset button visibility
10. On upgrade success: also update the card's star bar in `#WeaponList` (need to track the card index for the selected weapon)

Here's the complete implementation:

```java
package io.hyvexa.purge.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.purge.data.PurgeScrapStore;
import io.hyvexa.purge.data.PurgeWeaponUpgradeStore;
import io.hyvexa.purge.manager.PurgeInstanceManager;
import io.hyvexa.purge.manager.PurgeWaveConfigManager;
import io.hyvexa.purge.manager.PurgeWeaponConfigManager;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PurgeWeaponSelectPage extends InteractiveCustomUIPage<PurgeWeaponSelectPage.PurgeWeaponSelectData> {

    public enum Mode { ADMIN, PLAYER }

    private static final String BUTTON_BACK = "Back";
    private static final String BUTTON_SELECT_PREFIX = "Select:";
    private static final String BUTTON_UPGRADE = "Upgrade";
    private static final String BUTTON_RESET = "Reset";

    private final Mode mode;
    private final UUID playerId;
    private final PurgeWeaponConfigManager weaponConfigManager;
    private final PurgeWaveConfigManager waveConfigManager;
    private final PurgeInstanceManager instanceManager;
    private final boolean isOp;

    // Track selected weapon for inline upgrade (PLAYER mode only)
    private String selectedWeaponId;
    // Ordered list of weapon IDs matching their card index in #WeaponList
    private List<String> weaponIdOrder;

    public PurgeWeaponSelectPage(@Nonnull PlayerRef playerRef,
                                  Mode mode,
                                  UUID playerId,
                                  PurgeWeaponConfigManager weaponConfigManager,
                                  PurgeWaveConfigManager waveConfigManager,
                                  PurgeInstanceManager instanceManager,
                                  boolean isOp) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PurgeWeaponSelectData.CODEC);
        this.mode = mode;
        this.playerId = playerId;
        this.weaponConfigManager = weaponConfigManager;
        this.waveConfigManager = waveConfigManager;
        this.instanceManager = instanceManager;
        this.isOp = isOp;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder,
                      @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Purge_WeaponSelect.ui");

        if (mode == Mode.ADMIN) {
            uiCommandBuilder.set("#Title.Text", "Weapon Configuration");
            uiCommandBuilder.set("#Subtitle.Text", "Select a weapon to configure.");
        }

        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BACK), false);

        buildWeaponList(uiCommandBuilder, uiEventBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull PurgeWeaponSelectData data) {
        super.handleDataEvent(ref, store, data);
        String button = data.getButton();
        if (button == null) return;

        if (BUTTON_BACK.equals(button)) {
            handleBack(ref, store);
            return;
        }
        if (button.startsWith(BUTTON_SELECT_PREFIX)) {
            String weaponId = button.substring(BUTTON_SELECT_PREFIX.length());
            handleSelect(weaponId, ref, store);
            return;
        }
        if (BUTTON_UPGRADE.equals(button)) {
            handleUpgrade(ref, store);
            return;
        }
        if (BUTTON_RESET.equals(button)) {
            handleReset(ref, store);
        }
    }

    private void handleBack(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (mode == Mode.ADMIN) {
            Player player = store.getComponent(ref, Player.getComponentType());
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (player != null && playerRef != null) {
                player.getPageManager().openCustomPage(ref, store,
                        new PurgeAdminIndexPage(playerRef, waveConfigManager, instanceManager, weaponConfigManager));
            }
        } else {
            close();
        }
    }

    private void handleSelect(String weaponId, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (mode == Mode.ADMIN) {
            Player player = store.getComponent(ref, Player.getComponentType());
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (player == null || playerRef == null) return;
            player.getPageManager().openCustomPage(ref, store,
                    new PurgeWeaponAdminPage(playerRef, weaponId, weaponConfigManager, waveConfigManager, instanceManager));
        } else {
            // PLAYER mode: show detail panel inline
            this.selectedWeaponId = weaponId;
            sendRefresh();
        }
    }

    private void handleUpgrade(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (selectedWeaponId == null) return;

        Player player = store.getComponent(ref, Player.getComponentType());
        PurgeWeaponUpgradeStore.UpgradeResult result =
                PurgeWeaponUpgradeStore.getInstance().tryUpgrade(playerId, selectedWeaponId, weaponConfigManager);

        String displayName = weaponConfigManager.getDisplayName(selectedWeaponId);
        if (player != null) {
            switch (result) {
                case SUCCESS -> {
                    int newLevel = PurgeWeaponUpgradeStore.getInstance().getLevel(playerId, selectedWeaponId);
                    String stars = weaponConfigManager.getStarDisplay(newLevel);
                    int dmg = weaponConfigManager.getDamage(selectedWeaponId, newLevel);
                    player.sendMessage(Message.raw(displayName + " upgraded to " + stars + " star (" + dmg + " dmg)!"));
                }
                case MAX_LEVEL -> player.sendMessage(Message.raw(displayName + " is already at max level!"));
                case NOT_ENOUGH_SCRAP -> player.sendMessage(Message.raw("Not enough scrap to upgrade."));
            }
        }
        sendRefresh();
    }

    private void handleReset(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (selectedWeaponId == null) return;

        PurgeWeaponUpgradeStore.getInstance().setLevel(playerId, selectedWeaponId, 0);
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.sendMessage(Message.raw(weaponConfigManager.getDisplayName(selectedWeaponId) + " reset to level 0."));
        }
        sendRefresh();
    }

    private void sendRefresh() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();

        // Re-bind back button
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BACK), false);

        // Re-bind all weapon card buttons
        if (weaponIdOrder != null) {
            for (int i = 0; i < weaponIdOrder.size(); i++) {
                String wId = weaponIdOrder.get(i);
                String root = "#WeaponList[" + i + "]";
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                        root + " #SelectButton",
                        EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SELECT_PREFIX + wId), false);

                // Update card star bars (in case upgrade changed them)
                if (mode == Mode.PLAYER) {
                    int cardLevel = PurgeWeaponUpgradeStore.getInstance().getLevel(playerId, wId);
                    updateCardStarDisplay(commandBuilder, root, cardLevel);
                }

                // Highlight selected card
                if (wId.equals(selectedWeaponId)) {
                    commandBuilder.set(root + " .OutlineColor", "#f59e0b");
                    commandBuilder.set(root + " .OutlineSize", 1);
                } else {
                    commandBuilder.set(root + " .OutlineColor", "#ffffff(0.08)");
                    commandBuilder.set(root + " .OutlineSize", 1);
                }
            }
        }

        // Populate detail panel
        if (selectedWeaponId != null && mode == Mode.PLAYER) {
            populateDetailPanel(commandBuilder, eventBuilder);
        }

        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    private void populateDetailPanel(UICommandBuilder cmd, UIEventBuilder evt) {
        cmd.set("#DetailPanel.Visible", true);
        cmd.set("#DetailName.Text", weaponConfigManager.getDisplayName(selectedWeaponId));

        int currentLevel = PurgeWeaponUpgradeStore.getInstance().getLevel(playerId, selectedWeaponId);
        int maxLevel = weaponConfigManager.getMaxLevel();
        int currentDmg = weaponConfigManager.getDamage(selectedWeaponId, currentLevel);
        long scrap = PurgeScrapStore.getInstance().getScrap(playerId);

        // Detail star display
        updateDetailStarDisplay(cmd, currentLevel);

        if (currentLevel >= maxLevel) {
            cmd.set("#DetailDamage.Text", currentDmg + " dmg - MAX LEVEL");
            cmd.set("#DetailDamage.Style.TextColor", "#fbbf24");
            cmd.set("#DetailUpgradeGroup.Visible", false);
            cmd.set("#DetailStatus.Text", "Max level reached!");
            cmd.set("#DetailStatus.Style.TextColor", "#fbbf24");
        } else {
            int nextLevel = currentLevel + 1;
            int nextDmg = weaponConfigManager.getDamage(selectedWeaponId, nextLevel);
            long nextCost = weaponConfigManager.getCost(selectedWeaponId, nextLevel);
            cmd.set("#DetailDamage.Text", currentDmg + " -> " + nextDmg + " dmg");
            cmd.set("#DetailDamage.Style.TextColor", "#9fb0ba");

            if (scrap >= nextCost) {
                cmd.set("#DetailUpgradeGroup.Visible", true);
                cmd.set("#UpgradeButton.Text", "UPGRADE - " + nextCost + " scrap");
                cmd.set("#DetailStatus.Text", "");
                evt.addEventBinding(CustomUIEventBindingType.Activating, "#UpgradeButton",
                        EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_UPGRADE), false);
            } else {
                cmd.set("#DetailUpgradeGroup.Visible", false);
                cmd.set("#DetailStatus.Text", "Not enough scrap (" + scrap + "/" + nextCost + ")");
                cmd.set("#DetailStatus.Style.TextColor", "#ef4444");
            }
        }

        // OP reset button
        if (isOp && currentLevel > 0) {
            cmd.set("#DetailResetGroup.Visible", true);
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#ResetButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_RESET), false);
        } else {
            cmd.set("#DetailResetGroup.Visible", false);
        }
    }

    private void buildWeaponList(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.clear("#WeaponList");
        weaponIdOrder = new ArrayList<>();

        List<String> weaponIds = new ArrayList<>(weaponConfigManager.getWeaponIds());
        weaponIds.sort(String::compareTo);

        int index = 0;
        for (String weaponId : weaponIds) {
            int playerLevel = 0;
            if (mode == Mode.PLAYER && playerId != null) {
                playerLevel = PurgeWeaponUpgradeStore.getInstance().getLevel(playerId, weaponId);
                if (playerLevel < 1) continue; // Only show owned weapons
            }

            String root = "#WeaponList[" + index + "]";
            commandBuilder.append("#WeaponList", "Pages/Purge_WeaponSelectEntry.ui");

            if (mode == Mode.ADMIN) {
                commandBuilder.set(root + " #WeaponName.Visible", true);
                commandBuilder.set(root + " #WeaponName.Text", weaponConfigManager.getDisplayName(weaponId));
            } else {
                commandBuilder.set(root + " #StarBar.Visible", true);
                updateCardStarDisplay(commandBuilder, root, playerLevel);
            }

            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    root + " #SelectButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SELECT_PREFIX + weaponId), false);

            weaponIdOrder.add(weaponId);
            index++;
        }
    }

    private void updateCardStarDisplay(UICommandBuilder cmd, String root, int level) {
        int fullStars = level / 2;
        boolean hasHalf = level % 2 == 1;
        for (int p = 0; p < 5; p++) {
            cmd.set(root + " #S" + p + "F.Visible", p < fullStars);
            cmd.set(root + " #S" + p + "H.Visible", p == fullStars && hasHalf);
            cmd.set(root + " #S" + p + "E.Visible", p >= fullStars && !(p == fullStars && hasHalf));
        }
    }

    private void updateDetailStarDisplay(UICommandBuilder cmd, int level) {
        int fullStars = level / 2;
        boolean hasHalf = level % 2 == 1;
        for (int p = 0; p < 5; p++) {
            cmd.set("#DS" + p + "F.Visible", p < fullStars);
            cmd.set("#DS" + p + "H.Visible", p == fullStars && hasHalf);
            cmd.set("#DS" + p + "E.Visible", p >= fullStars && !(p == fullStars && hasHalf));
        }
    }

    public static class PurgeWeaponSelectData extends ButtonEventData {
        public static final BuilderCodec<PurgeWeaponSelectData> CODEC =
                BuilderCodec.<PurgeWeaponSelectData>builder(PurgeWeaponSelectData.class, PurgeWeaponSelectData::new)
                        .addField(new KeyedCodec<>(ButtonEventData.KEY_BUTTON, Codec.STRING),
                                (data, value) -> data.button = value, data -> data.button)
                        .build();

        private String button;

        @Override
        public String getButton() {
            return button;
        }
    }
}
```

Key changes from existing code:
- Constructor gains `boolean isOp` parameter
- `selectedWeaponId` + `weaponIdOrder` fields track state
- `handleSelect()` in PLAYER mode sets `selectedWeaponId` and calls `sendRefresh()` instead of opening a new page
- `handleUpgrade()` and `handleReset()` moved from `PurgeWeaponUpgradePage`
- `sendRefresh()` rebuilds event bindings + updates card stars + populates detail panel
- `populateDetailPanel()` mirrors the old `PurgeWeaponUpgradePage.populateDisplay()` logic but targets `#Detail*` elements
- Card highlight: selected card gets orange outline (`#f59e0b`), others get subtle outline

**Step 2: Commit**

```bash
git add hyvexa-purge/src/main/java/io/hyvexa/purge/ui/PurgeWeaponSelectPage.java
git commit -m "Merge weapon upgrade logic into PurgeWeaponSelectPage"
```

---

### Task 3: Update all callers of the old constructor + delete old files

**Files:**
- Modify: `hyvexa-purge/src/main/java/io/hyvexa/purge/ui/PurgeAdminIndexPage.java` (if it creates PurgeWeaponSelectPage)
- Modify: `hyvexa-purge/src/main/java/io/hyvexa/purge/command/PurgeCommand.java` (if it creates PurgeWeaponSelectPage)
- Modify: any other file that creates `PurgeWeaponSelectPage` or `PurgeWeaponUpgradePage`
- Delete: `hyvexa-purge/src/main/java/io/hyvexa/purge/ui/PurgeWeaponUpgradePage.java`
- Delete: `hyvexa-purge/src/main/resources/Common/UI/Custom/Pages/Purge_WeaponUpgrade.ui`

**Step 1: Find all callers**

Search for:
- `new PurgeWeaponSelectPage(` — update to pass `isOp` as last arg
- `new PurgeWeaponUpgradePage(` — these should no longer exist (the only caller was the old `PurgeWeaponSelectPage.handleSelect()` which is already removed)
- `PurgeWeaponUpgradePage` imports — remove

For each caller of `new PurgeWeaponSelectPage(...)`:
- The old constructor was: `(playerRef, mode, playerId, weaponConfigManager, waveConfigManager, instanceManager)`
- The new constructor is: `(playerRef, mode, playerId, weaponConfigManager, waveConfigManager, instanceManager, isOp)`
- For ADMIN mode callers: pass `false` for isOp (doesn't matter, detail panel not used in admin)
- For PLAYER mode callers: pass `PermissionUtils.isOp(player)` for isOp

**Step 2: Delete old files**

Remove:
- `hyvexa-purge/src/main/java/io/hyvexa/purge/ui/PurgeWeaponUpgradePage.java`
- `hyvexa-purge/src/main/resources/Common/UI/Custom/Pages/Purge_WeaponUpgrade.ui`

**Step 3: Commit**

```bash
git add -A hyvexa-purge/
git commit -m "Update callers for new PurgeWeaponSelectPage constructor, delete PurgeWeaponUpgradePage"
```
