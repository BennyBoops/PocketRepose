package net.bennyboops.modid.block;

import net.bennyboops.modid.PocketRepose;
import net.minecraft.block.AbstractBlock;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

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
    public static Block PORTAL;

    private static final Map<Identifier, Block> BLOCK_ITEMS = new LinkedHashMap<>();
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
                        Block.createCuboidShape(0, 0, 0, 16, 15, 16),
                        SoundEvents.BLOCK_BARREL_OPEN,
                        SoundEvents.BLOCK_BARREL_CLOSE
                )
        );
        PORTAL = registerBlock("portal",
                new PocketPortalBlock(AbstractBlock.Settings.copy(Blocks.NETHER_PORTAL)
                        .sounds(BlockSoundGroup.LODESTONE)
                        .nonOpaque()
                        .luminance(state -> 10)
                        .strength(5.0f)));
        blockHelper = null;
    }

    public static void registerBlockItems(RegisterEvent.RegisterHelper<Item> helper) {
        BLOCK_ITEMS.forEach((id, block) -> helper.register(id, new BlockItem(block, new Item.Settings())));
    }

    private static Block registerBlock(String name, Block block) {
        Identifier id = Identifier.of(PocketRepose.MOD_ID, name);
        blockHelper.register(id, block);
        BLOCK_ITEMS.put(id, block);
        return block;
    }
}
