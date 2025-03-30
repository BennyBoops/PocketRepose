package net.bennyboops.modid.block;

import net.bennyboops.modid.block.entity.SuitcaseBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.entity.Entity;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PocketPortalBlock extends Block {
    private static final Map<String, PlayerPositionData> LAST_KNOWN_POSITIONS = new HashMap<>();
    private static final int SEARCH_RADIUS_CHUNKS = 12;
    public static class PlayerPositionData {
        public final double x;
        public final double y;
        public final double z;
        public final float yaw;
        public final float pitch;
        public final long timestamp;
        public PlayerPositionData(double x, double y, double z, float yaw, float pitch) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public PocketPortalBlock(Settings settings) {
        super(settings);
    }

    public static void storePlayerPosition(ServerPlayerEntity player) {
        LAST_KNOWN_POSITIONS.put(
                player.getUuidAsString(),
                new PlayerPositionData(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch())
        );
    }

    private boolean attemptPlayerInventorySuitcaseTeleport(World world, ServerWorld overworld, ServerPlayerEntity player, String keystoneName) {
        for (ServerPlayerEntity serverPlayer : world.getServer().getPlayerManager().getPlayerList()) {
            if (scanPlayerInventoryForSuitcase(serverPlayer, player, keystoneName, world, overworld)) {
                return true;
            }
        }
        return false;
    }

    private boolean scanPlayerInventoryForSuitcase(ServerPlayerEntity inventoryOwner, ServerPlayerEntity exitingPlayer,
                                                   String keystoneName, World world, ServerWorld overworld) {
        for (int i = 0; i < inventoryOwner.getInventory().size(); i++) {
            ItemStack stack = inventoryOwner.getInventory().getStack(i);
            if (isSuitcaseItemWithKeystone(stack, keystoneName)) {
                cleanUpSuitcaseItemNbt(stack, exitingPlayer, keystoneName);
                inventoryOwner.getInventory().setStack(i, stack);
                teleportToPosition(world, exitingPlayer, overworld,
                        inventoryOwner.getX(), inventoryOwner.getY() + 1.0, inventoryOwner.getZ(),
                        exitingPlayer.getYaw(), exitingPlayer.getPitch());

                return true;
            }
        }
        return false;
    }

    private boolean isSuitcaseItemWithKeystone(ItemStack stack, String keystoneName) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem) ||
                !(((BlockItem) stack.getItem()).getBlock() instanceof SuitcaseBlock)) {
            return false;
        }

        if (!stack.hasNbt()) return false;
        NbtCompound beTag = stack.getSubNbt("BlockEntityTag");
        if (beTag == null) return false;

        return beTag.contains("BoundKeystone") && keystoneName.equals(beTag.getString("BoundKeystone"));
    }

    private void cleanUpSuitcaseItemNbt(ItemStack stack, ServerPlayerEntity player, String keystoneName) {
        if (!stack.hasNbt()) return;
        NbtCompound beTag = stack.getSubNbt("BlockEntityTag");
        if (beTag == null) return;
        if (beTag.contains("EnteredPlayers", NbtElement.LIST_TYPE)) {
            NbtList playersList = beTag.getList("EnteredPlayers", NbtElement.COMPOUND_TYPE);
            NbtList newPlayersList = new NbtList();
            boolean playerFound = false;
            for (int i = 0; i < playersList.size(); i++) {
                NbtCompound playerData = playersList.getCompound(i);
                if (!player.getUuidAsString().equals(playerData.getString("UUID"))) {
                    newPlayersList.add(playerData);
                } else {
                    playerFound = true;
                }
            }
            if (playerFound) {
                beTag.put("EnteredPlayers", newPlayersList);
                int remainingPlayers = newPlayersList.size();
                updateItemLore(stack, remainingPlayers);
            }
        }
        SuitcaseBlockEntity.removeSuitcaseEntry(keystoneName, player.getUuidAsString());
    }

    @Override
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (!world.isClient && entity instanceof ServerPlayerEntity player) {
            String currentDimension = world.getRegistryKey().getValue().getPath();
            if (currentDimension.startsWith("pocket_dimension_")) {
                String keystoneName = currentDimension.replace("pocket_dimension_", "");
                preparePlayerForTeleport(player);
                world.playSound(null, pos, SoundEvents.ITEM_BUNDLE_DROP_CONTENTS, SoundCategory.PLAYERS, 2.0f, 1.0f);
                ServerWorld overworld = world.getServer().getWorld(World.OVERWORLD);
                if (overworld == null) return;

                boolean teleported = false;

                // Method 1: Try to teleport to the original suitcase block entity
                teleported = attemptSuitcaseTeleport(world, overworld, player, keystoneName);

                // Method 2: Try to find suitcase in a player's inventory (new method)
                if (!teleported) {
                    teleported = attemptPlayerInventorySuitcaseTeleport(world, overworld, player, keystoneName);
                }

                // Method 3: Try to find the suitcase as an item entity in the world
                if (!teleported) {
                    teleported = attemptSuitcaseItemTeleport(world, overworld, player, keystoneName);
                }

                // Method 4: Try to use player's last known position
                if (!teleported) {
                    teleported = attemptLastKnownPositionTeleport(world, overworld, player);
                }

                // Fallback: Take them to spawn
                if (!teleported) {
                    player.sendMessage(Text.literal("§cCouldn't find your return point. Taking you to spawn.").formatted(Formatting.RED), true);
                    teleportToPosition(world, player, overworld,
                            overworld.getSpawnPos().getX() + 0.5,
                            overworld.getSpawnPos().getY() + 1.0,
                            overworld.getSpawnPos().getZ() + 0.5, 0, 0);
                }
                SuitcaseBlockEntity.removeSuitcaseEntry(keystoneName, player.getUuidAsString());
                LAST_KNOWN_POSITIONS.remove(player.getUuidAsString());
            }
        }
    }

    private void preparePlayerForTeleport(ServerPlayerEntity player) {
        player.stopRiding();
        player.velocityModified = true;
        player.setVelocity(Vec3d.ZERO);
        player.fallDistance = 0f;
    }

    // Method 1: Try to teleport to the original suitcase block entity
    private boolean attemptSuitcaseTeleport(World world, ServerWorld overworld, ServerPlayerEntity player, String keystoneName) {
        BlockPos suitcasePos = SuitcaseBlockEntity.findSuitcasePosition(keystoneName, player.getUuidAsString());
        if (suitcasePos == null) return false;
        ChunkPos suitcaseChunkPos = new ChunkPos(suitcasePos);
        overworld.setChunkForced(suitcaseChunkPos.x, suitcaseChunkPos.z, true);
        try {
            WorldChunk suitcaseChunk = overworld.getChunk(suitcaseChunkPos.x, suitcaseChunkPos.z);
            BlockEntity targetEntity = suitcaseChunk.getBlockEntity(suitcasePos);
            if (targetEntity instanceof SuitcaseBlockEntity suitcase) {
                SuitcaseBlockEntity.EnteredPlayerData exitData = suitcase.getExitPosition(player.getUuidAsString());
                if (exitData != null) {
                    teleportToPosition(world, player, overworld, exitData.x, exitData.y, exitData.z, exitData.yaw, player.getPitch());
                    world.getServer().execute(() -> {
                        overworld.setChunkForced(suitcaseChunkPos.x, suitcaseChunkPos.z, false);
                    });
                    return true;
                }
            }
        } finally {
            overworld.setChunkForced(suitcaseChunkPos.x, suitcaseChunkPos.z, false);
        }
        return false;
    }

    // Method 2: Try to find the suitcase as an item entity in the world
    private boolean attemptSuitcaseItemTeleport(World world, ServerWorld overworld, ServerPlayerEntity player, String keystoneName) {
        BlockPos searchCenter = null;
        BlockPos suitcasePos = SuitcaseBlockEntity.findSuitcasePosition(keystoneName, player.getUuidAsString());
        if (suitcasePos != null) {
            searchCenter = suitcasePos;
        } else {
            PlayerPositionData lastPos = LAST_KNOWN_POSITIONS.get(player.getUuidAsString());
            if (lastPos != null) {
                searchCenter = new BlockPos((int)lastPos.x, (int)lastPos.y, (int)lastPos.z);
            } else {
                searchCenter = overworld.getSpawnPos();
            }
        }
        int centerX = searchCenter.getX() >> 4;
        int centerZ = searchCenter.getZ() >> 4;
        for (int radius = 0; radius <= SEARCH_RADIUS_CHUNKS; radius++) {
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    if (radius > 0 && x > centerX - radius && x < centerX + radius &&
                            z > centerZ - radius && z < centerZ + radius) {
                        continue;
                    }
                    if (!overworld.isChunkLoaded(x, z)) {
                        continue;
                    }
                    WorldChunk chunk = overworld.getChunk(x, z);
                    List<ItemEntity> itemEntities = overworld.getEntitiesByClass(
                            ItemEntity.class,
                            new Box(chunk.getPos().getStartX(), 0, chunk.getPos().getStartZ(),
                                    chunk.getPos().getEndX(), 256, chunk.getPos().getEndZ()),
                            itemEntity -> {
                                ItemStack stack = itemEntity.getStack();
                                if (!stack.hasNbt()) return false;

                                NbtCompound beTag = stack.getSubNbt("BlockEntityTag");
                                if (beTag == null) return false;

                                return beTag.contains("BoundKeystone") &&
                                        keystoneName.equals(beTag.getString("BoundKeystone"));
                            }
                    );
                    if (!itemEntities.isEmpty()) {
                        ItemEntity suitcaseItem = itemEntities.get(0);
                        cleanUpSuitcaseItemNbt(suitcaseItem, player, keystoneName);
                        teleportToPosition(world, player, overworld,
                                suitcaseItem.getX(), suitcaseItem.getY() + 1.0, suitcaseItem.getZ(),
                                player.getYaw(), player.getPitch());
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // Method 3: Try to use player's last known position
    private boolean attemptLastKnownPositionTeleport(World world, ServerWorld overworld, ServerPlayerEntity player) {
        PlayerPositionData lastPos = LAST_KNOWN_POSITIONS.get(player.getUuidAsString());
        if (lastPos != null) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastPos.timestamp > 10 * 60 * 1000) {
                return false;
            }
            player.sendMessage(Text.literal("§6Returning to your last known position."), true);
            teleportToPosition(world, player, overworld,
                    lastPos.x, lastPos.y, lastPos.z,
                    lastPos.yaw, lastPos.pitch);
            return true;
        }
        return false;
    }

    private void teleportToPosition(World world, ServerPlayerEntity player, ServerWorld targetWorld,
                                    double x, double y, double z, float yaw, float pitch) {
        player.requestTeleport(x, y, z);
        world.getServer().execute(() -> {
            player.teleport(targetWorld, x, y, z, yaw, pitch);
        });
    }

    private void cleanUpSuitcaseItemNbt(ItemEntity suitcaseItem, ServerPlayerEntity player, String keystoneName) {
        ItemStack stack = suitcaseItem.getStack();
        if (!stack.hasNbt()) return;
        NbtCompound beTag = stack.getSubNbt("BlockEntityTag");
        if (beTag == null) return;
        if (beTag.contains("EnteredPlayers", NbtElement.LIST_TYPE)) {
            NbtList playersList = beTag.getList("EnteredPlayers", NbtElement.COMPOUND_TYPE);
            NbtList newPlayersList = new NbtList();
            boolean playerFound = false;
            for (int i = 0; i < playersList.size(); i++) {
                NbtCompound playerData = playersList.getCompound(i);
                if (!player.getUuidAsString().equals(playerData.getString("UUID"))) {
                    newPlayersList.add(playerData);
                } else {
                    playerFound = true;
                }
            }
            if (playerFound) {
                beTag.put("EnteredPlayers", newPlayersList);
                int remainingPlayers = newPlayersList.size();
                updateItemLore(stack, remainingPlayers);
                suitcaseItem.setStack(stack);
            }
        }
        SuitcaseBlockEntity.removeSuitcaseEntry(keystoneName, player.getUuidAsString());
    }

    private void updateItemLore(ItemStack stack, int playerCount) {
        if (stack.hasNbt() && stack.getNbt().contains("display")) {
            NbtCompound display = stack.getSubNbt("display");
            if (display != null && display.contains("Lore")) {
                NbtList lore = display.getList("Lore", NbtElement.STRING_TYPE);
                NbtList newLore = new NbtList();
                for (int i = 0; i < lore.size(); i++) {
                    String loreStr = lore.getString(i);
                    if (!loreStr.contains("traveler")) {
                        newLore.add(lore.get(i));
                    }
                }
                if (playerCount > 0) {
                    Text warningText = Text.literal("§c⚠ Contains " + playerCount + " traveler(s)!")
                            .formatted(Formatting.RED);
                    newLore.add(0, NbtString.of(Text.Serializer.toJson(warningText)));
                }
                display.put("Lore", newLore);
            }
        }
    }
}