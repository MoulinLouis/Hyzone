> **Note:** Build commands in this plan predate the WSL2 migration. Use native `./gradlew` commands — see [DEVELOPMENT_ENVIRONMENT.md](../DEVELOPMENT_ENVIRONMENT.md).

# Unit Test Expansion Plan

## Current State

### Existing test infrastructure
| Module | JUnit in build.gradle | `src/test/` exists | Test count |
|--------|-----------------------|--------------------|------------|
| hyvexa-core | Yes | Yes | 9 tests |
| hyvexa-parkour | Yes | Yes | 1 test |
| hyvexa-parkour-ascend | Yes | Yes | 1 test |
| hyvexa-purge | Yes (inferred) | Yes | 2 tests |
| hyvexa-runorfall | Yes (inferred) | Yes | 2 tests |
| hyvexa-votifier | **No** | **No** | 0 |
| hyvexa-hub | **No** | **No** | 0 |
| hyvexa-wardrobe | **No** | **No** | 0 |

### Existing tests (16 total)
- `BigNumberTest`, `FormatUtilsTest`, `GhostInterpolationTest`, `PaginationStateTest`
- `DailyShopRotationTest`, `AssetPathUtilsTest`, `DamageBypassRegistryTest`
- `CosmeticDefinitionTest`, `PurgeSkinDefinitionTest`, `PurgeSkinRegistryTest`
- `DuelMatchTest`
- `AscendConstantsTest`
- `PurgePlayerStatsTest`, `PurgeWaveDefinitionTest`
- `RunOrFallPlayerStatsTest`, `RunOrFallPlatformTest`

---

## Implementation Plan

### Phase 1 — Infrastructure Setup (modules without test support)

#### Step 1.1: Add JUnit to hyvexa-votifier/build.gradle
Add to dependencies:
```groovy
testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
```
Add task:
```groovy
tasks.named('test') {
    useJUnitPlatform()
}
```
Create directory: `hyvexa-votifier/src/test/java/org/hyvote/plugins/votifier/`

#### Step 1.2: Add JUnit to hyvexa-hub/build.gradle and hyvexa-wardrobe/build.gradle
Same pattern. Only do this if testable pure-logic classes are found (currently unlikely — these modules are thin wrappers).

#### Step 1.3: Add JUnit to hyvexa-purge/build.gradle and hyvexa-runorfall/build.gradle
These modules already have tests but may not have explicit JUnit config in build.gradle. Verify and add if missing.

---

### Phase 2 — Critical: Economy & Progression Formulas (highest risk)

These classes contain financial/progression formulas where bugs directly impact gameplay balance.

#### Step 2.1: `MineUpgradeType` — upgrade cost & effect formulas
**File:** `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/mine/data/MineUpgradeType.java`
**Risk:** Exponential cost curves — off-by-one in exponent breaks economy
**Tests to write:**
- `getCost()` for each upgrade type at level 0, mid, max-1 — verify exponential scaling
- `getEffect()` for each type at level 0, 1, mid, max — verify linear formulas
- `getChance()` for AoE upgrades (JACKHAMMER, STOMP, BLAST) at level 0, 1, mid, max — verify interpolation
- `getChance()` returns -1 for non-AoE types
- Boundary: level < 0, level > maxLevel (clamping behavior)

#### Step 2.2: `AscendConstants` — runner upgrade costs & multiplier formulas
**File:** `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/AscendConstants.java`
**Risk:** `getRunnerUpgradeCost()` and `getRunnerMultiplierIncrement()` drive core idle loop economy
**Tests to write (extend existing `AscendConstantsTest`):**
- `getRunnerUpgradeCost()` at various (speedLevel, mapDisplayOrder, stars) combos
- Verify cost increases with level, map order, and stars
- `getRunnerMultiplierIncrement()` — base case (no bonuses), with star scaling, with summit bonuses
- `calculateEarlyLevelBoost()` — level 0 gets full boost, level 9 gets minimal, level 10+ gets 1.0, stars > 0 gets 1.0
- `clampedLookup` edge cases (negative index, index beyond array)
- Infinity/NaN guards in multiplier increment

#### Step 2.3: `MinePlayerProgress` — inventory, sell, upgrade, conveyor logic
**File:** `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/mine/data/MinePlayerProgress.java`
**Risk:** 577 lines of inventory management with capacity limits, sell calculations, upgrade purchases
**Tests to write:**
- **Inventory:** `addToInventory`, `addToInventoryUpTo` with partial fills, `isInventoryFull` at boundary
- **Selling:** `sellBlock` (single type), `sellAll`, `sellAllExcept` — verify crystals earned and inventory cleared
- **Sell value:** `calculateInventoryValue` with various block prices, missing prices (defaults to 1)
- **Upgrades:** `purchaseUpgrade` — success, max level rejection, insufficient crystals
- **Pickaxe:** `purchasePickaxeEnhancement` — success, already maxed, no crystals. `upgradePickaxeTier` — all result paths
- **Conveyor:** `addToConveyorBuffer` capacity limits, `transferBufferToInventory` partial transfer when bag full, `transferBlockFromBuffer`
- **Eggs:** `addEgg`, `removeEgg` (count → 0 removes entry), `getEggCount`
- **Miners:** `addMiner`, `getMinerById`, slot assignment/unassignment, `isMinerAssigned`, `upgradeMinerSpeed`
- **Momentum:** `getMomentumMultiplier` at 0 and N combos, `getHasteMultiplier` at various levels

#### Step 2.4: `AscendPlayerProgress` — core progression state
**File:** `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/data/AscendPlayerProgress.java`
**Risk:** 775 lines, thread-safe atomic operations, multi-system state
**Tests to write:**
- **Volt:** `addVolt`, `casVolt` (CAS semantics — success and failure), `totalVoltEarned` tracking
- **Summit XP:** `addSummitXp`, `getSummitLevel` calculation from XP
- **Ascension:** `incrementAscensionCount`, `getSkillTreePoints`, skill node unlock/check
- **Achievements:** unlock, check, `getCompletedChallengeCount`
- **Map progress:** `MapProgress` nested class — momentum tracking, speed level, star evolution
- **Automation toggles:** set/get round-trip
- **Thread safety:** concurrent `addVolt` from multiple threads doesn't lose updates (stress test with `casVolt`)

---

### Phase 3 — Critical: Cryptography & Vote Parsing (security risk)

#### Step 3.1: `HmacUtil` — HMAC-SHA256 signature operations
**File:** `hyvexa-votifier/src/main/java/org/hyvote/plugins/votifier/crypto/HmacUtil.java`
**Tests to write:**
- `computeSignature` returns correct HMAC for known payload+token (compare against reference)
- `verifySignature` accepts valid signature, rejects invalid signature
- `verifySignature` rejects malformed Base64
- `verifySignature` returns false (not exception) on any error

#### Step 3.2: `V2VoteParser` — Votifier V2 protocol parsing
**File:** `hyvexa-votifier/src/main/java/org/hyvote/plugins/votifier/vote/V2VoteParser.java`
**Tests to write:**
- Valid payload parses correctly (construct real HMAC-signed JSON)
- Missing `payload` field → `VoteParseException`
- Missing `signature` field → `VoteParseException`
- Missing `serviceName` → `VoteParseException`
- Missing `username` → `VoteParseException`
- Invalid JSON → `VoteParseException`
- Wrong signature → `V2SignatureException`
- No token configured → `V2SignatureException`
- Challenge verification: match succeeds, mismatch throws `V2ChallengeException`, missing challenge throws
- Timestamp conversion: seconds → millis, millis stays millis, zero/negative → fallback

**Note:** `VoteSiteTokenConfig` needs to be either a simple POJO or we create a test-friendly implementation. Check if it's an interface or concrete class.

---

### Phase 4 — Medium Risk: Game State & Statistics

#### Step 4.1: `DuelStats` — win rate calculation
**File:** `hyvexa-parkour/src/main/java/io/hyvexa/duel/data/DuelStats.java`
**Tests to write:**
- `getWinRate()` → 0 when no games played
- `getWinRate()` → 100 when all wins
- `getWinRate()` → 50 for equal wins/losses
- Rounding behavior (e.g., 1 win, 2 losses = 33%)
- `incrementWins` / `incrementLosses` state tracking

#### Step 4.2: `DailyResetUtils` — UTC reset timing
**File:** `hyvexa-core/src/main/java/io/hyvexa/common/util/DailyResetUtils.java`
**Tests to write:**
- `formatTimeRemaining()` — pure math, easy: 3661s → "1h 1m", 0s → "0h 0m"
- `getSecondsUntilReset()` — returns value in [0, 86400), always positive
- **Note:** time-dependent tests — use range assertions, not exact values

#### Step 4.3: `GhostRecording` — recording container
**File:** `hyvexa-core/src/main/java/io/hyvexa/common/ghost/GhostRecording.java`
**Tests to write:**
- Construction with sample list
- `interpolateAt()` delegates correctly
- `getCompletionTimeMs()` returns last sample's timestamp
- Empty recording edge case

---

### Phase 5 — Lower Risk: Data Models & Enums

#### Step 5.1: `CollectedMiner` and `PickaxeTier`
Verify `PickaxeTier.fromTier()` round-trips, `next()` returns correct progression, last tier returns null.

#### Step 5.2: `PurgeWaveDefinition` — verify `totalCount()` calculation
Already has tests — review coverage and extend if needed.

#### Step 5.3: Votifier config POJOs
Low priority — mostly getters/setters. Skip unless time permits.

---

## Execution Notes

- **Run tests via:** `cmd.exe /c "gradlew.bat test"` (WSL2 I/O workaround)
- **Run single module:** `cmd.exe /c "gradlew.bat :hyvexa-parkour-ascend:test"`
- **Dependencies:** Some classes reference `AscendConstants` enums — these are pure-logic, so the dependency chain stays testable
- **No mocking needed:** All target classes are pure logic with no Hytale server dependencies
- **Naming convention:** Follow existing pattern — `<ClassName>Test.java` in mirrored package under `src/test/java/`

## Priority Order (for incremental delivery)

1. **Phase 1.1** — votifier test infra (unblocks Phase 3)
2. **Phase 2.1** — `MineUpgradeType` (most formula-dense enum, quick win)
3. **Phase 2.2** — `AscendConstants` extensions (core economy)
4. **Phase 2.3** — `MinePlayerProgress` (largest untested pure-logic class)
5. **Phase 3.1** — `HmacUtil` (security-critical, small surface)
6. **Phase 3.2** — `V2VoteParser` (security-critical, complex validation)
7. **Phase 4.1** — `DuelStats` (quick win)
8. **Phase 4.2** — `DailyResetUtils` (quick win)
9. **Phase 2.4** — `AscendPlayerProgress` (largest scope, needs careful CAS testing)
10. **Phase 4.3 + 5** — remaining classes

## Estimated New Tests

| Phase | Tests | Classes |
|-------|-------|---------|
| 2.1 | ~15 | MineUpgradeType |
| 2.2 | ~12 | AscendConstants (extension) |
| 2.3 | ~25 | MinePlayerProgress |
| 2.4 | ~20 | AscendPlayerProgress |
| 3.1 | ~5 | HmacUtil |
| 3.2 | ~12 | V2VoteParser |
| 4.1 | ~5 | DuelStats |
| 4.2 | ~4 | DailyResetUtils |
| 4.3+5 | ~8 | GhostRecording, PickaxeTier, CollectedMiner |
| **Total** | **~106** | **10 classes** |
