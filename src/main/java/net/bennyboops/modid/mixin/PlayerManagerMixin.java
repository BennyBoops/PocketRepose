package net.bennyboops.modid.mixin;

import net.bennyboops.modid.PocketRepose;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin {

    // Compasses point at the spawn position the server sends; in a pocket dimension that's its player entry point
    @Redirect(method = {"sendWorldInfo", "respawnPlayer"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;getSpawnPos()Lnet/minecraft/util/math/BlockPos;"))
    private BlockPos pocketRepose$compassTarget(ServerWorld world) {
        return PocketRepose.compassTarget(world);
    }
}
