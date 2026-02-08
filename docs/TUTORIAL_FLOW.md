# Ascend - Progressive Tutorial Flow

This document defines the progressive tutorial system for Ascend mode. Tutorials appear automatically based on player progression, explaining only what becomes relevant at each milestone.

**UI format:** Split panel — left panel (220px, 9:16 portrait image), right panel (text content). Navigation via Next/Back/Got It buttons.

**Image convention:** `Textures/help/{tutorial}_step{N}.png` (9:16 portrait, 220×392px)

---

## Tutorial Timeline Overview

| # | Trigger | Tutorial Name | Steps |
|---|---------|---------------|-------|
| 1 | First join to Ascend | Welcome | 2 |
| 2 | First manual map completion | First Completion | 2 |
| 3 | Runner reaches level 5 (new map unlocks) | New Map Unlocked | 1 |
| 4 | Runner reaches level 20 (evolution available) | Evolution | 2 |
| 5 | Coins reach first elevation cost (~30K) | Elevation | 2 |
| 6 | Coins reach 1B (Summit available) | Summit | 2 |
| 7 | Coins reach 10Q (Ascension available) | Ascension | 2 |

---

## 1. Welcome

**Trigger:** First join to Ascend mode.

### Step 1 — Welcome to Ascend

**Image:** `welcome_step1.png`
_Idea: Overview of the Ascend island/world._

**Title:** Welcome to Ascend

**Text:**
> Run parkour maps, earn coins, and unlock automated runners that play for you.

### Step 2 — Play Your First Map

**Image:** `welcome_step2.png`
_Idea: Map select menu with first map highlighted._

**Title:** Your First Run

**Text:**
> Type **`/ascend`** to open the map menu and pick a map. Complete it to earn coins and unlock your runner.

---

## 2. First Completion

**Trigger:** First manual map completion.

### Step 1 — Nice Run!

**Image:** `firstcompletion_step1.png`
_Idea: HUD showing coins earned and multiplier going up._

**Title:** Nice Run!

**Text:**
> You earned **coins** and your map multiplier went up by **+0.1**. The higher your multiplier, the more you earn.

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
> Runner level 5 unlocks the next map. All map multipliers are **multiplied together** — more maps = way more coins.

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

**Trigger:** Coins reach first elevation cost (~30K).

### Step 1 — Elevation

**Image:** `elevation_step1.png`
_Idea: Elevation UI showing level and multiplier preview._

**Title:** Elevation

**Text:**
> Spend your coins to gain elevation levels. Your level **is** your multiplier: level 10 = ×10, level 100 = ×100.
>
> Open with **`/ascend elevate`**.

### Step 2 — Keep Elevating

**Image:** `elevation_step2.png`
_Idea: Before/after multiplier comparison._

**Title:** Elevate Often

**Text:**
> Only your coins are spent — runners, multipliers, and map progress are kept. Elevate whenever you can afford it.

---

## 6. Summit

**Trigger:** Coins reach 1 billion (1B).

### Step 1 — The Summit

**Image:** `summit_step1.png`
_Idea: Summit UI with 3 category cards._

**Title:** Summit

**Text:**
> Convert coins into **permanent upgrades**: Runner Speed, Multiplier Gain, and Evolution Power. These stay forever.
>
> Open with **`/ascend summit`**.

### Step 2 — Full Reset

**Image:** `summit_step2.png`
_Idea: Visual of what resets vs. what's kept._

**Title:** The Reset

**Text:**
> Summit resets coins, elevation, runners, and maps. You keep your **best times** and **Summit upgrades**. Each cycle you'll progress faster.

---

## 7. Ascension

**Trigger:** Coins reach 1 Decillion (1Dc).

### Step 1 — Ascension

**Image:** `ascension_step1.png`
_Idea: Ascension UI with progress bar and "Ascend" button._

**Title:** Ascension

**Text:**
> The ultimate prestige. Resets **everything** including Summit — but grants a **Skill Tree point** for powerful permanent abilities.
>
> Open with **`/ascend ascension`**.

### Step 2 — Skill Tree

**Image:** `ascension_step2.png`
_Idea: Skill tree overview with the 5 paths._

**Title:** Skill Tree

**Text:**
> **Coin** · **Speed** · **Manual** · **Hybrid** · **Ultimate**
>
> 18 nodes across 5 paths. Skill points are permanent across all future Ascensions.

---

## Implementation Notes

### Trigger System

Each tutorial needs a **trigger condition** and a **"seen" flag** stored per player to prevent re-showing.

Suggested approach:
- Store a `Set<String>` of completed tutorial IDs in the player's data (e.g., `ascend_settings` table or a new `ascend_tutorials` table)
- Check triggers at key moments (map completion, runner purchase, runner upgrade, coin threshold reached)
- Only show if the player hasn't seen that tutorial yet

### Trigger Checkpoints (where to hook)

| Tutorial | Where to Check |
|----------|---------------|
| Welcome | `PlayerReady` / first Ascend world join |
| First Completion | `AscendRunTracker` on manual completion |
| New Map Unlocked | `MapUnlockHelper.checkAndEnsureUnlock()` |
| Evolution | Evolution flow in map select page |
| Elevation | `ElevationPage` open or HUD coin threshold check |
| Summit | HUD coin threshold check or `/ascend summit` first open |
| Ascension | HUD coin threshold check or `/ascend ascension` first open |

### Image Checklist

| File | Size | Description |
|------|------|-------------|
| `welcome_step1.png` | 220×320 | Ascend world overview |
| `welcome_step2.png` | 220×320 | Map select menu / map start |
| `firstcompletion_step1.png` | 220×320 | Coins + multiplier earned |
| `firstcompletion_step2.png` | 220×320 | Buy Runner button / Kweebec NPC |
| `mapunlock_step1.png` | 220×320 | New map appearing in menu |
| `evolution_step1.png` | 220×320 | Evolve button / NPC transformation |
| `evolution_step2.png` | 220×320 | Star progression / Kweebec variants |
| `elevation_step1.png` | 220×320 | Elevation UI page |
| `elevation_step2.png` | 220×320 | Coins → elevation level diagram |
| `summit_step1.png` | 220×320 | Summit UI with 3 categories |
| `summit_step2.png` | 220×320 | Reset vs. kept visual |
| `ascension_step1.png` | 220×320 | Ascension UI / progress bar |
| `ascension_step2.png` | 220×320 | Skill tree overview |
