package net.bennyboops.modid.block;

import net.bennyboops.modid.block.entity.SuitcaseBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
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

    // Cache of player's last known positions in the overworld before entering any pocket dimension
    private static final Map<String, PlayerPositionData> LAST_KNOWN_POSITIONS = new HashMap<>();

    // Search radius for item entities
    private static final int SEARCH_RADIUS_CHUNKS = 12; // Search in a 24x24 chunk area

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

    // Static method to store player's position before entering a pocket dimension
    public static void storePlayerPosition(ServerPlayerEntity player) {
        LAST_KNOWN_POSITIONS.put(
                player.getUuidAsString(),
                new PlayerPositionData(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch())
        );
    }

    @Override
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (!world.isClient && entity instanceof ServerPlayerEntity player) {
            String currentDimension = world.getRegistryKey().getValue().getPath();

            // Only process if player is in a pocket dimension
            if (currentDimension.startsWith("pocket_dimension_")) {
                String keystoneName = currentDimension.replace("pocket_dimension_", "");

                // Prepare player for teleport
                preparePlayerForTeleport(player);
                world.playSound(null, pos, SoundEvents.ITEM_BUNDLE_DROP_CONTENTS, SoundCategory.PLAYERS, 2.0f, 1.0f);

                // Try teleport methods in order of preference
                ServerWorld overworld = world.getServer().getWorld(World.OVERWORLD);
                if (overworld == null) return;
                boolean teleported = attemptSuitcaseTeleport(world, overworld, player, keystoneName);

                if (!teleported) {
                    teleported = attemptSuitcaseItemTeleport(world, world.getServer().getWorld(World.OVERWORLD), player, keystoneName);
                }

                if (!teleported) {
                    teleported = attemptLastKnownPositionTeleport(world, world.getServer().getWorld(World.OVERWORLD), player);
                }

                // Final fallback - world spawn
                if (!teleported) {
                    overworld = world.getServer().getWorld(World.OVERWORLD);
                    if (overworld != null) {
                        player.sendMessage(Text.literal("§cCouldn't find your return point. Taking you to spawn."), true);
                        teleportToPosition(world, player, overworld, overworld.getSpawnPos().getX() + 0.5,
                                overworld.getSpawnPos().getY() + 1.0, overworld.getSpawnPos().getZ() + 0.5, 0, 0);
                    }
                }

                // Always clean up registry entry
                SuitcaseBlockEntity.removeSuitcaseEntry(keystoneName, player.getUuidAsString());
                LAST_KNOWN_POSITIONS.remove(player.getUuidAsString());
            }
        }
    }

    // Helper method for preparing player for teleport
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

        // Force load the chunk
        ChunkPos suitcaseChunkPos = new ChunkPos(suitcasePos);
        overworld.setChunkForced(suitcaseChunkPos.x, suitcaseChunkPos.z, true);

        try {
            // Get the chunk and verify the suitcase
            WorldChunk suitcaseChunk = overworld.getChunk(suitcaseChunkPos.x, suitcaseChunkPos.z);
            BlockEntity targetEntity = suitcaseChunk.getBlockEntity(suitcasePos);

            if (targetEntity instanceof SuitcaseBlockEntity suitcase) {
                SuitcaseBlockEntity.EnteredPlayerData exitData = suitcase.getExitPosition(player.getUuidAsString());

                if (exitData != null) {
                    // Verify the suitcase position matches where we expect it to be
                    if (!exitData.suitcasePos.equals(suitcasePos)) {
                        // Suitcase was moved, update the registry with the current position
                        player.sendMessage(Text.literal("§6Suitcase has been relocated!"), false);
                    }

                    // Execute teleport using the entry point coordinates, not the current suitcase position
                    teleportToPosition(world, player, overworld, exitData.x, exitData.y, exitData.z, exitData.yaw, player.getPitch());

                    // Unforce the chunk after a short delay
                    world.getServer().execute(() -> {
                        overworld.setChunkForced(suitcaseChunkPos.x, suitcaseChunkPos.z, false);
                    });

                    return true;
                }
            }
        } finally {
            // Make sure we unforce the chunk if something went wrong
            overworld.setChunkForced(suitcaseChunkPos.x, suitcaseChunkPos.z, false);
        }

        return false;
    }

    // Method 2: Try to find the suitcase as an item entity in the world
    private boolean attemptSuitcaseItemTeleport(World world, ServerWorld overworld, ServerPlayerEntity player, String keystoneName) {
        // Log the attempt for debugging
        player.sendMessage(Text.literal("§7Searching for your suitcase in the world..."), true);

        // Search in a radius around the last known suitcase position or player's stored position
        BlockPos searchCenter = null;

        // Try to get suitcase position first
        BlockPos suitcasePos = SuitcaseBlockEntity.findSuitcasePosition(keystoneName, player.getUuidAsString());
        if (suitcasePos != null) {
            searchCenter = suitcasePos;
            player.sendMessage(Text.literal("§7Searching near registered position..."), false);
        } else {
            // Fall back to player's last known position
            PlayerPositionData lastPos = LAST_KNOWN_POSITIONS.get(player.getUuidAsString());
            if (lastPos != null) {
                searchCenter = new BlockPos((int)lastPos.x, (int)lastPos.y, (int)lastPos.z);
                player.sendMessage(Text.literal("§7Searching near your last position..."), false);
            } else {
                // If we have no reference point, use world spawn
                searchCenter = overworld.getSpawnPos();
                player.sendMessage(Text.literal("§7No reference point found, searching near spawn..."), false);
            }
        }

        // Calculate search area
        int centerX = searchCenter.getX() >> 4; // Convert to chunk coordinates
        int centerZ = searchCenter.getZ() >> 4;

        // Optimize search by starting from nearest chunks and expanding outward
        for (int radius = 0; radius <= SEARCH_RADIUS_CHUNKS; radius++) {
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    // Only search the perimeter of this radius to avoid rechecking inner chunks
                    if (radius > 0 && x > centerX - radius && x < centerX + radius &&
                            z > centerZ - radius && z < centerZ + radius) {
                        continue;
                    }

                    // Check if the chunk is loaded
                    if (!overworld.isChunkLoaded(x, z)) {
                        continue;
                    }

                    // Get the chunk
                    WorldChunk chunk = overworld.getChunk(x, z);

                    // Search for suitcase items in this chunk
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

                        // Clean up player entry from the item NBT data
                        cleanUpSuitcaseItemNbt(suitcaseItem, player, keystoneName);

                        player.sendMessage(Text.literal("§6Found your suitcase!"), true);


                        // Teleport to the item entity location
                        teleportToPosition(world, player, overworld,
                                suitcaseItem.getX(), suitcaseItem.getY() + 1.0, suitcaseItem.getZ(),
                                player.getYaw(), player.getPitch());
                        return true;
                    }
                }
            }

            // If we've searched a significant area with no results, provide feedback
            if (radius % 4 == 0 && radius > 0) {
                player.sendMessage(Text.literal("§7Expanded search radius to " + radius + " chunks..."), false);
            }
        }

        player.sendMessage(Text.literal("§cCouldn't find your suitcase in the world."), true);
        return false;
    }

    // Method 3: Try to use player's last known position
    private boolean attemptLastKnownPositionTeleport(World world, ServerWorld overworld, ServerPlayerEntity player) {
        PlayerPositionData lastPos = LAST_KNOWN_POSITIONS.get(player.getUuidAsString());
        if (lastPos != null) {
            // Make sure this position isn't too old (e.g., 10 minutes)
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastPos.timestamp > 10 * 60 * 1000) {
                return false; // Position is too old, don't use it
            }

            player.sendMessage(Text.literal("§6Returning to your last known position."), true);

            // Teleport to last known position
            teleportToPosition(world, player, overworld,
                    lastPos.x, lastPos.y, lastPos.z,
                    lastPos.yaw, lastPos.pitch);
            return true;
        }

        return false;
    }



    // Helper method to execute the actual teleport
    private void teleportToPosition(World world, ServerPlayerEntity player, ServerWorld targetWorld,
                                    double x, double y, double z, float yaw, float pitch) {
        // Request initial position update
        player.requestTeleport(x, y, z);

        // Execute teleport on next tick
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

            // Create a new list without the current player
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

            // Only update if we found and removed the player
            if (playerFound) {
                beTag.put("EnteredPlayers", newPlayersList);

                // Update lore to reflect the new count
                int remainingPlayers = newPlayersList.size();
                updateItemLore(stack, remainingPlayers);

                // Update the item entity with the modified stack
                suitcaseItem.setStack(stack);
            }
        }

        // Always make sure to clean up the registry
        SuitcaseBlockEntity.removeSuitcaseEntry(keystoneName, player.getUuidAsString());
    }

    private void updateItemLore(ItemStack stack, int playerCount) {
        if (stack.hasNbt() && stack.getNbt().contains("display")) {
            NbtCompound display = stack.getSubNbt("display");
            if (display != null && display.contains("Lore")) {
                NbtList lore = display.getList("Lore", NbtElement.STRING_TYPE);
                NbtList newLore = new NbtList();

                // Keep non-warning lore entries
                for (int i = 0; i < lore.size(); i++) {
                    String loreStr = lore.getString(i);
                    if (!loreStr.contains("traveler")) {
                        newLore.add(lore.get(i));
                    }
                }

                // Add warning if still players inside
                if (playerCount > 0) {
                    Text warningText = Text.literal("§c⚠ Contains " + playerCount + " traveler(s)!")
                            .formatted(Formatting.RED);
                    newLore.add(0, NbtString.of(Text.Serializer.toJson(warningText)));
                }

                display.put("Lore", newLore);
            }
        }
    }
    private boolean isSuitcaseValid(ServerWorld world, BlockPos pos) {
        if (pos == null || !world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            return false;
        }

        BlockEntity entity = world.getBlockEntity(pos);
        return entity instanceof SuitcaseBlockEntity;
    }

    // Then update the attemptSuitcaseTeleport method to handle relocation:

}