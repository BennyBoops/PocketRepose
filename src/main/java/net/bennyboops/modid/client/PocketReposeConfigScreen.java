package net.bennyboops.modid.client;

import net.bennyboops.modid.config.ConfigOption;
import net.bennyboops.modid.config.ConfigState;
import net.bennyboops.modid.config.PocketReposeConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The mod's config screen, opened from the mods list. Three tabs: Pocket Dimension and Items (the server settings)
 * and Client (this player's own). In a world, the server tabs show that world's settings as its server last sent
 * them, update as soon as anyone changes them, and are greyed out for players who aren't operators. From the title
 * screen they edit the defaults new worlds start with.
 */
public class PocketReposeConfigScreen extends Screen {
    private enum Tab {
        POCKET_DIMENSION(ConfigOption.Category.POCKET_DIMENSION),
        ITEMS(ConfigOption.Category.ITEMS),
        CLIENT(null);

        @Nullable final ConfigOption.Category category;

        Tab(@Nullable ConfigOption.Category category) {
            this.category = category;
        }
    }

    private static final int ROW_WIDTH = 310;
    private static final int WIDGET_WIDTH = 110;
    private static final int WIDE_WIDGET_WIDTH = 150;
    // Ticks to wait after the last change before sending it, so dragging a slider or typing sends one update
    private static final int SEND_DELAY = 6;
    private static Tab lastTab = Tab.POCKET_DIMENSION;

    private final Screen parent;
    private Tab tab = lastTab;
    @Nullable private NbtCompound values;
    private final NbtCompound pending = new NbtCompound();
    private int sendIn;
    private int seenRevision = -1;
    private boolean seenCanEdit;
    @Nullable private OptionList list;

    public PocketReposeConfigScreen(Screen parent) {
        super(Text.translatable("config.pocket-repose.title"));
        this.parent = parent;
    }

    private boolean inWorld() {
        return client != null && client.world != null;
    }

    /** Server settings can be changed by operators in a world, and by anyone from the title screen (the defaults). */
    private boolean canEdit() {
        if (!inWorld()) return true;
        return client.player != null && client.player.hasPermissionLevel(PocketReposeConfig.PERMISSION_LEVEL)
                && ConfigState.serverSettings() != null;
    }

    private NbtCompound values() {
        if (values == null) {
            NbtCompound fromServer = ConfigState.serverSettings();
            values = !inWorld() ? PocketReposeConfig.readDefaults()
                    : fromServer != null ? fromServer : PocketReposeConfig.builtInDefaults();
            values.copyFrom(pending);
        }
        return values;
    }

    @Override
    protected void init() {
        seenRevision = ConfigState.revision();
        seenCanEdit = canEdit();

        int tabWidth = 100;
        int tabX = width / 2 - (tabWidth * 3 + 8) / 2;
        for (Tab each : Tab.values()) {
            ButtonWidget button = ButtonWidget.builder(Text.translatable("config.pocket-repose.tab." + each.name().toLowerCase(Locale.ROOT)),
                    b -> switchTo(each)).dimensions(tabX + each.ordinal() * (tabWidth + 4), 34, tabWidth, 20).build();
            button.active = each != tab;
            addDrawableChild(button);
        }

        list = new OptionList(client, width, height, 64, height - 36);
        if (tab.category == null) {
            list.add(new Row(Text.translatable("config.pocket-repose.option.hallwayShader"), true,
                    CyclingButtonWidget.onOffBuilder(HallwaySettings.enabled()).omitKeyText()
                            .tooltip(value -> Tooltip.of(Text.literal(HallwaySettings.DESCRIPTION)))
                            .build(0, 0, WIDGET_WIDTH, 20, Text.translatable("config.pocket-repose.option.hallwayShader"),
                                    (button, value) -> HallwaySettings.setEnabled(value))));
        } else {
            boolean editable = canEdit();
            for (ConfigOption option : PocketReposeConfig.OPTIONS) {
                if (option.category == tab.category) list.add(row(option, editable));
            }
        }
        addDrawableChild(list);

        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close()).dimensions(width / 2 - 100, height - 28, 200, 20).build());
    }

    private Row row(ConfigOption option, boolean editable) {
        Text label = Text.translatable("config.pocket-repose.option." + option.key);
        Tooltip tooltip = Tooltip.of(Text.literal(option.description));
        Object value = option.valueIn(values());
        ClickableWidget widget = switch (option.kind) {
            case BOOLEAN -> CyclingButtonWidget.onOffBuilder(Boolean.TRUE.equals(value)).omitKeyText()
                    .build(0, 0, WIDGET_WIDTH, 20, label, (button, on) -> change(option.key, NbtByte.of(on)));
            case NUMBER -> option.max - option.min <= 10
                    ? new NumberSlider(option, value instanceof Float f ? f : option.min)
                    : numberField(option, value instanceof Float f ? f : option.min);
            case ID_LIST -> idListField(option, value);
        };
        widget.setTooltip(tooltip);
        widget.active = editable;
        if (widget instanceof TextFieldWidget field) field.setEditable(editable);
        return new Row(label, editable, widget);
    }

    private TextFieldWidget numberField(ConfigOption option, float value) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, 0, 0, WIDGET_WIDTH, 20, Text.translatable("config.pocket-repose.option." + option.key));
        field.setText(ConfigOption.formatNumber(value));
        field.setChangedListener(text -> {
            Float parsed = parseNumber(text, option);
            field.setEditableColor(parsed == null ? 0xFF5555 : 0xE0E0E0);
            if (parsed != null) change(option.key, NbtFloat.of(parsed));
        });
        return field;
    }

    @Nullable
    private static Float parseNumber(String text, ConfigOption option) {
        try {
            float parsed = Float.parseFloat(text.trim());
            return parsed >= option.min && parsed <= option.max ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private TextFieldWidget idListField(ConfigOption option, @Nullable Object value) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, 0, 0, WIDE_WIDGET_WIDTH, 20, Text.translatable("config.pocket-repose.option." + option.key));
        field.setMaxLength(4096);
        @SuppressWarnings("unchecked") List<String> ids = value instanceof List<?> ? (List<String>) value : List.of();
        field.setText(String.join(", ", ids));
        field.setPlaceholder(Text.literal("minecraft:villager, ...").formatted(Formatting.DARK_GRAY));
        field.setChangedListener(text -> {
            NbtList list = new NbtList();
            for (String part : text.split(",")) {
                String id = part.trim();
                if (!id.isEmpty()) list.add(NbtString.of(id));
            }
            change(option.key, list);
        });
        return field;
    }

    private void change(String key, NbtElement value) {
        values().put(key, value);
        pending.put(key, value.copy());
        sendIn = SEND_DELAY;
    }

    private void send() {
        if (pending.isEmpty()) return;
        if (inWorld()) {
            ConfigClientNetwork.sendChanges(pending.copy());
        } else {
            PocketReposeConfig.writeDefaults(values());
        }
        for (String key : new ArrayList<>(pending.getKeys())) pending.remove(key);
    }

    private void switchTo(Tab next) {
        send();
        tab = next;
        lastTab = next;
        clearAndInit();
    }

    @Override
    public void tick() {
        super.tick();
        if (sendIn > 0 && --sendIn == 0) send();

        // Someone changed a setting, or this player's operator status changed: show the new state, unless the
        // player is in the middle of dragging a slider or typing
        boolean canEdit = canEdit();
        if ((ConfigState.revision() != seenRevision || canEdit != seenCanEdit) && !isDragging() && !(getFocused() instanceof TextFieldWidget)
                && !(list != null && list.getFocused() != null && list.getFocused().getFocused() instanceof TextFieldWidget)) {
            if (inWorld()) values = null;
            double scroll = list == null ? 0 : list.getScrollAmount();
            clearAndInit();
            if (list != null) list.setScrollAmount(scroll);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, subtitle(), width / 2, 22, 0xA0A0A0);
    }

    private Text subtitle() {
        if (tab.category == null) return Text.translatable("config.pocket-repose.subtitle.client");
        if (!inWorld()) return Text.translatable("config.pocket-repose.subtitle.defaults");
        if (ConfigState.serverSettings() == null) return Text.translatable("config.pocket-repose.subtitle.waiting");
        if (!canEdit()) return Text.translatable("config.pocket-repose.subtitle.operators").formatted(Formatting.GOLD);
        return Text.translatable("config.pocket-repose.subtitle.world");
    }

    @Override
    public void close() {
        send();
        if (client != null) client.setScreen(parent);
    }

    /** A slider for a setting with a small range (the knock volume), in steps of 0.1. */
    private class NumberSlider extends SliderWidget {
        private final ConfigOption option;

        NumberSlider(ConfigOption option, float value) {
            super(0, 0, WIDGET_WIDTH, 20, Text.empty(), (value - option.min) / (option.max - option.min));
            this.option = option;
            updateMessage();
        }

        private float current() {
            float raw = option.min + (float) value * (option.max - option.min);
            return Math.round(raw * 10) / 10f;
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(ConfigOption.formatNumber(current())));
        }

        @Override
        protected void applyValue() {
            change(option.key, NbtFloat.of(current()));
        }
    }

    private static class OptionList extends ElementListWidget<Row> {
        OptionList(MinecraftClient client, int width, int height, int top, int bottom) {
            super(client, width, height, top, bottom, 24);
        }

        void add(Row row) {
            addEntry(row);
        }

        @Override
        public int getRowWidth() {
            return ROW_WIDTH;
        }
    }

    private class Row extends ElementListWidget.Entry<Row> {
        private final Text label;
        private final boolean enabled;
        private final ClickableWidget widget;

        Row(Text label, boolean enabled, ClickableWidget widget) {
            this.label = label;
            this.enabled = enabled;
            this.widget = widget;
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
                           int mouseX, int mouseY, boolean hovered, float delta) {
            int labelWidth = entryWidth - widget.getWidth() - 6;
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(label, labelWidth).getString(),
                    x, y + (20 - 8) / 2, enabled ? 0xFFFFFF : 0x808080);
            widget.setPosition(x + entryWidth - widget.getWidth(), y);
            widget.render(context, mouseX, mouseY, delta);
        }

        @Override
        public List<? extends Element> children() {
            return List.of(widget);
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return List.of(widget);
        }
    }
}
