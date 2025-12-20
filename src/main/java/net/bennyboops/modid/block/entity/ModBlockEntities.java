package net.bennyboops.modid.block.entity;

import net.bennyboops.modid.PocketRepose;
import net.bennyboops.modid.block.ModBlocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModBlockEntities {

    public static final BlockEntityType<SuitcaseBlockEntity> SUITCASE_BLOCK_ENTITY =
            Registry.register(
                    Registries.BLOCK_ENTITY_TYPE,
                    Identifier.of(PocketRepose.MOD_ID, "suitcase"),
                    BlockEntityType.Builder.create(
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
                            ModBlocks.SECRET_BARREL
                    ).build()
            );

    public static void registerBlockEntities() {
        PocketRepose.LOGGER.info("Registering block entities for " + PocketRepose.MOD_ID);
    }
}