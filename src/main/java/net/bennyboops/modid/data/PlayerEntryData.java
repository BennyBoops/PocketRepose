package net.bennyboops.modid.data;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

public class PlayerEntryData extends PersistentState {

    private static final String DATA_KEY = "pocket_player_entry";

    public static final Type<PlayerEntryData> TYPE = new Type<>(PlayerEntryData::new, PlayerEntryData::fromNbt, DataFixTypes.LEVEL);

    public static PlayerEntryData get(ServerWorld world) {
        PersistentStateManager mgr = world.getPersistentStateManager();
        return mgr.getOrCreate(TYPE, DATA_KEY);
    }

    private Vec3d entryPos   = Vec3d.ZERO;
    private float entryYaw   = 0f;
    private float entryPitch = 0f;

    public PlayerEntryData() {
        this.entryPos = new Vec3d(17.5, 97, 9.5); // default spawn
    }

    private static PlayerEntryData fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapperLookup) {
        PlayerEntryData data = new PlayerEntryData();
        data.readNbt(nbt);
        return data;
    }

    private void readNbt(NbtCompound nbt) {
        this.entryPos = new Vec3d(
                nbt.getDouble("px"),
                nbt.getDouble("py"),
                nbt.getDouble("pz"));
        this.entryYaw   = nbt.getFloat("pyaw");
        this.entryPitch = nbt.getFloat("ppitch");
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapperLookup) {
        nbt.putDouble("px",    entryPos.x);
        nbt.putDouble("py",    entryPos.y);
        nbt.putDouble("pz",    entryPos.z);
        nbt.putFloat ("pyaw",  entryYaw);
        nbt.putFloat ("ppitch", entryPitch);
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
