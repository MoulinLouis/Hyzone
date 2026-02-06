"""
Ascend economy simulation v7 - flattened cost curve.
Cost of level N: 30000 * 1.15^(N^0.77) instead of 30000 * 1.15^N.
Level = multiplier (1:1), cost just grows slower at high levels.
"""

PB_TIMES = [2.6, 5.0, 10.0, 15.0, 20.0]
BASE_REWARDS = [1, 5, 25, 100, 500]
MAP_UNLOCK_PRICES = [0, 100, 500, 2500, 10000]
RUNNER_PRICES = [50, 200, 1000, 5000, 20000]

SPEED_PER_LEVEL = 0.10
MAX_SPEED_LEVEL = 20

ELEVATION_BASE_COST = 30000
ELEVATION_COST_GROWTH = 1.15
ELEVATION_COST_CURVE = 0.77  # flattens exponential at high levels

MULT_INCREMENT = 0.1
MANUAL_MULT_INCREMENT = 0.1
MANUAL_RUN_OVERHEAD = 2.0

ASCENSION_THRESHOLD = 10_000_000_000_000_000  # 10Q

DT = 0.25
SIM_TIME = 14400

import math

def speed_upgrade_cost(speed_level, map_display_order):
    offsets = [0, 3, 6, 10, 15]
    map_mults = [1.0, 2.0, 4.0, 8.0, 16.0]
    offset = offsets[map_display_order]
    map_mult = map_mults[map_display_order]
    effective = speed_level + offset
    base = 5 * (2 ** effective) + effective * 10
    early_boost = 1.0
    if speed_level < 10 and map_display_order >= 2:
        boost_map = [1.0, 1.0, 4.0, 8.0, 16.0]
        max_boost = boost_map[map_display_order]
        if max_boost > 1.0:
            decay = (10 - speed_level) / 10.0
            early_boost = 1.0 + (max_boost - 1.0) * decay
    return base * map_mult * early_boost

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

def fmt(n):
    if abs(n) >= 1e15: return f"{n/1e15:.2f}Q"
    if abs(n) >= 1e12: return f"{n/1e12:.2f}T"
    if abs(n) >= 1e9: return f"{n/1e9:.2f}B"
    if abs(n) >= 1e6: return f"{n/1e6:.2f}M"
    if abs(n) >= 1e3: return f"{n/1e3:.2f}K"
    return f"{n:.0f}"

def simulate():
    elevation = 1
    coins = 0.0
    multipliers = [1.0] * 5
    speed_levels = [0] * 5
    maps_unlocked = [True, False, False, False, False]
    runners_bought = [False] * 5

    time = 0.0
    last_income_rate = 0.0
    elevation_count = 0
    accumulating = False
    time_since_elevation = 0.0

    interval = 120
    next_snapshot = 0
    rows = []
    ascension_time = None
    elev_log = []

    while time < SIM_TIME:
        mult_product = 1.0
        for m in multipliers:
            mult_product *= max(1.0, m)
        elev_mult = max(1, elevation)

        # === INCOME ===
        runner_income = 0.0
        for i in range(5):
            if not runners_bought[i]:
                continue
            speed_mult = 1.0 + speed_levels[i] * SPEED_PER_LEVEL
            completion_rate = speed_mult / PB_TIMES[i]
            reward_per_run = BASE_REWARDS[i] * mult_product * elev_mult
            runner_income += completion_rate * reward_per_run * DT
            multipliers[i] += completion_rate * MULT_INCREMENT * DT

        manual_income = 0.0
        best_manual_map = -1
        for i in range(4, -1, -1):
            if maps_unlocked[i]:
                best_manual_map = i
                break
        if best_manual_map >= 0:
            t_per_run = PB_TIMES[best_manual_map] + MANUAL_RUN_OVERHEAD
            runs_per_sec = 1.0 / t_per_run
            manual_reward = BASE_REWARDS[best_manual_map] * mult_product * elev_mult
            manual_income = runs_per_sec * manual_reward * DT
            multipliers[best_manual_map] += runs_per_sec * MANUAL_MULT_INCREMENT * DT

        total_income = runner_income + manual_income
        coins += total_income
        last_income_rate = total_income / DT if DT > 0 else 0

        # === SPENDING ===
        for i in range(5):
            if not maps_unlocked[i] and coins >= MAP_UNLOCK_PRICES[i]:
                coins -= MAP_UNLOCK_PRICES[i]
                maps_unlocked[i] = True
        for i in range(5):
            if maps_unlocked[i] and not runners_bought[i] and coins >= RUNNER_PRICES[i]:
                coins -= RUNNER_PRICES[i]
                runners_bought[i] = True
        for _ in range(5):
            best_i = -1
            best_cost = float('inf')
            for i in range(5):
                if runners_bought[i] and speed_levels[i] < MAX_SPEED_LEVEL:
                    c = speed_upgrade_cost(speed_levels[i], i)
                    if c < best_cost and c <= coins * 0.6:
                        best_cost = c
                        best_i = i
            if best_i >= 0:
                coins -= best_cost
                speed_levels[best_i] += 1
            else:
                break

        # === ELEVATION DECISION ===
        time_since_elevation += DT

        if not accumulating and time_since_elevation > 30:
            if runners_bought[0]:
                levels_gain, cost = calc_elevation_purchase(elevation, coins)
                if levels_gain > 0:
                    gain_pct = levels_gain / max(1, elevation)
                    min_levels = max(3, int(elevation * 0.3))
                    if levels_gain >= min_levels:
                        old_elev = elevation
                        elevation += levels_gain
                        elevation_count += 1
                        elev_log.append((time/60, old_elev, elevation, levels_gain, gain_pct*100))
                        coins = 0.0
                        multipliers = [1.0] * 5
                        speed_levels = [0] * 5
                        maps_unlocked = [True, False, False, False, False]
                        runners_bought = [False] * 5
                        time_since_elevation = 0.0
                    elif time_since_elevation > 300 and gain_pct < 0.1:
                        accumulating = True

        if ascension_time is None and coins >= ASCENSION_THRESHOLD:
            ascension_time = time

        if time >= next_snapshot:
            phase = "ACCUM" if accumulating else "ELEV"
            rows.append((next_snapshot / 60, coins, last_income_rate, elevation, elevation_count, phase))
            next_snapshot += interval

        time += DT
        if ascension_time is not None and time > ascension_time + 600:
            break

    print(f"{'Time':>6s} | {'Coins':>12s} | {'Coins/sec':>12s} | {'Elevation':>10s} | {'Elev#':>5s} | Phase")
    print("-" * 70)
    for mins, c, rate, elev, ecount, phase in rows:
        marker = ""
        if ascension_time and abs(mins - ascension_time/60) < 2:
            marker = " << 10Q"
        print(f"{mins:5.0f}m | {fmt(c):>12s} | {fmt(rate):>12s}/s | x{elev:<8d} | {ecount:>5d} | {phase}{marker}")

    print("\n--- Elevation Log ---")
    for t, old_e, new_e, gain, pct in elev_log:
        print(f"  {t:5.1f}m: x{old_e} -> x{new_e} (+{gain} levels, +{pct:.0f}%)")

    # Show cost comparison
    print("\n--- Cost Comparison: Old vs New ---")
    print(f"{'Level':>8s} | {'Old Cost':>14s} | {'New Cost':>14s} | {'Ratio':>8s}")
    print("-" * 52)
    for lvl in [1, 5, 10, 20, 50, 100, 200, 500]:
        old_cost = 30000 * (1.15 ** lvl)
        new_cost = elevation_cost(lvl)
        ratio = new_cost / old_cost if old_cost > 0 else 0
        print(f"{lvl:>8d} | {fmt(old_cost):>14s} | {fmt(new_cost):>14s} | {ratio:>7.2f}x")

    if ascension_time is not None:
        print(f"\n>> ASCENSION (10Q) reached at {ascension_time/60:.1f} min")
    else:
        print(f"\n>> NOT reached in {SIM_TIME/60:.0f} min. Final: {fmt(coins)}, x{elevation}")

simulate()
