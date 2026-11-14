package net.bennyboops.modid.client;

import net.bennyboops.modid.PocketRepose;
import net.bennyboops.modid.item.KeyPreviewData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * An unnamed key's tooltip: a line of text ("Rename to bind") with a picture of what the key creates below it.
 * (Tooltip pictures always come right under the item's name, so the text is drawn here to keep it above.)
 * Small pixel-art icons (32 pixels or less) are drawn at exactly twice
 * their size so they stay sharp; bigger pictures are fitted to 128 GUI pixels wide.
 */
public class KeyPreviewTooltip implements TooltipComponent {
    private static final int ICON_SCALE = 2;
    private static final int MAX_ICON_SIZE = 32;
    private static final int PICTURE_WIDTH = 128;
    private static final int GAP = 2;
    private static final int LINE_HEIGHT = 10;
    // Each picture's size in pixels, read from the resource once
    private static final Map<Identifier, int[]> SIZES = new HashMap<>();

    private final Identifier texture;
    private final Text caption;
    private final int textureWidth;
    private final int textureHeight;
    private final int width;
    private final int height;

    public KeyPreviewTooltip(KeyPreviewData data) {
        this.texture = data.texture();
        this.caption = data.caption();
        int[] size = SIZES.computeIfAbsent(texture, KeyPreviewTooltip::readSize);
        this.textureWidth = size[0];
        this.textureHeight = size[1];
        if (textureWidth <= MAX_ICON_SIZE && textureHeight <= MAX_ICON_SIZE) {
            this.width = textureWidth * ICON_SCALE;
            this.height = textureHeight * ICON_SCALE;
        } else {
            this.width = PICTURE_WIDTH;
            this.height = Math.max(1, Math.round(PICTURE_WIDTH * (float) textureHeight / textureWidth));
        }
    }

    private static int[] readSize(Identifier texture) {
        try (InputStream in = MinecraftClient.getInstance().getResourceManager().open(texture);
             NativeImage image = NativeImage.read(in)) {
            return new int[]{image.getWidth(), image.getHeight()};
        } catch (Exception e) {
            PocketRepose.LOGGER.warn("Couldn't read key preview {}", texture, e);
            return new int[]{16, 16};
        }
    }

    @Override
    public int getHeight() {
        return LINE_HEIGHT + GAP + height + GAP;
    }

    @Override
    public int getWidth(TextRenderer textRenderer) {
        return Math.max(width, textRenderer.getWidth(caption));
    }

    @Override
    public void drawItems(TextRenderer textRenderer, int x, int y, DrawContext context) {
        context.drawText(textRenderer, caption, x, y, 0xFFFFFF, true);
        context.drawTexture(texture, x, y + LINE_HEIGHT + GAP, width, height, 0, 0, textureWidth, textureHeight, textureWidth, textureHeight);
    }
}
