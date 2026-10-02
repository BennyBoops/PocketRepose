package net.bennyboops.modid.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;

/**
 * Sky renderer for pocket dimensions.
 *
 * Draws, in order: an upper gradient dome, a lower "nebula" dome that fills the
 * space below the horizon, two colourful star layers, aurora ribbons, the sun and
 * moon, and finally a sea of mist below the island that hides the void floor.
 */
public class PocketSkyRenderer implements DimensionRenderingRegistry.SkyRenderer {
    private static final Identifier SUN = new Identifier("textures/environment/sun.png");
    private static final Identifier MOON_PHASES = new Identifier("textures/environment/moon_phases.png");

    private static final float DOME_RADIUS = 100.0f;
    /** World-space height of the mist sea. The island floor is at roughly y 96. */
    private static final double MIST_HEIGHT = 40.0;
    private static final int MIST_GRID = 24;

    private static final int AURORA_SEGMENTS = 48;

    private VertexBuffer starsA;
    private VertexBuffer starsB;
    private final FloatingShardRenderer shards = new FloatingShardRenderer();

    // Palette. Each colour is {r, g, b} in 0..1.
    private static final float[] ZENITH_DAY = {0.46f, 0.42f, 0.90f};
    private static final float[] ZENITH_NIGHT = {0.05f, 0.03f, 0.14f};
    private static final float[] MID_DAY = {0.68f, 0.58f, 0.96f};
    private static final float[] MID_NIGHT = {0.14f, 0.08f, 0.30f};
    private static final float[] BELOW_DAY = {0.52f, 0.38f, 0.82f};
    private static final float[] BELOW_NIGHT = {0.12f, 0.05f, 0.26f};
    private static final float[] NADIR_DAY = {0.20f, 0.10f, 0.42f};
    private static final float[] NADIR_NIGHT = {0.03f, 0.01f, 0.09f};
    private static final float[] NEBULA_A = {0.95f, 0.45f, 0.80f}; // pink
    private static final float[] NEBULA_B = {0.35f, 0.75f, 0.95f}; // cyan
    private static final float[] MIST_DAY = {0.90f, 0.84f, 0.98f};
    private static final float[] MIST_NIGHT = {0.30f, 0.22f, 0.50f};

    @Override
    public void render(WorldRenderContext context) {
        ClientWorld world = context.world();
        MatrixStack matrices = context.matrixStack();
        Matrix4f projection = context.projectionMatrix();
        float tickDelta = context.tickDelta();

        if (starsA == null) {
            starsA = buildStars(10842L, 1300, 0.10f, 0.22f);
            starsB = buildStars(31337L, 700, 0.14f, 0.30f);
        }

        float skyAngle = world.getSkyAngle(tickDelta);
        // 1 at noon, 0 at midnight, smooth in between.
        float day = MathHelper.clamp(MathHelper.cos(skyAngle * MathHelper.TAU) * 1.6f + 0.5f, 0.0f, 1.0f);
        float night = 1.0f - day;
        float rain = world.getRainGradient(tickDelta);
        float clear = 1.0f - rain * 0.6f;
        float time = (world.getTime() % 240000L) + tickDelta;

        float[] fog = RenderSystem.getShaderFogColor();
        float[] horizon = {fog[0], fog[1], fog[2]};

        // The sky pass has fog applied by the caller; we do our own blending by colour instead.
        BackgroundRenderer.clearFog();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Matrix4f pose = matrices.peek().getPositionMatrix();

        renderUpperDome(pose, projection, day, horizon, clear);
        renderLowerDome(pose, projection, day, horizon, time);
        renderStars(matrices, projection, night, time, clear);
        renderAurora(pose, projection, night, time, clear);
        renderSunAndMoon(matrices, projection, world, skyAngle, clear, tickDelta);
        shards.render(matrices, projection, context.camera().getPos(), time, day);
        renderMistSea(matrices, projection, context.camera().getPos(), day, horizon, time);

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    // ---------------------------------------------------------------------------------------------
    // Domes

    private void renderUpperDome(Matrix4f pose, Matrix4f projection, float day, float[] horizon, float clear) {
        float[] zenith = mix(ZENITH_NIGHT, ZENITH_DAY, day);
        float[] mid = mix(MID_NIGHT, MID_DAY, day);
        scale(zenith, clear);
        scale(mid, clear);

        // Rings from the zenith down to just below the horizon. Elevation in degrees, then colour.
        float[] elevations = {90f, 55f, 30f, 14f, 5f, 0f, -4f};
        float[][] colours = {
                zenith,
                mix(zenith, mid, 0.55f),
                mid,
                mix(mid, horizon, 0.55f),
                mix(mid, horizon, 0.85f),
                horizon,
                horizon
        };
        drawRingDome(pose, projection, elevations, colours, null, 0f);
    }

    private void renderLowerDome(Matrix4f pose, Matrix4f projection, float day, float[] horizon, float time) {
        float[] below = mix(BELOW_NIGHT, BELOW_DAY, day);
        float[] nadir = mix(NADIR_NIGHT, NADIR_DAY, day);

        float[] elevations = {0f, -6f, -16f, -30f, -50f, -70f, -90f};
        float[][] colours = {
                horizon,
                mix(horizon, below, 0.45f),
                below,
                mix(below, nadir, 0.45f),
                mix(below, nadir, 0.8f),
                nadir,
                nadir
        };
        // Nebula swirl strength per ring: strongest in the middle of the lower dome.
        float[] swirl = {0f, 0.15f, 0.45f, 0.75f, 0.65f, 0.35f, 0.1f};
        drawRingDome(pose, projection, elevations, colours, swirl, time);
    }

    /**
     * Draws a dome as a sequence of triangle strips between consecutive rings. If swirl is
     * given, the ring colours are tinted by an animated nebula pattern that depends on azimuth.
     */
    private void drawRingDome(Matrix4f pose, Matrix4f projection, float[] elevations, float[][] colours,
                              float[] swirl, float time) {
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        int slices = 48;

        for (int r = 0; r < elevations.length - 1; r++) {
            buffer.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
            for (int s = 0; s <= slices; s++) {
                float azimuth = (float) s / slices * MathHelper.TAU;
                for (int k = 0; k < 2; k++) {
                    int ring = r + k;
                    float elev = elevations[ring] * MathHelper.RADIANS_PER_DEGREE;
                    float y = MathHelper.sin(elev) * DOME_RADIUS;
                    float h = MathHelper.cos(elev) * DOME_RADIUS;
                    float x = MathHelper.cos(azimuth) * h;
                    float z = MathHelper.sin(azimuth) * h;
                    float[] c = colours[ring];
                    float cr = c[0], cg = c[1], cb = c[2];
                    if (swirl != null && swirl[ring] > 0f) {
                        float n = nebula(azimuth, elevations[ring], time);
                        float[] tint = mix(NEBULA_B, NEBULA_A, n);
                        float amount = swirl[ring] * (0.18f + 0.22f * Math.abs(n - 0.5f) * 2f);
                        cr = MathHelper.lerp(amount, cr, tint[0]);
                        cg = MathHelper.lerp(amount, cg, tint[1]);
                        cb = MathHelper.lerp(amount, cb, tint[2]);
                    }
                    buffer.vertex(pose, x, y, z).color(cr, cg, cb, 1.0f).next();
                }
            }
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        }
    }

    /** Slow-moving pseudo-noise in 0..1 over azimuth and elevation. */
    private static float nebula(float azimuth, float elevationDeg, float time) {
        float t = time * 0.00035f;
        float a = MathHelper.sin(azimuth * 3.0f + t * 2.1f + elevationDeg * 0.05f);
        float b = MathHelper.sin(azimuth * 5.0f - t * 1.3f + elevationDeg * 0.11f);
        float c = MathHelper.sin(azimuth * 2.0f + t * 0.7f);
        return MathHelper.clamp((a * 0.5f + b * 0.3f + c * 0.2f) * 0.5f + 0.5f, 0f, 1f);
    }

    // ---------------------------------------------------------------------------------------------
    // Stars

    private VertexBuffer buildStars(long seed, int count, float minSize, float maxSize) {
        Random random = Random.create(seed);
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        float[][] palette = {
                {1.0f, 1.0f, 1.0f},
                {0.80f, 0.88f, 1.0f},
                {1.0f, 0.82f, 0.92f},
                {1.0f, 0.93f, 0.72f},
                {0.75f, 1.0f, 0.95f}
        };

        for (int i = 0; i < count; i++) {
            double dx = random.nextFloat() * 2.0f - 1.0f;
            double dy = random.nextFloat() * 2.0f - 1.0f;
            double dz = random.nextFloat() * 2.0f - 1.0f;
            double size = minSize + random.nextFloat() * (maxSize - minSize);
            double len = dx * dx + dy * dy + dz * dz;
            if (len >= 1.0 || len <= 0.01) {
                continue;
            }
            len = 1.0 / Math.sqrt(len);
            dx *= len;
            dy *= len;
            dz *= len;
            double px = dx * 100.0;
            double py = dy * 100.0;
            double pz = dz * 100.0;
            double azimuth = Math.atan2(dx, dz);
            double sinAz = Math.sin(azimuth);
            double cosAz = Math.cos(azimuth);
            double polar = Math.atan2(Math.sqrt(dx * dx + dz * dz), dy);
            double sinPol = Math.sin(polar);
            double cosPol = Math.cos(polar);
            double spin = random.nextDouble() * Math.PI * 2.0;
            double sinSpin = Math.sin(spin);
            double cosSpin = Math.cos(spin);

            float[] c = palette[random.nextInt(palette.length)];
            float alpha = 0.55f + random.nextFloat() * 0.45f;

            for (int corner = 0; corner < 4; corner++) {
                double cx = ((corner & 2) - 1) * size;
                double cy = ((corner + 1 & 2) - 1) * size;
                double rx = cx * cosSpin - cy * sinSpin;
                double ry = cy * cosSpin + cx * sinSpin;
                double y2 = rx * sinPol;
                double h = -rx * cosPol;
                double x2 = h * sinAz - ry * cosAz;
                double z2 = ry * sinAz + h * cosAz;
                buffer.vertex(px + x2, py + y2, pz + z2).color(c[0], c[1], c[2], alpha).next();
            }
        }

        VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vb.bind();
        vb.upload(buffer.end());
        VertexBuffer.unbind();
        return vb;
    }

    private void renderStars(MatrixStack matrices, Matrix4f projection, float night, float time, float clear) {
        // Faint by day so the sky always reads as enchanted, bright at night.
        float base = (0.12f + 0.88f * night) * clear;
        float twinkleA = 0.80f + 0.20f * MathHelper.sin(time * 0.045f);
        float twinkleB = 0.75f + 0.25f * MathHelper.sin(time * 0.071f + 1.7f);

        RenderSystem.blendFuncSeparate(
                com.mojang.blaze3d.platform.GlStateManager.SrcFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DstFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.SrcFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.DstFactor.ZERO);

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * 0.0025f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(25.0f));
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, base * twinkleA);
        drawBuffer(starsA, matrices.peek().getPositionMatrix(), projection);
        matrices.pop();

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-time * 0.0018f + 40.0f));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-35.0f));
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, base * twinkleB);
        drawBuffer(starsB, matrices.peek().getPositionMatrix(), projection);
        matrices.pop();

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.defaultBlendFunc();
    }

    private static void drawBuffer(VertexBuffer buffer, Matrix4f pose, Matrix4f projection) {
        buffer.bind();
        buffer.draw(pose, projection, GameRenderer.getPositionColorProgram());
        VertexBuffer.unbind();
    }

    // ---------------------------------------------------------------------------------------------
    // Aurora

    private void renderAurora(Matrix4f pose, Matrix4f projection, float night, float time, float clear) {
        float strength = (0.10f + 0.40f * night) * clear;
        if (strength <= 0.01f) {
            return;
        }
        RenderSystem.blendFuncSeparate(
                com.mojang.blaze3d.platform.GlStateManager.SrcFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DstFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.SrcFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.DstFactor.ZERO);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        // Each ribbon: start azimuth, sweep, base elevation, wave amplitude, speed, phase.
        float[][] ribbons = {
                {0.3f, 2.6f, 42f, 7f, 0.0016f, 0.0f},
                {2.9f, 2.2f, 58f, 5f, -0.0011f, 2.1f},
                {4.6f, 1.9f, 33f, 9f, 0.0021f, 4.2f}
        };
        float[][] colours = {
                {0.35f, 0.95f, 0.75f}, // teal-green
                {0.60f, 0.45f, 1.00f}, // violet
                {1.00f, 0.55f, 0.85f}  // pink
        };

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        for (int i = 0; i < ribbons.length; i++) {
            float[] rb = ribbons[i];
            float drift = time * rb[4] + rb[5];
            buffer.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
            for (int s = 0; s <= AURORA_SEGMENTS; s++) {
                float t = (float) s / AURORA_SEGMENTS;
                float azimuth = rb[0] + rb[1] * t + time * 0.00004f;
                float wave = MathHelper.sin(t * 9.0f + drift * 3.0f) * rb[3]
                        + MathHelper.sin(t * 4.0f - drift * 1.7f) * rb[3] * 0.5f;
                float baseElev = rb[2] + wave;
                float thickness = 9.0f + 4.0f * MathHelper.sin(t * 6.0f + drift * 2.0f);
                // Fade the ends of the ribbon.
                float endFade = MathHelper.clamp(MathHelper.sin(t * MathHelper.PI), 0f, 1f);
                float shimmer = 0.7f + 0.3f * MathHelper.sin(t * 22.0f + time * 0.03f + i);
                float alpha = strength * endFade * shimmer;

                float colourShift = MathHelper.clamp(0.5f + 0.5f * MathHelper.sin(t * 3.0f + drift), 0f, 1f);
                float[] c = mix(colours[i], colours[(i + 1) % colours.length], colourShift);

                // Bottom (transparent), then top (transparent) with a bright middle emulated by a 3-point strip.
                emitDomeVertex(buffer, pose, azimuth, baseElev, c, 0.0f);
                emitDomeVertex(buffer, pose, azimuth, baseElev + thickness * 0.35f, c, alpha);
            }
            BufferRenderer.drawWithGlobalProgram(buffer.end());

            buffer.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
            for (int s = 0; s <= AURORA_SEGMENTS; s++) {
                float t = (float) s / AURORA_SEGMENTS;
                float azimuth = rb[0] + rb[1] * t + time * 0.00004f;
                float wave = MathHelper.sin(t * 9.0f + drift * 3.0f) * rb[3]
                        + MathHelper.sin(t * 4.0f - drift * 1.7f) * rb[3] * 0.5f;
                float baseElev = rb[2] + wave;
                float thickness = 9.0f + 4.0f * MathHelper.sin(t * 6.0f + drift * 2.0f);
                float endFade = MathHelper.clamp(MathHelper.sin(t * MathHelper.PI), 0f, 1f);
                float shimmer = 0.7f + 0.3f * MathHelper.sin(t * 22.0f + time * 0.03f + i);
                float alpha = strength * endFade * shimmer;
                float colourShift = MathHelper.clamp(0.5f + 0.5f * MathHelper.sin(t * 3.0f + drift), 0f, 1f);
                float[] c = mix(colours[i], colours[(i + 1) % colours.length], colourShift);

                emitDomeVertex(buffer, pose, azimuth, baseElev + thickness * 0.35f, c, alpha);
                emitDomeVertex(buffer, pose, azimuth, baseElev + thickness, c, 0.0f);
            }
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        }
        RenderSystem.defaultBlendFunc();
    }

    private static void emitDomeVertex(BufferBuilder buffer, Matrix4f pose, float azimuth, float elevationDeg,
                                       float[] c, float alpha) {
        float elev = elevationDeg * MathHelper.RADIANS_PER_DEGREE;
        float y = MathHelper.sin(elev) * DOME_RADIUS;
        float h = MathHelper.cos(elev) * DOME_RADIUS;
        float x = MathHelper.cos(azimuth) * h;
        float z = MathHelper.sin(azimuth) * h;
        buffer.vertex(pose, x, y, z).color(c[0], c[1], c[2], alpha).next();
    }

    // ---------------------------------------------------------------------------------------------
    // Sun and moon (vanilla-like)

    private void renderSunAndMoon(MatrixStack matrices, Matrix4f projection, ClientWorld world, float skyAngle,
                                  float clear, float tickDelta) {
        RenderSystem.blendFuncSeparate(
                com.mojang.blaze3d.platform.GlStateManager.SrcFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DstFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.SrcFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.DstFactor.ZERO);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, clear);

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90.0f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(skyAngle * 360.0f));
        Matrix4f pose = matrices.peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        float sunSize = 30.0f;
        RenderSystem.setShaderTexture(0, SUN);
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        buffer.vertex(pose, -sunSize, 100.0f, -sunSize).texture(0.0f, 0.0f).next();
        buffer.vertex(pose, sunSize, 100.0f, -sunSize).texture(1.0f, 0.0f).next();
        buffer.vertex(pose, sunSize, 100.0f, sunSize).texture(1.0f, 1.0f).next();
        buffer.vertex(pose, -sunSize, 100.0f, sunSize).texture(0.0f, 1.0f).next();
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        float moonSize = 20.0f;
        RenderSystem.setShaderTexture(0, MOON_PHASES);
        int phase = world.getMoonPhase();
        int px = phase % 4;
        int py = phase / 4 % 2;
        float u0 = px / 4.0f;
        float v0 = py / 2.0f;
        float u1 = (px + 1) / 4.0f;
        float v1 = (py + 1) / 2.0f;
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        buffer.vertex(pose, -moonSize, -100.0f, moonSize).texture(u1, v1).next();
        buffer.vertex(pose, moonSize, -100.0f, moonSize).texture(u0, v1).next();
        buffer.vertex(pose, moonSize, -100.0f, -moonSize).texture(u0, v0).next();
        buffer.vertex(pose, -moonSize, -100.0f, -moonSize).texture(u1, v0).next();
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        matrices.pop();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.defaultBlendFunc();
    }

    // ---------------------------------------------------------------------------------------------
    // Mist sea

    /**
     * A soft, slowly drifting plane of mist below the island. It writes depth so that the
     * portal-block floor at the bottom of the world is hidden behind it.
     */
    private void renderMistSea(MatrixStack matrices, Matrix4f projection, Vec3d camera, float day, float[] horizon,
                               float time) {
        float relY = (float) (MIST_HEIGHT - camera.y);
        if (relY > -2.0f) {
            // Camera is at or below the mist: skip rather than draw a plane through the player.
            return;
        }
        float[] mist = mix(MIST_NIGHT, MIST_DAY, day);
        // Pull the mist slightly towards the horizon colour so it fades into the fog at distance.
        float[] mistFar = mix(mist, horizon, 0.6f);

        // Cover a square around the camera. Its size follows the far plane so it never clips.
        float extent = 700.0f;
        float cell = extent * 2.0f / MIST_GRID;
        double originX = Math.floor(camera.x / cell) * cell;
        double originZ = Math.floor(camera.z / cell) * cell;

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        Matrix4f pose = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();

        for (int gz = -MIST_GRID / 2; gz < MIST_GRID / 2; gz++) {
            buffer.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
            for (int gx = -MIST_GRID / 2; gx <= MIST_GRID / 2; gx++) {
                for (int k = 0; k < 2; k++) {
                    double wx = originX + gx * cell;
                    double wz = originZ + (gz + k) * cell;
                    float rx = (float) (wx - camera.x);
                    float rz = (float) (wz - camera.z);
                    float dist = MathHelper.sqrt(rx * rx + rz * rz);
                    float farBlend = MathHelper.clamp(dist / extent, 0f, 1f);
                    float[] c = mix(mist, mistFar, farBlend);
                    float puff = mistNoise((float) wx, (float) wz, time);
                    float alpha = 0.70f + 0.30f * puff;
                    float bright = 0.88f + 0.12f * puff;
                    buffer.vertex(pose, rx, relY, rz)
                            .color(c[0] * bright, c[1] * bright, c[2] * bright, alpha).next();
                }
            }
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        }
        RenderSystem.depthMask(false);
    }

    /** Slow drifting cloud-like value in 0..1 from world coordinates. */
    private static float mistNoise(float x, float z, float time) {
        float t = time * 0.02f;
        float a = MathHelper.sin(x * 0.020f + t * 0.35f) * MathHelper.cos(z * 0.017f - t * 0.25f);
        float b = MathHelper.sin((x + z) * 0.011f + t * 0.15f);
        float c = MathHelper.cos((x - z * 0.7f) * 0.031f - t * 0.45f);
        return MathHelper.clamp((a * 0.5f + b * 0.3f + c * 0.2f) * 0.5f + 0.5f, 0f, 1f);
    }

    // ---------------------------------------------------------------------------------------------
    // Colour helpers

    private static float[] mix(float[] a, float[] b, float t) {
        return new float[]{
                MathHelper.lerp(t, a[0], b[0]),
                MathHelper.lerp(t, a[1], b[1]),
                MathHelper.lerp(t, a[2], b[2])
        };
    }

    private static void scale(float[] c, float f) {
        c[0] *= f;
        c[1] *= f;
        c[2] *= f;
    }
}
