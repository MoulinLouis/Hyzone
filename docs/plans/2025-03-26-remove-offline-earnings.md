# Plan: Remove All Offline/Passive Earnings from Ascend

## Context

The user wants to completely remove the offline earnings system from Ascend. Currently, when a player disconnects, their runners "continue earning" at 10% rate, and on reconnect a popup shows what they earned while away. This entire system needs to be removed.

## Changes

### 1. Delete dedicated files (3 Java + 2 UI)

- `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/passive/PassiveEarningsManager.java` — core logic
- `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ui/PassiveEarningsPage.java` — UI controller
- `hyvexa-parkour-ascend/src/main/resources/Common/UI/Custom/Pages/Ascend_PassiveEarnings.ui` — popup layout
- `hyvexa-parkour-ascend/src/main/resources/Common/UI/Custom/Pages/Ascend_PassiveEarningsEntry.ui` — entry layout

### 2. Remove constants from `AscendConstants.java`

Delete `PASSIVE_OFFLINE_RATE_PERCENT`, `PASSIVE_MAX_TIME_MS`, `PASSIVE_MIN_TIME_MS` (lines ~52-55).

### 3. Clean up `SessionState.java`

Remove `lastActiveTimestamp` and `hasUnclaimedPassive` fields + getters/setters.

### 4. Clean up `AscendPlayerStore.java`

Remove the 4 public methods (~lines 1062-1080): `getLastActiveTimestamp`, `setLastActiveTimestamp`, `hasUnclaimedPassive`, `setHasUnclaimedPassive`.

### 5. Clean up `AscendPlayerPersistence.java`

- Remove `last_active_timestamp` and `has_unclaimed_passive` from INSERT/UPDATE SQL statements
- Remove loading of those columns on player data load (~lines 694-699)

### 6. Clean up `AscendDatabaseSetup.java`

Remove the migration blocks that ADD these two columns (~lines 816-845).

> **Note:** The `last_active_timestamp` and `has_unclaimed_passive` columns will remain in already-migrated databases as inert dead columns. No DROP COLUMN migration is added — it would lock the table for no functional benefit since nothing reads or writes them after this change.

### 7. Clean up `ParkourAscendPlugin.java`

- Remove `passiveEarningsManager` field and its initialization (~line 127, 352-355)
- Remove `checkPassiveEarningsOnJoin` call (~lines 539-541)
- Remove `onPlayerLeaveAscend` call (~lines 995-996)
- Remove import

### 8. Remove offline copy from in-game onboarding

In `AscendOnboardingCopy.java`, rewrite the 3 lines that mention offline/away earnings:
- Line 42: "earning multiplier while you're away" → remove offline claim
- Line 46: "They earn multiplier even while you're offline" → reword
- Line 182: "even while you're offline" → remove offline claim

### 9. Update documentation

- `docs/Ascend/ECONOMY_BALANCE.md` — remove "Passive Earnings / Offline Production" section (lines ~306-320) and other offline references
- `docs/Ascend/DATABASE.md` — remove `last_active_timestamp` and `has_unclaimed_passive` column docs
- `docs/Ascend/README.md` — remove PassiveEarningsManager references
- `docs/Ascend/TUTORIAL_FLOW.md` — remove "even while you're offline" and "while you're away" mentions
- `docs/ARCHITECTURE.md` — remove passive earnings constants from config table (~lines 716-717)
- `docs/ARCHITECTURE_DIAGRAMS.md` — remove `PassiveEarningsManager` from diagram (~line 124)
- `CHANGELOG.md` — add removal entry

## Verification

- `grep -r "PassiveEarnings\|lastActiveTimestamp\|hasUnclaimedPassive\|last_active_timestamp\|has_unclaimed_passive\|PASSIVE_OFFLINE\|PASSIVE_MAX\|PASSIVE_MIN" hyvexa-parkour-ascend/` — zero results
- `grep -r "PassiveEarnings\|passive earning\|offline.*earn\|offline.*rate" docs/` — zero results
- `grep -ri "while you're offline\|while you're away" hyvexa-parkour-ascend/` — zero results (catches onboarding copy)
- Build compiles (on user request)
