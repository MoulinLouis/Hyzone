# Mine Mode - Status Check

Audit du mode Mine au 2026-03-20.

## Ce qui est implémenté

### Core
- **MineManager** - Tracking des blocs cassés par zone, ratio de destruction, cooldown de regen, regeneration (remplissage aléatoire pondéré)
- **MineConfigStore** - CRUD mine/zones/gate/prix en MySQL, cache mémoire avec locks, single-mine convenience methods (getMine(), getZone(), getMineId(), getMinerSlots())
- **MinePlayerStore** - Cache session joueur, dirty tracking avec auto-save 5s debounce, persistance multi-table
- **MinePlayerProgress** - Cristaux, upgrades (7 types), inventaire, slot-based miner progression

### Data Models
- **Mine** - id, name, displayOrder, unlockCost (BigNumber), spawn coords/rotation, zones
- **MineZone** - AABB bounds, blockTable (poids par type de bloc), regenThreshold (0.8), regenCooldownSeconds (45), depth layers (Y-range + block distribution)
- **MineUpgradeType** - 7 upgrades avec courbes de coût hardcodées :
  - Bag Capacity (max 50) : `25 x 1.2^level`, +10 slots/level (base 50)
  - Momentum (max 25) : `50 x 1.22^level`, combo count for more damage
  - Fortune (max 25) : `60 x 1.22^level`, double drop chance %
  - Jackhammer (max 10) : `150 x 1.28^level`, breaks column of blocks below
  - Stomp (max 15) : `200 x 1.30^level`, breaks layer of blocks around feet
  - Blast (max 15) : `250 x 1.30^level`, breaks blocks in sphere around target
  - Haste (max 20) : `40 x 1.20^level`, +5% mining speed/level

### Gameplay Systems
- **MineBreakSystem** - Handler BreakBlockEvent : validation joueur, zone, cooldown, stockage inventaire
- **MineDamageSystem** - Handler DamageBlockEvent : applique le multiplicateur mining speed du joueur
- **MineAoEBreaker** - Jackhammer, Stomp, Blast AoE proc system avec chances linéaires
- **MineRewardHelper** - Block reward calculation, fortune procs, momentum combo tracking
- **MineGateChecker** - Détection entry/exit gate par tick, téléportation, swap HUD, cooldown 2s, gate ascension (ascensionCount >= 1)
- **MineBonusCalculator** - Bonus passifs cross-progression vers le parkour (simplifié single-mine) :
  - Multiplier Gain : +20% (all miner slots have miners)
  - Runner Speed et Volt Gain : toujours 1.0 (pas de multi-mine unlock)

### Miners (robots automatisés)
- **MineRobotManager** - Spawn/despawn NPCs par joueur par slot, tick production 50ms, cleanup orphelins, flat player->slot map (single mine)
- **MinerRobotState** - Production rate : `6.0 x (1 + speedLevel*0.10) x (1 + stars*0.5)` blocs/min
- Progression miner : Achat -> Speed Lv 1-25 -> Evolve Star 2-5
- Coûts : Speed = `50 x 1.15^totalLevel`, Evolve = `5000 x 3^stars`
- Visuels par stars : Kweebec_Seedling -> Kweebec_Sapling -> Kweebec_Sproutling -> Kweebec_Sapling_Pink -> Kweebec_Razorleaf -> Kweebec_Rootling

### UI Pages (joueur)
- **MineSellPage** - Vente de tout l'inventaire pour cristaux
- **MinePage** - Tabbed page: upgrades globaux (tab 1) + miners par slot (tab 2)
- **MineBagPage** (via `/mine`) - Affichage inventaire
- **MineAchievementsPage** - Mine-specific achievement tracking
- **MineHudManager** - HUD in-game : cristaux, inventaire, cooldowns zones
- **MineToastManager** - Notifications flottantes 4 slots avec fade-out

### UI Pages (admin)
- **MineAdminPage** - Config mine, set spawn, order, cost
- **MineZoneAdminPage** - Config zones (AABB, block table, regen threshold/cooldown, depth layers, regen manuelle)
- **MineGateAdminPage** - Config entry/exit gate (AABB + destination)
- **MineBlockPickerPage** - Global block prices and HP configuration

### Commandes
- `/mine` - Ouvre le bag
- `/mine sell` / `upgrades` / `achievements` - Sous-commandes joueur
- `/mine addcrystals <amount>` - OP only

### Database (15 tables)
| Table | Rôle |
|-------|------|
| `mine_definitions` | Config mine (id, name, order, cost, spawn) |
| `mine_zones` | Zones minables (AABB, block table, regen config) |
| `mine_zone_layers` | Depth layers per zone (Y-range + block distribution) |
| `mine_gate` | Single entry/exit gate (AABB + destination, id=1) |
| `mine_players` | Progression joueur (cristaux) |
| `mine_player_inventory` | Inventaire blocs minés |
| `block_prices` | Prix global par bloc (simple BIGINT) |
| `block_hp` | HP global par bloc |
| `mine_player_mines` | État unlock/completion par mine (legacy, not loaded in single-mine) |
| `mine_player_miners` | État miners par mine (has_miner, speed, stars) |
| `mine_miner_slots` | Configured miner slots per mine |
| `mine_achievements` | Mine achievement tracking per player |
| `mine_player_stats` | Lifetime counters (total_blocks_mined, total_crystals_earned) |
| `mine_conveyor_waypoints` | Conveyor belt waypoint definitions |
| `mine_player_conveyor_buffer` | Per-player conveyor buffer state |

### Documentation existante
- `docs/Ascend/ECONOMY_BALANCE.md` - Section "Mine Economy"
- `docs/Ascend/README.md` - Section Mine avec managers, stores, init order
- `docs/DATABASE.md` - Schéma des tables

## Ce qui manque potentiellement pour la production

> A confirmer par le propriétaire du projet - basé uniquement sur l'audit du code.

- **Pas de tutoriel/onboarding mine** - Aucune intégration avec le système de tutoriel existant (`TUTORIAL_FLOW.md`)
- **Pas de leaderboard mine** - Pas de classement cristaux ou progression
- **Pas de sons/feedback** - Aucun son custom pour minage, vente, achat upgrade, evolution miner
- **Pas de particules/VFX** - Pas d'effets visuels pour le minage ou la regen de zone
- **Balance non testée** - Les valeurs d'économie sont marquées "provisional" dans ECONOMY_BALANCE.md
- **Pas de prestige/reset** - Pas de système de reset de progression mine
