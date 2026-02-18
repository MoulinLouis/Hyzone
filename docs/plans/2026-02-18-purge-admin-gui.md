# Purge Admin GUI Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add `/pu admin` command (OP only) that opens a category-based admin GUI, starting with a "Spawns" category for managing purge spawn points via UI.

**Architecture:** Follows the exact `/pk admin` pattern: command opens `PurgeAdminIndexPage` (category hub) which navigates to `PurgeSpawnAdminPage` (scrollable spawn list with add/delete). Uses `InteractiveCustomUIPage<T>` with `BuilderCodec` for all event handling. Dynamic list rows appended via `UICommandBuilder.append()`.

**Tech Stack:** Hytale Server API (InteractiveCustomUIPage, UICommandBuilder, UIEventBuilder, BuilderCodec), Hytale UI markup (.ui files)

---

### Task 1: Create Purge_AdminIndex.ui

**Files:**
- Create: `hyvexa-purge/src/main/resources/Common/UI/Custom/Pages/Purge_AdminIndex.ui`

**Step 1: Create the admin index UI file**

```ui
$C = "../Common.ui";

$C.@PageOverlay {
  SceneBlur {}

  Group #AdminIndexWindow {
    Anchor: (Width: 420, Height: 400);
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
        Anchor: (Left: 20, Top: 16, Width: 300, Height: 24);
        Style: (FontSize: 18, TextColor: #f0f4f8, RenderBold: true);
        Text: "Purge Admin";
      }
    }

    // === CONTENT ===
    Group #Content {
      Anchor: (Left: 0, Right: 0);
      FlexWeight: 1;
      LayoutMode: Top;
      Padding: (Left: 14, Right: 14, Top: 10, Bottom: 10);

      Label {
        Style: (FontSize: 14, TextColor: #9fb0ba);
        Text: "Manage Purge mode settings.";
      }

      Group {
        Anchor: (Left: 2, Right: 2, Top: 10, Height: 46, Width: 390);
        Background: $C.@InputBoxBackground;

        Button #SpawnsButton {
          Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0);
          LayoutMode: Left;
          Padding: (Left: 10, Right: 10, Top: 8, Bottom: 8);
          Background: #00000000;
          Style: ButtonStyle(
            Default: (Background: #00000000),
            Hovered: (Background: #ffffff(0.08)),
            Pressed: (Background: #ffffff(0.03)),
            Sounds: $C.@ButtonSounds,
          );

          Label #SpawnsLabel {
            Style: (FontSize: 16, RenderBold: true, TextColor: #93844c);
            Text: "Spawn Points";
          }
        }
      }
    }
  }
}

$C.@BackButton {}
```

**Step 2: Commit**

```bash
git add hyvexa-purge/src/main/resources/Common/UI/Custom/Pages/Purge_AdminIndex.ui
git commit -m "feat(purge): add admin index UI page"
```

---

### Task 2: Create PurgeAdminIndexPage.java

**Files:**
- Create: `hyvexa-purge/src/main/java/io/hyvexa/purge/ui/PurgeAdminIndexPage.java`

**Reference:** `hyvexa-parkour/src/main/java/io/hyvexa/parkour/ui/AdminIndexPage.java` (follow same pattern)

**Step 1: Create the Java page class**

```java
package io.hyvexa.purge.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.purge.manager.PurgeSpawnPointManager;

import javax.annotation.Nonnull;

public class PurgeAdminIndexPage extends InteractiveCustomUIPage<PurgeAdminIndexPage.PurgeAdminIndexData> {

    private static final String BUTTON_SPAWNS = "Spawns";
    private final PurgeSpawnPointManager spawnPointManager;

    public PurgeAdminIndexPage(@Nonnull PlayerRef playerRef, PurgeSpawnPointManager spawnPointManager) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PurgeAdminIndexData.CODEC);
        this.spawnPointManager = spawnPointManager;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Purge_AdminIndex.ui");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SpawnsButton",
                EventData.of(PurgeAdminIndexData.KEY_BUTTON, BUTTON_SPAWNS), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull PurgeAdminIndexData data) {
        super.handleDataEvent(ref, store, data);
        if (data.button == null) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        if (BUTTON_SPAWNS.equals(data.button)) {
            player.getPageManager().openCustomPage(ref, store,
                    new PurgeSpawnAdminPage(playerRef, spawnPointManager));
        }
    }

    public static class PurgeAdminIndexData {
        static final String KEY_BUTTON = "Button";

        public static final BuilderCodec<PurgeAdminIndexData> CODEC =
                BuilderCodec.<PurgeAdminIndexData>builder(PurgeAdminIndexData.class, PurgeAdminIndexData::new)
                        .addField(new KeyedCodec<>(KEY_BUTTON, Codec.STRING),
                                (data, value) -> data.button = value, data -> data.button)
                        .build();

        String button;
    }
}
```

**Step 2: Commit**

```bash
git add hyvexa-purge/src/main/java/io/hyvexa/purge/ui/PurgeAdminIndexPage.java
git commit -m "feat(purge): add PurgeAdminIndexPage class"
```

---

### Task 3: Create Purge_SpawnEntry.ui

**Files:**
- Create: `hyvexa-purge/src/main/resources/Common/UI/Custom/Pages/Purge_SpawnEntry.ui`

**Reference:** `hyvexa-parkour/src/main/resources/Common/UI/Custom/Pages/Parkour_AdminPlayerEntry.ui`

**Step 1: Create the spawn entry template**

Each row shows: `#ID` (left), `X, Y, Z (yaw)` (middle), delete button (right).

```ui
$C = "../Common.ui";

Group {
  LayoutMode: Left;
  Anchor: (Left: 2, Right: 2, Bottom: 6, Height: 44, Width: 470);
  Background: $C.@InputBoxBackground;
  Padding: (Left: 10, Right: 6, Top: 6, Bottom: 6);

  Label #SpawnId {
    Anchor: (Width: 40);
    Style: (FontSize: 13, RenderBold: true, TextColor: #93844c);
    Text: "#0";
  }

  Label #SpawnCoords {
    FlexWeight: 1;
    Style: (FontSize: 12, TextColor: #cfd7dc);
    Text: "0, 0, 0";
  }

  Group {
    Anchor: (Width: 32, Height: 32);

    TextButton #DeleteButton {
      Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0);
      Text: "X";
      Style: TextButtonStyle(
        Default: (
          Background: #00000000,
          LabelStyle: (FontSize: 14, RenderBold: true, TextColor: #ef4444, HorizontalAlignment: Center, VerticalAlignment: Center)
        ),
        Hovered: (
          Background: #ef4444(0.15),
          LabelStyle: (FontSize: 14, RenderBold: true, TextColor: #f87171, HorizontalAlignment: Center, VerticalAlignment: Center)
        ),
        Pressed: (
          Background: #ef4444(0.08),
          LabelStyle: (FontSize: 14, RenderBold: true, TextColor: #ef4444, HorizontalAlignment: Center, VerticalAlignment: Center)
        ),
        Sounds: $C.@ButtonSounds,
      );
    }
  }
}
```

**Step 2: Commit**

```bash
git add hyvexa-purge/src/main/resources/Common/UI/Custom/Pages/Purge_SpawnEntry.ui
git commit -m "feat(purge): add spawn entry UI template"
```

---

### Task 4: Create Purge_SpawnAdmin.ui

**Files:**
- Create: `hyvexa-purge/src/main/resources/Common/UI/Custom/Pages/Purge_SpawnAdmin.ui`

**Reference:** `hyvexa-parkour/src/main/resources/Common/UI/Custom/Pages/Parkour_AdminPlayers.ui` (scrollable list pattern)

**Step 1: Create the spawn admin UI file**

```ui
$C = "../Common.ui";

$C.@PageOverlay {
  SceneBlur {}

  Group #SpawnAdminWindow {
    Anchor: (Width: 520, Height: 520);
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
        Anchor: (Left: 20, Top: 16, Width: 300, Height: 24);
        Style: (FontSize: 18, TextColor: #f0f4f8, RenderBold: true);
        Text: "Spawn Points";
      }

      // Back button
      Group {
        Anchor: (Right: 16, Top: 10, Width: 72, Height: 32);

        TextButton #BackButton {
          Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0);
          Text: "< Back";
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
      Padding: (Left: 12, Right: 12, Top: 6, Bottom: 12);

      Label #SpawnCount {
        Style: (FontSize: 14, TextColor: #9fb0ba);
        Text: "0 spawn points configured.";
      }

      Label #EmptyText {
        Anchor: (Top: 6);
        Style: (FontSize: 13, TextColor: #9fb0ba);
        Text: "";
      }

      // Scrollable list
      Group #SpawnList {
        Anchor: (Width: 490, Top: 8);
        FlexWeight: 1;
        LayoutMode: TopScrolling;
        Padding: (Left: 6, Right: 6);
        ScrollbarStyle: $C.@DefaultScrollbarStyle;

        Group #SpawnCards {
          LayoutMode: Top;
        }
      }

      // Add button
      TextButton #AddSpawnButton {
        Anchor: (Height: 34, Width: 200, Top: 8);
        Text: "+ Add Spawn Here";
        Style: TextButtonStyle(
          Default: (
            Background: #1a5c2e(0.8),
            LabelStyle: (FontSize: 14, TextColor: #86efac, RenderBold: true, HorizontalAlignment: Center, VerticalAlignment: Center)
          ),
          Hovered: (
            Background: #22763a(0.9),
            LabelStyle: (FontSize: 14, TextColor: #bbf7d0, RenderBold: true, HorizontalAlignment: Center, VerticalAlignment: Center)
          ),
          Pressed: (
            Background: #15492a(0.8),
            LabelStyle: (FontSize: 14, TextColor: #86efac, RenderBold: true, HorizontalAlignment: Center, VerticalAlignment: Center)
          ),
          Sounds: $C.@ButtonSounds,
        );
      }
    }
  }
}

$C.@BackButton {}
```

**Step 2: Commit**

```bash
git add hyvexa-purge/src/main/resources/Common/UI/Custom/Pages/Purge_SpawnAdmin.ui
git commit -m "feat(purge): add spawn admin UI page"
```

---

### Task 5: Create PurgeSpawnAdminPage.java

**Files:**
- Create: `hyvexa-purge/src/main/java/io/hyvexa/purge/ui/PurgeSpawnAdminPage.java`

**Reference:** `hyvexa-parkour/src/main/java/io/hyvexa/parkour/ui/AdminPlayersPage.java` (dynamic list pattern with `commandBuilder.append()` + `commandBuilder.set()` + indexed event bindings)

**Step 1: Create the Java page class**

```java
package io.hyvexa.purge.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.purge.data.PurgeSpawnPoint;
import io.hyvexa.purge.manager.PurgeSpawnPointManager;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class PurgeSpawnAdminPage extends InteractiveCustomUIPage<PurgeSpawnAdminPage.PurgeSpawnAdminData> {

    private static final String BUTTON_BACK = "Back";
    private static final String BUTTON_ADD = "Add";
    private static final String BUTTON_DELETE_PREFIX = "Delete:";

    private final PurgeSpawnPointManager spawnPointManager;
    // Store ref/store for add-spawn (need player position)
    private Ref<EntityStore> lastRef;
    private Store<EntityStore> lastStore;

    public PurgeSpawnAdminPage(@Nonnull PlayerRef playerRef, PurgeSpawnPointManager spawnPointManager) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PurgeSpawnAdminData.CODEC);
        this.spawnPointManager = spawnPointManager;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        this.lastRef = ref;
        this.lastStore = store;
        uiCommandBuilder.append("Pages/Purge_SpawnAdmin.ui");
        bindStaticEvents(uiEventBuilder);
        buildSpawnList(uiCommandBuilder, uiEventBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull PurgeSpawnAdminData data) {
        super.handleDataEvent(ref, store, data);
        this.lastRef = ref;
        this.lastStore = store;
        if (data.button == null) {
            return;
        }
        if (BUTTON_BACK.equals(data.button)) {
            openIndex(ref, store);
            return;
        }
        if (BUTTON_ADD.equals(data.button)) {
            handleAdd(ref, store);
            return;
        }
        if (data.button.startsWith(BUTTON_DELETE_PREFIX)) {
            handleDelete(data.button.substring(BUTTON_DELETE_PREFIX.length()), ref, store);
        }
    }

    private void handleAdd(Ref<EntityStore> ref, Store<EntityStore> store) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null || transform.getPosition() == null) {
            sendMessage(store, ref, "Could not read your position.");
            return;
        }
        Vector3d pos = transform.getPosition();
        Vector3f rot = transform.getRotation();
        float yaw = rot != null ? rot.getY() : 0f;
        int id = spawnPointManager.addSpawnPoint(pos.getX(), pos.getY(), pos.getZ(), yaw);
        if (id > 0) {
            sendMessage(store, ref, "Spawn #" + id + " added at "
                    + String.format("%.1f, %.1f, %.1f", pos.getX(), pos.getY(), pos.getZ()));
        } else {
            sendMessage(store, ref, "Failed to add spawn point.");
        }
        sendRefresh();
    }

    private void handleDelete(String rawId, Ref<EntityStore> ref, Store<EntityStore> store) {
        int id;
        try {
            id = Integer.parseInt(rawId);
        } catch (NumberFormatException e) {
            return;
        }
        if (spawnPointManager.removeSpawnPoint(id)) {
            sendMessage(store, ref, "Spawn #" + id + " removed.");
        } else {
            sendMessage(store, ref, "Spawn #" + id + " not found.");
        }
        sendRefresh();
    }

    private void openIndex(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
                new PurgeAdminIndexPage(playerRef, spawnPointManager));
    }

    private void sendRefresh() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        bindStaticEvents(eventBuilder);
        buildSpawnList(commandBuilder, eventBuilder);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    private void bindStaticEvents(UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(PurgeSpawnAdminData.KEY_BUTTON, BUTTON_BACK), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#AddSpawnButton",
                EventData.of(PurgeSpawnAdminData.KEY_BUTTON, BUTTON_ADD), false);
    }

    private void buildSpawnList(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.clear("#SpawnCards");
        Collection<PurgeSpawnPoint> allSpawns = spawnPointManager.getAll();
        commandBuilder.set("#SpawnCount.Text", allSpawns.size() + " spawn point"
                + (allSpawns.size() != 1 ? "s" : "") + " configured.");

        if (allSpawns.isEmpty()) {
            commandBuilder.set("#EmptyText.Text", "Use the button below to add spawn points.");
            return;
        }
        commandBuilder.set("#EmptyText.Text", "");

        // Sort by ID for consistent ordering
        List<PurgeSpawnPoint> sorted = new ArrayList<>(allSpawns);
        sorted.sort(Comparator.comparingInt(PurgeSpawnPoint::id));

        int index = 0;
        for (PurgeSpawnPoint spawn : sorted) {
            commandBuilder.append("#SpawnCards", "Pages/Purge_SpawnEntry.ui");
            commandBuilder.set("#SpawnCards[" + index + "] #SpawnId.Text", "#" + spawn.id());
            commandBuilder.set("#SpawnCards[" + index + "] #SpawnCoords.Text",
                    String.format("%.1f, %.1f, %.1f (yaw: %.0f)", spawn.x(), spawn.y(), spawn.z(), spawn.yaw()));
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    "#SpawnCards[" + index + "] #DeleteButton",
                    EventData.of(PurgeSpawnAdminData.KEY_BUTTON, BUTTON_DELETE_PREFIX + spawn.id()), false);
            index++;
        }
    }

    private void sendMessage(Store<EntityStore> store, Ref<EntityStore> ref, String text) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.sendMessage(Message.raw(text));
        }
    }

    public static class PurgeSpawnAdminData {
        static final String KEY_BUTTON = "Button";

        public static final BuilderCodec<PurgeSpawnAdminData> CODEC =
                BuilderCodec.<PurgeSpawnAdminData>builder(PurgeSpawnAdminData.class, PurgeSpawnAdminData::new)
                        .addField(new KeyedCodec<>(KEY_BUTTON, Codec.STRING),
                                (data, value) -> data.button = value, data -> data.button)
                        .build();

        String button;
    }
}
```

**Step 2: Commit**

```bash
git add hyvexa-purge/src/main/java/io/hyvexa/purge/ui/PurgeSpawnAdminPage.java
git commit -m "feat(purge): add PurgeSpawnAdminPage with dynamic spawn list"
```

---

### Task 6: Modify PurgeCommand to add `admin` subcommand

**Files:**
- Modify: `hyvexa-purge/src/main/java/io/hyvexa/purge/command/PurgeCommand.java`

**Step 1: Add spawnPointManager field and update constructor**

The command needs access to `PurgeSpawnPointManager` to pass it to the admin page. Update the constructor to accept it alongside `sessionManager`.

In `PurgeCommand.java`:
- Add field: `private final PurgeSpawnPointManager spawnPointManager;`
- Update constructor to accept `PurgeSpawnPointManager spawnPointManager` as second param
- Add import for `PurgeSpawnPointManager`, `PermissionUtils`, `PurgeAdminIndexPage`, `TransformComponent`

**Step 2: Add `admin` case to the switch statement**

In `handleCommand()`, add to the switch block:
```java
case "admin" -> openAdminMenu(player, ref, store);
```

Update usage message to: `"Usage: /purge <start|stop|stats|admin>"`

**Step 3: Add `openAdminMenu()` method**

```java
private void openAdminMenu(Player player, Ref<EntityStore> ref, Store<EntityStore> store) {
    if (!PermissionUtils.isOp(player)) {
        player.sendMessage(Message.raw("You must be OP to use /purge admin."));
        return;
    }
    PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
    if (playerRef == null) {
        return;
    }
    player.getPageManager().openCustomPage(ref, store,
            new PurgeAdminIndexPage(playerRef, spawnPointManager));
}
```

**Step 4: Update HyvexaPurgePlugin to pass spawnPointManager**

In `HyvexaPurgePlugin.java` line 94, change:
```java
this.getCommandRegistry().registerCommand(new PurgeCommand(sessionManager));
```
to:
```java
this.getCommandRegistry().registerCommand(new PurgeCommand(sessionManager, spawnPointManager));
```

**Step 5: Commit**

```bash
git add hyvexa-purge/src/main/java/io/hyvexa/purge/command/PurgeCommand.java
git add hyvexa-purge/src/main/java/io/hyvexa/purge/HyvexaPurgePlugin.java
git commit -m "feat(purge): wire /pu admin command to open admin GUI"
```

---

### Task 7: Update CHANGELOG.md

**Files:**
- Modify: `CHANGELOG.md`

**Step 1: Add changelog entry**

Add under the latest date section (or create new section if needed):
```
- **Purge**: Added `/pu admin` command with spawn point management GUI
```

**Step 2: Commit**

```bash
git add CHANGELOG.md
git commit -m "docs: add changelog entry for purge admin GUI"
```
