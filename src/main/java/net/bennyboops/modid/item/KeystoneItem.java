package net.bennyboops.modid.item;

import net.bennyboops.modid.PocketRepose;
import net.bennyboops.modid.block.ModBlocks;
import net.bennyboops.modid.data.MobEntryData;
import net.bennyboops.modid.world.Fantasy;
import net.bennyboops.modid.world.PortalChunkGenerator;
import net.bennyboops.modid.world.RuntimeWorldConfig;
import net.bennyboops.modid.world.RuntimeWorldHandle;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class KeystoneItem extends Item {

    private static final ResourceLocation POCKET_DIMENSION_TYPE_ID =
            ResourceLocation.fromNamespaceAndPath("pocket-repose", "pocket_dimension_type");

    public KeystoneItem(Properties settings) { super(settings); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world,
                                                  Player player,
                                                  InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        String rawName = ChatFormatting.stripFormatting(stack.getHoverName().getString());
        String keystoneName = rawName == null ? "" : rawName.toLowerCase(Locale.ROOT);
        if (!stack.has(DataComponents.CUSTOM_NAME)
                || keystoneName.equals("item.pocket-repose.keystone")) {
            return InteractionResultHolder.pass(stack);
        }

        if (world.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        String dimensionName = "pocket_dimension_" +
                keystoneName.replaceAll("[^a-z0-9_]", "");
        createOrLoadPersistentDimension(world.getServer(), dimensionName);

        if (!stack.isEnchanted()) {
            Holder<Enchantment> bindingCurse =
                    world.getServer().registryAccess()
                            .registryOrThrow(Registries.ENCHANTMENT)
                            .getHolderOrThrow(Enchantments.BINDING_CURSE);

            stack.enchant(bindingCurse, 1);
        }

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_CLUSTER_FALL,
                SoundSource.PLAYERS, 2f, 2f);

        return InteractionResultHolder.success(stack);
    }

    private void createOrLoadPersistentDimension(MinecraftServer server,
                                                 String dimensionName) {

        ResourceLocation worldId = ResourceLocation.fromNamespaceAndPath("pocket-repose", dimensionName);
        Path dimPath = server.getWorldPath(LevelResource.ROOT)
                .resolve("dimensions")
                .resolve("pocket-repose")
                .resolve(dimensionName);
        boolean exists = Files.exists(dimPath);

        ResourceKey<DimensionType> typeKey =
                ResourceKey.create(Registries.DIMENSION_TYPE, POCKET_DIMENSION_TYPE_ID);

        Registry<Biome> biomeRegistry =
                server.registryAccess().registryOrThrow(Registries.BIOME);

        ChunkGenerator generator = new PortalChunkGenerator(biomeRegistry);
        long seed = server.overworld().getSeed();

        RuntimeWorldConfig cfg = new RuntimeWorldConfig()
                .setDimensionType(typeKey)
                .setGenerator(generator)
                .setSeed(seed);

        RuntimeWorldHandle handle =
                Fantasy.get(server).getOrOpenPersistentWorld(worldId, cfg);

        registerDimension(server, dimensionName);

        if (!exists) {
            if (PocketRepose.getSpawnIsland()) {
                placeStructureImmediately(server, handle.asWorld(), dimensionName);
                PocketRepose.LOGGER.info("Created new pocket dimension {} with island structure", dimensionName);
            } else {
                placeGrassCube(handle.asWorld());
                PocketRepose.LOGGER.info("Created new pocket dimension {} with grass cube", dimensionName);
            }
        }
    }

    private void placeGrassCube(ServerLevel world) {
        BlockPos spawnPos = new BlockPos(17, 97, 9);

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos grassPos = spawnPos.offset(x, y - 2, z);
                    world.setBlock(grassPos, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }

        BlockPos portalPos = new BlockPos(17, 100, 9);
        world.setBlock(portalPos, ModBlocks.PORTAL.get().defaultBlockState(), Block.UPDATE_ALL);

        MobEntryData mobData = MobEntryData.get(world);
        mobData.setEntry(new Vec3(17.5, 97.0, 9.5), 0f, 0f);

        PocketRepose.LOGGER.info("Placed cube, portal, and set mob entry at spawn location");
    }

    private void placeStructureImmediately(MinecraftServer server,
                                           ServerLevel world,
                                           String dimensionName) {
        ResourceLocation structureId = ResourceLocation.fromNamespaceAndPath("pocket-repose", "pocket_island_01");

        PocketRepose.LOGGER.info("Looking for structure: {}", structureId);

        var templateOpt = server.getStructureManager().get(structureId);

        if (templateOpt.isEmpty()) {
            PocketRepose.LOGGER.error("Structure NOT found, attempting direct file load");

            try {
                String resourcePath = "/data/pocket-repose/structure/pocket_island_01.nbt";
                var inputStream = getClass().getResourceAsStream(resourcePath);

                if (inputStream != null) {
                    PocketRepose.LOGGER.info("Found structure in mod resources");

                    CompoundTag nbt = NbtIo.readCompressed(inputStream, NbtAccounter.unlimitedHeap());
                    StructureTemplate template = new StructureTemplate();

                    var blockLookup = server.registryAccess().lookupOrThrow(Registries.BLOCK);
                    template.load(blockLookup, nbt);

                    BlockPos origin = new BlockPos(0, 64, 0);
                    world.getChunk(origin);

                    template.placeInWorld(world, origin, origin,
                            new StructurePlaceSettings()
                                    .setMirror(Mirror.NONE)
                                    .setRotation(Rotation.NONE)
                                    .setIgnoreEntities(false),
                            world.getRandom(),
                            Block.UPDATE_ALL);

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

        template.placeInWorld(world, origin, origin,
                new StructurePlaceSettings()
                        .setMirror(Mirror.NONE)
                        .setRotation(Rotation.NONE)
                        .setIgnoreEntities(false),
                world.getRandom(),
                Block.UPDATE_ALL);

        PocketRepose.LOGGER.info("Structure placed successfully");
    }

    public static boolean isValidKeystone(ItemStack stack) {
        String stripped = ChatFormatting.stripFormatting(stack.getHoverName().getString());
        String cleanedName = stripped == null ? "" : stripped.toLowerCase(Locale.ROOT);

        boolean hasCustomName = stack.has(DataComponents.CUSTOM_NAME)
                || (stack.getFrame() != null
                && stack.getFrame().hasCustomName());

        boolean isDefault = cleanedName.equals("item.pocket-repose.keystone");

        return hasCustomName && !isDefault && stack.isEnchanted();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag type) {
        boolean hasCustomName = stack.has(DataComponents.CUSTOM_NAME);
        String stripped = ChatFormatting.stripFormatting(stack.getHoverName().getString());
        boolean isDefault = stripped != null
                && stripped.equalsIgnoreCase("item.pocket-repose.keystone");

        if (!hasCustomName || isDefault) {
            tooltip.add(Component.literal("Rename to bind")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }

    }

    @Override
    public void inventoryTick(ItemStack stack, Level world,
                              Entity entity, int slot, boolean selected) {
        ItemFrame frame = stack.getFrame();

        boolean hasCustomName = stack.has(DataComponents.CUSTOM_NAME);

        String displayName = stack.getHoverName().getString();
        boolean isDefaultName = displayName.equalsIgnoreCase("item.pocket-repose.keystone")
                || displayName.equalsIgnoreCase("Keystone");

        boolean shouldShowBroken = !hasCustomName || isDefaultName;

        if (frame != null) {
            shouldShowBroken = !frame.hasCustomName();
        }

        if (shouldShowBroken) {
            CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
            if (cmd == null || cmd.value() != 1) {
                stack.set(DataComponents.CUSTOM_MODEL_DATA,
                        new CustomModelData(1));
            }
        } else {
            stack.remove(DataComponents.CUSTOM_MODEL_DATA);
        }

        if (!world.isClientSide && stack.isEnchanted()) {
            int cost = stack.getOrDefault(DataComponents.REPAIR_COST, 0);
            if (cost < 32_767) {
                stack.set(DataComponents.REPAIR_COST, 32_767);
            }

            ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
            if (enchantments != null) {
                stack.set(DataComponents.ENCHANTMENTS, enchantments.withTooltip(false));
            }
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.isEnchanted();
    }

    private void registerDimension(MinecraftServer server,
                                   String dimensionName) {
        Path dir = server.getWorldPath(LevelResource.ROOT)
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
