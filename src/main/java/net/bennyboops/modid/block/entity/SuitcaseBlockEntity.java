package net.bennyboops.modid.block.entity;

import net.minecraft.server.ServerTask;
import net.minecraft.server.world.ServerWorld;
import net.bennyboops.modid.block.SuitcaseBlock;
import net.minecraft.block.Block;
import net.bennyboops.modid.data.SuitcaseLocationTracker;
import net.bennyboops.modid.data.SuitcaseRegistrySavedData;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class SuitcaseBlockEntity extends BlockEntity {

    public static final String NBT_SUITCASE_ID = "SuitcaseId";
    private UUID suitcaseId = UUID.randomUUID();
    public UUID getSuitcaseId() {
        return suitcaseId;
    }

    // The placed pocket blocks in loaded chunks, by suitcase id, to tell a block that was broken from one that
    // another mod moved somewhere else
    private static final Map<UUID, SuitcaseBlockEntity> PLACED = new HashMap<>();
    // The server tick this block entity was put into the world
    private long placedTick = -1;

    /**
     * Whether a pocket block with this suitcase id turned up somewhere else during the given tick, which is what
     * happens when another mod moves it. (A copy placed earlier, like a creative-mode duplicate, doesn't count.)
     */
    public boolean movedElsewhere(long tick) {
        SuitcaseBlockEntity other = PLACED.get(suitcaseId);
        return other != null && other != this && !other.isRemoved() && other.placedTick >= tick;
    }

    @Override
    public void markRemoved() {
        super.markRemoved();
        PLACED.remove(suitcaseId, this);
    }

    // Called whenever the block entity is put into a world: placed, loaded, or moved there by another mod (Sable
    // assembling it into a physics object, for one). If it has moved, the way back out follows it
    @Override
    public void cancelRemoval() {
        super.cancelRemoval();
        register();
    }

    /**
     * Notes this block entity as placed, and checks whether it has moved. Done when it's put into a world and again
     * when its data is loaded into it, since a mod that moves blocks (Sable does) loads the data after placing it.
     */
    private void register() {
        if (!(world instanceof ServerWorld serverWorld) || isRemoved()) return;
        MinecraftServer server = serverWorld.getServer();
        placedTick = server.getTicks();
        PLACED.values().remove(this);
        PLACED.put(suitcaseId, this);
        if (boundKeystoneName != null) {
            BlockPos at = pos.toImmutable();
            // Queued for the end of the tick, once the block is fully in place
            server.send(new ServerTask(server.getTicks(), () -> SuitcaseBlock.retrackIfMoved(serverWorld, at)));
        }
    }

    private String boundKeystoneName;
    private boolean isLocked = false;
    private boolean dimensionLocked = false;
    private final List<EnteredPlayerData> enteredPlayers = new ArrayList<>();
    public static class EnteredPlayerData {
        public final String uuid;
        public final double x;
        public final double y;
        public final double z;
        public final float pitch;
        public final float yaw;
        public final BlockPos suitcasePos;

        public EnteredPlayerData(String uuid, double x, double y, double z, float pitch, float yaw, BlockPos suitcasePos) {
            this.uuid = uuid;
            this.x = x;
            this.y = y;
            this.z = z;
            this.pitch = pitch;
            this.yaw = yaw;
            this.suitcasePos = suitcasePos;
        }

        public NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putString("UUID", uuid);
            nbt.putDouble("X", x);
            nbt.putDouble("Y", y);
            nbt.putDouble("Z", z);
            nbt.putFloat("Pitch", pitch);
            nbt.putFloat("Yaw", yaw);
            nbt.putInt("SuitcaseX", suitcasePos.getX());
            nbt.putInt("SuitcaseY", suitcasePos.getY());
            nbt.putInt("SuitcaseZ", suitcasePos.getZ());
            return nbt;
        }

        public static EnteredPlayerData fromNbt(NbtCompound nbt) {
            return new EnteredPlayerData(
                    nbt.getString("UUID"),
                    nbt.getDouble("X"),
                    nbt.getDouble("Y"),
                    nbt.getDouble("Z"),
                    nbt.getFloat("Pitch"),
                    nbt.getFloat("Yaw"),
                    new BlockPos(nbt.getInt("SuitcaseX"), nbt.getInt("SuitcaseY"), nbt.getInt("SuitcaseZ"))
            );
        }
    }

    // Client-only lid animation for the Secret Chest, with the same timing as a vanilla chest
    private float lidProgress;
    private float lastLidProgress;

    public SuitcaseBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SUITCASE_BLOCK_ENTITY, pos, state);
    }

    public void tickLid(boolean open) {
        lastLidProgress = lidProgress;
        lidProgress = open ? Math.min(lidProgress + 0.1F, 1.0F) : Math.max(lidProgress - 0.1F, 0.0F);
    }

    public float getLidProgress(float tickDelta) {
        return net.minecraft.util.math.MathHelper.lerp(tickDelta, lastLidProgress, lidProgress);
    }

    public boolean canOpenInDimension(World world) {
        if (world == null) return true;
        if (boundKeystoneName == null || boundKeystoneName.isEmpty()) return true;

        String currentDim = world.getRegistryKey().getValue().toString();
        String ownPocketDim = "pocket-repose:pocket_dimension_" + boundKeystoneName;

        return !currentDim.equals(ownPocketDim);
    }

    public boolean isDimensionLocked() {
        return dimensionLocked;
    }

    public void playerEntered(ServerPlayerEntity player) {
        enteredPlayers.removeIf(data -> data.uuid.equals(player.getUuidAsString()));

        EnteredPlayerData data = new EnteredPlayerData(
                player.getUuidAsString(),
                player.getX(), player.getY(), player.getZ(),
                player.getPitch(), player.getYaw(),
                this.getPos()
        );
        enteredPlayers.add(data);

        Map<String, BlockPos> suitcases = SUITCASE_REGISTRY.computeIfAbsent(
                boundKeystoneName, k -> new HashMap<>()
        );
        suitcases.put(player.getUuidAsString(), this.getPos());

        MinecraftServer server = player.getServer();
        if (server != null && boundKeystoneName != null) {
            SuitcaseLocationTracker tracker = SuitcaseLocationTracker.get(server);
            if (tracker != null) {

                tracker.setLastSuitcase(boundKeystoneName, player.getUuidAsString(), this.getSuitcaseId());

                String dimId = player.getServerWorld().getRegistryKey().getValue().toString();
                UUID suitcaseId = this.getSuitcaseId();

                tracker.setLastSuitcase(boundKeystoneName, player.getUuidAsString(), suitcaseId);

                tracker.updateLocation(
                        boundKeystoneName,
                        player.getUuidAsString(),
                        dimId,
                        suitcaseId,
                        player.getX(), player.getY(), player.getZ(),
                        player.getYaw(), player.getPitch(),
                        SuitcaseLocationTracker.LocationType.ENTRY
                );

            }
            SuitcaseRegistrySavedData.onRegistryChanged(server);
        }

        markDirty();
    }

    public EnteredPlayerData getExitPosition(String playerUuid) {
        for (EnteredPlayerData data : enteredPlayers) {
            if (data.uuid.equals(playerUuid)) {
                EnteredPlayerData exitData = new EnteredPlayerData(
                        data.uuid,
                        this.getPos().getX() + 0.5,
                        this.getPos().getY() + 1.0,
                        this.getPos().getZ() + 0.5,
                        data.pitch, data.yaw,
                        this.getPos()
                );
                enteredPlayers.remove(data);
                markDirty();
                return exitData;
            }
        }
        return null;
    }

    public void bindKeystone(String keystoneName) {
        this.boundKeystoneName = keystoneName;
        markDirtyAndSync();
    }

    /** Saves the change and sends it to nearby players, whose game uses it to open pocket doors without lag. */
    private void markDirtyAndSync() {
        markDirty();
        if (world != null && !world.isClient) {
            world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
        }
    }

    public String getBoundKeystoneName() {
        return boundKeystoneName;
    }

    public void setLocked(boolean locked) {
        this.isLocked = locked;
        markDirtyAndSync();
    }

    public boolean isLocked() {
        return isLocked;
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        nbt.putUuid(NBT_SUITCASE_ID, suitcaseId);

        if (boundKeystoneName != null) nbt.putString("BoundKeystone", boundKeystoneName);

        nbt.putBoolean("Locked", isLocked);
        nbt.putBoolean("DimensionLocked", dimensionLocked);

        NbtList players = new NbtList();
        for (EnteredPlayerData data : enteredPlayers) players.add(data.toNbt());
        nbt.put("EnteredPlayers", players);

        super.writeNbt(nbt);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);

        if (nbt.containsUuid(NBT_SUITCASE_ID)) {
            suitcaseId = nbt.getUuid(NBT_SUITCASE_ID);
        } else {
            suitcaseId = UUID.randomUUID();
        }

        boundKeystoneName = nbt.contains("BoundKeystone") ? nbt.getString("BoundKeystone") : null;
        isLocked = nbt.getBoolean("Locked");
        dimensionLocked = nbt.contains("DimensionLocked") && nbt.getBoolean("DimensionLocked");

        enteredPlayers.clear();
        if (nbt.contains("EnteredPlayers", NbtElement.LIST_TYPE)) {
            for (NbtElement e : nbt.getList("EnteredPlayers", NbtElement.COMPOUND_TYPE)) {
                enteredPlayers.add(EnteredPlayerData.fromNbt((NbtCompound) e));
            }
        }
        register();
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return createNbtWithId();
    }

    public static final Map<String, Map<String, BlockPos>> SUITCASE_REGISTRY =
            Collections.synchronizedMap(new HashMap<>());

    public static BlockPos findSuitcasePosition(String keystoneName, String playerUuid) {
        Map<String, BlockPos> suitcases = SUITCASE_REGISTRY.get(keystoneName);
        if (suitcases != null) return suitcases.get(playerUuid);
        return null;
    }

    public static void removeSuitcaseEntry(String keystoneName, String playerUuid, MinecraftServer server) {
        Map<String, BlockPos> suitcases = SUITCASE_REGISTRY.get(keystoneName);
        if (suitcases != null) {
            suitcases.remove(playerUuid);
            if (suitcases.isEmpty()) SUITCASE_REGISTRY.remove(keystoneName);
            if (server != null) SuitcaseRegistrySavedData.onRegistryChanged(server);
        }
    }

    public List<EnteredPlayerData> getEnteredPlayers() {
        return enteredPlayers;
    }

    public void updatePlayerSuitcasePosition(String playerUuid, BlockPos newPos) {
        for (int i = 0; i < enteredPlayers.size(); i++) {
            EnteredPlayerData data = enteredPlayers.get(i);
            if (data.uuid.equals(playerUuid)) {
                EnteredPlayerData updatedData = new EnteredPlayerData(
                        data.uuid,
                        data.x, data.y, data.z,
                        data.pitch, data.yaw,
                        newPos
                );
                enteredPlayers.set(i, updatedData);
                break;
            }
        }

        if (boundKeystoneName != null) {
            Map<String, BlockPos> suitcases = SUITCASE_REGISTRY.computeIfAbsent(boundKeystoneName, k -> new HashMap<>());
            suitcases.put(playerUuid, newPos);
        }

        markDirty();
    }

    public static void initializeSuitcaseRegistry(Map<String, Map<String, BlockPos>> savedRegistry) {
        SUITCASE_REGISTRY.clear();
        for (Map.Entry<String, Map<String, BlockPos>> entry : savedRegistry.entrySet()) {
            Map<String, BlockPos> players = SUITCASE_REGISTRY.computeIfAbsent(entry.getKey(), k -> new HashMap<>());
            players.putAll(entry.getValue());
        }
    }

    public static void saveSuitcaseRegistryTo(Map<String, Map<String, BlockPos>> destination) {
        destination.clear();
        for (Map.Entry<String, Map<String, BlockPos>> entry : SUITCASE_REGISTRY.entrySet()) {
            Map<String, BlockPos> players = destination.computeIfAbsent(entry.getKey(), k -> new HashMap<>());
            players.putAll(entry.getValue());
        }
    }
}
