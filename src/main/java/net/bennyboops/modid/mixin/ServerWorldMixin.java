package net.bennyboops.modid.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.objectweb.asm.Opcodes;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.bennyboops.modid.PocketRepose;
import net.minecraft.util.Identifier;
import net.bennyboops.modid.world.FantasyWorldAccess;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin implements FantasyWorldAccess {
    @Unique
    private static final int TICK_TIMEOUT = 20 * 15;

    @Unique
    private boolean fantasy$tickWhenEmpty = true;
    @Unique
    private int fantasy$tickTimeout;

    @Shadow
    public abstract List<ServerPlayerEntity> getPlayers();

    @Shadow
    public abstract ServerChunkManager getChunkManager();

    @Override
    public void fantasy$setTickWhenEmpty(boolean tickWhenEmpty) {
        this.fantasy$tickWhenEmpty = tickWhenEmpty;
    }

    // Pocket dimensions get their own seed instead of the overworld's (RuntimeWorld does the same)
    @Inject(method = "getSeed", at = @At("HEAD"), cancellable = true)
    private void pocketRepose$pocketDimensionSeed(CallbackInfoReturnable<Long> cir) {
        ServerWorld world = (ServerWorld) (Object) this;
        Identifier id = world.getRegistryKey().getValue();
        if (PocketRepose.MOD_ID.equals(id.getNamespace())) {
            long worldSeed = world.getServer().getSaveProperties().getGeneratorOptions().getSeed();
            cir.setReturnValue(PocketRepose.pocketSeed(worldSeed, id.getPath()));
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void tick(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        boolean shouldTick = this.fantasy$tickWhenEmpty || !this.isWorldEmpty();
        if (shouldTick) {
            this.fantasy$tickTimeout = TICK_TIMEOUT;
        } else if (this.fantasy$tickTimeout-- <= 0) {
            ci.cancel();
        }
    }

    @Override
    public boolean fantasy$shouldTick() {
        boolean shouldTick = this.fantasy$tickWhenEmpty || !this.isWorldEmpty();
        return shouldTick || this.fantasy$tickTimeout > 0;
    }

    @Unique
    private boolean isWorldEmpty() {
        return this.getPlayers().isEmpty() && this.getChunkManager().getLoadedChunkCount() <= 0;
    }


    // No rain packet fix needed here: NeoForge already sends weather packets only to the
    // players in the dimension whose weather changed.

    // Pocket dimensions share the overworld's time, so they can't skip the night on their own (their
    // sleepers were just woken up with the night still going). They hand their sleep checks to the
    // overworld instead, and the overworld counts the players in every pocket dimension as its own.
    @Inject(method = "updateSleepingPlayers", at = @At("HEAD"), cancellable = true)
    private void pocketRepose$sleepWithOverworld(CallbackInfo ci) {
        ServerWorld world = (ServerWorld) (Object) this;
        if (pocketRepose$isPocketDimension(world)) {
            ci.cancel();
            world.getServer().getOverworld().updateSleepingPlayers();
        }
    }

    @Redirect(method = {"updateSleepingPlayers", "sendSleepingStatus", "wakeSleepingPlayers"},
            at = @At(value = "FIELD", target = "Lnet/minecraft/server/world/ServerWorld;players:Ljava/util/List;", opcode = Opcodes.GETFIELD))
    private List<ServerPlayerEntity> pocketRepose$includePocketSleepers(ServerWorld world) {
        return pocketRepose$sleepingGroup(world);
    }

    @ModifyArg(method = "tick", index = 1,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/SleepManager;canResetTime(ILjava/util/List;)Z"))
    private List<ServerPlayerEntity> pocketRepose$includePocketSleepersInTimeCheck(List<ServerPlayerEntity> players) {
        return pocketRepose$sleepingGroup((ServerWorld) (Object) this);
    }

    @Unique
    private static List<ServerPlayerEntity> pocketRepose$sleepingGroup(ServerWorld world) {
        if (world.getRegistryKey() != World.OVERWORLD) return world.getPlayers();
        List<ServerPlayerEntity> players = new ArrayList<>(world.getPlayers());
        for (ServerWorld other : world.getServer().getWorlds()) {
            if (pocketRepose$isPocketDimension(other)) players.addAll(other.getPlayers());
        }
        return players;
    }

    @Unique
    private static boolean pocketRepose$isPocketDimension(ServerWorld world) {
        return PocketRepose.MOD_ID.equals(world.getRegistryKey().getValue().getNamespace());
    }


}
