package net.bennyboops.modid.block;

import net.bennyboops.modid.PocketRepose;
import net.bennyboops.modid.item.SecretChestItem;
import net.minecraft.block.AbstractBlock;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSetType;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.TallBlockItem;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.shape.VoxelShapes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class ModBlocks {
    public static Block SUITCASE;
    public static Block WHITE_SUITCASE;
    public static Block BLACK_SUITCASE;
    public static Block LIGHT_GRAY_SUITCASE;
    public static Block GRAY_SUITCASE;
    public static Block RED_SUITCASE;
    public static Block ORANGE_SUITCASE;
    public static Block YELLOW_SUITCASE;
    public static Block LIME_SUITCASE;
    public static Block GREEN_SUITCASE;
    public static Block CYAN_SUITCASE;
    public static Block LIGHT_BLUE_SUITCASE;
    public static Block BLUE_SUITCASE;
    public static Block PURPLE_SUITCASE;
    public static Block MAGENTA_SUITCASE;
    public static Block PINK_SUITCASE;
    public static Block SECRET_BARREL;
    public static Block SECRET_CHEST;
    public static Block PORTAL;

    // Pocket doors (one for every wood door), filled in as they're registered below
    public static final List<Block> POCKET_DOORS = new ArrayList<>();
    // Exit portal doors (one for every wood door): pocket door looks, portal block behavior
    public static final List<Block> EXIT_DOORS = new ArrayList<>();

    public static Block OAK_POCKET_DOOR;
    public static Block SPRUCE_POCKET_DOOR;
    public static Block BIRCH_POCKET_DOOR;
    public static Block JUNGLE_POCKET_DOOR;
    public static Block ACACIA_POCKET_DOOR;
    public static Block DARK_OAK_POCKET_DOOR;
    public static Block MANGROVE_POCKET_DOOR;
    public static Block CHERRY_POCKET_DOOR;
    public static Block BAMBOO_POCKET_DOOR;
    public static Block CRIMSON_POCKET_DOOR;
    public static Block WARPED_POCKET_DOOR;

    public static Block OAK_EXIT_PORTAL_DOOR;
    public static Block SPRUCE_EXIT_PORTAL_DOOR;
    public static Block BIRCH_EXIT_PORTAL_DOOR;
    public static Block JUNGLE_EXIT_PORTAL_DOOR;
    public static Block ACACIA_EXIT_PORTAL_DOOR;
    public static Block DARK_OAK_EXIT_PORTAL_DOOR;
    public static Block MANGROVE_EXIT_PORTAL_DOOR;
    public static Block CHERRY_EXIT_PORTAL_DOOR;
    public static Block BAMBOO_EXIT_PORTAL_DOOR;
    public static Block CRIMSON_EXIT_PORTAL_DOOR;
    public static Block WARPED_EXIT_PORTAL_DOOR;

    // The item each block gets, created once the item registry opens
    private static final Map<Identifier, Function<Block, Item>> BLOCK_ITEMS = new LinkedHashMap<>();
    private static final Map<Identifier, Block> BLOCKS_WITH_ITEMS = new LinkedHashMap<>();
    private static RegisterEvent.RegisterHelper<Block> blockHelper;

    // Forge only allows blocks to be created while the block registry is open, so this runs from RegisterEvent
    public static void registerBlocks(RegisterEvent.RegisterHelper<Block> helper) {
        blockHelper = helper;
        SUITCASE = registerBlock("suitcase",
                new SuitcaseBlock(AbstractBlock.Settings.copy(Blocks.BROWN_WOOL)
                        .sounds(BlockSoundGroup.WOOL)
                        .strength(0.2f)
                        .nonOpaque()
                        .luminance(state -> state.get(SuitcaseBlock.OPEN) ? 8 : 0)));
        WHITE_SUITCASE = registerBlock("white_suitcase",
                new SuitcaseBlock(AbstractBlock.Settings.copy(Blocks.WHITE_WOOL).sounds(BlockSoundGroup.WOOL).strength(0.2f).nonOpaque().luminance(state -> state.get(SuitcaseBlock.OPEN) ? 8 : 0)));
        BLACK_SUITCASE = registerBlock("black_suitcase",
                new SuitcaseBlock(AbstractBlock.Settings.copy(Blocks.BLACK_WOOL).sounds(BlockSoundGroup.WOOL).strength(0.2f).nonOpaque().luminance(state -> state.get(SuitcaseBlock.OPEN) ? 8 : 0)));
        LIGHT_GRAY_SUITCASE = registerBlock("light_gray_suitcase",
                new SuitcaseBlock(AbstractBlock.Settings.copy(Blocks.LIGHT_GRAY_WOOL).sounds(BlockSoundGroup.WOOL).strength(0.2f).nonOpaque().luminance(state -> state.get(SuitcaseBlock.OPEN) ? 8 : 0)));
        GRAY_SUITCASE = registerBlock("gray_suitcase",
                new SuitcaseBlock(AbstractBlock.Settings.copy(Blocks.GRAY_WOOL).sounds(BlockSoundGroup.WOOL).strength(0.2f).nonOpaque().luminance(state -> state.get(SuitcaseBlock.OPEN) ? 8 : 0)));
        RED_SUITCASE = registerBlock("red_suitcase",
                new SuitcaseBlock(AbstractBlock.Settings.copy(Blocks.RED_WOOL).sounds(BlockSoundGroup.WOOL).strength(0.2f).nonOpaque().luminance(state -> state.get(SuitcaseBlock.OPEN) ? 8 : 0)));
        ORANGE_SUITCASE = registerBlock("orange_suitcase",
                new SuitcaseBlock(AbstractBlock.Settings.copy(Blocks.ORANGE_WOOL).sounds(BlockSoundGroup.WOOL).strength(0.2f).nonOpaque().luminance(state -> state.get(SuitcaseBlock.OPEN) ? 8 : 0)));
        YELLOW_SUITCASE = registerBlock("yellow_suitcase",
                new SuitcaseBlock(AbstractBlock.Settings.copy(Blocks.YELLOW_WOOL).sounds(BlockSoundGroup.WOOL).strength(0.2f).nonOpaque().luminance(state -> state.get(SuitcaseBlock.OPEN) ? 8 : 0)));
        LIME_SUITCASE = registerBlock("lime_suitcase",
                new SuitcaseBlock(AbstractBlock.Settings.copy(Blocks.LIME_WOOL).sounds(BlockSoundGroup.WOOL).strength(0.2f).nonOpaque().luminance(state -> state.get(SuitcaseBlock.OPEN) ? 8 : 0)));
        GREEN_SUITCASE = registerBlock("green_suitcase",
                new SuitcaseBlock(AbstractBlock.Settings.copy(Blocks.GREEN_WOOL).sounds(BlockSoundGroup.WOOL).strength(0.2f).nonOpaque().luminance(state -> state.get(SuitcaseBlock.OPEN) ? 8 : 0)));
        CYAN_SUITCASE = registerBlock("cyan_suitcase",
                new SuitcaseBlock(AbstractBlock.Settings.copy(Blocks.CYAN_WOOL).sounds(BlockSoundGroup.WOOL).strength(0.2f).nonOpaque().luminance(state -> state.get(SuitcaseBlock.OPEN) ? 8 : 0)));
        LIGHT_BLUE_SUITCASE = registerBlock("light_blue_suitcase",
                new SuitcaseBlock(AbstractBlock.Settings.copy(Blocks.LIGHT_BLUE_WOOL).sounds(BlockSoundGroup.WOOL).strength(0.2f).nonOpaque().luminance(state -> state.get(SuitcaseBlock.OPEN) ? 8 : 0)));
        BLUE_SUITCASE = registerBlock("blue_suitcase",
                new SuitcaseBlock(AbstractBlock.Settings.copy(Blocks.BLUE_WOOL).sounds(BlockSoundGroup.WOOL).strength(0.2f).nonOpaque().luminance(state -> state.get(SuitcaseBlock.OPEN) ? 8 : 0)));
        PURPLE_SUITCASE = registerBlock("purple_suitcase",
                new SuitcaseBlock(AbstractBlock.Settings.copy(Blocks.PURPLE_WOOL).sounds(BlockSoundGroup.WOOL).strength(0.2f).nonOpaque().luminance(state -> state.get(SuitcaseBlock.OPEN) ? 8 : 0)));
        MAGENTA_SUITCASE = registerBlock("magenta_suitcase",
                new SuitcaseBlock(AbstractBlock.Settings.copy(Blocks.MAGENTA_WOOL).sounds(BlockSoundGroup.WOOL).strength(0.2f).nonOpaque().luminance(state -> state.get(SuitcaseBlock.OPEN) ? 8 : 0)));
        PINK_SUITCASE = registerBlock("pink_suitcase",
                new SuitcaseBlock(AbstractBlock.Settings.copy(Blocks.PINK_WOOL).sounds(BlockSoundGroup.WOOL).strength(0.2f).nonOpaque().luminance(state -> state.get(SuitcaseBlock.OPEN) ? 8 : 0)));

        SECRET_BARREL = registerBlock("secret_barrel",
                new CustomSuitcaseBlock(
                        AbstractBlock.Settings.copy(Blocks.BARREL)
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

        // Its item is drawn with the chest model, like the vanilla chest item
        SECRET_CHEST = registerBlock("secret_chest",
                new SecretChestBlock(AbstractBlock.Settings.copy(Blocks.CHEST)
                        .sounds(BlockSoundGroup.WOOD)
                        .strength(1.0f)
                        .nonOpaque()
                        .luminance(state -> state.get(SuitcaseBlock.OPEN) ? 8 : 0)),
                block -> new SecretChestItem(block, new Item.Settings()));

        OAK_POCKET_DOOR = registerPocketDoor("oak", Blocks.OAK_DOOR, BlockSetType.OAK);
        SPRUCE_POCKET_DOOR = registerPocketDoor("spruce", Blocks.SPRUCE_DOOR, BlockSetType.SPRUCE);
        BIRCH_POCKET_DOOR = registerPocketDoor("birch", Blocks.BIRCH_DOOR, BlockSetType.BIRCH);
        JUNGLE_POCKET_DOOR = registerPocketDoor("jungle", Blocks.JUNGLE_DOOR, BlockSetType.JUNGLE);
        ACACIA_POCKET_DOOR = registerPocketDoor("acacia", Blocks.ACACIA_DOOR, BlockSetType.ACACIA);
        DARK_OAK_POCKET_DOOR = registerPocketDoor("dark_oak", Blocks.DARK_OAK_DOOR, BlockSetType.DARK_OAK);
        MANGROVE_POCKET_DOOR = registerPocketDoor("mangrove", Blocks.MANGROVE_DOOR, BlockSetType.MANGROVE);
        CHERRY_POCKET_DOOR = registerPocketDoor("cherry", Blocks.CHERRY_DOOR, BlockSetType.CHERRY);
        BAMBOO_POCKET_DOOR = registerPocketDoor("bamboo", Blocks.BAMBOO_DOOR, BlockSetType.BAMBOO);
        CRIMSON_POCKET_DOOR = registerPocketDoor("crimson", Blocks.CRIMSON_DOOR, BlockSetType.CRIMSON);
        WARPED_POCKET_DOOR = registerPocketDoor("warped", Blocks.WARPED_DOOR, BlockSetType.WARPED);

        OAK_EXIT_PORTAL_DOOR = registerExitDoor("oak", Blocks.OAK_DOOR, BlockSetType.OAK);
        SPRUCE_EXIT_PORTAL_DOOR = registerExitDoor("spruce", Blocks.SPRUCE_DOOR, BlockSetType.SPRUCE);
        BIRCH_EXIT_PORTAL_DOOR = registerExitDoor("birch", Blocks.BIRCH_DOOR, BlockSetType.BIRCH);
        JUNGLE_EXIT_PORTAL_DOOR = registerExitDoor("jungle", Blocks.JUNGLE_DOOR, BlockSetType.JUNGLE);
        ACACIA_EXIT_PORTAL_DOOR = registerExitDoor("acacia", Blocks.ACACIA_DOOR, BlockSetType.ACACIA);
        DARK_OAK_EXIT_PORTAL_DOOR = registerExitDoor("dark_oak", Blocks.DARK_OAK_DOOR, BlockSetType.DARK_OAK);
        MANGROVE_EXIT_PORTAL_DOOR = registerExitDoor("mangrove", Blocks.MANGROVE_DOOR, BlockSetType.MANGROVE);
        CHERRY_EXIT_PORTAL_DOOR = registerExitDoor("cherry", Blocks.CHERRY_DOOR, BlockSetType.CHERRY);
        BAMBOO_EXIT_PORTAL_DOOR = registerExitDoor("bamboo", Blocks.BAMBOO_DOOR, BlockSetType.BAMBOO);
        CRIMSON_EXIT_PORTAL_DOOR = registerExitDoor("crimson", Blocks.CRIMSON_DOOR, BlockSetType.CRIMSON);
        WARPED_EXIT_PORTAL_DOOR = registerExitDoor("warped", Blocks.WARPED_DOOR, BlockSetType.WARPED);

        PORTAL = registerBlock("portal",
                new PocketPortalBlock(AbstractBlock.Settings.copy(Blocks.NETHER_PORTAL)
                        .sounds(BlockSoundGroup.LODESTONE)
                        .nonOpaque()
                        .luminance(state -> 10)
                        .strength(5.0f)));
        blockHelper = null;
    }

    public static void registerBlockItems(RegisterEvent.RegisterHelper<Item> helper) {
        BLOCK_ITEMS.forEach((id, item) -> helper.register(id, item.apply(BLOCKS_WITH_ITEMS.get(id))));
    }

    private static Block registerPocketDoor(String wood, Block door, BlockSetType type) {
        Block block = registerBlock(wood + "_pocket_door", new PocketDoorBlock(type, AbstractBlock.Settings.copy(door)
                .strength(1.0f)
                .luminance(state -> state.get(DoorBlock.OPEN) ? 8 : 0)),
                b -> new TallBlockItem(b, new Item.Settings()));
        POCKET_DOORS.add(block);
        return block;
    }

    private static Block registerExitDoor(String wood, Block door, BlockSetType type) {
        // Break time of the portal block (its sounds are set in ExitDoorBlock)
        Block block = registerBlock(wood + "_exit_portal_door", new ExitDoorBlock(type, AbstractBlock.Settings.copy(door)
                .strength(5.0f)
                .luminance(state -> state.get(DoorBlock.OPEN) ? 8 : 0)),
                b -> new TallBlockItem(b, new Item.Settings()));
        EXIT_DOORS.add(block);
        return block;
    }

    private static Block registerBlock(String name, Block block) {
        return registerBlock(name, block, b -> new BlockItem(b, new Item.Settings()));
    }

    private static Block registerBlock(String name, Block block, Function<Block, Item> item) {
        Identifier id = new Identifier(PocketRepose.MOD_ID, name);
        blockHelper.register(id, block);
        BLOCK_ITEMS.put(id, item);
        BLOCKS_WITH_ITEMS.put(id, block);
        return block;
    }
}
