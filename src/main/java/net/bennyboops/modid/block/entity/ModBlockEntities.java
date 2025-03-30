package net.bennyboops.modid.block.entity;

import net.bennyboops.modid.block.ModBlocks;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModBlockEntities {
    public static final BlockEntityType<SuitcaseBlockEntity> SUITCASE_BLOCK_ENTITY =
            Registry.register(
                    Registries.BLOCK_ENTITY_TYPE,
                    new Identifier("pocket-repose", "suitcase"),
                    FabricBlockEntityTypeBuilder.create(
                            SuitcaseBlockEntity::new,
                            ModBlocks.SUITCASE
                    ).build()
            );

    public static void registerBlockEntities() {
    }
}