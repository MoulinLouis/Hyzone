package io.hyvexa.ascend.hud;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import io.hyvexa.ascend.AscendConstants;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class HudEffectManager {

    private final Map<String, HudEffect> activeEffects = new HashMap<>();

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

    public void update(UICommandBuilder builder) {
        Iterator<Map.Entry<String, HudEffect>> iterator = activeEffects.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, HudEffect> entry = iterator.next();
            HudEffect effect = entry.getValue();

            if (!effect.isApplied()) {
                builder.set(effect.getElementId() + ".Style.TextColor", effect.getFlashColor());
                effect.markAsApplied();
            } else {
                builder.set(effect.getElementId() + ".Style.TextColor", effect.getOriginalColor());
                iterator.remove();
            }
        }
    }

    public boolean hasActiveEffects() {
        return !activeEffects.isEmpty();
    }

    public void clearEffects() {
        activeEffects.clear();
    }
}
