package net.bennyboops.modid.world;

import net.bennyboops.modid.PocketRepose;
import net.bennyboops.modid.data.MobEntryData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Anything that falls off a pocket island is returned to that dimension's mob entry point.
 *
 * The island floats above a walk-through portal floor, so falling entities used to drop into
 * the true void: players hit the exit logic with its many fallbacks, mobs simply died. This
 * makes the outcome the same for every entity: below the catch height, you land at the entry.
 */
public final class PocketVoidCatcher {
    /** Well below the island (which bottoms out around y 64) but above the portal floor at y -64. */
    public static final double CATCH_Y = -40.0;
    private static final String DIMENSION_PREFIX = "pocket_dimension_";

    private PocketVoidCatcher() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(PocketVoidCatcher::sweep);
    }

    public static boolean isPocketDimension(World world) {
        Identifier id = world.getRegistryKey().getValue();
        return id.getNamespace().equals(PocketRepose.MOD_ID) && id.getPath().startsWith(DIMENSION_PREFIX);
    }

    private static void sweep(ServerWorld world) {
        if (!isPocketDimension(world)) {
            return;
        }
        // Collect first: teleporting while iterating the entity index is unsafe.
        List<Entity> fallen = new ArrayList<>();
        for (Entity entity : world.iterateEntities()) {
            if (!entity.isRemoved() && entity.getY() < CATCH_Y) {
                fallen.add(entity);
            }
        }
        if (fallen.isEmpty()) {
            return;
        }
        MobEntryData entry = MobEntryData.get(world);
        if (entry.getEntryPos().y <= CATCH_Y + 10) {
            // An entry point set below the catch height would bounce entities forever; leave them be.
            return;
        }
        for (Entity entity : fallen) {
            returnToEntry(world, entity, entry);
        }
    }

    public static void returnToEntry(ServerWorld world, Entity entity, MobEntryData entry) {
        Vec3d dest = entry.getEntryPos();
        float yaw = entry.getEntryYaw();
        float pitch = entry.getEntryPitch();

        entity.stopRiding();
        entity.removeAllPassengers();
        entity.fallDistance = 0f;
        entity.setVelocity(Vec3d.ZERO);
        entity.velocityModified = true;

        if (entity instanceof ServerPlayerEntity player) {
            player.teleport(world, dest.x, dest.y, dest.z, yaw, pitch);
            player.fallDistance = 0f;
        } else {
            entity.refreshPositionAndAngles(dest.x, dest.y, dest.z, yaw, pitch);
            entity.teleport(dest.x, dest.y, dest.z);
            entity.setYaw(yaw);
            entity.setHeadYaw(yaw);
            entity.setPitch(pitch);
        }
        world.playSound(null, dest.x, dest.y, dest.z,
                SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.NEUTRAL, 0.6f, 1.2f);
    }
}
