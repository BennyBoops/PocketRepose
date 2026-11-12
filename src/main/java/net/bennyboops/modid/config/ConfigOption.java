package net.bennyboops.modid.config;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** One server setting that can be changed with a command, the config file, or the config screen. */
public final class ConfigOption {
    public enum Category {
        POCKET_DIMENSION("pocket_dimension", "Pocket Dimension"),
        ITEMS("items", "Items");

        public final String section;
        public final String title;

        Category(String section, String title) {
            this.section = section;
            this.title = title;
        }
    }

    public enum Kind { BOOLEAN, NUMBER, ID_LIST }

    public final String key;
    public final Category category;
    public final Kind kind;
    /** Shown above the setting in the config file and as its tooltip in the config screen. */
    public final String description;
    public final float min, max;
    private final Object defaultValue;
    private final Supplier<Object> getter;
    private final Consumer<Object> setter;

    private ConfigOption(String key, Category category, Kind kind, String description, float min, float max,
                         Object defaultValue, Supplier<Object> getter, Consumer<Object> setter) {
        this.key = key;
        this.category = category;
        this.kind = kind;
        this.description = description;
        this.min = min;
        this.max = max;
        this.defaultValue = defaultValue;
        this.getter = getter;
        this.setter = setter;
    }

    public static ConfigOption bool(Category category, String key, String description, boolean defaultValue,
                                    Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return new ConfigOption(key, category, Kind.BOOLEAN, description, 0, 1, defaultValue,
                getter::get, value -> setter.accept((Boolean) value));
    }

    public static ConfigOption number(Category category, String key, String description, float defaultValue, float min, float max,
                                      Supplier<Float> getter, Consumer<Float> setter) {
        return new ConfigOption(key, category, Kind.NUMBER, description, min, max, defaultValue,
                getter::get, value -> setter.accept((Float) value));
    }

    public static ConfigOption idList(Category category, String key, String description,
                                      Supplier<List<String>> getter, Consumer<List<String>> setter) {
        return new ConfigOption(key, category, Kind.ID_LIST, description, 0, 0, List.of(),
                getter::get, value -> {
                    @SuppressWarnings("unchecked") List<String> list = (List<String>) value;
                    setter.accept(list);
                });
    }

    /** Writes the current value into the compound under this option's key. */
    public void write(NbtCompound nbt) {
        write(nbt, getter.get());
    }

    /** Writes the built-in default into the compound under this option's key. */
    public void writeDefault(NbtCompound nbt) {
        write(nbt, defaultValue);
    }

    private void write(NbtCompound nbt, Object value) {
        switch (kind) {
            case BOOLEAN -> nbt.putBoolean(key, (Boolean) value);
            case NUMBER -> nbt.putFloat(key, (Float) value);
            case ID_LIST -> {
                NbtList list = new NbtList();
                @SuppressWarnings("unchecked") List<String> ids = (List<String>) value;
                for (String id : ids) list.add(NbtString.of(id));
                nbt.put(key, list);
            }
        }
    }

    /** Sets the option from the compound's value for its key. Returns false if there was none or it wasn't valid. */
    public boolean read(NbtCompound nbt) {
        Object value = valueIn(nbt);
        if (value == null) return false;
        setter.accept(value);
        return true;
    }

    /** The compound's value for this option, checked and clamped, or null if it has none or it isn't valid. */
    public Object valueIn(NbtCompound nbt) {
        if (!nbt.contains(key)) return null;
        return switch (kind) {
            case BOOLEAN -> nbt.contains(key, NbtElement.BYTE_TYPE) ? nbt.getBoolean(key) : null;
            case NUMBER -> {
                if (!nbt.contains(key, NbtElement.NUMBER_TYPE)) yield null;
                float value = nbt.getFloat(key);
                yield Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : null;
            }
            case ID_LIST -> {
                if (!nbt.contains(key, NbtElement.LIST_TYPE)) yield null;
                NbtList list = nbt.getList(key, NbtElement.STRING_TYPE);
                List<String> ids = new ArrayList<>();
                for (int i = 0; i < list.size(); i++) ids.add(list.getString(i));
                yield ids;
            }
        };
    }

    /** Parses a value as written in the config file into the compound. Returns false if it isn't valid. */
    public boolean parseInto(NbtCompound nbt, String text) {
        text = text.trim();
        switch (kind) {
            case BOOLEAN -> {
                if (!text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false")) return false;
                nbt.putBoolean(key, Boolean.parseBoolean(text));
            }
            case NUMBER -> {
                try {
                    nbt.putFloat(key, Float.parseFloat(text));
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            case ID_LIST -> {
                if (!text.startsWith("[") || !text.endsWith("]")) return false;
                NbtList list = new NbtList();
                for (String part : text.substring(1, text.length() - 1).split(",")) {
                    String id = part.trim();
                    if (id.length() >= 2 && (id.startsWith("\"") && id.endsWith("\"") || id.startsWith("'") && id.endsWith("'"))) {
                        id = id.substring(1, id.length() - 1).trim();
                    }
                    if (!id.isEmpty()) list.add(NbtString.of(id));
                }
                nbt.put(key, list);
            }
        }
        return true;
    }

    /** The compound's value for this option as written in the config file. */
    public String format(NbtCompound nbt) {
        Object value = valueIn(nbt);
        if (value == null) value = defaultValue;
        return switch (kind) {
            case BOOLEAN -> value.toString();
            case NUMBER -> formatNumber((Float) value);
            case ID_LIST -> {
                @SuppressWarnings("unchecked") List<String> ids = (List<String>) value;
                StringBuilder out = new StringBuilder("[");
                for (int i = 0; i < ids.size(); i++) {
                    if (i > 0) out.append(", ");
                    out.append('"').append(ids.get(i)).append('"');
                }
                yield out.append(']').toString();
            }
        };
    }

    public static String formatNumber(float value) {
        return value == Math.rint(value) ? Integer.toString((int) value) + ".0" : Float.toString(value);
    }
}
