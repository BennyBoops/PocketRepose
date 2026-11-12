package net.bennyboops.modid.block;

import net.bennyboops.modid.config.PocketReposeConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.bennyboops.modid.PocketRepose;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Moves players into and out of pocket dimensions at the end of the server tick. Players leave
 * when they touch a portal block, walk through an exit door, use an Exit Keystone or fall off the
 * bottom of the pocket dimension, and enter when they sneak into an open suitcase or walk through
 * an open pocket door.
 */
public final class PocketTravelHandler {
    // Players leaving this tick, and whether they leave with ender pearl effects (Exit Keystone)
    private static final Map<UUID, Boolean> PENDING_EXITS = new HashMap<>();
    private static final Map<UUID, PendingEntry> PENDING_ENTRIES = new LinkedHashMap<>();
    // Where each player stood at the end of the last tick, before this tick's movement
    private static Map<UUID, Vec3d> lastTickPositions = new HashMap<>();

    /** from: where the player stood before walking in, which decides the side a pocket door lets them back out on. */
    private record PendingEntry(ServerWorld world, BlockPos pos, Vec3d from) {
    }

    // Items thrown into pocket blocks, and out through portal blocks or exit portal doors, this tick
    private static final Map<ItemEntity, ItemEntry> PENDING_ITEM_ENTRIES = new LinkedHashMap<>();
    private static final Set<ItemEntity> PENDING_ITEM_EXITS = new LinkedHashSet<>();

    private record ItemEntry(ServerWorld world, BlockPos pos) {
    }

    // An item that has just gone through a pocket block won't go through another until it's picked up (thrown again,
    // it's a new item). Otherwise one coming out on top of an open suitcase would fall straight back in.
    private static final String TRAVELED_TAG = "pocket_repose.traveled";

    public static boolean hasTraveled(Entity entity) {
        return entity.getCommandTags().contains(TRAVELED_TAG);
    }

    public static void markTraveled(Entity entity) {
        entity.addCommandTag(TRAVELED_TAG);
    }

    /** An item thrown onto an open pocket block (or the lower half of a pocket door) at pos goes in at the end of the tick. */
    public static void requestItemEntry(ItemEntity item, ServerWorld world, BlockPos pos) {
        if (!hasTraveled(item)) PENDING_ITEM_ENTRIES.putIfAbsent(item, new ItemEntry(world, pos.toImmutable()));
    }

    /** An item thrown into a portal block or exit portal door in a pocket dimension leaves it at the end of the tick. */
    public static void requestItemExit(ItemEntity item) {
        if (!hasTraveled(item)) PENDING_ITEM_EXITS.add(item);
    }

    private PocketTravelHandler() {
    }

    public static void initialize() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
    }

    public static void requestExit(ServerPlayerEntity player) {
        requestExit(player, false);
    }

    public static void requestExit(ServerPlayerEntity player, boolean pearlEffects) {
        PENDING_EXITS.merge(player.getUuid(), pearlEffects, Boolean::logicalOr);
    }

    /** Ender pearl style teleport particles around the player. */
    public static void pearlParticles(ServerPlayerEntity player) {
        player.getServerWorld().spawnParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(),
                32, 0.3, 0.8, 0.3, 0.6);
    }

    /** Ender pearl style arrival: teleport particles and sound around the player. */
    public static void pearlEffects(ServerPlayerEntity player) {
        pearlParticles(player);
        player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_TELEPORT,
                SoundCategory.PLAYERS, 1.0F, 1.0F);
    }

    public static void requestEntry(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        // By the end of the tick a fast (or lagging) player can already be past a pocket door's doorway, so
        // remember where they stood before this tick's movement brought them into it
        Vec3d from = lastTickPositions.getOrDefault(player.getUuid(), player.getPos());
        PENDING_ENTRIES.putIfAbsent(player.getUuid(), new PendingEntry(world, pos.toImmutable(), from));
    }

    private static void tick(MinecraftServer server) {
        PocketReposeConfig.tick(server);
        List<ServerPlayerEntity> players = new ArrayList<>(server.getPlayerManager().getPlayerList());
        for (ServerPlayerEntity player : players) {
            ServerWorld world = player.getServerWorld();
            if (!PocketPortalBlock.isPocketDimension(world) || player.isSpectator() || !player.isAlive()) continue;

            boolean fellOut = player.getY() < world.getBottomY();
            Boolean pearl = PENDING_EXITS.get(player.getUuid());
            if (fellOut || pearl != null) {
                boolean pearlEffects = pearl != null && pearl;
                PocketPortalBlock.exitPocket(player, world, pearlEffects);
                if (pearlEffects) {
                    pearlEffects(player);
                } else if (player.getServerWorld() != world) {
                    // The exit sound played where they left; play it where they arrived so they hear it too
                    SuitcaseBlock.playTravelSound(player);
                }
            }
        }
        PENDING_EXITS.clear();

        // Items thrown out through a portal block or an exit portal door
        List<ItemEntity> leaving = new ArrayList<>(PENDING_ITEM_EXITS);
        PENDING_ITEM_EXITS.clear();
        for (ItemEntity item : leaving) {
            if (!item.isAlive() || !(item.getWorld() instanceof ServerWorld world) || !PocketPortalBlock.isPocketDimension(world)) continue;
            world.playSound(null, item.getX(), item.getY(), item.getZ(), SoundEvents.ITEM_BUNDLE_DROP_CONTENTS, SoundCategory.PLAYERS, 1F, 1F);
            markTraveled(item);
            PocketRepose.releaseItem(world, item);
        }

        // Mobs and items that fall off the bottom of a pocket dimension leave it too. Checked every 10 ticks:
        // nothing falls far enough in that time to reach the void below the world, where it would be lost
        boolean checkFalls = server.getTicks() % 10 == 0;
        for (ServerWorld world : server.getWorlds()) {
            if (!checkFalls || !PocketPortalBlock.isPocketDimension(world)) continue;
            List<Entity> fallen = new ArrayList<>();
            for (Entity entity : world.iterateEntities()) {
                boolean mob = entity instanceof LivingEntity && !(entity instanceof PlayerEntity);
                if ((mob || entity instanceof ItemEntity) && entity.isAlive() && entity.getY() < world.getBottomY()) {
                    fallen.add(entity);
                }
            }
            for (Entity entity : fallen) {
                if (entity instanceof ItemEntity item) {
                    markTraveled(item);
                    PocketRepose.releaseItem(world, item);
                } else {
                    PocketRepose.releaseFallenMob(world, (LivingEntity) entity);
                }
            }
        }

        // After exits, so a player who left this tick doesn't also enter a suitcase from the pocket they left
        Map<UUID, PendingEntry> entries = new LinkedHashMap<>(PENDING_ENTRIES);
        PENDING_ENTRIES.clear();
        entries.forEach((uuid, entry) -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) SuitcaseBlock.enterSuitcase(player, entry.world(), entry.pos(), entry.from());
        });

        // Items thrown into pocket blocks
        Map<ItemEntity, ItemEntry> items = new LinkedHashMap<>(PENDING_ITEM_ENTRIES);
        PENDING_ITEM_ENTRIES.clear();
        items.forEach((item, entry) -> SuitcaseBlock.sendItemIn(item, entry.world(), entry.pos()));

        Map<UUID, Vec3d> positions = new HashMap<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            positions.put(player.getUuid(), player.getPos());
        }
        lastTickPositions = positions;
    }
}
