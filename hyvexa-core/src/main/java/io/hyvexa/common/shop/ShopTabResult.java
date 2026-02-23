package io.hyvexa.common.shop;

public final class ShopTabResult {

    public static final ShopTabResult NONE = new ShopTabResult(Type.NONE, null);
    public static final ShopTabResult REFRESH = new ShopTabResult(Type.REFRESH, null);

    private final Type type;
    private final String confirmKey;

    private ShopTabResult(Type type, String confirmKey) {
        this.type = type;
        this.confirmKey = confirmKey;
    }

    public static ShopTabResult showConfirm(String key) {
        return new ShopTabResult(Type.SHOW_CONFIRM, key);
    }

    public static ShopTabResult hideConfirm() {
        return new ShopTabResult(Type.HIDE_CONFIRM, null);
    }

    public Type getType() {
        return type;
    }

    public String getConfirmKey() {
        return confirmKey;
    }

    public enum Type {
        NONE,
        REFRESH,
        SHOW_CONFIRM,
        HIDE_CONFIRM
    }
}
