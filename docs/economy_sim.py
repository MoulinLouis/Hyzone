"""
Ascend economy simulation v8 - fidelity pass.
Matches ECONOMY_BALANCE.md and AscendConstants.java exactly.

Key mechanics modeled:
- Runners are free, require map unlocked + ghost (one-time manual completion)
- Ghosts are permanent: once created, they persist across all resets (elevation, summit)
- Map auto-unlock: runner on map i reaching speedLevel >= 5 unlocks map i+1 (once per elevation)
- Evolution: stars track per runner, resets speedLevel but not cost progression
- totalLevel = stars * 20 + speedLevel (continuous cost curve)
- Multiplier gain: +0.1/run at 0 stars, +0.2/run at 1+ stars
- Elevation: cost 30000 * 1.15^(level^0.77), multiplier = level
- Elevation resets: coins, map unlocks, multipliers, runners, stars, speed levels (NOT ghosts)

Strategy:
- Elevation timing: mini forward simulation (60s) models compound multiplier growth
  to project future coins. Elevate only when waiting barely improves the payoff
  AND the gain is meaningful (>= 30% of current elevation).
  No sticky ACCUM flag — always re-evaluate.
- Upgrade purchasing: ROI-based (best payback time first) instead of cheapest-first.
  Prioritizes upgrades that increase income the most per coin spent.
"""

import math

# === CONSTANTS (from AscendConstants.java / ECONOMY_BALANCE.md) ===

BASE_TIMES = [5, 10, 16, 26, 42]           # Runner base run times (seconds)
BASE_REWARDS = [1, 5, 25, 100, 500]        # Coins per completion
MAP_UNLOCK_PRICES = [0, 100, 500, 2500, 10000]

SPEED_PER_LEVEL = 0.10                     # +10% per speed level (uniform all maps)
MAX_SPEED_LEVEL = 20
MAX_STARS = 5
MAP_UNLOCK_REQUIRED_LEVEL = 5              # Auto-unlock next map at runner level 5

MAP_OFFSETS = [0, 1, 2, 3, 4]
MAP_COST_MULTIPLIERS = [1.0, 1.4, 1.9, 2.6, 3.5]
MAP_EARLY_BOOST = [1.0, 1.5, 2.0, 2.5, 3.0]
EARLY_BOOST_THRESHOLD = 10                 # Boost applies for speedLevel 0-9

ELEVATION_BASE_COST = 30000
ELEVATION_COST_GROWTH = 1.15
ELEVATION_COST_CURVE = 0.77                # flattens exponential at high levels

MANUAL_MULT_INCREMENT = 0.1               # +0.1 per manual completion
RUNNER_MULT_BASE = 0.1                    # +0.1 at 0 stars
RUNNER_MULT_EVOLVED = 0.2                 # +0.2 at 1+ stars

MANUAL_RUN_OVERHEAD = 2.0                 # Seconds overhead per manual run

ASCENSION_THRESHOLD = 10_000_000_000_000_000  # 10Q

DT = 0.25
SIM_TIME = 14400

# --- Strategy tuning ---
FORWARD_HORIZON = 60       # Seconds to simulate ahead for elevation decision
FORWARD_STEP = 1.0         # Step size for forward sim (coarser = faster)
MARGINAL_THRESHOLD = 0.10  # Elevate when waiting 60s gains < 10% more levels
MIN_ELEV_GAIN_PCT = 0.30   # Only elevate if gain >= 30% of current elevation
ELEV_COOLDOWN = 30         # Seconds after elevation before re-evaluating
MAX_UPGRADE_PAYBACK = 60   # Only buy upgrades that pay back within 60s
MAX_UPGRADE_SPEND = 0.70   # Don't spend more than 70% of coins on one upgrade


def early_level_boost(speed_level, map_idx, stars):
    """Decaying cost boost for levels 0-9 on maps 1+ during first evolution (0 stars)."""
    if stars > 0 or speed_level >= EARLY_BOOST_THRESHOLD:
        return 1.0
    max_boost = MAP_EARLY_BOOST[map_idx]
    if max_boost <= 1.0:
        return 1.0
    decay = (EARLY_BOOST_THRESHOLD - speed_level) / EARLY_BOOST_THRESHOLD
    return 1.0 + (max_boost - 1.0) * decay


def speed_upgrade_cost(speed_level, map_idx, stars):
    """Cost to upgrade runner speed. Uses totalLevel for continuous progression."""
    total_level = stars * MAX_SPEED_LEVEL + speed_level
    effective_level = total_level + MAP_OFFSETS[map_idx]
    base = 5 * (2 ** effective_level) + effective_level * 10
    cost = base * MAP_COST_MULTIPLIERS[map_idx]
    boost = early_level_boost(speed_level, map_idx, stars)
    return cost * boost


def elevation_cost(level):
    effective = level ** ELEVATION_COST_CURVE
    return ELEVATION_BASE_COST * (ELEVATION_COST_GROWTH ** effective)


def calc_elevation_purchase(current_level, coins):
    levels = 0
    total_cost = 0.0
    lvl = current_level
    while True:
        c = elevation_cost(lvl)
        if total_cost + c > coins:
            break
        total_cost += c
        lvl += 1
        levels += 1
        if levels > 100000:
            break
    return levels, total_cost


def runner_mult_increment(stars):
    """Multiplier gain per runner completion: 0.1 at 0 stars, 0.2 at 1+ stars."""
    return RUNNER_MULT_EVOLVED if stars > 0 else RUNNER_MULT_BASE


def project_coins_forward(coins, multipliers, speed_levels, stars, has_runner,
                          maps_unlocked, has_ghost, elev_mult):
    """Mini forward simulation projecting coins FORWARD_HORIZON seconds ahead.

    Models compound multiplier growth from runners + manual play, giving a much
    more accurate projection than the linear `coins + rate * time` approach.
    """
    sim_mults = list(multipliers)
    sim_coins = coins

    # Determine manual play target (same logic as main loop)
    manual_map = -1
    for i in range(4, -1, -1):
        if maps_unlocked[i] and not has_ghost[i]:
            manual_map = i
            break
    if manual_map < 0:
        for i in range(4, -1, -1):
            if maps_unlocked[i]:
                manual_map = i
                break

    t = 0.0
    while t < FORWARD_HORIZON:
        mp = 1.0
        for m in sim_mults:
            mp *= max(1.0, m)

        for i in range(5):
            if not has_runner[i]:
                continue
            sm = 1.0 + speed_levels[i] * SPEED_PER_LEVEL
            cr = sm / BASE_TIMES[i]
            sim_coins += BASE_REWARDS[i] * mp * elev_mult * cr * FORWARD_STEP
            mi = runner_mult_increment(stars[i])
            sim_mults[i] += cr * mi * FORWARD_STEP

        if manual_map >= 0:
            rps = 1.0 / (BASE_TIMES[manual_map] + MANUAL_RUN_OVERHEAD)
            sim_coins += BASE_REWARDS[manual_map] * mp * elev_mult * rps * FORWARD_STEP
            sim_mults[manual_map] += rps * MANUAL_MULT_INCREMENT * FORWARD_STEP

        t += FORWARD_STEP

    return sim_coins


def fmt(n):
    if abs(n) >= 1e15: return f"{n/1e15:.2f}Q"
    if abs(n) >= 1e12: return f"{n/1e12:.2f}T"
    if abs(n) >= 1e9: return f"{n/1e9:.2f}B"
    if abs(n) >= 1e6: return f"{n/1e6:.2f}M"
    if abs(n) >= 1e3: return f"{n/1e3:.2f}K"
    return f"{n:.0f}"


def simulate():
    # --- Per-map state ---
    elevation = 0
    coins = 0.0
    multipliers = [1.0] * 5
    speed_levels = [0] * 5
    stars = [0] * 5
    maps_unlocked = [True, False, False, False, False]
    has_runner = [False] * 5
    has_ghost = [False] * 5           # Permanent: once set, never cleared by any reset
    auto_unlocked_next = [False] * 5  # Per-elevation guard: True once map i has auto-unlocked map i+1

    time = 0.0
    last_income_rate = 0.0
    elevation_count = 0
    time_since_elevation = 0.0

    interval = 300                           # Snapshot every 5 minutes
    next_snapshot = 0
    rows = []
    ascension_time = None
    elev_log = []

    while time < SIM_TIME:
        # Calculate total multiplier = product of all 5 map multipliers
        mult_product = 1.0
        for m in multipliers:
            mult_product *= max(1.0, m)
        # Elevation multiplier: level directly (0 and 1 both give x1)
        elev_mult = max(1, elevation)

        # === INCOME ===
        runner_income = 0.0
        for i in range(5):
            if not has_runner[i]:
                continue
            # Speed upgrades reduce run time multiplicatively
            speed_mult = 1.0 + speed_levels[i] * SPEED_PER_LEVEL
            completion_rate = speed_mult / BASE_TIMES[i]
            reward_per_run = BASE_REWARDS[i] * mult_product * elev_mult
            runner_income += completion_rate * reward_per_run * DT
            # Runner multiplier gain: applied to runner's own map slot
            mult_inc = runner_mult_increment(stars[i])
            multipliers[i] += completion_rate * mult_inc * DT

        # Manual play: prioritize maps needing first completion (to create ghosts),
        # then fall back to highest unlocked map for best rewards
        manual_map = -1
        for i in range(4, -1, -1):
            if maps_unlocked[i] and not has_ghost[i]:
                manual_map = i
                break
        if manual_map < 0:
            for i in range(4, -1, -1):
                if maps_unlocked[i]:
                    manual_map = i
                    break

        manual_income = 0.0
        if manual_map >= 0:
            t_per_run = BASE_TIMES[manual_map] + MANUAL_RUN_OVERHEAD
            runs_per_sec = 1.0 / t_per_run
            manual_reward = BASE_REWARDS[manual_map] * mult_product * elev_mult
            manual_income = runs_per_sec * manual_reward * DT
            # Manual multiplier gain: +0.1 per completion (not affected by stars)
            multipliers[manual_map] += runs_per_sec * MANUAL_MULT_INCREMENT * DT
            # One-time ghost creation: once set, persists forever (across all resets)
            if not has_ghost[manual_map]:
                has_ghost[manual_map] = True

        total_income = runner_income + manual_income
        coins += total_income
        last_income_rate = total_income / DT if DT > 0 else 0

        # === SPENDING ===

        # Map unlocks via coins
        for i in range(5):
            if not maps_unlocked[i] and coins >= MAP_UNLOCK_PRICES[i]:
                coins -= MAP_UNLOCK_PRICES[i]
                maps_unlocked[i] = True

        # Auto-unlock: runner on map i reaching level 5 unlocks map i+1 (once per elevation)
        for i in range(4):
            if not auto_unlocked_next[i] and has_runner[i] and speed_levels[i] >= MAP_UNLOCK_REQUIRED_LEVEL:
                auto_unlocked_next[i] = True
                if not maps_unlocked[i + 1]:
                    maps_unlocked[i + 1] = True

        # Spawn runners (free): requires map unlocked + ghost exists
        for i in range(5):
            if maps_unlocked[i] and has_ghost[i] and not has_runner[i]:
                has_runner[i] = True

        # Speed upgrades: ROI-based (best payback time first)
        # Buy the upgrade that pays for itself fastest, as long as payback < threshold.
        while True:
            best_i = -1
            best_payback = float('inf')
            best_cost = 0
            for i in range(5):
                if has_runner[i] and speed_levels[i] < MAX_SPEED_LEVEL:
                    c = speed_upgrade_cost(speed_levels[i], i, stars[i])
                    if c > coins * MAX_UPGRADE_SPEND:
                        continue
                    # Income delta: +1 speed level gives +SPEED_PER_LEVEL more completions/sec
                    delta_income = BASE_REWARDS[i] * mult_product * elev_mult * SPEED_PER_LEVEL / BASE_TIMES[i]
                    if delta_income <= 0:
                        continue
                    payback = c / delta_income
                    if payback < best_payback:
                        best_payback = payback
                        best_i = i
                        best_cost = c
            if best_i >= 0 and best_payback < MAX_UPGRADE_PAYBACK:
                coins -= best_cost
                speed_levels[best_i] += 1
            else:
                break

        # Evolution: at max speed level, evolve if stars < MAX_STARS
        for i in range(5):
            if has_runner[i] and speed_levels[i] >= MAX_SPEED_LEVEL and stars[i] < MAX_STARS:
                stars[i] += 1
                speed_levels[i] = 0  # Reset speed level (cost continues from totalLevel)

        # === ELEVATION DECISION ===
        # Forward sim lookahead: project coins 60s ahead with compound multiplier growth.
        # Elevate when: (1) waiting barely improves payoff (marginal < 10%)
        #           AND (2) gain is meaningful (>= 30% of current elevation).
        # No sticky ACCUM flag — always re-evaluate.
        time_since_elevation += DT

        if time_since_elevation > ELEV_COOLDOWN and has_runner[0]:
            levels_now, _ = calc_elevation_purchase(elevation, coins)
            if levels_now >= 3:
                gain_pct = levels_now / max(1, elevation)
                if gain_pct >= MIN_ELEV_GAIN_PCT:
                    projected = project_coins_forward(
                        coins, multipliers, speed_levels, stars,
                        has_runner, maps_unlocked, has_ghost, elev_mult)
                    levels_later, _ = calc_elevation_purchase(elevation, projected)
                    marginal = (levels_later - levels_now) / max(1, levels_now)
                    if marginal < MARGINAL_THRESHOLD:
                        old_elev = elevation
                        elevation += levels_now
                        elevation_count += 1
                        elev_log.append((time/60, old_elev, elevation, levels_now, gain_pct*100))
                        # Elevation resets: coins, map unlocks (except first),
                        # multipliers, runners, stars, speed levels.
                        # Ghosts are permanent — runners respawn as soon as
                        # their map is re-unlocked (no manual replay needed).
                        coins = 0.0
                        multipliers = [1.0] * 5
                        speed_levels = [0] * 5
                        stars = [0] * 5
                        maps_unlocked = [True, False, False, False, False]
                        has_runner = [False] * 5
                        auto_unlocked_next = [False] * 5  # Reset per-elevation guard
                        # has_ghost is permanent — NOT touched by elevation
                        time_since_elevation = 0.0

        if ascension_time is None and coins >= ASCENSION_THRESHOLD:
            ascension_time = time

        # === SNAPSHOT ===
        if time >= next_snapshot:
            n_runners = sum(1 for r in has_runner if r)
            n_maps = sum(1 for u in maps_unlocked if u)
            # Coins per run on best map (highest unlocked runner)
            best_run_reward = 0.0
            for i in range(4, -1, -1):
                if has_runner[i]:
                    best_run_reward = BASE_REWARDS[i] * mult_product * elev_mult
                    break
            # Speed summary: list of speed levels for active runners
            spd_summary = "/".join(str(speed_levels[i]) for i in range(5) if has_runner[i]) or "-"
            star_summary = "/".join(f"{stars[i]}*" for i in range(5) if has_runner[i]) or "-"

            rows.append({
                'time': next_snapshot / 60,
                'coins': coins,
                'rate': last_income_rate,
                'elevation': elevation,
                'elev_count': elevation_count,
                'mult_product': mult_product,
                'best_run': best_run_reward,
                'runners': n_runners,
                'maps': n_maps,
                'speeds': spd_summary,
                'stars': star_summary,
            })
            next_snapshot += interval

        time += DT
        if ascension_time is not None and time > ascension_time + 600:
            break

    # === OUTPUT ===
    hdr = (f"{'Time':>6s} | {'Coins':>10s} | {'Coin/sec':>10s} | {'Coin/run':>10s} "
           f"| {'Elev':>6s} | {'Mult':>10s} | {'R':>1s} | {'M':>1s} | {'Speeds':<12s} | {'Stars':<14s}")
    print(hdr)
    print("-" * len(hdr))
    for r in rows:
        marker = ""
        if ascension_time and abs(r['time'] - ascension_time/60) < 5:
            marker = " <<10Q"
        print(f"{r['time']:5.0f}m | {fmt(r['coins']):>10s} | {fmt(r['rate']):>10s}/s | {fmt(r['best_run']):>10s} "
              f"| x{r['elevation']:<5d} | {fmt(r['mult_product']):>10s} | {r['runners']:>1d} | {r['maps']:>1d} "
              f"| {r['speeds']:<12s} | {r['stars']:<14s}{marker}")

    print("\n--- Elevation Log ---")
    for t, old_e, new_e, gain, pct in elev_log:
        print(f"  {t:5.1f}m: x{old_e} -> x{new_e} (+{gain} levels, +{pct:.0f}%)")

    if ascension_time is not None:
        print(f"\n>> ASCENSION (10Q) reached at {ascension_time/60:.1f} min")
    else:
        print(f"\n>> NOT reached in {SIM_TIME/60:.0f} min. Final: {fmt(coins)}, x{elevation}")

simulate()
