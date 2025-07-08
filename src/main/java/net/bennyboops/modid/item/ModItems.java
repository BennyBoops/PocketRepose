package net.bennyboops.modid.item;

import net.bennyboops.modid.PocketRepose;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModItems {
    public static final Item KEYSTONE = registerItem("keystone", new KeystoneItem(new Item.Settings()));

    private static Item registerItem(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(PocketRepose.MOD_ID, name), item);
    }

    public static void registerModItems() {
        PocketRepose.LOGGER.info("Registering mod items for " + PocketRepose.MOD_ID);
    }
}
