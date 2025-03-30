package net.bennyboops.modid.block.entity;

import net.bennyboops.modid.block.PocketPortalBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import java.util.*;

public class SuitcaseBlockEntity extends BlockEntity {
    private String boundKeystoneName;
    private boolean isLocked = false;
    private boolean dimensionLocked = true; // Default to true for safety
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
                    new BlockPos(
                            nbt.getInt("SuitcaseX"),
                            nbt.getInt("SuitcaseY"),
                            nbt.getInt("SuitcaseZ")
                    )
            );
        }
    }

    public SuitcaseBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SUITCASE_BLOCK_ENTITY, pos, state);
    }

    public boolean canOpenInDimension(World world) {
        if (!dimensionLocked) {
            return true;
        }
        Set<RegistryKey<World>> allowedDimensions = Set.of(
                World.OVERWORLD
                //World.NETHER,
                //World.END
        );
        return allowedDimensions.contains(world.getRegistryKey());
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
        PocketPortalBlock.storePlayerPosition(player);
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
        markDirty();
    }

    public String getBoundKeystoneName() {
        return boundKeystoneName;
    }

    public void setLocked(boolean locked) {
        this.isLocked = locked;
        markDirty();
    }

    public boolean isLocked() {
        return isLocked;
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        if (boundKeystoneName != null) {
            nbt.putString("BoundKeystone", boundKeystoneName);
        }
        nbt.putBoolean("Locked", isLocked);
        nbt.putBoolean("DimensionLocked", dimensionLocked);

        NbtList playersList = new NbtList();
        for (EnteredPlayerData data : enteredPlayers) {
            playersList.add(data.toNbt());
        }
        nbt.put("EnteredPlayers", playersList);

        super.writeNbt(nbt);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        if (nbt.contains("BoundKeystone")) {
            boundKeystoneName = nbt.getString("BoundKeystone");
        }
        isLocked = nbt.getBoolean("Locked");
        dimensionLocked = !nbt.contains("DimensionLocked") || nbt.getBoolean("DimensionLocked");
        enteredPlayers.clear();
        if (nbt.contains("EnteredPlayers", NbtElement.LIST_TYPE)) {
            NbtList playersList = nbt.getList("EnteredPlayers", NbtElement.COMPOUND_TYPE);
            for (NbtElement element : playersList) {
                enteredPlayers.add(EnteredPlayerData.fromNbt((NbtCompound) element));
            }
        }
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        NbtCompound nbt = new NbtCompound();
        writeNbt(nbt);
        return nbt;
    }

    public static final Map<String, Map<String, BlockPos>> SUITCASE_REGISTRY = Collections.synchronizedMap(new HashMap<>());

    public static BlockPos findSuitcasePosition(String keystoneName, String playerUuid) {
        Map<String, BlockPos> suitcases = SUITCASE_REGISTRY.get(keystoneName);
        if (suitcases != null) {
            return suitcases.get(playerUuid);
        }
        return null;
    }

    public static void removeSuitcaseEntry(String keystoneName, String playerUuid) {
        Map<String, BlockPos> suitcases = SUITCASE_REGISTRY.get(keystoneName);
        if (suitcases != null) {
            suitcases.remove(playerUuid);
            if (suitcases.isEmpty()) {
                SUITCASE_REGISTRY.remove(keystoneName);
            }
        }
    }

    public List<EnteredPlayerData> getEnteredPlayers() {
        return enteredPlayers;
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
            Map<String, BlockPos> suitcases = SUITCASE_REGISTRY.computeIfAbsent(
                    boundKeystoneName, k -> new HashMap<>()
            );
            suitcases.put(playerUuid, newPos);
        }
        markDirty();
    }
}