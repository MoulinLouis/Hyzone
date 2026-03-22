<!-- Last verified against code: 2026-03-22 -->
# Hyvexa Architecture Diagrams

Visual reference for AI agents and developers. All diagrams reflect the current codebase state.

---

## 1. Module Dependency Graph

```mermaid
graph TD
    CORE[hyvexa-core<br><i>Shared DB, utilities, bridges</i>]
    PARKOUR[hyvexa-parkour<br><i>Main parkour gameplay</i>]
    ASCEND[hyvexa-parkour-ascend<br><i>Ascend idle mode</i>]
    HUB[hyvexa-hub<br><i>Hub routing + mode selection</i>]
    PURGE[hyvexa-purge<br><i>Zombie PvE survival</i>]
    ROF[hyvexa-runorfall<br><i>Platforming minigame</i>]
    WARDROBE[hyvexa-wardrobe<br><i>Cosmetics shop</i>]
    VOTIFIER[hyvexa-votifier<br><i>Vote receiver</i>]
    LAUNCH[hyvexa-launch<br><i>IntelliJ classpath anchor</i>]

    HYTALE[(HytaleServer.jar)]
    HIKARI[(HikariCP + MySQL)]
    HYGUNS[(HygunsPlugin)]
    SQLITE[(SQLite)]

    PARKOUR -->|implementation| CORE
    ASCEND -->|implementation| CORE
    HUB -->|implementation| CORE
    PURGE -->|implementation| CORE
    ROF -->|implementation| CORE
    WARDROBE -->|implementation| CORE

    PARKOUR -.->|compileOnly| VOTIFIER
    PURGE -.->|compileOnly| HYGUNS

    CORE --> HIKARI
    VOTIFIER --> SQLITE

    PARKOUR -.->|compileOnly| HYTALE
    ASCEND -.->|compileOnly| HYTALE
    HUB -.->|compileOnly| HYTALE
    PURGE -.->|compileOnly| HYTALE
    ROF -.->|compileOnly| HYTALE
    WARDROBE -.->|compileOnly| HYTALE
    VOTIFIER -.->|compileOnly| HYTALE

    style CORE fill:#4a9eff,color:#fff
    style VOTIFIER fill:#ff9f43,color:#fff
    style LAUNCH fill:#888,color:#fff
    style HYTALE fill:#333,color:#fff
```

> **Votifier is fully standalone** — no dependency on hyvexa-core, uses SQLite instead of MySQL.

---

## 2. Plugin Initialization Lifecycle

```mermaid
sequenceDiagram
    participant S as Hytale Server
    participant P as Plugin.preLoad()
    participant DB as DatabaseManager
    participant ST as Stores
    participant M as Managers
    participant ECS as ECS Systems
    participant SCH as Scheduled Tasks

    S->>P: preLoad()
    P->>P: Register interaction codecs

    S->>P: setup()
    P->>DB: Initialize HikariCP pool
    DB->>DB: Connect MySQL (10 max connections)

    P->>ST: Initialize stores (lazy-load pattern)
    ST->>DB: CREATE TABLE IF NOT EXISTS
    ST->>DB: Run migrations

    P->>M: Create managers
    M->>ST: Reference stores for data access

    P->>ECS: Register entity systems
    Note over ECS: NoBreakSystem, NoDropSystem,<br>FinishDetection, MineDamage...

    P->>SCH: Schedule periodic tasks
    Note over SCH: HUD ticks, autosave,<br>playtime, cleanup...

    P->>P: Register event handlers
    Note over P: PlayerReady, PlayerDisconnect,<br>AddPlayerToWorld...
```

---

## 3. Per-Module Manager Map

```mermaid
graph LR
    subgraph PARKOUR["hyvexa-parkour"]
        P_RT[RunTracker]
        P_HUD[HudManager]
        P_DUEL[DuelTracker]
        P_GHOST[GhostRecorder<br>GhostNpcManager]
        P_PET[PetManager]
        P_PERKS[PlayerPerksManager]
        P_PLAY[PlaytimeManager]
        P_COL[CollisionManager]
        P_INV[InventorySyncManager]
        P_LB[LeaderboardHologramManager]
        P_ANN[AnnouncementManager]
        P_CLEAN[PlayerCleanupManager]
    end

    subgraph ASCEND["hyvexa-parkour-ascend"]
        A_RT[AscendRunTracker]
        A_HUD[AscendHudManager]
        A_ROB[RobotManager]
        A_SUM[SummitManager]
        A_ASC[AscensionManager]
        A_TRA[TranscendenceManager]
        A_CHL[ChallengeManager]
        A_ACH[AchievementManager]
        A_PAS[PassiveEarningsManager]
        A_TUT[TutorialTriggerService]
        A_MINE[MineManager<br>MineHudManager<br>MineRobotManager]
    end

    subgraph HUB_MOD["hyvexa-hub"]
        H_ROUTER[HubRouter]
        H_HUD[Hub HUD Lifecycle]
    end

    subgraph PURGE_MOD["hyvexa-purge"]
        PU_SESS[PurgeSessionManager]
        PU_WAVE[PurgeWaveManager]
        PU_INST[PurgeInstanceManager]
        PU_PARTY[PurgePartyManager]
        PU_HUD[PurgeHudManager]
        PU_UPG[PurgeUpgradeManager]
        PU_WXP[WeaponXpManager]
        PU_CLS[PurgeClassManager]
        PU_MIS[PurgeMissionManager]
    end

    subgraph ROF_MOD["hyvexa-runorfall"]
        R_GM[RunOrFallGameManager]
    end

    subgraph WARD_MOD["hyvexa-wardrobe"]
        W_TABS[ShopTab Registry<br>WardrobeShopTab<br>EffectsShopTab<br>PurgeSkinShopTab]
    end
```

---

## 4. Core Infrastructure

```mermaid
graph TD
    subgraph DATABASE["Database Layer"]
        DM[DatabaseManager<br><i>Singleton, HikariCP pool</i>]
        BPS[BasePlayerStore&lt;V&gt;<br><i>Abstract, ConcurrentHashMap cache</i>]
        CCS[CachedCurrencyStore<br><i>Abstract, 30min TTL cache</i>]
    end

    subgraph STORES["Shared Stores"]
        VS[VexaStore]
        FS[FeatherStore]
        CS[CosmeticStore]
        DLS[DiscordLinkStore]
        AS[AnalyticsStore]
        PSS[PurgeSkinStore]
    end

    subgraph BRIDGES["Cross-Module Bridges"]
        GMB[GameModeBridge<br><i>Interaction routing</i>]
        WB[WardrobeBridge<br><i>Atomic cosmetic purchases</i>]
        CB[CurrencyBridge<br><i>Currency abstraction</i>]
        STR[ShopTabRegistry<br><i>Cross-module shop tabs</i>]
        WR[WhitelistRegistry<br><i>File-based fallback</i>]
        MHB[MultiHudBridge<br><i>HUD attach/evict</i>]
    end

    subgraph UTILS["Utilities"]
        AEH[AsyncExecutionHelper<br><i>World-thread dispatch</i>]
        MG[ModeGate<br><i>World type checking</i>]
        SI[StoreInitializer<br><i>Safe bulk init</i>]
    end

    CCS --> DM
    BPS --> DM
    VS --> CCS
    FS --> CCS
    CS --> BPS
    DLS --> BPS
    VS --> CB
    FS --> CB
    WB --> CB
    WB --> CS
```

---

## 5. Player Data Flow

### Login

```mermaid
sequenceDiagram
    participant Client
    participant EH as Event Handler
    participant MG as ModeGate
    participant ST as Stores (Cache)
    participant DB as MySQL
    participant W as World Thread

    Client->>EH: PlayerConnectEvent
    EH->>EH: Disable collision
    EH->>EH: Sync inventory

    Client->>EH: PlayerReadyEvent
    EH->>MG: Check world type
    MG-->>EH: isParkourWorld ✓

    par Load player data
        EH->>ST: getOrLoad(playerId)
        ST->>ST: Check cache → miss
        ST->>DB: SELECT FROM player_progress
        DB-->>ST: Row data
        ST->>ST: Cache in ConcurrentHashMap
        ST-->>EH: PlayerData
    end

    EH->>W: world.execute()
    W->>W: Attach HUD
    W->>W: Apply perks
    W->>W: Restore saved run state
    W->>W: Teleport to checkpoint
```

### Disconnect

```mermaid
sequenceDiagram
    participant Client
    participant EH as Event Handler
    participant ST as Stores
    participant DB as MySQL
    participant HUD as MultiHudBridge

    Client->>EH: PlayerDisconnectEvent

    par Persist & Cleanup
        EH->>ST: Save run state
        ST->>DB: INSERT ON DUPLICATE KEY UPDATE
        EH->>ST: Save settings
        ST->>DB: UPDATE player_settings
        EH->>HUD: evictPlayer(uuid)
        EH->>ST: evict(uuid) on all stores
        Note over ST: Clear ConcurrentHashMap entries
    end
```

---

## 6. Persistence Pattern

```mermaid
graph LR
    subgraph WRITE["Write Path"]
        CODE[Game Logic] -->|"Immediate"| CACHE[(In-Memory Cache<br>ConcurrentHashMap)]
        CACHE -->|"Debounced / Async"| POOL[HikariCP Pool]
        POOL -->|"INSERT ON DUPLICATE<br>KEY UPDATE"| MYSQL[(MySQL)]
    end

    subgraph READ["Read Path"]
        REQ[Data Request] -->|"getOrLoad()"| CHECK{Cache Hit?}
        CHECK -->|"Yes"| RET[Return Cached]
        CHECK -->|"No"| LOAD[Load from DB]
        LOAD --> MYSQL
        MYSQL --> FILL[Cache Result]
        FILL --> RET
    end

    style CACHE fill:#4a9eff,color:#fff
    style MYSQL fill:#ff6b6b,color:#fff
```

> **Memory-first**: reads always hit cache. Writes update cache immediately, then queue async DB persistence. Player sees no latency.

---

## 7. Threading Model

```mermaid
graph TD
    subgraph THREADS["Thread Types"]
        WT[World Thread<br><i>Entity mods, teleports,<br>inventory, HUD</i>]
        SE[Scheduled Executor<br><i>Tick loops, delayed callbacks</i>]
        HP[HikariCP Pool<br><i>MySQL queries</i>]
        MT[Main/Command Thread<br><i>Event handlers, scheduling</i>]
    end

    subgraph RULES["Safety Rules"]
        R1["Entity changes → world.execute()"]
        R2["DB queries → async / HikariCP"]
        R3["Scheduled tasks → CAN read cache<br>CANNOT modify entities directly"]
        R4["Stores → ConcurrentHashMap (thread-safe)"]
    end

    SE -->|"world.execute()"| WT
    MT -->|"CompletableFuture.runAsync()"| HP
    HP -->|"thenAccept → world.execute()"| WT
    SE -->|"Read only"| R4

    style WT fill:#e74c3c,color:#fff
    style SE fill:#f39c12,color:#fff
    style HP fill:#3498db,color:#fff
    style MT fill:#2ecc71,color:#fff
```

### Async Safety Pattern

```mermaid
sequenceDiagram
    participant ANY as Any Thread
    participant DB as DB Pool (async)
    participant WT as World Thread

    ANY->>DB: loadAsync(playerId)
    DB->>DB: SELECT FROM table
    DB-->>ANY: CompletableFuture<Data>
    ANY->>WT: .thenAccept → world.execute()
    WT->>WT: Modify entity safely
```

---

## 8. Scheduled Tasks Overview

```mermaid
gantt
    title Tick Intervals by Module
    dateFormat X
    axisFormat %L ms

    section Parkour
    RunTrackerTick (ECS)     :50, 100
    HUD Updates              :100, 200
    Duel Tick                :100, 200
    Collision Removal        :2000, 4000
    Playtime Accumulate      :60000, 120000

    section Ascend
    Main Tick (50ms + 200ms full) :50, 100
    Ghost Recording              :50, 100
    Robot Replay                 :50, 100
    Mine Zone Regen              :1000, 2000

    section Hub
    HUD Recovery      :1000, 2000
    Player Counts     :5000, 10000

    section Purge
    Combo Bar Decay   :50, 100
    HUD Slow Update   :5000, 10000
```

---

## 9. Cross-Module Communication

```mermaid
graph TD
    subgraph BRIDGES["Bridge Pattern (Separate Classloaders)"]
        direction TB
        GMB[GameModeBridge<br><i>Interaction routing without<br>direct module deps</i>]
        CB[CurrencyBridge<br><i>Vexa/Feather abstraction</i>]
        WB[WardrobeBridge<br><i>Atomic purchase transactions</i>]
        STR[ShopTabRegistry<br><i>Shared shop tab list</i>]
        WLR[WhitelistRegistry<br><i>+ file-based fallback</i>]
        MHB[MultiHudBridge<br><i>Cross-module HUD control</i>]
    end

    HUB[Hub] -->|"Routes players"| GMB
    ROF[RunOrFall] -->|"Registers handlers"| GMB
    PARKOUR[Parkour] -->|"Registers handlers"| GMB

    WARDROBE[Wardrobe] -->|"Registers tabs"| STR
    WARDROBE -->|"Purchases via"| WB
    WB -->|"Deducts via"| CB

    VS[VexaStore] -->|"Registers"| CB
    FS[FeatherStore] -->|"Registers"| CB

    ASCEND[Ascend] -->|"Registers whitelist"| WLR
    HUB -->|"Reads whitelist"| WLR

    PARKOUR -->|"Attaches HUDs"| MHB
    ASCEND -->|"Attaches HUDs"| MHB
    HUB -->|"Attaches HUDs"| MHB

    style BRIDGES fill:#1a1a2e,color:#fff
```

> **Why bridges?** Each plugin loads in its own classloader. Direct singleton access across plugins fails. Bridges use static registries in hyvexa-core (shared classloader) or file-based fallbacks.

---

## 10. UI System

```mermaid
graph TD
    subgraph PAGE["Custom UI Pages"]
        UI_FILE[".ui File<br><i>(JSON layout definition)</i>"]
        PAGE_CLS["InteractiveCustomUIPage<br><i>build() + handleDataEvent()</i>"]
        CMD["UICommandBuilder<br><i>append .ui, set properties</i>"]
        EVT["UIEventBuilder<br><i>bind element events</i>"]
    end

    subgraph HUD["HUD System"]
        HUD_CLS["CustomHUDInstance<br><i>tick() every 100-5000ms</i>"]
        MHB_2["MultiHudBridge<br><i>attach / show / evict</i>"]
    end

    subgraph INTERACT["Interaction System"]
        CODEC["Interaction Codec<br><i>Registered per plugin</i>"]
        ITEM["Item with Interaction"]
        HANDLER["playerHandler<br>.handleInteraction()"]
    end

    PAGE_CLS --> CMD
    PAGE_CLS --> EVT
    CMD -->|"Loads"| UI_FILE
    EVT -->|"On click"| PAGE_CLS

    HUD_CLS -->|"Managed by"| MHB_2
    HUD_CLS -->|"updateProperty()"| CLIENT[Client Display]

    ITEM -->|"Use item"| HANDLER
    HANDLER -->|"Dispatch via"| CODEC
    CODEC -->|"Opens"| PAGE_CLS
```

---

## 11. Documentation Map

```mermaid
graph TD
    subgraph ENTRY["Entry Points"]
        CLAUDE["CLAUDE.md<br><i>Agent workflow & rules</i>"]
        README["README.md<br><i>Project overview</i>"]
        DINDEX["docs/README.md<br><i>Source-of-truth index</i>"]
    end

    subgraph REFERENCE["Reference Docs"]
        ARCH["ARCHITECTURE.md"]
        DB["DATABASE.md"]
        CP["CODE_PATTERNS.md"]
        HAPI["HYTALE_API.md"]
        DIAG["ARCHITECTURE_DIAGRAMS.md<br><i>(this file)</i>"]
    end

    subgraph MODULES["Module READMEs"]
        M_CORE["Core/README.md"]
        M_PARK["Parkour/README.md"]
        M_ASC["Ascend/README.md"]
        M_HUB["Hub/README.md"]
        M_PURGE["Purge/README.md"]
        M_ROF["RunOrFall/README.md"]
        M_WARD["Wardrobe/README.md"]
        M_VOTE["Votifier/README.md"]
        M_DC["DiscordBot/README.md"]
    end

    subgraph VOLATILE["High-Volatility Docs"]
        ECON["Ascend/ECONOMY_BALANCE.md"]
        TUT["Ascend/TUTORIAL_FLOW.md"]
        CHANGE["CHANGELOG.md"]
        PATCH["PLAYER_PATCH_NOTES.md"]
    end

    subgraph UIREF["Hytale Custom UI<br>(120+ files)"]
        UI_IDX["hytale-custom-ui/index.mdx"]
        UI_EL["Elements, Enums,<br>Property Types"]
    end

    CLAUDE -->|"Points to"| DINDEX
    DINDEX -->|"Maps topics to"| REFERENCE
    DINDEX -->|"Maps modules to"| MODULES
    DINDEX -->|"Tracks volatility"| VOLATILE

    style CLAUDE fill:#e74c3c,color:#fff
    style DINDEX fill:#4a9eff,color:#fff
    style VOLATILE fill:#f39c12,color:#fff
```

> **Agent reading order**: `CLAUDE.md` → `docs/README.md` (index) → relevant reference doc for the task at hand.
