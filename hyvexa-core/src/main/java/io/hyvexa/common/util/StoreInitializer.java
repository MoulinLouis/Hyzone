package io.hyvexa.common.util;

import com.hypixel.hytale.logger.HytaleLogger;

public final class StoreInitializer {

    private StoreInitializer() {
    }

    public static void initialize(HytaleLogger logger, Runnable... initializers) {
        for (Runnable init : initializers) {
            try {
                init.run();
            } catch (Exception e) {
                logger.atWarning().withCause(e).log("Store initialization failed");
            }
        }
    }
}
