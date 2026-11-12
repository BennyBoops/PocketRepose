package net.bennyboops.modid.config;

import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.Nullable;

/** On the client: the settings of the world the player is in, as last sent by its server. */
public final class ConfigState {
    @Nullable private static NbtCompound serverSettings;
    // Goes up with every update, so an open config screen knows to show the new values
    private static int revision;

    private ConfigState() {
    }

    public static void receive(NbtCompound settings) {
        serverSettings = settings;
        revision++;
    }

    public static void clear() {
        serverSettings = null;
        revision++;
    }

    @Nullable
    public static NbtCompound serverSettings() {
        return serverSettings == null ? null : serverSettings.copy();
    }

    public static int revision() {
        return revision;
    }
}
