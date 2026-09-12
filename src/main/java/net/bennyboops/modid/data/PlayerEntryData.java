package net.bennyboops.modid.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.phys.Vec3;

public class PlayerEntryData extends SavedData {

    private static final String DATA_KEY = "pocket_player_entry";

    public static final SavedData.Factory<PlayerEntryData> TYPE =
            new SavedData.Factory<>(PlayerEntryData::new, PlayerEntryData::fromNbt, DataFixTypes.LEVEL);

    public static PlayerEntryData get(ServerLevel world) {
        DimensionDataStorage mgr = world.getDataStorage();
        return mgr.computeIfAbsent(TYPE, DATA_KEY);
    }

    private Vec3 entryPos = Vec3.ZERO;
    private float entryYaw = 0f;
    private float entryPitch = 0f;

    public PlayerEntryData() {
        this.entryPos = new Vec3(17.5, 97, 9.5); // default spawn
    }

    private static PlayerEntryData fromNbt(CompoundTag nbt, HolderLookup.Provider wrapperLookup) {
        PlayerEntryData data = new PlayerEntryData();
        data.readNbt(nbt);
        return data;
    }

    private void readNbt(CompoundTag nbt) {
        this.entryPos = new Vec3(
                nbt.getDouble("px"),
                nbt.getDouble("py"),
                nbt.getDouble("pz"));
        this.entryYaw = nbt.getFloat("pyaw");
        this.entryPitch = nbt.getFloat("ppitch");
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider wrapperLookup) {
        nbt.putDouble("px", entryPos.x);
        nbt.putDouble("py", entryPos.y);
        nbt.putDouble("pz", entryPos.z);
        nbt.putFloat("pyaw", entryYaw);
        nbt.putFloat("ppitch", entryPitch);
        return nbt;
    }

    public void setEntry(Vec3 pos, float yaw, float pitch) {
        this.entryPos = pos;
        this.entryYaw = yaw;
        this.entryPitch = pitch;
        this.setDirty();
    }

    public Vec3 getEntryPos()    { return entryPos; }
    public float getEntryYaw()   { return entryYaw; }
    public float getEntryPitch() { return entryPitch; }
}
