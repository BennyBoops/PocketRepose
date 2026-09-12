package net.bennyboops.modid.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SuitcaseLocationTracker extends SavedData {

    public static final String DATA_NAME = "pocket_repose_suitcase_locations";

    public static final SavedData.Factory<SuitcaseLocationTracker> TYPE = new SavedData.Factory<>(
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

        public CompoundTag toNbt() {
            CompoundTag nbt = new CompoundTag();
            nbt.putString("dim", dimensionId == null ? "" : dimensionId);
            if (suitcaseId != null) nbt.putUUID("suitcaseId", suitcaseId);
            nbt.putDouble("x", x);
            nbt.putDouble("y", y);
            nbt.putDouble("z", z);
            nbt.putFloat("yaw", yaw);
            nbt.putFloat("pitch", pitch);
            nbt.putLong("timestamp", timestamp);
            nbt.putString("type", type.name());
            return nbt;
        }

        public static LocationData fromNbt(CompoundTag nbt) {
            String dim = nbt.getString("dim");
            UUID sid = nbt.hasUUID("suitcaseId") ? nbt.getUUID("suitcaseId") : null;

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

        public CompoundTag toNbt() {
            CompoundTag nbt = new CompoundTag();
            nbt.putString("dim", dimensionId == null ? "" : dimensionId);
            nbt.putDouble("x", x);
            nbt.putDouble("y", y);
            nbt.putDouble("z", z);
            nbt.putLong("timestamp", timestamp);
            nbt.putString("type", type.name());
            return nbt;
        }

        public static SuitcaseInstanceLocation fromNbt(CompoundTag nbt) {
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

    private static SuitcaseLocationTracker fromNbt(CompoundTag nbt, HolderLookup.Provider lookup) {
        SuitcaseLocationTracker tracker = new SuitcaseLocationTracker();

        if (nbt.contains("Locations", Tag.TAG_COMPOUND)) {
            CompoundTag ks = nbt.getCompound("Locations");
            for (String keystone : ks.getAllKeys()) {
                ListTag playerList = ks.getList(keystone, Tag.TAG_COMPOUND);
                Map<String, LocationData> map = new HashMap<>();
                for (int i = 0; i < playerList.size(); i++) {
                    CompoundTag rec = playerList.getCompound(i);
                    map.put(rec.getString("UUID"), LocationData.fromNbt(rec.getCompound("Location")));
                }
                tracker.locations.put(keystone, map);
            }
        }

        if (nbt.contains("EntryLocations", Tag.TAG_COMPOUND)) {
            CompoundTag ks = nbt.getCompound("EntryLocations");
            for (String keystone : ks.getAllKeys()) {
                ListTag playerList = ks.getList(keystone, Tag.TAG_COMPOUND);
                Map<String, LocationData> map = new HashMap<>();
                for (int i = 0; i < playerList.size(); i++) {
                    CompoundTag rec = playerList.getCompound(i);
                    map.put(rec.getString("UUID"), LocationData.fromNbt(rec.getCompound("Location")));
                }
                tracker.entryLocations.put(keystone, map);
            }
        }

        if (nbt.contains("LastSuitcase", Tag.TAG_COMPOUND)) {
            CompoundTag ks = nbt.getCompound("LastSuitcase");
            for (String keystone : ks.getAllKeys()) {
                CompoundTag players = ks.getCompound(keystone);
                Map<String, UUID> map = new HashMap<>();
                for (String playerUuid : players.getAllKeys()) {
                    try { map.put(playerUuid, UUID.fromString(players.getString(playerUuid))); } catch (Exception ignored) {}
                }
                tracker.lastSuitcase.put(keystone, map);
            }
        }

        if (nbt.contains("SuitcaseLocations", Tag.TAG_COMPOUND)) {
            CompoundTag all = nbt.getCompound("SuitcaseLocations");
            for (String sidStr : all.getAllKeys()) {
                try {
                    UUID sid = UUID.fromString(sidStr);
                    tracker.suitcaseLocations.put(sid, SuitcaseInstanceLocation.fromNbt(all.getCompound(sidStr)));
                } catch (Exception ignored) {}
            }
        }

        return tracker;
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider lookup) {
        CompoundTag locTop = new CompoundTag();
        for (var e : locations.entrySet()) {
            ListTag list = new ListTag();
            for (var p : e.getValue().entrySet()) {
                CompoundTag rec = new CompoundTag();
                rec.putString("UUID", p.getKey());
                rec.put("Location", p.getValue().toNbt());
                list.add(rec);
            }
            locTop.put(e.getKey(), list);
        }
        nbt.put("Locations", locTop);

        CompoundTag entryTop = new CompoundTag();
        for (var e : entryLocations.entrySet()) {
            ListTag list = new ListTag();
            for (var p : e.getValue().entrySet()) {
                CompoundTag rec = new CompoundTag();
                rec.putString("UUID", p.getKey());
                rec.put("Location", p.getValue().toNbt());
                list.add(rec);
            }
            entryTop.put(e.getKey(), list);
        }
        nbt.put("EntryLocations", entryTop);

        CompoundTag last = new CompoundTag();
        for (var e : lastSuitcase.entrySet()) {
            CompoundTag players = new CompoundTag();
            for (var p : e.getValue().entrySet()) {
                players.putString(p.getKey(), p.getValue().toString());
            }
            last.put(e.getKey(), players);
        }
        nbt.put("LastSuitcase", last);

        CompoundTag all = new CompoundTag();
        for (var e : suitcaseLocations.entrySet()) {
            all.put(e.getKey().toString(), e.getValue().toNbt());
        }
        nbt.put("SuitcaseLocations", all);

        return nbt;
    }

    public static SuitcaseLocationTracker get(MinecraftServer server) {
        if (server == null) return null;
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return null;
        DimensionDataStorage mgr = overworld.getDataStorage();
        return mgr.computeIfAbsent(TYPE, DATA_NAME);
    }



    public void setLastSuitcase(String keystone, String playerUuid, UUID suitcaseId) {
        lastSuitcase.computeIfAbsent(keystone, k -> new HashMap<>()).put(playerUuid, suitcaseId);
        setDirty();
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

        setDirty();
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
        setDirty();
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
                setDirty();
            }
        }
    }
}
