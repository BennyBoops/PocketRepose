package net.bennyboops.modid.item;

import net.bennyboops.modid.PocketRepose;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.minecraftforge.registries.RegisterEvent;

public class ModItems {
    public static Item GOLD_KEY;
    public static Item IRON_KEY;
    public static Item EXIT_KEYSTONE;
    public static Item NETHERITE_KEY;
    public static Item LEGACY_KEYSTONE;

    // Forge only allows items to be created while the item registry is open, so this runs from RegisterEvent
    public static void registerModItems(RegisterEvent.RegisterHelper<Item> helper) {
        GOLD_KEY = registerItem(helper, "gold_key", new KeystoneItem(new Item.Settings(), KeystoneItem.Layout.ISLAND));
        IRON_KEY = registerItem(helper, "iron_key", new KeystoneItem(new Item.Settings(), KeystoneItem.Layout.BLANK));
        // Enchanted look and magenta (epic) name, like the enchanted golden apple. 50 uses outside pocket dimensions
        EXIT_KEYSTONE = registerItem(helper, "exit_keystone", new ExitKeystoneItem(new Item.Settings()
                .maxDamage(ExitKeystoneItem.USES)
                .rarity(Rarity.EPIC)
                .component(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true)));
        NETHERITE_KEY = registerItem(helper, "netherite_key", new KeystoneItem(new Item.Settings().fireproof(), KeystoneItem.Layout.WORLD));

        // Keys made before 1.5.0 were "pocket-repose:keystone". It still works as an Island Key, and turns into a
        // gold_key once it's in a player's inventory, so existing worlds don't lose their keys. Not craftable.
        LEGACY_KEYSTONE = registerItem(helper, "keystone", new KeystoneItem(new Item.Settings(), KeystoneItem.Layout.ISLAND));
    }

    private static Item registerItem(RegisterEvent.RegisterHelper<Item> helper, String name, Item item) {
        helper.register(Identifier.of(PocketRepose.MOD_ID, name), item);
        return item;
    }
}
