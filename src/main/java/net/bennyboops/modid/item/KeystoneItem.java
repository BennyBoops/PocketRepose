package net.bennyboops.modid.item;

import net.bennyboops.modid.PocketRepose;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class KeystoneItem extends Item {
    public static final Set<String> TEMPLATE_DIMENSIONS = Set.of(
            "template_house",
            "template_garden",
            "template_cave",
            "template_workshop"
    );

    public KeystoneItem(Settings settings) {
        super(settings);
    }


    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        String keystoneName = stack.getName().getString().toLowerCase();
        String defaultName = "item.pocket-repose.keystone";

        // Check both the display name and if it has a custom name
        if (!stack.hasCustomName() || keystoneName.equals(defaultName)) {
            return TypedActionResult.pass(stack);
        }

        if (world.isClient) {
            return TypedActionResult.success(stack);
        }

        // If already bound (has enchantment), prevent using again
        if (stack.hasEnchantments()) {
            return TypedActionResult.pass(stack);
        }

        String dimensionName = "pocket_dimension_" + keystoneName.replaceAll("[^a-z0-9_]", "");

        // Try to create dimension, but bind either way if name is valid
        createDimension(world.getServer(), dimensionName);

        // Add multiple enchantments to increase repair cost
        for (int i = 0; i < 20; i++) {
            stack.addEnchantment(Enchantments.BINDING_CURSE, 1);
        }

        // Hide the enchantments from tooltip
        stack.addHideFlag(ItemStack.TooltipSection.ENCHANTMENTS);
        // Lock the name
        stack.addHideFlag(ItemStack.TooltipSection.MODIFIERS);

        // Set repair cost
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putInt("RepairCost", 32767);

        // Play binding sound
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_AMETHYST_CLUSTER_FALL, SoundCategory.PLAYERS, 2.0F, 2.0F);

//        player.sendMessage(Text.literal("§aKeystone bound! Use a suitcase to teleport."), false);
        return TypedActionResult.success(stack);
    }

    public static boolean isValidKeystone(ItemStack stack) {
        String keystoneName = stack.getName().getString().toLowerCase();
        return stack.hasCustomName() &&
                !keystoneName.equals("item.pocket-repose.keystone") &&
                stack.hasEnchantments();
    }


    // Add tooltip to indicate renaming requirement
    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        if (!stack.hasCustomName() ||
                stack.getName().getString().toLowerCase().equals("item.pocket-repose.keystone")) {
            tooltip.add(Text.literal("§7Rename to bind").formatted(Formatting.ITALIC));
        }
    }


    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (!stack.hasCustomName() ||
                stack.getName().getString().toLowerCase().equals("item.pocket-repose.keystone")) {
            NbtCompound nbt = stack.getOrCreateNbt();
            nbt.putInt("CustomModelData", 1);
        } else if (stack.getNbt() != null && stack.getNbt().contains("CustomModelData")) {
            stack.getNbt().remove("CustomModelData");
        }
    }

    private boolean createDimension(MinecraftServer server, String dimensionName) {
        try {
            Path datapackPath = server.getSavePath(WorldSavePath.DATAPACKS)
                    .resolve("pocket-repose");
            Path dimensionPath = datapackPath
                    .resolve("data")
                    .resolve("pocket-repose")
                    .resolve("dimension");

            Files.createDirectories(dimensionPath);
            createPackMcmeta(datapackPath);

            // Create dimension file
            Path dimensionFile = dimensionPath.resolve(dimensionName + ".json");
            boolean dimensionExists = Files.exists(dimensionFile);

            // Check if dimension registry already has this dimension
            boolean isDimensionRegistered = server.getWorldRegistryKeys().stream()
                    .anyMatch(key -> key.getValue().toString().equals("pocket-repose:" + dimensionName));

            // Check persistent data to see if this dimension has been created before
            Path dimensionRegistryPath = server.getSavePath(WorldSavePath.ROOT)
                    .resolve("data")
                    .resolve("pocket-repose")
                    .resolve("dimension_registry");
            Files.createDirectories(dimensionRegistryPath);
            Path dimensionRegistryFile = dimensionRegistryPath.resolve("registry.txt");

            Set<String> registeredDimensions = new HashSet<>();
            if (Files.exists(dimensionRegistryFile)) {
                registeredDimensions = new HashSet<>(Files.readAllLines(dimensionRegistryFile));
            }

            boolean isDimensionInRegistry = registeredDimensions.contains(dimensionName);

            if (!dimensionExists) {
                String dimensionJson = """
                    {
                        "type": "pocket-repose:pocket_dimension_type",
                        "generator": {
                            "type": "minecraft:flat",
                            "settings": {
                                "biome": "pocket-repose:pocket_islands",
                                "layers": [
                                    {
                                        "block": "pocket-repose:portal",
                                        "height": 1
                                    }
                                ]
                            }
                        }
                    }
                    """;
                Files.writeString(dimensionFile, dimensionJson);

                // Only add to registry if it's a new dimension
                if (!isDimensionInRegistry) {
                    registeredDimensions.add(dimensionName);
                    Files.write(dimensionRegistryFile, registeredDimensions);
                }
            }

            // Only create a structure marker if this is truly a new dimension
            // that hasn't been registered before
            if (!isDimensionInRegistry && !isDimensionRegistered) {
                Path structureMarkerPath = server.getSavePath(WorldSavePath.ROOT)
                        .resolve("data")
                        .resolve("pocket-repose")
                        .resolve("pending_structures");
                Files.createDirectories(structureMarkerPath);
                Files.writeString(structureMarkerPath.resolve(dimensionName + ".txt"), "pending");

                server.getGameRules().get(GameRules.KEEP_INVENTORY).set(true, server);
                return true;
            }

            return false;

        } catch (IOException e) {
            PocketRepose.LOGGER.error("Failed to create dimension: " + dimensionName, e);
            return false;
        }
    }

    private void createPackMcmeta(Path datapackPath) throws IOException {
        Path packMcmeta = datapackPath.resolve("pack.mcmeta");
        if (!Files.exists(packMcmeta)) {
            String content = """
                    {
                        "pack": {
                            "pack_format": 15,
                            "description": "pocket-repose Dimensions"
                        }
                    }
                    """;
            Files.writeString(packMcmeta, content);
        }
    }
}


/**
public class KeystoneItem extends Item {
    public static final Set<String> TEMPLATE_DIMENSIONS = Set.of(
            "template_house",
            "template_garden",
            "template_cave",
            "template_workshop"
    );

    public KeystoneItem(Settings settings) {
        super(settings);
    }


    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        String keystoneName = stack.getName().getString().toLowerCase();
        String defaultName = "item.pocket-repose.keystone";

        // Check both the display name and if it has a custom name
        if (!stack.hasCustomName() || keystoneName.equals(defaultName)) {
            return TypedActionResult.pass(stack);
        }

        if (world.isClient) {
            return TypedActionResult.success(stack);
        }

        // If already bound (has enchantment), prevent using again
        if (stack.hasEnchantments()) {
            return TypedActionResult.pass(stack);
        }

        String dimensionName = "pocket_dimension_" + keystoneName.replaceAll("[^a-z0-9_]", "");

        // Try to create dimension, but bind either way if name is valid
        createDimension(world.getServer(), dimensionName);

        // Add multiple enchantments to increase repair cost
        for (int i = 0; i < 20; i++) {
            stack.addEnchantment(Enchantments.BINDING_CURSE, 1);
        }

        // Hide the enchantments from tooltip
        stack.addHideFlag(ItemStack.TooltipSection.ENCHANTMENTS);
        // Lock the name
        stack.addHideFlag(ItemStack.TooltipSection.MODIFIERS);

        // Set repair cost
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putInt("RepairCost", 32767);

        // Play binding sound
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_AMETHYST_CLUSTER_FALL, SoundCategory.PLAYERS, 2.0F, 2.0F);

//        player.sendMessage(Text.literal("§aKeystone bound! Use a suitcase to teleport."), false);
        return TypedActionResult.success(stack);
    }

    public static boolean isValidKeystone(ItemStack stack) {
        String keystoneName = stack.getName().getString().toLowerCase();
        return stack.hasCustomName() &&
                !keystoneName.equals("item.pocket-repose.keystone") &&
                stack.hasEnchantments();
    }


    // Add tooltip to indicate renaming requirement
    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        if (!stack.hasCustomName() ||
                stack.getName().getString().toLowerCase().equals("item.pocket-repose.keystone")) {
            tooltip.add(Text.literal("§7Rename to bind").formatted(Formatting.ITALIC));
        }
    }


    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (!stack.hasCustomName() ||
                stack.getName().getString().toLowerCase().equals("item.pocket-repose.keystone")) {
            NbtCompound nbt = stack.getOrCreateNbt();
            nbt.putInt("CustomModelData", 1);
        } else if (stack.getNbt() != null && stack.getNbt().contains("CustomModelData")) {
            stack.getNbt().remove("CustomModelData");
        }
    }

    private boolean createDimension(MinecraftServer server, String dimensionName) {
        try {
            Path datapackPath = server.getSavePath(WorldSavePath.DATAPACKS)
                    .resolve("pocket-repose");
            Path dimensionPath = datapackPath
                    .resolve("data")
                    .resolve("pocket-repose")
                    .resolve("dimension");

            Files.createDirectories(dimensionPath);
            createPackMcmeta(datapackPath);

            // Create a marker file to indicate we need to place a structure
            Path structureMarkerPath = server.getSavePath(WorldSavePath.ROOT)
                    .resolve("data")
                    .resolve("pocket-repose")
                    .resolve("pending_structures");
            Files.createDirectories(structureMarkerPath);
            Files.writeString(structureMarkerPath.resolve(dimensionName + ".txt"), "pending");

            // Create dimension file
            Path dimensionFile = dimensionPath.resolve(dimensionName + ".json");
            if (!Files.exists(dimensionFile)) {
                String dimensionJson = """
                        {
                            "type": "pocket-repose:pocket_dimension_type",
                            "generator": {
                                "type": "minecraft:flat",
                                "settings": {
                                    "biome": "pocket-repose:pocket_islands",
                                    "layers": [
                                        {
                                            "block": "pocket-repose:portal",
                                            "height": 1
                                        }
                                    ]
                                }
                            }
                        }
                        """;
                Files.writeString(dimensionFile, dimensionJson);
                return true;
            }
            return false;

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void createPackMcmeta(Path datapackPath) throws IOException {
        Path packMcmeta = datapackPath.resolve("pack.mcmeta");
        if (!Files.exists(packMcmeta)) {
            String content = """
                    {
                        "pack": {
                            "pack_format": 15,
                            "description": "pocket-repose Dimensions"
                        }
                    }
                    """;
            Files.writeString(packMcmeta, content);
        }
    }
}
     **/