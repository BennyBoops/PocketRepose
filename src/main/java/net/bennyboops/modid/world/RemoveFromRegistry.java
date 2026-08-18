package net.bennyboops.modid.world;

import net.minecraft.core.MappedRegistry;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface RemoveFromRegistry<T> {
    @SuppressWarnings("unchecked")
    static <T> boolean remove(MappedRegistry<T> registry, ResourceLocation key) {
        return ((RemoveFromRegistry<T>) registry).fantasy$remove(key);
    }

    @SuppressWarnings("unchecked")
    static <T> boolean remove(MappedRegistry<T> registry, T value) {
        return ((RemoveFromRegistry<T>) registry).fantasy$remove(value);
    }

    boolean fantasy$remove(T value);

    boolean fantasy$remove(ResourceLocation key);

    void fantasy$setFrozen(boolean value);

    boolean fantasy$isFrozen();
}
