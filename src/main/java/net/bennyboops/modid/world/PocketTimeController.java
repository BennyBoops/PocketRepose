package net.bennyboops.modid.world;

import net.bennyboops.modid.PocketRepose;
import net.bennyboops.modid.data.PocketTimeData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * Gives each pocket dimension its own day/night cycle, controlled by right-clicking a clock
 * hanging in an item frame inside that dimension: normal speed, 10x speed, stopped.
 */
public final class PocketTimeController {
    private static final String DIMENSION_PREFIX = "pocket_dimension_";

    private PocketTimeController() {
    }

    public static void register() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!(entity instanceof ItemFrameEntity frame) || !isPocketDimension(world)) {
                return ActionResult.PASS;
            }
            if (!frame.getHeldItemStack().isOf(Items.CLOCK)) {
                return ActionResult.PASS;
            }
            // Cancel on both sides so vanilla doesn't rotate the clock in the frame.
            if (world.isClient) {
                return ActionResult.SUCCESS;
            }
            if (!(world instanceof ServerWorld serverWorld) || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.SUCCESS;
            }
            int speed = PocketTimeData.get(serverWorld).cycleSpeed();
            String label;
            float pitch;
            switch (speed) {
                case PocketTimeData.SPEED_FAST -> {
                    label = "§d⌛ Time flows quickly";
                    pitch = 1.4f;
                }
                case PocketTimeData.SPEED_STOPPED -> {
                    label = "§d⌛ Time stands still";
                    pitch = 0.6f;
                }
                default -> {
                    label = "§d⌛ Time flows normally";
                    pitch = 1.0f;
                }
            }
            serverPlayer.sendMessage(Text.literal(label), true);
            serverWorld.playSound(null, frame.getX(), frame.getY(), frame.getZ(),
                    SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 1.0f, pitch);
            return ActionResult.SUCCESS;
        });

        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (!isPocketDimension(world)) {
                return;
            }
            PocketTimeData data = PocketTimeData.get(world);
            data.tick();
            world.setTimeOfDay(data.getTimeOfDay());
            if (world.getPlayers().isEmpty()) {
                return;
            }
            // Send the exact time every tick with the daylight-cycle flag off, so clients never
            // run their own clock and the sky moves smoothly at any speed, including stopped.
            WorldTimeUpdateS2CPacket packet =
                    new WorldTimeUpdateS2CPacket(world.getTime(), data.getTimeOfDay(), false);
            for (ServerPlayerEntity player : world.getPlayers()) {
                player.networkHandler.sendPacket(packet);
            }
        });
    }

    private static boolean isPocketDimension(World world) {
        Identifier id = world.getRegistryKey().getValue();
        return id.getNamespace().equals(PocketRepose.MOD_ID) && id.getPath().startsWith(DIMENSION_PREFIX);
    }
}
