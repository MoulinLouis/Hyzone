# Summit System Rebalance Design

Date: 2026-02-05

## Overview

Refonte complète du système Summit (prestige tier 2) pour améliorer la clarté et l'engagement joueur.

### Problèmes actuels
- **Coin Flow** : mécanique peu intéressante (simple multiplicateur de coins)
- **Evolution Power** : concept trop abstrait, joueurs ne comprennent pas l'effet
- **Plafond niveau 10** : limite artificielle sur la progression

### Objectifs
- Mécaniques plus intuitives avec affichage visuel clair
- Progression infinie sans plafond
- Système d'XP pour visualiser la progression

---

## Nouvelles catégories Summit

### 1. Runner Speed
**Effet** : Multiplicateur sur la vitesse de complétion des runners.

**Formule** : `1 + 0.45 × √niveau`

| Niveau | Multiplicateur |
|--------|---------------|
| 0 | ×1.00 |
| 1 | ×1.45 |
| 2 | ×1.64 |
| 3 | ×1.78 |
| 4 | ×1.90 |
| 5 | ×2.01 |
| 10 | ×2.42 |
| 20 | ×3.01 |
| 50 | ×4.18 |
| 100 | ×5.50 |

**Caractéristique** : Diminishing returns (√) - les premiers niveaux ont le plus d'impact.

---

### 2. Multiplier Gain
**Effet** : Multiplicateur sur les gains de multiplicateur par run des runners.

**Formule** : `1 + 5 × niveau^0.9`

| Niveau | Multiplicateur |
|--------|---------------|
| 0 | ×1 |
| 1 | ×6 |
| 2 | ×10.3 |
| 3 | ×14.4 |
| 5 | ×22.3 |
| 10 | ×40.7 |
| 20 | ×75.3 |
| 50 | ×166.5 |
| 100 | ×316.5 |

**Caractéristique** : Diminishing returns (^0.9) - progression forte au début, puis ralentit.

---

### 3. Evolution Power
**Effet** : Multiplicateur appliqué au multiplicateur de la map lors d'une évolution de runner.

**Formule** : `10 + 0.5 × niveau^0.8`

| Niveau | Multiplicateur |
|--------|---------------|
| 0 | ×10.00 |
| 1 | ×10.50 |
| 2 | ×10.87 |
| 3 | ×11.20 |
| 4 | ×11.51 |
| 5 | ×11.80 |
| 10 | ×13.16 |
| 20 | ×15.50 |
| 50 | ×21.20 |
| 100 | ×29.91 |

**Caractéristique** : Diminishing returns (^0.8) - progression stable sans explosion.

---

## Changement du système d'évolution

### Avant
Évoluer un runner doublait le gain de multiplicateur par run :
- 0★ : +0.1/run
- 1★ : +0.2/run
- 2★ : +0.4/run
- etc.

### Nouveau
Évoluer un runner applique un **multiplicateur instantané** sur le multiplicateur de la map :
- Base : ×10
- Avec Evolution Power : ×(10 + 0.5 × niveau^0.8)

**Exemple** : Un runner sur une map avec multiplicateur ×5, évolué avec Evolution Power niveau 5 (×11.8) → nouveau multiplicateur = ×59.

---

## Système d'XP Summit

### Conversion coins → XP
**Ratio** : 1000 coins = 1 XP

### Seuils par niveau
**Formule** : `100 × niveau^1.5` XP pour atteindre le niveau suivant

| Niveau | XP requis | XP cumulé | Coins équivalents |
|--------|-----------|-----------|-------------------|
| 1 | 100 | 100 | 100K |
| 2 | 283 | 383 | 383K |
| 3 | 520 | 903 | 903K |
| 4 | 800 | 1,703 | 1.7M |
| 5 | 1,118 | 2,821 | 2.8M |
| 10 | 3,162 | 13,246 | 13.2M |
| 20 | 8,944 | 62,946 | 62.9M |
| 50 | 35,355 | 474,341 | 474M |

### Pool d'XP
- **Commun** aux 3 catégories
- Chaque catégorie track son propre niveau et XP accumulé

### Investissement
- Lors d'un Summit, le joueur choisit **UNE seule catégorie**
- **Tout l'XP** va dans cette catégorie (pas de répartition)
- Pas de stockage d'XP entre les Summits

---

## Flow utilisateur

1. Joueur accumule des coins en jouant
2. Ouvre le menu Summit
3. Voit l'XP potentiel : `+{coins / 1000} XP`
4. Pour chaque catégorie, voit :
   - Niveau actuel et multiplicateur : `[Lv. X] Actuel: ×M.MM`
   - Niveau potentiel après Summit : `[Lv. Y] Next: ×N.NN`
   - Barre de progression : `████░░░░ Exp A/B (+C)`
5. Choisit une catégorie et confirme
6. XP investi, peut monter plusieurs niveaux d'un coup
7. Coins et Elevation remis à 0

---

## UI Mockup

```
╔══════════════════════════════════════════════════════════╗
║                      S U M M I T                         ║
║                                                          ║
║  Coins: 5,000,000  →  +5000 XP                          ║
║                                                          ║
║  ┌─────────────────────────────────────────────────────┐ ║
║  │ RUNNER SPEED                              [SUMMIT]  │ ║
║  │ [Lv. 3] Actuel: ×1.87  →  [Lv. 5] Next: ×2.12      │ ║
║  │ ████████████░░░░░░░░ Exp 720/1118 (+5000)          │ ║
║  └─────────────────────────────────────────────────────┘ ║
║                                                          ║
║  ┌─────────────────────────────────────────────────────┐ ║
║  │ MULTIPLIER GAIN                           [SUMMIT]  │ ║
║  │ [Lv. 2] Actuel: ×4  →  [Lv. 5] Next: ×12           │ ║
║  │ ██████░░░░░░░░░░░░░░ Exp 283/520 (+5000)           │ ║
║  └─────────────────────────────────────────────────────┘ ║
║                                                          ║
║  ┌─────────────────────────────────────────────────────┐ ║
║  │ EVOLUTION POWER                           [SUMMIT]  │ ║
║  │ [Lv. 0] Actuel: ×10  →  [Lv. 4] Next: ×11.51       │ ║
║  │ ░░░░░░░░░░░░░░░░░░░░ Exp 0/100 (+5000)             │ ║
║  └─────────────────────────────────────────────────────┘ ║
║                                                          ║
║  ⚠️ Summit resets: Coins + Elevation                     ║
╚══════════════════════════════════════════════════════════╝
```

---

## Resets au Summit

| Ressource | Reset ? |
|-----------|---------|
| Coins | ✓ Oui |
| Elevation level | ✓ Oui |
| Map multipliers | Non |
| Runner levels/stars | Non |
| Summit XP/levels | Non |
| Unlocked maps | Non |

---

## Notes d'équilibrage

- Les valeurs de conversion (1000:1) et seuils (100 × n^1.5) sont des points de départ
- À ajuster après tests en jeu selon :
  - Temps moyen entre Summits
  - Progression des niveaux Summit
  - Impact sur l'économie globale

---

## Implémentation

### Fichiers à modifier
- `SummitManager.java` - Logique des catégories et calculs
- `SummitPage.java` - UI et affichage
- `Ascend_Summit.ui` - Layout UI
- `AscendPlayerProgress.java` - Stockage XP par catégorie
- `AscendConstants.java` - Formules et constantes
- `RobotManager.java` - Intégration Evolution Power sur évolution

### Base de données
- Table `ascend_player_summit` : ajouter colonnes XP par catégorie
- Migration pour convertir les anciens niveaux en XP équivalent

### Tests prioritaires
1. Formules retournent les bonnes valeurs
2. Niveau up automatique quand XP atteint le seuil
3. Reset correct des coins/elevation
4. Affichage UI correct du +XP potentiel
