package net.bennyboops.modid.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.bennyboops.modid.util.SafeIterator;

import java.util.Collection;
import java.util.Iterator;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
    @Redirect(method = "tickWorlds", at = @At(value = "INVOKE", target = "Ljava/lang/Iterable;iterator()Ljava/util/Iterator;", ordinal = 0), require = 0)
    private Iterator<ServerWorld> fantasy$copyBeforeTicking(Iterable<ServerWorld> instance) {
        return new SafeIterator<>((Collection<ServerWorld>) instance);
    }
}
