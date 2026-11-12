package net.bennyboops.modid.data;

import net.bennyboops.modid.PocketRepose;
import net.bennyboops.modid.config.PocketReposeConfig;
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
 * Persists the /pocketRepose settings (the exitKeystone and pocketDimensions options, and the capture blacklist)
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
        if (nbt.contains("HostileMobSpawning")) PocketRepose.setHostileMobSpawning(nbt.getBoolean("HostileMobSpawning"));
        if (nbt.contains("PassiveMobSpawning")) PocketRepose.setPassiveMobSpawning(nbt.getBoolean("PassiveMobSpawning"));
        if (nbt.contains("AllowSpawnPoint")) PocketRepose.setAllowSpawnPoint(nbt.getBoolean("AllowSpawnPoint"));
        if (nbt.contains("ExitKeystoneInOverworld")) PocketRepose.setExitKeystoneInOverworld(nbt.getBoolean("ExitKeystoneInOverworld"));
        if (nbt.contains("ExitKeystoneCooldown")) PocketRepose.setExitKeystoneCooldown(nbt.getFloat("ExitKeystoneCooldown"));
        if (nbt.contains("ExitKeystoneInNether")) PocketRepose.setExitKeystoneInNether(nbt.getBoolean("ExitKeystoneInNether"));
        if (nbt.contains("ExitKeystoneInEnd")) PocketRepose.setExitKeystoneInEnd(nbt.getBoolean("ExitKeystoneInEnd"));
        if (nbt.contains("CanCapturePassive")) PocketRepose.setCanCapturePassive(nbt.getBoolean("CanCapturePassive"));
        if (nbt.contains("AllowInNether")) PocketRepose.setAllowInNether(nbt.getBoolean("AllowInNether"));
        if (nbt.contains("AllowInEnd")) PocketRepose.setAllowInEnd(nbt.getBoolean("AllowInEnd"));
        if (nbt.contains("KnockVolume")) PocketRepose.setKnockVolume(nbt.getFloat("KnockVolume"));
        if (nbt.contains("CloseOnEntry")) PocketRepose.setCloseOnEntry(nbt.getBoolean("CloseOnEntry"));
        if (nbt.contains("CloseOnLockedEntry")) PocketRepose.setCloseOnLockedEntry(nbt.getBoolean("CloseOnLockedEntry"));

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
        nbt.putBoolean("HostileMobSpawning", PocketRepose.getHostileMobSpawning());
        nbt.putBoolean("PassiveMobSpawning", PocketRepose.getPassiveMobSpawning());
        nbt.putBoolean("AllowSpawnPoint", PocketRepose.getAllowSpawnPoint());
        nbt.putBoolean("ExitKeystoneInOverworld", PocketRepose.getExitKeystoneInOverworld());
        nbt.putFloat("ExitKeystoneCooldown", PocketRepose.getExitKeystoneCooldown());
        nbt.putBoolean("ExitKeystoneInNether", PocketRepose.getExitKeystoneInNether());
        nbt.putBoolean("ExitKeystoneInEnd", PocketRepose.getExitKeystoneInEnd());
        nbt.putBoolean("CanCapturePassive", PocketRepose.getCanCapturePassive());
        nbt.putBoolean("AllowInNether", PocketRepose.getAllowInNether());
        nbt.putBoolean("AllowInEnd", PocketRepose.getAllowInEnd());
        nbt.putFloat("KnockVolume", PocketRepose.getKnockVolume());
        nbt.putBoolean("CloseOnEntry", PocketRepose.getCloseOnEntry());
        nbt.putBoolean("CloseOnLockedEntry", PocketRepose.getCloseOnLockedEntry());

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
        // New worlds start from the defaults file; settings an older version saved with the world come next,
        // and the world's own config file (if it has one yet) has the final say
        PocketReposeConfig.applyDefaults();
        get(server);
        PocketReposeConfig.loadWorld(server);
    }

    /** A setting changed (by a command or the config screen): saves it and updates everyone's config screen. */
    public static void onSettingsChanged(MinecraftServer server) {
        markChanged(server);
        PocketReposeConfig.onChanged(server);
    }

    /** Saves the settings with the world as well, so they also survive going back to an older version. */
    public static void markChanged(MinecraftServer server) {
        PocketReposeSettingsData data = get(server);
        if (data != null) data.markDirty();
    }
}
