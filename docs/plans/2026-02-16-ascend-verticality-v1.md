# Ascend Verticality v1 — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add 3 new challenges, an AP multiplier system (1 + completed challenges), remove old per-challenge rewards, and make Summit levels unlimited.

**Architecture:** Challenge rewards are replaced by a universal AP multiplier derived from the count of completed challenges. Summit hard cap is removed by decoupling the level calculation upper bound from the XP calibration constant. No new DB tables.

**Tech Stack:** Java (Hytale server plugin), Hytale Custom UI (.ui files). No automated tests — manual in-game verification.

---

### Task 1: Add new ChallengeType entries

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/AscendConstants.java:677-693`

**Step 1: Add 3 new enum entries after CHALLENGE_4**

In `AscendConstants.ChallengeType`, add after CHALLENGE_4 (line 693, replace the semicolon with a comma and add):

```java
        CHALLENGE_4(4, "Challenge 4",
            "Complete an Ascension with 50% Evolution Power",
            "#ef4444",
            Set.of(), Set.of(), 1.0, 1.0, 0.5),
        CHALLENGE_5(5, "Challenge 5",
            "Complete an Ascension with 50% Runner Speed and Multiplier Gain",
            "#8b5cf6",
            Set.of(), Set.of(), 0.5, 0.5, 1.0),
        CHALLENGE_6(6, "Challenge 6",
            "Complete an Ascension with all Summit bonuses at 50%",
            "#ec4899",
            Set.of(), Set.of(), 0.5, 0.5, 0.5),
        CHALLENGE_7(7, "Challenge 7",
            "Complete an Ascension without maps 4 and 5",
            "#f97316",
            Set.of(), Set.of(3, 4), 1.0, 1.0, 1.0);
```

Note: `Set.of(3, 4)` blocks map displayOrder 3 (Vert) and 4 (Bleu).

**Step 2: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/AscendConstants.java
git commit -m "feat(ascend): add challenges 5-7 (mixed summit malus, all summit, maps 4+5 blocked)"
```

---

### Task 2: Add AP multiplier to AscensionManager

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendPlayerProgress.java:517-527`
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ascension/AscensionManager.java:40-54`

**Step 1: Add getCompletedChallengeCount() to AscendPlayerProgress**

After the `getCompletedChallengeRewards()` method (line 527), add:

```java
    public int getCompletedChallengeCount() {
        return completedChallengeRewards.size();
    }
```

**Step 2: Apply AP multiplier in AscensionManager.performAscension()**

Change line 53 from:
```java
        int newPoints = progress.addSkillTreePoints(1);
```
To:
```java
        int apGained = 1 + progress.getCompletedChallengeCount();
        int newPoints = progress.addSkillTreePoints(apGained);
```

Update the Javadoc (line 40-41) and log message (line 106-107) accordingly:
```java
    /**
     * Performs an Ascension: grants AP (1 + completed challenges), resets progress (preserves map PBs).
     *
     * @return the new Ascension count, or -1 if insufficient vexa
     */
```

Log:
```java
        LOGGER.atInfo().log("[Ascension] Player " + playerId + " ascended! Count: " + newAscensionCount
            + ", AP gained: " + apGained + ", total AP: " + newPoints);
```

**Step 3: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendPlayerProgress.java \
       hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ascension/AscensionManager.java
git commit -m "feat(ascend): AP multiplier - gain 1 + completedChallenges AP per ascension"
```

---

### Task 3: Remove old challenge permanent rewards

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/robot/RobotManager.java:690-696,1043-1046`
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/tracker/AscendRunTracker.java:294-300`
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/passive/PassiveEarningsManager.java:121-126`
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/summit/SummitManager.java:190-192,209-215,249-250,254-260`

**Step 1: Remove Challenge 1 reward from RobotManager.java**

Delete lines 690-696 (the `// Challenge 1 reward: x1.5 multiplier gain on map 5` block):
```java
        // Challenge 1 reward: x1.5 multiplier gain on map 5 (displayOrder 4)
        AscendPlayerProgress ownerProgress = playerStore.getPlayer(ownerId);
        if (ownerProgress != null && ownerProgress.hasChallengeReward(ChallengeType.CHALLENGE_1)) {
            if (map.getDisplayOrder() == 4) {
                multiplierIncrement = multiplierIncrement.multiply(BigNumber.fromDouble(1.5));
            }
        }
```

Delete lines 1043-1046 (the `// Challenge 2 reward: x1.1 global runner speed` block):
```java
                    // Challenge 2 reward: x1.1 global runner speed
                    if (progress.hasChallengeReward(ChallengeType.CHALLENGE_2)) {
                        speedMultiplier *= 1.1;
                    }
```

Check if these removals cause the `ChallengeType` import to become unused. If so, remove the import.

**Step 2: Remove Challenge 1 reward from AscendRunTracker.java**

Delete lines 294-300:
```java
        // Challenge 1 reward: x1.5 multiplier gain on map 5 (displayOrder 4)
        AscendPlayerProgress challengeProgress = playerStore.getPlayer(playerId);
        if (challengeProgress != null && challengeProgress.hasChallengeReward(ChallengeType.CHALLENGE_1)) {
            if (map.getDisplayOrder() == 4) {
                runnerIncrement = runnerIncrement.multiply(BigNumber.fromDouble(1.5));
            }
        }
```

Check if `ChallengeType` import becomes unused.

**Step 3: Remove Challenge 1 reward from PassiveEarningsManager.java**

Delete lines 121-126:
```java
            // Challenge 1 reward: x1.5 multiplier gain on map 5 (displayOrder 4)
            if (progress.hasChallengeReward(ChallengeType.CHALLENGE_1)) {
                if (map.getDisplayOrder() == 4) {
                    multiplierIncrement = multiplierIncrement.multiply(BigNumber.fromDouble(1.5));
                }
            }
```

Check if `ChallengeType` import becomes unused.

**Step 4: Remove Challenge 3 & 4 rewards from SummitManager.java**

In `getMultiplierGainBonus()` (lines 190-192), remove:
```java
        // Challenge 3 reward: x1.2 permanent multiplier gain bonus
        fullBonus = applyChallengeRewardMultiplierGain(playerId, fullBonus);
```
Just `return fullBonus;` directly.

Delete the entire `applyChallengeRewardMultiplierGain` method (lines 209-215).

In `getEvolutionPowerBonus()` (lines 249-250), remove:
```java
        // Challenge 4 reward: +1 base Evolution Power
        fullBonus = applyChallengeRewardEvolutionPower(playerId, fullBonus);
```
Just `return fullBonus;` directly.

Delete the entire `applyChallengeRewardEvolutionPower` method (lines 254-260).

Update Javadoc on both methods to remove references to challenge rewards.

**Step 5: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/robot/RobotManager.java \
       hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/tracker/AscendRunTracker.java \
       hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/passive/PassiveEarningsManager.java \
       hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/summit/SummitManager.java
git commit -m "refactor(ascend): remove old per-challenge permanent rewards (replaced by AP multiplier)"
```

---

### Task 4: Remove Summit level cap

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/AscendConstants.java:444-445,473-476,575-589,595-600`
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ui/SummitPage.java:147-198`
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/command/SummitCommand.java:118-123`

**Important:** Do NOT change `SUMMIT_MAX_LEVEL` or `SUMMIT_MAX_XP` — they are used to calibrate the XP conversion formula (`SUMMIT_XP_VEXA_POWER`). Instead, remove the cap enforcement from level calculation and bonus calculation, keeping the calibration intact.

**Step 1: Uncap `getBonusForLevel()` in SummitCategory**

In `AscendConstants.java`, in `SummitCategory.getBonusForLevel()` (line 476), change:
```java
            int safeLevel = Math.max(0, Math.min(level, SUMMIT_MAX_LEVEL));
```
To:
```java
            int safeLevel = Math.max(0, level);
```

Also update the Javadoc on the method (line 473) — remove `500-1000 (hard cap)`, replace with `500+ (deep cap)`.

**Step 2: Uncap `calculateLevelFromXp()`**

In `AscendConstants.java`, change `calculateLevelFromXp()` (lines 575-589) to dynamically compute the upper bound instead of using SUMMIT_MAX_LEVEL:

```java
    public static int calculateLevelFromXp(long xp) {
        if (xp <= 0) return 0;
        // Dynamic upper bound: cumXp ~ n^3/3, so n ~ (3*xp)^(1/3)
        int hi = Math.max(SUMMIT_MAX_LEVEL, (int) Math.ceil(Math.cbrt(3.0 * xp)) + 10);
        int lo = 0;
        while (lo < hi) {
            int mid = lo + (hi - lo + 1) / 2;
            if (getCumulativeXpForLevel(mid) <= xp) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }
```

**Step 3: Uncap `getXpProgress()`**

In `AscendConstants.java`, change `getXpProgress()` (lines 595-600). Remove the early return at max level:

```java
    public static long[] getXpProgress(long totalXp) {
        int level = calculateLevelFromXp(totalXp);
        long xpForCurrentLevel = getCumulativeXpForLevel(level);
        long xpInLevel = totalXp - xpForCurrentLevel;
        long xpForNextLevel = getXpForLevel(level + 1);
        return new long[]{xpInLevel, xpForNextLevel};
    }
```

**Step 4: Remove "max level" display from SummitPage.java**

In `SummitPage.java`, remove the `isMaxLevel` variable and all branches using it (lines 147-198). Replace with:

```java
            // Category name with level progression
            String levelText;
            if (preview.hasGain()) {
                levelText = String.format(" (Lv.%d -> Lv.%d)", preview.currentLevel(), preview.newLevel());
            } else {
                levelText = String.format(" (Lv.%d)", preview.currentLevel());
            }
            commandBuilder.set("#CategoryCards[" + i + "] #CategoryName.Text", category.getDisplayName() + levelText);

            // Bonus text
            String bonusText = "Current: " + formatBonus(category, preview.currentBonus())
                + " -> Next: " + formatBonus(category, preview.newBonus());
            commandBuilder.set("#CategoryCards[" + i + "] #CategoryBonus.Text", bonusText);

            // XP progress text
            String xpText;
            long xpRemaining = preview.currentXpRequired() - preview.currentXpInLevel();
            long xpAfterSummit = xpRemaining - preview.xpToGain();
            if (preview.xpToGain() > 0 && xpAfterSummit <= 0) {
                long nextLevelXpReq = AscendConstants.getXpForLevel(preview.newLevel() + 1);
```

Keep the rest of the XP text logic unchanged (the `else` branch with the normal format stays).

For the progress bar (lines 191-198), remove the `isMaxLevel` branch:
```java
            double progressPercent = preview.currentXpRequired() > 0
                ? (double) preview.currentXpInLevel() / preview.currentXpRequired()
                : 0;
```

**Step 5: Remove "max level" check from SummitCommand.java**

Delete lines 118-123:
```java
            if (preview.currentLevel() >= AscendConstants.SUMMIT_MAX_LEVEL) {
                player.sendMessage(Message.raw("[Summit] " + category.getDisplayName()
                    + " is already at max level (" + AscendConstants.SUMMIT_MAX_LEVEL + ").")
                    .color(SystemMessageUtils.SECONDARY));
                return;
            }
```

**Step 6: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/AscendConstants.java \
       hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ui/SummitPage.java \
       hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/command/SummitCommand.java
git commit -m "feat(ascend): remove summit level cap (levels now unlimited, diminishing returns curve unchanged)"
```

---

### Task 5: Update Challenge UI — accent colors and AP multiplier display

**Files:**
- Modify: `hyvexa-parkour-ascend/src/main/resources/Common/UI/Custom/Pages/Ascend_ChallengeEntry.ui:11-37`
- Modify: `hyvexa-parkour-ascend/src/main/resources/Common/UI/Custom/Pages/Ascend_Challenge.ui:24-29,181-232`
- Modify: `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ui/AscendChallengePage.java`

**Step 1: Add new accent color groups to Ascend_ChallengeEntry.ui**

In the `#AccentBar` group (after line 36, before the closing `}`), add two new accent groups:

```
    Group #AccentViolet {
      Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0);
      Background: #8b5cf6;
      Visible: false;
    }

    Group #AccentPink {
      Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0);
      Background: #ec4899;
      Visible: false;
    }
```

Note: Challenge 7's orange (#f97316) will be mapped to the existing `AccentOrange` (#f59e0b) — close enough for an accent bar.

**Step 2: Add AP Multiplier label in Ascend_Challenge.ui header**

After the "Ascension Challenges" title label (line 29), add an AP multiplier label:

```
      Label #ApMultiplier {
        Anchor: (Left: 20, Top: 34, Width: 300, Height: 18);
        Style: (FontSize: 13, TextColor: #fbbf24, RenderBold: true);
        Text: "AP Multiplier: x1";
      }
```

Increase the header height to accommodate it — change line 15 from `Height: 52` to `Height: 58`.

**Step 3: Expand Break Ascension progress bar to 7 segments**

In `Ascend_Challenge.ui`, expand the progress bar inside `#BreakLocked` (lines 193-232). After the Challenge 4 bar (line 231), add 3 more segments:

```
                Group { Anchor: (Width: 3); }

                // Challenge 5 — violet
                Group {
                  Anchor: (Height: 8, Top: 1);
                  FlexWeight: 1;
                  Background: #1a2530;
                  Group #Bar5 { Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0); Background: #8b5cf6; Visible: false; }
                }
                Group { Anchor: (Width: 3); }

                // Challenge 6 — pink
                Group {
                  Anchor: (Height: 8, Top: 1);
                  FlexWeight: 1;
                  Background: #1a2530;
                  Group #Bar6 { Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0); Background: #ec4899; Visible: false; }
                }
                Group { Anchor: (Width: 3); }

                // Challenge 7 — orange
                Group {
                  Anchor: (Height: 8, Top: 1);
                  FlexWeight: 1;
                  Background: #1a2530;
                  Group #Bar7 { Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0); Background: #f97316; Visible: false; }
                }
```

**Step 4: Update AscendChallengePage.java**

Multiple changes needed:

**(a) Add new accent colors to the map:**
```java
    private static final Map<String, String> ACCENT_COLOR_MAP = Map.of(
        "#ef4444", "AccentRed",
        "#3b82f6", "AccentBlue",
        "#10b981", "AccentGreen",
        "#f59e0b", "AccentOrange",
        "#8b5cf6", "AccentViolet",
        "#ec4899", "AccentPink",
        "#f97316", "AccentOrange"  // Challenge 7 orange → reuse AccentOrange
    );
    private static final String[] ALL_ACCENT_IDS = {"AccentRed", "AccentBlue", "AccentGreen", "AccentOrange", "AccentViolet", "AccentPink"};
```

Note: `Map.of()` doesn't allow duplicate keys, but #f97316 maps to AccentOrange same as #f59e0b. Since both can't be in the same Map.of(), use `Map.ofEntries()` instead, or simply change Challenge 7's accentColor in the enum to `"#f59e0b"` (matching the existing orange). **Simpler: change Challenge 7 to use `"#f59e0b"` in AscendConstants.java** so the map has no duplicates:

```java
    private static final Map<String, String> ACCENT_COLOR_MAP = Map.of(
        "#ef4444", "AccentRed",
        "#3b82f6", "AccentBlue",
        "#10b981", "AccentGreen",
        "#f59e0b", "AccentOrange",
        "#8b5cf6", "AccentViolet",
        "#ec4899", "AccentPink"
    );
    private static final String[] ALL_ACCENT_IDS = {"AccentRed", "AccentBlue", "AccentGreen", "AccentOrange", "AccentViolet", "AccentPink"};
```

And in AscendConstants, set Challenge 7's accent to `"#f59e0b"`.

**(b) Set AP multiplier in build():**

After getting `progress` (line 86), add:
```java
        // AP Multiplier display
        int apMultiplier = 1 + (progress != null ? progress.getCompletedChallengeCount() : 0);
        commandBuilder.set("#ApMultiplier.Text", "AP Multiplier: x" + apMultiplier);
```

**(c) Replace buildRewardDescription:**
```java
    private String buildRewardDescription(ChallengeType type) {
        return "Reward: +1 AP Multiplier";
    }
```

**(d) Rewrite buildMalusDescription to handle multiple malus:**
```java
    private String buildMalusDescription(ChallengeType type) {
        List<String> parts = new java.util.ArrayList<>();

        if (!type.getBlockedMapDisplayOrders().isEmpty()) {
            if (type.getBlockedMapDisplayOrders().size() > 1) {
                parts.add("Maps 4 & 5 locked");
            } else {
                parts.add("Map 5 locked");
            }
        }
        if (type.getSpeedEffectiveness() < 1.0) {
            int pct = (int) (type.getSpeedEffectiveness() * 100);
            parts.add("Runner Speed at " + pct + "%");
        }
        if (type.getMultiplierGainEffectiveness() < 1.0) {
            int pct = (int) (type.getMultiplierGainEffectiveness() * 100);
            parts.add("Multiplier Gain at " + pct + "%");
        }
        if (type.getEvolutionPowerEffectiveness() < 1.0) {
            int pct = (int) (type.getEvolutionPowerEffectiveness() * 100);
            parts.add("Evolution Power at " + pct + "%");
        }

        if (parts.isEmpty()) return "Malus: None";
        return "Malus: " + String.join(" + ", parts);
    }
```

**(e) Update Break Ascension message** (line 298):
Change `"Complete all 4 challenges first."` to `"Complete all challenges first."`

**Step 5: Commit**

```bash
git add hyvexa-parkour-ascend/src/main/resources/Common/UI/Custom/Pages/Ascend_ChallengeEntry.ui \
       hyvexa-parkour-ascend/src/main/resources/Common/UI/Custom/Pages/Ascend_Challenge.ui \
       hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ui/AscendChallengePage.java \
       hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/AscendConstants.java
git commit -m "feat(ascend): update challenge UI with AP multiplier display, new accent colors, 7-segment progress bar"
```

---

### Task 6: Update CHANGELOG and economy docs

**Files:**
- Modify: `CHANGELOG.md`
- Modify: `docs/ECONOMY_BALANCE.md`

**Step 1: Add to CHANGELOG.md**

Add a new section at the top:
```markdown
## v0.1.3 — Ascend Verticality v1

### Ascend
- Added 3 new challenges: mixed Summit malus, all Summit malus, maps 4+5 blocked
- Challenge rewards now grant AP multiplier (+1 per challenge completed) instead of permanent bonuses
- Summit levels are now unlimited (diminishing returns curve unchanged)
- AP Multiplier displayed in the Challenges tab
```

**Step 2: Update ECONOMY_BALANCE.md**

Update the relevant sections for:
- AP formula: `AP per ascension = 1 + completed_challenges` (max x8 with 7 challenges)
- Challenge rewards: now all give +1 AP multiplier instead of individual bonuses
- Summit cap: removed (was 1000, now unlimited)

**Step 3: Commit**

```bash
git add CHANGELOG.md docs/ECONOMY_BALANCE.md
git commit -m "docs: update changelog and economy balance for verticality v1"
```
