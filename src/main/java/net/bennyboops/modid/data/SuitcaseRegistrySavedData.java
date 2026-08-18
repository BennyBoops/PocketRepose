package net.bennyboops.modid.data;

import net.bennyboops.modid.block.entity.SuitcaseBlockEntity;
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

public class SuitcaseRegistrySavedData extends SavedData {

    public static final String DATA_NAME = "pocket_repose_suitcase_registry";

    public static final SavedData.Factory<SuitcaseRegistrySavedData> TYPE = new SavedData.Factory<>(
            SuitcaseRegistrySavedData::new,
            SuitcaseRegistrySavedData::fromNbt,
            DataFixTypes.LEVEL);

    private final Map<String, Map<String, BlockPos>> registry = new HashMap<>();

    public SuitcaseRegistrySavedData() {
    }

    private static SuitcaseRegistrySavedData fromNbt(CompoundTag nbt, HolderLookup.Provider lookup) {
        SuitcaseRegistrySavedData data = new SuitcaseRegistrySavedData();
        if (nbt.contains("RegistryEntries", Tag.TAG_COMPOUND)) {
            CompoundTag top = nbt.getCompound("RegistryEntries");
            for (String keystone : top.getAllKeys()) {
                ListTag playerList = top.getList(keystone, Tag.TAG_COMPOUND);
                Map<String, BlockPos> playerMap = new HashMap<>();
                for (int i = 0; i < playerList.size(); i++) {
                    CompoundTag rec = playerList.getCompound(i);
                    String uuid = rec.getString("UUID");
                    int x = rec.getInt("X");
                    int y = rec.getInt("Y");
                    int z = rec.getInt("Z");
                    playerMap.put(uuid, new BlockPos(x, y, z));
                }
                data.registry.put(keystone, playerMap);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider lookup) {
        CompoundTag top = new CompoundTag();
        for (Map.Entry<String, Map<String, BlockPos>> entry : registry.entrySet()) {
            String keystone = entry.getKey();
            Map<String, BlockPos> playerMap = entry.getValue();

            ListTag playerList = new ListTag();
            for (Map.Entry<String, BlockPos> e2 : playerMap.entrySet()) {
                CompoundTag record = new CompoundTag();
                record.putString("UUID", e2.getKey());
                BlockPos pos = e2.getValue();
                record.putInt("X", pos.getX());
                record.putInt("Y", pos.getY());
                record.putInt("Z", pos.getZ());
                playerList.add(record);
            }
            top.put(keystone, playerList);
        }
        nbt.put("RegistryEntries", top);
        return nbt;
    }

    public void syncFromStaticRegistry() {
        registry.clear();
        SuitcaseBlockEntity.saveSuitcaseRegistryTo(registry);
        setDirty();
    }

    public void syncToStaticRegistry() {
        SuitcaseBlockEntity.initializeSuitcaseRegistry(registry);
    }

    public static void onServerStart(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        DimensionDataStorage mgr = overworld.getDataStorage();
        SuitcaseRegistrySavedData data = mgr.computeIfAbsent(TYPE, DATA_NAME);
        data.syncToStaticRegistry();
    }

    public static void onRegistryChanged(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        DimensionDataStorage mgr = overworld.getDataStorage();
        SuitcaseRegistrySavedData data = mgr.computeIfAbsent(TYPE, DATA_NAME);
        data.syncFromStaticRegistry();
    }
}
