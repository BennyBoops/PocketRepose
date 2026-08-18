package net.bennyboops.modid.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.phys.Vec3;

public class MobEntryData extends SavedData {

    private static final String DATA_KEY = "pocket_entry_data";

    public static final SavedData.Factory<MobEntryData> TYPE =
            new SavedData.Factory<>(MobEntryData::new, MobEntryData::fromNbt, DataFixTypes.LEVEL);

    public static MobEntryData get(ServerLevel world) {
        DimensionDataStorage mgr = world.getDataStorage();
        return mgr.computeIfAbsent(TYPE, DATA_KEY);
    }

    private Vec3 entryPos = Vec3.ZERO;
    private float entryYaw = 0f;
    private float entryPitch = 0f;

    public MobEntryData() {
        this.entryPos = new Vec3(35.5, 85, 16.5); // sensible default
    }

    private static MobEntryData fromNbt(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        MobEntryData data = new MobEntryData();
        data.readNbt(nbt);
        return data;
    }

    private void readNbt(CompoundTag nbt) {
        this.entryPos = new Vec3(nbt.getDouble("entryX"), nbt.getDouble("entryY"), nbt.getDouble("entryZ"));
        this.entryYaw = nbt.getFloat("entryYaw");
        this.entryPitch = nbt.getFloat("entryPitch");
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        nbt.putDouble("entryX", entryPos.x);
        nbt.putDouble("entryY", entryPos.y);
        nbt.putDouble("entryZ", entryPos.z);
        nbt.putFloat("entryYaw", entryYaw);
        nbt.putFloat("entryPitch", entryPitch);
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
