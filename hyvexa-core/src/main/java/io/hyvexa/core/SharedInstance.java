package io.hyvexa.core;

/**
 * Thread-safe holder for shared singleton instances (stores, managers).
 * Eliminates the boilerplate volatile field + createAndRegister/get/destroy
 * pattern that was previously copy-pasted into every shared store.
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
     * Register the shared instance. Throws if already registered (double-init).
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
     * Clear the shared instance (call during shutdown for clean hot-reload).
     */
    public synchronized void destroy() {
        instance = null;
    }
}
