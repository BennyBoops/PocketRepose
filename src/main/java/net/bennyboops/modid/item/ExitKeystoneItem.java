package net.bennyboops.modid.item;

import net.bennyboops.modid.PocketRepose;
import net.bennyboops.modid.block.PocketPortalBlock;
import net.bennyboops.modid.block.PocketTravelHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;

import java.util.List;

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
            serverPlayer.teleportTo(serverPlayer.getRespawnTarget(true, TeleportTarget.NO_OP));
            serverPlayer.fallDistance = 0f;
            PocketTravelHandler.pearlEffects(serverPlayer);
            // Played where they arrive, so they always hear it
            serverPlayer.getServerWorld().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                    SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1F, 0F);
            stack.damage(1, player, LivingEntity.getSlotForHand(hand));
        }

        int cooldownTicks = Math.round(PocketRepose.getExitKeystoneCooldown() * 20);
        if (cooldownTicks > 0) {
            player.getItemCooldownManager().set(this, cooldownTicks);
        }
        return TypedActionResult.success(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("A portable exit from dimensions.").formatted(Formatting.GRAY, Formatting.ITALIC));
    }
}
