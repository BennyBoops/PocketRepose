package net.bennyboops.modid.client;

import net.minecraft.client.render.DimensionEffects;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Dimension effects for pocket dimensions: lower, closer clouds and a fog colour
 * tinted towards a twilight palette so the horizon blends with the custom sky.
 */
public class PocketDimensionEffects extends DimensionEffects {
    /** Clouds drift a little above the island (which sits at roughly y 96 to 100). */
    public static final float CLOUD_HEIGHT = 132.0f;

    public PocketDimensionEffects() {
        super(CLOUD_HEIGHT, true, SkyType.NORMAL, false, false);
    }

    @Override
    public Vec3d adjustFogColor(Vec3d color, float sunHeight) {
        // sunHeight: 1 = noon, 0 = midnight (already clamped by the caller).
        float day = MathHelper.clamp(sunHeight, 0.0f, 1.0f);

        // Pastel lilac by day, deep violet by night.
        Vec3d dayTint = new Vec3d(0.86, 0.74, 0.97);
        Vec3d nightTint = new Vec3d(0.14, 0.09, 0.26);
        Vec3d tint = nightTint.lerp(dayTint, day);

        // Blend the biome fog colour towards the tint, then keep the vanilla day/night dimming curve.
        Vec3d blended = color.lerp(tint, 0.55);
        double brightness = day * 0.90 + 0.10;
        return blended.multiply(brightness);
    }

    @Override
    public boolean useThickFog(int camX, int camY) {
        return false;
    }
}
