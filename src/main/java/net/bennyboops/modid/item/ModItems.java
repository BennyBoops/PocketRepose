package net.bennyboops.modid.item;

import net.bennyboops.modid.PocketRepose;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.neoforged.neoforge.registries.RegisterEvent;

public class ModItems {
    public static Item KEYSTONE;

    // Forge only allows items to be created while the item registry is open, so this runs from RegisterEvent
    public static void registerModItems(RegisterEvent.RegisterHelper<Item> helper) {
        KEYSTONE = new KeystoneItem(new Item.Settings());
        helper.register(Identifier.of(PocketRepose.MOD_ID, "keystone"), KEYSTONE);
    }
}
