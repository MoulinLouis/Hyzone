package io.hyvexa.ascend.hud;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import io.hyvexa.ascend.AscendConstants;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class HudEffectManager {

    private final Map<String, HudEffect> activeEffects = new HashMap<>();

    /**
     * Trigger a multiplier flash effect for a specific element.
     * If an effect is already active for this element, it will be restarted.
     *
     * @param elementId   The UI element ID (e.g., "#TopRedValue")
     * @param slotIndex   The multiplier slot index (0-4) to get the correct colors
     */
    public void triggerMultiplierEffect(String elementId, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= AscendConstants.MULTIPLIER_COLORS.length) {
            return;
        }

        String[] colors = AscendConstants.MULTIPLIER_COLORS[slotIndex];
        String originalColor = colors[0];
        String flashColor = colors[1];

        HudEffect effect = new HudEffect(elementId, originalColor, flashColor);
        activeEffects.put(elementId, effect);
    }

    /**
     * Update all active effects and apply color changes to the UI.
     * Simple 2-phase system:
     * - First update: Show flash color
     * - Second update: Restore original color and remove effect
     *
     * @param builder The UI command builder to apply color changes to
     */
    public void update(UICommandBuilder builder) {
        Iterator<Map.Entry<String, HudEffect>> iterator = activeEffects.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, HudEffect> entry = iterator.next();
            HudEffect effect = entry.getValue();

            if (!effect.isApplied()) {
                // First update: Apply flash color
                builder.set(effect.getElementId() + ".Style.TextColor", effect.getFlashColor());
                effect.markAsApplied();
            } else {
                // Second update: Restore original color and remove
                builder.set(effect.getElementId() + ".Style.TextColor", effect.getOriginalColor());
                iterator.remove();
            }
        }
    }

    /**
     * Check if there are any active effects.
     */
    public boolean hasActiveEffects() {
        return !activeEffects.isEmpty();
    }

    /**
     * Clear all active effects.
     */
    public void clearEffects() {
        activeEffects.clear();
    }
}
