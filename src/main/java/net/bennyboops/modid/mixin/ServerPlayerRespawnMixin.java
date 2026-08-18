package net.bennyboops.modid.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public class ServerPlayerRespawnMixin {

    @Inject(method = "setRespawnPosition", at = @At("HEAD"), cancellable = true)
    private void pocketRepose$preventSpawnSettingInPocketDimensions(ResourceKey<Level> dimension,
                                                                   BlockPos pos,
                                                                   float angle,
                                                                   boolean forced,
                                                                   boolean sendMessage,
                                                                   CallbackInfo ci) {
        if (dimension != null
                && dimension.location().getNamespace().equals("pocket-repose")
                && dimension.location().getPath().startsWith("pocket_dimension_")) {
            ci.cancel();
        }
    }
}
