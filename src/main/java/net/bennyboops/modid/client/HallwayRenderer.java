package net.bennyboops.modid.client;

import net.bennyboops.modid.PocketRepose;
import net.bennyboops.modid.block.CustomSuitcaseBlock;
import net.bennyboops.modid.block.DoorwayBlock;
import net.bennyboops.modid.block.ExitDoorBlock;
import net.bennyboops.modid.block.PocketPortalBlock;
import net.bennyboops.modid.block.SecretChestBlock;
import net.bennyboops.modid.block.SuitcaseBlock;
import net.bennyboops.modid.block.entity.SuitcaseBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the opening of an open pocket block as a dark hallway (the doorway shader), fading to black within about a
 * block:
 * <ul>
 *     <li>a pocket or exit portal door's doorway: walls and ceiling of the door's wood (as planks), and a floor
 *     that carries on the top of the block the door stands on;</li>
 *     <li>the top of a suitcase or the inside of the secret chest: a shaft down, lined with the suitcase's wool
 *     or the chest's lid lining.</li>
 * </ul>
 * With the hallway turned off (/pocketReposeClient hallwayShader), a shader pack on, or the shader not loaded, the
 * opening is plain black instead.
 */
public final class HallwayRenderer {
    private static ShaderProgram shader;
    private static final Map<String, RenderLayer> LAYERS = new HashMap<>();
    private static final RenderLayer BLACK = RenderLayer.getEntitySolid(new Identifier(PocketRepose.MOD_ID, "textures/block/pocket_door_void.png"));
    // The secret chest is drawn with the vanilla chest texture; its shaft is lined with the inside of its lid
    private static final Identifier CHEST = new Identifier("minecraft", "textures/entity/chest/normal.png");
    private static final Random RANDOM = Random.create();

    // Flags carried to the shader in the vertex color's red channel
    private static final int COLUMNS_ALONG_TANGENT = 1;
    private static final int SHAFT = 2;
    private static final int CHEST_LINING = 4;

    private HallwayRenderer() {
    }

    public static void setShader(ShaderProgram program) {
        shader = program;
    }

    static ShaderProgram shader() {
        return shader;
    }

    /** Whether this block's opening reaches outside its own block (a door's doorway is two blocks tall). */
    public static boolean tall(BlockState state) {
        return state.getBlock() instanceof DoorwayBlock door && door.hasDoorframe();
    }

    public static void render(BlockEntity suitcase, float tickDelta, MatrixStack matrices,
                              VertexConsumerProvider consumers, int light) {
        BlockState state = suitcase.getCachedState();
        Block block = state.getBlock();
        if (block instanceof DoorwayBlock door) {
            // An exit portal door only leads anywhere inside a pocket dimension; elsewhere its doorway is left open
            boolean leadsSomewhere = !(block instanceof ExitDoorBlock)
                    || (suitcase.getWorld() != null && PocketPortalBlock.isPocketDimension(suitcase.getWorld()));
            if (door.hasDoorframe() && state.get(DoorBlock.OPEN) && leadsSomewhere) {
                doorway(suitcase, state, matrices, consumers, light);
            }
        } else if (block instanceof SecretChestBlock) {
            // Just above the chest's floor, over the black middle of it (inside its brown border); shown while the
            // lid is up
            if (suitcase instanceof SuitcaseBlockEntity chest && chest.getLidProgress(tickDelta) > 0) {
                shaft(state, 3, 13, 3, 13, 10.1f, CHEST, CHEST_LINING, matrices, consumers, light);
            }
        } else if (block instanceof CustomSuitcaseBlock) {
            // The secret barrel keeps its painted hole
        } else if (block instanceof SuitcaseBlock) {
            // The floor of the open suitcase's tray, inside its rim (the models face north)
            if (state.get(SuitcaseBlock.OPEN)) {
                Identifier wool = new Identifier("minecraft", "textures/block/" + woolColor(block) + "_wool.png");
                int turns = turns(state.get(SuitcaseBlock.FACING));
                float[] lo = turn(1, 3, turns), hi = turn(15, 13, turns);
                shaft(state, Math.min(lo[0], hi[0]) * 16, Math.max(lo[0], hi[0]) * 16, Math.min(lo[1], hi[1]) * 16,
                        Math.max(lo[1], hi[1]) * 16, 1, wool, 0, matrices, consumers, light);
            }
        }
    }

    /** The wool a suitcase is made of: "suitcase" is the brown one, the others are "<color>_suitcase". */
    private static String woolColor(Block suitcase) {
        String path = Registries.BLOCK.getId(suitcase).getPath();
        return path.equals("suitcase") ? "brown" : path.substring(0, path.length() - "_suitcase".length());
    }

    /** How many clockwise quarter turns the blockstate gives a model facing north (suitcases). */
    private static int turns(Direction facing) {
        return switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }

    /** A horizontal opening at height y (pixels) over x0..x1, z0..z1 (pixels), with a shaft below it. */
    private static void shaft(BlockState state, float x0, float x1, float z0, float z1, float y, Identifier walls,
                              int extraFlags, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        MatrixStack.Entry entry = matrices.peek();
        float[][] corners = {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}};
        float[] n = {0, 1, 0};
        if (!hallwayOn()) {
            black(consumers, entry, corners, y, y, n, light, true);
            return;
        }
        // u runs along x from x0..x1 and v along z from 0, the axes the shader uses for a top opening
        VertexConsumer hall = consumers.getBuffer(layer(walls, walls, walls, walls));
        int flags = COLUMNS_ALONG_TANGENT | SHAFT | extraFlags;
        for (float[] c : corners) {
            hall.vertex(entry.getPositionMatrix(), c[0] / 16f, y / 16f, c[1] / 16f).color(flags, (int) x0, (int) x1, (int) (z1 - z0))
                    .texture(c[0], c[1] - z0).overlay(0xFFFF, 0xFF).light(light).normal(entry.getNormalMatrix(), n[0], n[1], n[2]).next();
        }
    }

    private static void doorway(BlockEntity suitcase, BlockState state, MatrixStack matrices,
                                VertexConsumerProvider consumers, int light) {
        // Built in the frame models' space (the door facing east, closed slab at x 0..3, hinge on the north side
        // for a left hinge), in pixels, then turned to the door's facing like the blockstate turns the models.
        // The doorway runs from beside the hinge to the frame, or to the door's edge when another door is beside it.
        boolean left = state.get(DoorBlock.HINGE) == DoorHinge.LEFT;
        boolean paired = state.get(DoorwayBlock.PAIRED);
        float end = paired ? 16 : 15;
        float z0 = left ? 3 : 16 - end, z1 = left ? end : 13;
        // The door texture's column at each end (the hinge is always at the texture's left, the frame at column 15)
        float u0 = left ? z0 : 16 - z0, u1 = left ? z1 : 16 - z1;

        int turns = switch (state.get(DoorBlock.FACING)) {
            case SOUTH -> 1;
            case WEST -> 2;
            case NORTH -> 3;
            default -> 0;
        };
        float[] a = turn(1, z0, turns), b = turn(1, z1, turns);
        float[] nxz = turnDirection(-1, 0, turns);
        float[] n = {nxz[0], 0, nxz[1]};

        // Which way the columns run along the tangent the shader uses, (n.z, 0, -n.x)
        float along = (b[0] - a[0]) * n[2] - (b[1] - a[1]) * n[0];
        boolean columnsAlongTangent = (along > 0) == (u1 > u0);
        int uMin = 3, uMax = paired ? 29 : 15, vMax = 31;
        float top = 31;

        MatrixStack.Entry entry = matrices.peek();
        if (!hallwayOn()) {
            float[][] corners = {{a[0] * 16, a[1] * 16}, {b[0] * 16, b[1] * 16}, {b[0] * 16, b[1] * 16}, {a[0] * 16, a[1] * 16}};
            black(consumers, entry, corners, 0, top, n, light, false);
            return;
        }

        // The floor: the top of the block the door stands on, tinted like it is (grass, for example)
        Ground ground = ground(suitcase);
        Vec3d camera = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        GlUniform cameraFraction = shader.getUniform("CameraFraction");
        if (cameraFraction != null) {
            cameraFraction.set((float) (camera.x - Math.floor(camera.x)), (float) (camera.y - Math.floor(camera.y)),
                    (float) (camera.z - Math.floor(camera.z)));
        }

        String wood = Registries.BLOCK.getId(state.getBlock()).getPath().replace("_pocket_door", "").replace("_exit_portal_door", "");
        Identifier planks = new Identifier("minecraft", "textures/block/" + wood + "_planks.png");
        // The door's own textures, for the opened door lying along the hinge-side wall
        Identifier doorBottom = new Identifier("minecraft", "textures/block/" + wood + "_door_bottom.png");
        Identifier doorTop = new Identifier("minecraft", "textures/block/" + wood + "_door_top.png");
        VertexConsumer hall = consumers.getBuffer(layer(planks, ground.texture() != null ? ground.texture() : planks, doorBottom, doorTop));
        int flags = columnsAlongTangent ? COLUMNS_ALONG_TANGENT : 0;
        int tintU = ((ground.tint() >> 16) & 255) | (((ground.tint() >> 8) & 255) << 8), tintV = ground.tint() & 255;
        float[][] corners = {{a[0], 0, a[1], u0, 0}, {b[0], 0, b[1], u1, 0}, {b[0], top, b[1], u1, 31}, {a[0], top, a[1], u0, 31}};
        for (float[] c : corners) {
            // The bounds of the opening ride along in the vertex color, the floor's tint in the overlay slot
            hall.vertex(entry.getPositionMatrix(), c[0], c[1] / 16f, c[2]).color(flags, uMin, uMax, vMax).texture(c[3], c[4])
                    .overlay(tintU, tintV).light(light).normal(entry.getNormalMatrix(), n[0], n[1], n[2]).next();
        }
    }

    private static boolean hallwayOn() {
        return shader != null && HallwaySettings.enabled() && !ShaderPacks.inUse();
    }

    /**
     * The opening as a plain black quad, seen from both sides. corners are (x, z) in pixels; flat openings sit at
     * height y0, upright ones run from y0 to y1 (pixels) between corners 0 and 1.
     */
    private static void black(VertexConsumerProvider consumers, MatrixStack.Entry entry, float[][] corners, float y0,
                              float y1, float[] n, int light, boolean flat) {
        VertexConsumer black = consumers.getBuffer(BLACK);
        float[][] quad = flat
                ? new float[][]{{corners[0][0], y0, corners[0][1]}, {corners[1][0], y0, corners[1][1]}, {corners[2][0], y0, corners[2][1]}, {corners[3][0], y0, corners[3][1]}}
                : new float[][]{{corners[0][0], y0, corners[0][1]}, {corners[1][0], y0, corners[1][1]}, {corners[1][0], y1, corners[1][1]}, {corners[0][0], y1, corners[0][1]}};
        for (int side = 0; side < 2; side++) {
            for (int i = 0; i < 4; i++) {
                float[] c = quad[side == 0 ? i : 3 - i];
                float sign = side == 0 ? 1 : -1;
                black.vertex(entry.getPositionMatrix(), c[0] / 16f, c[1] / 16f, c[2] / 16f).color(0, 0, 0, 255).texture(0, 0)
                        .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry.getNormalMatrix(), n[0] * sign, n[1] * sign, n[2] * sign).next();
            }
        }
    }

    private record Ground(Identifier texture, int tint) {
    }

    /** The top face of the block the door stands on: its texture and tint (null texture if it has no top face). */
    private static Ground ground(BlockEntity suitcase) {
        World world = suitcase.getWorld();
        BlockPos below = suitcase.getPos().down();
        if (world != null) {
            BlockState state = world.getBlockState(below);
            MinecraftClient client = MinecraftClient.getInstance();
            BakedModel model = client.getBlockRenderManager().getModel(state);
            RANDOM.setSeed(42L);
            List<BakedQuad> quads = model.getQuads(state, Direction.UP, RANDOM);
            if (quads.isEmpty()) {
                RANDOM.setSeed(42L);
                quads = model.getQuads(state, null, RANDOM).stream().filter(q -> q.getFace() == Direction.UP).toList();
            }
            if (!quads.isEmpty()) {
                BakedQuad quad = quads.get(0);
                Identifier sprite = quad.getSprite().getContents().getId();
                int tint = quad.hasColor() ? client.getBlockColors().getColor(state, world, below, quad.getColorIndex()) : 0xFFFFFF;
                return new Ground(new Identifier(sprite.getNamespace(), "textures/" + sprite.getPath() + ".png"), tint);
            }
        }
        return new Ground(null, 0xFFFFFF);
    }

    /** A point (in pixels, model space) turned clockwise by 90 degrees per turn, in blocks. */
    private static float[] turn(float x, float z, int turns) {
        for (int i = 0; i < turns; i++) {
            float nx = 16 - z;
            z = x;
            x = nx;
        }
        return new float[]{x / 16f, z / 16f};
    }

    private static float[] turnDirection(float x, float z, int turns) {
        for (int i = 0; i < turns; i++) {
            float nx = -z;
            z = x;
            x = nx;
        }
        return new float[]{x, z};
    }

    private static RenderLayer layer(Identifier walls, Identifier floor, Identifier doorBottom, Identifier doorTop) {
        return LAYERS.computeIfAbsent(walls + "|" + floor + "|" + doorBottom + "|" + doorTop,
                key -> Layers.hallway(walls, floor, doorBottom, doorTop));
    }

    // RenderLayer's building blocks are only reachable from a subclass
    private static final class Layers extends RenderLayer {
        private Layers() {
            super("pocket_repose_unused", VertexFormats.POSITION, VertexFormat.DrawMode.QUADS, 0, false, false, () -> {}, () -> {});
        }

        static RenderLayer hallway(Identifier walls, Identifier floor, Identifier doorBottom, Identifier doorTop) {
            return RenderLayer.of("pocket_repose_hallway", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
                    VertexFormat.DrawMode.QUADS, 256,
                    RenderLayer.MultiPhaseParameters.builder()
                            .program(new RenderPhase.ShaderProgram(HallwayRenderer::shader))
                            // Samplers 0, 1, 3 and 4; the lightmap takes 2, so that slot only holds a stand-in
                            .texture(RenderPhase.Textures.create().add(walls, false, false).add(floor, false, false)
                                    .add(walls, false, false).add(doorBottom, false, false).add(doorTop, false, false).build())
                            .lightmap(ENABLE_LIGHTMAP)
                            .cull(DISABLE_CULLING)
                            .build(false));
        }
    }
}
