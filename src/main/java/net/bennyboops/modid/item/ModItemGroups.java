package net.bennyboops.modid.item;

import net.bennyboops.modid.PocketRepose;
import net.bennyboops.modid.block.ModBlocks;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ModItemGroups {
    public static final ItemGroup POCKET_GROUP = Registry.register(Registries.ITEM_GROUP,
            new Identifier(PocketRepose.MOD_ID, "pocket"),
            FabricItemGroup.builder().displayName(Text.translatable("itemgroup.pocket"))
                    .icon(() -> new ItemStack(ModItems.KEYSTONE)).entries((displayContext, entries) -> {

                        entries.add(ModItems.KEYSTONE);

                        entries.add(ModBlocks.SUITCASE);
                        entries.add(ModBlocks.WHITE_SUITCASE);
                        entries.add(ModBlocks.LIGHT_GRAY_SUITCASE);
                        entries.add(ModBlocks.GRAY_SUITCASE);
                        entries.add(ModBlocks.BLACK_SUITCASE);
                        entries.add(ModBlocks.RED_SUITCASE);
                        entries.add(ModBlocks.ORANGE_SUITCASE);
                        entries.add(ModBlocks.YELLOW_SUITCASE);
                        entries.add(ModBlocks.LIME_SUITCASE);
                        entries.add(ModBlocks.GREEN_SUITCASE);
                        entries.add(ModBlocks.CYAN_SUITCASE);
                        entries.add(ModBlocks.LIGHT_BLUE_SUITCASE);
                        entries.add(ModBlocks.BLUE_SUITCASE);
                        entries.add(ModBlocks.MAGENTA_SUITCASE);
                        entries.add(ModBlocks.PURPLE_SUITCASE);
                        entries.add(ModBlocks.PINK_SUITCASE);

                        entries.add(ModBlocks.SECRET_BARREL);

                        entries.add(ModBlocks.PORTAL);

                        entries.add(Blocks.ANVIL);

                    }).build());
    public static void registerItemGroups() {
        PocketRepose.LOGGER.info("Registering item groups for" + PocketRepose.MOD_ID);
    }
}
