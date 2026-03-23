# Improvement Prompts

Three prompts to send in separate conversations. Each one should produce an implementation plan in `docs/plans/`.

---

## 1. Test Coverage

Analyse la codebase et identifie toutes les classes de pure logique (zéro import Hytale) qui sont testables en isolation. Écris un plan d'implémentation pour ajouter des tests unitaires sur ces classes, en priorisant celles qui portent le plus de risque (calculs économiques, formules de progression, opérations BigNumber, utilitaires partagés). Le plan doit couvrir aussi la mise en place de la structure de test pour les modules qui n'en ont pas encore.

---

## 2. CI Pipeline

Analyse le projet (Gradle multi-module, WSL2, GitHub) et écris un plan pour mettre en place un pipeline CI minimal mais efficace : build, tests, et validation de base sur chaque push. Le plan doit rester simple et pragmatique — pas d'over-engineering, juste ce qui apporte de la valeur immédiate pour un projet de cette taille.

---

## 3. Singleton Decoupling

Analyse l'utilisation des singletons dans la codebase (managers, stores, bridges) et évalue le couplage que ça crée. Écris un plan pour réduire ce couplage sur les classes les plus critiques, sans tout réécrire — un refactor progressif qui améliore la testabilité et la maintenabilité sans casser l'existant.
