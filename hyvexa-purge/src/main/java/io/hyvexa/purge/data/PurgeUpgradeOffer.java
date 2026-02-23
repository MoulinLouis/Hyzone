package io.hyvexa.purge.data;

public record PurgeUpgradeOffer(PurgeUpgradeType type, PurgeUpgradeRarity rarity) {

    public int value() {
        return type.getBaseValue() * rarity.getMultiplier();
    }
}
