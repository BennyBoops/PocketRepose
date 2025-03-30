package net.bennyboops.modid.item;

import net.bennyboops.modid.PocketRepose;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.*;
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

    public KeystoneItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        String keystoneName = stack.getName().getString().toLowerCase();
        String defaultName = "item.pocket-repose.keystone";
        if (!stack.hasCustomName() || keystoneName.equals(defaultName)) {
            return TypedActionResult.pass(stack);
        }
        if (world.isClient) {
            return TypedActionResult.success(stack);
        }
        if (stack.hasEnchantments()) {
            return TypedActionResult.pass(stack);
        }
        String dimensionName = "pocket_dimension_" + keystoneName.replaceAll("[^a-z0-9_]", "");
        createDimension(world.getServer(), dimensionName);
        for (int i = 0; i < 20; i++) {
            stack.addEnchantment(Enchantments.BINDING_CURSE, 1);
        }
        stack.addHideFlag(ItemStack.TooltipSection.ENCHANTMENTS);
        stack.addHideFlag(ItemStack.TooltipSection.MODIFIERS);
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putInt("RepairCost", 32767);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_AMETHYST_CLUSTER_FALL, SoundCategory.PLAYERS, 2.0F, 2.0F);
        return TypedActionResult.success(stack);
    }

    public static boolean isValidKeystone(ItemStack stack) {
        String keystoneName = stack.getName().getString().toLowerCase();
        return stack.hasCustomName() &&
                !keystoneName.equals("item.pocket-repose.keystone") &&
                stack.hasEnchantments();
    }

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
            Path dimensionFile = dimensionPath.resolve(dimensionName + ".json");
            boolean dimensionExists = Files.exists(dimensionFile);
            boolean isDimensionRegistered = server.getWorldRegistryKeys().stream()
                    .anyMatch(key -> key.getValue().toString().equals("pocket-repose:" + dimensionName));
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
                if (!isDimensionInRegistry) {
                    registeredDimensions.add(dimensionName);
                    Files.write(dimensionRegistryFile, registeredDimensions);
                }
            }
            if (!isDimensionInRegistry && !isDimensionRegistered) {
                Path structureMarkerPath = server.getSavePath(WorldSavePath.ROOT)
                        .resolve("data")
                        .resolve("pocket-repose")
                        .resolve("pending_structures");
                Files.createDirectories(structureMarkerPath);
                Files.writeString(structureMarkerPath.resolve(dimensionName + ".txt"), "pending");
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