# Summit System Rebalance - Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Refonte du système Summit avec XP, nouvelles formules, et évolution ×10

**Architecture:** Modifier SummitCategory pour les nouvelles formules, ajouter tracking XP dans AscendPlayerProgress et DB, mettre à jour SummitManager pour la logique XP, refaire l'UI avec barres de progression.

**Tech Stack:** Java, Hytale UI (.ui), MySQL

---

Date: 2026-02-05

## Overview

Refonte complète du système Summit (prestige tier 2) pour améliorer la clarté et l'engagement joueur.

### Problèmes actuels
- **Coin Flow** : mécanique peu intéressante (simple multiplicateur de coins)
- **Evolution Power** : concept trop abstrait, joueurs ne comprennent pas l'effet
- **Plafond niveau 10** : limite artificielle sur la progression

### Objectifs
- Mécaniques plus intuitives avec affichage visuel clair
- Progression infinie sans plafond
- Système d'XP pour visualiser la progression

---

## Nouvelles catégories Summit

### 1. Runner Speed
**Effet** : Multiplicateur sur la vitesse de complétion des runners.

**Formule** : `1 + 0.45 × √niveau`

| Niveau | Multiplicateur |
|--------|---------------|
| 0 | ×1.00 |
| 1 | ×1.45 |
| 2 | ×1.64 |
| 3 | ×1.78 |
| 4 | ×1.90 |
| 5 | ×2.01 |
| 10 | ×2.42 |
| 20 | ×3.01 |
| 50 | ×4.18 |
| 100 | ×5.50 |

**Caractéristique** : Diminishing returns (√) - les premiers niveaux ont le plus d'impact.

---

### 2. Multiplier Gain
**Effet** : Multiplicateur sur les gains de multiplicateur par run des runners.

**Formule** : `1 + 5 × niveau^0.9`

| Niveau | Multiplicateur |
|--------|---------------|
| 0 | ×1 |
| 1 | ×6 |
| 2 | ×10.3 |
| 3 | ×14.4 |
| 5 | ×22.3 |
| 10 | ×40.7 |
| 20 | ×75.3 |
| 50 | ×166.5 |
| 100 | ×316.5 |

**Caractéristique** : Diminishing returns (^0.9) - progression forte au début, puis ralentit.

---

### 3. Evolution Power
**Effet** : Multiplicateur appliqué au multiplicateur de la map lors d'une évolution de runner.

**Formule** : `10 + 0.5 × niveau^0.8`

| Niveau | Multiplicateur |
|--------|---------------|
| 0 | ×10.00 |
| 1 | ×10.50 |
| 2 | ×10.87 |
| 3 | ×11.20 |
| 4 | ×11.51 |
| 5 | ×11.80 |
| 10 | ×13.16 |
| 20 | ×15.50 |
| 50 | ×21.20 |
| 100 | ×29.91 |

**Caractéristique** : Diminishing returns (^0.8) - progression stable sans explosion.

---

## Changement du système d'évolution

### Avant
Évoluer un runner doublait le gain de multiplicateur par run :
- 0★ : +0.1/run
- 1★ : +0.2/run
- 2★ : +0.4/run
- etc.

### Nouveau
Évoluer un runner applique un **multiplicateur instantané** sur le multiplicateur de la map :
- Base : ×10
- Avec Evolution Power : ×(10 + 0.5 × niveau^0.8)

**Exemple** : Un runner sur une map avec multiplicateur ×5, évolué avec Evolution Power niveau 5 (×11.8) → nouveau multiplicateur = ×59.

---

## Système d'XP Summit

### Conversion coins → XP
**Ratio** : 1000 coins = 1 XP

### Seuils par niveau
**Formule** : `100 × niveau^1.5` XP pour atteindre le niveau suivant

| Niveau | XP requis | XP cumulé | Coins équivalents |
|--------|-----------|-----------|-------------------|
| 1 | 100 | 100 | 100K |
| 2 | 283 | 383 | 383K |
| 3 | 520 | 903 | 903K |
| 4 | 800 | 1,703 | 1.7M |
| 5 | 1,118 | 2,821 | 2.8M |
| 10 | 3,162 | 13,246 | 13.2M |
| 20 | 8,944 | 62,946 | 62.9M |
| 50 | 35,355 | 474,341 | 474M |

### Pool d'XP
- **Commun** aux 3 catégories
- Chaque catégorie track son propre niveau et XP accumulé

### Investissement
- Lors d'un Summit, le joueur choisit **UNE seule catégorie**
- **Tout l'XP** va dans cette catégorie (pas de répartition)
- Pas de stockage d'XP entre les Summits

---

## Flow utilisateur

1. Joueur accumule des coins en jouant
2. Ouvre le menu Summit
3. Voit l'XP potentiel : `+{coins / 1000} XP`
4. Pour chaque catégorie, voit :
   - Niveau actuel et multiplicateur : `[Lv. X] Actuel: ×M.MM`
   - Niveau potentiel après Summit : `[Lv. Y] Next: ×N.NN`
   - Barre de progression : `████░░░░ Exp A/B (+C)`
5. Choisit une catégorie et confirme
6. XP investi, peut monter plusieurs niveaux d'un coup
7. Coins et Elevation remis à 0

---

## UI Mockup

```
╔══════════════════════════════════════════════════════════╗
║                      S U M M I T                         ║
║                                                          ║
║  Coins: 5,000,000  →  +5000 XP                          ║
║                                                          ║
║  ┌─────────────────────────────────────────────────────┐ ║
║  │ RUNNER SPEED                              [SUMMIT]  │ ║
║  │ [Lv. 3] Actuel: ×1.87  →  [Lv. 5] Next: ×2.12      │ ║
║  │ ████████████░░░░░░░░ Exp 720/1118 (+5000)          │ ║
║  └─────────────────────────────────────────────────────┘ ║
║                                                          ║
║  ┌─────────────────────────────────────────────────────┐ ║
║  │ MULTIPLIER GAIN                           [SUMMIT]  │ ║
║  │ [Lv. 2] Actuel: ×4  →  [Lv. 5] Next: ×12           │ ║
║  │ ██████░░░░░░░░░░░░░░ Exp 283/520 (+5000)           │ ║
║  └─────────────────────────────────────────────────────┘ ║
║                                                          ║
║  ┌─────────────────────────────────────────────────────┐ ║
║  │ EVOLUTION POWER                           [SUMMIT]  │ ║
║  │ [Lv. 0] Actuel: ×10  →  [Lv. 4] Next: ×11.51       │ ║
║  │ ░░░░░░░░░░░░░░░░░░░░ Exp 0/100 (+5000)             │ ║
║  └─────────────────────────────────────────────────────┘ ║
║                                                          ║
║  ⚠️ Summit resets: Coins + Elevation                     ║
╚══════════════════════════════════════════════════════════╝
```

---

## Resets au Summit

| Ressource | Reset ? |
|-----------|---------|
| Coins | ✓ Oui |
| Elevation level | ✓ Oui |
| Map multipliers | Non |
| Runner levels/stars | Non |
| Summit XP/levels | Non |
| Unlocked maps | Non |

---

## Notes d'équilibrage

- Les valeurs de conversion (1000:1) et seuils (100 × n^1.5) sont des points de départ
- À ajuster après tests en jeu selon :
  - Temps moyen entre Summits
  - Progression des niveaux Summit
  - Impact sur l'économie globale

---

## Implémentation

### Fichiers à modifier
- `SummitManager.java` - Logique des catégories et calculs
- `SummitPage.java` - UI et affichage
- `Ascend_Summit.ui` - Layout UI
- `AscendPlayerProgress.java` - Stockage XP par catégorie
- `AscendConstants.java` - Formules et constantes
- `RobotManager.java` - Intégration Evolution Power sur évolution

### Base de données
- Table `ascend_player_summit` : ajouter colonnes XP par catégorie
- Migration pour convertir les anciens niveaux en XP équivalent

### Tests prioritaires
1. Formules retournent les bonnes valeurs
2. Niveau up automatique quand XP atteint le seuil
3. Reset correct des coins/elevation
4. Affichage UI correct du +XP potentiel

---

## Implementation Tasks

### Task 1: Update SummitCategory enum and formulas

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/AscendConstants.java:427-464`

**Step 1: Replace SummitCategory enum with new categories and formulas**

```java
public enum SummitCategory {
    RUNNER_SPEED("Runner Speed"),      // 1 + 0.45 × √niveau
    MULTIPLIER_GAIN("Multiplier Gain"), // 1 + 5 × niveau^0.9
    EVOLUTION_POWER("Evolution Power"); // 10 + 0.5 × niveau^0.8

    private final String displayName;

    SummitCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get the bonus multiplier for a given level.
     * - RUNNER_SPEED: 1 + 0.45 × √niveau
     * - MULTIPLIER_GAIN: 1 + 5 × niveau^0.9
     * - EVOLUTION_POWER: 10 + 0.5 × niveau^0.8
     */
    public double getBonusForLevel(int level) {
        int safeLevel = Math.max(0, level);
        return switch (this) {
            case RUNNER_SPEED -> 1.0 + 0.45 * Math.sqrt(safeLevel);
            case MULTIPLIER_GAIN -> 1.0 + 5.0 * Math.pow(safeLevel, 0.9);
            case EVOLUTION_POWER -> 10.0 + 0.5 * Math.pow(safeLevel, 0.8);
        };
    }
}
```

**Step 2: Remove old bonusPerLevel field and getBonusPerLevel() method**

The new formulas don't use a constant per-level bonus, so remove these.

**Step 3: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/AscendConstants.java
git commit -m "refactor(summit): update SummitCategory with new formulas"
```

---

### Task 2: Add XP-based level calculation constants

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/AscendConstants.java:466-520`

**Step 1: Replace Summit level threshold constants with XP formula**

```java
// ========================================
// Summit XP System
// ========================================

public static final long SUMMIT_COINS_TO_XP_RATIO = 1000L; // 1000 coins = 1 XP
public static final double SUMMIT_XP_LEVEL_BASE = 100.0;   // Base XP for level formula
public static final double SUMMIT_XP_LEVEL_EXPONENT = 1.5; // Exponent for level formula

/**
 * Convert coins to XP.
 * Formula: coins / 1000
 */
public static long coinsToXp(BigDecimal coins) {
    return coins.divide(BigDecimal.valueOf(SUMMIT_COINS_TO_XP_RATIO), 0, RoundingMode.FLOOR).longValue();
}

/**
 * Calculate XP required to reach a specific level (from level-1).
 * Formula: 100 × level^1.5
 */
public static long getXpForLevel(int level) {
    if (level <= 0) return 0;
    return (long) Math.ceil(SUMMIT_XP_LEVEL_BASE * Math.pow(level, SUMMIT_XP_LEVEL_EXPONENT));
}

/**
 * Calculate cumulative XP required to reach a level (total from 0).
 */
public static long getCumulativeXpForLevel(int level) {
    long total = 0;
    for (int i = 1; i <= level; i++) {
        total += getXpForLevel(i);
    }
    return total;
}

/**
 * Calculate the level achieved with given cumulative XP.
 */
public static int calculateLevelFromXp(long xp) {
    int level = 0;
    long cumulative = 0;
    while (true) {
        long nextLevelXp = getXpForLevel(level + 1);
        if (cumulative + nextLevelXp > xp) {
            break;
        }
        cumulative += nextLevelXp;
        level++;
    }
    return level;
}

/**
 * Calculate XP progress within current level.
 * Returns [currentXpInLevel, xpRequiredForNextLevel]
 */
public static long[] getXpProgress(long totalXp) {
    int level = calculateLevelFromXp(totalXp);
    long xpForCurrentLevel = getCumulativeXpForLevel(level);
    long xpInLevel = totalXp - xpForCurrentLevel;
    long xpForNextLevel = getXpForLevel(level + 1);
    return new long[]{xpInLevel, xpForNextLevel};
}
```

**Step 2: Remove old SUMMIT_LEVEL_THRESHOLDS array and related methods**

Delete `SUMMIT_LEVEL_THRESHOLDS`, `SUMMIT_MAX_LEVEL`, `calculateSummitLevel(BigDecimal)`, `getCoinsForSummitLevel(int)`, `getCoinsForNextSummitLevel(int)`.

**Step 3: Keep SUMMIT_MIN_COINS but update to use XP ratio**

```java
public static final long SUMMIT_MIN_COINS = SUMMIT_COINS_TO_XP_RATIO; // 1000 coins = 1 XP minimum
```

**Step 4: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/AscendConstants.java
git commit -m "feat(summit): add XP-based level calculation system"
```

---

### Task 3: Update AscendPlayerProgress for XP tracking

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendPlayerProgress.java:18-104`

**Step 1: Change summit storage from levels to XP**

```java
// Summit System - XP per category (level calculated from XP)
private final Map<AscendConstants.SummitCategory, Long> summitXp = new ConcurrentHashMap<>();
```

**Step 2: Update getter/setter methods**

```java
// ========================================
// Summit System (XP-based)
// ========================================

public long getSummitXp(AscendConstants.SummitCategory category) {
    return summitXp.getOrDefault(category, 0L);
}

public void setSummitXp(AscendConstants.SummitCategory category, long xp) {
    summitXp.put(category, Math.max(0, xp));
}

public long addSummitXp(AscendConstants.SummitCategory category, long amount) {
    long current = getSummitXp(category);
    long newXp = Math.max(0, current + amount);
    summitXp.put(category, newXp);
    return newXp;
}

public int getSummitLevel(AscendConstants.SummitCategory category) {
    return AscendConstants.calculateLevelFromXp(getSummitXp(category));
}

public Map<AscendConstants.SummitCategory, Long> getSummitXpMap() {
    return new EnumMap<>(AscendConstants.SummitCategory.class) {{
        for (AscendConstants.SummitCategory cat : AscendConstants.SummitCategory.values()) {
            put(cat, getSummitXp(cat));
        }
    }};
}

public void clearSummitXp() {
    summitXp.clear();
}
```

**Step 3: Remove old summitLevels field and related methods**

Delete `summitLevels` Map, `setSummitLevel()`, `addSummitLevel()`, `getSummitLevels()`, `clearSummitLevels()`.

**Step 4: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendPlayerProgress.java
git commit -m "refactor(summit): change from level storage to XP storage"
```

---

### Task 4: Update database schema for XP

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendDatabaseSetup.java`

**Step 1: Update table schema to store XP instead of level**

Find the CREATE TABLE for `ascend_player_summit` and change `level INT` to `xp BIGINT`:

```java
stmt.executeUpdate("""
    CREATE TABLE IF NOT EXISTS ascend_player_summit (
        player_uuid VARCHAR(36) NOT NULL,
        category VARCHAR(32) NOT NULL,
        xp BIGINT NOT NULL DEFAULT 0,
        PRIMARY KEY (player_uuid, category),
        FOREIGN KEY (player_uuid) REFERENCES ascend_players(uuid) ON DELETE CASCADE
    ) ENGINE=InnoDB
    """);
```

**Step 2: Add migration for existing data**

Add after the CREATE TABLE:

```java
// Migrate old 'level' column to 'xp' if exists
try {
    var rs = stmt.executeQuery("SHOW COLUMNS FROM ascend_player_summit LIKE 'level'");
    if (rs.next()) {
        // Old schema exists, migrate level to xp
        // Convert level to equivalent XP (cumulative XP for that level)
        stmt.executeUpdate("""
            ALTER TABLE ascend_player_summit
            ADD COLUMN IF NOT EXISTS xp BIGINT NOT NULL DEFAULT 0
            """);
        // Note: Migration of actual values will be handled in AscendPlayerStore load
        stmt.executeUpdate("""
            ALTER TABLE ascend_player_summit
            DROP COLUMN IF EXISTS level
            """);
    }
} catch (Exception e) {
    // Column doesn't exist or already migrated, ignore
}
```

**Step 3: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendDatabaseSetup.java
git commit -m "feat(db): update summit table schema for XP storage"
```

---

### Task 5: Update AscendPlayerStore for XP persistence

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendPlayerStore.java`

**Step 1: Update loadSummitData to read XP**

Find the method that loads summit data and change from `level` to `xp`:

```java
// In loadSummitData or similar method
String sql = "SELECT category, xp FROM ascend_player_summit WHERE player_uuid = ?";
// ...
long xp = rs.getLong("xp");
progress.setSummitXp(category, xp);
```

**Step 2: Update saveSummitData to write XP**

```java
// In saveSummitData or similar method
String sql = "INSERT INTO ascend_player_summit (player_uuid, category, xp) VALUES (?, ?, ?) " +
             "ON DUPLICATE KEY UPDATE xp = VALUES(xp)";
// ...
stmt.setLong(3, progress.getSummitXp(category));
```

**Step 3: Update getSummitBonus to use new formulas**

```java
public BigDecimal getSummitBonus(UUID playerId, SummitCategory category) {
    var progress = getPlayer(playerId);
    if (progress == null) {
        return category == SummitCategory.EVOLUTION_POWER
            ? BigDecimal.valueOf(10.0)  // Base evolution multiplier
            : BigDecimal.ONE;
    }
    int level = progress.getSummitLevel(category);
    return BigDecimal.valueOf(category.getBonusForLevel(level));
}
```

**Step 4: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendPlayerStore.java
git commit -m "feat(store): update summit persistence for XP system"
```

---

### Task 6: Update SummitManager for XP-based logic

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/summit/SummitManager.java`

**Step 1: Update canSummit to check minimum XP gain**

```java
public boolean canSummit(UUID playerId) {
    BigDecimal coins = playerStore.getCoins(playerId);
    long potentialXp = AscendConstants.coinsToXp(coins);
    return potentialXp >= 1; // At least 1 XP to gain
}
```

**Step 2: Update previewSummit for XP system**

```java
public SummitPreview previewSummit(UUID playerId, SummitCategory category) {
    BigDecimal coins = playerStore.getCoins(playerId);
    long xpToGain = AscendConstants.coinsToXp(coins);

    long currentXp = playerStore.getSummitXp(playerId, category);
    int currentLevel = AscendConstants.calculateLevelFromXp(currentXp);

    long newXp = currentXp + xpToGain;
    int newLevel = AscendConstants.calculateLevelFromXp(newXp);

    double currentBonus = category.getBonusForLevel(currentLevel);
    double newBonus = category.getBonusForLevel(newLevel);

    long[] currentProgress = AscendConstants.getXpProgress(currentXp);
    long[] newProgress = AscendConstants.getXpProgress(newXp);

    return new SummitPreview(
        category,
        currentLevel,
        newLevel,
        newLevel - currentLevel,
        currentBonus,
        newBonus,
        coins.doubleValue(),
        xpToGain,
        currentProgress[0],  // xpInCurrentLevel
        currentProgress[1],  // xpForNextLevel
        newProgress[0],      // xpInNewLevel
        newProgress[1]       // xpForNewNextLevel
    );
}
```

**Step 3: Update SummitPreview record**

```java
public record SummitPreview(
    SummitCategory category,
    int currentLevel,
    int newLevel,
    int levelGain,
    double currentBonus,
    double newBonus,
    double coinsToSpend,
    long xpToGain,
    long currentXpInLevel,
    long currentXpRequired,
    long newXpInLevel,
    long newXpRequired
) {
    public boolean hasGain() {
        return xpToGain > 0;
    }
}
```

**Step 4: Update performSummit for XP**

```java
public SummitResult performSummit(UUID playerId, SummitCategory category) {
    BigDecimal coins = playerStore.getCoins(playerId);
    long xpToGain = AscendConstants.coinsToXp(coins);

    if (xpToGain < 1) {
        return new SummitResult(-1, List.of(), 0);
    }

    int previousLevel = playerStore.getSummitLevel(playerId, category);

    // Add XP to category
    playerStore.addSummitXp(playerId, category, xpToGain);

    int newLevel = playerStore.getSummitLevel(playerId, category);

    // Get first map ID for reset
    String firstMapId = null;
    if (mapStore != null) {
        List<AscendMap> maps = mapStore.listMapsSorted();
        if (!maps.isEmpty()) {
            firstMapId = maps.get(0).getId();
        }
    }

    // Full reset: coins, elevation (but NOT multipliers, runners, unlocks)
    List<String> mapsWithRunners = playerStore.resetProgressForSummit(playerId, firstMapId);

    playerStore.markDirty(playerId);

    LOGGER.atInfo().log("[Summit] Player " + playerId + " summited " + category.name()
        + " +" + xpToGain + " XP, Lv." + previousLevel + " → Lv." + newLevel);

    return new SummitResult(newLevel, mapsWithRunners, xpToGain);
}
```

**Step 5: Update SummitResult record**

```java
public record SummitResult(int newLevel, List<String> mapsWithRunners, long xpGained) {
    public boolean succeeded() {
        return newLevel >= 0;
    }
}
```

**Step 6: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/summit/SummitManager.java
git commit -m "refactor(summit): implement XP-based summit logic"
```

---

### Task 7: Update SummitPage UI logic

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ui/SummitPage.java`

**Step 1: Update category order and colors**

```java
SummitCategory[] categories = {
    SummitCategory.RUNNER_SPEED,
    SummitCategory.MULTIPLIER_GAIN,
    SummitCategory.EVOLUTION_POWER
};

private String resolveCategoryAccentColor(int index) {
    return switch (index) {
        case 0 -> "#2d5a7b";  // Blue for Runner Speed
        case 1 -> "#5a6b3d";  // Green for Multiplier Gain
        default -> "#5a3d6b"; // Purple for Evolution Power
    };
}
```

**Step 2: Update getCategoryDescription**

```java
private String getCategoryDescription(SummitCategory category) {
    return switch (category) {
        case RUNNER_SPEED -> "Multiplies runner completion speed";
        case MULTIPLIER_GAIN -> "Multiplies multiplier gain per run";
        case EVOLUTION_POWER -> "Multiplies map multiplier on evolution";
    };
}
```

**Step 3: Update buildCategoryCards for XP display**

```java
// After getting preview
SummitManager.SummitPreview preview = summitManager.previewSummit(playerId, category);

// Current level and bonus display
String levelText = "[Lv. " + preview.currentLevel() + "] ";
String currentBonusText = formatBonus(category, preview.currentBonus());
commandBuilder.set("#CategoryCards[" + i + "] #CurrentLevel.Text", levelText + currentBonusText);

// Preview level if XP gain
if (preview.hasGain()) {
    String previewText = "→ [Lv. " + preview.newLevel() + "] " + formatBonus(category, preview.newBonus());
    commandBuilder.set("#CategoryCards[" + i + "] #PreviewLevel.Text", previewText);
} else {
    commandBuilder.set("#CategoryCards[" + i + "] #PreviewLevel.Text", "");
}

// XP progress bar text
String xpText = String.format("Exp %d/%d (+%d)",
    preview.currentXpInLevel(),
    preview.currentXpRequired(),
    preview.xpToGain());
commandBuilder.set("#CategoryCards[" + i + "] #XpProgress.Text", xpText);

// XP progress bar fill (percentage)
double progressPercent = preview.currentXpRequired() > 0
    ? (double) preview.currentXpInLevel() / preview.currentXpRequired()
    : 0;
commandBuilder.set("#CategoryCards[" + i + "] #XpBarFill.Anchor.Width", (int)(progressPercent * 100) + "%");
```

**Step 4: Update formatBonus for new categories**

```java
private String formatBonus(SummitCategory category, double value) {
    return String.format(Locale.US, "×%.2f", value);
}
```

**Step 5: Update coins display to show XP conversion**

```java
// In buildCategoryCards, update coins display
long potentialXp = AscendConstants.coinsToXp(coins);
String coinsText = FormatUtils.formatCoinsForHudDecimal(coins) + " → +" + potentialXp + " XP";
commandBuilder.set("#CoinsValue.Text", coinsText);
```

**Step 6: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ui/SummitPage.java
git commit -m "feat(ui): update SummitPage for XP display"
```

---

### Task 8: Update Ascend_SummitEntry.ui for XP bar

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/resources/Common/UI/Custom/Pages/Ascend_SummitEntry.ui`

**Step 1: Add XP progress bar to the card**

After `#CategoryBonus` label, add:

```
Group #XpBarContainer {
  Anchor: (Left: 0, Right: 0, Top: 6, Height: 16);
  Background: #000000(0.3);

  Group #XpBarFill {
    Anchor: (Left: 0, Top: 0, Bottom: 0, Width: 0%);
    Background: #7c3aed(0.6);
  }

  Label #XpProgress {
    Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0);
    Style: (
      FontSize: 11,
      TextColor: #f0f4f8,
      HorizontalAlignment: Center,
      VerticalAlignment: Center
    );
    Text: "Exp 0/100 (+0)";
  }
}
```

**Step 2: Adjust CategoryBonus and other elements positioning**

Update anchors to make room for the XP bar.

**Step 3: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/resources/Common/UI/Custom/Pages/Ascend_SummitEntry.ui
git commit -m "feat(ui): add XP progress bar to summit entry card"
```

---

### Task 9: Update evolution system for ×10 multiplier

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/robot/RobotManager.java`
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendPlayerProgress.java`

**Step 1: Find the evolution method in RobotManager**

Locate where runner evolution happens (incrementRobotStars or similar).

**Step 2: Apply Evolution Power multiplier to map multiplier**

```java
// When evolving a runner:
double evolutionPower = summitManager.getEvolutionPowerBonus(playerId).doubleValue();
// evolutionPower is already the full multiplier (10 + 0.5 × level^0.8)

MapProgress mapProgress = progress.getOrCreateMapProgress(mapId);
BigDecimal currentMultiplier = mapProgress.getMultiplier();
BigDecimal newMultiplier = currentMultiplier.multiply(BigDecimal.valueOf(evolutionPower));
mapProgress.setMultiplier(newMultiplier);
```

**Step 3: Remove old star-based multiplier increment logic**

The old system added multiplier per run based on stars. Now evolution just multiplies the map multiplier once.

**Step 4: Update getRunnerMultiplierIncrement to return flat value**

Since evolution now multiplies the map multiplier directly, the per-run increment should be flat:

```java
// In AscendConstants
public static BigDecimal getRunnerMultiplierIncrement(int stars, double multiplierGainBonus) {
    // Base increment: 0.1
    // With Multiplier Gain Summit bonus applied
    BigDecimal base = new BigDecimal("0.1");
    return base.multiply(BigDecimal.valueOf(multiplierGainBonus));
}
```

**Step 5: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/robot/RobotManager.java
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/AscendConstants.java
git commit -m "feat(evolution): implement ×10 multiplier on map for evolution"
```

---

### Task 10: Update resetProgressForSummit

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendPlayerStore.java`

**Step 1: Create or update resetProgressForSummit method**

Summit should reset coins and elevation, but NOT map multipliers, runners, or unlocks:

```java
/**
 * Reset progress for Summit: coins and elevation only.
 * Does NOT reset map multipliers, runners, or unlocks.
 */
public List<String> resetProgressForSummit(UUID playerId, String firstMapId) {
    var progress = getPlayer(playerId);
    if (progress == null) {
        return List.of();
    }

    // Reset coins
    progress.setCoins(BigDecimal.ZERO);

    // Reset elevation
    progress.setElevationMultiplier(1);

    markDirty(playerId);

    return List.of(); // No runners to despawn
}
```

**Step 2: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendPlayerStore.java
git commit -m "fix(summit): reset only coins and elevation, not full progress"
```

---

### Task 11: Final integration and cleanup

**Files:**
- Review all modified files

**Step 1: Remove unused imports and dead code**

Check all modified files for unused imports.

**Step 2: Update ECONOMY_BALANCE.md with new formulas**

Update the documentation to reflect the new Summit system.

**Step 3: Test the complete flow**

- Open Summit UI
- Verify XP display and preview
- Perform Summit
- Verify level up and bonus application
- Verify reset (coins + elevation only)
- Verify Evolution Power applies on runner evolution

**Step 4: Commit**

```bash
git add -A
git commit -m "chore(summit): cleanup and finalize XP-based system"
```
