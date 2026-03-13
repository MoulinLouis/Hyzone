package io.hyvexa.purge.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.CommandUtils;
import io.hyvexa.common.util.ModeGate;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.purge.data.PurgeClass;
import io.hyvexa.purge.data.PurgeClassStore;
import io.hyvexa.purge.data.PurgeParty;
import io.hyvexa.purge.data.PurgePlayerStats;
import io.hyvexa.purge.data.PurgePlayerStore;
import io.hyvexa.purge.data.PurgeScrapStore;
import io.hyvexa.purge.data.PurgeWeaponUpgradeStore;
import io.hyvexa.purge.manager.PurgeInstanceManager;
import io.hyvexa.purge.manager.PurgePartyManager;
import io.hyvexa.purge.manager.PurgeSessionManager;
import io.hyvexa.purge.manager.PurgeVariantConfigManager;
import io.hyvexa.purge.manager.PurgeWaveConfigManager;
import io.hyvexa.purge.manager.PurgeWeaponConfigManager;
import io.hyvexa.purge.ui.PurgeAdminIndexPage;
import io.hyvexa.purge.ui.PurgeSkinShopPage;
import io.hyvexa.purge.ui.PurgeWeaponSelectPage;
import io.hyvexa.purge.util.PurgePlayerNameResolver;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PurgeCommand extends AbstractAsyncCommand {

    private static final Message MESSAGE_OP_REQUIRED = Message.raw("You must be OP to use /purge admin.");
    private static final Message MESSAGE_PARTY_LEADER_START_REQUIRED =
            Message.raw("Only the leader can start. Any party member can invite players.");

    private final PurgeSessionManager sessionManager;
    private final PurgeWaveConfigManager waveConfigManager;
    private final PurgePartyManager partyManager;
    private final PurgeInstanceManager instanceManager;
    private final PurgeWeaponConfigManager weaponConfigManager;
    private final PurgeVariantConfigManager variantConfigManager;

    public PurgeCommand(PurgeSessionManager sessionManager,
                        PurgeWaveConfigManager waveConfigManager,
                        PurgePartyManager partyManager,
                        PurgeInstanceManager instanceManager,
                        PurgeWeaponConfigManager weaponConfigManager,
                        PurgeVariantConfigManager variantConfigManager) {
        super("purge", "Purge zombie survival commands");
        this.setPermissionGroup(GameMode.Adventure);
        this.setAllowsExtraArguments(true);
        this.sessionManager = sessionManager;
        this.waveConfigManager = waveConfigManager;
        this.partyManager = partyManager;
        this.instanceManager = instanceManager;
        this.weaponConfigManager = weaponConfigManager;
        this.variantConfigManager = variantConfigManager;
    }

    @Override
    @Nonnull
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        CommandSender sender = ctx.sender();
        if (!(sender instanceof Player player)) {
            ctx.sendMessage(Message.raw("This command can only be used by players."));
            return CompletableFuture.completedFuture(null);
        }
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            ctx.sendMessage(Message.raw("Player not in world."));
            return CompletableFuture.completedFuture(null);
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData() != null ? store.getExternalData().getWorld() : null;
        if (world == null) {
            ctx.sendMessage(Message.raw("Could not resolve your world."));
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> handleCommand(ctx, player, ref, store, world), world);
    }

    private void handleCommand(CommandContext ctx, Player player, Ref<EntityStore> ref,
                               Store<EntityStore> store, World world) {
        String[] args = CommandUtils.tokenize(ctx);
        if (args.length == 0) {
            player.sendMessage(Message.raw("Usage: /purge <start|stop|stats|party|upgrade|loadout|shop|skins|scrap|admin>"));
            return;
        }

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        UUID playerId = playerRef != null ? playerRef.getUuid() : null;
        if (playerId == null) {
            player.sendMessage(Message.raw("Could not identify player."));
            return;
        }

        String subcommand = args[0].toLowerCase();
        switch (subcommand) {
            case "start" -> handleStart(player, ref, world, playerId);
            case "stop" -> handleStop(player, playerId);
            case "stats" -> handleStats(player, playerId);
            case "party" -> handleParty(player, playerId, args);
            case "upgrade" -> handleUpgrade(player, ref, store, playerId, args);
            case "loadout" -> handleLoadout(player, ref, store, playerId);
            case "shop" -> handleShop(player, ref, store, playerId);
            case "skins" -> handleSkins(player, ref, store, playerId);
            case "class" -> handleClass(player, playerId, args);
            case "admin" -> openAdminMenu(player, ref, store);
            case "scrap" -> handleScrap(player, playerId, args);
            default -> player.sendMessage(Message.raw("Usage: /purge <start|stop|stats|party|upgrade|loadout|shop|skins|class|scrap|admin>"));
        }
    }

    private void openAdminMenu(Player player, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!PermissionUtils.isOp(player)) {
            player.sendMessage(MESSAGE_OP_REQUIRED);
            return;
        }
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
                new PurgeAdminIndexPage(playerRef, waveConfigManager, instanceManager, weaponConfigManager, variantConfigManager));
    }

    private void handleStart(Player player, Ref<EntityStore> ref, World world, UUID playerId) {
        if (!ModeGate.isPurgeWorld(world)) {
            player.sendMessage(Message.raw("You must be in the Purge world to start a session."));
            return;
        }
        PurgeParty party = partyManager.getPartyByPlayer(playerId);
        if (party != null && !party.isLeader(playerId)) {
            player.sendMessage(MESSAGE_PARTY_LEADER_START_REQUIRED);
            return;
        }
        sessionManager.startSession(playerId, ref);
    }

    private void handleStop(Player player, UUID playerId) {
        if (sessionManager.getSessionByPlayer(playerId) == null) {
            player.sendMessage(Message.raw("No active Purge session."));
            return;
        }
        sessionManager.leaveSession(playerId, "voluntary stop");
    }

    private void handleStats(Player player, UUID playerId) {
        PurgePlayerStats stats = PurgePlayerStore.getInstance().getOrCreate(playerId);
        long scrap = PurgeScrapStore.getInstance().getScrap(playerId);
        player.sendMessage(Message.raw("-- Purge Stats --"));
        player.sendMessage(Message.raw("Best wave: " + stats.getBestWave()));
        player.sendMessage(Message.raw("Total kills: " + stats.getTotalKills()));
        player.sendMessage(Message.raw("Total sessions: " + stats.getTotalSessions()));
        player.sendMessage(Message.raw("Scrap: " + scrap));
    }

    private void handleUpgrade(Player player, Ref<EntityStore> ref, Store<EntityStore> store, UUID playerId, String[] args) {
        if (args.length >= 2) {
            String action = args[1].toLowerCase();
            if (!"reset".equals(action)) {
                player.sendMessage(Message.raw("Usage: /purge upgrade [reset]"));
                return;
            }
            if (!PermissionUtils.isOp(player)) {
                player.sendMessage(Message.raw("You must be OP to use /purge upgrade reset."));
                return;
            }
            for (String weaponId : weaponConfigManager.getWeaponIds()) {
                PurgeWeaponUpgradeStore.getInstance().setLevel(playerId, weaponId, 0);
            }
            // Re-initialize defaults so default weapons are level 1
            PurgeWeaponUpgradeStore.getInstance().initializeDefaults(
                    playerId, weaponConfigManager.getDefaultWeaponIds());
            player.sendMessage(Message.raw("All weapon upgrades reset. Default weapons restored."));
            return;
        }

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
                new PurgeWeaponSelectPage(playerRef, PurgeWeaponSelectPage.Mode.PLAYER, playerId,
                        weaponConfigManager, null, null, null));
    }

    private void handleLoadout(Player player, Ref<EntityStore> ref, Store<EntityStore> store, UUID playerId) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
                new PurgeWeaponSelectPage(playerRef, PurgeWeaponSelectPage.Mode.LOADOUT, playerId,
                        weaponConfigManager, null, null, null));
    }

    private void handleShop(Player player, Ref<EntityStore> ref, Store<EntityStore> store, UUID playerId) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
                new PurgeWeaponSelectPage(playerRef, PurgeWeaponSelectPage.Mode.SHOP, playerId,
                        weaponConfigManager, null, null, null));
    }

    private void handleSkins(Player player, Ref<EntityStore> ref, Store<EntityStore> store, UUID playerId) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
                new PurgeSkinShopPage(playerRef, playerId));
    }

    private void handleScrap(Player player, UUID callerPlayerId, String[] args) {
        if (!PermissionUtils.isOp(player)) {
            player.sendMessage(Message.raw("You must be OP to use /purge scrap."));
            return;
        }
        if (args.length < 3) {
            player.sendMessage(Message.raw("Usage: /purge scrap <add|remove|set|check> <player> [amount]"));
            return;
        }
        String action = args[1].toLowerCase();
        String targetName = args[2];
        PlayerRef targetRef = findOnlineByName(targetName);
        if (targetRef == null) {
            player.sendMessage(Message.raw("Player not found: " + targetName));
            return;
        }
        UUID targetId = targetRef.getUuid();

        if ("check".equals(action)) {
            long scrap = PurgeScrapStore.getInstance().getScrap(targetId);
            player.sendMessage(Message.raw(targetName + " has " + scrap + " scrap."));
            return;
        }

        if (args.length < 4) {
            player.sendMessage(Message.raw("Usage: /purge scrap " + action + " <player> <amount>"));
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[3]);
        } catch (NumberFormatException e) {
            player.sendMessage(Message.raw("Invalid amount: " + args[3]));
            return;
        }
        if (amount < 0) {
            player.sendMessage(Message.raw("Amount must be positive."));
            return;
        }

        switch (action) {
            case "add" -> {
                PurgeScrapStore.getInstance().addScrap(targetId, amount);
                long newScrap = PurgeScrapStore.getInstance().getScrap(targetId);
                player.sendMessage(Message.raw("Added " + amount + " scrap to " + targetName + ". New total: " + newScrap));
            }
            case "remove" -> {
                PurgeScrapStore.getInstance().removeScrap(targetId, amount);
                long newScrap = PurgeScrapStore.getInstance().getScrap(targetId);
                player.sendMessage(Message.raw("Removed " + amount + " scrap from " + targetName + ". New total: " + newScrap));
            }
            case "set" -> {
                long current = PurgeScrapStore.getInstance().getScrap(targetId);
                if (amount > current) {
                    PurgeScrapStore.getInstance().addScrap(targetId, amount - current);
                } else if (amount < current) {
                    PurgeScrapStore.getInstance().removeScrap(targetId, current - amount);
                }
                player.sendMessage(Message.raw("Set " + targetName + " scrap to " + amount + "."));
            }
            default -> player.sendMessage(Message.raw("Usage: /purge scrap <add|remove|set|check> <player> [amount]"));
        }
    }

    private void handleClass(Player player, UUID playerId, String[] args) {
        if (args.length < 2) {
            showClassList(player, playerId);
            return;
        }
        String action = args[1].toLowerCase();
        switch (action) {
            case "select" -> handleClassSelect(player, playerId, args);
            case "unlock" -> handleClassUnlock(player, playerId, args);
            case "info" -> handleClassInfo(player, playerId, args);
            case "none" -> {
                if (sessionManager.hasActiveSession(playerId)) {
                    player.sendMessage(Message.raw("Cannot change class during an active session."));
                    return;
                }
                PurgeClassStore.getInstance().selectClass(playerId, null);
                player.sendMessage(Message.raw("Class deselected. You will play without a class."));
            }
            default -> player.sendMessage(Message.raw("Usage: /purge class [select|unlock|info|none]"));
        }
    }

    private void handleClassSelect(Player player, UUID playerId, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Message.raw("Usage: /purge class select <name>"));
            return;
        }
        PurgeClass target = PurgeClass.fromName(args[2]);
        if (target == null) {
            player.sendMessage(Message.raw("Unknown class: " + args[2] + ". Options: Scavenger, Tank, Assault, Medic"));
            return;
        }
        if (!PurgeClassStore.getInstance().isUnlocked(playerId, target)) {
            player.sendMessage(Message.raw(target.getDisplayName() + " is not unlocked. Use /purge class unlock " + target.getDisplayName()));
            return;
        }
        if (sessionManager.hasActiveSession(playerId)) {
            player.sendMessage(Message.raw("Cannot change class during an active session."));
            return;
        }
        PurgeClassStore.getInstance().selectClass(playerId, target);
        player.sendMessage(Message.raw("Class set to " + target.getDisplayName() + "."));
    }

    private void handleClassUnlock(Player player, UUID playerId, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Message.raw("Usage: /purge class unlock <name>"));
            return;
        }
        PurgeClass target = PurgeClass.fromName(args[2]);
        if (target == null) {
            player.sendMessage(Message.raw("Unknown class: " + args[2] + ". Options: Scavenger, Tank, Assault, Medic"));
            return;
        }
        PurgeClassStore.PurchaseResult result = PurgeClassStore.getInstance().purchaseClass(playerId, target);
        switch (result) {
            case SUCCESS -> player.sendMessage(Message.raw("Unlocked " + target.getDisplayName() + "! Use /purge class select " + target.getDisplayName() + " to equip it."));
            case ALREADY_UNLOCKED -> player.sendMessage(Message.raw(target.getDisplayName() + " is already unlocked."));
            case NOT_ENOUGH_SCRAP -> {
                long have = PurgeScrapStore.getInstance().getScrap(playerId);
                player.sendMessage(Message.raw("Not enough scrap. Need " + target.getUnlockCost() + ", have " + have + "."));
            }
        }
    }

    private void handleClassInfo(Player player, UUID playerId, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Message.raw("Usage: /purge class info <name>"));
            return;
        }
        PurgeClass target = PurgeClass.fromName(args[2]);
        if (target == null) {
            player.sendMessage(Message.raw("Unknown class: " + args[2] + ". Options: Scavenger, Tank, Assault, Medic"));
            return;
        }
        showClassInfo(player, playerId, target);
    }

    private void showClassList(Player player, UUID playerId) {
        PurgeClass selected = PurgeClassStore.getInstance().getSelectedClass(playerId);
        java.util.Set<PurgeClass> unlocked = PurgeClassStore.getInstance().getUnlockedClasses(playerId);
        long scrap = PurgeScrapStore.getInstance().getScrap(playerId);

        player.sendMessage(Message.raw("-- Purge Classes -- (Scrap: " + scrap + ")"));
        for (PurgeClass pc : PurgeClass.values()) {
            boolean owned = unlocked.contains(pc);
            boolean active = pc == selected;
            String status = active ? "[ACTIVE]" : owned ? "[UNLOCKED]" : "[" + pc.getUnlockCost() + " scrap]";
            player.sendMessage(Message.raw("  " + pc.getDisplayName() + " " + status));
        }
        player.sendMessage(Message.raw("Use /purge class info <name> for details."));
    }

    private void showClassInfo(Player player, UUID playerId, PurgeClass pc) {
        boolean owned = PurgeClassStore.getInstance().isUnlocked(playerId, pc);
        player.sendMessage(Message.raw("-- " + pc.getDisplayName() + " --"));
        switch (pc) {
            case SCAVENGER -> {
                player.sendMessage(Message.raw("Stat: +30% scrap earned per wave"));
                player.sendMessage(Message.raw("Perk: Kill streak scrap bonus (+5 scrap per streak kill)"));
            }
            case TANK -> {
                player.sendMessage(Message.raw("Stat: +40 max HP, -15% movement speed"));
                player.sendMessage(Message.raw("Perk: 20% damage reduction from zombies"));
            }
            case ASSAULT -> {
                player.sendMessage(Message.raw("Stat: +20% weapon damage, +10% movement speed"));
                player.sendMessage(Message.raw("Perk: Kill streak damage ramp (+5% per streak level, up to +45%)"));
            }
            case MEDIC -> {
                player.sendMessage(Message.raw("Stat: Passive HP regen (+2 HP every 3s)"));
                player.sendMessage(Message.raw("Perk: Heal on kill (+5 HP per zombie killed)"));
            }
        }
        player.sendMessage(Message.raw("Cost: " + pc.getUnlockCost() + " scrap" + (owned ? " (owned)" : "")));
    }

    private void handleParty(Player player, UUID playerId, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Message.raw("Usage: /purge party <create|invite|accept|leave|list>"));
            return;
        }
        String action = args[1].toLowerCase();
        switch (action) {
            case "create" -> {
                if (partyManager.getPartyByPlayer(playerId) != null) {
                    player.sendMessage(Message.raw("You are already in a party."));
                    return;
                }
                partyManager.createParty(playerId);
                player.sendMessage(Message.raw("Party created."));
            }
            case "invite" -> {
                if (args.length < 3) {
                    player.sendMessage(Message.raw("Usage: /purge party invite <playerName> (any party member can invite)"));
                    return;
                }
                String targetName = args[2];
                PlayerRef targetRef = findOnlineByName(targetName);
                if (targetRef == null) {
                    player.sendMessage(Message.raw("Player not found."));
                    return;
                }
                UUID targetId = targetRef.getUuid();
                if (targetId == null || targetId.equals(playerId)) {
                    player.sendMessage(Message.raw("You cannot invite yourself."));
                    return;
                }
                partyManager.invite(playerId, targetId);
            }
            case "accept" -> partyManager.accept(playerId);
            case "leave" -> {
                if (partyManager.getPartyByPlayer(playerId) == null) {
                    player.sendMessage(Message.raw("You are not in a party."));
                    return;
                }
                partyManager.leaveParty(playerId);
                player.sendMessage(Message.raw("You left the party."));
            }
            case "list" -> {
                PurgeParty party = partyManager.getPartyByPlayer(playerId);
                if (party == null) {
                    player.sendMessage(Message.raw("You are not in a party."));
                    return;
                }
                List<UUID> members = new ArrayList<>(party.getMembersSnapshot());
                members.sort(Comparator.comparing(
                        memberId -> PurgePlayerNameResolver.resolve(memberId, PurgePlayerNameResolver.FallbackStyle.FULL_UUID),
                        String.CASE_INSENSITIVE_ORDER));
                player.sendMessage(Message.raw("-- Party Members (" + members.size()
                        + "/" + PurgeParty.MAX_SIZE + ") --"));
                for (UUID memberId : members) {
                    String name = PurgePlayerNameResolver.resolve(memberId, PurgePlayerNameResolver.FallbackStyle.FULL_UUID);
                    player.sendMessage(Message.raw("- " + name));
                }
            }
            default -> player.sendMessage(Message.raw("Usage: /purge party <create|invite|accept|leave|list>"));
        }
    }

    private PlayerRef findOnlineByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            if (playerRef != null && name.equalsIgnoreCase(playerRef.getUsername())) {
                return playerRef;
            }
        }
        return null;
    }
}
