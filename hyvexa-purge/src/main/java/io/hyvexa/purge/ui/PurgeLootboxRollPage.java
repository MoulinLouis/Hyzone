package io.hyvexa.purge.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.purge.HyvexaPurgePlugin;
import io.hyvexa.purge.data.PurgeSessionPlayerState;
import io.hyvexa.purge.data.PurgeWeaponUpgradeStore;
import io.hyvexa.purge.manager.PurgeWeaponConfigManager;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class PurgeLootboxRollPage extends InteractiveCustomUIPage<PurgeLootboxRollPage.LootboxEventData> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String BUTTON_ACCEPT = "Accept";
    private static final String BUTTON_DECLINE = "Decline";
    private static final int SPIN_STEPS = 30;
    private static final long SPIN_BASE_DELAY_MS = 60;
    private static final int TIMEOUT_SECONDS = 10;
    private static final List<String> ICON_WEAPON_IDS = List.of(
            "AK47", "Barret50", "ColtRevolver", "DesertEagle", "DoubleBarrel",
            "Flamethrower", "Glock18", "M4A1s", "MP9", "Mac10", "Thompson"
    );

    private final UUID playerId;
    private final PurgeSessionPlayerState playerState;
    private final String rolledWeaponId;
    private final List<String> candidateWeapons;

    private volatile ScheduledFuture<?> spinTask;
    private volatile ScheduledFuture<?> timeoutTask;
    private volatile World world;
    private final AtomicBoolean resolved = new AtomicBoolean(false);

    public PurgeLootboxRollPage(@Nonnull PlayerRef playerRef,
                                UUID playerId,
                                PurgeSessionPlayerState playerState,
                                String rolledWeaponId,
                                List<String> candidateWeapons) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, LootboxEventData.CODEC);
        this.playerId = playerId;
        this.playerState = playerState;
        this.rolledWeaponId = rolledWeaponId;
        this.candidateWeapons = candidateWeapons;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder,
                      @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Purge_LootboxRoll.ui");
        this.world = store.getExternalData().getWorld();

        // Hide buttons until spin completes
        uiCommandBuilder.set("#AcceptButton.Visible", false);
        uiCommandBuilder.set("#DeclineButton.Visible", true);
        uiCommandBuilder.set("#TimerLabel.Text", TIMEOUT_SECONDS + "s remaining");

        // Bind buttons
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#AcceptButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_ACCEPT), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#DeclineButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_DECLINE), false);

        // Start spin animation
        startSpinAnimation();

        // Start timeout countdown
        startTimeout();
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull LootboxEventData data) {
        super.handleDataEvent(ref, store, data);
        String button = data.getButton();
        if (button == null) {
            return;
        }

        if (BUTTON_ACCEPT.equals(button)) {
            handleAccept(ref, store);
        } else if (BUTTON_DECLINE.equals(button)) {
            handleDecline(ref, store);
        }
    }

    private void handleAccept(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!resolved.compareAndSet(false, true)) {
            return;
        }
        cancelTasks();

        HyvexaPurgePlugin plugin = HyvexaPurgePlugin.getInstance();
        if (plugin != null && ref.isValid()) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                plugin.switchWeapon(player, playerState, rolledWeaponId);
                PurgeWeaponConfigManager config = plugin.getWeaponConfigManager();
                String displayName = config.getDisplayName(rolledWeaponId);
                int level = PurgeWeaponUpgradeStore.getInstance().getLevel(playerId, rolledWeaponId);
                int effectiveLevel = Math.max(level, 1);
                int dmg = config.getDamage(rolledWeaponId, effectiveLevel);
                player.sendMessage(Message.raw("Weapon switched to " + displayName + " (" + dmg + " dmg)!"));
            }
        }
        close();
    }

    private void handleDecline(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!resolved.compareAndSet(false, true)) {
            return;
        }
        cancelTasks();
        close();
    }

    private void startSpinAnimation() {
        AtomicInteger step = new AtomicInteger(0);

        spinTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(() -> {
            try {
                int currentStep = step.getAndIncrement();
                if (currentStep >= SPIN_STEPS) {
                    // Final: show the rolled weapon
                    ScheduledFuture<?> task = spinTask;
                    if (task != null) {
                        task.cancel(false);
                        spinTask = null;
                    }
                    showFinalResult();
                    return;
                }

                // Pick a random weapon to display during spin
                String displayWeapon;
                if (currentStep >= SPIN_STEPS - 3) {
                    displayWeapon = rolledWeaponId;
                } else {
                    displayWeapon = candidateWeapons.get(
                            ThreadLocalRandom.current().nextInt(candidateWeapons.size()));
                }

                HyvexaPurgePlugin plugin = HyvexaPurgePlugin.getInstance();
                String displayName = plugin != null
                        ? plugin.getWeaponConfigManager().getDisplayName(displayWeapon)
                        : displayWeapon;

                UICommandBuilder cmd = new UICommandBuilder();
                updateWeaponIcon(cmd, displayWeapon);
                cmd.set("#WeaponName.Text", displayName);
                this.sendUpdate(cmd, new UIEventBuilder(), false);
            } catch (Exception e) {
                LOGGER.atFine().log("Spin animation error: " + e.getMessage());
            }
        }, 0, SPIN_BASE_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void showFinalResult() {
        HyvexaPurgePlugin plugin = HyvexaPurgePlugin.getInstance();
        PurgeWeaponConfigManager config = plugin != null ? plugin.getWeaponConfigManager() : null;

        String displayName = config != null ? config.getDisplayName(rolledWeaponId) : rolledWeaponId;
        int level = PurgeWeaponUpgradeStore.getInstance().getLevel(playerId, rolledWeaponId);
        int effectiveLevel = Math.max(level, 1);
        int dmg = config != null ? config.getDamage(rolledWeaponId, effectiveLevel) : 0;

        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();

        updateWeaponIcon(cmd, rolledWeaponId);
        cmd.set("#WeaponName.Text", displayName);
        cmd.set("#WeaponName.Style.TextColor", "#fbbf24");
        cmd.set("#DamageLabel.Text", dmg + " dmg");

        // Show star display
        int maxLevel = config != null ? config.getMaxLevel() : 10;
        updateStarDisplay(cmd, effectiveLevel);

        // Show Accept button
        cmd.set("#AcceptButton.Visible", true);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#AcceptButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_ACCEPT), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#DeclineButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_DECLINE), false);

        this.sendUpdate(cmd, evt, false);
    }

    private void updateWeaponIcon(UICommandBuilder cmd, String weaponId) {
        String normalized = ICON_WEAPON_IDS.contains(weaponId) ? weaponId : "AK47";
        for (String id : ICON_WEAPON_IDS) {
            cmd.set("#Icon" + id + ".Visible", false);
        }
        cmd.set("#Icon" + normalized + ".Visible", true);
    }

    private void updateStarDisplay(UICommandBuilder cmd, int level) {
        int fullStars = level / 2;
        boolean hasHalf = level % 2 == 1;
        for (int p = 0; p < 5; p++) {
            cmd.set("#S" + p + "F.Visible", p < fullStars);
            cmd.set("#S" + p + "H.Visible", p == fullStars && hasHalf);
            cmd.set("#S" + p + "E.Visible", p >= fullStars && !(p == fullStars && hasHalf));
        }
    }

    private void startTimeout() {
        AtomicInteger remaining = new AtomicInteger(TIMEOUT_SECONDS);
        timeoutTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(() -> {
            try {
                int secs = remaining.decrementAndGet();
                if (secs < 0) {
                    return;
                }
                UICommandBuilder cmd = new UICommandBuilder();
                cmd.set("#TimerLabel.Text", secs + "s remaining");
                this.sendUpdate(cmd, new UIEventBuilder(), false);
                if (secs == 0 && resolved.compareAndSet(false, true)) {
                    World w = world;
                    if (w != null) {
                        w.execute(() -> close());
                    }
                }
            } catch (Exception e) {
                LOGGER.atFine().log("Timeout tick error: " + e.getMessage());
            }
        }, 1000, 1000, TimeUnit.MILLISECONDS);
    }

    private void cancelTasks() {
        ScheduledFuture<?> spin = spinTask;
        if (spin != null) {
            spin.cancel(false);
            spinTask = null;
        }
        ScheduledFuture<?> timeout = timeoutTask;
        if (timeout != null) {
            timeout.cancel(false);
            timeoutTask = null;
        }
    }

    @Override
    public void close() {
        cancelTasks();
        super.close();
    }

    public static class LootboxEventData extends ButtonEventData {
        public static final BuilderCodec<LootboxEventData> CODEC =
                BuilderCodec.<LootboxEventData>builder(LootboxEventData.class, LootboxEventData::new)
                        .addField(new KeyedCodec<>(ButtonEventData.KEY_BUTTON, Codec.STRING),
                                (data, value) -> data.button = value, data -> data.button)
                        .build();

        private String button;

        @Override
        public String getButton() {
            return button;
        }
    }
}
