package net.bennyboops.modid.mixin;

import com.mojang.serialization.Lifecycle;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionOptionsRegistryHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(DimensionOptionsRegistryHolder.class)
public interface DimensionOptionsRegistryHolderInvoker {
    @Invoker("getLifecycle")
    static Lifecycle pocketRepose$getLifecycle(RegistryKey<DimensionOptions> key, DimensionOptions options) {
        throw new AssertionError();
    }
}
