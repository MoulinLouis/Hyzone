# Ascend Mode - Design Document

## Overview

**Ascend** is an idle/incremental game mode based on parkour. Players complete mini-courses to earn AscendCoins, then purchase robots that automate these courses for passive income.

### Core Principles

- **Total separation** from Parkour mode (different world, currency, progression)
- **Visible robots** as real entities in the game world
- **Manual collection** of earnings to encourage engagement
- **Progressive unlocks** starting simple, adding complexity over time

## Architecture

### Module Structure

```
hyvexa-parkour-ascend/
├── src/main/java/io/hyvexa/ascend/
│   ├── ParkourAscendPlugin.java    # Entry point
│   ├── command/                     # /ascend commands
│   ├── data/                        # MySQL stores
│   ├── robot/                       # Robot spawning & AI
│   ├── tracker/                     # Run tracking for manual runs
│   └── ui/                          # UI pages
└── src/main/resources/
    ├── manifest.json
    └── Common/UI/Custom/Pages/      # UI definitions
```

### Dependencies

- `hyvexa-core` for DatabaseManager, ModeGate, PlayerMode.ASCEND
- Hytale Server API for entity spawning, velocity, physics

### World Setup

- Dedicated Ascend world (separate from Hub and Parkour)
- Open world layout with all mini-maps visible
- Central collection point (chest/NPC)
- Players can walk between maps and observe robots

## Gameplay Loop

### Core Cycle

```
1. Player enters Ascend world
2. Parkour1 is free, other maps locked
3. Complete Parkour1 manually → earn AscendCoins
4. Buy Robot for Parkour1 → robot farms automatically
5. Collect earnings at central chest
6. Buy access to Parkour2 → complete manually → buy robot
7. Scale up robot empire
```

### Rules

| Rule | Description |
|------|-------------|
| Manual completion required | Must complete a map once before assigning a robot |
| Storage limit | Robots have max storage; stop farming when full |
| Central collection | One collection point for all robot earnings |
| No cross-mode interaction | AscendCoins cannot be converted to Parkour XP |

## Robot System

### Technical Implementation

**Spawning:**
```java
// Spawn robot entity
LivingEntity robot = world.spawnEntity(robotEntity, position, rotation);

// Add identification components
store.addComponent(robotRef, RobotComponent.getComponentType(),
    new RobotComponent(ownerId, mapId));
```

**Movement (scripted via velocity):**
```java
// Get velocity component
Velocity velocity = accessor.getComponent(robotRef, Velocity.getComponentType());

// Move toward next waypoint
Vector3d direction = nextWaypoint.subtract(currentPos).normalize();
velocity.set(direction.multiply(speed));

// Check for jump
MovementStatesComponent states = accessor.getComponent(robotRef,
    MovementStatesComponent.getComponentType());
if (states.getMovementStates().onGround && shouldJump) {
    velocity.addForce(0, jumpForce, 0);
}
```

**Waypoint System:**
- Each map has pre-recorded waypoints with timing data
- Waypoints stored as JSON in database
- Robot follows waypoints sequentially
- Teleports back to start on completion

### Robot Lifecycle

```
IDLE (no coins to store)
    │
    ▼
RUNNING (following waypoints)
    │
    ▼
COMPLETED (reached end)
    │
    ├─► Storage not full → Add coins, teleport to start, RUNNING
    │
    └─► Storage full → WAITING (idle animation)
            │
            ▼
        Player collects → RUNNING
```

### Upgrades (MVP)

| Upgrade | Effect | Scaling |
|---------|--------|---------|
| Speed | Faster run completion | +10% per level |
| Gains | More coins per run | +15% per level |

**Future upgrades (post-MVP):**
- Storage capacity
- Efficiency (fewer failures)
- Multi-run (batch runs before stopping)

## Data Model

### MySQL Tables

```sql
-- Player wallet and global state
CREATE TABLE ascend_players (
    uuid VARCHAR(36) PRIMARY KEY,
    coins BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Map definitions
CREATE TABLE ascend_maps (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    price BIGINT NOT NULL DEFAULT 0,           -- Cost to unlock (0 = free)
    robot_price BIGINT NOT NULL,               -- Cost to buy robot
    base_reward BIGINT NOT NULL,               -- Coins per run
    base_run_time_ms BIGINT NOT NULL,          -- Base time for one run
    storage_capacity INT NOT NULL DEFAULT 100, -- Max pending coins
    world VARCHAR(64) NOT NULL,
    start_x DOUBLE, start_y DOUBLE, start_z DOUBLE,
    start_rot_x FLOAT, start_rot_y FLOAT, start_rot_z FLOAT,
    finish_x DOUBLE, finish_y DOUBLE, finish_z DOUBLE,
    waypoints_json TEXT,                       -- JSON array of waypoints
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Player progress per map
CREATE TABLE ascend_player_maps (
    player_uuid VARCHAR(36) NOT NULL,
    map_id VARCHAR(32) NOT NULL,
    unlocked BOOLEAN NOT NULL DEFAULT FALSE,
    completed_manually BOOLEAN NOT NULL DEFAULT FALSE,
    has_robot BOOLEAN NOT NULL DEFAULT FALSE,
    robot_speed_level INT NOT NULL DEFAULT 0,
    robot_gains_level INT NOT NULL DEFAULT 0,
    pending_coins BIGINT NOT NULL DEFAULT 0,
    last_collection_at TIMESTAMP NULL,
    PRIMARY KEY (player_uuid, map_id),
    FOREIGN KEY (player_uuid) REFERENCES ascend_players(uuid) ON DELETE CASCADE,
    FOREIGN KEY (map_id) REFERENCES ascend_maps(id) ON DELETE CASCADE
);

-- Upgrade costs configuration
CREATE TABLE ascend_upgrade_costs (
    upgrade_type VARCHAR(32) NOT NULL,         -- 'speed' or 'gains'
    level INT NOT NULL,
    cost BIGINT NOT NULL,
    PRIMARY KEY (upgrade_type, level)
);
```

### In-Memory Stores

```java
public class AscendPlayerStore {
    private final Map<UUID, AscendPlayer> players = new ConcurrentHashMap<>();
    // Coins, map progress, robot states
}

public class AscendMapStore {
    private final Map<String, AscendMap> maps = new LinkedHashMap<>();
    // Map definitions, waypoints
}

public class RobotManager {
    private final Map<UUID, Map<String, ActiveRobot>> playerRobots = new ConcurrentHashMap<>();
    // Active robot entities, run states
}
```

## User Interface

### HUD Elements

- **Coin counter** (top right): Current AscendCoins balance
- **Collection indicator**: Pulsing icon when earnings available

### Main Menu (`/ascend`)

```
┌─────────────────────────────────────┐
│         ASCEND                      │
│         💰 1,234 coins              │
├─────────────────────────────────────┤
│ [Parkour1] ✅ Robot active          │
│   Speed Lv.2 | Gains Lv.1           │
│   [Upgrade] [Teleport]              │
├─────────────────────────────────────┤
│ [Parkour2] 🔓 Ready for robot       │
│   Cost: 500 coins                   │
│   [Buy Robot] [Teleport]            │
├─────────────────────────────────────┤
│ [Parkour3] 🔒 Locked                │
│   Unlock: 1,000 coins               │
│   [Buy Access]                      │
└─────────────────────────────────────┘
```

### Collection UI (Central Chest)

```
┌─────────────────────────────────────┐
│       COLLECT EARNINGS              │
├─────────────────────────────────────┤
│ Parkour1:  +45 coins                │
│ Parkour2:  +32 coins                │
│ ─────────────────────               │
│ Total:     +77 coins                │
│                                     │
│      [COLLECT ALL]                  │
└─────────────────────────────────────┘
```

### Commands

| Command | Description |
|---------|-------------|
| `/ascend` | Open main menu |
| `/ascend collect` | Collect all pending earnings |
| `/ascend stats` | View personal statistics |

### Admin Commands

| Command | Description |
|---------|-------------|
| `/ascend admin` | Admin panel |
| `/ascend admin maps` | Manage maps |
| `/ascend admin give <player> <coins>` | Give coins |
| `/ascend admin reset <player>` | Reset player progress |

## MVP Scope

### Included

- 3 mini-maps (Parkour1, Parkour2, Parkour3)
- 1 robot type (basic appearance)
- 2 upgrades (Speed, Gains) with 5 levels each
- Central collection point
- Basic UI (menu, collection)
- MySQL persistence
- Robot spawning and scripted movement

### Excluded (Post-MVP)

- Additional upgrades (Storage, Efficiency, Multi-run)
- Robot customization/skins
- Leaderboards
- Prestige/rebirth system
- Sound effects and particles
- Offline earnings calculation

## Economy Balancing (Initial Values)

### Map Costs

| Map | Unlock Cost | Robot Cost | Base Reward | Run Time |
|-----|-------------|------------|-------------|----------|
| Parkour1 | 0 (free) | 100 | 5 | 30s |
| Parkour2 | 200 | 300 | 12 | 45s |
| Parkour3 | 500 | 600 | 25 | 60s |

### Upgrade Costs

| Level | Speed Cost | Gains Cost |
|-------|------------|------------|
| 1 | 50 | 50 |
| 2 | 100 | 100 |
| 3 | 200 | 200 |
| 4 | 400 | 400 |
| 5 | 800 | 800 |

### Storage

- Default capacity: 100 coins per robot
- Forces collection every ~20 runs (Parkour1)

## Technical Considerations

### Threading

- Robot tick updates on world thread via `CompletableFuture.runAsync(..., world)`
- Database saves debounced (5 second delay like Parkour)
- Robot position updates batched per tick

### Performance

- Maximum robots per player: 10 (MVP: 3)
- Robot tick rate: 200ms (5 times/second)
- Despawn robots when owner offline (respawn on login)

### Error Handling

- Robot stuck detection (no progress for 10 seconds) → reset to start
- Invalid waypoints → skip to next valid waypoint
- Database failure → queue for retry, warn player

## Implementation Order

1. **Data layer** - Tables, stores, basic CRUD
2. **Player management** - Join/leave, coin tracking
3. **Map system** - Map definitions, unlock logic
4. **Manual runs** - Complete maps manually for coins
5. **Robot spawning** - Spawn entity, basic appearance
6. **Robot movement** - Waypoint following, velocity
7. **Robot earnings** - Generate coins, storage limit
8. **Collection system** - Central chest, collect all
9. **Upgrades** - Speed and Gains upgrades
10. **UI polish** - Menus, HUD, feedback

## Resolved Design Decisions

1. **Waypoint recording** - Admin tool (OP only) to create maps: set start, record waypoints, set finish. Similar pattern to existing Parkour admin tools.
2. **Robot appearance** - Use `Kweebec_Sapling` entity model from Hytale assets.
3. **World design** - Manual world building by project owners. Plugin only needs spawn coordinates per map.
4. **Offline handling** - Do robots earn while player is offline? (MVP: No)
