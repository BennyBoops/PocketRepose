package net.bennyboops.modid.block.entity;

import net.bennyboops.modid.PocketRepose;
import net.bennyboops.modid.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PocketRepose.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SuitcaseBlockEntity>> SUITCASE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("suitcase", () -> BlockEntityType.Builder.of(
                    SuitcaseBlockEntity::new,

                    ModBlocks.SUITCASE.get(),
                    ModBlocks.WHITE_SUITCASE.get(),
                    ModBlocks.BLACK_SUITCASE.get(),
                    ModBlocks.LIGHT_GRAY_SUITCASE.get(),
                    ModBlocks.GRAY_SUITCASE.get(),
                    ModBlocks.RED_SUITCASE.get(),
                    ModBlocks.ORANGE_SUITCASE.get(),
                    ModBlocks.YELLOW_SUITCASE.get(),
                    ModBlocks.LIME_SUITCASE.get(),
                    ModBlocks.GREEN_SUITCASE.get(),
                    ModBlocks.CYAN_SUITCASE.get(),
                    ModBlocks.LIGHT_BLUE_SUITCASE.get(),
                    ModBlocks.BLUE_SUITCASE.get(),
                    ModBlocks.PURPLE_SUITCASE.get(),
                    ModBlocks.MAGENTA_SUITCASE.get(),
                    ModBlocks.PINK_SUITCASE.get(),
                    ModBlocks.SECRET_BARREL.get()
            ).build(null));

    public static void registerBlockEntities(IEventBus modEventBus) {
        PocketRepose.LOGGER.info("Registering block entities for " + PocketRepose.MOD_ID);
        BLOCK_ENTITIES.register(modEventBus);
    }
}
