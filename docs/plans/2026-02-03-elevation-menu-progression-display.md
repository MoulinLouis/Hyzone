# Elevation Menu - Multiplier Progression Display

**Date:** 2026-02-03
**Module:** hyvexa-parkour-ascend
**Status:** Approved

## Overview

Simplify the `/ascend elevate` menu by removing redundant coin display and focusing on progression toward the next multiplier tier.

## Problem

Currently, the elevation menu displays the player's coin balance, which is redundant since coins are already visible on the main HUD. This wastes valuable UI space that could better communicate progression information.

## Solution

Remove the coin balance display and replace it with clear, focused information about the cost to reach the next multiplier tier.

## Changes

### UI File (Ascend_Elevation.ui)

**Remove:** Entire `#CoinsRow` group (lines 29-43)
- Eliminates "Your Coins:" label and coin value display
- Frees up vertical space in the menu

**Keep:** `#ConversionRate` label (lines 45-49)
- Repurposed to show only the cost to next multiplier
- No structural changes to the UI element itself

### Java Code (ElevationPage.java)

**In `updateDisplay()` method:**

1. **Remove** coin display update (line 225):
   ```java
   commandBuilder.set("#CoinsValue.Text", FormatUtils.formatCoinsForHudDecimal(coins));
   ```

2. **Simplify** conversion rate text (lines 228-232):
   - Remove `"Next: "` prefix
   - Keep discount indicator for elevation cost reduction

   **Before:**
   ```java
   String costText = "Next: " + FormatUtils.formatCoinsForHud(nextCost) + " coins";
   ```

   **After:**
   ```java
   String costText = FormatUtils.formatCoinsForHud(nextCost) + " coins";
   ```

3. **Keep** discount logic:
   ```java
   if (costMultiplier < 1.0) {
       costText += " (-" + Math.round((1.0 - costMultiplier) * 100) + "%)";
   }
   ```

## Display Examples

### Without Discount
```
5,000 coins
```

### With 20% Elevation Cost Reduction (Skill Tree)
```
4,000 coins (-20%)
```

## Behavior

- **Player at x1 multiplier:** Shows cost to reach x2
- **Player at x5 multiplier:** Shows cost to reach x6
- Display updates every second (existing auto-refresh)
- Discount percentage shown if player has unlocked elevation cost reduction from ascension skill tree

## Notes

- The `coins` variable remains in the code for purchase calculations
- All purchase logic, button states, and multiplier displays remain unchanged
- Only the display logic for coin balance is removed
