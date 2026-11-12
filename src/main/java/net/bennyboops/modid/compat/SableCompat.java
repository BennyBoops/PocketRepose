package net.bennyboops.modid.compat;

import net.bennyboops.modid.PocketRepose;
import net.minecraft.util.math.Position;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.lang.reflect.Method;

/**
 * Support for Sable physics objects, without needing Sable installed.
 *
 * Sable keeps the blocks of a physics object in a far-off area of the world (millions of blocks out) and draws
 * them wherever the object really is. A suitcase placed on one is tracked at that far-off spot, so when someone
 * leaves its pocket dimension, the spot is turned into where the object (and the suitcase on it) is right now.
 */
public final class SableCompat {
    private static boolean checked;
    private static Object companion;
    private static Method projectOut, getContaining, logicalPose, transformNormal;

    private SableCompat() {
    }

    /** Whether Sable is installed (and its API could be found). Also logs it, so it shows up in server logs. */
    public static boolean available() {
        if (!checked) {
            checked = true;
            try {
                Class<?> api = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
                Object instance = api.getField("INSTANCE").get(null);
                Method project = api.getMethod("projectOutOfSubLevel", World.class, Vec3d.class);
                Method containing = api.getMethod("getContaining", World.class, Position.class);
                Method pose = Class.forName("dev.ryanhcode.sable.companion.SubLevelAccess").getMethod("logicalPose");
                Method normal = Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc").getMethod("transformNormal", Vec3d.class);
                companion = instance;
                projectOut = project;
                getContaining = containing;
                logicalPose = pose;
                transformNormal = normal;
                PocketRepose.LOGGER.info("Sable found: pocket blocks on physics objects are supported");
            } catch (ClassNotFoundException e) {
                // Sable isn't installed
            } catch (Throwable e) {
                PocketRepose.LOGGER.warn("Sable found, but its API wasn't what was expected; exits from suitcases on physics objects may be off", e);
            }
        }
        return companion != null;
    }

    /** Where a position on a physics object is in the world right now. Positions not on one are returned as they are. */
    public static Vec3d toWorld(World world, Vec3d pos) {
        if (!available()) return pos;
        try {
            Object result = projectOut.invoke(companion, world, pos);
            return result instanceof Vec3d projected ? projected : pos;
        } catch (Throwable e) {
            return pos;
        }
    }

    /** A facing on a physics object, turned the way the object is turned right now. */
    public static float toWorldYaw(World world, Vec3d pos, float yaw) {
        if (!available()) return yaw;
        try {
            Object subLevel = getContaining.invoke(companion, world, pos);
            if (subLevel == null) return yaw;
            Object pose = logicalPose.invoke(subLevel);
            Object turned = transformNormal.invoke(pose, Vec3d.fromPolar(0, yaw));
            if (!(turned instanceof Vec3d direction) || direction.horizontalLengthSquared() < 1.0E-6) return yaw;
            return (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        } catch (Throwable e) {
            return yaw;
        }
    }
}
