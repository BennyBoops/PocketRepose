package net.bennyboops.modid.data;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SuitcaseLocationTracker extends PersistentState {

    public static final String DATA_NAME = "pocket_repose_suitcase_locations";

    public static final Type<SuitcaseLocationTracker> TYPE = new Type<>(
            SuitcaseLocationTracker::new,
            SuitcaseLocationTracker::fromNbt,
            DataFixTypes.LEVEL
    );

    private final Map<String, Map<String, LocationData>> locations = new HashMap<>();
    private final Map<String, Map<String, LocationData>> entryLocations = new HashMap<>();
    private final Map<String, Map<String, UUID>> lastSuitcase = new HashMap<>();
    private final Map<UUID, SuitcaseInstanceLocation> suitcaseLocations = new HashMap<>();

    public enum LocationType {
        BLOCK,
        ENTRY,
        ITEM_ENTITY,
        DESTROYED
    }

    public static class LocationData {
        public String dimensionId;
        public UUID suitcaseId;
        public double x, y, z;
        public float yaw, pitch;
        public long timestamp;
        public LocationType type;

        public LocationData(String dimensionId, UUID suitcaseId,
                            double x, double y, double z,
                            float yaw, float pitch,
                            LocationType type) {
            this.dimensionId = dimensionId;
            this.suitcaseId = suitcaseId;
            this.x = x; this.y = y; this.z = z;
            this.yaw = yaw; this.pitch = pitch;
            this.type = type;
            this.timestamp = System.currentTimeMillis();
        }

        public NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putString("dim", dimensionId == null ? "" : dimensionId);
            if (suitcaseId != null) nbt.putUuid("suitcaseId", suitcaseId);
            nbt.putDouble("x", x);
            nbt.putDouble("y", y);
            nbt.putDouble("z", z);
            nbt.putFloat("yaw", yaw);
            nbt.putFloat("pitch", pitch);
            nbt.putLong("timestamp", timestamp);
            nbt.putString("type", type.name());
            return nbt;
        }

        public static LocationData fromNbt(NbtCompound nbt) {
            String dim = nbt.getString("dim");
            UUID sid = nbt.containsUuid("suitcaseId") ? nbt.getUuid("suitcaseId") : null;

            LocationData data = new LocationData(
                    dim,
                    sid,
                    nbt.getDouble("x"),
                    nbt.getDouble("y"),
                    nbt.getDouble("z"),
                    nbt.getFloat("yaw"),
                    nbt.getFloat("pitch"),
                    LocationType.valueOf(nbt.getString("type"))
            );
            data.timestamp = nbt.getLong("timestamp");
            return data;
        }
    }

    public static class SuitcaseInstanceLocation {
        public String dimensionId;
        public double x, y, z;
        public long timestamp;
        public LocationType type;

        public SuitcaseInstanceLocation(String dimensionId, double x, double y, double z, LocationType type) {
            this.dimensionId = dimensionId;
            this.x = x; this.y = y; this.z = z;
            this.type = type;
            this.timestamp = System.currentTimeMillis();
        }

        public NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putString("dim", dimensionId == null ? "" : dimensionId);
            nbt.putDouble("x", x);
            nbt.putDouble("y", y);
            nbt.putDouble("z", z);
            nbt.putLong("timestamp", timestamp);
            nbt.putString("type", type.name());
            return nbt;
        }

        public static SuitcaseInstanceLocation fromNbt(NbtCompound nbt) {
            SuitcaseInstanceLocation loc = new SuitcaseInstanceLocation(
                    nbt.getString("dim"),
                    nbt.getDouble("x"),
                    nbt.getDouble("y"),
                    nbt.getDouble("z"),
                    LocationType.valueOf(nbt.getString("type"))
            );
            loc.timestamp = nbt.getLong("timestamp");
            return loc;
        }
    }

    public SuitcaseLocationTracker() {}

    private static SuitcaseLocationTracker fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        SuitcaseLocationTracker tracker = new SuitcaseLocationTracker();

        if (nbt.contains("Locations", NbtElement.COMPOUND_TYPE)) {
            NbtCompound ks = nbt.getCompound("Locations");
            for (String keystone : ks.getKeys()) {
                NbtList playerList = ks.getList(keystone, NbtElement.COMPOUND_TYPE);
                Map<String, LocationData> map = new HashMap<>();
                for (int i = 0; i < playerList.size(); i++) {
                    NbtCompound rec = playerList.getCompound(i);
                    map.put(rec.getString("UUID"), LocationData.fromNbt(rec.getCompound("Location")));
                }
                tracker.locations.put(keystone, map);
            }
        }

        if (nbt.contains("EntryLocations", NbtElement.COMPOUND_TYPE)) {
            NbtCompound ks = nbt.getCompound("EntryLocations");
            for (String keystone : ks.getKeys()) {
                NbtList playerList = ks.getList(keystone, NbtElement.COMPOUND_TYPE);
                Map<String, LocationData> map = new HashMap<>();
                for (int i = 0; i < playerList.size(); i++) {
                    NbtCompound rec = playerList.getCompound(i);
                    map.put(rec.getString("UUID"), LocationData.fromNbt(rec.getCompound("Location")));
                }
                tracker.entryLocations.put(keystone, map);
            }
        }

        if (nbt.contains("LastSuitcase", NbtElement.COMPOUND_TYPE)) {
            NbtCompound ks = nbt.getCompound("LastSuitcase");
            for (String keystone : ks.getKeys()) {
                NbtCompound players = ks.getCompound(keystone);
                Map<String, UUID> map = new HashMap<>();
                for (String playerUuid : players.getKeys()) {
                    try { map.put(playerUuid, UUID.fromString(players.getString(playerUuid))); } catch (Exception ignored) {}
                }
                tracker.lastSuitcase.put(keystone, map);
            }
        }

        if (nbt.contains("SuitcaseLocations", NbtElement.COMPOUND_TYPE)) {
            NbtCompound all = nbt.getCompound("SuitcaseLocations");
            for (String sidStr : all.getKeys()) {
                try {
                    UUID sid = UUID.fromString(sidStr);
                    tracker.suitcaseLocations.put(sid, SuitcaseInstanceLocation.fromNbt(all.getCompound(sidStr)));
                } catch (Exception ignored) {}
            }
        }

        return tracker;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtCompound locTop = new NbtCompound();
        for (var e : locations.entrySet()) {
            NbtList list = new NbtList();
            for (var p : e.getValue().entrySet()) {
                NbtCompound rec = new NbtCompound();
                rec.putString("UUID", p.getKey());
                rec.put("Location", p.getValue().toNbt());
                list.add(rec);
            }
            locTop.put(e.getKey(), list);
        }
        nbt.put("Locations", locTop);

        NbtCompound entryTop = new NbtCompound();
        for (var e : entryLocations.entrySet()) {
            NbtList list = new NbtList();
            for (var p : e.getValue().entrySet()) {
                NbtCompound rec = new NbtCompound();
                rec.putString("UUID", p.getKey());
                rec.put("Location", p.getValue().toNbt());
                list.add(rec);
            }
            entryTop.put(e.getKey(), list);
        }
        nbt.put("EntryLocations", entryTop);

        NbtCompound last = new NbtCompound();
        for (var e : lastSuitcase.entrySet()) {
            NbtCompound players = new NbtCompound();
            for (var p : e.getValue().entrySet()) {
                players.putString(p.getKey(), p.getValue().toString());
            }
            last.put(e.getKey(), players);
        }
        nbt.put("LastSuitcase", last);

        NbtCompound all = new NbtCompound();
        for (var e : suitcaseLocations.entrySet()) {
            all.put(e.getKey().toString(), e.getValue().toNbt());
        }
        nbt.put("SuitcaseLocations", all);

        return nbt;
    }

    public static SuitcaseLocationTracker get(MinecraftServer server) {
        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        if (overworld == null) return null;
        PersistentStateManager mgr = overworld.getPersistentStateManager();
        return mgr.getOrCreate(TYPE, DATA_NAME);
    }



    public void setLastSuitcase(String keystone, String playerUuid, UUID suitcaseId) {
        lastSuitcase.computeIfAbsent(keystone, k -> new HashMap<>()).put(playerUuid, suitcaseId);
        markDirty();
    }

    public UUID getLastSuitcase(String keystone, String playerUuid) {
        Map<String, UUID> m = lastSuitcase.get(keystone);
        return (m == null) ? null : m.get(playerUuid);
    }

    public void updateLocation(String keystone, String playerUuid, String dimensionId, UUID suitcaseId,
                               double x, double y, double z, float yaw, float pitch, LocationType type) {

        if (type == LocationType.ENTRY) {
            entryLocations
                    .computeIfAbsent(keystone, k -> new HashMap<>())
                    .put(playerUuid, new LocationData(dimensionId, suitcaseId, x, y, z, yaw, pitch, type));
        }

        locations.computeIfAbsent(keystone, k -> new HashMap<>())
                .put(playerUuid, new LocationData(dimensionId, suitcaseId, x, y, z, yaw, pitch, type));

        markDirty();
    }

    public void updateLocationFromBlock(String keystone, String playerUuid, String dimensionId, UUID suitcaseId,
                                        BlockPos pos, float yaw, float pitch) {
        updateLocation(keystone, playerUuid, dimensionId, suitcaseId,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                yaw, pitch, LocationType.BLOCK);
    }

    public LocationData getLocation(String keystone, String playerUuid) {
        Map<String, LocationData> m = locations.get(keystone);
        return (m == null) ? null : m.get(playerUuid);
    }

    public LocationData getEntryLocation(String keystone, String playerUuid) {
        Map<String, LocationData> m = entryLocations.get(keystone);
        return (m == null) ? null : m.get(playerUuid);
    }

    public void updateSuitcaseLocation(UUID suitcaseId, String dimensionId,
                                       double x, double y, double z, LocationType type) {
        if (suitcaseId == null) return;
        suitcaseLocations.put(suitcaseId, new SuitcaseInstanceLocation(dimensionId, x, y, z, type));
        markDirty();
    }

    public void updateSuitcaseLocationFromBlock(UUID suitcaseId, String dimensionId, BlockPos pos) {
        updateSuitcaseLocation(suitcaseId, dimensionId,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                LocationType.BLOCK);
    }

    public SuitcaseInstanceLocation getSuitcaseLocation(UUID suitcaseId) {
        return (suitcaseId == null) ? null : suitcaseLocations.get(suitcaseId);
    }

    public void markDestroyed(String keystone, String playerUuid) {
        Map<String, LocationData> playerMap = locations.get(keystone);
        if (playerMap != null) {
            LocationData existing = playerMap.get(playerUuid);
            if (existing != null) {
                existing.type = LocationType.DESTROYED;
                markDirty();
            }
        }
    }
}
