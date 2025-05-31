package net.bennyboops.modid.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

public class PocketEntryData extends PersistentState {
    private static final String DATA_KEY = "pocket_entry_data";
    private Vec3d entryPos = Vec3d.ZERO;
    private float entryYaw = 0f, entryPitch = 0f;

    public PocketEntryData() {
        super();
        this.entryPos   = new Vec3d(35.5, 85, 16.5);
        this.entryYaw   = 0f;
        this.entryPitch = 0f;
    }

    public static PocketEntryData get(ServerWorld world) {
        PersistentStateManager mgr = world.getPersistentStateManager();
        return mgr.getOrCreate(
                nbt -> {
                    PocketEntryData d = new PocketEntryData();
                    d.readNbt(nbt);
                    return d;
                },
                PocketEntryData::new,
                DATA_KEY
        );
    }

    public void readNbt(NbtCompound nbt) {
        double x = nbt.getDouble("entryX");
        double y = nbt.getDouble("entryY");
        double z = nbt.getDouble("entryZ");
        this.entryPos = new Vec3d(x, y, z);
        this.entryYaw   = nbt.getFloat("entryYaw");
        this.entryPitch = nbt.getFloat("entryPitch");
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putDouble("entryX",   entryPos.x);
        nbt.putDouble("entryY",   entryPos.y);
        nbt.putDouble("entryZ",   entryPos.z);
        nbt.putFloat( "entryYaw",   entryYaw);
        nbt.putFloat( "entryPitch", entryPitch);
        return nbt;
    }

    public void setEntry(Vec3d pos, float yaw, float pitch) {
        this.entryPos   = pos;
        this.entryYaw   = yaw;
        this.entryPitch = pitch;
        this.markDirty();
    }

    public Vec3d getEntryPos()   { return entryPos; }
    public float getEntryYaw()    { return entryYaw; }
    public float getEntryPitch()  { return entryPitch; }
}
