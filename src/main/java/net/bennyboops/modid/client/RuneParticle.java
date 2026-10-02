package net.bennyboops.modid.client;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.util.math.MathHelper;

/** A small glowing rune that drifts upward, sways gently and fades out. */
public class RuneParticle extends SpriteBillboardParticle {
    private static final float[][] PALETTE = {
            {0.75f, 0.55f, 1.00f}, // violet
            {0.45f, 0.90f, 1.00f}, // cyan
            {1.00f, 0.65f, 0.95f}, // pink
            {1.00f, 0.90f, 0.55f}, // gold
            {0.70f, 1.00f, 0.85f}  // mint
    };

    private final float swayPhase;
    private final float swaySpeed;
    private final float baseScale;

    protected RuneParticle(ClientWorld world, double x, double y, double z, SpriteProvider sprites) {
        super(world, x, y, z, 0.0, 0.0, 0.0);
        this.setSprite(sprites);
        this.velocityX = (random.nextDouble() - 0.5) * 0.006;
        this.velocityY = 0.012 + random.nextDouble() * 0.014;
        this.velocityZ = (random.nextDouble() - 0.5) * 0.006;
        this.maxAge = 70 + random.nextInt(60);
        this.gravityStrength = 0.0f;
        this.collidesWithWorld = false;
        this.baseScale = 0.06f + random.nextFloat() * 0.05f;
        this.scale = this.baseScale;
        this.swayPhase = random.nextFloat() * MathHelper.TAU;
        this.swaySpeed = 0.06f + random.nextFloat() * 0.05f;
        this.angle = (random.nextFloat() - 0.5f) * 0.6f;
        float[] c = PALETTE[random.nextInt(PALETTE.length)];
        this.setColor(c[0], c[1], c[2]);
        this.alpha = 0.0f;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.dead) {
            return;
        }
        float t = (float) this.age / this.maxAge;
        // Fade in quickly, hold, then fade out.
        float fadeIn = MathHelper.clamp(t * 6.0f, 0.0f, 1.0f);
        float fadeOut = MathHelper.clamp((1.0f - t) * 2.5f, 0.0f, 1.0f);
        this.alpha = Math.min(fadeIn, fadeOut) * 0.9f;
        this.scale = this.baseScale * (0.8f + 0.2f * fadeIn);
        float sway = MathHelper.sin(this.age * this.swaySpeed + this.swayPhase) * 0.0025f;
        this.velocityX += sway;
        this.velocityZ += MathHelper.cos(this.age * this.swaySpeed * 0.8f + this.swayPhase) * 0.0025f;
        this.velocityX *= 0.93;
        this.velocityZ *= 0.93;
        this.prevAngle = this.angle;
        this.angle += 0.01f;
    }

    @Override
    public int getBrightness(float tint) {
        return 0xF000F0;
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Factory implements ParticleFactory<DefaultParticleType> {
        private final SpriteProvider sprites;

        public Factory(SpriteProvider sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(DefaultParticleType type, ClientWorld world, double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new RuneParticle(world, x, y, z, sprites);
        }
    }
}
