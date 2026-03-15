# Mine Mode - Status Check

Audit du mode Mine au 2026-03-15.

## Ce qui est implémenté

### Core
- **MineManager** - Tracking des blocs cassés par zone, ratio de destruction, cooldown de regen, regeneration (remplissage aléatoire pondéré)
- **MineConfigStore** - CRUD complet mines/zones/gates/prix en MySQL, cache mémoire avec locks
- **MinePlayerStore** - Cache session joueur, dirty tracking avec auto-save 5s debounce, persistance multi-table
- **MinePlayerProgress** - Cristaux, upgrades (4 types), inventaire, états par mine, états par miner

### Data Models
- **Mine** - id, name, displayOrder, unlockCost (BigNumber), spawn coords/rotation, zones
- **MineZone** - AABB bounds, blockTable (poids par type de bloc), regenThreshold (0.8), regenCooldownSeconds (45)
- **MineUpgradeType** - 4 upgrades avec courbes de coût hardcodées :
  - Mining Speed (max 100) : `10 x 1.15^level`, +10% vitesse/level
  - Bag Capacity (max 50) : `25 x 1.2^level`, +10 slots/level (base 50)
  - Multi-Break (max 20) : `100 x 1.5^level`, +5% chance/level
  - Auto-Sell (max 1) : 500 cristaux, vente auto à la casse

### Gameplay Systems
- **MineBreakSystem** - Handler BreakBlockEvent : validation joueur, zone, cooldown, multi-break RNG, stockage ou auto-sell
- **MineDamageSystem** - Handler DamageBlockEvent : applique le multiplicateur mining speed du joueur
- **MineGateChecker** - Détection entry/exit gates par tick, téléportation, swap HUD, cooldown 2s, gate ascension (ascensionCount >= 1)
- **MineBonusCalculator** - Bonus passifs cross-progression vers le parkour :
  - Runner Speed : +5% (mine 2 unlock), +10% (mining speed maxé)
  - Multiplier Gain : +10% (mine 3 unlock), +20% (tous les miners achetés)
  - Volt Gain : +15% (mine 4 unlock)

### Miners (robots automatisés)
- **MineRobotManager** - Spawn/despawn NPCs par joueur par mine, tick production 50ms, cleanup orphelins
- **MinerRobotState** - Production rate : `6.0 x (1 + speedLevel*0.10) x (1 + stars*0.5)` blocs/min
- Progression miner : Achat (1000) -> Speed Lv 1-25 -> Evolve Star 2-5
- Coûts : Speed = `50 x 1.15^totalLevel`, Evolve = `5000 x 3^stars`
- Visuels par stars : Kweebec_Seedling -> Kweebec_Rootling (etc.)

### UI Pages (joueur)
- **MineSelectPage** - Liste des mines, unlock/teleport
- **MineSellPage** - Vente de tout l'inventaire pour cristaux
- **MineUpgradePage** - Achat upgrades globaux + miners par mine
- **MineBagPage** (via `/mine`) - Affichage inventaire
- **MineHudManager** - HUD in-game : cristaux, inventaire, cooldowns zones
- **MineToastManager** - Notifications flottantes 4 slots avec fade-out

### UI Pages (admin)
- **MineAdminPage** - CRUD mines, set spawn, order, cost
- **MineZoneAdminPage** - Config zones (AABB, block table, regen threshold/cooldown, regen manuelle)
- **MineGateAdminPage** - Config entry/exit gates (AABB + destination)
- **MineBlockPricesPage** - Prix par bloc par mine (mantissa + exponent)

### .ui Files (17 fichiers)
- Player : MineSelectPage, MineSelectEntry, Ascend_MineSell, Ascend_MineSellEntry, Ascend_MineUpgrade, Ascend_MineUpgradeEntry, Ascend_MineHud, Ascend_MineBag, Ascend_MineBagEntry
- Admin : Ascend_MineAdmin, Ascend_MineAdminEntry, Ascend_MineZoneAdmin, Ascend_MineZoneEntry, Ascend_MineBlockEntry, Ascend_MineGateAdmin, Ascend_MineBlockPrices, Ascend_MineBlockPricesEntry

### Commandes
- `/mine` - Ouvre le bag
- `/mine sell` / `upgrades` / `select` - Sous-commandes joueur
- `/mine addcrystals <amount>` - OP only

### Database (8 tables)
| Table | Rôle |
|-------|------|
| `mine_definitions` | Config mines (id, name, order, cost, spawn) |
| `mine_zones` | Zones minables (AABB, block table, regen config) |
| `mine_gate` | Entry/exit gates (AABB + destination) |
| `mine_block_prices` | Prix par bloc par mine |
| `mine_players` | Progression joueur (cristaux, niveaux upgrades) |
| `mine_player_inventory` | Inventaire blocs minés |
| `mine_player_mines` | État unlock/completion par mine |
| `mine_player_miners` | État miners par mine (has_miner, speed, stars) |

### Documentation existante
- `docs/Ascend/ECONOMY_BALANCE.md` - Section "Mine Economy" complète
- `docs/Ascend/README.md` - Section Mine avec managers, stores, init order
- `docs/DATABASE.md` - Schéma des 8 tables

## Ce qui manque potentiellement pour la production

> A confirmer par le propriétaire du projet - basé uniquement sur l'audit du code.

- **Pas de tutoriel/onboarding mine** - Aucune intégration avec le système de tutoriel existant (`TUTORIAL_FLOW.md`)
- **Pas de leaderboard mine** - Pas de classement cristaux ou progression
- **Pas de sons/feedback** - Aucun son custom pour minage, vente, achat upgrade, evolution miner
- **Pas de particules/VFX** - Pas d'effets visuels pour le minage ou la regen de zone
- **MineBagPage** référencée dans MineCommand mais pas trouvée dans `mine/ui/` (peut être ailleurs ou manquante)
- **Blocs hardcodés dans ZoneAdmin** - 7 types de blocs prédéfinis (Stone + 6 Crystals), extensible via admin mais limité
- **Balance non testée** - Les valeurs d'économie sont marquées "provisional" dans ECONOMY_BALANCE.md
- **Pas de prestige/reset** - Pas de système de reset de progression mine
- **Pas de statistiques** - Pas de tracking blocs minés total, cristaux gagnés, temps passé
- **Miner visuels** - Seuls 2 types NPC mentionnés (Seedling, Rootling), pas clair si les 5 tiers d'étoiles ont des visuels distincts
