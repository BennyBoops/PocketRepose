package net.bennyboops.modid.block;

import net.bennyboops.modid.block.entity.SuitcaseBlockEntity;
import net.bennyboops.modid.data.SuitcaseLocationTracker;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.nbt.NbtCompound;
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
import net.minecraft.util.math.Box;
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
        if (!(entity instanceof ServerPlayerEntity player)) return;

        String currentPath = world.getRegistryKey().getValue().getPath();
        if (!currentPath.startsWith("pocket_dimension_")) return;

        String keystoneName = currentPath.substring("pocket_dimension_".length());
        String playerUuid = player.getUuidAsString();

        preparePlayerForTeleport(player);
        world.playSound(null, pos, SoundEvents.ITEM_BUNDLE_DROP_CONTENTS, SoundCategory.PLAYERS, 2.0f, 1.0f);

        MinecraftServer server = world.getServer();
        SuitcaseLocationTracker tracker = SuitcaseLocationTracker.get(server);

        UUID expectedSuitcaseId = (tracker == null) ? null : tracker.getLastSuitcase(keystoneName, playerUuid);

        boolean teleported = false;

        //Method 0: Authoritative suitcaseId -> location
        if (!teleported && tracker != null && expectedSuitcaseId != null) {
            SuitcaseLocationTracker.SuitcaseInstanceLocation sLoc = tracker.getSuitcaseLocation(expectedSuitcaseId);
            if (sLoc != null && sLoc.type != SuitcaseLocationTracker.LocationType.DESTROYED) {
                ServerWorld targetWorld = worldFromId(server, sLoc.dimensionId);
                if (targetWorld != null) {
                    teleportToPosition(player, targetWorld, sLoc.x, sLoc.y, sLoc.z, player.getYaw(), player.getPitch());
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
                    if (targetWorld != null) {
                        teleportToPosition(player, targetWorld, loc.x, loc.y, loc.z, loc.yaw, loc.pitch);
                        teleported = true;
                    }
                }
            }
        }

        //Method 2: Inventory)
        if (!teleported && expectedSuitcaseId != null) {
            outer:
            for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
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


        //Method 3: Dropped items near players
        if (!teleported && expectedSuitcaseId != null) {
            for (ServerWorld w : server.getWorlds()) {
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    if (p.getServerWorld() != w) continue;

                    Box box = p.getBoundingBox().expand(256);
                    List<ItemEntity> items = w.getEntitiesByClass(ItemEntity.class, box,
                            item -> isSuitcaseWithKeystoneAndId(item.getStack(), keystoneName, expectedSuitcaseId));

                    if (!items.isEmpty()) {
                        ItemEntity suitcaseItem = items.get(0);
                        cleanUpSuitcaseItemNbt(suitcaseItem.getStack(), player, keystoneName);
                        suitcaseItem.setStack(suitcaseItem.getStack());

                        teleportToPosition(player, w,
                                suitcaseItem.getX(), suitcaseItem.getY() + 1.0, suitcaseItem.getZ(),
                                player.getYaw(), player.getPitch());
                        teleported = true;
                        break;
                    }
                }
                if (teleported) break;
            }
        }

        //Method 4: Containers
        if (!teleported && expectedSuitcaseId != null) {
            teleported = searchContainersForSuitcase(server, player, keystoneName, expectedSuitcaseId);
        }

        //Method 5: ENTRY fallback
        if (!teleported && tracker != null) {
            SuitcaseLocationTracker.LocationData entry = tracker.getEntryLocation(keystoneName, playerUuid);
            if (entry != null) {
                ServerWorld targetWorld = worldFromId(server, entry.dimensionId);
                if (targetWorld != null) {
                    player.sendMessage(Text.literal("§6Returning to entry point"), true);
                    teleportToPosition(player, targetWorld,
                            entry.x, entry.y, entry.z,
                            entry.yaw, entry.pitch);
                    teleported = true;
                }
            }
        }

        if (!teleported) {
            player.sendMessage(Text.literal("§cNo exit point found - Returning to spawn"), true);

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
    private static Vec3d findSafeSpotNear(ServerWorld world, BlockPos origin) {
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

    private boolean searchContainersForSuitcase(MinecraftServer server, ServerPlayerEntity exitingPlayer,
                                                String keystoneName, UUID expectedSuitcaseId) {
        for (ServerPlayerEntity onlinePlayer : server.getPlayerManager().getPlayerList()) {
            ServerWorld playerWorld = onlinePlayer.getServerWorld();

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

    private void preparePlayerForTeleport(ServerPlayerEntity player) {
        player.stopRiding();
        player.velocityModified = true;
        player.setVelocity(Vec3d.ZERO);
        player.fallDistance = 0f;
    }

    //Exact teleport
    private void teleportToPosition(ServerPlayerEntity player, ServerWorld targetWorld,
                                    double x, double y, double z, float yaw, float pitch) {
        player.requestTeleport(x, y, z);
        targetWorld.getServer().execute(() -> player.teleport(targetWorld, x, y, z, yaw, pitch));
    }

    private ServerWorld worldFromId(MinecraftServer server, String dimStr) {
        if (dimStr == null || dimStr.isEmpty()) return server.getWorld(World.OVERWORLD);

        Identifier id = Identifier.tryParse(dimStr);
        if (id == null) return server.getWorld(World.OVERWORLD);

        RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, id);
        ServerWorld w = server.getWorld(key);
        return (w != null) ? w : server.getWorld(World.OVERWORLD);
    }

    private boolean isSuitcaseWithKeystoneAndId(ItemStack stack, String keystoneName, UUID expectedSuitcaseId) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem bi)
                || !(bi.getBlock() instanceof SuitcaseBlock)) {
            return false;
        }

        NbtComponent beTag = stack.get(DataComponentTypes.BLOCK_ENTITY_DATA);
        if (beTag == null) return false;

        NbtCompound nbt = beTag.copyNbt();
        if (!keystoneName.equals(nbt.getString("BoundKeystone"))) return false;

        if (!nbt.containsUuid(SuitcaseBlockEntity.NBT_SUITCASE_ID)) return false;
        UUID id = nbt.getUuid(SuitcaseBlockEntity.NBT_SUITCASE_ID);

        return expectedSuitcaseId.equals(id);
    }

    private void cleanUpSuitcaseItemNbt(ItemStack stack, ServerPlayerEntity player, String keystoneName) {
        NbtComponent component = stack.get(DataComponentTypes.BLOCK_ENTITY_DATA);
        if (component == null) return;

        NbtElement raw = component.copyNbt();
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
                stack.set(DataComponentTypes.BLOCK_ENTITY_DATA, NbtComponent.of(tag));
                updateItemLore(stack, kept.size(), keystoneName, tag.getBoolean("Locked"));
            }
        }
    }

    private boolean containsSuitcaseWithKeystoneAndId(ItemStack stack, String keystone, UUID id, int depth) {
        if (depth <= 0 || stack.isEmpty()) return false;

        if (isSuitcaseWithKeystoneAndId(stack, keystone, id)) return true;

        ContainerComponent cc = stack.get(DataComponentTypes.CONTAINER);
        if (cc == null) return false;

        for (ItemStack inner : cc.iterateNonEmpty()) {
            if (containsSuitcaseWithKeystoneAndId(inner, keystone, id, depth - 1)) return true;
        }
        return false;
    }


    private void updateItemLore(ItemStack stack, int playerCount, String keystone, boolean locked) {
        List<Text> lines = new ArrayList<>();

        if (playerCount > 0) {
            lines.add(Text.literal("⚠ Contains " + playerCount + " Traveler(s)!")
                    .formatted(Formatting.RED));
        }

        lines.add(Text.literal("Bound to: " + (locked ? "§k" : "") + keystone.replace("_", " "))
                .formatted(Formatting.GRAY));
        lines.add(Text.literal(locked ? "§cLocked" : "§aUnlocked")
                .formatted(Formatting.GRAY));

        stack.set(DataComponentTypes.LORE, new LoreComponent(lines));
    }
}
