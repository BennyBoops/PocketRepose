package net.bennyboops.modid.config;

import net.bennyboops.modid.PocketRepose;
import net.bennyboops.modid.data.PocketReposeSettingsData;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;

import static net.bennyboops.modid.config.ConfigOption.Category.ITEMS;
import static net.bennyboops.modid.config.ConfigOption.Category.POCKET_DIMENSION;

/**
 * The server settings (the /pocketRepose options), kept in a config file in each world:
 * {@code <world>/serverconfig/pocket-repose-server.toml}. A new world's file starts from
 * {@code config/pocket-repose-defaults.toml}. Commands, the config screen and edits to the file all change the same
 * settings, and every change is sent to the players in the world so their config screens stay up to date.
 */
public final class PocketReposeConfig {
    public static final String WORLD_FILE = "pocket-repose-server.toml";
    public static final String DEFAULTS_FILE = "pocket-repose-defaults.toml";
    /** Changing settings in game needs the same permission as the /pocketRepose commands. */
    public static final int PERMISSION_LEVEL = 2;
    // How often the world's file is checked for edits made while the server runs (2 seconds)
    private static final int FILE_CHECK_TICKS = 40;

    /** Where the defaults file goes. Set by each loader as the mod starts. */
    public static Path configDir = Path.of("config");

    public static final List<ConfigOption> OPTIONS = List.of(
            ConfigOption.bool(POCKET_DIMENSION, "allowHostileMobSpawning", "Hostile mobs spawn naturally inside pocket dimensions",
                    false, PocketRepose::getHostileMobSpawning, PocketRepose::setHostileMobSpawning),
            ConfigOption.bool(POCKET_DIMENSION, "allowPassiveMobSpawning", "Passive mobs spawn naturally inside pocket dimensions",
                    true, PocketRepose::getPassiveMobSpawning, PocketRepose::setPassiveMobSpawning),
            ConfigOption.bool(POCKET_DIMENSION, "allowSetSpawnPoint", "Players can set their spawn point with a bed inside pocket dimensions",
                    false, PocketRepose::getAllowSpawnPoint, PocketRepose::setAllowSpawnPoint),
            ConfigOption.bool(POCKET_DIMENSION, "spawnIsland", "New pocket dimensions start with an island (otherwise a single grass block)",
                    true, PocketRepose::getSpawnIsland, PocketRepose::setSpawnIsland),
            ConfigOption.number(POCKET_DIMENSION, "knockVolume", "How loud a knock on a locked suitcase is inside its pocket dimension (0 to 10)",
                    0.5F, 0, 10, PocketRepose::getKnockVolume, PocketRepose::setKnockVolume),

            ConfigOption.bool(ITEMS, "closeOnEntry", "Unlocked pocket blocks close behind players who enter them",
                    false, PocketRepose::getCloseOnEntry, PocketRepose::setCloseOnEntry),
            ConfigOption.bool(ITEMS, "closeOnLockedEntry", "Locked pocket blocks close behind players who enter them",
                    true, PocketRepose::getCloseOnLockedEntry, PocketRepose::setCloseOnLockedEntry),
            ConfigOption.bool(ITEMS, "allowRecursion", "Pocket blocks can be entered from inside pocket dimensions",
                    true, PocketRepose::getAllowRecursion, PocketRepose::setAllowRecursion),
            ConfigOption.bool(ITEMS, "allowInNether", "Pocket blocks can be entered in the Nether",
                    true, PocketRepose::getAllowInNether, PocketRepose::setAllowInNether),
            ConfigOption.bool(ITEMS, "allowInEnd", "Pocket blocks can be entered in the End",
                    true, PocketRepose::getAllowInEnd, PocketRepose::setAllowInEnd),
            ConfigOption.bool(ITEMS, "allowHostileMobCapture", "Hostile mobs can be captured into pocket dimensions",
                    false, PocketRepose::getCanCaptureHostile, PocketRepose::setCanCaptureHostile),
            ConfigOption.bool(ITEMS, "allowPassiveMobCapture", "Passive mobs can be captured into pocket dimensions",
                    true, PocketRepose::getCanCapturePassive, PocketRepose::setCanCapturePassive),
            ConfigOption.idList(ITEMS, "captureBlacklist", "Mobs that can never be captured, as entity ids, e.g. [\"minecraft:villager\"]",
                    PocketReposeConfig::blacklistIds, PocketReposeConfig::setBlacklist),
            ConfigOption.number(ITEMS, "exitKeystoneCooldown", "Seconds before the Exit Keystone can be used again (0 to 600)",
                    1.0F, 0, 600, PocketRepose::getExitKeystoneCooldown, PocketRepose::setExitKeystoneCooldown),
            ConfigOption.bool(ITEMS, "exitKeystoneWorksInOverworld", "The Exit Keystone returns players to their spawn point from the Overworld",
                    false, PocketRepose::getExitKeystoneInOverworld, PocketRepose::setExitKeystoneInOverworld),
            ConfigOption.bool(ITEMS, "exitKeystoneWorksInNether", "The Exit Keystone returns players to their spawn point from the Nether",
                    true, PocketRepose::getExitKeystoneInNether, PocketRepose::setExitKeystoneInNether),
            ConfigOption.bool(ITEMS, "exitKeystoneWorksInEnd", "The Exit Keystone returns players to their spawn point from the End",
                    true, PocketRepose::getExitKeystoneInEnd, PocketRepose::setExitKeystoneInEnd)
    );

    // When the world's file was last written or read by the mod, to notice edits made by hand
    @Nullable private static FileTime knownFileTime;

    private PocketReposeConfig() {
    }

    // ---- Server side ----

    /**
     * Settings as a new world starts with them: the built-in defaults, then the defaults file (written with the
     * built-in defaults the first time).
     */
    public static void applyDefaults() {
        Path file = configDir.resolve(DEFAULTS_FILE);
        if (Files.exists(file)) {
            applyAll(read(file));
        } else {
            write(file, defaultsHeader(), builtInDefaults());
        }
    }

    /**
     * Loads the world's file once the world's own saved settings are in place. If the world has no file yet, it's
     * written from the current settings (the defaults, or what an older version saved with the world).
     */
    public static void loadWorld(MinecraftServer server) {
        Path file = worldFile(server);
        if (Files.exists(file)) {
            applyAll(read(file));
            knownFileTime = modifiedTime(file);
        } else {
            save(server);
        }
    }

    /** Called whenever a setting changes: saves the world's file and updates everyone's config screen. */
    public static void onChanged(MinecraftServer server) {
        save(server);
        ConfigNetwork.sendToAll(server, snapshot());
    }

    /** Picks up edits made to the world's file while the server runs. */
    public static void tick(MinecraftServer server) {
        if (server.getTicks() % FILE_CHECK_TICKS != 0) return;
        Path file = worldFile(server);
        FileTime time = modifiedTime(file);
        if (time == null || time.equals(knownFileTime)) return;
        knownFileTime = time;
        applyAll(read(file));
        PocketRepose.LOGGER.info("Reloaded Pocket Repose settings from {}", file);
        PocketReposeSettingsData.markChanged(server);
        ConfigNetwork.sendToAll(server, snapshot());
    }

    /** A player changed settings in the config screen. Only operators can; anyone else just gets the real values back. */
    public static void onChangeRequested(ServerPlayerEntity player, NbtCompound changes) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        if (!player.hasPermissionLevel(PERMISSION_LEVEL)) {
            ConfigNetwork.sendTo(player, snapshot());
            return;
        }
        boolean changed = false;
        for (ConfigOption option : OPTIONS) {
            if (option.read(changes)) {
                changed = true;
                PocketRepose.LOGGER.info("{} set Pocket Repose setting {} to {}", player.getName().getString(), option.key, option.format(changes));
            }
        }
        if (changed) PocketReposeSettingsData.onSettingsChanged(server);
        else ConfigNetwork.sendTo(player, snapshot());
    }

    /** All the current settings, as sent to players. */
    public static NbtCompound snapshot() {
        NbtCompound nbt = new NbtCompound();
        for (ConfigOption option : OPTIONS) option.write(nbt);
        return nbt;
    }

    public static Path worldFile(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("serverconfig").resolve(WORLD_FILE).normalize();
    }

    private static void save(MinecraftServer server) {
        Path file = worldFile(server);
        write(file, worldHeader(), snapshot());
        knownFileTime = modifiedTime(file);
    }

    private static void applyAll(NbtCompound nbt) {
        for (ConfigOption option : OPTIONS) option.read(nbt);
    }

    // ---- The defaults file, edited from the config screen when no world is open ----

    public static NbtCompound readDefaults() {
        NbtCompound nbt = builtInDefaults();
        Path file = configDir.resolve(DEFAULTS_FILE);
        if (Files.exists(file)) nbt.copyFrom(read(file));
        return nbt;
    }

    public static void writeDefaults(NbtCompound nbt) {
        write(configDir.resolve(DEFAULTS_FILE), defaultsHeader(), nbt);
    }

    public static NbtCompound builtInDefaults() {
        NbtCompound nbt = new NbtCompound();
        for (ConfigOption option : OPTIONS) option.writeDefault(nbt);
        return nbt;
    }

    // ---- The capture blacklist as entity ids ----

    private static List<String> blacklistIds() {
        List<String> ids = new ArrayList<>();
        for (EntityType<?> type : PocketRepose.getEntityBlacklist()) ids.add(Registries.ENTITY_TYPE.getId(type).toString());
        ids.sort(null);
        return ids;
    }

    private static void setBlacklist(List<String> ids) {
        PocketRepose.clearBlacklist();
        for (String id : ids) {
            Identifier identifier = Identifier.tryParse(id);
            if (identifier == null) continue;
            Registries.ENTITY_TYPE.getOrEmpty(identifier).ifPresent(type -> {
                if (type != EntityType.PLAYER) PocketRepose.addToBlacklist(type);
            });
        }
    }

    // ---- File format: a small part of TOML (sections, "key = value" and # comments) ----

    private static NbtCompound read(Path file) {
        NbtCompound nbt = new NbtCompound();
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            PocketRepose.LOGGER.error("Couldn't read {}", file, e);
            return nbt;
        }
        for (String line : lines) {
            String text = line.trim();
            if (text.isEmpty() || text.startsWith("#") || text.startsWith("[") && !text.contains("=")) continue;
            int hash = text.indexOf('#');
            if (hash >= 0) text = text.substring(0, hash).trim();
            int equals = text.indexOf('=');
            if (equals <= 0) continue;
            String key = text.substring(0, equals).trim();
            String value = text.substring(equals + 1).trim();
            for (ConfigOption option : OPTIONS) {
                if (option.key.equals(key)) {
                    if (!option.parseInto(nbt, value)) {
                        PocketRepose.LOGGER.warn("Ignoring invalid value for {} in {}: {}", key, file, value);
                    }
                    break;
                }
            }
        }
        return nbt;
    }

    private static void write(Path file, String header, NbtCompound values) {
        StringBuilder out = new StringBuilder(header);
        for (ConfigOption.Category category : ConfigOption.Category.values()) {
            out.append("\n[").append(category.section).append("]\n");
            for (ConfigOption option : OPTIONS) {
                if (option.category != category) continue;
                out.append("# ").append(option.description).append('\n');
                out.append(option.key).append(" = ").append(option.format(values)).append('\n');
            }
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            PocketRepose.LOGGER.error("Couldn't write {}", file, e);
        }
    }

    private static String worldHeader() {
        return "# Pocket Repose settings for this world.\n"
                + "# Edits made here are picked up while the game or server is running.\n"
                + "# Operators can also change these in game, with /pocketRepose or the mod's config screen.\n";
    }

    private static String defaultsHeader() {
        return "# Pocket Repose settings that new worlds start with.\n"
                + "# Each world keeps its own copy in <world>/serverconfig/" + WORLD_FILE + ".\n";
    }

    @Nullable
    private static FileTime modifiedTime(Path file) {
        try {
            return Files.exists(file) ? Files.getLastModifiedTime(file) : null;
        } catch (IOException e) {
            return null;
        }
    }
}
