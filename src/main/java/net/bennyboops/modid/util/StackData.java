package net.bennyboops.modid.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class StackData {
    private StackData() {
    }

    /**
     * Items stored inside a container item: shulker boxes (container component) and bundles (bundle contents).
     */
    public static List<ItemStack> getContainedStacks(ItemStack stack) {
        ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
        BundleContentsComponent bundle = stack.get(DataComponentTypes.BUNDLE_CONTENTS);
        if (container == null && bundle == null) return List.of();
        List<ItemStack> result = new ArrayList<>();
        if (container != null) container.iterateNonEmpty().forEach(result::add);
        if (bundle != null) bundle.iterate().forEach(result::add);
        return result;
    }

    public static boolean hasContainedStacks(ItemStack stack) {
        return stack.get(DataComponentTypes.CONTAINER) != null || stack.get(DataComponentTypes.BUNDLE_CONTENTS) != null;
    }
}
