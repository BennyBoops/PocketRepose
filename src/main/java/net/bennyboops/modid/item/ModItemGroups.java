package net.bennyboops.modid.item;

import net.bennyboops.modid.PocketRepose;
import net.bennyboops.modid.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItemGroups {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, PocketRepose.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> POCKET_GROUP =
            CREATIVE_MODE_TABS.register("pocket", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemgroup.pocket"))
                    .icon(() -> new ItemStack(ModItems.KEYSTONE.get()))
                    .displayItems((displayContext, entries) -> {

                        entries.accept(ModItems.KEYSTONE.get());

                        entries.accept(ModBlocks.SUITCASE.get());
                        entries.accept(ModBlocks.WHITE_SUITCASE.get());
                        entries.accept(ModBlocks.LIGHT_GRAY_SUITCASE.get());
                        entries.accept(ModBlocks.GRAY_SUITCASE.get());
                        entries.accept(ModBlocks.BLACK_SUITCASE.get());
                        entries.accept(ModBlocks.RED_SUITCASE.get());
                        entries.accept(ModBlocks.ORANGE_SUITCASE.get());
                        entries.accept(ModBlocks.YELLOW_SUITCASE.get());
                        entries.accept(ModBlocks.LIME_SUITCASE.get());
                        entries.accept(ModBlocks.GREEN_SUITCASE.get());
                        entries.accept(ModBlocks.CYAN_SUITCASE.get());
                        entries.accept(ModBlocks.LIGHT_BLUE_SUITCASE.get());
                        entries.accept(ModBlocks.BLUE_SUITCASE.get());
                        entries.accept(ModBlocks.MAGENTA_SUITCASE.get());
                        entries.accept(ModBlocks.PURPLE_SUITCASE.get());
                        entries.accept(ModBlocks.PINK_SUITCASE.get());

                        entries.accept(ModBlocks.SECRET_BARREL.get());

                        entries.accept(ModBlocks.PORTAL.get());

                        entries.accept(Blocks.ANVIL);

                    }).build());

    public static void registerItemGroups(IEventBus modEventBus) {
        PocketRepose.LOGGER.info("Registering item groups for " + PocketRepose.MOD_ID);
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
