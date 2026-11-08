package net.bennyboops.modid.item;

import net.bennyboops.modid.PocketRepose;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

public class ModItems {
    public static final Item GOLD_KEY = registerItem("gold_key", new KeystoneItem(new Item.Settings(), KeystoneItem.Layout.ISLAND));
    public static final Item IRON_KEY = registerItem("iron_key", new KeystoneItem(new Item.Settings(), KeystoneItem.Layout.BLANK));
    // Enchanted look and magenta (epic) name, like the enchanted golden apple. 50 uses outside pocket dimensions
    public static final Item EXIT_KEYSTONE = registerItem("exit_keystone", new ExitKeystoneItem(new Item.Settings()
            .maxDamage(ExitKeystoneItem.USES)
            .rarity(Rarity.EPIC)
            .component(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true)));
    public static final Item NETHERITE_KEY = registerItem("netherite_key", new KeystoneItem(new Item.Settings().fireproof(), KeystoneItem.Layout.WORLD));

    // Keys made before 1.5.0 were "pocket-repose:keystone". It still works as an Island Key, and turns into a
    // gold_key once it's in a player's inventory, so existing worlds don't lose their keys. Not craftable.
    public static final Item LEGACY_KEYSTONE = registerItem("keystone", new KeystoneItem(new Item.Settings(), KeystoneItem.Layout.ISLAND));

    private static Item registerItem(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(PocketRepose.MOD_ID, name), item);
    }

    public static void registerModItems() {
        PocketRepose.LOGGER.info("Registering mod items for " + PocketRepose.MOD_ID);
    }
}
