package net.bennyboops.modid.data;

import net.bennyboops.modid.PocketRepose;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

/**
 * Persists the /pocketRepose settings (canCaptureHostile, spawnIsland, allowRecursion, mobBlacklist)
 * per world, so they survive restarts and don't leak between singleplayer worlds.
 */
public class PocketReposeSettingsData extends PersistentState {

    public static final String DATA_NAME = "pocket_repose_settings";


    public PocketReposeSettingsData() {
    }

    private static PocketReposeSettingsData fromNbt(NbtCompound nbt) {
        if (nbt.contains("CanCaptureHostile")) PocketRepose.setCanCaptureHostile(nbt.getBoolean("CanCaptureHostile"));
        if (nbt.contains("SpawnIsland")) PocketRepose.setSpawnIsland(nbt.getBoolean("SpawnIsland"));
        if (nbt.contains("AllowRecursion")) PocketRepose.setAllowRecursion(nbt.getBoolean("AllowRecursion"));

        NbtList blacklist = nbt.getList("MobBlacklist", NbtElement.STRING_TYPE);
        for (int i = 0; i < blacklist.size(); i++) {
            Identifier id = Identifier.tryParse(blacklist.getString(i));
            if (id == null) continue;
            Registries.ENTITY_TYPE.getOrEmpty(id).ifPresent(PocketRepose::addToBlacklist);
        }
        return new PocketReposeSettingsData();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putBoolean("CanCaptureHostile", PocketRepose.getCanCaptureHostile());
        nbt.putBoolean("SpawnIsland", PocketRepose.getSpawnIsland());
        nbt.putBoolean("AllowRecursion", PocketRepose.getAllowRecursion());

        NbtList blacklist = new NbtList();
        for (EntityType<?> type : PocketRepose.getEntityBlacklist()) {
            blacklist.add(NbtString.of(Registries.ENTITY_TYPE.getId(type).toString()));
        }
        nbt.put("MobBlacklist", blacklist);
        return nbt;
    }

    private static PocketReposeSettingsData get(MinecraftServer server) {
        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        if (overworld == null) return null;
        return overworld.getPersistentStateManager().getOrCreate(PocketReposeSettingsData::fromNbt, PocketReposeSettingsData::new, DATA_NAME);
    }

    public static void onServerStart(MinecraftServer server) {
        PocketRepose.resetSettings();
        get(server);
    }

    public static void onSettingsChanged(MinecraftServer server) {
        PocketReposeSettingsData data = get(server);
        if (data != null) data.markDirty();
    }
}
