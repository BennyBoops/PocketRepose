package net.bennyboops.modid.util;

import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.20.1 stand-ins for the 1.21 item data components used by the mod
 * (block entity data, lore and container contents are plain NBT in this version).
 */
public final class StackData {
    private StackData() {
    }

    /**
     * @return a copy of the stack's BlockEntityTag, or null if it has none
     */
    @Nullable
    public static NbtCompound getBlockEntityData(ItemStack stack) {
        NbtCompound nbt = BlockItem.getBlockEntityNbt(stack);
        return nbt == null ? null : nbt.copy();
    }

    public static void setBlockEntityData(ItemStack stack, NbtCompound nbt) {
        stack.setSubNbt(BlockItem.BLOCK_ENTITY_TAG_KEY, nbt.copy());
    }

    public static void setLore(ItemStack stack, List<Text> lines) {
        NbtList lore = new NbtList();
        for (Text line : lines) {
            lore.add(NbtString.of(Text.Serializer.toJson(line)));
        }
        stack.getOrCreateSubNbt(ItemStack.DISPLAY_KEY).put(ItemStack.LORE_KEY, lore);
    }

    /**
     * Items stored inside a container item: shulker boxes (BlockEntityTag.Items) and bundles (Items).
     */
    public static List<ItemStack> getContainedStacks(ItemStack stack) {
        List<ItemStack> result = new ArrayList<>();
        NbtCompound nbt = stack.getNbt();
        if (nbt == null) return result;

        NbtCompound beTag = BlockItem.getBlockEntityNbt(stack);
        if (beTag != null) addItems(beTag.getList("Items", NbtElement.COMPOUND_TYPE), result);
        addItems(nbt.getList("Items", NbtElement.COMPOUND_TYPE), result);
        return result;
    }

    public static boolean hasContainedStacks(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        if (nbt == null) return false;
        NbtCompound beTag = BlockItem.getBlockEntityNbt(stack);
        return (beTag != null && beTag.contains("Items", NbtElement.LIST_TYPE))
                || nbt.contains("Items", NbtElement.LIST_TYPE);
    }

    private static void addItems(NbtList items, List<ItemStack> out) {
        for (int i = 0; i < items.size(); i++) {
            ItemStack inner = ItemStack.fromNbt(items.getCompound(i));
            if (!inner.isEmpty()) out.add(inner);
        }
    }
}
