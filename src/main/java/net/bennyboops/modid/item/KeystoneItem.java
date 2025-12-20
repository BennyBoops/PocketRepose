package net.bennyboops.modid.item;

import net.bennyboops.modid.PocketRepose;
import net.bennyboops.modid.world.PortalChunkGenerator;
import net.minecraft.block.Block;
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

    private static final Identifier POCKET_DIMENSION_TYPE_ID =
            Identifier.of("pocket-repose", "pocket_dimension_type");

    public KeystoneItem(Settings settings) { super(settings); }

    @Override
    public TypedActionResult<ItemStack> use(World world,
                                            PlayerEntity player,
                                            Hand hand) {
        ItemStack stack = player.getStackInHand(hand);

        String rawName      = Formatting.strip(stack.getName().getString());
        String keystoneName = rawName.toLowerCase(Locale.ROOT);
        if (!stack.contains(DataComponentTypes.CUSTOM_NAME)
                || keystoneName.equals("item.pocket-repose.keystone")) {
            return TypedActionResult.pass(stack);
        }

        if (world.isClient) {
            return TypedActionResult.success(stack);
        }

        String dimensionName = "pocket_dimension_" +
                keystoneName.replaceAll("[^a-z0-9_]", "");
        createOrLoadPersistentDimension(world.getServer(), dimensionName);

        if (!stack.hasEnchantments()) {
            RegistryEntry<Enchantment> bindingCurse =
                    world.getServer().getRegistryManager()
                            .get(RegistryKeys.ENCHANTMENT)
                            .entryOf(Enchantments.BINDING_CURSE);

            stack.addEnchantment(bindingCurse, 1);
        }

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_AMETHYST_CLUSTER_FALL,
                SoundCategory.PLAYERS, 2f, 2f);

        return TypedActionResult.success(stack);
    }

    private void createOrLoadPersistentDimension(MinecraftServer server,
                                                 String dimensionName) {

        Identifier worldId = Identifier.of("pocket-repose", dimensionName);
        Path dimPath = server.getSavePath(WorldSavePath.ROOT)
                .resolve("dimensions")
                .resolve("pocket-repose")
                .resolve(dimensionName);
        boolean exists = Files.exists(dimPath);

        RegistryKey<DimensionType> typeKey =
                RegistryKey.of(RegistryKeys.DIMENSION_TYPE,
                        POCKET_DIMENSION_TYPE_ID);

        Registry<Biome> biomeRegistry =
                server.getRegistryManager().get(RegistryKeys.BIOME);

        ChunkGenerator generator = new PortalChunkGenerator(biomeRegistry);
        long seed = server.getOverworld().getSeed();

        RuntimeWorldConfig cfg = new RuntimeWorldConfig()
                .setDimensionType(typeKey)
                .setGenerator(generator)
                .setSeed(seed);

        RuntimeWorldHandle handle =
                Fantasy.get(server).getOrOpenPersistentWorld(worldId, cfg);

        registerDimension(server, dimensionName);

        if (!exists) {
            placeStructureImmediately(server, handle.asWorld(), dimensionName);
            PocketRepose.LOGGER.info("Created new pocket dimension {}", dimensionName);
        }
    }

    private void placeStructureImmediately(MinecraftServer server,
                                           ServerWorld world,
                                           String dimensionName) {
        Identifier structureId = Identifier.of("pocket-repose", "pocket_island_01");

        PocketRepose.LOGGER.info("Looking for structure: {}", structureId);

        var templateOpt = server.getStructureTemplateManager().getTemplate(structureId);

        if (templateOpt.isEmpty()) {
            PocketRepose.LOGGER.error("Structure NOT found, attempting direct file load");

            try {
                String resourcePath = "/data/pocket-repose/structures/pocket_island_01.nbt";
                var inputStream = getClass().getResourceAsStream(resourcePath);

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

        boolean isDefault = cleanedName.equals("item.pocket-repose.keystone");

        return hasCustomName && !isDefault && stack.hasEnchantments();
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context,
                              List<Text> tooltip, TooltipType type) {
        boolean hasCustomName = stack.contains(DataComponentTypes.CUSTOM_NAME);
        boolean isDefault     = Formatting.strip(stack.getName().getString())
                .equalsIgnoreCase("item.pocket-repose.keystone");

        if (!hasCustomName || isDefault) {
            tooltip.add(Text.literal("Rename to bind")
                    .formatted(Formatting.GRAY, Formatting.ITALIC));
        }

        // Don't show enchantments in tooltip
        // This is handled by removing them from the component below
    }

    @Override
    public void inventoryTick(ItemStack stack, World world,
                              Entity entity, int slot, boolean selected) {
        ItemFrameEntity frame = stack.getFrame();

        // Check if it has a custom name (renamed in anvil)
        boolean hasCustomName = stack.contains(DataComponentTypes.CUSTOM_NAME);

        // Check if it's the default name (untranslated key)
        String displayName = stack.getName().getString();
        boolean isDefaultName = displayName.equalsIgnoreCase("item.pocket-repose.keystone")
                || displayName.equalsIgnoreCase("Keystone"); // Add translated name too

        // Show broken texture if: no custom name OR still has default name
        boolean shouldShowBroken = !hasCustomName || isDefaultName;

        // Handle item frame case
        if (frame != null) {
            shouldShowBroken = !frame.hasCustomName();
        }

        if (shouldShowBroken) {
            // Show broken texture (custom_model_data=1)
            CustomModelDataComponent cmd = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
            if (cmd == null || cmd.value() != 1) {
                stack.set(DataComponentTypes.CUSTOM_MODEL_DATA,
                        new CustomModelDataComponent(1));
            }
        } else {
            // Show normal texture (remove custom_model_data)
            stack.remove(DataComponentTypes.CUSTOM_MODEL_DATA);
        }

        if (!world.isClient && stack.hasEnchantments()) {
            // Set high repair cost so it can't be easily modified
            int cost = stack.getOrDefault(DataComponentTypes.REPAIR_COST, 0);
            if (cost < 32_767) {
                stack.set(DataComponentTypes.REPAIR_COST, 32_767);
            }

            // Hide enchantment tooltip
            ItemEnchantmentsComponent enchantments = stack.get(DataComponentTypes.ENCHANTMENTS);
            if (enchantments != null) {
                stack.set(DataComponentTypes.ENCHANTMENTS, enchantments.withShowInTooltip(false));
            }
        }
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        // Always show enchantment glint if it has enchantments
        return stack.hasEnchantments();
    }

    private void registerDimension(MinecraftServer server,
                                   String dimensionName) {
        Path dir = server.getSavePath(WorldSavePath.ROOT)
                .resolve("data/pocket-repose/dimension_registry");

        try {
            Files.createDirectories(dir);
            Path file = dir.resolve("registry.txt");

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