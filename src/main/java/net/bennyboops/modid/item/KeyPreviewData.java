package net.bennyboops.modid.item;

import net.minecraft.text.Text;
import net.minecraft.item.tooltip.TooltipData;
import net.minecraft.util.Identifier;

/** An unnamed key's tooltip: a line of text above a picture of what the key creates (drawn by KeyPreviewTooltip). */
public record KeyPreviewData(Identifier texture, Text caption) implements TooltipData {
}
