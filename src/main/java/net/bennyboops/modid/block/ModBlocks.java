package net.bennyboops.modid.block;

import net.bennyboops.modid.PocketRepose;
import net.bennyboops.modid.item.ModItems;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PocketRepose.MOD_ID);

    public static final DeferredBlock<SuitcaseBlock> SUITCASE = registerSuitcase("suitcase", Blocks.BROWN_WOOL);
    public static final DeferredBlock<SuitcaseBlock> WHITE_SUITCASE = registerSuitcase("white_suitcase", Blocks.WHITE_WOOL);
    public static final DeferredBlock<SuitcaseBlock> BLACK_SUITCASE = registerSuitcase("black_suitcase", Blocks.BLACK_WOOL);
    public static final DeferredBlock<SuitcaseBlock> LIGHT_GRAY_SUITCASE = registerSuitcase("light_gray_suitcase", Blocks.LIGHT_GRAY_WOOL);
    public static final DeferredBlock<SuitcaseBlock> GRAY_SUITCASE = registerSuitcase("gray_suitcase", Blocks.GRAY_WOOL);
    public static final DeferredBlock<SuitcaseBlock> RED_SUITCASE = registerSuitcase("red_suitcase", Blocks.RED_WOOL);
    public static final DeferredBlock<SuitcaseBlock> ORANGE_SUITCASE = registerSuitcase("orange_suitcase", Blocks.ORANGE_WOOL);
    public static final DeferredBlock<SuitcaseBlock> YELLOW_SUITCASE = registerSuitcase("yellow_suitcase", Blocks.YELLOW_WOOL);
    public static final DeferredBlock<SuitcaseBlock> LIME_SUITCASE = registerSuitcase("lime_suitcase", Blocks.LIME_WOOL);
    public static final DeferredBlock<SuitcaseBlock> GREEN_SUITCASE = registerSuitcase("green_suitcase", Blocks.GREEN_WOOL);
    public static final DeferredBlock<SuitcaseBlock> CYAN_SUITCASE = registerSuitcase("cyan_suitcase", Blocks.CYAN_WOOL);
    public static final DeferredBlock<SuitcaseBlock> LIGHT_BLUE_SUITCASE = registerSuitcase("light_blue_suitcase", Blocks.LIGHT_BLUE_WOOL);
    public static final DeferredBlock<SuitcaseBlock> BLUE_SUITCASE = registerSuitcase("blue_suitcase", Blocks.BLUE_WOOL);
    public static final DeferredBlock<SuitcaseBlock> PURPLE_SUITCASE = registerSuitcase("purple_suitcase", Blocks.PURPLE_WOOL);
    public static final DeferredBlock<SuitcaseBlock> MAGENTA_SUITCASE = registerSuitcase("magenta_suitcase", Blocks.MAGENTA_WOOL);
    public static final DeferredBlock<SuitcaseBlock> PINK_SUITCASE = registerSuitcase("pink_suitcase", Blocks.PINK_WOOL);

    public static final DeferredBlock<CustomSuitcaseBlock> SECRET_BARREL = registerBlock("secret_barrel",
            () -> new CustomSuitcaseBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.BARREL)
                            .sound(SoundType.WOOD)
                            .strength(1.0f)
                            .noOcclusion(),
                    Block.box(0, 0, 0, 16, 15, 16),
                    SoundEvents.BARREL_OPEN,
                    SoundEvents.BARREL_CLOSE
            ));

    public static final DeferredBlock<PocketPortalBlock> PORTAL = registerBlock("portal",
            () -> new PocketPortalBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.NETHER_PORTAL)
                    .sound(SoundType.LODESTONE)
                    .noOcclusion()
                    .lightLevel(state -> 10)
                    .strength(5.0f)));

    private static DeferredBlock<SuitcaseBlock> registerSuitcase(String name, Block copyFrom) {
        return registerBlock(name, () -> new SuitcaseBlock(BlockBehaviour.Properties.ofFullCopy(copyFrom)
                .sound(SoundType.WOOL)
                .strength(0.2f)
                .noOcclusion()
                .lightLevel(state -> state.getValue(SuitcaseBlock.OPEN) ? 8 : 0)));
    }

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> block) {
        DeferredBlock<T> registered = BLOCKS.register(name, block);
        ModItems.ITEMS.registerSimpleBlockItem(name, registered);
        return registered;
    }

    public static void registerModBlocks(IEventBus modEventBus) {
        PocketRepose.LOGGER.info("Registering mod blocks for " + PocketRepose.MOD_ID);
        BLOCKS.register(modEventBus);
    }
}
