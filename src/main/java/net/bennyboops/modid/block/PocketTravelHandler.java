package net.bennyboops.modid.block;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.bennyboops.modid.PocketRepose;
import net.minecraft.entity.Entity;
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
import java.util.Map;
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

    private PocketTravelHandler() {
    }

    public static void initialize() {
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) tick(event.getServer());
        });
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
        player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                SoundCategory.PLAYERS, 1.0F, 1.0F);
    }

    public static void requestEntry(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        // By the end of the tick a fast (or lagging) player can already be past a pocket door's doorway, so
        // remember where they stood before this tick's movement brought them into it
        Vec3d from = lastTickPositions.getOrDefault(player.getUuid(), player.getPos());
        PENDING_ENTRIES.putIfAbsent(player.getUuid(), new PendingEntry(world, pos.toImmutable(), from));
    }

    private static void tick(MinecraftServer server) {
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

        // Mobs that fall off the bottom of a pocket dimension leave it too
        for (ServerWorld world : server.getWorlds()) {
            if (!PocketPortalBlock.isPocketDimension(world)) continue;
            List<LivingEntity> fallen = new ArrayList<>();
            for (Entity entity : world.iterateEntities()) {
                if (entity instanceof LivingEntity mob && !(entity instanceof PlayerEntity) && mob.isAlive()
                        && mob.getY() < world.getBottomY()) {
                    fallen.add(mob);
                }
            }
            fallen.forEach(mob -> PocketRepose.releaseFallenMob(world, mob));
        }

        // After exits, so a player who left this tick doesn't also enter a suitcase from the pocket they left
        Map<UUID, PendingEntry> entries = new LinkedHashMap<>(PENDING_ENTRIES);
        PENDING_ENTRIES.clear();
        entries.forEach((uuid, entry) -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) SuitcaseBlock.enterSuitcase(player, entry.world(), entry.pos(), entry.from());
        });

        Map<UUID, Vec3d> positions = new HashMap<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            positions.put(player.getUuid(), player.getPos());
        }
        lastTickPositions = positions;
    }
}
