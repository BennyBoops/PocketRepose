package net.bennyboops.modid.client;

import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** This player's own choice of whether pocket blocks draw the hallway shader, saved in config/pocket-repose-client.properties. */
public final class HallwaySettings {
    private static final Path FILE = MinecraftClient.getInstance().runDirectory.toPath().resolve("config").resolve("pocket-repose-client.properties");
    private static boolean enabled = true;
    private static boolean loaded;

    private HallwaySettings() {
    }

    public static boolean enabled() {
        if (!loaded) {
            loaded = true;
            if (Files.exists(FILE)) {
                Properties props = new Properties();
                try (Reader reader = Files.newBufferedReader(FILE)) {
                    props.load(reader);
                    enabled = Boolean.parseBoolean(props.getProperty("hallwayShader", "true"));
                } catch (IOException ignored) {
                    // Keep the default
                }
            }
        }
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        loaded = true;
        Properties props = new Properties();
        props.setProperty("hallwayShader", Boolean.toString(value));
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE)) {
                props.store(writer, "Pocket Repose client settings");
            }
        } catch (IOException ignored) {
            // Still applies for this session
        }
    }
}
