package io.hyvexa.purge.data;

public record PurgeUpgradeOffer(PurgeUpgradeType type, PurgeUpgradeRarity rarity, int value) {

    public PurgeUpgradeOffer(PurgeUpgradeType type, PurgeUpgradeRarity rarity) {
        this(type, rarity, type.getBaseValue() * rarity.getMultiplier());
    }
}
