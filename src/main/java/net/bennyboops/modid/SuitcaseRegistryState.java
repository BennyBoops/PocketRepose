package net.bennyboops.modid;

import net.bennyboops.modid.block.entity.SuitcaseBlockEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

public class SuitcaseRegistryState extends PersistentState {
    private static final String REGISTRY_KEY = "pocket_repose_suitcase_registry";
    private final Map<String, Map<String, BlockPos>> registry = new HashMap<>();

    public SuitcaseRegistryState() {
        super();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList keystonesList = new NbtList();
        for (Map.Entry<String, Map<String, BlockPos>> keystoneEntry : registry.entrySet()) {
            NbtCompound keystoneNbt = new NbtCompound();
            keystoneNbt.putString("KeystoneName", keystoneEntry.getKey());
            NbtList playersList = new NbtList();
            for (Map.Entry<String, BlockPos> playerEntry : keystoneEntry.getValue().entrySet()) {
                NbtCompound playerNbt = new NbtCompound();
                playerNbt.putString("UUID", playerEntry.getKey());
                BlockPos pos = playerEntry.getValue();
                playerNbt.putInt("X", pos.getX());
                playerNbt.putInt("Y", pos.getY());
                playerNbt.putInt("Z", pos.getZ());

                playersList.add(playerNbt);
            }
            keystoneNbt.put("Players", playersList);
            keystonesList.add(keystoneNbt);
        }
        nbt.put("SuitcaseRegistry", keystonesList);
        return nbt;
    }

    public static SuitcaseRegistryState createFromNbt(NbtCompound nbt) {
        SuitcaseRegistryState state = new SuitcaseRegistryState();
        if (nbt.contains("SuitcaseRegistry")) {
            NbtList keystonesList = nbt.getList("SuitcaseRegistry", 10);
            for (int i = 0; i < keystonesList.size(); i++) {
                NbtCompound keystoneNbt = keystonesList.getCompound(i);
                String keystoneName = keystoneNbt.getString("KeystoneName");
                Map<String, BlockPos> playersMap = new HashMap<>();
                NbtList playersList = keystoneNbt.getList("Players", 10);
                for (int j = 0; j < playersList.size(); j++) {
                    NbtCompound playerNbt = playersList.getCompound(j);
                    String uuid = playerNbt.getString("UUID");
                    BlockPos pos = new BlockPos(
                            playerNbt.getInt("X"),
                            playerNbt.getInt("Y"),
                            playerNbt.getInt("Z")
                    );
                    playersMap.put(uuid, pos);
                }
                state.registry.put(keystoneName, playersMap);
            }
        }
        return state;
    }

    private static SuitcaseRegistryState getState(MinecraftServer server) {
        return server.getWorld(World.OVERWORLD).getPersistentStateManager()
                .getOrCreate(SuitcaseRegistryState::createFromNbt, SuitcaseRegistryState::new, REGISTRY_KEY);
    }

    public static void registerEvents() {
        // Initialize the registry on server start
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            SuitcaseRegistryState state = getState(server);
            SuitcaseBlockEntity.initializeSuitcaseRegistry(state.registry);
        });
        // Save the registry on server stop
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            SuitcaseRegistryState state = getState(server);
            SuitcaseBlockEntity.saveSuitcaseRegistryTo(state.registry);
            state.markDirty();
        });
    }
}