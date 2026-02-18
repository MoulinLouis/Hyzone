# Cat Easter Egg Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a hidden "Cat Collector" easter egg where players find 5 cat NPCs via `/cat <token>` commands, tracked as a new achievement category.

**Architecture:** Single command `CatCommand` validates fixed tokens against a map, stores found cats in `AscendPlayerProgress.foundCats` (Set<String>), persists to a new `ascend_player_cats` DB table, and triggers achievement unlock at 5/5 via the existing `AchievementManager`.

**Tech Stack:** Java, MySQL, Hytale Server API (AbstractAsyncCommand, Toast system)

---

### Task 1: Add foundCats field to AscendPlayerProgress

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendPlayerProgress.java`

**Step 1: Add field and methods after the Transcendence section (after line 578)**

Add at the end of the class, before the `AutoSummitCategoryConfig` inner class (line 580):

```java
    // ========================================
    // Easter Egg - Cat Collector
    // ========================================

    private final Set<String> foundCats = ConcurrentHashMap.newKeySet();

    public boolean hasFoundCat(String token) {
        return foundCats.contains(token);
    }

    public boolean addFoundCat(String token) {
        return foundCats.add(token);
    }

    public int getFoundCatCount() {
        return foundCats.size();
    }

    public Set<String> getFoundCats() {
        return Set.copyOf(foundCats);
    }

    public void setFoundCats(Set<String> cats) {
        foundCats.clear();
        if (cats != null) {
            foundCats.addAll(cats);
        }
    }
```

**Step 2: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendPlayerProgress.java
git commit -m "feat: add foundCats field to AscendPlayerProgress"
```

---

### Task 2: Add EASTER_EGGS category and CAT_COLLECTOR achievement

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/AscendConstants.java`

**Step 1: Add EASTER_EGGS to AchievementCategory enum (line 859, before SECRET)**

```java
        CHALLENGES("Challenges"),
        EASTER_EGGS("Easter Eggs"),
        SECRET("Secret");
```

**Step 2: Add CAT_COLLECTOR to AchievementType enum (between CHALLENGE_MASTER and CHAIN_RUNNER, around line 907)**

```java
        // Easter Eggs
        CAT_COLLECTOR("Cat Collector", "Find all 5 hidden cats", AchievementCategory.EASTER_EGGS, true),

        // Secret - Hidden
```

**Step 3: Add constant for required cat count**

After the existing achievement threshold constants (after line 952):

```java
    public static final int ACHIEVEMENT_CATS_REQUIRED = 5;
```

**Step 4: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/AscendConstants.java
git commit -m "feat: add EASTER_EGGS category and CAT_COLLECTOR achievement type"
```

---

### Task 3: Wire CAT_COLLECTOR into AchievementManager

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/achievement/AchievementManager.java`

**Step 1: Add CAT_COLLECTOR case to `isAchievementEarned` switch (line 111, before CHAIN_RUNNER)**

```java
            // Easter Eggs
            case CAT_COLLECTOR -> progress.getFoundCatCount() >= AscendConstants.ACHIEVEMENT_CATS_REQUIRED;

            // Secret
```

**Step 2: Add CAT_COLLECTOR case to `getProgress` switch (line 356, before CHAIN_RUNNER)**

```java
            // Easter Eggs
            case CAT_COLLECTOR -> {
                current = Math.min(progress.getFoundCatCount(), AscendConstants.ACHIEVEMENT_CATS_REQUIRED);
                required = AscendConstants.ACHIEVEMENT_CATS_REQUIRED;
            }

            // Secret
```

**Step 3: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/achievement/AchievementManager.java
git commit -m "feat: wire CAT_COLLECTOR achievement into AchievementManager"
```

---

### Task 4: Add database table and persistence

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendDatabaseSetup.java`
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendPlayerPersistence.java`

**Step 1: Add `ascend_player_cats` table creation in `AscendDatabaseSetup.ensureTables()`**

After the achievement table creation (after line 146), add:

```java
            // Easter Egg - Cat Collector
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ascend_player_cats (
                    player_uuid VARCHAR(36) NOT NULL,
                    cat_token VARCHAR(16) NOT NULL,
                    found_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (player_uuid, cat_token),
                    FOREIGN KEY (player_uuid) REFERENCES ascend_players(uuid) ON DELETE CASCADE
                ) ENGINE=InnoDB
                """);
```

**Step 2: Add cat persistence to `AscendPlayerPersistence.doSyncSave()`**

2a. Add SQL string (after `achievementSql`, around line 295):

```java
        String catSql = """
            INSERT IGNORE INTO ascend_player_cats (player_uuid, cat_token)
            VALUES (?, ?)
            """;
```

2b. Add PreparedStatement in the try-with-resources block (after `delChallengeRecords` line ~314):

Add `ascend_player_cats` to the delete statements array and add a `catStmt` PreparedStatement. The try-with-resources opens at line 305. Add:

```java
                 PreparedStatement catStmt = conn.prepareStatement(catSql);
                 PreparedStatement delCats = conn.prepareStatement(String.format(deleteChildSql, "ascend_player_cats"));
```

2c. Add `DatabaseManager.applyQueryTimeout(catStmt);` after the other timeout calls.

2d. Add `delCats` to the reset deletion array (line 334):

```java
                    if (resetPendingPlayers.remove(playerId)) {
                        String pid = playerId.toString();
                        for (PreparedStatement delStmt : new PreparedStatement[]{delMaps, delSummit, delSkills, delAchievements, delCats}) {
```

2e. Add cat save loop (after achievements save, around line 436):

```java
                    // Save found cats
                    for (String catToken : progress.getFoundCats()) {
                        catStmt.setString(1, playerId.toString());
                        catStmt.setString(2, catToken);
                        catStmt.addBatch();
                    }
```

2f. Add `catStmt.executeBatch();` after `achievementStmt.executeBatch();` (around line 447).

**Step 3: Add load method in `AscendPlayerPersistence`**

After `loadAchievementsForPlayer` (around line 770), add:

```java
    private void loadCatsForPlayer(Connection conn, UUID playerId, AscendPlayerProgress progress) throws SQLException {
        String sql = "SELECT cat_token FROM ascend_player_cats WHERE player_uuid = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    progress.addFoundCat(rs.getString("cat_token"));
                }
            }
        }
    }
```

**Step 4: Call loadCatsForPlayer in `loadPlayerFromDatabase`**

After `loadAchievementsForPlayer(conn, playerId, progress);` (line 649), add:

```java
            loadCatsForPlayer(conn, playerId, progress);
```

**Step 5: Add `ascend_player_cats` to `deletePlayerDataFromDatabase` tables array**

In the `deletePlayerDataFromDatabase` method (line 782), add `"ascend_player_cats"` to the tables array:

```java
        String[] tables = {
            "ascend_player_maps",
            "ascend_player_summit",
            "ascend_player_skills",
            "ascend_player_achievements",
            "ascend_player_cats",
            "ascend_ghost_recordings",
            "ascend_challenges",
            "ascend_challenge_records"
        };
```

**Step 6: Add cat reset to `AscendPlayerStore.resetPlayerProgress`**

After `progress.setUnlockedAchievements(null);` (line 148), add:

```java
        // Reset Easter Egg cats
        progress.setFoundCats(null);
```

**Step 7: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendDatabaseSetup.java
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendPlayerPersistence.java
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendPlayerStore.java
git commit -m "feat: add ascend_player_cats table and persistence for cat easter egg"
```

---

### Task 5: Create CatCommand

**Files:**
- Create: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/command/CatCommand.java`
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ParkourAscendPlugin.java`

**Step 1: Create CatCommand.java**

```java
package io.hyvexa.ascend.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.hud.AscendHudManager;
import io.hyvexa.ascend.hud.ToastType;
import io.hyvexa.ascend.util.AscendModeGate;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * /cat <token> - Hidden easter egg command triggered via NPC dialog buttons.
 * Each token corresponds to a hidden cat NPC placed in the Ascend world.
 */
public class CatCommand extends AbstractAsyncCommand {

    private static final Map<String, String> VALID_CATS = Map.of(
        "WHK", "Whiskers",
        "PUR", "Shadow",
        "MRW", "Marble",
        "FLF", "Fluffball",
        "NKO", "Neko"
    );

    public CatCommand() {
        super("cat", "Interact with a cat");
        this.setPermissionGroup(GameMode.Adventure);
    }

    @Override
    @Nonnull
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        CommandSender sender = ctx.sender();
        if (!(sender instanceof Player player)) {
            return CompletableFuture.completedFuture(null);
        }

        String[] args = ctx.args();
        if (args.length < 1) {
            return CompletableFuture.completedFuture(null);
        }

        String token = args[0].toUpperCase();
        if (!VALID_CATS.containsKey(token)) {
            return CompletableFuture.completedFuture(null);
        }

        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return CompletableFuture.completedFuture(null);
        }

        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();

        return CompletableFuture.runAsync(() -> {
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) {
                return;
            }
            if (AscendModeGate.denyIfNotAscend(ctx, world)) {
                return;
            }

            ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
            if (plugin == null || plugin.getPlayerStore() == null) {
                return;
            }

            AscendPlayerStore playerStore = plugin.getPlayerStore();
            UUID playerId = playerRef.getUuid();
            AscendPlayerProgress progress = playerStore.getPlayer(playerId);
            if (progress == null) {
                return;
            }

            if (progress.hasFoundCat(token)) {
                AscendHudManager hm = plugin.getHudManager();
                if (hm != null) {
                    hm.showToast(playerId, ToastType.INFO, "You already found this cat!");
                }
                return;
            }

            progress.addFoundCat(token);
            playerStore.markDirty(playerId);

            int found = progress.getFoundCatCount();
            AscendHudManager hm = plugin.getHudManager();
            if (hm != null) {
                hm.showToast(playerId, ToastType.ECONOMY, "Cat found! (" + found + "/5)");
            }

            if (found >= 5 && plugin.getAchievementManager() != null) {
                plugin.getAchievementManager().checkAndUnlockAchievements(playerId, player);
            }
        }, world);
    }
}
```

**Step 2: Register command in ParkourAscendPlugin**

After `getCommandRegistry().registerCommand(new TranscendCommand());` (line 222), add:

```java
        getCommandRegistry().registerCommand(new CatCommand());
```

**Step 3: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/command/CatCommand.java
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ParkourAscendPlugin.java
git commit -m "feat: add /cat command and register in plugin for cat easter egg"
```

---

### Task 6: Update CHANGELOG

**Files:**
- Modify: `CHANGELOG.md`

**Step 1: Add entry**

Under the latest version section, add:

```
- Added hidden Cat Collector easter egg (find 5 cats in Ascend for a secret achievement)
```

**Step 2: Commit**

```bash
git add CHANGELOG.md
git commit -m "docs: add cat easter egg to changelog"
```
