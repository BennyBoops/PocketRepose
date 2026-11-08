package net.bennyboops.modid.mixin;

import com.mojang.serialization.Lifecycle;
import net.bennyboops.modid.PocketRepose;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionOptionsRegistryHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(DimensionOptionsRegistryHolder.DimensionsConfig.class)
public abstract class DimensionsConfigMixin {
    @Unique
    private static final int VANILLA_DIMENSION_COUNT = 3;

    // Vanilla marks any world with extra dimensions as using experimental settings, which shows a
    // warning every time the world is opened. Pocket dimensions are left out of that check.
    @Inject(method = "getLifecycle", at = @At("HEAD"), cancellable = true)
    private void pocketRepose$ignorePocketDimensions(CallbackInfoReturnable<Lifecycle> cir) {
        Registry<DimensionOptions> dimensions = ((DimensionOptionsRegistryHolder.DimensionsConfig) (Object) this).dimensions();

        Lifecycle lifecycle = Lifecycle.stable();
        int otherDimensions = 0;
        boolean hasPocketDimensions = false;
        for (Map.Entry<RegistryKey<DimensionOptions>, DimensionOptions> entry : dimensions.getEntrySet()) {
            if (PocketRepose.MOD_ID.equals(entry.getKey().getValue().getNamespace())) {
                hasPocketDimensions = true;
                continue;
            }
            otherDimensions++;
            lifecycle = lifecycle.add(DimensionOptionsRegistryHolderInvoker.pocketRepose$getLifecycle(entry.getKey(), entry.getValue()));
        }
        if (!hasPocketDimensions) return;

        if (otherDimensions != VANILLA_DIMENSION_COUNT) lifecycle = lifecycle.add(Lifecycle.experimental());
        cir.setReturnValue(lifecycle);
    }
}
