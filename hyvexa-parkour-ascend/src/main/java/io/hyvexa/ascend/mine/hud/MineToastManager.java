package io.hyvexa.ascend.mine.hud;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import io.hyvexa.ascend.mine.MineBlockDisplay;

public class MineToastManager {

    private static final int MAX_SLOTS = 4;
    private static final long TOAST_DURATION_MS = 3000;

    private final MineToastEntry[] slots = new MineToastEntry[MAX_SLOTS];

    public void onBlockMined(String blockTypeId, int count) {
        // Stack onto existing toast for same block type
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (slots[i] != null && slots[i].blockTypeId.equals(blockTypeId)) {
                slots[i].count += count;
                slots[i].createdAt = System.currentTimeMillis();
                return;
            }
        }

        // Find an empty slot
        int emptySlot = -1;
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (slots[i] == null) {
                emptySlot = i;
                break;
            }
        }

        // If no empty slot, recycle the oldest
        if (emptySlot < 0) {
            emptySlot = findOldestSlot();
        }

        slots[emptySlot] = new MineToastEntry(blockTypeId, count, System.currentTimeMillis());
    }

    public void update(UICommandBuilder cmd) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < MAX_SLOTS; i++) {
            MineToastEntry entry = slots[i];
            if (entry == null) {
                continue;
            }

            long elapsed = now - entry.createdAt;
            if (elapsed >= TOAST_DURATION_MS) {
                hideSlot(cmd, i);
                slots[i] = null;
                continue;
            }

            float progress = 1.0f - (float) elapsed / TOAST_DURATION_MS;
            showSlot(cmd, i, entry, progress);
        }
    }

    public boolean hasActiveToasts() {
        for (MineToastEntry slot : slots) {
            if (slot != null) {
                return true;
            }
        }
        return false;
    }

    public void clear() {
        for (int i = 0; i < MAX_SLOTS; i++) {
            slots[i] = null;
        }
    }

    private void showSlot(UICommandBuilder cmd, int slot, MineToastEntry entry, float progress) {
        String prefix = "#MineToast" + slot;
        cmd.set(prefix + ".Visible", true);
        cmd.set(prefix + "Icon.ItemId", entry.itemId);
        cmd.set(prefix + "Text.Text", entry.displayName + " x" + entry.count);
        cmd.set(prefix + "Bar.Value", progress);
    }

    private void hideSlot(UICommandBuilder cmd, int slot) {
        cmd.set("#MineToast" + slot + ".Visible", false);
    }

    private int findOldestSlot() {
        int oldest = 0;
        long oldestTime = Long.MAX_VALUE;
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (slots[i] != null && slots[i].createdAt < oldestTime) {
                oldestTime = slots[i].createdAt;
                oldest = i;
            }
        }
        return oldest;
    }

    private static class MineToastEntry {
        final String blockTypeId;
        final String itemId;
        final String displayName;
        int count;
        long createdAt;

        MineToastEntry(String blockTypeId, int count, long createdAt) {
            this.blockTypeId = blockTypeId;
            this.itemId = MineBlockDisplay.getItemId(blockTypeId);
            this.displayName = MineBlockDisplay.getDisplayName(blockTypeId);
            this.count = count;
            this.createdAt = createdAt;
        }
    }
}
