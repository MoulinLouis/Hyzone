package io.hyvexa.core;

import java.util.function.Supplier;

/**
 * Thread-safe holder for shared singleton instances (stores, managers).
 *
 * <p>With bridge classloader delegation, all plugins share one copy of
 * hyvexa-core classes (and their static fields). {@link #register(Object)}
 * enforces single initialization; {@link #getOrCreate(Supplier)} is for
 * optional dependencies that may not yet exist when the consumer loads.
 *
 * <p>Usage:
 * <pre>
 * private static final SharedInstance&lt;MyStore&gt; SHARED = new SharedInstance&lt;&gt;("MyStore");
 *
 * public static MyStore createAndRegister(ConnectionProvider db) {
 *     var store = new MyStore(db);
 *     store.initialize();
 *     return SHARED.register(store);
 * }
 * public static MyStore get()  { return SHARED.get(); }
 * public static void destroy() { SHARED.destroy(); }
 * </pre>
 */
public final class SharedInstance<T> {

    private final String name;
    private volatile T instance;

    public SharedInstance(String name) {
        this.name = name;
    }

    /**
     * Register the shared instance. Throws if already registered (double-init is a bug).
     * Thread-safe: synchronized to prevent check-then-act races.
     */
    public synchronized T register(T value) {
        if (instance != null) {
            throw new IllegalStateException(name + " already initialized");
        }
        instance = value;
        return value;
    }

    /**
     * Check whether the shared instance has been registered.
     * Use for optional dependencies that may not be loaded yet.
     */
    public boolean isInitialized() {
        return instance != null;
    }

    /**
     * Get the shared instance. Throws if not yet registered.
     */
    public T get() {
        T ref = instance;
        if (ref == null) {
            throw new IllegalStateException(name + " not yet initialized — check plugin load order");
        }
        return ref;
    }

    /**
     * Get the shared instance, creating it via the factory if not yet registered.
     * Thread-safe: only one thread will invoke the factory.
     * Use for optional dependencies (e.g. PurgeSkinStore).
     */
    public T getOrCreate(Supplier<T> factory) {
        T ref = instance; // volatile read — fast path
        if (ref != null) {
            return ref;
        }
        synchronized (this) {
            if (instance == null) {
                instance = factory.get();
            }
            return instance;
        }
    }

    /**
     * Clear the shared instance (call during shutdown for clean hot-reload).
     */
    public synchronized void destroy() {
        instance = null;
    }
}
