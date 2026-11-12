package net.bennyboops.modid.data;

import net.bennyboops.modid.PocketRepose;
import net.bennyboops.modid.block.PocketPortalBlock;
import net.bennyboops.modid.block.SuitcaseBlock;
import net.bennyboops.modid.util.StackData;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps track of where suitcase items are without searching the worlds every few seconds.
 *
 * Dropped items holding a pocket block (directly, or inside a shulker box or bundle) are remembered as they load
 * and unload, which also records where they were last seen. Players' inventories are still checked regularly,
 * since that's cheap. Everything else (a suitcase in a chest, or a dropped one that has moved) is only looked up
 * when someone or something actually leaves a pocket dimension, by {@link #refresh}.
 */
public final class SuitcaseFinder {
    private static final int DEPTH = 4;
    // How far around a suitcase's last known spot to look through containers for it
    private static final int CONTAINER_SEARCH_CHUNKS = 2;

    private static final Set<ItemEntity> ITEMS = new HashSet<>();

    private SuitcaseFinder() {}

    /** An item entity was added to a world (dropped, spawned, or loaded with its chunk). */
    public static void onItemLoaded(ItemEntity item) {
        if (!(item.getWorld() instanceof ServerWorld world) || !carriesPocketBlock(item.getStack(), DEPTH)) return;
        ITEMS.add(item);
        record(world, item);
    }

    /** An item entity left a world (picked up, destroyed, merged, or unloaded with its chunk). */
    public static void onItemUnloaded(ItemEntity item) {
        if (!ITEMS.remove(item)) return;
        if (item.getWorld() instanceof ServerWorld world) record(world, item);
    }

    public static void clear() {
        ITEMS.clear();
    }

    /** A loaded item entity holding this suitcase, outside the given world, or null. */
    @Nullable
    public static ItemEntity findItem(String keystone, UUID suitcaseId, @Nullable World exclude) {
        for (ItemEntity item : ITEMS) {
            if (item.isRemoved() || item.getWorld() == exclude) continue;
            if (PocketPortalBlock.containsSuitcaseWithKeystoneAndId(item.getStack(), keystone, suitcaseId, DEPTH)) return item;
        }
        return null;
    }

    /**
     * Brings a suitcase's tracked location up to date just before it's used as a way out, by checking where it
     * is now: in an online player's inventory, as a dropped item, or in a container near where it was last seen.
     * A placed suitcase's location is already exact, so it's left alone.
     */
    public static void refresh(MinecraftServer server, @Nullable SuitcaseLocationTracker tracker, String keystone,
                               @Nullable UUID suitcaseId, ServerWorld leaving) {
        if (tracker == null || suitcaseId == null) return;
        SuitcaseLocationTracker.SuitcaseInstanceLocation known = tracker.getSuitcaseLocation(suitcaseId);
        if (known != null && known.type != SuitcaseLocationTracker.LocationType.ITEM_ENTITY) return;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerWorld world = player.getServerWorld();
            if (world == leaving) continue;
            for (int i = 0; i < player.getInventory().size(); i++) {
                if (PocketPortalBlock.containsSuitcaseWithKeystoneAndId(player.getInventory().getStack(i), keystone, suitcaseId, DEPTH)) {
                    tracker.updateSuitcaseLocation(suitcaseId, dimension(world), player.getX(), player.getY() + 1.0, player.getZ(),
                            SuitcaseLocationTracker.LocationType.ITEM_ENTITY);
                    return;
                }
            }
        }

        ItemEntity item = findItem(keystone, suitcaseId, leaving);
        if (item != null) {
            tracker.updateSuitcaseLocation(suitcaseId, dimension(item.getWorld()), item.getX(), item.getY() + 1.0, item.getZ(),
                    SuitcaseLocationTracker.LocationType.ITEM_ENTITY);
            return;
        }

        if (known == null) return;
        ServerWorld world = world(server, known.dimensionId);
        if (world == null || world == leaving) return;
        BlockPos container = findContainer(world, known.x, known.z, keystone, suitcaseId);
        if (container != null) {
            tracker.updateSuitcaseLocation(suitcaseId, dimension(world),
                    container.getX() + 0.5, container.getY() + 1.0, container.getZ() + 0.5,
                    SuitcaseLocationTracker.LocationType.ITEM_ENTITY);
        }
    }

    /** A container in the loaded chunks around a spot that holds this suitcase, or null. */
    @Nullable
    private static BlockPos findContainer(ServerWorld world, double x, double z, String keystone, UUID suitcaseId) {
        int chunkX = ChunkSectionPos.getSectionCoord(x);
        int chunkZ = ChunkSectionPos.getSectionCoord(z);
        for (int cx = chunkX - CONTAINER_SEARCH_CHUNKS; cx <= chunkX + CONTAINER_SEARCH_CHUNKS; cx++) {
            for (int cz = chunkZ - CONTAINER_SEARCH_CHUNKS; cz <= chunkZ + CONTAINER_SEARCH_CHUNKS; cz++) {
                if (!world.isChunkLoaded(cx, cz)) continue;
                WorldChunk chunk = world.getChunk(cx, cz);
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof Inventory inventory)) continue;
                    for (int i = 0; i < inventory.size(); i++) {
                        if (PocketPortalBlock.containsSuitcaseWithKeystoneAndId(inventory.getStack(i), keystone, suitcaseId, DEPTH)) {
                            return blockEntity.getPos();
                        }
                    }
                }
            }
        }
        return null;
    }

    private static void record(ServerWorld world, ItemEntity item) {
        SuitcaseLocationTracker tracker = SuitcaseLocationTracker.get(world.getServer());
        if (tracker == null) return;
        PocketRepose.scanStackRecursivelyForSuitcases(tracker, item.getStack(), dimension(world),
                item.getX(), item.getY() + 1.0, item.getZ(), 0, 0, DEPTH);
    }

    private static boolean carriesPocketBlock(ItemStack stack, int depth) {
        if (depth <= 0 || stack.isEmpty()) return false;
        if (stack.getItem() instanceof BlockItem blockItem && SuitcaseBlock.isPocketBlock(blockItem.getBlock())) return true;
        for (ItemStack inner : StackData.getContainedStacks(stack)) {
            if (carriesPocketBlock(inner, depth - 1)) return true;
        }
        return false;
    }

    private static String dimension(World world) {
        return world.getRegistryKey().getValue().toString();
    }

    @Nullable
    private static ServerWorld world(MinecraftServer server, String dimensionId) {
        Identifier id = dimensionId == null ? null : Identifier.tryParse(dimensionId);
        return id == null ? null : server.getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
    }
}
