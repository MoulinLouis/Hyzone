package io.hyvexa.ascend.mine.hud;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;

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
                slots[i].dirty = true;
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
            entry.dirty = false;
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
        boolean dirty;

        MineToastEntry(String blockTypeId, int count, long createdAt) {
            this.blockTypeId = blockTypeId;
            this.itemId = stripNamespace(blockTypeId);
            this.displayName = formatBlockName(blockTypeId);
            this.count = count;
            this.createdAt = createdAt;
            this.dirty = true;
        }
    }

    static String stripNamespace(String blockTypeId) {
        if (blockTypeId == null) return "";
        int colonIndex = blockTypeId.indexOf(':');
        return colonIndex >= 0 ? blockTypeId.substring(colonIndex + 1) : blockTypeId;
    }

    static String formatBlockName(String blockTypeId) {
        if (blockTypeId == null || blockTypeId.isEmpty()) return "Unknown";
        String name = stripNamespace(blockTypeId);
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(' ');
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)));
                if (parts[i].length() > 1) sb.append(parts[i].substring(1));
            }
        }
        return sb.toString();
    }
}
