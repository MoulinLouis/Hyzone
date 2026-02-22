package io.hyvexa.ascend.hud;

public enum ToastType {
    SUCCESS("Success"),
    EVOLUTION("Evolution"),
    BATCH("Batch"),
    ECONOMY("Economy"),
    ERROR("Error"),
    INFO("Info");

    private final String suffix;

    ToastType(String suffix) {
        this.suffix = suffix;
    }

    public String getSuffix() {
        return suffix;
    }
}
