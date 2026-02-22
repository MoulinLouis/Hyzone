# Ascend Verticality v1 — Design

## Scope

Add vertical depth to the Ascend game mode through 3 changes:
1. AP multiplier tied to challenge completions
2. 3 new harder challenges
3. Summit levels unlimited
4. Remove old per-challenge permanent rewards (replaced by AP multiplier)

## 1. AP Multiplier System

**Formula**: `AP gained per ascension = 1 * (1 + completedChallengesCount)`

- Base: x1 (0 challenges done)
- Max with 7 challenges: x8
- Calculated dynamically from `ascend_challenge_records` — no new DB column
- Displayed in Challenge tab UI as "AP Multiplier: xN"

**Code change**: `AscensionManager.performAscension()` line 53:
```java
// Before:
progress.addSkillTreePoints(1);
// After:
progress.addSkillTreePoints(1 + progress.getCompletedChallengeCount());
```

## 2. Remove Old Challenge Permanent Rewards

Remove reward application code from:
- `RobotManager.java` — x1.5 map5 multiplier (l.690-696), +10% speed (l.1043-1046)
- `AscendRunTracker.java` — x1.5 map5 multiplier (l.294-300)
- `PassiveEarningsManager.java` — x1.5 map5 multiplier (l.121-126)
- `SummitManager.java` — +20% multiplier gain (l.209-215), +1 evo power (l.254-260)

Keep `completedChallengeRewards` set in `AscendPlayerProgress` — still needed for sequential unlock logic and AP multiplier count.

Update Challenge UI: replace per-challenge reward text with "Reward: +1 AP Multiplier" for all challenges.

## 3. New Challenges

| ID | Name | Description | Malus | Color |
|----|------|-------------|-------|-------|
| 5 | Challenge 5 | 50% Runner Speed + 50% Multiplier Gain | `runnerSpeedFactor=0.5, multiplierGainFactor=0.5` | `#8b5cf6` (violet) |
| 6 | Challenge 6 | 50% on all 3 Summit categories | `runnerSpeedFactor=0.5, multiplierGainFactor=0.5, evolutionPowerFactor=0.5` | `#ec4899` (pink) |
| 7 | Challenge 7 | Maps 4+5 blocked (Vert + Bleu) | `blockedMaps=Set.of(3, 4)` | `#f97316` (orange) |

- Same framework as challenges 1-4 (ChallengeType enum)
- Sequential unlock: challenge N requires 1..N-1 completed
- DB: `ascend_challenge_records` already supports any `challenge_type_id`
- Each completed challenge adds +1 to AP multiplier (same as existing)

## 4. Summit Unlimited

- Raise `SUMMIT_MAX_LEVEL` from 1000 to 100000
- Existing diminishing returns curve (4th-root above level 500) naturally throttles progression
- Update UI and commands to remove "max level" messaging
- Recalculate `SUMMIT_MAX_XP` accordingly

## Files Impacted

### Core changes:
- `AscendConstants.java` — new ChallengeTypes, summit cap
- `AscensionManager.java` — AP multiplier
- `AscendPlayerProgress.java` — `getCompletedChallengeCount()` method
- `ChallengeManager.java` — unlock logic for 7 challenges

### Reward removal:
- `RobotManager.java`
- `AscendRunTracker.java`
- `PassiveEarningsManager.java`
- `SummitManager.java`

### UI:
- `Ascend_Challenge.ui` — AP multiplier display
- `AscendChallengePage.java` — reward text, multiplier label
- `SummitPage.java` — remove max level check
- `SummitCommand.java` — remove max level message

## Out of Scope
- New skill tree nodes (future update)
- AP cost scaling for existing/new nodes
- Horizontal expansion
