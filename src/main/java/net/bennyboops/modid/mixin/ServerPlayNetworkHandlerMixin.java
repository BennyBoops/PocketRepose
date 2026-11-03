package net.bennyboops.modid.mixin;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    @Shadow
    public ServerPlayerEntity player;

    @Unique
    private ServerWorld pocketRepose$worldBeforeMove;

    @Inject(method = "onPlayerMove",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V"))
    private void pocketRepose$rememberWorld(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        pocketRepose$worldBeforeMove = player.getServerWorld();
    }

    // If anything sends the player to another dimension while their movement is being applied (a suitcase
    // or portal block they walked into), the rest of that movement belongs to the old dimension. Applying
    // it would put them at their old coordinates in the new dimension, so stop here.
    @Inject(method = "onPlayerMove",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V",
                    shift = At.Shift.AFTER),
            cancellable = true)
    private void pocketRepose$stopIfDimensionChanged(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        if (player.getServerWorld() != pocketRepose$worldBeforeMove) ci.cancel();
    }
}
