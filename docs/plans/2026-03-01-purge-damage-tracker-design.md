# Purge Damage Tracker & DPS Meter

## Objectif

Tracker les degats infliges par chaque joueur pendant une session Purge et afficher un classement (scoreboard) en temps reel dans le HUD.

- **Metric affichee** : damage total cumule par joueur (pas de DPS reel)
- **Persistence** : session uniquement (pas de sauvegarde en DB)
- **Affichage** : scoreboard integre dans le HUD existant (`Purge_RunHud.ui`)

---

## 1. Tracking des degats dans `PurgeSessionPlayerState`

**Fichier** : `hyvexa-purge/.../data/PurgeSessionPlayerState.java`

Ajouter un compteur atomique de damage total :

```java
private final AtomicLong totalDamageDealt = new AtomicLong(0);

public long getTotalDamageDealt() { return totalDamageDealt.get(); }
public void addDamageDealt(long amount) { totalDamageDealt.addAndGet(amount); }
```

On utilise `AtomicLong` (comme `kills` utilise `AtomicInteger`) car le damage s'accumule depuis le damage system (world thread) et est lu par le HUD tick (scheduler thread).

---

## 2. Enregistrement du damage dans `PurgeDamageModifierSystem`

**Fichier** : `hyvexa-purge/.../system/PurgeDamageModifierSystem.java`

Dans `applyPlayerDamageOverride()`, apres le calcul du damage final (ligne ~161), enregistrer le montant dans le player state :

```java
// Juste apres: event.setAmount(damage);
if (playerState != null) {
    playerState.addDamageDealt((long) damage);
}
```

Cela capture le damage exact applique au zombie, incluant les multiplicateurs d'arme et de XP. Le tracking se fait au moment ou le damage est inflige, pas au moment de la mort du zombie -- ce qui est plus precis pour un "damage dealt".

**Note** : seul le damage contre les zombies de la session est comptabilise (le friendly fire est deja annule plus haut dans le flow). Le damage overkill (au-dela des HP restants du zombie) est comptabilise aussi car le calcul exact serait trop couteux et la difference est negligeable.

---

## 3. UI du scoreboard - Fichier `.ui`

**Fichier** : `hyvexa-purge/.../resources/Common/UI/Custom/Pages/Purge_RunHud.ui`

Ajouter un nouveau groupe `#DmgBoard` dans le `#HudLayer`, positionne en haut a gauche (symetrique au panneau info a droite). Le scoreboard supporte jusqu'a 4 joueurs (taille max d'une party Purge).

```
Group #DmgBoard {
  Anchor: (Left: 20, Top: 200, Width: 240, Height: 148);
  LayoutMode: Top;
  Background: #0d1620(0.88);
  OutlineColor: #ffffff(0.06);
  OutlineSize: 1;
  Padding: (Left: 12, Right: 12, Top: 8, Bottom: 8);
  Visible: false;

  Label #DmgBoardTitle {
    Anchor: (Height: 20, Left: 0, Right: 0);
    Style: (FontSize: 14, TextColor: #ef4444, RenderBold: true, LetterSpacing: 1, RenderUppercase: true);
    Text: "DAMAGE";
  }

  Group #DmgRow0 {
    Anchor: (Left: 0, Right: 0, Top: 4, Height: 24);
    LayoutMode: Left;
    Visible: false;

    Label #DmgName0 {
      Anchor: (Height: 24);
      Style: (FontSize: 15, TextColor: #e7f1f4, RenderBold: true, VerticalAlignment: Center);
      Text: "";
    }
    Group { FlexWeight: 1; }
    Label #DmgValue0 {
      Anchor: (Height: 24);
      Style: (FontSize: 15, TextColor: #f59e0b, RenderBold: true, VerticalAlignment: Center);
      Text: "0";
    }
  }

  // DmgRow1, DmgRow2, DmgRow3 -- identiques avec suffixes 1, 2, 3
}
```

Le design suit les conventions existantes du HUD Purge :
- Meme fond sombre semi-transparent (`#0d1620`)
- Meme outline subtile
- Couleurs coherentes (noms en blanc, valeurs en orange)
- Pas d'underscores dans les IDs (regle CLAUDE.md)

---

## 4. Java HUD - `PurgeHud`

**Fichier** : `hyvexa-purge/.../hud/PurgeHud.java`

Ajouter le cache et la methode d'update pour le damage board :

```java
// Cache
private String[] lastDmgNames = new String[4];
private String[] lastDmgValues = new String[4];
private boolean dmgBoardVisible = false;

public void updateDamageBoard(String[] names, String[] values, int count) {
    UICommandBuilder cmd = new UICommandBuilder();
    boolean changed = false;

    if (!dmgBoardVisible) {
        dmgBoardVisible = true;
        cmd.set("#DmgBoard.Visible", true);
        changed = true;
    }

    for (int i = 0; i < 4; i++) {
        boolean rowVisible = i < count;
        String name = rowVisible ? names[i] : "";
        String value = rowVisible ? values[i] : "";

        if (!name.equals(lastDmgNames[i]) || !value.equals(lastDmgValues[i])) {
            lastDmgNames[i] = name;
            lastDmgValues[i] = value;
            cmd.set("#DmgRow" + i + ".Visible", rowVisible);
            if (rowVisible) {
                cmd.set("#DmgName" + i + ".Text", name);
                cmd.set("#DmgValue" + i + ".Text", value);
            }
            changed = true;
        }
    }

    if (changed) {
        update(false, cmd);
    }
}

public void hideDamageBoard() {
    if (dmgBoardVisible) {
        dmgBoardVisible = false;
        lastDmgNames = new String[4];
        lastDmgValues = new String[4];
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#DmgBoard.Visible", false);
        update(false, cmd);
    }
}
```

Ajouter le reset du cache dans `resetCache()`.

---

## 5. `PurgeHudManager` - Tick du scoreboard

**Fichier** : `hyvexa-purge/.../hud/PurgeHudManager.java`

Ajouter une methode `tickDamageBoard()` appelee depuis le slow tick existant (ou un tick dedie). Cette methode :

1. Pour chaque session active, collecte les `(playerName, totalDamage)` de tous les joueurs
2. Trie par damage decroissant
3. Envoie la mise a jour a chaque joueur de la session

```java
public void tickDamageBoard(PurgeSessionManager sessionManager) {
    // Pour chaque HUD actif, trouver la session du joueur
    for (var entry : purgeHuds.entrySet()) {
        UUID playerId = entry.getKey();
        PurgeSession session = sessionManager.getSessionByPlayer(playerId);
        if (session == null) continue;

        // Collecter et trier les damages
        List<Map.Entry<String, Long>> sorted = new ArrayList<>();
        for (UUID pid : session.getConnectedParticipants()) {
            PurgeSessionPlayerState ps = session.getPlayerState(pid);
            if (ps == null) continue;
            String name = PurgePlayerNameResolver.resolve(pid, SHORT);
            sorted.add(Map.entry(name, ps.getTotalDamageDealt()));
        }
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        int count = Math.min(sorted.size(), 4);
        String[] names = new String[count];
        String[] values = new String[count];
        for (int i = 0; i < count; i++) {
            names[i] = sorted.get(i).getKey();
            values[i] = formatDamage(sorted.get(i).getValue());
        }

        PurgeHud hud = entry.getValue();
        hud.updateDamageBoard(names, values, count);
    }
}

private String formatDamage(long damage) {
    if (damage >= 1_000_000) return String.format("%.1fM", damage / 1_000_000.0);
    if (damage >= 1_000) return String.format("%.1fK", damage / 1_000.0);
    return String.valueOf(damage);
}
```

**Frequence du tick** : reutiliser le slow tick existant (qui tourne deja toutes les ~1s via `tickSlowUpdates()`). Pas besoin d'un tick separe -- le damage board n'a pas besoin d'etre plus reactif que 1s.

**Optimisation** : le tick itere une fois par HUD mais recalcule les memes donnees de session. Pour eviter ca, on peut cacher le tri par session. Cependant, avec un max de 4 joueurs par session, c'est negligeable. On garde simple.

---

## 6. Integration dans le lifecycle de session

### Start session (`PurgeSessionManager.startSession()`)
- Rien a faire -- `PurgeSessionPlayerState` initialise `totalDamageDealt` a 0 automatiquement

### Show/Hide du board
- **Show** : dans `startSession()`, apres `hudManager.showRunHud(pid)`, le damage board sera rendu visible par le premier tick
- **Hide** : dans `hudManager.hideRunHud()`, ajouter `hud.hideDamageBoard()`

### End session summary
- Dans `PurgeSessionManager.leaveSession()` et `stopSessionById()`, ajouter le damage dealt au message summary :
```java
long dmg = playerState != null ? playerState.getTotalDamageDealt() : 0;
String summary = "Purge ended - Wave " + session.getCurrentWave()
    + " - " + kills + " kills"
    + " - " + formatDamage(dmg) + " damage"
    + " - " + summaryScrap + " scrap earned"
    + " (" + reason + ")";
```

---

## 7. Appel du tick damage board

**Fichier** : `hyvexa-purge/.../HyvexaPurgePlugin.java`

Le `tickSlowUpdates()` du HudManager est deja appele regulierement. Il faut ajouter l'appel a `tickDamageBoard()` dans le meme cycle. L'approche la plus simple est d'appeler `tickDamageBoard(sessionManager)` depuis `tickSlowUpdates()` directement, en passant le sessionManager en parametre ou en le stockant comme champ dans `PurgeHudManager`.

Alternative : appeler `tickDamageBoard()` depuis le meme scheduler qui appelle `tickSlowUpdates()` dans le plugin principal.

---

## Fichiers a modifier

| Fichier | Changement |
|---------|-----------|
| `PurgeSessionPlayerState.java` | Ajouter `AtomicLong totalDamageDealt` + accesseurs |
| `PurgeDamageModifierSystem.java` | Enregistrer le damage dans `applyPlayerDamageOverride()` |
| `Purge_RunHud.ui` | Ajouter le groupe `#DmgBoard` avec 4 lignes |
| `PurgeHud.java` | Ajouter `updateDamageBoard()`, `hideDamageBoard()`, reset cache |
| `PurgeHudManager.java` | Ajouter `tickDamageBoard()`, `formatDamage()`, appel dans le slow tick |
| `PurgeSessionManager.java` | Ajouter damage dealt au summary de fin de session |

## Non-modifie

| Fichier | Raison |
|---------|--------|
| `PurgePlayerStats.java` | Pas de persistence en DB (session only) |
| `PurgePlayerStore.java` | Pas de colonne damage en DB |
| Schema DB | Aucun changement |
