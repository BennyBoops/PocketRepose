package net.bennyboops.modid.mixin;

import net.bennyboops.modid.world.FantasyWorldAccess;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.BooleanSupplier;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin implements FantasyWorldAccess {
    @Unique
    private static final int pocketRepose$TICK_TIMEOUT = 20 * 15;

    @Unique
    private boolean fantasy$tickWhenEmpty = true;
    @Unique
    private int fantasy$tickTimeout;

    @Shadow
    public abstract List<ServerPlayer> players();

    @Shadow
    public abstract ServerChunkCache getChunkSource();

    @Override
    public void fantasy$setTickWhenEmpty(boolean tickWhenEmpty) {
        this.fantasy$tickWhenEmpty = tickWhenEmpty;
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void pocketRepose$skipEmptyWorldTick(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        boolean shouldTick = this.fantasy$tickWhenEmpty || !this.pocketRepose$isWorldEmpty();
        if (shouldTick) {
            this.fantasy$tickTimeout = pocketRepose$TICK_TIMEOUT;
        } else if (this.fantasy$tickTimeout-- <= 0) {
            ci.cancel();
        }
    }

    @Override
    public boolean fantasy$shouldTick() {
        boolean shouldTick = this.fantasy$tickWhenEmpty || !this.pocketRepose$isWorldEmpty();
        return shouldTick || this.fantasy$tickTimeout > 0;
    }

    @Unique
    private boolean pocketRepose$isWorldEmpty() {
        return this.players().isEmpty() && this.getChunkSource().getLoadedChunksCount() <= 0;
    }
}
