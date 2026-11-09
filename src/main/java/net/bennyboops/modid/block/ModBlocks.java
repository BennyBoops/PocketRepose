package net.bennyboops.modid.block;

import net.minecraft.util.shape.VoxelShapes;
import net.bennyboops.modid.PocketRepose;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSetType;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.TallBlockItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class ModBlocks {
    // Pocket doors (one for every wood door), filled in as they're registered below
    public static final List<Block> POCKET_DOORS = new ArrayList<>();
    // Exit portal doors (one for every wood door): pocket door looks, portal block behavior
    public static final List<Block> EXIT_DOORS = new ArrayList<>();

    public static final Block SUITCASE = registerBlock("suitcase",
            new SuitcaseBlock(FabricBlockSettings.copyOf(Blocks.BROWN_WOOL)
                    .sounds(BlockSoundGroup.WOOL)
                    .strength(0.2f)
                    .nonOpaque()
                    .luminance(state -> state.get(SuitcaseBlock.OPEN) ? 8 : 0)));
    public static final Block WHITE_SUITCASE = registerBlock("white_suitcase",
            new SuitcaseBlock(FabricBlockSettings.copyOf(Blocks.WHITE_WOOL).sounds(BlockSoundGroup.WOOL).strength(0.2f).nonOpaque().luminance(state -> state.get(SuitcaseBlock.OPEN) ? 8 : 0)));
    public static final Block BLACK_SUITCASE = registerBlock("black_suitcase",
            new SuitcaseBlock(FabricBlockSettings.copyOf(Blocks.BLACK_WOOL).sounds(BlockSoundGroup.WOOL).strength(0.2f).nonOpaque().luminance(state -> state.get(SuitcaseBlock.OPEN) ? 8 : 0)));
    public static final Block LIGHT_GRAY_SUITCASE = registerBlock("light_gray_suitcase",
            new SuitcaseBlock(FabricBlockSettings.copyOf(Blocks.LIGHT_GRAY_WOOL).sounds(BlockSoundGroup.WOOL).strength(0.2f).nonOpaque().luminance(state -> state.get(SuitcaseBlock.OPEN) ? 8 : 0)));
    public static final Block GRAY_SUITCASE = registerBlock("gray_suitcase",
            new SuitcaseBlock(FabricBlockSettings.copyOf(Blocks.GRAY_WOOL).sounds(BlockSoundGroup.WOOL).strength(0.2f).nonOpaque().luminance(state -> state.get(SuitcaseBlock.OPEN) ? 8 : 0)));
    public static final Block RED_SUITCASE = registerBlock("red_suitcase",
            new SuitcaseBlock(FabricBlockSettings.copyOf(Blocks.RED_WOOL).sounds(BlockSoundGroup.WOOL).strength(0.2f).nonOpaque().luminance(state -> state.get(SuitcaseBlock.OPEN) ? 8 : 0)));
    public static final Block ORANGE_SUITCASE = registerBlock("orange_suitcase",
            new SuitcaseBlock(FabricBlockSettings.copyOf(Blocks.ORANGE_WOOL).sounds(BlockSoundGroup.WOOL).strength(0.2f).nonOpaque().luminance(state -> state.get(SuitcaseBlock.OPEN) ? 8 : 0)));
    public static final Block YELLOW_SUITCASE = registerBlock("yellow_suitcase",
            new SuitcaseBlock(FabricBlockSettings.copyOf(Blocks.YELLOW_WOOL).sounds(BlockSoundGroup.WOOL).strength(0.2f).nonOpaque().luminance(state -> state.get(SuitcaseBlock.OPEN) ? 8 : 0)));
    public static final Block LIME_SUITCASE = registerBlock("lime_suitcase",
            new SuitcaseBlock(FabricBlockSettings.copyOf(Blocks.LIME_WOOL).sounds(BlockSoundGroup.WOOL).strength(0.2f).nonOpaque().luminance(state -> state.get(SuitcaseBlock.OPEN) ? 8 : 0)));
    public static final Block GREEN_SUITCASE = registerBlock("green_suitcase",
            new SuitcaseBlock(FabricBlockSettings.copyOf(Blocks.GREEN_WOOL).sounds(BlockSoundGroup.WOOL).strength(0.2f).nonOpaque().luminance(state -> state.get(SuitcaseBlock.OPEN) ? 8 : 0)));
    public static final Block CYAN_SUITCASE = registerBlock("cyan_suitcase",
            new SuitcaseBlock(FabricBlockSettings.copyOf(Blocks.CYAN_WOOL).sounds(BlockSoundGroup.WOOL).strength(0.2f).nonOpaque().luminance(state -> state.get(SuitcaseBlock.OPEN) ? 8 : 0)));
    public static final Block LIGHT_BLUE_SUITCASE = registerBlock("light_blue_suitcase",
            new SuitcaseBlock(FabricBlockSettings.copyOf(Blocks.LIGHT_BLUE_WOOL).sounds(BlockSoundGroup.WOOL).strength(0.2f).nonOpaque().luminance(state -> state.get(SuitcaseBlock.OPEN) ? 8 : 0)));
    public static final Block BLUE_SUITCASE = registerBlock("blue_suitcase",
            new SuitcaseBlock(FabricBlockSettings.copyOf(Blocks.BLUE_WOOL).sounds(BlockSoundGroup.WOOL).strength(0.2f).nonOpaque().luminance(state -> state.get(SuitcaseBlock.OPEN) ? 8 : 0)));
    public static final Block PURPLE_SUITCASE = registerBlock("purple_suitcase",
            new SuitcaseBlock(FabricBlockSettings.copyOf(Blocks.PURPLE_WOOL).sounds(BlockSoundGroup.WOOL).strength(0.2f).nonOpaque().luminance(state -> state.get(SuitcaseBlock.OPEN) ? 8 : 0)));
    public static final Block MAGENTA_SUITCASE = registerBlock("magenta_suitcase",
            new SuitcaseBlock(FabricBlockSettings.copyOf(Blocks.MAGENTA_WOOL).sounds(BlockSoundGroup.WOOL).strength(0.2f).nonOpaque().luminance(state -> state.get(SuitcaseBlock.OPEN) ? 8 : 0)));
    public static final Block PINK_SUITCASE = registerBlock("pink_suitcase",
            new SuitcaseBlock(FabricBlockSettings.copyOf(Blocks.PINK_WOOL).sounds(BlockSoundGroup.WOOL).strength(0.2f).nonOpaque().luminance(state -> state.get(SuitcaseBlock.OPEN) ? 8 : 0)));

    public static final Block SECRET_BARREL = registerBlock("secret_barrel",
            new CustomSuitcaseBlock(
                    FabricBlockSettings.copyOf(Blocks.BARREL)
                            .sounds(BlockSoundGroup.WOOD)
                            .strength(1.0f)
                            .nonOpaque(),
                    // Solid one pixel short of the top (so standing on it counts as being in it), outlined as a full block
                    Block.createCuboidShape(0, 0, 0, 16, 15, 16),
                    VoxelShapes.fullCube(),
                    SoundEvents.BLOCK_BARREL_OPEN,
                    SoundEvents.BLOCK_BARREL_CLOSE
            )
    );

    public static final Block SECRET_CHEST = registerBlock("secret_chest",
            new SecretChestBlock(FabricBlockSettings.copyOf(Blocks.CHEST)
                    .sounds(BlockSoundGroup.WOOD)
                    .strength(1.0f)
                    .nonOpaque()
                    .luminance(state -> state.get(SuitcaseBlock.OPEN) ? 8 : 0)));

    public static final Block OAK_POCKET_DOOR = registerPocketDoor("oak", Blocks.OAK_DOOR, BlockSetType.OAK);
    public static final Block SPRUCE_POCKET_DOOR = registerPocketDoor("spruce", Blocks.SPRUCE_DOOR, BlockSetType.SPRUCE);
    public static final Block BIRCH_POCKET_DOOR = registerPocketDoor("birch", Blocks.BIRCH_DOOR, BlockSetType.BIRCH);
    public static final Block JUNGLE_POCKET_DOOR = registerPocketDoor("jungle", Blocks.JUNGLE_DOOR, BlockSetType.JUNGLE);
    public static final Block ACACIA_POCKET_DOOR = registerPocketDoor("acacia", Blocks.ACACIA_DOOR, BlockSetType.ACACIA);
    public static final Block DARK_OAK_POCKET_DOOR = registerPocketDoor("dark_oak", Blocks.DARK_OAK_DOOR, BlockSetType.DARK_OAK);
    public static final Block MANGROVE_POCKET_DOOR = registerPocketDoor("mangrove", Blocks.MANGROVE_DOOR, BlockSetType.MANGROVE);
    public static final Block CHERRY_POCKET_DOOR = registerPocketDoor("cherry", Blocks.CHERRY_DOOR, BlockSetType.CHERRY);
    public static final Block BAMBOO_POCKET_DOOR = registerPocketDoor("bamboo", Blocks.BAMBOO_DOOR, BlockSetType.BAMBOO);
    public static final Block CRIMSON_POCKET_DOOR = registerPocketDoor("crimson", Blocks.CRIMSON_DOOR, BlockSetType.CRIMSON);
    public static final Block WARPED_POCKET_DOOR = registerPocketDoor("warped", Blocks.WARPED_DOOR, BlockSetType.WARPED);

    public static final Block OAK_EXIT_PORTAL_DOOR = registerExitDoor("oak", Blocks.OAK_DOOR, BlockSetType.OAK);
    public static final Block SPRUCE_EXIT_PORTAL_DOOR = registerExitDoor("spruce", Blocks.SPRUCE_DOOR, BlockSetType.SPRUCE);
    public static final Block BIRCH_EXIT_PORTAL_DOOR = registerExitDoor("birch", Blocks.BIRCH_DOOR, BlockSetType.BIRCH);
    public static final Block JUNGLE_EXIT_PORTAL_DOOR = registerExitDoor("jungle", Blocks.JUNGLE_DOOR, BlockSetType.JUNGLE);
    public static final Block ACACIA_EXIT_PORTAL_DOOR = registerExitDoor("acacia", Blocks.ACACIA_DOOR, BlockSetType.ACACIA);
    public static final Block DARK_OAK_EXIT_PORTAL_DOOR = registerExitDoor("dark_oak", Blocks.DARK_OAK_DOOR, BlockSetType.DARK_OAK);
    public static final Block MANGROVE_EXIT_PORTAL_DOOR = registerExitDoor("mangrove", Blocks.MANGROVE_DOOR, BlockSetType.MANGROVE);
    public static final Block CHERRY_EXIT_PORTAL_DOOR = registerExitDoor("cherry", Blocks.CHERRY_DOOR, BlockSetType.CHERRY);
    public static final Block BAMBOO_EXIT_PORTAL_DOOR = registerExitDoor("bamboo", Blocks.BAMBOO_DOOR, BlockSetType.BAMBOO);
    public static final Block CRIMSON_EXIT_PORTAL_DOOR = registerExitDoor("crimson", Blocks.CRIMSON_DOOR, BlockSetType.CRIMSON);
    public static final Block WARPED_EXIT_PORTAL_DOOR = registerExitDoor("warped", Blocks.WARPED_DOOR, BlockSetType.WARPED);

    public static final Block PORTAL = registerBlock("portal",
            new PocketPortalBlock(FabricBlockSettings.copyOf(Blocks.NETHER_PORTAL)
                    .sounds(BlockSoundGroup.LODESTONE)
                    .nonOpaque()
                    .luminance(state -> 10)
                    .strength(5.0f)));

    private static Block registerPocketDoor(String wood, Block door, BlockSetType type) {
        Identifier id = Identifier.of(PocketRepose.MOD_ID, wood + "_pocket_door");
        // No light when open, so the hallway behind the doorway stays dark
        Block block = Registry.register(Registries.BLOCK, id, new PocketDoorBlock(type, AbstractBlock.Settings.copy(door)
                .strength(1.0f)));
        Registry.register(Registries.ITEM, id, new TallBlockItem(block, new Item.Settings()));
        POCKET_DOORS.add(block);
        return block;
    }

    private static Block registerExitDoor(String wood, Block door, BlockSetType type) {
        Identifier id = Identifier.of(PocketRepose.MOD_ID, wood + "_exit_portal_door");
        // Break time of the portal block (its sounds are set in ExitDoorBlock)
        Block block = Registry.register(Registries.BLOCK, id, new ExitDoorBlock(type, AbstractBlock.Settings.copy(door)
                .strength(5.0f)));
        Registry.register(Registries.ITEM, id, new TallBlockItem(block, new Item.Settings()));
        EXIT_DOORS.add(block);
        return block;
    }

    private static Block registerBlock(String name, Block block) {
        registerBlockItem(name, block);
        return Registry.register(Registries.BLOCK, Identifier.of(PocketRepose.MOD_ID, name), block);

    }
    private static Item registerBlockItem(String name, Block block) {
        return Registry.register(Registries.ITEM, Identifier.of(PocketRepose.MOD_ID, name),
                new BlockItem(block, new Item.Settings()));
    }
    public static void registerModBlocks() {
        PocketRepose.LOGGER.info("Registering mod blocks for" + PocketRepose.MOD_ID);
    }
}