package net.bennyboops.modid.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

public class PlayerEntryData extends PersistentState {
    private static final String DATA_KEY = "pocket_player_entry";

    private Vec3d entryPos = Vec3d.ZERO;
    private float entryYaw = 0f, entryPitch = 0f;

    public PlayerEntryData() {
        super();
        this.entryPos   = new Vec3d(17.5, 97, 9.5);
        this.entryYaw   = 0f;
        this.entryPitch = 0f;
    }

    /** load or create for this world */
    public static PlayerEntryData get(ServerWorld world) {
        PersistentStateManager mgr = world.getPersistentStateManager();
        return mgr.getOrCreate(
                nbt -> {
                    PlayerEntryData d = new PlayerEntryData();
                    d.readNbt(nbt);
                    return d;
                },
                PlayerEntryData::new,
                DATA_KEY
        );
    }

    public void readNbt(NbtCompound nbt) {
        this.entryPos   = new Vec3d(
                nbt.getDouble("px"),
                nbt.getDouble("py"),
                nbt.getDouble("pz")
        );
        this.entryYaw   = nbt.getFloat("pyaw");
        this.entryPitch = nbt.getFloat("ppitch");
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putDouble("px",    entryPos.x);
        nbt.putDouble("py",    entryPos.y);
        nbt.putDouble("pz",    entryPos.z);
        nbt.putFloat( "pyaw",  entryYaw);
        nbt.putFloat( "ppitch", entryPitch);
        return nbt;
    }

    public void setEntry(Vec3d pos, float yaw, float pitch) {
        this.entryPos   = pos;
        this.entryYaw   = yaw;
        this.entryPitch = pitch;
        this.markDirty();
    }

    public Vec3d   getEntryPos()   { return entryPos; }
    public float   getEntryYaw()    { return entryYaw; }
    public float   getEntryPitch()  { return entryPitch; }
}
