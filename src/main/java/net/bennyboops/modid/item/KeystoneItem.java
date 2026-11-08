package net.bennyboops.modid.item;

import net.bennyboops.modid.PocketRepose;
import net.bennyboops.modid.block.ModBlocks;
import net.bennyboops.modid.data.MobEntryData;
import net.bennyboops.modid.data.PlayerEntryData;
import net.bennyboops.modid.world.PortalChunkGenerator;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.dimension.DimensionType;

import net.minecraft.world.gen.chunk.*;
import org.jetbrains.annotations.Nullable;

import net.bennyboops.modid.world.Fantasy;
import net.bennyboops.modid.world.RuntimeWorldConfig;
import net.bennyboops.modid.world.RuntimeWorldHandle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class KeystoneItem extends Item {

    // Where players (and mobs) arrive on the blank island, pocket_island_02
    private static final Vec3d BLANK_ISLAND_ENTRY = new Vec3d(2.5, 71, 2.5);
    // How many chunks out from 0,0 to look for dry ground when a World Key dimension is created
    private static final int WORLD_SPAWN_SEARCH_RINGS = 2;
    private static final String WORLD_REGISTRY_FILE = "worlds.txt";

    /** What a key builds when it creates a new pocket dimension. */
    public enum Layout {
        /** Island Key: the full island, pocket_island_01 */
        ISLAND("Rename to bind. Creates an island."),
        /** Blank Key: the small blank island, pocket_island_02 */
        BLANK("Rename to bind. Creates a void."),
        /** World Key: a whole new world with normal terrain */
        WORLD("Rename to bind. Creates a world.");

        private final String tooltip;

        Layout(String tooltip) {
            this.tooltip = tooltip;
        }
    }

    private final Layout layout;

    public KeystoneItem(Settings settings, Layout layout) {
        super(settings);
        this.layout = layout;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world,
                                            PlayerEntity player,
                                            Hand hand) {
        ItemStack stack = player.getStackInHand(hand);

        if (!stack.contains(DataComponentTypes.CUSTOM_NAME)) {
            return TypedActionResult.pass(stack);
        }

        if (world.isClient) {
            return TypedActionResult.success(stack);
        }

        return bind(world.getServer(), player, stack, true)
                ? TypedActionResult.success(stack)
                : TypedActionResult.fail(stack);
    }

    /**
     * Creates (or loads) the pocket dimension a renamed key points to, without changing the key.
     * This runs as the key leaves the anvil: the key is still on the cursor then, and changing it there
     * makes the client show a leftover copy on its cursor if the screen is closed quickly (which creative
     * mode lets you keep). The key itself is marked as bound once it's in an inventory slot.
     */
    public static boolean openDimension(MinecraftServer server, PlayerEntity player, ItemStack stack, boolean reportErrors) {
        if (!(stack.getItem() instanceof KeystoneItem keystone) || !stack.contains(DataComponentTypes.CUSTOM_NAME)) {
            return false;
        }

        String sanitizedName = sanitizedName(stack);
        if (sanitizedName.isEmpty()) {
            // Names with no a-z/0-9/_ characters would all share the same "pocket_dimension_" world
            if (reportErrors) {
                player.sendMessage(Text.literal("Keystone name must contain letters or numbers")
                        .formatted(Formatting.RED), true);
            }
            return false;
        }

        String dimensionName = "pocket_dimension_" + sanitizedName;
        boolean newDimension = server.getWorld(RegistryKey.of(RegistryKeys.WORLD,
                Identifier.of("pocket-repose", dimensionName))) == null;
        createOrLoadPersistentDimension(server, dimensionName, keystone.layout);

        if (newDimension) {
            player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_AMETHYST_CLUSTER_FALL,
                    SoundCategory.PLAYERS, 2f, 2f);
        }
        return true;
    }

    /** Binds a renamed key: opens its pocket dimension and marks the key as bound. */
    public static boolean bind(MinecraftServer server, PlayerEntity player, ItemStack stack, boolean reportErrors) {
        if (!openDimension(server, player, stack, reportErrors)) {
            return false;
        }

        if (!stack.hasEnchantments()) {
            RegistryEntry<Enchantment> bindingCurse =
                    server.getRegistryManager()
                            .get(RegistryKeys.ENCHANTMENT)
                            .entryOf(Enchantments.BINDING_CURSE);

            stack.addEnchantment(bindingCurse, 1);
        }
        // Hide the curse right away; otherwise it shows in the tooltip until the key's next inventory tick
        hideBindingEnchantment(stack);
        return true;
    }

    /** A key is bound once it has been renamed and given its binding enchantment. */
    public static boolean isBound(ItemStack stack) {
        return stack.getItem() instanceof KeystoneItem
                && stack.contains(DataComponentTypes.CUSTOM_NAME)
                && stack.hasEnchantments();
    }

    public static String sanitizedName(ItemStack stack) {
        return Formatting.strip(stack.getName().getString())
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]", "");
    }

    public static ServerWorld createOrLoadPersistentDimension(MinecraftServer server,
                                                              String dimensionName) {
        return createOrLoadPersistentDimension(server, dimensionName, Layout.ISLAND);
    }

    /**
     * Opens a pocket dimension, building its starting island (or world spawn) only if the dimension
     * is new. For islands the spawnIsland setting overrides the key: when it's off, every new island
     * dimension gets the grass cube. World Key dimensions always generate a world.
     */
    public static ServerWorld createOrLoadPersistentDimension(MinecraftServer server,
                                                              String dimensionName,
                                                              Layout layout) {

        Identifier worldId = Identifier.of("pocket-repose", dimensionName);
        Path dimPath = server.getSavePath(WorldSavePath.ROOT)
                .resolve("dimensions")
                .resolve("pocket-repose")
                .resolve(dimensionName);
        // A world created this session may not have been saved to disk yet, so also check if it's loaded
        boolean exists = Files.exists(dimPath)
                || server.getWorld(RegistryKey.of(RegistryKeys.WORLD, worldId)) != null;

        boolean worldDimension = isWorldDimension(server, dimensionName) || (!exists && layout == Layout.WORLD);
        if (worldDimension) {
            addToDimensionRegistry(server, WORLD_REGISTRY_FILE, dimensionName);
        }

        RuntimeWorldConfig cfg = worldDimension
                ? PocketRepose.createWorldDimensionConfig(server)
                : PocketRepose.createPocketWorldConfig(server);

        RuntimeWorldHandle handle =
                Fantasy.get(server).getOrOpenPersistentWorld(worldId, cfg);

        registerDimension(server, dimensionName);

        if (!exists) {
            if (worldDimension) {
                placeWorldSpawn(handle.asWorld());
                PocketRepose.LOGGER.info("Created new world dimension {}", dimensionName);
            } else if (PocketRepose.getSpawnIsland() && layout == Layout.BLANK) {
                placeStructureImmediately(server, handle.asWorld(), "pocket_island_02");
                PlayerEntryData.get(handle.asWorld()).setEntry(BLANK_ISLAND_ENTRY, 0f, 0f);
                MobEntryData.get(handle.asWorld()).setEntry(BLANK_ISLAND_ENTRY, 0f, 0f);
                PocketRepose.LOGGER.info("Created new pocket dimension {} with blank island", dimensionName);
            } else if (PocketRepose.getSpawnIsland()) {
                placeStructureImmediately(server, handle.asWorld(), "pocket_island_01");
                PocketRepose.LOGGER.info("Created new pocket dimension {} with island structure", dimensionName);
            } else {
                placeGrassCube(handle.asWorld());
                PocketRepose.LOGGER.info("Created new pocket dimension {} with grass cube", dimensionName);
            }
        }
        return handle.asWorld();
    }

    /**
     * Picks where players arrive in a World Key dimension: dry ground near 0,0, searching outward a
     * chunk at a time. The exit portal goes three blocks above, like on the grass cube.
     */
    private static void placeWorldSpawn(ServerWorld world) {
        BlockPos spawn = null;
        for (int ring = 0; ring <= WORLD_SPAWN_SEARCH_RINGS && spawn == null; ring++) {
            for (int dx = -ring; dx <= ring && spawn == null; dx++) {
                for (int dz = -ring; dz <= ring && spawn == null; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    spawn = findDryGround(world, dx * 16 + 8, dz * 16 + 8);
                }
            }
        }

        if (spawn == null) {
            // Nothing but water nearby: stand on a small grass platform at the surface
            world.getChunk(0, 0);
            spawn = new BlockPos(8, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, 8, 8), 8);
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    world.setBlockState(spawn.add(x, -1, z), Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
        }

        world.setBlockState(spawn.up(3), ModBlocks.PORTAL.getDefaultState(), Block.NOTIFY_ALL);

        Vec3d entry = Vec3d.ofBottomCenter(spawn);
        PlayerEntryData.get(world).setEntry(entry, 0f, 0f);
        MobEntryData.get(world).setEntry(entry, 0f, 0f);
    }

    @Nullable
    private static BlockPos findDryGround(ServerWorld world, int x, int z) {
        // getTopY only reads loaded chunks, so generate this one first
        world.getChunk(x >> 4, z >> 4);
        BlockPos feet = new BlockPos(x, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        BlockState ground = world.getBlockState(feet.down());
        if (!ground.getFluidState().isEmpty() || ground.isIn(BlockTags.LEAVES) || !ground.isSolidBlock(world, feet.down())) {
            return null;
        }
        if (!world.getBlockState(feet).isAir() || !world.getBlockState(feet.up()).isAir()) {
            return null;
        }
        return feet;
    }

    private static void placeGrassCube(ServerWorld world) {
        BlockPos spawnPos = new BlockPos(17, 97, 9);

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos grassPos = spawnPos.add(x, y - 2, z);
                    world.setBlockState(grassPos, Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
        }

        BlockPos portalPos = new BlockPos(17, 100, 9);
        world.setBlockState(portalPos, net.bennyboops.modid.block.ModBlocks.PORTAL.getDefaultState(), Block.NOTIFY_ALL);

        net.bennyboops.modid.data.MobEntryData mobData = net.bennyboops.modid.data.MobEntryData.get(world);
        mobData.setEntry(new net.minecraft.util.math.Vec3d(17.5, 97.0, 9.5), 0f, 0f);

        PocketRepose.LOGGER.info("Placed cube, portal, and set mob entry at spawn location");
    }

    private static void placeStructureImmediately(MinecraftServer server,
                                           ServerWorld world,
                                           String structureName) {
        Identifier structureId = Identifier.of("pocket-repose", structureName);

        PocketRepose.LOGGER.info("Looking for structure: {}", structureId);

        var templateOpt = server.getStructureTemplateManager().getTemplate(structureId);

        if (templateOpt.isEmpty()) {
            PocketRepose.LOGGER.error("Structure NOT found, attempting direct file load");

            try {
                String resourcePath = "/data/pocket-repose/structure/" + structureName + ".nbt";
                var inputStream = KeystoneItem.class.getResourceAsStream(resourcePath);

                if (inputStream != null) {
                    PocketRepose.LOGGER.info("Found structure in mod resources");

                    NbtCompound nbt = net.minecraft.nbt.NbtIo.readCompressed(inputStream,
                            net.minecraft.nbt.NbtSizeTracker.ofUnlimitedBytes());
                    StructureTemplate template = new StructureTemplate();

                    var blockLookup = server.getRegistryManager().getWrapperOrThrow(RegistryKeys.BLOCK);
                    template.readNbt(blockLookup, nbt);

                    BlockPos origin = new BlockPos(0, 64, 0);
                    world.getChunk(origin);

                    template.place(world, origin, origin,
                            new StructurePlacementData()
                                    .setMirror(BlockMirror.NONE)
                                    .setRotation(BlockRotation.NONE)
                                    .setIgnoreEntities(false),
                            world.getRandom(),
                            Block.NOTIFY_ALL);

                    PocketRepose.LOGGER.info("Structure loaded and placed from mod resources");
                    inputStream.close();
                    return;
                } else {
                    PocketRepose.LOGGER.error("Structure not found in mod resources at: {}", resourcePath);
                }
            } catch (Exception e) {
                PocketRepose.LOGGER.error("Failed to load structure from mod resources", e);
            }

            return;
        }

        StructureTemplate template = templateOpt.get();
        BlockPos origin = new BlockPos(0, 64, 0);
        world.getChunk(origin);

        template.place(world, origin, origin,
                new StructurePlacementData()
                        .setMirror(BlockMirror.NONE)
                        .setRotation(BlockRotation.NONE)
                        .setIgnoreEntities(false),
                world.getRandom(),
                Block.NOTIFY_ALL);

        PocketRepose.LOGGER.info("Structure placed successfully");
    }

    public static boolean isValidKeystone(ItemStack stack) {
        String cleanedName = Formatting.strip(stack.getName().getString())
                .toLowerCase(Locale.ROOT);

        boolean hasCustomName = stack.contains(DataComponentTypes.CUSTOM_NAME)
                || (stack.getFrame() != null
                && stack.getFrame().hasCustomName());

        boolean isDefault = cleanedName.equals(stack.getItem().getTranslationKey());

        return hasCustomName && !isDefault && stack.hasEnchantments();
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context,
                              List<Text> tooltip, TooltipType type) {
        boolean hasCustomName = stack.contains(DataComponentTypes.CUSTOM_NAME);
        boolean isDefault     = Formatting.strip(stack.getName().getString())
                .equalsIgnoreCase(getTranslationKey());

        if (!hasCustomName || isDefault) {
            tooltip.add(Text.literal(layout.tooltip)
                    .formatted(Formatting.GRAY, Formatting.ITALIC));
        }

    }

    @Override
    public void inventoryTick(ItemStack stack, World world,
                              Entity entity, int slot, boolean selected) {
        if (!world.isClient && this == ModItems.LEGACY_KEYSTONE && entity instanceof PlayerEntity player) {
            replaceLegacyKey(player, stack);
            return;
        }

        // A renamed key is marked as bound once it's in an inventory slot (see openDimension). This also
        // catches shift-clicked keys and keys renamed before binding happened at the anvil
        if (!world.isClient && entity instanceof PlayerEntity player
                && stack.contains(DataComponentTypes.CUSTOM_NAME) && !stack.hasEnchantments()
                && !sanitizedName(stack).isEmpty()) {
            bind(world.getServer(), player, stack, false);
        }

        ItemFrameEntity frame = stack.getFrame();

        boolean hasCustomName = stack.contains(DataComponentTypes.CUSTOM_NAME);

        String displayName = stack.getName().getString();
        boolean isDefaultName = displayName.equalsIgnoreCase(getTranslationKey())
                || displayName.equalsIgnoreCase("Keystone");

        boolean shouldShowBroken = !hasCustomName || isDefaultName;

        if (frame != null) {
            shouldShowBroken = !frame.hasCustomName();
        }

        if (shouldShowBroken) {
            CustomModelDataComponent cmd = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
            if (cmd == null || cmd.value() != 1) {
                stack.set(DataComponentTypes.CUSTOM_MODEL_DATA,
                        new CustomModelDataComponent(1));
            }
        } else {
            stack.remove(DataComponentTypes.CUSTOM_MODEL_DATA);
        }

        if (!world.isClient && stack.hasEnchantments()) {
            hideBindingEnchantment(stack);
        }
    }

    /** Swaps an old "keystone" for a gold_key with the same name, binding and everything else. */
    private static void replaceLegacyKey(PlayerEntity player, ItemStack stack) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.getStack(i) == stack) {
                inventory.setStack(i, stack.copyComponentsToNewStack(ModItems.GOLD_KEY, stack.getCount()));
                return;
            }
        }
    }

    private static void hideBindingEnchantment(ItemStack stack) {
        int cost = stack.getOrDefault(DataComponentTypes.REPAIR_COST, 0);
        if (cost < 32_767) {
            stack.set(DataComponentTypes.REPAIR_COST, 32_767);
        }

        ItemEnchantmentsComponent enchantments = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (enchantments != null) {
            stack.set(DataComponentTypes.ENCHANTMENTS, enchantments.withShowInTooltip(false));
        }
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return stack.hasEnchantments();
    }

    private static void registerDimension(MinecraftServer server,
                                   String dimensionName) {
        addToDimensionRegistry(server, "registry.txt", dimensionName);
    }

    /**
     * World Key dimensions are also listed here, so after a restart they're reloaded with terrain
     * generation instead of as an empty island dimension.
     */
    public static boolean isWorldDimension(MinecraftServer server, String dimensionName) {
        Path file = dimensionRegistryDir(server).resolve(WORLD_REGISTRY_FILE);
        try {
            return Files.exists(file) && Files.readAllLines(file).stream()
                    .anyMatch(line -> line.trim().equals(dimensionName));
        } catch (IOException e) {
            PocketRepose.LOGGER.error("Failed to read world dimension registry", e);
            return false;
        }
    }

    private static Path dimensionRegistryDir(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("data/pocket-repose/dimension_registry");
    }

    private static void addToDimensionRegistry(MinecraftServer server, String fileName, String dimensionName) {
        Path dir = dimensionRegistryDir(server);

        try {
            Files.createDirectories(dir);
            Path file = dir.resolve(fileName);

            Set<String> lines = Files.exists(file)
                    ? new HashSet<>(Files.readAllLines(file))
                    : new HashSet<>();

            if (lines.add(dimensionName)) {
                Files.write(file, lines);
            }
        } catch (IOException e) {
            PocketRepose.LOGGER.error("Failed to update dimension registry", e);
        }
    }
}