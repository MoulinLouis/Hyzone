package io.hyvexa.common.skin;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public final class DailyShopRotation {

    private static final int MAX_SLOTS = 4;

    private DailyShopRotation() {}

    public static List<PurgeSkinDefinition> getRotation(UUID playerId) {
        List<PurgeSkinDefinition> all = PurgeSkinRegistry.getAllSkins();
        PurgeSkinStore skinStore = PurgeSkinStore.getInstance();

        // Filter out owned skins
        List<PurgeSkinDefinition> unowned = new ArrayList<>();
        for (PurgeSkinDefinition def : all) {
            if (!skinStore.ownsSkin(playerId, def.getWeaponId(), def.getSkinId())) {
                unowned.add(def);
            }
        }
        if (unowned.isEmpty()) {
            return List.of();
        }

        // Deterministic shuffle using player UUID + day
        long daysSinceEpoch = LocalDate.now(ZoneOffset.UTC).toEpochDay();
        long seed = playerId.getMostSignificantBits() ^ playerId.getLeastSignificantBits() ^ daysSinceEpoch;
        Collections.shuffle(unowned, new Random(seed));

        int count = Math.min(MAX_SLOTS, unowned.size());
        return unowned.subList(0, count);
    }

    public static long getSecondsUntilReset() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
        return java.time.Duration.between(now, midnight).getSeconds();
    }

    public static String formatTimeRemaining(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return hours + "h " + minutes + "m";
    }
}
