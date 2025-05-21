package net.bennyboops.modid.block;

import net.bennyboops.modid.PocketRepose;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

public class ModBlocks {
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
                    Block.createCuboidShape(0, 0, 0, 16, 15, 16),
                    SoundEvents.BLOCK_BARREL_OPEN,
                    SoundEvents.BLOCK_BARREL_CLOSE
            )
    );

    public static final Block PORTAL = registerBlock("portal",
            new PocketPortalBlock(FabricBlockSettings.copyOf(Blocks.NETHER_PORTAL)
                    .sounds(BlockSoundGroup.LODESTONE)
                    .nonOpaque()
                    .luminance(state -> 10)
                    .strength(-1f)));

    private static Block registerBlock(String name, Block block) {
        registerBlockItem(name, block);
        return Registry.register(Registries.BLOCK, Identifier.of(PocketRepose.MOD_ID, name), block);

    }
    private static Item registerBlockItem(String name, Block block) {
        return Registry.register(Registries.ITEM, Identifier.of(PocketRepose.MOD_ID, name),
                new BlockItem(block, new FabricItemSettings()));
    }
    public static void registerModBlocks() {
        PocketRepose.LOGGER.info("Registering mod blocks for" + PocketRepose.MOD_ID);
    }
}