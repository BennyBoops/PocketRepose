package net.bennyboops.modid.item;

import net.bennyboops.modid.PocketRepose;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PocketRepose.MOD_ID);

    public static final DeferredItem<KeystoneItem> KEYSTONE =
            ITEMS.register("keystone", () -> new KeystoneItem(new Item.Properties()));

    public static void registerModItems(IEventBus modEventBus) {
        PocketRepose.LOGGER.info("Registering mod items for " + PocketRepose.MOD_ID);
        ITEMS.register(modEventBus);
    }
}
