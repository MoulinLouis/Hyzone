package io.hyvexa.common.util;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Shared helper for orphaned NPC entity cleanup across server restarts.
 * Handles file-based UUID persistence (save on shutdown, load on startup)
 * and deferred entity removal via world-thread execution.
 *
 * <p>Usage: each manager (GhostNpcManager, RobotManager) creates an instance
 * and delegates file I/O and pending-removal processing to it.
 */
public class OrphanedEntityCleanup {

    private final HytaleLogger logger;
    private final Path uuidsFilePath;
    private final Set<UUID> orphanedUuids = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Ref<EntityStore>> pendingRemovals = new ConcurrentHashMap<>();
    private volatile boolean cleanupPending;

    public OrphanedEntityCleanup(HytaleLogger logger, Path uuidsFilePath) {
        this.logger = logger;
        this.uuidsFilePath = uuidsFilePath;
    }

    /** Load orphaned UUIDs from file. Call during startup. */
    public void loadOrphanedUuids() {
        if (!Files.exists(uuidsFilePath)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(uuidsFilePath);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                try {
                    orphanedUuids.add(UUID.fromString(trimmed));
                } catch (IllegalArgumentException ignored) {
                }
            }
            Files.delete(uuidsFilePath);
        } catch (IOException e) {
            logger.atWarning().log("Failed to load orphaned UUIDs from " + uuidsFilePath + ": " + e.getMessage());
        }
        cleanupPending = !orphanedUuids.isEmpty();
    }

    /** Save UUIDs to file for next-startup cleanup. Call during shutdown. */
    public void saveUuidsForCleanup(Set<UUID> activeEntityUuids) {
        Set<UUID> uuids = new java.util.HashSet<>(orphanedUuids);
        uuids.addAll(activeEntityUuids);
        if (uuids.isEmpty()) {
            try {
                Files.deleteIfExists(uuidsFilePath);
            } catch (IOException ignored) {
            }
            return;
        }
        try {
            Files.createDirectories(uuidsFilePath.getParent());
            List<String> lines = uuids.stream()
                    .map(UUID::toString)
                    .collect(Collectors.toList());
            Files.write(uuidsFilePath, lines);
        } catch (IOException e) {
            logger.atWarning().log("Failed to save orphaned UUIDs to " + uuidsFilePath + ": " + e.getMessage());
        }
    }

    /** Check if a UUID is in the orphaned set. */
    public boolean isOrphaned(UUID entityUuid) {
        return orphanedUuids.contains(entityUuid);
    }

    /** Add a UUID to the orphaned set. */
    public void addOrphan(UUID entityUuid) {
        if (entityUuid != null) {
            orphanedUuids.add(entityUuid);
            cleanupPending = true;
        }
    }

    /** Queue an orphaned entity for deferred removal. */
    public void queueForRemoval(UUID entityUuid, Ref<EntityStore> entityRef) {
        if (entityUuid == null || entityRef == null) {
            return;
        }
        pendingRemovals.putIfAbsent(entityUuid, entityRef);
        cleanupPending = true;
    }

    /** Check if the given UUID is pending removal. */
    public boolean isPendingRemoval(UUID entityUuid) {
        return pendingRemovals.containsKey(entityUuid);
    }

    /** Process pending removals. Call from tick() outside ECS processing. */
    public void processPendingRemovals() {
        if (pendingRemovals.isEmpty()) {
            return;
        }
        for (UUID entityUuid : List.copyOf(pendingRemovals.keySet())) {
            Ref<EntityStore> ref = pendingRemovals.remove(entityUuid);
            if (ref == null) {
                continue;
            }
            if (!ref.isValid()) {
                markCleaned(entityUuid);
                continue;
            }
            Store<EntityStore> store = ref.getStore();
            if (store == null) {
                markCleaned(entityUuid);
                continue;
            }
            World world = store.getExternalData().getWorld();
            if (world == null) {
                removeOrphanDirect(entityUuid, ref, store);
                continue;
            }
            world.execute(() -> removeOrphanOnWorldThread(entityUuid, ref));
        }
    }

    /** Mark an orphan as cleaned. */
    public void markCleaned(UUID entityUuid) {
        orphanedUuids.remove(entityUuid);
        pendingRemovals.remove(entityUuid);
        if (orphanedUuids.isEmpty() && pendingRemovals.isEmpty()) {
            cleanupPending = false;
        }
    }

    public boolean isCleanupPending() {
        return cleanupPending || !pendingRemovals.isEmpty();
    }

    private void removeOrphanOnWorldThread(UUID entityUuid, Ref<EntityStore> ref) {
        try {
            if (ref == null || !ref.isValid()) {
                markCleaned(entityUuid);
                return;
            }
            Store<EntityStore> store = ref.getStore();
            if (store == null) {
                markCleaned(entityUuid);
                return;
            }
            store.removeEntity(ref, RemoveReason.REMOVE);
            markCleaned(entityUuid);
        } catch (Exception e) {
            logger.atWarning().log("Failed to remove orphaned entity " + entityUuid + ": " + e.getMessage());
            if (ref != null && ref.isValid()) {
                pendingRemovals.put(entityUuid, ref);
                cleanupPending = true;
            } else {
                markCleaned(entityUuid);
            }
        }
    }

    private void removeOrphanDirect(UUID entityUuid, Ref<EntityStore> ref, Store<EntityStore> store) {
        try {
            store.removeEntity(ref, RemoveReason.REMOVE);
            markCleaned(entityUuid);
        } catch (Exception e) {
            logger.atWarning().log("Failed to remove orphaned entity " + entityUuid + ": " + e.getMessage());
            markCleaned(entityUuid);
        }
    }
}
