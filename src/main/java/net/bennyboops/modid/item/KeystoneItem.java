package net.bennyboops.modid.item;

import net.bennyboops.modid.PocketRepose;
import net.bennyboops.modid.world.PortalChunkGenerator;
import net.minecraft.block.Block;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
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

    private static final Identifier POCKET_DIMENSION_TYPE_ID = Identifier.of("pocket-repose", "pocket_dimension_type");

    public KeystoneItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        String keystoneName = stack.getName().getString().toLowerCase(Locale.ROOT);
        String defaultName = "item.pocket-repose.keystone";
        boolean hasCustomName = stack.contains(DataComponentTypes.CUSTOM_NAME);
        if (!hasCustomName || keystoneName.equals(defaultName)) {
            return TypedActionResult.pass(stack);
        }
        if (world.isClient) {
            return TypedActionResult.success(stack);
        }
        if (stack.hasEnchantments()) {
            return TypedActionResult.pass(stack);
        }
        String dimensionName = "pocket_dimension_" + keystoneName.replaceAll("[^a-z0-9_]", "");

        createOrLoadPersistentDimension(world.getServer(), dimensionName);

        MinecraftServer server = world.getServer();
        RegistryEntry<Enchantment> bindingCurse =
                server.getRegistryManager().get(RegistryKeys.ENCHANTMENT).entryOf(Enchantments.BINDING_CURSE);

        stack.addEnchantment(bindingCurse, 1);
        stack.set(DataComponentTypes.REPAIR_COST, 32_767);

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_AMETHYST_CLUSTER_FALL, SoundCategory.PLAYERS,
                2.0F, 2.0F);
        return TypedActionResult.success(stack);
    }


    private void createOrLoadPersistentDimension(MinecraftServer server, String dimensionName) {
        Identifier worldId = Identifier.of("pocket-repose", dimensionName);

        Path worldSavePath = server.getSavePath(WorldSavePath.ROOT)
                .resolve("dimensions")
                .resolve("pocket-repose")
                .resolve(dimensionName);
        boolean dimensionExists = Files.exists(worldSavePath);

        RegistryKey<DimensionType> typeKey = RegistryKey.of(RegistryKeys.DIMENSION_TYPE, POCKET_DIMENSION_TYPE_ID);

        Registry<Biome> biomeRegistry = server.getRegistryManager().get(RegistryKeys.BIOME);

        //RegistryKey<Biome> voidBiomeKey = RegistryKey.of(RegistryKeys.BIOME, new Identifier("minecraft", "the_void"));
        RegistryKey<Biome> voidBiomeKey = RegistryKey.of(RegistryKeys.BIOME, Identifier.of("pocket-repose", "pocket_islands"));

        ChunkGenerator generator = new PortalChunkGenerator(biomeRegistry);

        long seed = server.getOverworld().getSeed();

        RuntimeWorldConfig config = new RuntimeWorldConfig()
                .setDimensionType(typeKey)
                .setGenerator(generator)
                .setSeed(seed);

        RuntimeWorldHandle handle = Fantasy.get(server)
                .getOrOpenPersistentWorld(worldId, config);

        registerDimension(server, dimensionName);

        if (!dimensionExists) {
            ServerWorld world = handle.asWorld();
            placeStructureImmediately(server, world, dimensionName);
            System.out.println("Created new dimension with structure: " + dimensionName);
        } else {
            System.out.println("Loaded existing dimension: " + dimensionName);
        }
    }

    private void placeStructureImmediately(MinecraftServer server, ServerWorld world, String dimensionName) {
        try {
            StructureTemplate template = server.getStructureTemplateManager()
                    .getTemplate(Identifier.of("pocket-repose", "pocket_island_01"))
                    .orElse(null);

            if (template != null) {
                BlockPos pos = new BlockPos(0, 64, 0);

                world.getChunk(pos);

                template.place(
                        world,
                        pos,
                        pos,
                        new StructurePlacementData()
                                .setMirror(BlockMirror.NONE)
                                .setRotation(BlockRotation.NONE)
                                .setIgnoreEntities(false),
                        world.getRandom(),
                        Block.NOTIFY_LISTENERS | Block.FORCE_STATE
                );

                System.out.println("Immediately placed pocket island structure in new dimension: " + dimensionName);
            } else {
                System.err.println("Could not find structure template: pocket_island_01");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Path getStructureMarkerPath(MinecraftServer server, String dimensionName) {
        return server.getSavePath(WorldSavePath.ROOT)
                .resolve("data")
                .resolve("pocket-repose")
                .resolve("pending_structures")
                .resolve(dimensionName + ".txt");
    }

    public static boolean isValidKeystone(ItemStack stack) {
        String keystoneName = stack.getName().getString().toLowerCase();
        return stack.getFrame().hasCustomName() &&
                !keystoneName.equals("item.pocket-repose.keystone") &&
                stack.hasEnchantments();
    }

    public void appendTooltip(ItemStack stack, @Nullable World world, java.util.List<Text> tooltip, TooltipContext context) {
        if (!stack.getFrame().hasCustomName() ||
                stack.getName().getString().toLowerCase().equals("item.pocket-repose.keystone")) {
            tooltip.add(Text.literal("§7Rename to bind").formatted(Formatting.ITALIC));
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, World world,
                              Entity entity, int slot, boolean selected) {
        ItemFrameEntity frame   = stack.getFrame();
        String          rawName = stack.getName().getString();
        boolean hasDefaultItemName =
                rawName.equalsIgnoreCase("item.pocket-repose.keystone");
        boolean shouldForceModel =
                (frame != null && !frame.hasCustomName())
                        || (frame == null && hasDefaultItemName);
        if (shouldForceModel) {
            CustomModelDataComponent current =
                    stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
            if (current == null || current.value() != 1) {
                stack.set(DataComponentTypes.CUSTOM_MODEL_DATA,
                        new CustomModelDataComponent(1));
            }
        } else {
            stack.remove(DataComponentTypes.CUSTOM_MODEL_DATA);
        }
        if (!world.isClient && stack.hasEnchantments()) {
            int cost = stack.getOrDefault(DataComponentTypes.REPAIR_COST, 0);
            if (cost < 32767) {
                stack.set(DataComponentTypes.REPAIR_COST, 32767);
            }
        }
    }



    private void registerDimension(MinecraftServer server, String dimensionName) {
        Path registryDir = server.getSavePath(WorldSavePath.ROOT)
                .resolve("data")
                .resolve("pocket-repose")
                .resolve("dimension_registry");

        try {
            Files.createDirectories(registryDir);
            Path registryFile = registryDir.resolve("registry.txt");

            // load existing lines
            Set<String> dims = new HashSet<>();
            if (Files.exists(registryFile)) {
                dims.addAll(Files.readAllLines(registryFile));
            }

            // add + save only if new
            if (dims.add(dimensionName)) {
                Files.write(registryFile, dims);
            }
        } catch (IOException e) {
            PocketRepose.LOGGER.error("Failed to write dimension registry", e);
        }
    }
}