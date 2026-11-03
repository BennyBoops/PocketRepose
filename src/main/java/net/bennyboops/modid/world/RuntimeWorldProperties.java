package net.bennyboops.modid.world;

import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;
import net.minecraft.world.SaveProperties;
import net.minecraft.world.level.UnmodifiableLevelProperties;

public final class RuntimeWorldProperties extends UnmodifiableLevelProperties {
    protected final RuntimeWorldConfig config;
    private final SaveProperties saveProperties;
    private final GameRules rules;

    public RuntimeWorldProperties(SaveProperties saveProperties, RuntimeWorldConfig config) {
        super(saveProperties, saveProperties.getMainWorldProperties());
        this.config = config;
        this.saveProperties = saveProperties;

        this.rules = new GameRules();
        config.getGameRules().applyTo(this.rules, null);
    }

    @Override
    public GameRules getGameRules() {
        // Share the overworld's GameRules instance so /gamerule changes apply here immediately
        if (this.config.shouldMirrorOverworldGameRules()) {
            return this.saveProperties.getGameRules();
        }
        return this.rules;
    }

    @Override
    public void setTimeOfDay(long timeOfDay) {
        if (this.config.shouldMirrorOverworldTimeAndWeather()) {
            super.setTimeOfDay(timeOfDay);
            return;
        }
        this.config.setTimeOfDay(timeOfDay);
    }

    @Override
    public long getTimeOfDay() {
        if (this.config.shouldMirrorOverworldTimeAndWeather()) {
            return super.getTimeOfDay();
        }
        return this.config.getTimeOfDay();
    }

    @Override
    public void setClearWeatherTime(int time) {
        if (this.config.shouldMirrorOverworldTimeAndWeather()) {
            super.setClearWeatherTime(time);
            return;
        }
        this.config.setSunny(time);
    }

    @Override
    public int getClearWeatherTime() {
        if (this.config.shouldMirrorOverworldTimeAndWeather()) {
            return super.getClearWeatherTime();
        }
        return this.config.getSunnyTime();
    }

    @Override
    public void setRaining(boolean raining) {
        if (this.config.shouldMirrorOverworldTimeAndWeather()) {
            super.setRaining(raining);
            return;
        }
        this.config.setRaining(raining);
    }

    @Override
    public boolean isRaining() {
        if (this.config.shouldMirrorOverworldTimeAndWeather()) {
            return super.isRaining();
        }
        return this.config.isRaining();
    }

    @Override
    public void setRainTime(int time) {
        if (this.config.shouldMirrorOverworldTimeAndWeather()) {
            super.setRainTime(time);
            return;
        }
        this.config.setRaining(time);
    }

    @Override
    public int getRainTime() {
        if (this.config.shouldMirrorOverworldTimeAndWeather()) {
            return super.getRainTime();
        }
        return this.config.getRainTime();
    }

    @Override
    public void setThundering(boolean thundering) {
        if (this.config.shouldMirrorOverworldTimeAndWeather()) {
            super.setThundering(thundering);
            return;
        }
        this.config.setThundering(thundering);
    }

    @Override
    public boolean isThundering() {
        if (this.config.shouldMirrorOverworldTimeAndWeather()) {
            return super.isThundering();
        }
        return this.config.isThundering();
    }

    @Override
    public void setThunderTime(int time) {
        if (this.config.shouldMirrorOverworldTimeAndWeather()) {
            super.setThunderTime(time);
            return;
        }
        this.config.setThundering(time);
    }

    @Override
    public int getThunderTime() {
        if (this.config.shouldMirrorOverworldTimeAndWeather()) {
            return super.getThunderTime();
        }
        return this.config.getThunderTime();
    }

    @Override
    public Difficulty getDifficulty() {
        if (this.config.shouldMirrorOverworldDifficulty()) {
            return this.saveProperties.getDifficulty();
        }
        return this.config.getDifficulty();
    }
}
