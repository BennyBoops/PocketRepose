package net.bennyboops.modid.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A handful of floating crystal formations slowly orbiting the island. Each one is a cluster of
 * amethyst block models, optionally tinted to other gem colours, rendered during the sky pass
 * so they use proper textures and the world lightmap but have no collision and live in no
 * world data.
 */
public class FloatingShardRenderer {
    /** The island structure is placed at (0,64,0) and is 48 blocks wide, so it is centred here. */
    private static final double CENTER_X = 24.0;
    private static final double CENTER_Z = 24.0;
    private static final int SHARD_COUNT = 14;

    private final Shard[] shards = new Shard[SHARD_COUNT];

    private record Piece(int x, int y, int z, BlockState state) {
    }

    private static final class Shard {
        double radius;
        double angle;
        double speed;
        double height;
        double bob;
        double bobSpeed;
        float tilt;
        float yawSpeed;
        float scale;
        float[] tint;
        List<Piece> pieces;
    }

    /** Crystal colours. Amethyst is native; the others are tints applied to the same models. */
    private static final float[][] TINTS = {
            {1.00f, 1.00f, 1.00f}, // amethyst (natural purple)
            {1.00f, 1.00f, 1.00f},
            {0.50f, 0.72f, 1.00f}, // sapphire blue
            {1.00f, 0.58f, 0.80f}, // rose quartz
            {0.42f, 1.00f, 0.82f}, // teal
    };

    public FloatingShardRenderer() {
        Random random = Random.create(7741L);
        for (int i = 0; i < SHARD_COUNT; i++) {
            Shard s = new Shard();
            boolean inner = i % 2 == 0;
            s.radius = inner ? 40 + random.nextDouble() * 16 : 58 + random.nextDouble() * 26;
            s.angle = random.nextDouble() * Math.PI * 2;
            s.speed = (0.00025 + random.nextDouble() * 0.0003) * (inner ? 1.0 : 0.65) * (random.nextBoolean() ? 1 : -1);
            s.height = inner ? 66 + random.nextDouble() * 30 : 54 + random.nextDouble() * 44;
            s.bob = 0.6 + random.nextDouble() * 1.6;
            s.bobSpeed = 0.005 + random.nextDouble() * 0.006;
            s.tilt = (random.nextFloat() - 0.5f) * 50f;
            s.yawSpeed = (random.nextFloat() - 0.5f) * 0.03f;
            s.scale = 0.7f + random.nextFloat() * 0.35f;
            s.pieces = buildCrystal(random);
            s.tint = TINTS[random.nextInt(TINTS.length)];
            shards[i] = s;
        }
    }

    /**
     * An elongated amethyst core with crystal clusters growing out of its exposed faces.
     * The core is 1 to 2 blocks wide and 2 to 4 tall, with the ends narrowed to a point.
     */
    private static List<Piece> buildCrystal(Random random) {
        List<Piece> pieces = new ArrayList<>();
        Set<Long> filled = new HashSet<>();
        int height = 2 + random.nextInt(3);
        boolean wide = random.nextInt(3) == 0;

        for (int y = 0; y < height; y++) {
            boolean end = y == 0 || y == height - 1;
            for (int x = 0; x < (wide ? 2 : 1); x++) {
                for (int z = 0; z < (wide ? 2 : 1); z++) {
                    // Taper: at the ends of a wide core, drop some blocks.
                    if (wide && end && random.nextBoolean()) {
                        continue;
                    }
                    BlockState body = random.nextInt(3) == 0
                            ? Blocks.BUDDING_AMETHYST.getDefaultState()
                            : Blocks.AMETHYST_BLOCK.getDefaultState();
                    pieces.add(new Piece(x, y, z, body));
                    filled.add(key(x, y, z));
                }
            }
        }
        if (filled.isEmpty()) {
            pieces.add(new Piece(0, 0, 0, Blocks.AMETHYST_BLOCK.getDefaultState()));
            filled.add(key(0, 0, 0));
        }

        // Crystals on exposed faces: always the top and bottom points, plus a scattering on the sides.
        List<Piece> cores = new ArrayList<>(pieces);
        for (Piece core : cores) {
            for (Direction dir : Direction.values()) {
                int nx = core.x() + dir.getOffsetX();
                int ny = core.y() + dir.getOffsetY();
                int nz = core.z() + dir.getOffsetZ();
                if (filled.contains(key(nx, ny, nz))) {
                    continue;
                }
                boolean vertical = dir.getAxis() == Direction.Axis.Y;
                int chance = vertical ? 1 : 3;
                if (random.nextInt(chance) != 0) {
                    continue;
                }
                BlockState crystal = pickCrystal(random, vertical).with(Properties.FACING, dir);
                pieces.add(new Piece(nx, ny, nz, crystal));
                filled.add(key(nx, ny, nz));
            }
        }
        return pieces;
    }

    private static BlockState pickCrystal(Random random, boolean vertical) {
        int roll = random.nextInt(vertical ? 3 : 4);
        if (roll == 0) {
            return Blocks.AMETHYST_CLUSTER.getDefaultState();
        } else if (roll == 1) {
            return Blocks.LARGE_AMETHYST_BUD.getDefaultState();
        } else if (roll == 2) {
            return vertical ? Blocks.AMETHYST_CLUSTER.getDefaultState() : Blocks.MEDIUM_AMETHYST_BUD.getDefaultState();
        }
        return Blocks.SMALL_AMETHYST_BUD.getDefaultState();
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (z + 512);
    }

    public void render(MatrixStack matrices, Matrix4f projection, Vec3d camera, float time, float day) {
        MinecraftClient client = MinecraftClient.getInstance();
        BlockRenderManager blockRenderer = client.getBlockRenderManager();
        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        // Sky light only; the lightmap darkens it at night for free.
        int light = LightmapTextureManager.pack(0, 15);

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);

        for (Shard s : shards) {
            double a = s.angle + s.speed * time;
            double wx = CENTER_X + Math.cos(a) * s.radius;
            double wz = CENTER_Z + Math.sin(a) * s.radius;
            double wy = s.height + Math.sin(time * s.bobSpeed + s.angle) * s.bob;

            float rx = (float) (wx - camera.x);
            float ry = (float) (wy - camera.y);
            float rz = (float) (wz - camera.z);
            if (rx * rx + ry * ry + rz * rz > 260f * 260f) {
                continue;
            }

            matrices.push();
            matrices.translate(rx, ry, rz);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * s.yawSpeed + (float) a * 57.3f));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(s.tilt));
            matrices.scale(s.scale, s.scale, s.scale);
            // Centre the cluster roughly on its origin.
            matrices.translate(-0.5f, -1.0f, -0.5f);
            for (Piece p : s.pieces) {
                matrices.push();
                matrices.translate(p.x(), p.y(), p.z());
                BakedModel model = blockRenderer.getModel(p.state());
                RenderLayer layer = RenderLayers.getEntityBlockLayer(p.state(), false);
                blockRenderer.getModelRenderer().render(matrices.peek(), consumers.getBuffer(layer), p.state(), model,
                        s.tint[0], s.tint[1], s.tint[2], light, OverlayTexture.DEFAULT_UV);
                matrices.pop();
            }
            matrices.pop();
        }
        consumers.draw();

        // Restore the state the rest of the sky pass expects.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
