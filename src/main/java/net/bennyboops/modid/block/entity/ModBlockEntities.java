package net.bennyboops.modid.block.entity;

import net.bennyboops.modid.PocketRepose;
import net.bennyboops.modid.block.ModBlocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraft.util.Identifier;

public class ModBlockEntities {

    public static BlockEntityType<SuitcaseBlockEntity> SUITCASE_BLOCK_ENTITY;

    public static void registerBlockEntities(RegisterEvent.RegisterHelper<BlockEntityType<?>> helper) {
        SUITCASE_BLOCK_ENTITY = BlockEntityType.Builder.create(
                            SuitcaseBlockEntity::new,

                            ModBlocks.SUITCASE,
                            ModBlocks.WHITE_SUITCASE,
                            ModBlocks.BLACK_SUITCASE,
                            ModBlocks.LIGHT_GRAY_SUITCASE,
                            ModBlocks.GRAY_SUITCASE,
                            ModBlocks.RED_SUITCASE,
                            ModBlocks.ORANGE_SUITCASE,
                            ModBlocks.YELLOW_SUITCASE,
                            ModBlocks.LIME_SUITCASE,
                            ModBlocks.GREEN_SUITCASE,
                            ModBlocks.CYAN_SUITCASE,
                            ModBlocks.LIGHT_BLUE_SUITCASE,
                            ModBlocks.BLUE_SUITCASE,
                            ModBlocks.PURPLE_SUITCASE,
                            ModBlocks.MAGENTA_SUITCASE,
                            ModBlocks.PINK_SUITCASE,
                            ModBlocks.SECRET_BARREL,
                            ModBlocks.SECRET_CHEST,
                            ModBlocks.OAK_POCKET_DOOR,
                            ModBlocks.SPRUCE_POCKET_DOOR,
                            ModBlocks.BIRCH_POCKET_DOOR,
                            ModBlocks.JUNGLE_POCKET_DOOR,
                            ModBlocks.ACACIA_POCKET_DOOR,
                            ModBlocks.DARK_OAK_POCKET_DOOR,
                            ModBlocks.MANGROVE_POCKET_DOOR,
                            ModBlocks.CHERRY_POCKET_DOOR,
                            ModBlocks.BAMBOO_POCKET_DOOR,
                            ModBlocks.CRIMSON_POCKET_DOOR,
                            ModBlocks.WARPED_POCKET_DOOR
                    ).build(null);
        helper.register(Identifier.of(PocketRepose.MOD_ID, "suitcase"), SUITCASE_BLOCK_ENTITY);
    }
}