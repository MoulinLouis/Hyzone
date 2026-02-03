# Dynamic Map Unlock UI Update - Design Document

**Date**: 2026-02-03
**Module**: hyvexa-parkour-ascend
**Status**: Approved

## Problem Statement

When a player upgrades their runner from level 2 to level 3 (0 stars) in Ascend mode, the next map is unlocked in the database and a success message is displayed. However, the UI does not refresh to show the newly unlocked map. The player must close and reopen the `/ascend` menu to see it.

## Current Behavior

1. Player upgrades runner level 2→3 in `handleRobotAction()` (line ~395)
2. System detects unlock condition (line ~400)
3. Database unlocks next map via `playerStore.checkAndUnlockEligibleMaps()`
4. Success message sent to player: "🎉 New map unlocked: [name]!"
5. **UI remains unchanged** - map not visible
6. Player must close/reopen menu to see the unlocked map

## Solution Design

### Approach: Event-Driven UI Update

Add the newly unlocked map to the UI immediately when the runner reaches level 3, without rebuilding the entire map list.

**Rejected Alternative**: Auto-refresh polling every second was considered but rejected because:
- Previously caused problems (that's why it's currently commented out)
- Unnecessary overhead - map unlock only occurs on level 2→3 upgrade
- Event-driven approach is more precise and efficient

### Implementation Details

**1. Modify `handleRobotAction()` (line ~400-415)**

After unlocking maps and sending the success message, add each unlocked map to the UI:

```java
if (newLevel == AscendConstants.MAP_UNLOCK_REQUIRED_RUNNER_LEVEL) {
    List<String> unlockedMapIds = playerStore.checkAndUnlockEligibleMaps(playerRef.getUuid(), mapStore);
    for (String unlockedMapId : unlockedMapIds) {
        AscendMap unlockedMap = mapStore.getMap(unlockedMapId);
        if (unlockedMap != null) {
            String mapName = unlockedMap.getName() != null && !unlockedMap.getName().isBlank()
                ? unlockedMap.getName()
                : unlockedMap.getId();
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.sendMessage(Message.raw("🎉 New map unlocked: " + mapName + "!"));
            }

            // NEW: Add map to UI immediately
            if (isCurrentPage() && ref.isValid()) {
                addMapToUI(ref, store, unlockedMap);
            }
        }
    }
    updateRobotRow(ref, store, mapId);
}
```

**2. New Method: `addMapToUI()`**

Create a helper method that adds a single map entry to the UI:

```java
private void addMapToUI(Ref<EntityStore> ref, Store<EntityStore> store, AscendMap map) {
    PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
    if (playerRef == null) {
        return;
    }

    UICommandBuilder commandBuilder = new UICommandBuilder();
    UIEventBuilder eventBuilder = new UIEventBuilder();

    // Use current lastMapCount as the index for the new map
    int index = lastMapCount;

    // Append new map card (reuse logic from buildMapList)
    commandBuilder.append("#MapCards", "Pages/Ascend_MapSelectEntry.ui");

    // Configure all visual elements (accent color, progress bar, stats, etc.)
    // ... (same logic as in buildMapList lines 174-260)

    // Increment map count
    lastMapCount++;

    // Send update to client
    if (isCurrentPage()) {
        sendUpdate(commandBuilder, eventBuilder, false);
    }
}
```

### Edge Cases Handled

1. **Multiple maps unlocked simultaneously**: Loop handles multiple `unlockedMapIds`
2. **Page closed**: Check `isCurrentPage()` before sending update
3. **Invalid reference**: Check `ref.isValid()` before operations
4. **Last map (map 5)**: No map to unlock, `unlockedMapIds` will be empty

### Runner System Clarification

- **Speed Level**: 0-20, upgraded with coins (+10% speed per level)
- **Stars**: 0-5, evolution after reaching level 20
- **Map Unlock Condition**: Runner at **0 stars** reaches **level 3**
  - After evolution, speed level resets to 0 (unless instant-evolution skill)
  - Only the first time a runner reaches level 3 unlocks the next map

## Testing

**Manual Test**:
1. Start with runner at level 2 (0 stars) on any map except map 5
2. Open `/ascend` menu
3. Click "Upgrade" to reach level 3
4. **Expected**: New map appears immediately in the list below
5. **Expected**: Success message displayed: "🎉 New map unlocked: [name]!"

## Files Modified

- `hyvexa-parkour-ascend/src/main/java/io/hyvexa/ascend/ui/AscendMapSelectPage.java`
  - Modify `handleRobotAction()` to call `addMapToUI()`
  - Add new method `addMapToUI()`
