package net.bennyboops.modid.client;

import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/**
 * This player's own settings (the Client tab of the config screen), saved in config/pocket-repose-client.toml.
 * Right now that's whether pocket blocks draw the hallway shader.
 */
public final class HallwaySettings {
    private static final Path CONFIG = MinecraftClient.getInstance().runDirectory.toPath().resolve("config");
    private static final Path FILE = CONFIG.resolve("pocket-repose-client.toml");
    // Where 1.5.0 and 1.5.1 kept it
    private static final Path OLD_FILE = CONFIG.resolve("pocket-repose-client.properties");
    public static final String DESCRIPTION = "Open pocket blocks look like a hallway leading into darkness, instead of a flat black opening"
            + " (always flat black while a shader pack is on)";

    private static boolean enabled = true;
    private static boolean loaded;

    private HallwaySettings() {
    }

    public static boolean enabled() {
        if (!loaded) load();
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        loaded = true;
        save();
    }

    private static void load() {
        loaded = true;
        try {
            if (Files.exists(FILE)) {
                for (String line : Files.readAllLines(FILE, StandardCharsets.UTF_8)) {
                    String text = line.trim();
                    if (text.startsWith("hallwayShader") && text.contains("=")) {
                        String value = text.substring(text.indexOf('=') + 1).trim();
                        int hash = value.indexOf('#');
                        if (hash >= 0) value = value.substring(0, hash).trim();
                        enabled = !value.equalsIgnoreCase("false");
                    }
                }
            } else if (Files.exists(OLD_FILE)) {
                Properties props = new Properties();
                try (Reader reader = Files.newBufferedReader(OLD_FILE)) {
                    props.load(reader);
                }
                enabled = Boolean.parseBoolean(props.getProperty("hallwayShader", "true"));
                save();
                Files.deleteIfExists(OLD_FILE);
            } else {
                save();
            }
        } catch (IOException ignored) {
            // Keep the default
        }
    }

    private static void save() {
        List<String> lines = List.of(
                "# Pocket Repose settings for this player. They also apply on servers.",
                "",
                "[client]",
                "# " + DESCRIPTION,
                "hallwayShader = " + enabled);
        try {
            Files.createDirectories(CONFIG);
            Files.write(FILE, lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Still applies for this session
        }
    }
}
