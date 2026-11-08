package net.bennyboops.modid.item;

import net.bennyboops.modid.PocketRepose;
import net.bennyboops.modid.block.ModBlocks;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ModItemGroups {
    public static ItemGroup POCKET_GROUP;

    public static void registerItemGroups(RegisterEvent.RegisterHelper<ItemGroup> helper) {
        POCKET_GROUP = ItemGroup.builder().displayName(Text.translatable("itemgroup.pocket"))
                    .icon(() -> new ItemStack(ModItems.GOLD_KEY)).entries((displayContext, entries) -> {

                        entries.add(ModItems.GOLD_KEY);
                        entries.add(ModItems.IRON_KEY);
                        entries.add(ModItems.NETHERITE_KEY);
                        entries.add(ModItems.EXIT_KEYSTONE);

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
                        entries.add(ModBlocks.SECRET_CHEST);
                        ModBlocks.POCKET_DOORS.forEach(entries::add);
                        ModBlocks.EXIT_DOORS.forEach(entries::add);

                        entries.add(ModBlocks.PORTAL);

                        entries.add(Blocks.ANVIL);

                    }).build();
        helper.register(Identifier.of(PocketRepose.MOD_ID, "pocket"), POCKET_GROUP);
    }
}
