package net.bennyboops.modid;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(value = PocketRepose.MODID, dist = Dist.CLIENT)
public class PocketReposeClient {
    public PocketReposeClient(IEventBus modEventBus, ModContainer modContainer) {
    }
}
