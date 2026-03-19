# Mine Balance Roadmap - v1 Draft

> **Statut :** proposition initiale à retravailler — tous les chiffres sont des points de départ raisonnés, pas des valeurs finales.
>
> **Objectif :** poser une base cohérente pour les 5 premières mines, avec un arc de progression de ~8-12h de jeu pur (sans compter les upgrades qui accélèrent).

---

## Table des matières

1. [Philosophie de design](#1-philosophie-de-design)
2. [Vue d'ensemble de la progression](#2-vue-densemble-de-la-progression)
3. [Définition des 5 mines](#3-définition-des-5-mines)
4. [Catalogue complet des blocs](#4-catalogue-complet-des-blocs)
5. [Distribution des blocs par mine et couche](#5-distribution-des-blocs-par-mine-et-couche)
6. [Projections de revenus](#6-projections-de-revenus)
7. [Progression pioche](#7-progression-pioche)
8. [Analyse des upgrades](#8-analyse-des-upgrades)
9. [Économie des miners](#9-économie-des-miners)
10. [Timeline de progression](#10-timeline-de-progression)
11. [Bonus cross-progression](#11-bonus-cross-progression)
12. [Leviers d'ajustement](#12-leviers-dajustement)

---

## 1. Philosophie de design

### Principes directeurs

1. **Le mining est un loop secondaire** — il alimente le parkour via les bonus cross-progression (+5% speed, +10% mult gain, etc.). Il ne doit pas éclipser le parkour, mais offrir une activité complémentaire avec sa propre satisfaction.

2. **Progression par paliers lisibles** — chaque mine = un palier clair. Le joueur doit sentir "j'ai atteint la mine suivante, les blocs sont nouveaux, plus jolis, plus chers, plus résistants". Pas de progression continue indifférenciée.

3. **Le ratio prix/HP est la métrique-clé** — c'est le "crystals per hit" qui détermine le sentiment de rentabilité. Ce ratio doit augmenter entre les mines (la nouvelle mine est toujours plus rentable) mais rester relativement stable au sein d'une même mine (pas de bloc-piège qui gaspille du temps).

4. **Les blocs rares excitent** — au sein d'une mine, les blocs rares doivent avoir un ratio prix/HP ~1.5-2× supérieur aux fillers. Trouver un minerai rare doit provoquer un petit rush de dopamine.

5. **Les upgrades accélèrent, ils ne sont pas obligatoires** — un joueur qui ne fait que miner-vendre sans upgrade doit pouvoir progresser, juste plus lentement. Les upgrades récompensent l'investissement stratégique.

### Hypothèses de modélisation

| Paramètre | Valeur utilisée | Justification |
|-----------|----------------|---------------|
| Fréquence de frappe base | 2 hits/sec | Rythme naturel de clic en jeu d'action |
| Effet pioche | Multiplie la fréquence de frappe | Wood ×1.0, Stone ×1.5, Iron ×2.0, etc. |
| Dégâts par coup | 1 (base) + momentum | Momentum = 1 + comboCount × 0.02 |
| Efficacité de mouvement | 70% | Temps passé à se déplacer, viser, etc. |
| Auto-sell overflow | Oui | Le sac plein auto-vend le surplus au prix courant |

---

## 2. Vue d'ensemble de la progression

```
Mine 1 (Carrière)          Mine 2 (Cristaux)         Mine 3 (Volcanique)
  Stone Pick ──────────> Iron Pick ──────────────> Crystal Pick
  ~140 cryst/min          ~350 cryst/min              ~970 cryst/min
  HP: 1-2                 HP: 2-4                     HP: 3-5
  Prix: 1-6               Prix: 3-16                  Prix: 8-25

                          Mine 4 (Abysses)           Mine 5 (Void)
                       Void Pick ──────────────> Prismatic Pick
                          ~1,700 cryst/min           ~2,800 cryst/min
                          HP: 4-8                    HP: 5-12
                          Prix: 15-60                Prix: 25-100
```

### Arc de progression cible

| Phase | Durée cible | Objectif principal | Feeling visé |
|-------|------------|-------------------|-------------|
| Découverte | 0-5 min | Apprendre mine→sell→upgrade | "C'est simple et satisfaisant" |
| Première montée | 5-15 min | Stone Pick + Mine 2 | "Je progresse vite, ça vaut le coup" |
| Mid-game | 15-60 min | Iron Pick → Crystal Pick | "De nouveaux blocs, de nouveaux upgrades" |
| Late-game | 60-180 min | Mine 4 + Void Pick | "Investment stratégique, miners actifs" |
| Endgame | 180+ min | Mine 5 + Prismatic + max upgrades | "Optimisation, grind satisfaisant" |

---

## 3. Définition des 5 mines

### Mine 1 — Carrière de Surface

| Propriété | Valeur |
|-----------|--------|
| **ID** | `mine_quarry` |
| **Nom** | Surface Quarry |
| **Coût d'unlock** | 0 (gratuite, point de départ) |
| **Thème** | Roches sédimentaires communes, terrain ouvert et clair |
| **Blocs uniques** | Stone, Mossy Stone, Sandstone, Chalk, Copper Ore |
| **HP typique** | 1-2 |
| **Prix moyen pondéré** | ~1.8 crystals/bloc |
| **Zone recommandée** | ~20×15×20 (6,000 blocs) |
| **Regen threshold** | 0.80 (80%) |
| **Regen cooldown** | 30 sec (plus court pour le débutant) |
| **Identité gameplay** | Mining rapide, satisfaction immédiate, pas de friction |

**Design intent :** Le joueur apprend les mécaniques. Blocs en 1 HP = instant break = feedback immédiat. Le Copper Ore (2 HP, rare) introduit le concept de blocs multi-hit. La transition vers Mine 2 est visible quand le joueur comprend qu'il a besoin de plus de cristaux.

---

### Mine 2 — Cavernes Cristallines

| Propriété | Valeur |
|-----------|--------|
| **ID** | `mine_crystal` |
| **Nom** | Crystal Caverns |
| **Coût d'unlock** | 1,500 crystals |
| **Thème** | Grottes profondes avec formations cristallines, lumière bleue/verte |
| **Blocs uniques** | Shale, Slate, Quartzite, Blue/Green/White Crystal, Iron Ore |
| **HP typique** | 2-4 |
| **Prix moyen pondéré** | ~6.7 crystals/bloc |
| **Zone recommandée** | ~25×20×25 (12,500 blocs) |
| **Regen threshold** | 0.80 |
| **Regen cooldown** | 40 sec |
| **Identité gameplay** | Premier "vrai" mining — les cristaux brillent, les ores récompensent |

**Design intent :** C'est ici que le système de couches (layers) entre en jeu. La surface de la mine contient surtout du Shale/Slate, mais en creusant profondément, le joueur découvre des veines de cristaux. Ça enseigne "aller plus profond = meilleur loot".

**Moment d'unlock :** Avec la Stone Pick en Mine 1 (~209 cryst/min), le joueur met ~7 minutes à accumuler 1,500 cristaux. Total ~12 min depuis le début. C'est le bon moment — le joueur commence à s'ennuyer de Mine 1.

---

### Mine 3 — Forge Volcanique

| Propriété | Valeur |
|-----------|--------|
| **ID** | `mine_volcanic` |
| **Nom** | Volcanic Forge |
| **Coût d'unlock** | 10,000 crystals |
| **Thème** | Caverne volcanique, basalte sombre, cristaux rouges/roses incandescents |
| **Blocs uniques** | Basalt, Volcanic, Red/Pink Crystal, Gold Ore, Cobalt Ore |
| **HP typique** | 3-5 |
| **Prix moyen pondéré** | ~14.6 crystals/bloc |
| **Zone recommandée** | ~25×25×25 (15,625 blocs) |
| **Regen threshold** | 0.80 |
| **Regen cooldown** | 45 sec |
| **Identité gameplay** | Mining exigeant, AoE upgrades commencent à briller |

**Design intent :** Le tournant mid-game. Les blocs à 5 HP (Gold, Cobalt) demandent un vrai investissement en momentum et en upgrades. C'est ici que Jackhammer et Blast deviennent attractifs. Les cristaux rouges/roses introduisent une nouvelle catégorie visuelle.

**Moment d'unlock :** Avec Iron Pick en Mine 2 (~462 cryst/min), il faut ~22 min pour 10,000. Total ~55 min. Le joueur a eu le temps d'explorer Mine 2, d'acheter quelques upgrades, et ressent le besoin de nouveauté.

---

### Mine 4 — Abysses Gelées

| Propriété | Valeur |
|-----------|--------|
| **ID** | `mine_abyss` |
| **Nom** | Frozen Abyss |
| **Coût d'unlock** | 50,000 crystals |
| **Thème** | Profondeurs glacées, marbre blanc, calcite, tons bleu-aqua |
| **Blocs uniques** | Marble, Calcite, Aqua, Yellow Crystal, Silver Ore, Mithril Ore |
| **HP typique** | 4-8 |
| **Prix moyen pondéré** | ~30.9 crystals/bloc |
| **Zone recommandée** | ~28×25×28 (19,600 blocs) |
| **Regen threshold** | 0.80 |
| **Regen cooldown** | 50 sec |
| **Identité gameplay** | Premium mining, Mithril = jackpot, miners rentables ici |

**Design intent :** Le late-game. Mithril Ore (8 HP, 60 crystals) est le premier bloc qui donne un vrai sentiment de "jackpot". Silver Ore est un bon intermédiaire. Yellow Crystal est la dernière couleur de cristal, rare et précieuse. C'est à ce stade que les miners deviennent intéressants — le revenu passif de Mine 4 compense l'investissement.

**Moment d'unlock :** Avec Crystal Pick en Mine 3 (~971 cryst/min), il faut ~52 min. Total ~107 min. C'est un gros palier, mais les upgrades accumulées (Fortune, Haste) réduisent ce temps en pratique.

---

### Mine 5 — Cœur du Void

| Propriété | Valeur |
|-----------|--------|
| **ID** | `mine_void` |
| **Nom** | Void Core |
| **Coût d'unlock** | 200,000 crystals |
| **Thème** | Dimension alternative, bedrock sombre, cristaux de sel, thorium lumineux |
| **Blocs uniques** | Bedrock, Salt, Thorium Ore + mix de blocs des mines précédentes |
| **HP typique** | 5-12 |
| **Prix moyen pondéré** | ~41.6 crystals/bloc |
| **Zone recommandée** | ~30×30×30 (27,000 blocs) |
| **Regen threshold** | 0.80 |
| **Regen cooldown** | 55 sec |
| **Identité gameplay** | Endgame, Thorium = ultra-rare jackpot, mine ultime |

**Design intent :** La mine finale. Thorium Ore (12 HP, 100 crystals) est le bloc le plus précieux du jeu. Le mix de blocs des mines précédentes (Gold, Mithril, cristaux rouges/roses/jaunes) fait que chaque hit peut révéler quelque chose d'intéressant. C'est la mine de "farm" endgame — le joueur y reste longtemps.

**Moment d'unlock :** Avec Void Pick en Mine 4 (~1,882 cryst/min), il faut ~106 min. Total ~213 min. Avec upgrades (estimation ×2), ~107 min. C'est le dernier gros investissement.

---

## 4. Catalogue complet des blocs

### Légende

- **HP** : nombre de coups (base) pour casser le bloc
- **Prix** : cristaux reçus par bloc vendu
- **Ratio** : prix ÷ HP = cristaux par coup investi (mesure d'efficacité)
- **Tier** : mine où le bloc apparaît pour la première fois
- **Rôle** : Filler (commun, >25%), Standard (15-25%), Uncommon (5-15%), Rare (<8%), Jackpot (<3%)

### Roches

| Block Type ID | Nom | HP | Prix | Ratio | Tier | Rôle |
|--------------|-----|-----|------|-------|------|------|
| `Rock_Stone` | Stone | 1 | 1 | 1.00 | Mine 1 | Filler |
| `Rock_Stone_Mossy` | Mossy Stone | 1 | 2 | 2.00 | Mine 1 | Standard |
| `Rock_Sandstone` | Sandstone | 1 | 2 | 2.00 | Mine 1 | Standard |
| `Rock_Chalk` | Chalk | 1 | 3 | 3.00 | Mine 1 | Uncommon |
| `Rock_Shale` | Shale | 2 | 4 | 2.00 | Mine 2 | Filler |
| `Rock_Slate` | Slate | 2 | 5 | 2.50 | Mine 2 | Standard |
| `Rock_Quartzite` | Quartzite | 3 | 7 | 2.33 | Mine 2 | Uncommon |
| `Rock_Basalt` | Basalt | 3 | 9 | 3.00 | Mine 3 | Filler |
| `Rock_Volcanic` | Volcanic | 4 | 14 | 3.50 | Mine 3 | Standard |
| `Rock_Marble` | Marble | 5 | 22 | 4.40 | Mine 4 | Filler |
| `Rock_Calcite` | Calcite | 5 | 25 | 5.00 | Mine 4 | Standard |
| `Rock_Aqua` | Aqua | 6 | 35 | 5.83 | Mine 4 | Uncommon |
| `Rock_Bedrock` | Bedrock | 8 | 40 | 5.00 | Mine 5 | Filler |
| `Rock_Salt` | Salt | 6 | 35 | 5.83 | Mine 5 | Standard |
| `Rock_Sandstone_Red` | Red Sandstone | 1 | 2 | 2.00 | Mine 1 | (variante Sandstone) |
| `Rock_Sandstone_White` | White Sandstone | 2 | 4 | 2.00 | Mine 2 | (variante, si utilisé) |

### Cristaux

| Block Type ID | Nom | HP | Prix | Ratio | Tier | Rôle |
|--------------|-----|-----|------|-------|------|------|
| `Rock_Crystal_Blue_Block` | Blue Crystal | 3 | 12 | 4.00 | Mine 2 | Rare |
| `Rock_Crystal_Green_Block` | Green Crystal | 3 | 12 | 4.00 | Mine 2 | Rare |
| `Rock_Crystal_White_Block` | White Crystal | 4 | 16 | 4.00 | Mine 2 | Rare |
| `Rock_Crystal_Red_Block` | Red Crystal | 4 | 20 | 5.00 | Mine 3 | Rare |
| `Rock_Crystal_Pink_Block` | Pink Crystal | 4 | 20 | 5.00 | Mine 3 | Rare |
| `Rock_Crystal_Yellow_Block` | Yellow Crystal | 7 | 50 | 7.14 | Mine 4 | Rare |

**Pattern cristaux :** le ratio augmente avec la rareté. Blue/Green (×4.0) → Red/Pink (×5.0) → Yellow (×7.14). Yellow Crystal est le cristal le plus précieux, réservé à Mine 4+.

### Minerais

| Block Type ID | Nom | HP | Prix | Ratio | Tier | Rôle |
|--------------|-----|-----|------|-------|------|------|
| `Ore_Copper_Stone` | Copper Stone | 2 | 6 | 3.00 | Mine 1 | Rare |
| `Ore_Copper_Sandstone` | Copper Sandstone | 2 | 6 | 3.00 | Mine 1 | (variante) |
| `Ore_Iron_Shale` | Iron Shale | 3 | 10 | 3.33 | Mine 2 | Uncommon |
| `Ore_Iron_Slate` | Iron Slate | 3 | 10 | 3.33 | Mine 2 | (variante) |
| `Ore_Gold_Basalt` | Gold Basalt | 5 | 25 | 5.00 | Mine 3 | Uncommon |
| `Ore_Gold_Volcanic` | Gold Volcanic | 5 | 25 | 5.00 | Mine 3 | (variante) |
| `Ore_Cobalt_Shale` | Cobalt Shale | 5 | 22 | 4.40 | Mine 3 | Uncommon |
| `Ore_Cobalt_Slate` | Cobalt Slate | 5 | 22 | 4.40 | Mine 3 | (variante) |
| `Ore_Silver_Basalt` | Silver Basalt | 7 | 45 | 6.43 | Mine 4 | Uncommon |
| `Ore_Silver_Slate` | Silver Slate | 7 | 45 | 6.43 | Mine 4 | (variante) |
| `Ore_Mithril_Stone` | Mithril Stone | 8 | 60 | 7.50 | Mine 4 | Rare |
| `Ore_Thorium_Sandstone` | Thorium Sandstone | 12 | 100 | 8.33 | Mine 5 | Jackpot |

**Pattern minerais :** chaque mine a 1-2 minerais thématiques. Le ratio progresse : Copper (3.0) → Iron (3.33) → Gold/Cobalt (4.4-5.0) → Silver (6.43) → Mithril (7.50) → Thorium (8.33). L'augmentation est lente mais constante, reflétant la vraie progression.

### Résumé ratio par tier

| Tier | Ratio filler | Ratio rare | Ratio jackpot | Gain vs. tier précédent |
|------|-------------|-----------|--------------|------------------------|
| Mine 1 | 1.0-2.0 | 3.0 | — | (base) |
| Mine 2 | 2.0-2.5 | 3.3-4.0 | — | +50-75% |
| Mine 3 | 3.0-3.5 | 4.4-5.0 | — | +40-50% |
| Mine 4 | 4.4-5.0 | 6.4-7.5 | — | +40-50% |
| Mine 5 | 5.0-5.8 | 7.5 | 8.33 | +15-20% |

> **Note :** Mine 5 a un gain plus faible en ratio parce que sa valeur vient du volume (gros blocs, AoE) et du Thorium jackpot, pas d'un ratio base meilleur sur les fillers.

---

## 5. Distribution des blocs par mine et couche

Chaque mine a une zone unique divisée en 3 couches par profondeur (Y). Les couches profondes contiennent plus de blocs rares et précieux.

### Mine 1 — Surface Quarry (Y 60-74, 15 blocs de haut)

#### Couche Surface (Y 70-74)

| Bloc | Poids | Rôle |
|------|-------|------|
| Rock_Stone | 65% | Filler dominant |
| Rock_Stone_Mossy | 20% | Standard |
| Rock_Sandstone | 12% | Standard |
| Rock_Chalk | 3% | Rare ici |
| Ore_Copper_Stone | 0% | Absent en surface |

**Avg price :** 1.47 — La surface est "facile et rapide", pas très rentable.

#### Couche Cœur (Y 65-69)

| Bloc | Poids | Rôle |
|------|-------|------|
| Rock_Stone | 50% | Filler |
| Rock_Stone_Mossy | 20% | Standard |
| Rock_Sandstone | 13% | Standard |
| Rock_Chalk | 9% | Uncommon |
| Ore_Copper_Stone | 8% | Rare |

**Avg price :** 1.91 — Plus de variété, le Copper commence à apparaître.

#### Couche Profonde (Y 60-64)

| Bloc | Poids | Rôle |
|------|-------|------|
| Rock_Stone | 35% | Filler réduit |
| Rock_Stone_Mossy | 18% | Standard |
| Rock_Sandstone | 15% | Standard |
| Rock_Chalk | 14% | Plus fréquent |
| Ore_Copper_Stone | 18% | Beaucoup plus fréquent |

**Avg price :** 2.35 — Creuser profond récompense. Le Copper à 18% contre 0% en surface crée un incentive clair.

#### Moyenne pondérée Mine 1 (1/3 par couche)

| Bloc | Poids global | HP | Prix |
|------|-------------|-----|------|
| Rock_Stone | 50.0% | 1 | 1 |
| Rock_Stone_Mossy | 19.3% | 1 | 2 |
| Rock_Sandstone | 13.3% | 1 | 2 |
| Rock_Chalk | 8.7% | 1 | 3 |
| Ore_Copper_Stone | 8.7% | 2 | 6 |

**Avg price global : 1.91** — **Avg HP : 1.09**

---

### Mine 2 — Crystal Caverns (Y 40-59, 20 blocs de haut)

#### Couche Surface (Y 53-59)

| Bloc | Poids | Rôle |
|------|-------|------|
| Rock_Shale | 40% | Filler |
| Rock_Slate | 28% | Standard |
| Rock_Quartzite | 15% | Uncommon |
| Ore_Iron_Shale | 7% | Uncommon |
| Rock_Crystal_Blue_Block | 5% | Rare |
| Rock_Crystal_Green_Block | 3% | Rare |
| Rock_Crystal_White_Block | 2% | Très rare |

**Avg price :** 5.74

#### Couche Cœur (Y 46-52)

| Bloc | Poids | Rôle |
|------|-------|------|
| Rock_Shale | 30% | Filler |
| Rock_Slate | 23% | Standard |
| Rock_Quartzite | 15% | Uncommon |
| Ore_Iron_Shale | 10% | Uncommon |
| Rock_Crystal_Blue_Block | 9% | Plus fréquent |
| Rock_Crystal_Green_Block | 7% | Plus fréquent |
| Rock_Crystal_White_Block | 6% | Rare |

**Avg price :** 7.19

#### Couche Profonde (Y 40-45)

| Bloc | Poids | Rôle |
|------|-------|------|
| Rock_Shale | 20% | Filler réduit |
| Rock_Slate | 18% | Standard |
| Rock_Quartzite | 14% | Uncommon |
| Ore_Iron_Shale | 13% | Fréquent |
| Rock_Crystal_Blue_Block | 13% | Fréquent |
| Rock_Crystal_Green_Block | 11% | Fréquent |
| Rock_Crystal_White_Block | 11% | Bien plus fréquent |

**Avg price :** 8.55

> **Sensation visée :** En surface, le joueur mine surtout du Shale/Slate gris. En descendant, les cristaux bleus et verts apparaissent en grappes de plus en plus denses. Le White Crystal profond est le "mini-jackpot" de Mine 2.

#### Moyenne pondérée Mine 2

| Bloc | Poids global | HP | Prix |
|------|-------------|-----|------|
| Rock_Shale | 30.0% | 2 | 4 |
| Rock_Slate | 23.0% | 2 | 5 |
| Rock_Quartzite | 14.7% | 3 | 7 |
| Ore_Iron_Shale | 10.0% | 3 | 10 |
| Rock_Crystal_Blue_Block | 9.0% | 3 | 12 |
| Rock_Crystal_Green_Block | 7.0% | 3 | 12 |
| Rock_Crystal_White_Block | 6.3% | 4 | 16 |

**Avg price global : 7.16** — **Avg HP : 2.47**

---

### Mine 3 — Volcanic Forge (Y 20-44, 25 blocs de haut)

#### Couche Surface (Y 37-44)

| Bloc | Poids | Rôle |
|------|-------|------|
| Rock_Basalt | 38% | Filler |
| Rock_Volcanic | 28% | Standard |
| Rock_Quartzite | 12% | Filler léger |
| Ore_Gold_Basalt | 7% | Uncommon |
| Ore_Cobalt_Shale | 5% | Uncommon |
| Rock_Crystal_Red_Block | 5% | Rare |
| Rock_Crystal_Pink_Block | 5% | Rare |

**Avg price :** 12.03

#### Couche Cœur (Y 28-36)

| Bloc | Poids | Rôle |
|------|-------|------|
| Rock_Basalt | 28% | Filler |
| Rock_Volcanic | 24% | Standard |
| Rock_Quartzite | 8% | Filler léger |
| Ore_Gold_Basalt | 12% | Plus fréquent |
| Ore_Cobalt_Shale | 10% | Plus fréquent |
| Rock_Crystal_Red_Block | 9% | Plus fréquent |
| Rock_Crystal_Pink_Block | 9% | Plus fréquent |

**Avg price :** 15.10

#### Couche Profonde (Y 20-27)

| Bloc | Poids | Rôle |
|------|-------|------|
| Rock_Basalt | 18% | Filler réduit |
| Rock_Volcanic | 17% | Standard |
| Rock_Quartzite | 5% | Minimal |
| Ore_Gold_Basalt | 16% | Abondant |
| Ore_Cobalt_Shale | 14% | Abondant |
| Rock_Crystal_Red_Block | 15% | Abondant |
| Rock_Crystal_Pink_Block | 15% | Abondant |

**Avg price :** 18.01

#### Moyenne pondérée Mine 3

| Bloc | Poids global | HP | Prix |
|------|-------------|-----|------|
| Rock_Basalt | 28.0% | 3 | 9 |
| Rock_Volcanic | 23.0% | 4 | 14 |
| Rock_Quartzite | 8.3% | 3 | 7 |
| Ore_Gold_Basalt | 11.7% | 5 | 25 |
| Ore_Cobalt_Shale | 9.7% | 5 | 22 |
| Rock_Crystal_Red_Block | 9.7% | 4 | 20 |
| Rock_Crystal_Pink_Block | 9.7% | 4 | 20 |

**Avg price global : 15.04** — **Avg HP : 3.80**

---

### Mine 4 — Frozen Abyss (Y 5-29, 25 blocs de haut)

#### Couche Surface (Y 22-29)

| Bloc | Poids | Rôle |
|------|-------|------|
| Rock_Marble | 33% | Filler |
| Rock_Calcite | 25% | Standard |
| Rock_Aqua | 14% | Uncommon |
| Ore_Silver_Basalt | 10% | Uncommon |
| Rock_Crystal_Yellow_Block | 6% | Rare |
| Ore_Mithril_Stone | 4% | Rare |
| Rock_Slate | 8% | Filler léger (rappel Mine 2) |

**Avg price :** 27.31

#### Couche Cœur (Y 14-21)

| Bloc | Poids | Rôle |
|------|-------|------|
| Rock_Marble | 25% | Filler |
| Rock_Calcite | 20% | Standard |
| Rock_Aqua | 16% | Plus fréquent |
| Ore_Silver_Basalt | 13% | Plus fréquent |
| Rock_Crystal_Yellow_Block | 10% | Plus fréquent |
| Ore_Mithril_Stone | 8% | Plus fréquent |
| Rock_Slate | 8% | Filler |

**Avg price :** 32.56

#### Couche Profonde (Y 5-13)

| Bloc | Poids | Rôle |
|------|-------|------|
| Rock_Marble | 18% | Filler réduit |
| Rock_Calcite | 14% | Standard |
| Rock_Aqua | 17% | Abondant |
| Ore_Silver_Basalt | 16% | Abondant |
| Rock_Crystal_Yellow_Block | 14% | Abondant |
| Ore_Mithril_Stone | 14% | Abondant |
| Rock_Slate | 7% | Minimal |

**Avg price :** 38.19

#### Moyenne pondérée Mine 4

| Bloc | Poids global | HP | Prix |
|------|-------------|-----|------|
| Rock_Marble | 25.3% | 5 | 22 |
| Rock_Calcite | 19.7% | 5 | 25 |
| Rock_Aqua | 15.7% | 6 | 35 |
| Ore_Silver_Basalt | 13.0% | 7 | 45 |
| Rock_Crystal_Yellow_Block | 10.0% | 7 | 50 |
| Ore_Mithril_Stone | 8.7% | 8 | 60 |
| Rock_Slate | 7.7% | 2 | 5 |

**Avg price global : 32.69** — **Avg HP : 5.55**

---

### Mine 5 — Void Core (Y -25 à 5, 30 blocs de haut)

#### Couche Surface (Y -2 à 5)

| Bloc | Poids | Rôle |
|------|-------|------|
| Rock_Bedrock | 28% | Filler |
| Rock_Salt | 20% | Standard |
| Ore_Gold_Basalt | 12% | Recyclé |
| Rock_Crystal_Red_Block | 10% | Recyclé |
| Ore_Mithril_Stone | 8% | Uncommon |
| Rock_Crystal_Pink_Block | 8% | Recyclé |
| Rock_Volcanic | 7% | Filler léger |
| Rock_Crystal_Yellow_Block | 4% | Rare |
| Ore_Thorium_Sandstone | 3% | Jackpot rare |

**Avg price :** 35.14

#### Couche Cœur (Y -14 à -3)

| Bloc | Poids | Rôle |
|------|-------|------|
| Rock_Bedrock | 22% | Filler |
| Rock_Salt | 18% | Standard |
| Ore_Mithril_Stone | 13% | Plus fréquent |
| Ore_Gold_Basalt | 10% | Recyclé |
| Rock_Crystal_Yellow_Block | 10% | Plus fréquent |
| Rock_Crystal_Red_Block | 8% | Recyclé |
| Rock_Crystal_Pink_Block | 7% | Recyclé |
| Ore_Thorium_Sandstone | 8% | Plus fréquent |
| Rock_Volcanic | 4% | Minimal |

**Avg price :** 43.54

#### Couche Profonde (Y -25 à -15)

| Bloc | Poids | Rôle |
|------|-------|------|
| Rock_Bedrock | 15% | Filler réduit |
| Rock_Salt | 12% | Standard |
| Ore_Thorium_Sandstone | 18% | ABONDANT — le jackpot |
| Ore_Mithril_Stone | 17% | Abondant |
| Rock_Crystal_Yellow_Block | 14% | Abondant |
| Rock_Crystal_Red_Block | 8% | Recyclé |
| Rock_Crystal_Pink_Block | 7% | Recyclé |
| Ore_Gold_Basalt | 6% | Minimal |
| Rock_Volcanic | 3% | Minimal |

**Avg price :** 53.21

> **Sensation visée :** La couche profonde de Mine 5 est le "paradis du mineur". 18% de Thorium (100 cryst chacun), 17% de Mithril (60 cryst), 14% de Yellow Crystal (50 cryst). Chaque bloc cassé a une chance significative d'être un jackpot. C'est la récompense ultime pour avoir investi dans Jackhammer (pour atteindre les couches profondes) et toutes les upgrades.

#### Moyenne pondérée Mine 5

| Bloc | Poids global | HP | Prix |
|------|-------------|-----|------|
| Rock_Bedrock | 21.7% | 8 | 40 |
| Rock_Salt | 16.7% | 6 | 35 |
| Ore_Thorium_Sandstone | 9.7% | 12 | 100 |
| Ore_Mithril_Stone | 12.7% | 8 | 60 |
| Ore_Gold_Basalt | 9.3% | 5 | 25 |
| Rock_Crystal_Yellow_Block | 9.3% | 7 | 50 |
| Rock_Crystal_Red_Block | 8.7% | 4 | 20 |
| Rock_Crystal_Pink_Block | 7.3% | 4 | 20 |
| Rock_Volcanic | 4.7% | 4 | 14 |

**Avg price global : 43.96** — **Avg HP : 6.62**

---

## 6. Projections de revenus

### Méthode de calcul

```
blocks_per_min = 60 / (avg_HP / hits_per_sec) × efficiency
hits_per_sec = 2.0 × pickaxe_speed_multiplier
efficiency = 0.70 (mouvement, visée, etc.)

income_per_min = blocks_per_min × avg_price
```

### Revenus manuels par phase (sans upgrades)

| Mine | Pioche | Hits/sec | Avg HP | Avg Prix | Blocs/min | Crystals/min |
|------|--------|---------|--------|----------|-----------|-------------|
| Mine 1 | Wood (×1.0) | 2.0 | 1.09 | 1.91 | 77 | **147** |
| Mine 1 | Stone (×1.5) | 3.0 | 1.09 | 1.91 | 116 | **221** |
| Mine 2 | Stone (×1.5) | 3.0 | 2.47 | 7.16 | 51 | **365** |
| Mine 2 | Iron (×2.0) | 4.0 | 2.47 | 7.16 | 68 | **487** |
| Mine 3 | Iron (×2.0) | 4.0 | 3.80 | 15.04 | 44 | **662** |
| Mine 3 | Crystal (×3.0) | 6.0 | 3.80 | 15.04 | 66 | **993** |
| Mine 4 | Crystal (×3.0) | 6.0 | 5.55 | 32.69 | 45 | **1,471** |
| Mine 4 | Void (×4.0) | 8.0 | 5.55 | 32.69 | 60 | **1,961** |
| Mine 5 | Void (×4.0) | 8.0 | 6.62 | 43.96 | 51 | **2,242** |
| Mine 5 | Prismatic (×5.0) | 10.0 | 6.62 | 43.96 | 63 | **2,769** |

### Impact estimé des upgrades (mid et max)

| Upgrade | Niveau mid | Bonus mid | Niveau max | Bonus max |
|---------|-----------|-----------|-----------|-----------|
| Fortune | 10 | +~22% income | 25 | +~58% income |
| Haste | 10 | +50% speed | 20 | +100% speed |
| Momentum | 10 | combo 35, +~35% dmg | 25 | combo 80, +~80% dmg |
| Jackhammer | 5 | 5 blocs/hit | 10 | 10 blocs/hit |
| Blast | 5 | rayon 2 | 15 | rayon 4 |

> **Note Fortune :** à niveau 25, triple chance = 10%, double chance = 50%. Espérance = 0.10×3 + 0.50×2 + 0.40×1 = 1.70 bloc/hit. Soit **+70% income**, arrondi à +58% car les triples sont rares.
>
> Calcul exact : E[fortune_25] = 0.10×3 + 0.50×2 + 0.40×1 = 0.30 + 1.00 + 0.40 = 1.70, donc +70%.

### Revenus avec upgrades mid-game (Fortune 10, Haste 10, Momentum 10)

Multiplicateur estimé : ×1.22 (fortune) × ×1.50 (haste) × ×1.15 (momentum, conservateur) = **×2.10**

| Mine | Pioche | Base cryst/min | Avec upgrades mid |
|------|--------|---------------|-------------------|
| Mine 3 | Crystal | 993 | **~2,085** |
| Mine 4 | Void | 1,961 | **~4,118** |
| Mine 5 | Prismatic | 2,769 | **~5,815** |

### Revenus avec upgrades endgame (Fortune 25, Haste 20, Momentum 25, Blast 10+)

Multiplicateur estimé (sans AoE) : ×1.70 × ×2.00 × ×1.40 = **×4.76**
Avec AoE (Blast/Jackhammer ajoutent ~2-4× blocs supplémentaires en pratique) : **×10-15**

| Mine | Pioche | Base cryst/min | Avec upgrades max (sans AoE) | Avec AoE |
|------|--------|---------------|------------------------------|----------|
| Mine 5 | Prismatic | 2,769 | **~13,180** | **~28,000-42,000** |

> Les AoE sont difficiles à modéliser précisément car ils dépendent de la densité de blocs non-cassés autour de la cible. Ces estimations supposent un blast radius 3 dans une zone densément peuplée.

---

## 7. Progression pioche

### Valeurs actuelles (code)

| Tier | Nom | Speed ×  | Coût | Prérequis |
|------|-----|---------|------|-----------|
| 0 | Wood Pickaxe | 1.0 | 0 | — |
| 1 | Stone Pickaxe | 1.5 | 500 | — |
| 2 | Iron Pickaxe | 2.0 | 5,000 | — |
| 3 | Crystal Pickaxe | 3.0 | 25,000 | 2+ mines unlock |
| 4 | Void Pickaxe | 4.0 | 100,000 | 3+ mines unlock |
| 5 | Prismatic Pickaxe | 5.0 | 500,000 | Toutes mines unlock |

### Analyse d'alignement avec l'économie minière

| Pioche | Coût | Revenu à l'achat | Temps pour payer | Verdict |
|--------|------|------------------|-----------------|---------|
| Stone | 500 | ~147/min (Mine 1, Wood) | **3.4 min** | Parfait — premier objectif rapide |
| Iron | 5,000 | ~365/min (Mine 2, Stone) | **13.7 min** | Bon — accessible mi-Mine 2 |
| Crystal | 25,000 | ~487/min (Mine 2, Iron) | **51 min** | Correct — gros palier, mais le joueur a aussi des upgrades |
| Void | 100,000 | ~993/min (Mine 3, Crystal) | **101 min** | Long mais ok avec upgrades mid (~48 min) |
| Prismatic | 500,000 | ~2,242/min (Mine 5, Void) | **223 min** | Très long, mais c'est l'endgame. Avec upgrades : ~93 min |

### Recommandation

Les valeurs actuelles sont **globalement bien calibrées** pour cette proposition de mines. Quelques points d'attention :

1. **Crystal Pickaxe (25,000) + Mine 3 unlock (10,000) = 35,000 total.** Le joueur doit accumuler ça en Mine 2 avec Iron Pick (~487/min). Soit ~72 min pour les deux. C'est le "mur" du mid-game. Avec Fortune 5 + Haste 5, ça tombe à ~45 min. **Acceptable.**

2. **Prismatic (500,000) nécessite "All mines unlocked"** = il faut avoir payé les 5 mines (total 261,500) AVANT de pouvoir acheter la pioche. C'est un frein intentionnel qui force l'exploration de toutes les mines. **Bon design.**

3. **Stone → Iron : saut ×10.** Le joueur à Stone Pick farm Mine 1 ou Mine 2. Le saut de coût est normal car Mine 2 paie ~2.5× mieux. **OK.**

---

## 8. Analyse des upgrades

### Coût total par upgrade (formules du code)

| Upgrade | Formule | Max Lvl | Coût lvl 1 | Coût lvl 10 | Coût lvl 25 | Total to max |
|---------|---------|---------|-----------|------------|------------|-------------|
| Bag Capacity | `25 × 1.20^lvl` | 50 | 25 | 129 | 2,365 | ~1,137,000 |
| Momentum | `50 × 1.22^lvl` | 25 | 50 | 363 | 5,965 | ~31,000 |
| Fortune | `60 × 1.22^lvl` | 25 | 60 | 436 | 7,158 | ~37,200 |
| Jackhammer | `150 × 1.28^lvl` | 10 | 150 | 1,556 | — | ~5,300 |
| Stomp | `200 × 1.30^lvl` | 15 | 200 | 2,758 | — | ~33,500 |
| Blast | `250 × 1.30^lvl` | 15 | 250 | 3,448 | — | ~41,800 |
| Haste | `40 × 1.20^lvl` | 20 | 40 | 207 | — | ~7,500 |

**Total pour max TOUT : ~1,293,300 crystals** (dont 88% pour Bag Capacity seul)

### Priorité d'upgrade suggérée (pour le joueur)

| Priorité | Upgrade | Raison | Quand |
|----------|---------|--------|-------|
| 1 | Haste 5-10 | +25-50% vitesse, très bon marché (~320-1,200 cryst) | Dès Mine 1 |
| 2 | Fortune 5-10 | +10-20% income, retour sur investissement rapide | Mine 1-2 |
| 3 | Bag Capacity 5-10 | 100-150 slots, réduit les aller-retours | Mine 1-2 |
| 4 | Momentum 5-10 | Combo 20-35, boost significatif sur blocs multi-HP | Mine 2-3 |
| 5 | Jackhammer 3-5 | Gagne 3-5 blocs gratuits par hit, très rentable | Mine 2-3 |
| 6 | Blast 5-8 | Rayon 2, énorme gain en volume | Mine 3+ |
| 7 | Stomp 5 | Rayon 2 sur atterrissage, situationnel | Mine 3+ |
| 8 | Haste/Fortune max | Endgame power spike | Mine 4-5 |
| 9 | Bag Capacity max | Crystal sink longue durée | Endgame |

### Analyse du Bag Capacity

Le Bag Capacity est intentionnellement le **plus gros crystal sink** du système :

| Niveau | Capacité | Coût de ce niveau | Coût total cumulé |
|--------|---------|-------------------|-------------------|
| 0 | 50 | — | 0 |
| 5 | 100 | 62 | 186 |
| 10 | 150 | 155 | 672 |
| 15 | 200 | 385 | 2,051 |
| 20 | 250 | 957 | 5,923 |
| 25 | 300 | 2,383 | 16,573 |
| 30 | 350 | 5,929 | 44,709 |
| 35 | 400 | 14,755 | 117,866 |
| 40 | 450 | 36,689 | 305,774 |
| 45 | 500 | 91,305 | 782,800 |
| 50 | 550 | 227,132 | 1,137,300 |

Les 10 derniers niveaux (41-50) coûtent ~831,500 pour +100 slots. C'est un sink exponentiel intentionnel. Le joueur "normal" s'arrêtera vers 20-30 (250-350 slots). Seuls les joueurs endgame pousseront au-delà.

**Point d'attention :** Avec l'auto-sell overflow, le bag capacity est moins critique qu'il n'y paraît. Le joueur qui a un sac plein ne perd pas de blocs — ils sont auto-vendus. Le bag est surtout utile pour **choisir** quand vendre (garder les blocs chers, laisser auto-sell les cheap). Considérer si 550 slots max est nécessaire ou si 300 suffirait (réduire max level à 25).

---

## 9. Économie des miners

### Coûts actuels (code)

| Action | Formule | Exemples |
|--------|---------|---------|
| Acheter un miner | `1,000` fixe | 1,000 par mine |
| Speed upgrade | `50 × 1.15^totalLevel` | Lvl 0: 50, Lvl 5: 101, Lvl 10: 202 |
| Evolution (stars) | `5,000 × 3^stars` | 0→1: 5,000 / 1→2: 15,000 / 2→3: 45,000 |

Où `totalLevel = stars × 25 + speedLevel` (le coût continue à grimper à travers les évolutions).

### Production par miner

```
blocks_per_min = 6.0 × (1.0 + speedLevel × 0.10) × (1.0 + stars × 0.50)
```

| Speed | Stars | Blocks/min | À Mine 1 (×1.91) | À Mine 3 (×15.04) | À Mine 5 (×43.96) |
|-------|-------|-----------|------------------|-------------------|-------------------|
| 0 | 0 | 6.0 | 11 cryst/min | 90 cryst/min | 264 cryst/min |
| 10 | 0 | 12.0 | 23 cryst/min | 180 cryst/min | 528 cryst/min |
| 25 | 0 | 21.0 | 40 cryst/min | 316 cryst/min | 923 cryst/min |
| 25 | 1 | 31.5 | 60 cryst/min | 474 cryst/min | 1,385 cryst/min |
| 25 | 2 | 42.0 | 80 cryst/min | 632 cryst/min | 1,846 cryst/min |
| 25 | 3 | 52.5 | 100 cryst/min | 789 cryst/min | 2,308 cryst/min |
| 25 | 5 | 73.5 | 140 cryst/min | 1,105 cryst/min | 3,231 cryst/min |

### Retour sur investissement (ROI) d'un miner

**Miner Mine 1 (avg 1.91 cryst/bloc), Speed 0, 0 stars :**
- Coût d'achat : 1,000
- Revenus : 11 cryst/min
- ROI : 1,000 / 11 = **91 minutes** ← très long pour la valeur produite

**Miner Mine 3 (avg 15.04 cryst/bloc), Speed 0, 0 stars :**
- Coût d'achat : 1,000
- Revenus : 90 cryst/min
- ROI : 1,000 / 90 = **11 minutes** ← excellent

**Miner Mine 5 (avg 43.96 cryst/bloc), Speed 10, 1 star :**
- Coût total : 1,000 (achat) + ~3,900 (speed 0-10) + 5,000 (evolution) = ~9,900
- Production : 6 × 2.0 × 1.5 = 18 blocks/min = 791 cryst/min
- ROI : 9,900 / 791 = **12.5 minutes** ← très bon

### Recommandation miners

1. **Miner Mine 1 : mauvais ROI.** Le joueur a intérêt à attendre d'avoir débloqué Mine 2 ou 3 avant d'investir dans un miner. À 1,000 crystals de coût et 11 cryst/min de revenus, le ROI est ~91 min. Le joueur a mieux à dépenser (pioche, upgrades).

2. **Miner Mine 3+ : excellent ROI.** Le coût fixe de 1,000 devient négligeable face aux block prices de Mine 3+. Un miner Mine 3 rembourse en 11 min et produit indéfiniment.

3. **Speed upgrades : ROI décroissant.** Chaque niveau de speed coûte +15% de plus mais produit linéairement plus. Les premiers levels (0-10) sont très rentables. Au-delà de 15, le coût grimpe vite.

4. **Évolutions (stars) : exponentiellement chères.** L'évolution 0→1 (5,000) booste de ×1.5, soit +50% production. Pour un miner Mine 5 base (~264/min), c'est +132/min. ROI : 5,000/132 = 38 min. Ok. L'évolution 2→3 (45,000) booste de ×2.5→×3.0 = +20% relatif. ROI beaucoup plus long. **Les stars hautes sont un sink endgame.**

### Suggestion d'ajustement

Le coût fixe de 1,000 pour acheter un miner est trop bas pour les mines tardives et trop haut pour Mine 1 (relative à l'income). Deux options :

- **Option A :** Coût d'achat variable par mine (500 pour Mine 1, 2,000 pour Mine 3, 5,000 pour Mine 5)
- **Option B :** Garder le coût fixe, accepter que les miners Mine 1 ne soient pas optimaux. Le joueur les achète quand même plus tard pour le bonus cross-progression "All miners = +20% multiplier gain". **Préférence personnelle pour B** — c'est plus simple et le bonus cross-prog justifie l'achat tardif.

---

## 10. Timeline de progression

### Scénario "joueur efficace" (focus mining, dépense raisonnable en upgrades)

> Hypothèse : le joueur dépense ~30% de ses crystals en upgrades (Haste, Fortune prioritaires) et ~70% en milestones (pickaxe, mine unlock).

| Temps | Milestone | Mine | Pioche | Income approx |
|-------|-----------|------|--------|--------------|
| 0 min | Début | Mine 1 | Wood | ~147/min |
| 4 min | Stone Pickaxe | Mine 1 | Stone | ~221/min |
| 8 min | Haste 3 + Fortune 2 | Mine 1 | Stone | ~280/min |
| 12 min | **Mine 2 unlock** | Mine 2 | Stone | ~365/min |
| 18 min | Haste 5 + Fortune 5 | Mine 2 | Stone | ~510/min |
| 25 min | Iron Pickaxe | Mine 2 | Iron | ~680/min |
| 35 min | Momentum 5 + Jackhammer 3 | Mine 2 | Iron | ~900/min |
| 48 min | **Mine 3 unlock** | Mine 3 | Iron | ~930/min |
| 55 min | Crystal Pickaxe | Mine 3 | Crystal | ~1,390/min |
| 70 min | Fortune 15 + Haste 10 + Blast 3 | Mine 3 | Crystal | ~2,200/min |
| 90 min | **Mine 4 unlock** | Mine 4 | Crystal | ~2,600/min |
| 105 min | Void Pickaxe | Mine 4 | Void | ~3,500/min |
| 120 min | Miner Mine 3 + Mine 4 | Mine 4 | Void | ~3,800/min + passif |
| 145 min | Fortune 20 + Haste 15 + Blast 8 | Mine 4 | Void | ~5,000/min |
| 175 min | **Mine 5 unlock** | Mine 5 | Void | ~5,600/min |
| 200 min | Prismatic Pickaxe | Mine 5 | Prismatic | ~7,000/min |
| 250 min | Max Fortune + Haste + Blast | Mine 5 | Prismatic | ~15,000/min |
| 350+ min | Max miners, Bag Capacity 30+ | Mine 5 | Prismatic | ~20,000+/min |

### Total mine unlocks + pickaxes

| Poste | Coût |
|-------|------|
| Mine 2 | 1,500 |
| Mine 3 | 10,000 |
| Mine 4 | 50,000 |
| Mine 5 | 200,000 |
| Stone Pick | 500 |
| Iron Pick | 5,000 |
| Crystal Pick | 25,000 |
| Void Pick | 100,000 |
| Prismatic Pick | 500,000 |
| **Total milestones** | **892,000** |

### Total upgrades (everything maxed)

| Poste | Coût |
|-------|------|
| Bag Capacity (50) | 1,137,000 |
| Fortune (25) | 37,200 |
| Haste (20) | 7,500 |
| Momentum (25) | 31,000 |
| Jackhammer (10) | 5,300 |
| Blast (15) | 41,800 |
| Stomp (15) | 33,500 |
| **Total upgrades** | **1,293,300** |

### Total miners (5 mines, speed 25, 2 stars chacun)

| Poste | Coût |
|-------|------|
| 5× achat | 5,000 |
| 5× speed 0→25 | ~89,000 |
| 5× evolution 0→2 | 100,000 |
| **Total miners** | **~194,000** |

### Grand total (tout maxer sauf miners stars 3+)

**~2,379,300 crystals**

Avec un income endgame de ~15,000/min, il faut ~159 min de farm pure en Mine 5 pour tout max (hors AoE qui multiplie encore). **Soit ~2.5h de farm endgame**, ce qui est raisonnable pour un "100% completion".

---

## 11. Bonus cross-progression

Rappel des bonus existants dans le code (`MineBonusCalculator`) :

| Condition | Bonus parkour | Impact |
|-----------|-------------|--------|
| Mine 2 unlock | +5% Runner Speed | Petit boost early, incentive à essayer le mining |
| Mine 3 unlock | +10% Multiplier Gain | Significatif mid-game |
| Mine 4 unlock | +15% Volt Gain | Très significatif late-game |
| Tous miners achetés | +20% Multiplier Gain | Incentive à investir dans les miners |

### Analyse

1. **Mine 2 (+5% speed) :** Coût 1,500 cryst. Accessible vers 12 min de mine. Bon incentive pour les joueurs parkour qui ne veulent que le bonus — 12 min de mining pour un +5% permanent.

2. **Mine 3 (+10% mult gain) :** Coût total ~11,500 cryst (Mine 2 + Mine 3). ~30 min de mining. Le +10% multiplier gain est significatif, surtout avec des stars élevées.

3. **Mine 4 (+15% volt gain) :** Coût total ~61,500 cryst. ~90 min de mining. Le +15% volt gain est excellent pour la progression parkour.

4. **Tous miners (+20% mult gain) :** 5 miners × 1,000 = 5,000 cryst. Mais nécessite toutes les mines débloquées (261,500). Le bonus de +20% mult gain est massif mais arrive tard.

### Suggestion

Le système actuel fonctionne bien en termes d'incentives. Les joueurs parkour-first ont un bon motif pour explorer le mining (les bonus sont permanents et significatifs). Les joueurs mine-first ont une boucle autonome satisfaisante avec les crystals/upgrades.

**Une idée à explorer :** un bonus pour Mine 5 unlock (ex: +25% volt gain ou +1 base evolution power). Actuellement Mine 5 n'a pas de bonus cross-progression dédié, ce qui réduit l'incentive pour les joueurs parkour d'atteindre l'endgame mine.

---

## 12. Leviers d'ajustement

Voici les paramètres les plus impactants à tourner si l'équilibrage ne convient pas après test :

### Vitesse de progression trop rapide ?

| Levier | Effet | Impact |
|--------|-------|--------|
| ↑ Mine unlock costs | Ralentit les transitions | Fort |
| ↑ Block HP | Plus de hits par bloc = moins de blocs/min | Fort |
| ↓ Block prices | Moins de crystals par bloc | Fort |
| ↑ Regen cooldown | Plus d'attente entre les resets de zone | Modéré |
| ↓ Zone size | Moins de blocs avant regen | Modéré |

### Vitesse de progression trop lente ?

| Levier | Effet | Impact |
|--------|-------|--------|
| ↓ Mine unlock costs | Transitions plus rapides | Fort |
| ↑ Block prices (rare) | Meilleur income des blocs rares | Modéré (touche la variance) |
| ↓ Block HP | Blocs plus faciles | Fort |
| ↑ Fortune/Haste effect | Upgrades plus puissantes | Modéré |
| ↓ Upgrade costs | Plus accessibles | Modéré |

### Gameplay ennuyeux en Mine X ?

| Levier | Effet |
|--------|-------|
| ↑ Écart de rareté entre couches | Plus intéressant de descendre |
| Ajouter un bloc "jackpot" local | Donne un but au farm |
| ↓ HP des fillers + ↑ HP des rares | Rythme plus contrasté |
| Ajouter des blocs visuellement distincts | Plus de feedback visuel |

### Équilibre mine-first vs parkour-first

| Levier | Effet |
|--------|-------|
| ↑ Cross-progression bonuses | Incentive à miner pour les joueurs parkour |
| ↓ Cross-progression bonuses | Le mining est plus autonome |
| ↑ Miner production | Le mining passif compense le temps parkour |
| Ajouter des milestones mine→parkour | Plus de raisons de switcher |

### Paramètres "safe" à ajuster en premier

1. **Block prices** — le plus facile, 0 impact sur le gameplay feel, change juste la vitesse
2. **Mine unlock costs** — change le timing des transitions
3. **Layer distributions** — change la variance et l'excitation sans toucher la base

### Paramètres dangereux (modifier avec précaution)

1. **Block HP** — change la sensation de jeu fondamentale (rapide vs lent)
2. **Pickaxe costs/requirements** — ces valeurs sont dans le code enum, pas en DB
3. **Upgrade formulas** — en dur dans le code, changement = recompile

---

## Annexe A — Block Type IDs pour la DB

Pour référence lors de la configuration en `mine_zones.block_table_json` et `block_prices` :

```
-- Roches
Rock_Stone, Rock_Stone_Mossy, Rock_Sandstone, Rock_Sandstone_Red, Rock_Sandstone_White
Rock_Chalk, Rock_Shale, Rock_Slate, Rock_Quartzite
Rock_Basalt, Rock_Volcanic
Rock_Marble, Rock_Calcite, Rock_Aqua
Rock_Bedrock, Rock_Salt

-- Cristaux
Rock_Crystal_Blue_Block, Rock_Crystal_Green_Block, Rock_Crystal_White_Block
Rock_Crystal_Red_Block, Rock_Crystal_Pink_Block, Rock_Crystal_Yellow_Block

-- Minerais (une seule variante rock par minerai par mine)
Ore_Copper_Stone, Ore_Copper_Sandstone, Ore_Copper_Shale
Ore_Iron_Shale, Ore_Iron_Slate, Ore_Iron_Stone, Ore_Iron_Sandstone, Ore_Iron_Basalt, Ore_Iron_Volcanic
Ore_Gold_Basalt, Ore_Gold_Volcanic, Ore_Gold_Sandstone, Ore_Gold_Shale, Ore_Gold_Stone
Ore_Cobalt_Shale, Ore_Cobalt_Slate
Ore_Silver_Basalt, Ore_Silver_Slate, Ore_Silver_Shale, Ore_Silver_Sandstone, Ore_Silver_Stone, Ore_Silver_Volcanic
Ore_Mithril_Stone
Ore_Thorium_Sandstone
```

## Annexe B — SQL seed pour block_prices et block_hp

> **Note :** Ces valeurs correspondent exactement au catalogue de la section 4.

```sql
-- Block prices (global)
INSERT INTO block_prices (block_type_id, price_mantissa, price_exp10) VALUES
-- Roches
('Rock_Stone', 1, 0),
('Rock_Stone_Mossy', 2, 0),
('Rock_Sandstone', 2, 0),
('Rock_Sandstone_Red', 2, 0),
('Rock_Chalk', 3, 0),
('Rock_Shale', 4, 0),
('Rock_Slate', 5, 0),
('Rock_Quartzite', 7, 0),
('Rock_Basalt', 9, 0),
('Rock_Volcanic', 14, 0),
('Rock_Marble', 22, 0),
('Rock_Calcite', 25, 0),
('Rock_Aqua', 35, 0),
('Rock_Bedrock', 40, 0),
('Rock_Salt', 35, 0),
-- Cristaux
('Rock_Crystal_Blue_Block', 12, 0),
('Rock_Crystal_Green_Block', 12, 0),
('Rock_Crystal_White_Block', 16, 0),
('Rock_Crystal_Red_Block', 20, 0),
('Rock_Crystal_Pink_Block', 20, 0),
('Rock_Crystal_Yellow_Block', 50, 0),
-- Minerais
('Ore_Copper_Stone', 6, 0),
('Ore_Copper_Sandstone', 6, 0),
('Ore_Copper_Shale', 6, 0),
('Ore_Iron_Shale', 10, 0),
('Ore_Iron_Slate', 10, 0),
('Ore_Iron_Stone', 10, 0),
('Ore_Iron_Sandstone', 10, 0),
('Ore_Iron_Basalt', 10, 0),
('Ore_Iron_Volcanic', 10, 0),
('Ore_Gold_Basalt', 25, 0),
('Ore_Gold_Volcanic', 25, 0),
('Ore_Gold_Sandstone', 25, 0),
('Ore_Gold_Shale', 25, 0),
('Ore_Gold_Stone', 25, 0),
('Ore_Cobalt_Shale', 22, 0),
('Ore_Cobalt_Slate', 22, 0),
('Ore_Silver_Basalt', 45, 0),
('Ore_Silver_Slate', 45, 0),
('Ore_Silver_Shale', 45, 0),
('Ore_Silver_Sandstone', 45, 0),
('Ore_Silver_Stone', 45, 0),
('Ore_Silver_Volcanic', 45, 0),
('Ore_Mithril_Stone', 60, 0),
('Ore_Thorium_Sandstone', 100, 0);

-- Block HP (global)
INSERT INTO block_hp (block_type_id, hp) VALUES
-- Roches
('Rock_Stone', 1),
('Rock_Stone_Mossy', 1),
('Rock_Sandstone', 1),
('Rock_Sandstone_Red', 1),
('Rock_Chalk', 1),
('Rock_Shale', 2),
('Rock_Slate', 2),
('Rock_Quartzite', 3),
('Rock_Basalt', 3),
('Rock_Volcanic', 4),
('Rock_Marble', 5),
('Rock_Calcite', 5),
('Rock_Aqua', 6),
('Rock_Bedrock', 8),
('Rock_Salt', 6),
-- Cristaux
('Rock_Crystal_Blue_Block', 3),
('Rock_Crystal_Green_Block', 3),
('Rock_Crystal_White_Block', 4),
('Rock_Crystal_Red_Block', 4),
('Rock_Crystal_Pink_Block', 4),
('Rock_Crystal_Yellow_Block', 7),
-- Minerais
('Ore_Copper_Stone', 2),
('Ore_Copper_Sandstone', 2),
('Ore_Copper_Shale', 2),
('Ore_Iron_Shale', 3),
('Ore_Iron_Slate', 3),
('Ore_Iron_Stone', 3),
('Ore_Iron_Sandstone', 3),
('Ore_Iron_Basalt', 3),
('Ore_Iron_Volcanic', 3),
('Ore_Gold_Basalt', 5),
('Ore_Gold_Volcanic', 5),
('Ore_Gold_Sandstone', 5),
('Ore_Gold_Shale', 5),
('Ore_Gold_Stone', 5),
('Ore_Cobalt_Shale', 5),
('Ore_Cobalt_Slate', 5),
('Ore_Silver_Basalt', 7),
('Ore_Silver_Slate', 7),
('Ore_Silver_Shale', 7),
('Ore_Silver_Sandstone', 7),
('Ore_Silver_Stone', 7),
('Ore_Silver_Volcanic', 7),
('Ore_Mithril_Stone', 8),
('Ore_Thorium_Sandstone', 12);
```
