# Tebex Integration

Integration between our in-game shop and Tebex for real-money purchases.

## Architecture

```
Player clicks "Buy" in /shop UI
        |
        v
Plugin creates checkout link (POST https://plugin.tebex.io/checkout)
        |
        v
Player receives clickable link in chat -> opens Tebex payment page
        |
        v
Player pays on Tebex
        |
        v
Tebex executes server command (e.g. "pk admin vexa give {username} 1000")
        |
        v
Player receives vexa/rank/item in-game
```

## Configuration

### Server-side secret key

File: `run/mods/Parkour/tebex.json` (gitignored, server-only)

```json
{
  "secretKey": "your-tebex-game-server-secret-key"
}
```

- Loaded at plugin startup via `TebexConfig.load()`
- Class: `hyvexa-core/src/main/java/io/hyvexa/core/tebex/TebexConfig.java`
- Auto-creates with empty key on first run

### Where to find the secret key

Tebex dashboard > Game Servers > your server > Secret Key

## Tebex Checkout API

### Create checkout link

```
POST https://plugin.tebex.io/checkout
Header: X-Tebex-Secret: <secretKey>
Content-Type: application/json

Body:
{
  "package_id": "7236545",
  "username": "PlayerName"
}

Response (HTTP 201):
{
  "url": "https://store.hyvexa.com/checkout/...",
  "expires": "2026-02-24T18:31:23+00:00"
}
```

- `package_id`: string, the Tebex package ID (found in Tebex dashboard > Packages)
- `username`: string, the player's in-game username
- `url`: clickable checkout link, pre-filled with package and player
- `expires`: ISO 8601 timestamp, link valid for ~15 minutes

## Server Commands (executed by Tebex after purchase)

Configure these in Tebex dashboard > Packages > [package] > Game Servers > Commands.

### Give vexa

```
pk admin vexa give {username} <amount>
```

- Resolves player by name or UUID (works online and offline)
- Console-only (no OP check) — Tebex runs as console
- In-game requires OP
- Class: `ParkourCommand.handleAdminVexa()` in `hyvexa-parkour`

### Give rank

```
pk admin rank give {username} vip
pk admin rank give {username} founder
pk admin rank broadcast {username} vip
pk admin rank broadcast {username} founder
```

- `give`: sets the rank in DB
- `broadcast`: sends celebration message to all players
- Typically chain both: give first, then broadcast

### Tebex variables

- `{username}` — player's in-game name (filled by Tebex)
- `{id}` — player's UUID if available

## Existing Code

| File | Purpose |
|------|---------|
| `hyvexa-core/.../tebex/TebexConfig.java` | Loads secret key from `tebex.json` |
| `hyvexa-parkour/.../command/TebexTestCommand.java` | Test command `/tebextest <package_id>` — creates checkout link and sends it to player |
| `hyvexa-parkour/.../command/ParkourCommand.java` | Admin commands (`pk admin vexa give`, `pk admin rank give/broadcast`) |

## Implementing the Shop -> Tebex Checkout Flow

### Goal

Player opens `/shop` > clicks a paid product > gets a checkout link in chat > pays on Tebex > receives items.

### Steps to implement

1. **Define packages on Tebex dashboard**
   - Create packages for each paid product (vexa packs, ranks, etc.)
   - Note each package's ID
   - Set the post-purchase command (e.g., `pk admin vexa give {username} 500`)

2. **Create a checkout service class** (in `hyvexa-core`)
   - Singleton `TebexCheckoutService` that holds the `HttpClient` and `TebexConfig`
   - Method: `createCheckout(String packageId, String username)` returns `CompletableFuture<CheckoutResult>`
   - `CheckoutResult`: url + expires (or error)
   - Runs HTTP on daemon thread, never blocks world thread
   - Reference: `TebexTestCommand.java` already has the working HTTP call pattern

3. **Map products to Tebex package IDs**
   - Option A: hardcode in a `TebexPackages` constants class (simplest)
   - Option B: add to `tebex.json` as a map (e.g., `"packages": {"vexa_500": "7236545", ...}`)

4. **Add "Buy" button handler in ShopTab**
   - When player clicks Buy on a paid product:
     1. Call `TebexCheckoutService.createCheckout(packageId, username)`
     2. On success: send clickable link message to player (same pattern as TebexTestCommand)
     3. On failure: show error message
   - The shop UI stays open, player clicks the link in chat

5. **Vexa Packs tab already has UI cards**
   - `Shop_VexaPacks.ui` has 3 cards: 500/4.99, 1150/9.99, 2500/19.99
   - Wire each card's button to create a checkout link with the right package ID
   - Tebex command for each: `pk admin vexa give {username} <amount>`

### Chat message format (already working)

```java
player.sendMessage(Message.raw("[Store] Checkout link ready!").color("#FFD700"));
player.sendMessage(Message.join(
    Message.raw("[Store] "),
    Message.raw("Click here to open").color("#55FFFF").link(url)
));
player.sendMessage(Message.raw("[Store] Expires in " + minutes + " min").color("#AAAAAA"));
```

### Thread safety pattern

```java
// On world thread (from ShopTab.handleEvent):
// 1. Get username
// 2. Fire HTTP on daemon thread
Thread httpThread = new Thread(() -> {
    // HTTP call to Tebex API
    HttpResult result = ...;

    // Send result back on world thread
    CompletableFuture.runAsync(() -> {
        player.sendMessage(...);
    }, world);
}, "tebex-checkout");
httpThread.setDaemon(true);
httpThread.start();
```

## Security Notes

- Secret key is **server-side only** (`tebex.json` in gitignored `run/mods/`)
- Checkout links are per-player, per-package, time-limited (~15 min)
- Post-purchase commands run as console (trusted, no OP bypass needed)
- Admin commands (`pk admin vexa/rank`) require OP when run by a player
- Never expose the secret key to clients or logs
