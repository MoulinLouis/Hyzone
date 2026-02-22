package io.hyvexa.purge.data;

public class PurgeSkinDefinition {

    private final String weaponId;
    private final String skinId;
    private final String displayName;
    private final int price;
    private final String itemId;

    public PurgeSkinDefinition(String weaponId, String skinId, String displayName, int price) {
        this.weaponId = weaponId;
        this.skinId = skinId;
        this.displayName = displayName;
        this.price = price;
        this.itemId = weaponId + "_" + skinId;
    }

    public String getWeaponId() { return weaponId; }
    public String getSkinId() { return skinId; }
    public String getDisplayName() { return displayName; }
    public int getPrice() { return price; }
    public String getItemId() { return itemId; }
}
