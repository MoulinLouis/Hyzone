package io.hyvexa.ascend.hud;

public class HudEffect {

    private final String elementId;
    private final String originalColor;
    private final String flashColor;
    private boolean applied; // Has the flash been shown?

    public HudEffect(String elementId, String originalColor, String flashColor) {
        this.elementId = elementId;
        this.originalColor = originalColor;
        this.flashColor = flashColor;
        this.applied = false;
    }

    public String getElementId() {
        return elementId;
    }

    public String getOriginalColor() {
        return originalColor;
    }

    public String getFlashColor() {
        return flashColor;
    }

    public boolean isApplied() {
        return applied;
    }

    public void markAsApplied() {
        this.applied = true;
    }
}
