# Ascend - Progressive Tutorial Flow

This document defines the progressive tutorial system for Ascend mode. Tutorials appear automatically based on player progression, explaining only what becomes relevant at each milestone.

**UI format:** Split panel — left panel (220px, 9:16 portrait image), right panel (text content). Navigation via Next/Back/Got It buttons.

**Image convention:** `Textures/help/{tutorial}_step{N}.png` (9:16 portrait, 220×392px)

---

## Tutorial Timeline Overview

| # | Trigger | Tutorial Name | Steps |
|---|---------|---------------|-------|
| 1 | First join to Ascend | Welcome | 3 |
| 2 | First manual map completion | First Completion | 2 |
| 3 | Runner reaches level 5 (new map unlocks) | New Map Unlocked | 1 |
| 4 | Runner reaches level 20 (evolution available) | Evolution | 2 |
| 5 | Volt reaches first elevation cost (~30K) | Elevation | 2 |
| 6 | Volt reaches 1B (Summit available) | Summit | 2 |
| 7 | Volt reaches 1Dc (Ascension available) | Ascension | 2 |
| 8 | Ascension Challenges skill unlocked | Challenges | 2 |

---

## 1. Welcome

**Trigger:** First join to Ascend mode.

### Step 1 — Welcome to Ascend

**Image:** `welcome_step1.png`
_Idea: Overview of the Ascend island/world._

**Title:** Welcome to Ascend

**Text:**
> Ascend is a parkour idle game. Run maps, earn volt, and build up an army of automated runners that play for you - even while you're offline.

### Step 2 — Your Shortcuts

**Image:** `welcome_step2.png`
_Idea: Inventory items with labels._

**Title:** Your Shortcuts

**Text:**
> You have 5 items in your inventory that open menus instantly - no need to type commands. Here's what each one does:
>
> - /ascend - Map menu, runners, and upgrades
> - /ascend leaderboard - Rankings and stats
> - /ascend automation - Runner speed controls
> - /ascend help - Tutorials and guides
> - /ascend profile - Your stats and progress

### Step 3 — Play Your First Map

**Image:** `welcome_step3.png`
_Idea: Map select menu with first map highlighted._

**Title:** Play Your First Map

**Text:**
> Open the map menu and pick a map. Complete it to earn your first volt and unlock new features along the way!

---

## 2. First Completion

**Trigger:** First manual map completion.

### Step 1 — Nice Run!

**Image:** `firstcompletion_step1.png`
_Idea: HUD showing volt earned and multiplier going up._

**Title:** Nice Run!

**Text:**
> You earned volt and your map multiplier went up! Manual runs give 5x the runner's multiplier gain.

### Step 2 — Buy a Runner

**Image:** `firstcompletion_step2.png`
_Idea: "Buy Runner" button in map select, or a Kweebec NPC on a map._

**Title:** Automate It

**Text:**
> Open **`/ascend`** and click **Buy Runner**. It replays the map automatically, earning multiplier while you're away.

---

## 3. New Map Unlocked

**Trigger:** Runner reaches level 5 (first time a new map unlocks).

### Step 1 — New Map Available

**Image:** `mapunlock_step1.png`
_Idea: Map select showing a new map appearing._

**Title:** New Map Unlocked!

**Text:**
> Runner level 5 unlocks the next map. All map multipliers are **multiplied together** — more maps = way more volt.

---

## 4. Evolution

**Trigger:** Runner reaches level 20 (max speed) for the first time.

### Step 1 — Evolution Available

**Image:** `evolution_step1.png`
_Idea: "Evolve" button, or runner NPC changing appearance._

**Title:** Evolution

**Text:**
> Your runner hit max speed. **Evolve** it to earn a star — each star triples the multiplier it earns per lap. Speed resets, but the gains are massive.

### Step 2 — Star Power

**Image:** `evolution_step2.png`
_Idea: Star progression with Kweebec variants (0★ → 5★)._

**Title:** Star Power

**Text:**
> **0★** +0.10 · **1★** +0.30 · **2★** +0.90 · **3★** +2.70 · **4★** +8.10 · **5★** +24.3 per lap.
>
> Always evolve when you can.

---

## 5. Elevation

**Trigger:** Volt reaches first elevation cost (~30K).

### Step 1 — Elevation

**Image:** `elevation_step1.png`
_Idea: Elevation UI showing level and multiplier preview._

**Title:** Elevation

**Text:**
> Spend your volt to gain elevation levels. Higher levels give bigger multipliers: level 10 = ×11, level 100 = ×126.
>
> Open with **`/ascend elevate`**.

### Step 2 — Keep Elevating

**Image:** `elevation_step2.png`
_Idea: Before/after multiplier comparison._

**Title:** Elevate Often

**Text:**
> Elevation resets volt, runners, multipliers, and map unlocks. You keep your **best times** and your new elevation level. Elevate often to grow faster.

---

## 6. Summit

**Trigger:** Volt reaches 1 billion (1B).

### Step 1 — The Summit

**Image:** `summit_step1.png`
_Idea: Summit UI with 3 category cards._

**Title:** Summit

**Text:**
> Convert volt into **permanent upgrades**: Runner Speed, Multiplier Gain, and Evolution Power. These stay forever.
>
> Open with **`/ascend summit`**.

### Step 2 — Full Reset

**Image:** `summit_step2.png`
_Idea: Visual of what resets vs. what's kept._

**Title:** The Reset

**Text:**
> Summit resets volt, elevation, runners, and maps. You keep your **best times** and **Summit upgrades**. Each cycle you'll progress faster.

---

## 7. Ascension

**Trigger:** Volt reaches 1 Decillion (1Dc).

### Step 1 — Ascension

**Image:** `ascension_step1.png`
_Idea: Ascension UI with progress bar and "Ascend" button._

**Title:** Ascension

**Text:**
> The ultimate prestige. Resets **everything** including Summit — but grants an **AP** for powerful permanent abilities.
>
> Open with **`/ascend ascension`**.

### Step 2 — Ascendancy Tree

**Image:** `ascension_step2.png`
_Idea: Ascendancy Tree overview._

**Title:** Ascendancy Tree

**Text:**
> 19 ascendancy nodes to unlock:
>
> **Tier 1 (1 AP):** Auto-Upgrade + Momentum, Auto-Evolution, Runner Speed Boost, Evolution Power+, Runner Speed II, Auto-Summit, Auto-Elevation, Ascension Challenges
>
> **Tier 2 (10-50 AP):** Momentum Surge, Momentum Endurance, Multiplier Boost, Runner Speed III, Evolution Power II
>
> **Tier 3 (100-1000 AP):** Runner Speed IV, Evolution Power III, Momentum Mastery, Multiplier Boost II, Auto Ascend, Runner Speed V
>
> AP are permanent across all future Ascensions.

---

## 8. Challenges

**Trigger:** Ascension Challenges skill node unlocked (or first `/ascend challenge` open).

### Step 1 — Ascension Challenges

**Image:** `challenges_step1.png`
_Idea: Challenge selection UI with 4 challenge cards._

**Title:** Ascension Challenges

**Text:**
> Test your skills with timed challenge runs. Each challenge applies a handicap — complete an Ascension under those conditions to earn permanent rewards.

### Step 2 — How It Works

**Image:** `challenges_step2.png`
_Idea: Snapshot/restore flow diagram._

**Title:** How It Works

**Text:**
> Starting a challenge snapshots your progress and resets you. Reach **1Dc** volt to complete it. Your original progress is fully restored afterward — win or quit.

---

## Implementation Notes

### Trigger System

Each tutorial needs a **trigger condition** and a **"seen" flag** stored per player to prevent re-showing.

Suggested approach:
- Store a `Set<String>` of completed tutorial IDs in the player's data (e.g., `ascend_settings` table or a new `ascend_tutorials` table)
- Check triggers at key moments (map completion, runner purchase, runner upgrade, volt threshold reached)
- Only show if the player hasn't seen that tutorial yet

### Trigger Checkpoints (where to hook)

| Tutorial | Where to Check |
|----------|---------------|
| Welcome | `PlayerReady` / first Ascend world join |
| First Completion | `AscendRunTracker` on manual completion |
| New Map Unlocked | `MapUnlockHelper.checkAndEnsureUnlock()` |
| Evolution | Evolution flow in map select page |
| Elevation | `ElevationPage` open or HUD volt threshold check |
| Summit | HUD volt threshold check or `/ascend summit` first open |
| Ascension | HUD volt threshold check or `/ascend ascension` first open |
| Challenges | `SkillTreePage` on ASCENSION_CHALLENGES node unlock or `/ascend challenge` first open |

### Image Checklist

| File | Size | Description |
|------|------|-------------|
| `welcome_step1.png` | 220×320 | Ascend world overview |
| `welcome_step2.png` | 220×320 | Inventory items with labels |
| `welcome_step3.png` | 220×320 | Map select menu / map start |
| `firstcompletion_step1.png` | 220×320 | Volt + multiplier earned |
| `firstcompletion_step2.png` | 220×320 | Buy Runner button / Kweebec NPC |
| `mapunlock_step1.png` | 220×320 | New map appearing in menu |
| `evolution_step1.png` | 220×320 | Evolve button / NPC transformation |
| `evolution_step2.png` | 220×320 | Star progression / Kweebec variants |
| `elevation_step1.png` | 220×320 | Elevation UI page |
| `elevation_step2.png` | 220×320 | Volt → elevation level diagram |
| `summit_step1.png` | 220×320 | Summit UI with 3 categories |
| `summit_step2.png` | 220×320 | Reset vs. kept visual |
| `ascension_step1.png` | 220×320 | Ascension UI / progress bar |
| `ascension_step2.png` | 220×320 | Ascendancy Tree overview |
| `challenges_step1.png` | 220×320 | Challenge selection UI |
| `challenges_step2.png` | 220×320 | Snapshot/restore flow |
