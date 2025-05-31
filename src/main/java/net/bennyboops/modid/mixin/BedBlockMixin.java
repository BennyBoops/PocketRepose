package net.bennyboops.modid.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public class BedBlockMixin {

    @Inject(method = "setSpawnPoint", at = @At("HEAD"), cancellable = true)
    private void preventSpawnSettingInPocketDimensions(RegistryKey<World> dimension,
                                                       BlockPos pos, float angle, boolean forced, boolean sendMessage, CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (dimension != null &&
                dimension.getValue().getNamespace().equals("pocket-repose") &&
                dimension.getValue().getPath().startsWith("pocket_dimension_")) {
            /**
            if (sendMessage) {
                player.sendMessage(Text.literal("Respawn point not set")
                        .formatted(Formatting.RED), true);
            }
             **/
            ci.cancel();
        }
    }
}