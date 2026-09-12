package net.bennyboops.modid.block.entity;

import net.bennyboops.modid.data.SuitcaseLocationTracker;
import net.bennyboops.modid.data.SuitcaseRegistrySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class SuitcaseBlockEntity extends BlockEntity {

    public static final String NBT_SUITCASE_ID = "SuitcaseId";
    private UUID suitcaseId = UUID.randomUUID();
    public UUID getSuitcaseId() {
        return suitcaseId;
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

        public CompoundTag toNbt() {
            CompoundTag nbt = new CompoundTag();
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

        public static EnteredPlayerData fromNbt(CompoundTag nbt) {
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

    public SuitcaseBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SUITCASE_BLOCK_ENTITY.get(), pos, state);
    }

    public boolean canOpenInDimension(Level world) {
        if (world == null) return true;
        if (boundKeystoneName == null || boundKeystoneName.isEmpty()) return true;

        String currentDim = world.dimension().location().toString();
        String ownPocketDim = "pocket-repose:pocket_dimension_" + boundKeystoneName;

        return !currentDim.equals(ownPocketDim);
    }

    public boolean isDimensionLocked() {
        return dimensionLocked;
    }

    private static final Set<UUID> PLAYERS_WHO_ENTERED = new HashSet<>();

    public boolean isFirstTimeEntering(ServerPlayer player) {
        return !PLAYERS_WHO_ENTERED.contains(player.getUUID());
    }

    public void playerEntered(ServerPlayer player) {
        enteredPlayers.removeIf(data -> data.uuid.equals(player.getStringUUID()));

        EnteredPlayerData data = new EnteredPlayerData(
                player.getStringUUID(),
                player.getX(), player.getY(), player.getZ(),
                player.getXRot(), player.getYRot(),
                this.getBlockPos()
        );
        enteredPlayers.add(data);

        PLAYERS_WHO_ENTERED.add(player.getUUID());

        Map<String, BlockPos> suitcases = SUITCASE_REGISTRY.computeIfAbsent(
                boundKeystoneName, k -> new HashMap<>()
        );
        suitcases.put(player.getStringUUID(), this.getBlockPos());

        MinecraftServer server = player.getServer();
        if (server != null && boundKeystoneName != null) {
            SuitcaseLocationTracker tracker = SuitcaseLocationTracker.get(server);
            if (tracker != null) {

                tracker.setLastSuitcase(boundKeystoneName, player.getStringUUID(), this.getSuitcaseId());

                String dimId = player.serverLevel().dimension().location().toString();
                UUID suitcaseId = this.getSuitcaseId();

                tracker.setLastSuitcase(boundKeystoneName, player.getStringUUID(), suitcaseId);

                tracker.updateLocation(
                        boundKeystoneName,
                        player.getStringUUID(),
                        dimId,
                        suitcaseId,
                        player.getX(), player.getY(), player.getZ(),
                        player.getYRot(), player.getXRot(),
                        SuitcaseLocationTracker.LocationType.ENTRY
                );

            }
            SuitcaseRegistrySavedData.onRegistryChanged(server);
        }

        setChanged();
    }

    public EnteredPlayerData getExitPosition(String playerUuid) {
        for (EnteredPlayerData data : enteredPlayers) {
            if (data.uuid.equals(playerUuid)) {
                EnteredPlayerData exitData = new EnteredPlayerData(
                        data.uuid,
                        this.getBlockPos().getX() + 0.5,
                        this.getBlockPos().getY() + 1.0,
                        this.getBlockPos().getZ() + 0.5,
                        data.pitch, data.yaw,
                        this.getBlockPos()
                );
                enteredPlayers.remove(data);
                setChanged();
                return exitData;
            }
        }
        return null;
    }

    public void bindKeystone(String keystoneName) {
        this.boundKeystoneName = keystoneName;
        setChanged();
    }

    public String getBoundKeystoneName() {
        return boundKeystoneName;
    }

    public void setLocked(boolean locked) {
        this.isLocked = locked;
        setChanged();
    }

    public boolean isLocked() {
        return isLocked;
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registry) {
        super.saveAdditional(nbt, registry);

        nbt.putUUID(NBT_SUITCASE_ID, suitcaseId);

        if (boundKeystoneName != null) nbt.putString("BoundKeystone", boundKeystoneName);

        nbt.putBoolean("Locked", isLocked);
        nbt.putBoolean("DimensionLocked", dimensionLocked);

        ListTag players = new ListTag();
        for (EnteredPlayerData data : enteredPlayers) players.add(data.toNbt());
        nbt.put("EnteredPlayers", players);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registry) {
        super.loadAdditional(nbt, registry);

        if (nbt.hasUUID(NBT_SUITCASE_ID)) {
            suitcaseId = nbt.getUUID(NBT_SUITCASE_ID);
        } else {
            suitcaseId = UUID.randomUUID();
        }

        boundKeystoneName = nbt.contains("BoundKeystone") ? nbt.getString("BoundKeystone") : null;
        isLocked = nbt.getBoolean("Locked");
        dimensionLocked = nbt.contains("DimensionLocked") && nbt.getBoolean("DimensionLocked");

        enteredPlayers.clear();
        if (nbt.contains("EnteredPlayers", Tag.TAG_LIST)) {
            for (Tag e : nbt.getList("EnteredPlayers", Tag.TAG_COMPOUND)) {
                enteredPlayers.add(EnteredPlayerData.fromNbt((CompoundTag) e));
            }
        }
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registry) {
        return saveWithId(registry);
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

        setChanged();
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
