package io.hyvexa.parkour.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import io.hyvexa.common.util.CommandUtils;
import io.hyvexa.common.util.EntityUtils;
import io.hyvexa.common.util.PermissionUtils;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class MobGalleryCommand extends AbstractAsyncCommand {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int PAGE_SIZE = 12;
    private static final int MAX_COLUMNS = 4;
    private static final double FORWARD_OFFSET = 10.0;
    private static final double COLUMN_SPACING = 6.0;
    private static final double ROW_SPACING = 8.0;
    private static final float FACE_PLAYER_YAW_OFFSET = 180.0f;

    private static final Map<String, List<String>> GROUPS = createGroups();
    private static final ConcurrentHashMap<UUID, List<SpawnedMobRecord>> ACTIVE_GALLERIES = new ConcurrentHashMap<>();

    private volatile NPCPlugin npcPlugin;

    public MobGalleryCommand() {
        super("mobgallery", "Spawn frozen hostile mob galleries for visual testing.");
        this.setPermissionGroup(GameMode.Adventure);
        this.setAllowsExtraArguments(true);
    }

    @Override
    @Nonnull
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        CommandSender sender = ctx.sender();
        if (!(sender instanceof Player player)) {
            ctx.sendMessage(Message.raw("Players only."));
            return CompletableFuture.completedFuture(null);
        }
        if (!PermissionUtils.isOp(player)) {
            ctx.sendMessage(Message.raw("You must be OP to use this command."));
            return CompletableFuture.completedFuture(null);
        }

        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            ctx.sendMessage(Message.raw("Player not in world."));
            return CompletableFuture.completedFuture(null);
        }

        Store<EntityStore> store = ref.getStore();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return CompletableFuture.completedFuture(null);
        }
        UUID playerId = playerRef.getUuid();

        String[] args = CommandUtils.tokenize(ctx);
        if (args.length == 0) {
            sendHelp(player);
            return CompletableFuture.completedFuture(null);
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "help" -> sendHelp(player);
            case "list" -> sendGroupList(player);
            case "clear" -> handleClear(player, playerId);
            case "spawn" -> handleSpawn(player, playerId, args, 1);
            default -> handleSpawn(player, playerId, args, 0);
        }

        return CompletableFuture.completedFuture(null);
    }

    private void handleClear(Player player, UUID playerId) {
        int cleared = clearActiveGallery(playerId);
        if (cleared <= 0) {
            player.sendMessage(Message.raw("No active mob gallery to clear."));
            return;
        }
        player.sendMessage(Message.raw("Cleared " + cleared + " gallery mobs."));
    }

    private void handleSpawn(Player player, UUID playerId, String[] args, int groupIndex) {
        if (args.length <= groupIndex) {
            player.sendMessage(Message.raw("Usage: /mobgallery spawn <group> [page]"));
            player.sendMessage(Message.raw("Use /mobgallery list to see groups."));
            return;
        }

        String group = args[groupIndex].toLowerCase();
        List<String> roles = GROUPS.get(group);
        if (roles == null) {
            player.sendMessage(Message.raw("Unknown mob group: " + args[groupIndex]));
            player.sendMessage(Message.raw("Use /mobgallery list to see groups."));
            return;
        }

        int totalPages = pageCount(roles.size());
        int page = 1;
        if (args.length > groupIndex + 1) {
            try {
                page = Integer.parseInt(args[groupIndex + 1]);
            } catch (NumberFormatException e) {
                player.sendMessage(Message.raw("Invalid page. Usage: /mobgallery " + group + " [page]"));
                return;
            }
        }

        if (page < 1 || page > totalPages) {
            player.sendMessage(Message.raw("Page out of range. " + group + " has " + totalPages + " page(s)."));
            return;
        }

        NPCPlugin plugin = getNpcPlugin();
        if (plugin == null) {
            player.sendMessage(Message.raw("NPCPlugin not available. Cannot spawn mob gallery."));
            return;
        }

        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            player.sendMessage(Message.raw("Player not in world."));
            return;
        }

        Store<EntityStore> store = ref.getStore();
        if (store == null) {
            player.sendMessage(Message.raw("No entity store available."));
            return;
        }
        World world = store.getExternalData().getWorld();
        if (world == null) {
            player.sendMessage(Message.raw("No world available."));
            return;
        }

        int requestedPage = page;
        world.execute(() -> spawnGalleryOnWorldThread(playerId, group, requestedPage, roles, world, plugin));
    }

    private void spawnGalleryOnWorldThread(UUID playerId, String group, int page, List<String> roles,
                                           World world, NPCPlugin plugin) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null) {
            return;
        }

        Ref<EntityStore> playerRef = world.getEntityStore().getRefFromUUID(playerId);
        if (playerRef == null || !playerRef.isValid()) {
            return;
        }

        Player player = store.getComponent(playerRef, Player.getComponentType());
        TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (player == null || transform == null) {
            return;
        }

        int startIndex = (page - 1) * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, roles.size());
        List<String> pageRoles = roles.subList(startIndex, endIndex);

        int cleared = clearActiveGallery(playerId);
        if (cleared > 0) {
            player.sendMessage(Message.raw("Cleared previous gallery (" + cleared + " mobs)."));
        }

        float playerYaw = transform.getRotation().getYaw();
        double yawRadians = Math.toRadians(playerYaw);
        double forwardX = Math.sin(yawRadians);
        double forwardZ = Math.cos(yawRadians);
        double rightX = Math.cos(yawRadians);
        double rightZ = -Math.sin(yawRadians);

        Vector3d playerPos = transform.getPosition();
        double baseX = playerPos.getX() + forwardX * FORWARD_OFFSET;
        double baseY = playerPos.getY();
        double baseZ = playerPos.getZ() + forwardZ * FORWARD_OFFSET;
        Vector3f rotation = new Vector3f(0.0f, normalizeYaw(playerYaw + FACE_PLAYER_YAW_OFFSET), 0.0f);

        int columns = Math.min(MAX_COLUMNS, pageRoles.size());
        List<SpawnedMobRecord> spawned = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (int index = 0; index < pageRoles.size(); index++) {
            int row = index / columns;
            int columnInRow = index % columns;
            int rowCount = Math.min(columns, pageRoles.size() - (row * columns));
            double rowHalfWidth = (rowCount - 1) / 2.0;

            double sideOffset = (columnInRow - rowHalfWidth) * COLUMN_SPACING;
            double forwardOffset = row * ROW_SPACING;

            Vector3d spawnPos = new Vector3d(
                    baseX + (rightX * sideOffset) + (forwardX * forwardOffset),
                    baseY,
                    baseZ + (rightZ * sideOffset) + (forwardZ * forwardOffset)
            );

            String roleName = pageRoles.get(index);
            SpawnedMobRecord record = spawnGalleryMob(store, world, plugin, roleName, spawnPos, rotation);
            if (record == null) {
                failed.add(roleName);
                continue;
            }
            spawned.add(record);
        }

        if (!spawned.isEmpty()) {
            ACTIVE_GALLERIES.put(playerId, List.copyOf(spawned));
        }

        int totalPages = pageCount(roles.size());
        player.sendMessage(Message.raw("Spawned mob gallery '" + group + "' page " + page + "/" + totalPages
                + " (" + spawned.size() + "/" + pageRoles.size() + " mobs)."));
        if (!failed.isEmpty()) {
            player.sendMessage(Message.raw("Failed to spawn: " + String.join(", ", failed)));
        }
        player.sendMessage(Message.raw("Use /mobgallery clear to remove the gallery."));
    }

    private SpawnedMobRecord spawnGalleryMob(Store<EntityStore> store, World world, NPCPlugin plugin,
                                             String roleName, Vector3d position, Vector3f rotation) {
        try {
            Object result = plugin.spawnNPC(store, roleName, roleName, position, rotation);
            Ref<EntityStore> entityRef = EntityUtils.extractEntityRef(result);
            if (entityRef == null || !entityRef.isValid()) {
                return null;
            }

            UUIDComponent uuidComponent = store.getComponent(entityRef, UUIDComponent.getComponentType());
            if (uuidComponent == null || uuidComponent.getUuid() == null) {
                store.removeEntity(entityRef, RemoveReason.REMOVE);
                return null;
            }

            try {
                store.addComponent(entityRef, Invulnerable.getComponentType(), Invulnerable.INSTANCE);
            } catch (Exception e) {
                LOGGER.atWarning().log("Failed to make gallery mob invulnerable: " + roleName + " - " + e.getMessage());
            }

            try {
                store.addComponent(entityRef, Frozen.getComponentType(), Frozen.get());
            } catch (Exception e) {
                LOGGER.atWarning().log("Failed to freeze gallery mob: " + roleName + " - " + e.getMessage());
            }

            try {
                Nameplate nameplate = store.ensureAndGetComponent(entityRef, Nameplate.getComponentType());
                nameplate.setText(roleName);
            } catch (Exception e) {
                LOGGER.atWarning().log("Failed to set gallery mob nameplate: " + roleName + " - " + e.getMessage());
            }

            removeInteractable(store, entityRef);
            return new SpawnedMobRecord(world.getName(), uuidComponent.getUuid());
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to spawn gallery mob " + roleName + ": " + e.getMessage());
            return null;
        }
    }

    private void removeInteractable(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        try {
            com.hypixel.hytale.component.Archetype<EntityStore> archetype = store.getArchetype(entityRef);
            if (archetype.contains(com.hypixel.hytale.server.core.modules.entity.component.Interactable.getComponentType())) {
                store.removeComponent(entityRef, com.hypixel.hytale.server.core.modules.entity.component.Interactable.getComponentType());
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to remove mob interactable component: " + e.getMessage());
        }
    }

    private int clearActiveGallery(UUID playerId) {
        List<SpawnedMobRecord> records = ACTIVE_GALLERIES.remove(playerId);
        if (records == null || records.isEmpty()) {
            return 0;
        }

        Map<String, List<SpawnedMobRecord>> byWorld = new LinkedHashMap<>();
        for (SpawnedMobRecord record : records) {
            byWorld.computeIfAbsent(record.worldName, ignored -> new ArrayList<>()).add(record);
        }

        for (Map.Entry<String, List<SpawnedMobRecord>> entry : byWorld.entrySet()) {
            World world = Universe.get().getWorld(entry.getKey());
            if (world == null) {
                continue;
            }
            List<SpawnedMobRecord> worldRecords = entry.getValue();
            world.execute(() -> clearGalleryOnWorldThread(world, worldRecords));
        }

        return records.size();
    }

    private void clearGalleryOnWorldThread(World world, List<SpawnedMobRecord> records) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null) {
            return;
        }

        for (SpawnedMobRecord record : records) {
            Ref<EntityStore> entityRef = world.getEntityStore().getRefFromUUID(record.entityUuid);
            if (entityRef == null || !entityRef.isValid()) {
                continue;
            }
            try {
                store.removeEntity(entityRef, RemoveReason.REMOVE);
            } catch (Exception e) {
                LOGGER.atWarning().log("Failed to clear gallery mob " + record.entityUuid + ": " + e.getMessage());
            }
        }
    }

    private void sendGroupList(Player player) {
        player.sendMessage(Message.raw("Mob gallery groups:"));
        for (Map.Entry<String, List<String>> entry : GROUPS.entrySet()) {
            int size = entry.getValue().size();
            int pages = pageCount(size);
            player.sendMessage(Message.raw("- " + entry.getKey() + " (" + size + " mobs, " + pages + " page(s))"));
        }
        player.sendMessage(Message.raw("Recommended first try: /mobgallery sample"));
    }

    private void sendHelp(Player player) {
        player.sendMessage(Message.raw("/mobgallery list - Show available groups"));
        player.sendMessage(Message.raw("/mobgallery clear - Remove your last gallery"));
        player.sendMessage(Message.raw("/mobgallery spawn <group> [page] - Spawn a frozen gallery"));
        player.sendMessage(Message.raw("/mobgallery <group> [page] - Shortcut"));
        player.sendMessage(Message.raw("Recommended first try: /mobgallery sample"));
    }

    private NPCPlugin getNpcPlugin() {
        if (npcPlugin != null) {
            return npcPlugin;
        }
        try {
            npcPlugin = NPCPlugin.get();
        } catch (Exception e) {
            LOGGER.atWarning().log("NPCPlugin not available for mobgallery: " + e.getMessage());
            npcPlugin = null;
        }
        return npcPlugin;
    }


    private static int pageCount(int size) {
        return Math.max(1, (size + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private static float normalizeYaw(float yaw) {
        float normalized = yaw % 360.0f;
        if (normalized < 0.0f) {
            normalized += 360.0f;
        }
        return normalized;
    }

    private static Map<String, List<String>> createGroups() {
        LinkedHashMap<String, List<String>> groups = new LinkedHashMap<>();

        List<String> sample = List.of(
                "Zombie",
                "Skeleton",
                "Bear_Grizzly",
                "Spider_Cave",
                "Goblin_Scrapper",
                "Outlander_Berserker",
                "Scarak_Defender",
                "Trork_Warrior",
                "Crawler_Void",
                "Golem_Firesteel",
                "Spirit_Root",
                "Piranha"
        );

        List<String> beasts = List.of(
                "Bear_Grizzly",
                "Bear_Polar",
                "Fox",
                "Hyena",
                "Leopard_Snow",
                "Tiger_Sabertooth",
                "Wolf_Black",
                "Wolf_White",
                "Emberwulf",
                "Fen_Stalker",
                "Snapdragon",
                "Yeti",
                "Crocodile",
                "Raptor_Cave",
                "Rex_Cave",
                "Toad_Rhino",
                "Toad_Rhino_Magma",
                "Larva_Silk",
                "Molerat",
                "Rat",
                "Scorpion",
                "Slug_Magma",
                "Snake_Cobra",
                "Snake_Marsh",
                "Snake_Rattle",
                "Spider",
                "Spider_Cave"
        );

        List<String> aquatic = List.of(
                "Piranha",
                "Piranha_Black"
        );

        List<String> elemental = List.of(
                "Golem_Crystal_Earth",
                "Golem_Crystal_Flame",
                "Golem_Crystal_Frost",
                "Golem_Crystal_Sand",
                "Golem_Crystal_Thunder",
                "Golem_Firesteel",
                "Spirit_Ember",
                "Spirit_Frost",
                "Spirit_Root",
                "Spirit_Thunder"
        );

        List<String> voidGroup = List.of(
                "Crawler_Void",
                "Larva_Void",
                "Eye_Void",
                "Spawn_Void",
                "Spectre_Void"
        );

        List<String> zombies = List.of(
                "Zombie",
                "Zombie_Burnt",
                "Zombie_Frost",
                "Zombie_Sand",
                "Zombie_Aberrant",
                "Zombie_Aberrant_Big",
                "Zombie_Aberrant_Small"
        );

        List<String> undeadSpecial = List.of(
                "Chicken_Undead",
                "Cow_Undead",
                "Pig_Undead",
                "Ghoul",
                "Hound_Bleached",
                "Shadow_Knight",
                "Werewolf",
                "Wraith",
                "Wraith_Lantern",
                "Risen_Gunner",
                "Risen_Knight"
        );

        List<String> skeletons = List.of(
                "Skeleton",
                "Skeleton_Archer",
                "Skeleton_Archmage",
                "Skeleton_Fighter",
                "Skeleton_Knight",
                "Skeleton_Mage",
                "Skeleton_Ranger",
                "Skeleton_Scout",
                "Skeleton_Soldier",
                "Skeleton_Burnt_Alchemist",
                "Skeleton_Burnt_Archer",
                "Skeleton_Burnt_Gunner",
                "Skeleton_Burnt_Knight",
                "Skeleton_Burnt_Lancer",
                "Skeleton_Burnt_Praetorian",
                "Skeleton_Burnt_Soldier",
                "Skeleton_Burnt_Wizard",
                "Skeleton_Frost_Archer",
                "Skeleton_Frost_Archmage",
                "Skeleton_Frost_Fighter",
                "Skeleton_Frost_Knight",
                "Skeleton_Frost_Mage",
                "Skeleton_Frost_Ranger",
                "Skeleton_Frost_Scout",
                "Skeleton_Frost_Soldier",
                "Skeleton_Incandescent_Fighter",
                "Skeleton_Incandescent_Footman",
                "Skeleton_Incandescent_Head",
                "Skeleton_Incandescent_Mage",
                "Skeleton_Pirate_Captain",
                "Skeleton_Pirate_Gunner",
                "Skeleton_Pirate_Striker",
                "Skeleton_Sand_Archer",
                "Skeleton_Sand_Archmage",
                "Skeleton_Sand_Assassin",
                "Skeleton_Sand_Guard",
                "Skeleton_Sand_Mage",
                "Skeleton_Sand_Ranger",
                "Skeleton_Sand_Scout",
                "Skeleton_Sand_Soldier",
                "Dungeon_Skeleton_Sand_Archer",
                "Dungeon_Skeleton_Sand_Assassin",
                "Dungeon_Skeleton_Sand_Mage",
                "Dungeon_Skeleton_Sand_Soldier"
        );

        List<String> goblin = List.of(
                "Goblin_Duke",
                "Goblin_Duke_Phase_2",
                "Goblin_Duke_Phase_3_Fast",
                "Goblin_Duke_Phase_3_Slow",
                "Goblin_Hermit",
                "Goblin_Lobber",
                "Goblin_Miner",
                "Goblin_Ogre",
                "Goblin_Ogre_Tutorial",
                "Goblin_Scavenger",
                "Goblin_Scavenger_Battleaxe",
                "Goblin_Scavenger_Sword",
                "Goblin_Scrapper",
                "Goblin_Thief"
        );

        List<String> outlander = List.of(
                "Hedera",
                "Outlander_Berserker",
                "Outlander_Brute",
                "Outlander_Cultist",
                "Outlander_Hunter",
                "Outlander_Marauder",
                "Outlander_Peon",
                "Outlander_Priest",
                "Outlander_Sorcerer",
                "Outlander_Stalker",
                "Wolf_Outlander_Priest",
                "Wolf_Outlander_Sorcerer"
        );

        List<String> scarak = List.of(
                "Scarak_Broodmother",
                "Scarak_Defender",
                "Scarak_Fighter",
                "Scarak_Fighter_Royal_Guard",
                "Scarak_Louse",
                "Scarak_Seeker",
                "Dungeon_Scarak_Broodmother",
                "Dungeon_Scarak_Broodmother_Young",
                "Dungeon_Scarak_Defender",
                "Dungeon_Scarak_Fighter",
                "Dungeon_Scarak_Louse",
                "Dungeon_Scarak_Seeker"
        );

        List<String> trork = List.of(
                "Trork_Brawler",
                "Trork_Chieftain",
                "Trork_Doctor_Witch",
                "Trork_Guard",
                "Trork_Hunter",
                "Trork_Mauler",
                "Trork_Sentry",
                "Trork_Shaman",
                "Trork_Unarmed",
                "Trork_Warrior",
                "Wolf_Trork_Hunter",
                "Wolf_Trork_Shaman"
        );

        groups.put("sample", sample);
        groups.put("beasts", beasts);
        groups.put("aquatic", aquatic);
        groups.put("elemental", elemental);
        groups.put("void", voidGroup);
        groups.put("zombies", zombies);
        groups.put("undead", concatUnique(undeadSpecial, zombies, skeletons));
        groups.put("skeletons", skeletons);
        groups.put("goblin", goblin);
        groups.put("outlander", outlander);
        groups.put("scarak", scarak);
        groups.put("trork", trork);
        groups.put("factions", concatUnique(goblin, outlander, scarak, trork));
        groups.put("all", concatUnique(beasts, aquatic, elemental, voidGroup, undeadSpecial, zombies, skeletons,
                goblin, outlander, scarak, trork));

        return Collections.unmodifiableMap(new LinkedHashMap<>(groups));
    }

    @SafeVarargs
    private static List<String> concatUnique(List<String>... groups) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (List<String> group : groups) {
            unique.addAll(group);
        }
        return List.copyOf(unique);
    }

    private record SpawnedMobRecord(String worldName, UUID entityUuid) {}
}
