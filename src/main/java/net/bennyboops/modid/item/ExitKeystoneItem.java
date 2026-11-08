package net.bennyboops.modid.item;

import net.bennyboops.modid.PocketRepose;
import net.bennyboops.modid.block.PocketPortalBlock;
import net.bennyboops.modid.block.PocketTravelHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;
import java.util.Optional;

/**
 * A handheld portal block: used anywhere in a pocket dimension, it takes you out. With the exitKeystone
 * worksInOverworld / worksInNether / worksInEnd settings on, using it there takes you to your spawn point.
 * It gives no messages, only an amethyst sound and ender pearl style teleport effects.
 */
public class ExitKeystoneItem extends Item {
    /** How many times it can take a player back to spawn; leaving a pocket dimension is free. */
    public static final int USES = 50;
    public ExitKeystoneItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        boolean inPocket = PocketPortalBlock.isPocketDimension(world);

        if (world.isClient) {
            return inPocket ? TypedActionResult.success(stack) : TypedActionResult.pass(stack);
        }
        boolean worksHere = inPocket
                || (world.getRegistryKey() == World.OVERWORLD && PocketRepose.getExitKeystoneInOverworld())
                || (world.getRegistryKey() == World.NETHER && PocketRepose.getExitKeystoneInNether())
                || (world.getRegistryKey() == World.END && PocketRepose.getExitKeystoneInEnd());
        if (!worksHere) {
            return TypedActionResult.pass(stack);
        }

        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
        // Same sound as binding a key
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_AMETHYST_CLUSTER_FALL,
                SoundCategory.PLAYERS, 2F, 2F);
        PocketTravelHandler.pearlParticles(serverPlayer);

        if (inPocket) {
            PocketTravelHandler.requestExit(serverPlayer, true);
        } else {
            // Like respawning, but alive: a respawn anchor's charge isn't used up
            teleportToRespawn(serverPlayer);
            serverPlayer.fallDistance = 0f;
            PocketTravelHandler.pearlEffects(serverPlayer);
            // Played where they arrive, so they always hear it
            serverPlayer.getServerWorld().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                    SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1F, 0F);
            stack.damage(1, player, p -> p.sendToolBreakStatus(hand));
        }

        int cooldownTicks = Math.round(PocketRepose.getExitKeystoneCooldown() * 20);
        if (cooldownTicks > 0) {
            player.getItemCooldownManager().set(this, cooldownTicks);
        }
        return TypedActionResult.success(stack);
    }

    /**
     * Sends a player to where they'd respawn (their bed or respawn anchor, facing it, or the world spawn),
     * without dying: a respawn anchor's charge isn't used up.
     */
    private static void teleportToRespawn(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        BlockPos spawn = player.getSpawnPointPosition();
        ServerWorld world = server.getWorld(player.getSpawnPointDimension());
        if (spawn != null && world != null) {
            Optional<Vec3d> found = PlayerEntity.findRespawnPosition(world, spawn, player.getSpawnAngle(), player.isSpawnForced(), true);
            if (found.isPresent()) {
                Vec3d pos = found.get();
                BlockState block = world.getBlockState(spawn);
                float yaw = player.getSpawnAngle();
                if (block.isIn(BlockTags.BEDS) || block.isOf(Blocks.RESPAWN_ANCHOR)) {
                    Vec3d toBlock = Vec3d.ofBottomCenter(spawn).subtract(pos).normalize();
                    yaw = (float) MathHelper.wrapDegrees(MathHelper.atan2(toBlock.z, toBlock.x) * 57.2957763671875 - 90.0);
                }
                player.teleport(world, pos.x, pos.y, pos.z, yaw, player.getPitch());
                return;
            }
            // The bed or anchor is gone
            player.networkHandler.sendPacket(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.NO_RESPAWN_BLOCK, 0.0F));
        }
        ServerWorld overworld = server.getOverworld();
        BlockPos top = overworld.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, overworld.getSpawnPos());
        player.teleport(overworld, top.getX() + 0.5, top.getY(), top.getZ() + 0.5, overworld.getSpawnAngle(), player.getPitch());
    }

    // Enchanted look, like the enchanted golden apple
    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("A portable exit from dimensions.").formatted(Formatting.GRAY, Formatting.ITALIC));
    }
}
