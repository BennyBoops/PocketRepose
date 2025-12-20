package net.bennyboops.modid.data;

import net.bennyboops.modid.block.entity.SuitcaseBlockEntity;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;
import net.minecraft.world.PersistentState;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

public class SuitcaseRegistrySavedData extends PersistentState {

    public static final String DATA_NAME = "pocket_repose_suitcase_registry";

    public static final Type<SuitcaseRegistrySavedData> TYPE = new Type<>(
            SuitcaseRegistrySavedData::new,
            SuitcaseRegistrySavedData::fromNbt,
            DataFixTypes.LEVEL);

    private final Map<String, Map<String, BlockPos>> registry = new HashMap<>();

    public SuitcaseRegistrySavedData() {
        // nothing else to initialise
    }

    private static SuitcaseRegistrySavedData fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        SuitcaseRegistrySavedData data = new SuitcaseRegistrySavedData();
        if (nbt.contains("RegistryEntries", NbtElement.COMPOUND_TYPE)) {
            NbtCompound top = nbt.getCompound("RegistryEntries");
            for (String keystone : top.getKeys()) {
                NbtList playerList = top.getList(keystone, NbtElement.COMPOUND_TYPE);
                Map<String, BlockPos> playerMap = new HashMap<>();
                for (int i = 0; i < playerList.size(); i++) {
                    NbtCompound rec = playerList.getCompound(i);
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
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtCompound top = new NbtCompound();
        for (Map.Entry<String, Map<String, BlockPos>> entry : registry.entrySet()) {
            String keystone = entry.getKey();
            Map<String, BlockPos> playerMap = entry.getValue();

            NbtList playerList = new NbtList();
            for (Map.Entry<String, BlockPos> e2 : playerMap.entrySet()) {
                NbtCompound record = new NbtCompound();
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
        markDirty();
    }

    public void syncToStaticRegistry() {
        SuitcaseBlockEntity.initializeSuitcaseRegistry(registry);
    }

    public static void onServerStart(MinecraftServer server) {
        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        if (overworld == null) return;

        PersistentStateManager mgr = overworld.getPersistentStateManager();
        SuitcaseRegistrySavedData data = mgr.getOrCreate(TYPE, DATA_NAME);
        data.syncToStaticRegistry();
    }

    public static void onRegistryChanged(MinecraftServer server) {
        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        if (overworld == null) return;

        PersistentStateManager mgr = overworld.getPersistentStateManager();
        SuitcaseRegistrySavedData data = mgr.getOrCreate(TYPE, DATA_NAME);
        data.syncFromStaticRegistry();
    }
}