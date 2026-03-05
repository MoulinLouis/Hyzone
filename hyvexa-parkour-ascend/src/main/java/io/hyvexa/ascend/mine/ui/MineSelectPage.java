package io.hyvexa.ascend.mine.ui;

import java.util.List;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.mine.hud.MineHudManager;
import io.hyvexa.ascend.mine.data.Mine;
import io.hyvexa.ascend.mine.data.MineConfigStore;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.ui.BaseAscendPage;
import io.hyvexa.common.math.BigNumber;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.FormatUtils;

public class MineSelectPage extends BaseAscendPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_TELEPORT_PREFIX = "Teleport:";
    private static final String BUTTON_UNLOCK_PREFIX = "Unlock:";
    private static final String BUTTON_DISABLED_PREFIX = "Disabled:";

    private final PlayerRef playerRef;
    private final MinePlayerProgress mineProgress;

    public MineSelectPage(@Nonnull PlayerRef playerRef, MinePlayerProgress mineProgress) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.playerRef = playerRef;
        this.mineProgress = mineProgress;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/MineSelectPage.ui");

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);

        populateContent(commandBuilder, eventBuilder);
    }

    private void populateContent(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.set("#CrystalsValue.Text", FormatUtils.formatLong(mineProgress.getCrystals()));

        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) return;
        MineConfigStore configStore = plugin.getMineConfigStore();
        if (configStore == null) return;

        List<Mine> mines = configStore.listMinesSorted();
        long crystals = mineProgress.getCrystals();

        commandBuilder.clear("#MineEntries");

        int index = 0;
        for (Mine mine : mines) {
            commandBuilder.append("#MineEntries", "Pages/MineSelectEntry.ui");
            String sel = "#MineEntries[" + index + "]";

            String displayName = mine.getName() != null && !mine.getName().isBlank()
                ? mine.getName() : mine.getId();
            commandBuilder.set(sel + " #MineName.Text", displayName);

            MinePlayerProgress.MineProgress state = mineProgress.getMineState(mine.getId());
            boolean unlocked = state.isUnlocked();

            if (unlocked) {
                commandBuilder.set(sel + " #UnlockedGroup.Visible", true);
                commandBuilder.set(sel + " #LockedGroup.Visible", false);

                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    sel + " #TeleportButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TELEPORT_PREFIX + mine.getId()), false);
            } else {
                commandBuilder.set(sel + " #UnlockedGroup.Visible", false);
                commandBuilder.set(sel + " #LockedGroup.Visible", true);

                long cost = mine.getUnlockCost().toLong();
                commandBuilder.set(sel + " #CostLabel.Text", FormatUtils.formatLong(cost) + " crystals");

                if (crystals >= cost) {
                    commandBuilder.set(sel + " #UnlockWrap.Visible", true);
                    commandBuilder.set(sel + " #DisabledWrap.Visible", false);

                    eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                        sel + " #UnlockButton",
                        EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_UNLOCK_PREFIX + mine.getId()), false);
                } else {
                    commandBuilder.set(sel + " #UnlockWrap.Visible", false);
                    commandBuilder.set(sel + " #DisabledWrap.Visible", true);

                    eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                        sel + " #DisabledButton",
                        EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_DISABLED_PREFIX + mine.getId()), false);
                }
            }

            index++;
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ButtonEventData data) {
        super.handleDataEvent(ref, store, data);
        String button = data.getButton();
        if (button == null) return;

        if (BUTTON_CLOSE.equals(button)) {
            this.close();
            return;
        }

        if (button.startsWith(BUTTON_TELEPORT_PREFIX)) {
            String mineId = button.substring(BUTTON_TELEPORT_PREFIX.length());
            handleTeleport(ref, store, mineId);
            return;
        }

        if (button.startsWith(BUTTON_UNLOCK_PREFIX)) {
            String mineId = button.substring(BUTTON_UNLOCK_PREFIX.length());
            handleUnlock(ref, store, mineId);
            return;
        }

        if (button.startsWith(BUTTON_DISABLED_PREFIX)) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.sendMessage(Message.raw("Not enough crystals to unlock this mine."));
            }
        }
    }

    private void handleTeleport(Ref<EntityStore> ref, Store<EntityStore> store, String mineId) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) return;
        MineConfigStore configStore = plugin.getMineConfigStore();
        if (configStore == null) return;

        Mine mine = configStore.getMine(mineId);
        if (mine == null || !mine.hasSpawn()) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.sendMessage(Message.raw("This mine has no spawn point configured."));
            }
            return;
        }

        MinePlayerProgress.MineProgress state = mineProgress.getMineState(mineId);
        if (!state.isUnlocked()) return;

        this.close();

        World world = store.getExternalData().getWorld();
        if (world == null) return;

        Vector3d destPos = new Vector3d(mine.getSpawnX(), mine.getSpawnY(), mine.getSpawnZ());
        Vector3f destRot = new Vector3f(mine.getSpawnRotX(), mine.getSpawnRotY(), mine.getSpawnRotZ());
        world.execute(() -> {
            if (ref.isValid()) {
                store.addComponent(ref, Teleport.getComponentType(), new Teleport(world, destPos, destRot));
                // Swap HUD: Ascend -> Mine
                ParkourAscendPlugin p = ParkourAscendPlugin.getInstance();
                if (p != null) {
                    p.getHudManager().removePlayer(playerRef.getUuid());
                    MineHudManager mhm = p.getMineHudManager();
                    if (mhm != null) {
                        Player player = store.getComponent(ref, Player.getComponentType());
                        if (player != null) {
                            mhm.attachHud(playerRef, player);
                        }
                    }
                }
            }
        });
    }

    private void handleUnlock(Ref<EntityStore> ref, Store<EntityStore> store, String mineId) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) return;
        MineConfigStore configStore = plugin.getMineConfigStore();
        if (configStore == null) return;

        Mine mine = configStore.getMine(mineId);
        if (mine == null) return;

        boolean success = mineProgress.unlockMine(mineId, mine.getUnlockCost());

        Player player = store.getComponent(ref, Player.getComponentType());
        if (success) {
            MinePlayerStore mineStore = plugin.getMinePlayerStore();
            if (mineStore != null) {
                mineStore.markDirty(playerRef.getUuid());
            }

            if (player != null) {
                player.sendMessage(Message.raw("Unlocked " + mine.getName() + "!"));
            }

            sendRefresh();
        } else {
            if (player != null) {
                player.sendMessage(Message.raw("Not enough crystals to unlock this mine."));
            }
        }
    }

    private void sendRefresh() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        populateContent(commandBuilder, eventBuilder);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }
}
