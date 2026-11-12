package net.bennyboops.modid.client;

import net.bennyboops.modid.config.ConfigNetwork;
import net.bennyboops.modid.config.ConfigState;
import net.minecraft.nbt.NbtCompound;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/** The client's half of the settings sync, and the config button in the mods list. */
public final class ConfigClientNetwork {
    private ConfigClientNetwork() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ConfigState.clear());
        ModLoadingContext.get().getActiveContainer().registerExtensionPoint(IConfigScreenFactory.class,
                (IConfigScreenFactory) (container, parent) -> new PocketReposeConfigScreen(parent));
    }

    /** Asks the server to change settings. It only does if the player is an operator. */
    public static void sendChanges(NbtCompound changes) {
        ConfigNetwork.sendToServer(changes);
    }
}
