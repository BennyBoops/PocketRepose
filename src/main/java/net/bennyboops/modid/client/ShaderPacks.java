package net.bennyboops.modid.client;

import java.lang.reflect.Method;

/**
 * Whether a shader pack is in use (through Iris's public API, looked up by name so Iris isn't needed to run).
 * Shader packs replace Minecraft's own shaders, so the pocket door hallway shader can't be used alongside one.
 */
public final class ShaderPacks {
    private static boolean looked;
    private static Object iris;
    private static Method isShaderPackInUse;

    private ShaderPacks() {
    }

    public static boolean inUse() {
        if (!looked) {
            looked = true;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                iris = api.getMethod("getInstance").invoke(null);
                isShaderPackInUse = api.getMethod("isShaderPackInUse");
            } catch (Throwable ignored) {
                // Iris isn't installed
            }
        }
        if (isShaderPackInUse == null) return false;
        try {
            return (boolean) isShaderPackInUse.invoke(iris);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
