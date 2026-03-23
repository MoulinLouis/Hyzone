package io.hyvexa.runorfall.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.ModeGate;
import io.hyvexa.core.bridge.GameModeBridge;
import io.hyvexa.runorfall.manager.RunOrFallGameManager;
import io.hyvexa.runorfall.util.RunOrFallUtils;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class RunOrFallBlinkInteraction extends SimpleInteraction {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final double BLINK_STEP_BLOCKS = 0.20d;
    private static final double MIN_BLINK_DISTANCE_SQ = 0.0001d;
    private static final double BLINK_PLAYER_RADIUS = 0.35d;
    private static final double BLINK_DIAGONAL_RADIUS = BLINK_PLAYER_RADIUS * 0.7071067811865476d;
    private static final String SFX_BLINK_USED = "SFX_Skeleton_Mage_Spellbook_Impact";
    private static final double[] BLINK_XZ_OFFSETS = new double[] {
            0.0d, 0.0d,
            BLINK_PLAYER_RADIUS, 0.0d,
            -BLINK_PLAYER_RADIUS, 0.0d,
            0.0d, BLINK_PLAYER_RADIUS,
            0.0d, -BLINK_PLAYER_RADIUS,
            BLINK_DIAGONAL_RADIUS, BLINK_DIAGONAL_RADIUS,
            BLINK_DIAGONAL_RADIUS, -BLINK_DIAGONAL_RADIUS,
            -BLINK_DIAGONAL_RADIUS, BLINK_DIAGONAL_RADIUS,
            -BLINK_DIAGONAL_RADIUS, -BLINK_DIAGONAL_RADIUS
    };
    private static final double[] BLINK_HEIGHT_OFFSETS = new double[] {0.10d, 0.90d, 1.70d};

    public static final BuilderCodec<RunOrFallBlinkInteraction> CODEC =
            BuilderCodec.builder(RunOrFallBlinkInteraction.class, RunOrFallBlinkInteraction::new).build();

    @Override
    public void handle(@Nonnull Ref<EntityStore> ref, boolean firstRun, float time,
                       @Nonnull InteractionType type, @Nonnull InteractionContext interactionContext) {
        super.handle(ref, firstRun, time, type, interactionContext);
        RunOrFallInteractionBridge.Services services = RunOrFallInteractionBridge.get();
        RunOrFallGameManager gameManager = services != null ? services.gameManager() : null;
        if (services == null || gameManager == null) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData() != null ? store.getExternalData().getWorld() : null;
        if (!ModeGate.isRunOrFallWorld(world)) {
            invokeParkourRestartInteraction(ref, firstRun, time, type, interactionContext);
            return;
        }
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        UUID playerId = playerRef != null ? playerRef.getUuid() : null;
        if (playerId == null || !gameManager.isInActiveRound(playerId)) {
            return;
        }
        if (gameManager.getBlinkCharges(playerId) <= 0) {
            return;
        }
        int blinkDistanceBlocks = gameManager.getBlinkDistanceBlocks();

        CompletableFuture.runAsync(() -> performBlink(ref, store, playerRef, world, gameManager, blinkDistanceBlocks), world);
    }

    private void performBlink(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef,
                              World world, RunOrFallGameManager gameManager, int blinkDistanceBlocks) {
        if (!ref.isValid()) {
            return;
        }
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null || transform.getPosition() == null) {
            return;
        }

        Vector3d origin = transform.getPosition();
        Vector3f headRotation = playerRef.getHeadRotation();
        Vector3f bodyRotation = transform.getRotation();
        float pitch = headRotation != null ? headRotation.getX() : (bodyRotation != null ? bodyRotation.getX() : 0.0f);
        float yaw = headRotation != null ? headRotation.getY() : (bodyRotation != null ? bodyRotation.getY() : 0.0f);
        double cosPitch = Math.cos(pitch);
        double dirX = -Math.sin(yaw) * cosPitch;
        double dirY = Math.sin(pitch);
        double dirZ = -Math.cos(yaw) * cosPitch;
        double blinkDistance = Math.max(1.0d, blinkDistanceBlocks);

        double targetY = origin.getY() + (dirY * blinkDistance);
        if (targetY < origin.getY()) {
            targetY = origin.getY();
        }

        Vector3d target = new Vector3d(
                origin.getX() + (dirX * blinkDistance),
                targetY,
                origin.getZ() + (dirZ * blinkDistance)
        );
        Vector3d safeTarget = resolveSafeBlinkTarget(world, origin, target, gameManager);
        if (safeTarget == null || distanceSq(origin, safeTarget) <= MIN_BLINK_DISTANCE_SQ) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        if (!gameManager.tryConsumeBlinkCharge(playerId)) {
            return;
        }
        playSfx(playerRef, SFX_BLINK_USED);

        float roll = bodyRotation != null ? bodyRotation.getZ() : 0.0f;
        Vector3f rotation = new Vector3f(pitch, yaw, roll);
        store.addComponent(ref, Teleport.getComponentType(), new Teleport(world, safeTarget, rotation));
    }

    private Vector3d resolveSafeBlinkTarget(World world, Vector3d origin, Vector3d requestedTarget,
                                            RunOrFallGameManager gameManager) {
        double dx = requestedTarget.getX() - origin.getX();
        double dy = requestedTarget.getY() - origin.getY();
        double dz = requestedTarget.getZ() - origin.getZ();
        double distance = Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
        if (distance <= 0.000001d) {
            return origin;
        }

        double stepX = dx / distance;
        double stepY = dy / distance;
        double stepZ = dz / distance;
        Vector3d lastFree = origin;

        for (double traveled = BLINK_STEP_BLOCKS; traveled <= distance + 0.000001d; traveled += BLINK_STEP_BLOCKS) {
            double length = Math.min(traveled, distance);
            Vector3d probe = new Vector3d(
                    origin.getX() + (stepX * length),
                    origin.getY() + (stepY * length),
                    origin.getZ() + (stepZ * length)
            );
            if (isBlockedAt(world, probe, gameManager)) {
                break;
            }
            lastFree = probe;
        }
        return lastFree;
    }

    private boolean isBlockedAt(World world, Vector3d position, RunOrFallGameManager gameManager) {
        for (int i = 0; i < BLINK_XZ_OFFSETS.length; i += 2) {
            double sampleX = position.getX() + BLINK_XZ_OFFSETS[i];
            double sampleZ = position.getZ() + BLINK_XZ_OFFSETS[i + 1];
            int blockX = (int) Math.floor(sampleX);
            int blockZ = (int) Math.floor(sampleZ);
            for (double yOffset : BLINK_HEIGHT_OFFSETS) {
                int blockY = (int) Math.floor(position.getY() + yOffset);
                Integer blockId = RunOrFallUtils.readBlockId(world, blockX, blockY, blockZ);
                if (isSolid(blockId, gameManager, blockX, blockY, blockZ)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isSolid(Integer blockId, RunOrFallGameManager gameManager, int blockX, int blockY, int blockZ) {
        if (blockId == null) {
            return true;
        }
        if (blockId == RunOrFallUtils.AIR_BLOCK_ID) {
            return false;
        }
        if (gameManager.canBlinkPassThrough(blockX, blockY, blockZ, blockId)) {
            return false;
        }
        return true;
    }

    private static double distanceSq(Vector3d a, Vector3d b) {
        double dx = b.getX() - a.getX();
        double dy = b.getY() - a.getY();
        double dz = b.getZ() - a.getZ();
        return (dx * dx) + (dy * dy) + (dz * dz);
    }

    private void playSfx(PlayerRef playerRef, String soundEventId) {
        int soundIndex = SoundEvent.getAssetMap().getIndex(soundEventId);
        if (soundIndex <= SoundEvent.EMPTY_ID) {
            return;
        }
        SoundUtil.playSoundEvent2dToPlayer(playerRef, soundIndex, com.hypixel.hytale.protocol.SoundCategory.SFX);
    }

    /**
     * Falls back to parkour's RestartCheckpointInteraction when the blink item is used
     * outside the RunOrFall world. Delegates via GameModeBridge (registered by parkour on startup).
     */
    private void invokeParkourRestartInteraction(Ref<EntityStore> ref, boolean firstRun, float time,
                                                 InteractionType type, InteractionContext interactionContext) {
        if (!GameModeBridge.invoke(GameModeBridge.PARKOUR_RESTART_CHECKPOINT,
                ref, firstRun, time, type, interactionContext)) {
            LOGGER.atWarning().log("Failed to route Ingredient_Lightning_Essence to Parkour restart: handler not registered.");
        }
    }
}
