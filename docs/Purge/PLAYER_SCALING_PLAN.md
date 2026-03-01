# Purge Player-Count Scaling - Implementation Plan

## Goal

Scale monster spawn counts dynamically based on how many players are in a Purge session. Solo play keeps base counts; each additional player increases the total zombies spawned.

## Current State

- Monster counts are **per-wave, per-variant** in the database (`purge_wave_variant_counts` table)
- **No player-count scaling exists** - a 4-player session spawns the same zombies as solo
- Wave HP scaling exists separately: `1.0 + (wave - 2) * 0.12` (unrelated, stays as-is)
- Player count is already tracked: `PurgeSession.getConnectedCount()`

## Scaling Formula

**Proposed**: additive linear scaling per extra player.

```
scaledCount = baseCount * (1.0 + SCALE_PER_PLAYER * (playerCount - 1))
```

### Comparison of multiplier values

| Players | 0.5x per extra (1.5x) | 0.75x per extra | 1.0x per extra (2x) |
|---------|----------------------|-----------------|---------------------|
| 1       | 1.0x (base)          | 1.0x            | 1.0x                |
| 2       | 1.5x                 | 1.75x           | 2.0x                |
| 3       | 2.0x                 | 2.5x            | 3.0x                |
| 4       | 2.5x                 | 3.25x           | 4.0x                |

**Example** - Wave 1 has 8 base zombies (5 SLOW + 3 NORMAL):

| Players | 0.5x/player | 0.75x/player | 1.0x/player |
|---------|-------------|--------------|-------------|
| 1       | 8           | 8            | 8           |
| 2       | 12          | 14           | 16          |
| 3       | 16          | 20           | 24          |
| 4       | 20          | 26           | 32          |

### Recommendation

**0.75x per extra player** feels like the sweet spot:
- 1.0x (double per player) is aggressive — 4 players face 4x zombies, which may overwhelm since zombies scale HP per wave too
- 0.5x is conservative — 4 players at only 2.5x may feel too easy
- 0.75x gives meaningful pressure without being punishing

But this is a balance call — the constant can be tuned later without code changes (it's a single constant).

## Implementation

### Step 1: Add scaling method to `PurgeWaveManager`

Add a static method that computes the scaled count:

```java
private static final double PLAYER_SCALE_PER_EXTRA = 0.75;

/**
 * Scale a base spawn count by connected player count.
 * Solo (1 player) returns the base count unchanged.
 */
static int scaledSpawnCount(int baseCount, int playerCount) {
    if (playerCount <= 1) return baseCount;
    double multiplier = 1.0 + PLAYER_SCALE_PER_EXTRA * (playerCount - 1);
    return (int) Math.round(baseCount * multiplier);
}
```

**File**: `PurgeWaveManager.java`

### Step 2: Apply scaling when building the spawn queue

In `startNextWave()`, the wave definition's `variantCounts` map is used to build the spawn queue. Scale each variant's count before building:

```java
// In startNextWave(), before buildSpawnQueue():
int playerCount = session.getConnectedCount();
Map<String, Integer> scaledCounts = new LinkedHashMap<>();
for (var entry : wave.variantCounts().entrySet()) {
    scaledCounts.put(entry.getKey(), scaledSpawnCount(entry.getValue(), playerCount));
}
// Use scaledCounts instead of wave.variantCounts() for queue building
```

This means `buildSpawnQueue()` needs to accept a `Map<String, Integer>` parameter instead of reading from the wave definition directly.

**File**: `PurgeWaveManager.java`
**Methods**: `startNextWave()`, `buildSpawnQueue()`

### Step 3: Update total-count tracking

The session tracks `totalZombiesThisWave` for wave-clear detection. This must use the scaled count, not the base count. Since the scaled counts are computed in `startNextWave()`, pass the scaled total through.

**File**: `PurgeWaveManager.java`
**Method**: `startNextWave()` — where `totalZombiesThisWave` is set

### Changes summary

| File | Change |
|------|--------|
| `PurgeWaveManager.java` | Add `scaledSpawnCount()` method |
| `PurgeWaveManager.java` | Scale variant counts in `startNextWave()` before queue building |
| `PurgeWaveManager.java` | Update `buildSpawnQueue()` to accept counts map parameter |

**No database changes.** No new tables, columns, or configs. The scaling constant lives in code.

### What NOT to change

- Wave HP scaling (`hpMultiplier`) — independent concern, stays as-is
- Spawn delay / batch size — no need to scale timing; more zombies is enough pressure
- Variant stats (damage, speed) — scaling count only, not individual power
- Scrap rewards — players earn more scrap naturally by killing more zombies

## Edge Cases

- **Player disconnects mid-wave**: Scaling is computed at wave start, not adjusted mid-wave. If a player leaves, the remaining players face the same count. This is intentional — recalculating mid-wave would be jarring and complex.
- **Rounding**: `Math.round()` ensures clean integer counts. A base of 3 at 1.75x = 5.25 rounds to 5.
- **Solo play**: `playerCount <= 1` short-circuits to base count, zero overhead.
