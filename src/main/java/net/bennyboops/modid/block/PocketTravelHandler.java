package net.bennyboops.modid.block;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Moves players into and out of pocket dimensions at the end of the server tick. Players leave
 * when they touch a portal block or fall off the bottom of the pocket dimension, and enter when
 * they sneak into an open suitcase.
 */
public final class PocketTravelHandler {
    private static final Set<UUID> PENDING_EXITS = new HashSet<>();
    private static final Map<UUID, PendingEntry> PENDING_ENTRIES = new LinkedHashMap<>();

    private record PendingEntry(ServerWorld world, BlockPos pos) {
    }

    private PocketTravelHandler() {
    }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(PocketTravelHandler::tick);
    }

    public static void requestExit(ServerPlayerEntity player) {
        PENDING_EXITS.add(player.getUuid());
    }

    public static void requestEntry(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        PENDING_ENTRIES.putIfAbsent(player.getUuid(), new PendingEntry(world, pos.toImmutable()));
    }

    private static void tick(MinecraftServer server) {
        List<ServerPlayerEntity> players = new ArrayList<>(server.getPlayerManager().getPlayerList());
        for (ServerPlayerEntity player : players) {
            ServerWorld world = player.getServerWorld();
            if (!PocketPortalBlock.isPocketDimension(world) || player.isSpectator() || !player.isAlive()) continue;

            boolean fellOut = player.getY() < world.getBottomY();
            if (fellOut || PENDING_EXITS.contains(player.getUuid())) {
                PocketPortalBlock.exitPocket(player, world);
            }
        }
        PENDING_EXITS.clear();

        // After exits, so a player who left this tick doesn't also enter a suitcase from the pocket they left
        Map<UUID, PendingEntry> entries = new LinkedHashMap<>(PENDING_ENTRIES);
        PENDING_ENTRIES.clear();
        entries.forEach((uuid, entry) -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) SuitcaseBlock.enterSuitcase(player, entry.world(), entry.pos());
        });
    }
}
