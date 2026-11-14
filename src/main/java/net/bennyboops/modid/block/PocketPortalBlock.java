package net.bennyboops.modid.block;

import net.bennyboops.modid.compat.SableCompat;
import net.bennyboops.modid.data.SuitcaseFinder;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.world.BlockView;
import net.minecraft.item.Item;
import net.bennyboops.modid.block.entity.SuitcaseBlockEntity;
import net.bennyboops.modid.data.SuitcaseLocationTracker;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.bennyboops.modid.util.StackData;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

public class PocketPortalBlock extends Block {

    public PocketPortalBlock(Settings settings) {
        super(settings);
    }

    @Override
    public List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) {
        return Collections.singletonList(new ItemStack(this));
    }

    private static final int MAX_NESTED_CONTAINER_DEPTH = 4;

    @Override
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (world.isClient) return;
        // Items thrown into it leave the pocket dimension like released mobs
        if (entity instanceof ItemEntity item) {
            if (isPocketDimension(world)) PocketTravelHandler.requestItemExit(item);
            return;
        }
        if (!(entity instanceof ServerPlayerEntity player)) return;
        if (!isPocketDimension(world)) return;

        // Teleporting from inside the movement packet handler makes vanilla re-apply the old
        // movement afterwards, so the actual exit happens at the end of the server tick
        PocketTravelHandler.requestExit(player);
    }

    /**
     * Which way someone coming back out at exit should face: the front of the suitcase, or away from the
     * pocket door. Null when the exit isn't at that placed block (then the usual facing is kept).
     */
    @Nullable
    private static Float exitYaw(World world, Vec3d exit, @Nullable UUID suitcaseId) {
        Float door = PocketDoorBlock.yawAwayFromDoor(world, exit, suitcaseId);
        return door != null ? door : SuitcaseBlock.yawFacingFront(world, exit, suitcaseId);
    }

    /** Tooltip for the portal block and exit portal doors. */
    public static Text exitTooltip() {
        return Text.literal("An exit from pocket dimensions").formatted(Formatting.GRAY, Formatting.ITALIC);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable BlockView world, List<Text> tooltip, TooltipContext options) {
        tooltip.add(exitTooltip());
    }

    public static boolean isPocketDimension(World world) {
        Identifier id = world.getRegistryKey().getValue();
        return "pocket-repose".equals(id.getNamespace()) && id.getPath().startsWith("pocket_dimension_");
    }

    /**
     * Sends a player out of the pocket dimension they're in, to the suitcase they entered through
     * (or the best fallback). Exit points inside the pocket being left are never used, since a
     * suitcase can't be opened from inside its own pocket dimension.
     */
    public static void exitPocket(ServerPlayerEntity player, ServerWorld pocketWorld) {
        exitPocket(player, pocketWorld, false);
    }

    /** As above; quietly skips the portal's departure sound (the Exit Keystone plays ender pearl sounds instead). */
    public static void exitPocket(ServerPlayerEntity player, ServerWorld pocketWorld, boolean quietly) {
        String currentPath = pocketWorld.getRegistryKey().getValue().getPath();
        String keystoneName = currentPath.substring("pocket_dimension_".length());
        String playerUuid = player.getUuidAsString();

        preparePlayerForTeleport(player);
        if (!quietly) {
            pocketWorld.playSound(null, player.getBlockPos(), SoundEvents.ITEM_BUNDLE_DROP_CONTENTS, SoundCategory.PLAYERS, 2.0f, 1.0f);
        }

        MinecraftServer server = pocketWorld.getServer();
        SuitcaseLocationTracker tracker = SuitcaseLocationTracker.get(server);

        UUID expectedSuitcaseId = (tracker == null) ? null : tracker.getLastSuitcase(keystoneName, playerUuid);
        // Where the suitcase is right now, if it's being carried, lies dropped, or was put in a container
        SuitcaseFinder.refresh(server, tracker, keystoneName, expectedSuitcaseId, pocketWorld);

        boolean teleported = false;

        //Method 0: Authoritative suitcaseId -> location
        if (!teleported && tracker != null && expectedSuitcaseId != null) {
            SuitcaseLocationTracker.SuitcaseInstanceLocation sLoc = tracker.getSuitcaseLocation(expectedSuitcaseId);
            if (sLoc != null && sLoc.type != SuitcaseLocationTracker.LocationType.DESTROYED) {
                ServerWorld targetWorld = worldFromId(server, sLoc.dimensionId);
                if (targetWorld != null && targetWorld != pocketWorld) {
                    Float facing = exitYaw(targetWorld, new Vec3d(sLoc.x, sLoc.y, sLoc.z), expectedSuitcaseId);
                    teleportToPosition(player, targetWorld, sLoc.x, sLoc.y, sLoc.z, facing != null ? facing : player.getYaw(), player.getPitch());
                    teleported = true;
                }
            }
        }

        //Method 1: Latest per-player tracked (only if it matches suitcaseId)
        if (!teleported && tracker != null) {
            SuitcaseLocationTracker.LocationData loc = tracker.getLocation(keystoneName, playerUuid);
            if (loc != null && loc.type != SuitcaseLocationTracker.LocationType.DESTROYED) {
                if (expectedSuitcaseId == null || (loc.suitcaseId != null && expectedSuitcaseId.equals(loc.suitcaseId))) {
                    ServerWorld targetWorld = worldFromId(server, loc.dimensionId);
                    if (targetWorld != null && targetWorld != pocketWorld) {
                        Float facing = exitYaw(targetWorld, new Vec3d(loc.x, loc.y, loc.z), loc.suitcaseId);
                        if (facing != null) {
                            teleportToPosition(player, targetWorld, loc.x, loc.y, loc.z, facing, player.getPitch());
                        } else {
                            teleportToPosition(player, targetWorld, loc.x, loc.y, loc.z, loc.yaw, loc.pitch);
                        }
                        teleported = true;
                    }
                }
            }
        }

        //Method 2: Inventory)
        if (!teleported && expectedSuitcaseId != null) {
            outer:
            for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
                if (online.getServerWorld() == pocketWorld) continue;
                for (int i = 0; i < online.getInventory().size(); i++) {
                    ItemStack stack = online.getInventory().getStack(i);

                    if (containsSuitcaseWithKeystoneAndId(stack, keystoneName, expectedSuitcaseId, MAX_NESTED_CONTAINER_DEPTH)) {

                        ServerWorld targetWorld = online.getServerWorld();
                        teleportToPosition(player, targetWorld,
                                online.getX(), online.getY() + 1.0, online.getZ(),
                                player.getYaw(), player.getPitch());

                        teleported = true;
                        break outer;
                    }
                }
            }
        }


        //Method 3: Dropped items
        if (!teleported && expectedSuitcaseId != null) {
            ItemEntity suitcaseItem = SuitcaseFinder.findItem(keystoneName, expectedSuitcaseId, pocketWorld);
            if (suitcaseItem != null && suitcaseItem.getWorld() instanceof ServerWorld w) {
                cleanUpSuitcaseItemNbt(suitcaseItem.getStack(), player, keystoneName);
                suitcaseItem.setStack(suitcaseItem.getStack());
                teleportToPosition(player, w,
                        suitcaseItem.getX(), suitcaseItem.getY() + 1.0, suitcaseItem.getZ(),
                        player.getYaw(), player.getPitch());
                teleported = true;
            }
        }

        //Method 4: Containers
        if (!teleported && expectedSuitcaseId != null) {
            teleported = searchContainersForSuitcase(server, pocketWorld, player, keystoneName, expectedSuitcaseId);
        }

        //Method 5: ENTRY fallback
        if (!teleported && tracker != null) {
            SuitcaseLocationTracker.LocationData entry = tracker.getEntryLocation(keystoneName, playerUuid);
            if (entry != null) {
                ServerWorld targetWorld = worldFromId(server, entry.dimensionId);
                if (targetWorld != null && targetWorld != pocketWorld) {
                    teleportToPosition(player, targetWorld,
                            entry.x, entry.y, entry.z,
                            entry.yaw, entry.pitch);
                    teleported = true;
                }
            }
        }

        if (!teleported) {

            MinecraftServer srv = player.getServer();
            if (srv != null) {
                boolean moved = false;

                BlockPos respawnPos = player.getSpawnPointPosition();
                RegistryKey<World> respawnDim = player.getSpawnPointDimension();

                if (respawnPos != null) {
                    ServerWorld respawnWorld = srv.getWorld(respawnDim);
                    if (respawnWorld != null && isValidRespawnBlock(respawnWorld, respawnPos)) {
                        Vec3d safe = findSafeSpotNear(respawnWorld, respawnPos);
                        if (safe != null) {
                            teleportToPosition(player, respawnWorld, safe.x, safe.y, safe.z, player.getYaw(), player.getPitch());
                            moved = true;
                        }
                    }
                }

                if (!moved) {
                    ServerWorld overworld = srv.getOverworld();
                    BlockPos worldSpawn = overworld.getSpawnPos();
                    Vec3d safe = findSafeSpotNear(overworld, worldSpawn);
                    if (safe == null) safe = new Vec3d(worldSpawn.getX() + 0.5, worldSpawn.getY() + 0.1, worldSpawn.getZ() + 0.5);

                    teleportToPosition(player, overworld, safe.x, safe.y, safe.z, overworld.getSpawnAngle(), player.getPitch());
                }
            }
        }

        SuitcaseBlockEntity.removeSuitcaseEntry(keystoneName, playerUuid, server);
    }

    private static boolean isValidRespawnBlock(ServerWorld world, BlockPos pos) {
        var state = world.getBlockState(pos);
        return state.getBlock() instanceof net.minecraft.block.BedBlock
                || state.isOf(net.minecraft.block.Blocks.RESPAWN_ANCHOR);
    }
    public static Vec3d findSafeSpotNear(ServerWorld world, BlockPos origin) {
        for (int dy = 0; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos p = origin.add(dx, dy, dz);
                    BlockPos below = p.down();

                    if (!world.getBlockState(below).isSolidBlock(world, below)) continue;

                    if (!world.getBlockState(p).getCollisionShape(world, p).isEmpty()) continue;
                    BlockPos pUp = p.up();
                    if (!world.getBlockState(pUp).getCollisionShape(world, pUp).isEmpty()) continue;

                    return new Vec3d(p.getX() + 0.5, p.getY() + 0.1, p.getZ() + 0.5);
                }
            }
        }
        return null;
    }

    private static boolean searchContainersForSuitcase(MinecraftServer server, ServerWorld pocketWorld, ServerPlayerEntity exitingPlayer,
                                                       String keystoneName, UUID expectedSuitcaseId) {
        for (ServerPlayerEntity onlinePlayer : server.getPlayerManager().getPlayerList()) {
            ServerWorld playerWorld = onlinePlayer.getServerWorld();
            if (playerWorld == pocketWorld) continue;

            ChunkPos playerChunk = onlinePlayer.getChunkPos();
            int radius = 8;

            for (int x = playerChunk.x - radius; x <= playerChunk.x + radius; x++) {
                for (int z = playerChunk.z - radius; z <= playerChunk.z + radius; z++) {
                    if (!playerWorld.isChunkLoaded(x, z)) continue;

                    WorldChunk chunk = playerWorld.getChunk(x, z);
                    for (var blockEntity : chunk.getBlockEntities().values()) {
                        if (!(blockEntity instanceof Inventory inventory)) continue;

                        for (int i = 0; i < inventory.size(); i++) {
                            ItemStack stack = inventory.getStack(i);
                            if (isSuitcaseWithKeystoneAndId(stack, keystoneName, expectedSuitcaseId)) {
                                cleanUpSuitcaseItemNbt(stack, exitingPlayer, keystoneName);
                                inventory.setStack(i, stack);

                                BlockPos containerPos = ((net.minecraft.block.entity.BlockEntity) blockEntity).getPos();
                                teleportToPosition(exitingPlayer, playerWorld,
                                        containerPos.getX() + 0.5,
                                        containerPos.getY() + 1.0,
                                        containerPos.getZ() + 0.5,
                                        exitingPlayer.getYaw(), exitingPlayer.getPitch());
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private static void preparePlayerForTeleport(ServerPlayerEntity player) {
        player.stopRiding();
        player.velocityModified = true;
        player.setVelocity(Vec3d.ZERO);
        player.fallDistance = 0f;
    }

    //Exact teleport
    private static void teleportToPosition(ServerPlayerEntity player, ServerWorld targetWorld,
                                           double x, double y, double z, float yaw, float pitch) {
        // A suitcase on a Sable physics object is tracked where Sable keeps the object's blocks, far from where it is
        Vec3d tracked = new Vec3d(x, y, z);
        Vec3d at = SableCompat.toWorld(targetWorld, tracked);
        player.teleport(targetWorld, at.x, at.y, at.z, SableCompat.toWorldYaw(targetWorld, tracked, yaw), pitch);
        player.setVelocity(Vec3d.ZERO);
        player.fallDistance = 0f;
    }

    private static ServerWorld worldFromId(MinecraftServer server, String dimStr) {
        if (dimStr == null || dimStr.isEmpty()) return server.getWorld(World.OVERWORLD);

        Identifier id = Identifier.tryParse(dimStr);
        if (id == null) return server.getWorld(World.OVERWORLD);

        RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, id);
        ServerWorld w = server.getWorld(key);
        return (w != null) ? w : server.getWorld(World.OVERWORLD);
    }

    private static boolean isSuitcaseWithKeystoneAndId(ItemStack stack, String keystoneName, UUID expectedSuitcaseId) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem bi)
                || !(SuitcaseBlock.isPocketBlock(bi.getBlock()))) {
            return false;
        }

        NbtCompound beTag = StackData.getBlockEntityData(stack);
        if (beTag == null) return false;

        NbtCompound nbt = beTag;
        if (!keystoneName.equals(nbt.getString("BoundKeystone"))) return false;

        if (!nbt.containsUuid(SuitcaseBlockEntity.NBT_SUITCASE_ID)) return false;
        UUID id = nbt.getUuid(SuitcaseBlockEntity.NBT_SUITCASE_ID);

        return expectedSuitcaseId.equals(id);
    }

    private static void cleanUpSuitcaseItemNbt(ItemStack stack, ServerPlayerEntity player, String keystoneName) {
        NbtCompound component = StackData.getBlockEntityData(stack);
        if (component == null) return;

        NbtElement raw = component;
        if (!(raw instanceof NbtCompound tag)) return;

        if (tag.contains("EnteredPlayers", NbtElement.LIST_TYPE)) {
            NbtList old = tag.getList("EnteredPlayers", NbtElement.COMPOUND_TYPE);
            NbtList kept = new NbtList();
            boolean removed = false;

            for (int i = 0; i < old.size(); i++) {
                NbtCompound entry = old.getCompound(i);
                if (player.getUuidAsString().equals(entry.getString("UUID"))) removed = true;
                else kept.add(entry);
            }

            if (removed) {
                tag.put("EnteredPlayers", kept);
                StackData.setBlockEntityData(stack, tag);
                updateItemLore(stack, kept.size(), keystoneName, tag.getBoolean("Locked"));
            }
        }
    }

    public static boolean containsSuitcaseWithKeystoneAndId(ItemStack stack, String keystone, UUID id, int depth) {
        if (depth <= 0 || stack.isEmpty()) return false;

        if (isSuitcaseWithKeystoneAndId(stack, keystone, id)) return true;

        for (ItemStack inner : StackData.getContainedStacks(stack)) {
            if (containsSuitcaseWithKeystoneAndId(inner, keystone, id, depth - 1)) return true;
        }
        return false;
    }


    private static void updateItemLore(ItemStack stack, int playerCount, String keystone, boolean locked) {
        List<Text> lines = new ArrayList<>();

        if (playerCount > 0) {
            lines.add(Text.literal("⚠ Contains " + playerCount + " Traveler(s)!")
                    .formatted(Formatting.RED));
        }

        lines.add(Text.literal("Bound to: " + (locked ? "§k" : "") + keystone.replace("_", " "))
                .formatted(Formatting.GRAY));
        lines.add(Text.literal(locked ? "§cLocked" : "§aUnlocked")
                .formatted(Formatting.GRAY));

        StackData.setLore(stack, lines);
    }
}
