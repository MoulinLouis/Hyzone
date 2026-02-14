package io.hyvexa.ascend.hud;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;

public class ToastManager {

    private static final int MAX_SLOTS = 4;
    private static final long TOAST_DURATION_MS = 5000;
    private static final long CONSOLIDATION_WINDOW_MS = 1000;

    private final ToastEntry[] slots = new ToastEntry[MAX_SLOTS];

    public void showToast(ToastType type, String message) {
        // Consolidation: if newest toast has same type and is recent, update its text
        ToastEntry newest = findNewestEntry();
        if (newest != null && newest.type == type
                && (System.currentTimeMillis() - newest.createdAt) < CONSOLIDATION_WINDOW_MS) {
            newest.message = message;
            newest.createdAt = System.currentTimeMillis();
            newest.dirty = true;
            return;
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

        slots[emptySlot] = new ToastEntry(type, message, System.currentTimeMillis());
    }

    public void update(UICommandBuilder cmd) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < MAX_SLOTS; i++) {
            ToastEntry entry = slots[i];
            if (entry == null) {
                continue;
            }

            long elapsed = now - entry.createdAt;
            if (elapsed >= TOAST_DURATION_MS) {
                // Toast expired — hide it
                hideSlot(cmd, i);
                slots[i] = null;
                continue;
            }

            // Show and update this slot
            float progress = 1.0f - (float) elapsed / TOAST_DURATION_MS;
            showSlot(cmd, i, entry, progress);
            entry.dirty = false;
        }
    }

    public boolean hasActiveToasts() {
        for (ToastEntry slot : slots) {
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

    private void showSlot(UICommandBuilder cmd, int slot, ToastEntry entry, float progress) {
        String prefix = "#Toast" + slot;
        cmd.set(prefix + ".Visible", true);
        cmd.set(prefix + "Text.Text", entry.message);

        // Toggle accent bar overlays — show only the matching type
        for (ToastType t : ToastType.values()) {
            boolean match = t == entry.type;
            cmd.set(prefix + "Accent" + t.getSuffix() + ".Visible", match);
            cmd.set(prefix + "Bar" + t.getSuffix() + ".Visible", match);
            if (match) {
                cmd.set(prefix + "Bar" + t.getSuffix() + ".Value", progress);
            }
        }
    }

    private void hideSlot(UICommandBuilder cmd, int slot) {
        String prefix = "#Toast" + slot;
        cmd.set(prefix + ".Visible", false);
    }

    private ToastEntry findNewestEntry() {
        ToastEntry newest = null;
        for (ToastEntry slot : slots) {
            if (slot != null && (newest == null || slot.createdAt > newest.createdAt)) {
                newest = slot;
            }
        }
        return newest;
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

    private static class ToastEntry {
        ToastType type;
        String message;
        long createdAt;
        boolean dirty;

        ToastEntry(ToastType type, String message, long createdAt) {
            this.type = type;
            this.message = message;
            this.createdAt = createdAt;
            this.dirty = true;
        }
    }
}
