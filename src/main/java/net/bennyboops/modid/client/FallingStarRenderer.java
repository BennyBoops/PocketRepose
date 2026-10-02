package net.bennyboops.modid.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Falling stars that streak down past the island and vanish into the mist below.
 * They live in world space and are drawn after the terrain, so the island occludes them.
 */
public class FallingStarRenderer {
    /** The island structure is placed at (0,64,0) and is 48 blocks wide, so it is centred here. */
    private static final double CENTER_X = 24.0;
    private static final double CENTER_Z = 24.0;
    /** Stars fade out here, just below the mist sea at y 40. */
    private static final double END_Y = 28.0;

    private static final int TRAIL_SEGMENTS = 20;
    /** Ticks between streaks: 5 to 10 seconds. */
    private static final float MIN_GAP = 100f;
    private static final float MAX_GAP = 200f;

    private static final float[][] COLOURS = {
            {1.00f, 1.00f, 1.00f},
            {0.80f, 0.90f, 1.00f},
            {1.00f, 0.85f, 0.95f},
            {1.00f, 0.95f, 0.70f},
            {0.75f, 1.00f, 0.95f}
    };

    private final Random random = Random.create();
    private final List<Star> active = new ArrayList<>();
    private float nextSpawn = -1f;
    private float lastTime = -1f;

    private static final class Star {
        Vec3d start;
        Vec3d end;
        float spawnTime;
        float duration;
        float trail;      // fraction of the path the tail spans
        float width;      // world blocks
        float[] colour;
    }

    public void render(WorldRenderContext context) {
        ClientWorld world = context.world();
        float tickDelta = context.tickDelta();
        float time = (world.getTime() % 240000L) + tickDelta;
        float skyAngle = world.getSkyAngle(tickDelta);
        float day = MathHelper.clamp(MathHelper.cos(skyAngle * MathHelper.TAU) * 1.6f + 0.5f, 0.0f, 1.0f);
        float night = 1.0f - day;
        float clear = 1.0f - world.getRainGradient(tickDelta) * 0.6f;

        if (lastTime < 0f || time < lastTime) {
            nextSpawn = time + 200f + random.nextFloat() * 600f;
        }
        lastTime = time;

        if (time >= nextSpawn) {
            spawn(time);
            nextSpawn = time + MIN_GAP + random.nextFloat() * (MAX_GAP - MIN_GAP);
            if (random.nextInt(4) == 0) {
                nextSpawn = time + 40f + random.nextFloat() * 80f;
            }
        }
        if (active.isEmpty()) {
            return;
        }

        Vec3d camera = context.camera().getPos();
        Matrix4f pose = context.matrixStack().peek().getPositionMatrix();
        float visibility = (0.55f + 0.45f * night) * clear;

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE,
                GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ZERO);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        Iterator<Star> it = active.iterator();
        while (it.hasNext()) {
            Star s = it.next();
            float progress = (time - s.spawnTime) / s.duration;
            if (progress > 1.0f + s.trail) {
                it.remove();
                continue;
            }
            drawStar(buffer, pose, camera, s, progress, visibility);
        }

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void spawn(float time) {
        Star s = new Star();
        // The island spans 4..44 on both axes (28 blocks from centre at the corners), so start
        // beyond that and only ever drift outward, so a star never crosses over the land.
        double angle = random.nextDouble() * Math.PI * 2;
        double radius = 34 + random.nextDouble() * 46;
        double sx = CENTER_X + Math.cos(angle) * radius;
        double sz = CENTER_Z + Math.sin(angle) * radius;
        double sy = 150 + random.nextDouble() * 40;
        double driftAngle = angle + (random.nextDouble() - 0.5) * (Math.PI * 0.45);
        double drift = 8 + random.nextDouble() * 22;
        s.start = new Vec3d(sx, sy, sz);
        s.end = new Vec3d(sx + Math.cos(driftAngle) * drift, END_Y, sz + Math.sin(driftAngle) * drift);
        s.spawnTime = time;
        s.duration = 34f + random.nextFloat() * 16f;
        s.trail = 0.28f + random.nextFloat() * 0.14f;
        s.width = 0.9f + random.nextFloat() * 0.6f;
        s.colour = COLOURS[random.nextInt(COLOURS.length)];
        active.add(s);
    }

    private static Vector3f pointAt(Star s, Vec3d camera, float t) {
        // Ease in slightly so the star accelerates as it falls.
        float e = t * t * 0.35f + t * 0.65f;
        Vec3d p = s.start.lerp(s.end, e);
        return new Vector3f((float) (p.x - camera.x), (float) (p.y - camera.y), (float) (p.z - camera.z));
    }

    private void drawStar(BufferBuilder buffer, Matrix4f pose, Vec3d camera, Star s, float progress, float visibility) {
        float head = Math.min(progress, 1.0f);
        float tail = Math.max(progress - s.trail, 0.0f);
        if (head <= tail) {
            return;
        }
        float fade = MathHelper.clamp(progress * 6f, 0f, 1f)
                * MathHelper.clamp((1.0f + s.trail - progress) / s.trail, 0f, 1f);
        // Dim as the head sinks into the mist.
        Vector3f headPos = pointAt(s, camera, head);
        float mistFade = MathHelper.clamp((float) ((headPos.y + camera.y) - END_Y) / 14f, 0.15f, 1f);
        float alphaScale = visibility * fade * mistFade;
        float[] c = s.colour;

        drawTrail(buffer, pose, camera, s, tail, head, s.width * 1.3f, 0.40f * alphaScale, c, 0.55f);
        drawTrail(buffer, pose, camera, s, tail, head, s.width * 0.32f, 1.0f * alphaScale, c, 1.0f);
        if (progress <= 1.0f) {
            drawHead(buffer, pose, headPos, s.width * 2.2f, alphaScale, c);
        }
    }

    private void drawTrail(BufferBuilder buffer, Matrix4f pose, Vec3d camera, Star s, float tail, float head,
                           float maxWidth, float maxAlpha, float[] c, float whiteness) {
        buffer.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= TRAIL_SEGMENTS; i++) {
            float k = (float) i / TRAIL_SEGMENTS;
            float t = MathHelper.lerp(k, tail, head);
            Vector3f p = pointAt(s, camera, t);
            Vector3f ahead = pointAt(s, camera, Math.min(t + 0.01f, 1.0f));
            Vector3f dir = new Vector3f(ahead).sub(p);
            if (dir.lengthSquared() < 1e-6f) {
                dir = new Vector3f((float) (s.end.x - s.start.x), (float) (s.end.y - s.start.y), (float) (s.end.z - s.start.z));
            }
            // Perpendicular to motion and to the view ray (camera is at the origin), so the strip faces the camera.
            Vector3f side = new Vector3f(dir).cross(p);
            if (side.lengthSquared() < 1e-6f) {
                side.set(1, 0, 0);
            }
            side.normalize();
            float profile = k * k * (1.0f - 0.35f * k);
            float width = maxWidth * (0.1f + 0.9f * k);
            float alpha = maxAlpha * profile / 0.65f;
            float r = MathHelper.lerp(whiteness * k, c[0], 1.0f);
            float g = MathHelper.lerp(whiteness * k, c[1], 1.0f);
            float b = MathHelper.lerp(whiteness * k, c[2], 1.0f);
            buffer.vertex(pose, p.x + side.x * width, p.y + side.y * width, p.z + side.z * width)
                    .color(r, g, b, alpha).next();
            buffer.vertex(pose, p.x - side.x * width, p.y - side.y * width, p.z - side.z * width)
                    .color(r, g, b, alpha).next();
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    /** A soft round camera-facing glow with a small hot core. */
    private void drawHead(BufferBuilder buffer, Matrix4f pose, Vector3f p, float radius, float alpha, float[] c) {
        Vector3f view = new Vector3f(p);
        if (view.lengthSquared() < 1e-4f) {
            return;
        }
        view.normalize();
        Vector3f up = new Vector3f(0, 1, 0).cross(view);
        if (up.lengthSquared() < 1e-4f) {
            up.set(1, 0, 0);
        }
        up.normalize();
        Vector3f right = new Vector3f(view).cross(up).normalize();

        int segments = 16;
        for (int pass = 0; pass < 2; pass++) {
            float rad = pass == 0 ? radius : radius * 0.3f;
            float edgeAlpha = pass == 0 ? 0.0f : alpha * 0.4f;
            buffer.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
            buffer.vertex(pose, p.x, p.y, p.z).color(1f, 1f, 1f, alpha).next();
            for (int i = 0; i <= segments; i++) {
                float ang = (float) i / segments * MathHelper.TAU;
                float cx = MathHelper.cos(ang) * rad;
                float cy = MathHelper.sin(ang) * rad;
                buffer.vertex(pose, p.x + up.x * cy + right.x * cx, p.y + up.y * cy + right.y * cx,
                        p.z + up.z * cy + right.z * cx).color(c[0], c[1], c[2], edgeAlpha).next();
            }
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        }
    }
}
