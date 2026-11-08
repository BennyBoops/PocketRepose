package net.bennyboops.modid.mixin;

import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.Unique;
import net.bennyboops.modid.PocketRepose;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.bennyboops.modid.world.FantasyWorldAccess;

@Mixin(ServerChunkManager.class)
public class ServerChunkManagerMixin {
    @Shadow
    @Final
    ServerWorld world;

    @Inject(method = "executeQueuedTasks", at = @At("HEAD"), cancellable = true)
    private void executeQueuedTasks(CallbackInfoReturnable<Boolean> ci) {
        if (!((FantasyWorldAccess) this.world).fantasy$shouldTick()) {
            ci.setReturnValue(false);
        }
    }

    // The hostileMobSpawning / passiveMobSpawning settings turn natural spawning off in pocket dimensions
    @ModifyArg(method = "tickChunks", index = 3,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/SpawnHelper;spawn(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/world/chunk/WorldChunk;Lnet/minecraft/world/SpawnHelper$Info;ZZZ)V"))
    private boolean pocketRepose$passiveMobSpawning(boolean spawnAnimals) {
        return spawnAnimals && (!pocketRepose$isPocketDimension() || PocketRepose.getPassiveMobSpawning());
    }

    @ModifyArg(method = "tickChunks", index = 4,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/SpawnHelper;spawn(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/world/chunk/WorldChunk;Lnet/minecraft/world/SpawnHelper$Info;ZZZ)V"))
    private boolean pocketRepose$hostileMobSpawning(boolean spawnMonsters) {
        return spawnMonsters && (!pocketRepose$isPocketDimension() || PocketRepose.getHostileMobSpawning());
    }

    @Unique
    private boolean pocketRepose$isPocketDimension() {
        return PocketRepose.MOD_ID.equals(this.world.getRegistryKey().getValue().getNamespace());
    }
}
