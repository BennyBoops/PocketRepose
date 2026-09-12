package net.bennyboops.modid.world;

import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.WorldData;

public final class RuntimeWorldProperties extends DerivedLevelData {
    protected final RuntimeWorldConfig config;
    private final GameRules rules;

    public RuntimeWorldProperties(WorldData worldData, RuntimeWorldConfig config) {
        super(worldData, worldData.overworldData());
        this.config = config;

        this.rules = new GameRules();
        config.getGameRules().applyTo(this.rules, null);
    }

    @Override
    public GameRules getGameRules() {
        return this.rules;
    }

    @Override
    public void setDayTime(long timeOfDay) {
        this.config.setTimeOfDay(timeOfDay);
    }

    @Override
    public long getDayTime() {
        return this.config.getTimeOfDay();
    }

    @Override
    public void setClearWeatherTime(int time) {
        this.config.setSunny(time);
    }

    @Override
    public int getClearWeatherTime() {
        return this.config.getSunnyTime();
    }

    @Override
    public void setRaining(boolean raining) {
        this.config.setRaining(raining);
    }

    @Override
    public boolean isRaining() {
        return this.config.isRaining();
    }

    @Override
    public void setRainTime(int time) {
        this.config.setRaining(time);
    }

    @Override
    public int getRainTime() {
        return this.config.getRainTime();
    }

    @Override
    public void setThundering(boolean thundering) {
        this.config.setThundering(thundering);
    }

    @Override
    public boolean isThundering() {
        return this.config.isThundering();
    }

    @Override
    public void setThunderTime(int time) {
        this.config.setThundering(time);
    }

    @Override
    public int getThunderTime() {
        return this.config.getThunderTime();
    }

    @Override
    public Difficulty getDifficulty() {
        return this.config.getDifficulty();
    }
}
