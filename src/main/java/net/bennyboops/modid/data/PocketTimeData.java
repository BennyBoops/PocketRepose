package net.bennyboops.modid.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

/** Per-pocket-dimension time of day and how fast it flows. Saved with the dimension. */
public class PocketTimeData extends PersistentState {
    private static final String DATA_KEY = "pocket_time";

    public static final int SPEED_STOPPED = 0;
    public static final int SPEED_NORMAL = 1;
    public static final int SPEED_FAST = 10;

    private long timeOfDay = 6000L;
    private int speed = SPEED_STOPPED;

    public static PocketTimeData get(ServerWorld world) {
        PersistentStateManager mgr = world.getPersistentStateManager();
        return mgr.getOrCreate(
                nbt -> {
                    PocketTimeData d = new PocketTimeData();
                    d.readNbt(nbt);
                    return d;
                },
                PocketTimeData::new,
                DATA_KEY
        );
    }

    public void readNbt(NbtCompound nbt) {
        this.timeOfDay = nbt.getLong("timeOfDay");
        this.speed = nbt.contains("speed") ? nbt.getInt("speed") : SPEED_STOPPED;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putLong("timeOfDay", timeOfDay);
        nbt.putInt("speed", speed);
        return nbt;
    }

    public long getTimeOfDay() {
        return timeOfDay;
    }

    public int getSpeed() {
        return speed;
    }

    /** Advances the clock by the current speed. Returns true if time moved. */
    public boolean tick() {
        if (speed <= 0) {
            return false;
        }
        timeOfDay += speed;
        markDirty();
        return true;
    }

    /** Normal, then fast, then stopped, then back to normal. */
    public int cycleSpeed() {
        speed = switch (speed) {
            case SPEED_NORMAL -> SPEED_FAST;
            case SPEED_FAST -> SPEED_STOPPED;
            default -> SPEED_NORMAL;
        };
        markDirty();
        return speed;
    }
}
