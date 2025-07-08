package net.bennyboops.modid.data;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

public class MobEntryData extends PersistentState {

    private static final String DATA_KEY = "pocket_entry_data";

    public static final Type<MobEntryData> TYPE = new Type<>(MobEntryData::new, MobEntryData::fromNbt, DataFixTypes.LEVEL);

    public static MobEntryData get(ServerWorld world) {
        PersistentStateManager mgr = world.getPersistentStateManager();
        return mgr.getOrCreate(TYPE, DATA_KEY);
    }

    private Vec3d entryPos   = Vec3d.ZERO;
    private float entryYaw   = 0f;
    private float entryPitch = 0f;

    public MobEntryData() {
        this.entryPos = new Vec3d(35.5, 85, 16.5); // sensible default
    }

    private static MobEntryData fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        MobEntryData data = new MobEntryData();
        data.readNbt(nbt);
        return data;
    }

    private void readNbt(NbtCompound nbt) {
        this.entryPos   = new Vec3d(nbt.getDouble("entryX"), nbt.getDouble("entryY"), nbt.getDouble("entryZ"));
        this.entryYaw   = nbt.getFloat("entryYaw");
        this.entryPitch = nbt.getFloat("entryPitch");
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        nbt.putDouble("entryX", entryPos.x);
        nbt.putDouble("entryY", entryPos.y);
        nbt.putDouble("entryZ", entryPos.z);
        nbt.putFloat ("entryYaw",   entryYaw);
        nbt.putFloat ("entryPitch", entryPitch);
        return nbt;
    }

    public void setEntry(Vec3d pos, float yaw, float pitch) {
        this.entryPos   = pos;
        this.entryYaw   = yaw;
        this.entryPitch = pitch;
        this.markDirty();
    }

    public Vec3d getEntryPos()   { return entryPos; }
    public float getEntryYaw()   { return entryYaw; }
    public float getEntryPitch() { return entryPitch; }
}