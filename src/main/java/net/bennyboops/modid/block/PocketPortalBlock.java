package net.bennyboops.modid.block;

import net.bennyboops.modid.block.entity.SuitcaseBlockEntity;
import net.bennyboops.modid.data.SuitcaseLocationTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class PocketPortalBlock extends Block {

    public PocketPortalBlock(Properties settings) {
        super(settings);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return Collections.singletonList(new ItemStack(this));
    }

    private static final int MAX_NESTED_CONTAINER_DEPTH = 4;

    @Override
    protected void entityInside(BlockState state, Level world, BlockPos pos, Entity entity) {
        if (world.isClientSide) return;
        if (!(entity instanceof ServerPlayer player)) return;

        String currentPath = world.dimension().location().getPath();
        if (!currentPath.startsWith("pocket_dimension_")) return;

        String keystoneName = currentPath.substring("pocket_dimension_".length());
        String playerUuid = player.getStringUUID();

        preparePlayerForTeleport(player);
        world.playSound(null, pos, SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 2.0f, 1.0f);

        MinecraftServer server = world.getServer();
        SuitcaseLocationTracker tracker = SuitcaseLocationTracker.get(server);

        UUID expectedSuitcaseId = (tracker == null) ? null : tracker.getLastSuitcase(keystoneName, playerUuid);

        boolean teleported = false;

        //Method 0: Authoritative suitcaseId -> location
        if (!teleported && tracker != null && expectedSuitcaseId != null) {
            SuitcaseLocationTracker.SuitcaseInstanceLocation sLoc = tracker.getSuitcaseLocation(expectedSuitcaseId);
            if (sLoc != null && sLoc.type != SuitcaseLocationTracker.LocationType.DESTROYED) {
                ServerLevel targetWorld = worldFromId(server, sLoc.dimensionId);
                if (targetWorld != null) {
                    teleportToPosition(player, targetWorld, sLoc.x, sLoc.y, sLoc.z, player.getYRot(), player.getXRot());
                    teleported = true;
                }
            }
        }

        //Method 1: Latest per-player tracked (only if it matches suitcaseId)
        if (!teleported && tracker != null) {
            SuitcaseLocationTracker.LocationData loc = tracker.getLocation(keystoneName, playerUuid);
            if (loc != null && loc.type != SuitcaseLocationTracker.LocationType.DESTROYED) {
                if (expectedSuitcaseId == null || (loc.suitcaseId != null && expectedSuitcaseId.equals(loc.suitcaseId))) {
                    ServerLevel targetWorld = worldFromId(server, loc.dimensionId);
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
            for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                for (int i = 0; i < online.getInventory().getContainerSize(); i++) {
                    ItemStack stack = online.getInventory().getItem(i);

                    if (containsSuitcaseWithKeystoneAndId(stack, keystoneName, expectedSuitcaseId, MAX_NESTED_CONTAINER_DEPTH)) {

                        ServerLevel targetWorld = online.serverLevel();
                        teleportToPosition(player, targetWorld,
                                online.getX(), online.getY() + 1.0, online.getZ(),
                                player.getYRot(), player.getXRot());

                        teleported = true;
                        break outer;
                    }
                }
            }
        }


        //Method 3: Dropped items near players
        if (!teleported && expectedSuitcaseId != null) {
            for (ServerLevel w : server.getAllLevels()) {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    if (p.serverLevel() != w) continue;

                    AABB box = p.getBoundingBox().inflate(256);
                    List<ItemEntity> items = w.getEntitiesOfClass(ItemEntity.class, box,
                            item -> isSuitcaseWithKeystoneAndId(item.getItem(), keystoneName, expectedSuitcaseId));

                    if (!items.isEmpty()) {
                        ItemEntity suitcaseItem = items.get(0);
                        cleanUpSuitcaseItemNbt(suitcaseItem.getItem(), player, keystoneName);
                        suitcaseItem.setItem(suitcaseItem.getItem());

                        teleportToPosition(player, w,
                                suitcaseItem.getX(), suitcaseItem.getY() + 1.0, suitcaseItem.getZ(),
                                player.getYRot(), player.getXRot());
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
                ServerLevel targetWorld = worldFromId(server, entry.dimensionId);
                if (targetWorld != null) {
                    player.displayClientMessage(Component.literal("§6Returning to entry point"), true);
                    teleportToPosition(player, targetWorld,
                            entry.x, entry.y, entry.z,
                            entry.yaw, entry.pitch);
                    teleported = true;
                }
            }
        }

        if (!teleported) {
            player.displayClientMessage(Component.literal("§cNo exit point found - Returning to spawn"), true);

            MinecraftServer srv = player.getServer();
            if (srv != null) {
                boolean moved = false;

                BlockPos respawnPos = player.getRespawnPosition();
                ResourceKey<Level> respawnDim = player.getRespawnDimension();

                if (respawnPos != null) {
                    ServerLevel respawnWorld = srv.getLevel(respawnDim);
                    if (respawnWorld != null && isValidRespawnBlock(respawnWorld, respawnPos)) {
                        Vec3 safe = findSafeSpotNear(respawnWorld, respawnPos);
                        if (safe != null) {
                            teleportToPosition(player, respawnWorld, safe.x, safe.y, safe.z, player.getYRot(), player.getXRot());
                            moved = true;
                        }
                    }
                }

                if (!moved) {
                    ServerLevel overworld = srv.overworld();
                    BlockPos worldSpawn = overworld.getSharedSpawnPos();
                    Vec3 safe = findSafeSpotNear(overworld, worldSpawn);
                    if (safe == null) safe = new Vec3(worldSpawn.getX() + 0.5, worldSpawn.getY() + 0.1, worldSpawn.getZ() + 0.5);

                    teleportToPosition(player, overworld, safe.x, safe.y, safe.z, overworld.getSharedSpawnAngle(), player.getXRot());
                }
            }
        }

        SuitcaseBlockEntity.removeSuitcaseEntry(keystoneName, playerUuid, server);
    }

    private static boolean isValidRespawnBlock(ServerLevel world, BlockPos pos) {
        var state = world.getBlockState(pos);
        return state.getBlock() instanceof net.minecraft.world.level.block.BedBlock
                || state.is(net.minecraft.world.level.block.Blocks.RESPAWN_ANCHOR);
    }

    private static Vec3 findSafeSpotNear(ServerLevel world, BlockPos origin) {
        for (int dy = 0; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos p = origin.offset(dx, dy, dz);
                    BlockPos below = p.below();

                    if (!world.getBlockState(below).isRedstoneConductor(world, below)) continue;

                    if (!world.getBlockState(p).getCollisionShape(world, p).isEmpty()) continue;
                    BlockPos pUp = p.above();
                    if (!world.getBlockState(pUp).getCollisionShape(world, pUp).isEmpty()) continue;

                    return new Vec3(p.getX() + 0.5, p.getY() + 0.1, p.getZ() + 0.5);
                }
            }
        }
        return null;
    }

    private boolean searchContainersForSuitcase(MinecraftServer server, ServerPlayer exitingPlayer,
                                                String keystoneName, UUID expectedSuitcaseId) {
        for (ServerPlayer onlinePlayer : server.getPlayerList().getPlayers()) {
            ServerLevel playerWorld = onlinePlayer.serverLevel();

            ChunkPos playerChunk = onlinePlayer.chunkPosition();
            int radius = 8;

            for (int x = playerChunk.x - radius; x <= playerChunk.x + radius; x++) {
                for (int z = playerChunk.z - radius; z <= playerChunk.z + radius; z++) {
                    if (!playerWorld.hasChunk(x, z)) continue;

                    LevelChunk chunk = playerWorld.getChunk(x, z);
                    for (var blockEntity : chunk.getBlockEntities().values()) {
                        if (!(blockEntity instanceof Container inventory)) continue;

                        for (int i = 0; i < inventory.getContainerSize(); i++) {
                            ItemStack stack = inventory.getItem(i);
                            if (isSuitcaseWithKeystoneAndId(stack, keystoneName, expectedSuitcaseId)) {
                                cleanUpSuitcaseItemNbt(stack, exitingPlayer, keystoneName);
                                inventory.setItem(i, stack);

                                BlockPos containerPos = blockEntity.getBlockPos();
                                teleportToPosition(exitingPlayer, playerWorld,
                                        containerPos.getX() + 0.5,
                                        containerPos.getY() + 1.0,
                                        containerPos.getZ() + 0.5,
                                        exitingPlayer.getYRot(), exitingPlayer.getXRot());
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private void preparePlayerForTeleport(ServerPlayer player) {
        player.stopRiding();
        player.hasImpulse = true;
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0f;
    }

    //Exact teleport
    private void teleportToPosition(ServerPlayer player, ServerLevel targetWorld,
                                    double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(x, y, z);
        targetWorld.getServer().execute(() -> player.teleportTo(targetWorld, x, y, z, yaw, pitch));
    }

    private ServerLevel worldFromId(MinecraftServer server, String dimStr) {
        if (dimStr == null || dimStr.isEmpty()) return server.getLevel(Level.OVERWORLD);

        ResourceLocation id = ResourceLocation.tryParse(dimStr);
        if (id == null) return server.getLevel(Level.OVERWORLD);

        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
        ServerLevel w = server.getLevel(key);
        return (w != null) ? w : server.getLevel(Level.OVERWORLD);
    }

    private boolean isSuitcaseWithKeystoneAndId(ItemStack stack, String keystoneName, UUID expectedSuitcaseId) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem bi)
                || !(bi.getBlock() instanceof SuitcaseBlock)) {
            return false;
        }

        CustomData beTag = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (beTag == null) return false;

        CompoundTag nbt = beTag.copyTag();
        if (!keystoneName.equals(nbt.getString("BoundKeystone"))) return false;

        if (!nbt.hasUUID(SuitcaseBlockEntity.NBT_SUITCASE_ID)) return false;
        UUID id = nbt.getUUID(SuitcaseBlockEntity.NBT_SUITCASE_ID);

        return expectedSuitcaseId.equals(id);
    }

    private void cleanUpSuitcaseItemNbt(ItemStack stack, ServerPlayer player, String keystoneName) {
        CustomData component = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (component == null) return;

        CompoundTag tag = component.copyTag();

        if (tag.contains("EnteredPlayers", Tag.TAG_LIST)) {
            ListTag old = tag.getList("EnteredPlayers", Tag.TAG_COMPOUND);
            ListTag kept = new ListTag();
            boolean removed = false;

            for (int i = 0; i < old.size(); i++) {
                CompoundTag entry = old.getCompound(i);
                if (player.getStringUUID().equals(entry.getString("UUID"))) removed = true;
                else kept.add(entry);
            }

            if (removed) {
                tag.put("EnteredPlayers", kept);
                stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(tag));
                updateItemLore(stack, kept.size(), keystoneName, tag.getBoolean("Locked"));
            }
        }
    }

    private boolean containsSuitcaseWithKeystoneAndId(ItemStack stack, String keystone, UUID id, int depth) {
        if (depth <= 0 || stack.isEmpty()) return false;

        if (isSuitcaseWithKeystoneAndId(stack, keystone, id)) return true;

        ItemContainerContents cc = stack.get(DataComponents.CONTAINER);
        if (cc == null) return false;

        for (ItemStack inner : cc.nonEmptyItems()) {
            if (containsSuitcaseWithKeystoneAndId(inner, keystone, id, depth - 1)) return true;
        }
        return false;
    }


    private void updateItemLore(ItemStack stack, int playerCount, String keystone, boolean locked) {
        List<Component> lines = new ArrayList<>();

        if (playerCount > 0) {
            lines.add(Component.literal("⚠ Contains " + playerCount + " Traveler(s)!")
                    .withStyle(ChatFormatting.RED));
        }

        lines.add(Component.literal("Bound to: " + (locked ? "§k" : "") + keystone.replace("_", " "))
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal(locked ? "§cLocked" : "§aUnlocked")
                .withStyle(ChatFormatting.GRAY));

        stack.set(DataComponents.LORE, new ItemLore(lines));
    }
}
