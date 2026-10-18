package com.magicbanana666.hats.common;

import com.magicbanana666.hats.HatsReinvented;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A small TOML config (Fabric has no config API): sections, comments, strings, numbers, booleans and lists.
 * Missing or invalid values are reset to their default and the file is written back, like NeoForge's config specs.
 */
public final class SimpleConfig
{
    private final List<ConfigValue<?>> values = new ArrayList<>();
    private volatile boolean loaded;

    private SimpleConfig() {}

    public boolean isLoaded()
    {
        return loaded;
    }

    public void unload()
    {
        loaded = false;
    }

    /** Reads the file (creating / correcting it when needed). */
    public synchronized void load(Path file)
    {
        Map<String, Object> read = new HashMap<>();
        boolean exists = Files.exists(file);
        if(exists)
        {
            try
            {
                read = parse(Files.readAllLines(file));
            }
            catch(IOException | RuntimeException e)
            {
                HatsReinvented.LOGGER.warn("Could not read config {}, using the defaults: {}", file, e.toString());
            }
        }
        boolean correct = exists;
        for(ConfigValue<?> value : values)
        {
            correct &= value.set(read.get(value.path));
        }
        loaded = true;
        if(!correct)
        {
            try
            {
                Files.createDirectories(file.getParent());
                Files.writeString(file, write());
            }
            catch(IOException e)
            {
                HatsReinvented.LOGGER.warn("Could not write config {}: {}", file, e.toString());
            }
        }
    }

    private String write()
    {
        StringBuilder sb = new StringBuilder();
        String section = "";
        for(ConfigValue<?> value : values)
        {
            String valueSection = value.path.contains(".") ? value.path.substring(0, value.path.lastIndexOf('.')) : "";
            String key = value.path.substring(value.path.lastIndexOf('.') + 1);
            if(!valueSection.equals(section))
            {
                section = valueSection;
                sb.append('\n').append('[').append(section).append("]\n");
            }
            String indent = section.isEmpty() ? "" : "\t";
            if(value.comment != null)
            {
                for(String line : value.comment.split("\n"))
                {
                    sb.append(indent).append("#").append(line).append('\n');
                }
            }
            if(value.range != null)
            {
                sb.append(indent).append("#Range: ").append(value.range).append('\n');
            }
            sb.append(indent).append(key).append(" = ").append(format(value.get())).append('\n');
        }
        return sb.toString().stripLeading();
    }

    private static String format(Object value)
    {
        if(value instanceof String s)
        {
            return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
        }
        if(value instanceof List<?> list)
        {
            List<String> parts = new ArrayList<>();
            for(Object o : list)
            {
                parts.add(format(o));
            }
            return "[" + String.join(", ", parts) + "]";
        }
        return String.valueOf(value);
    }

    // ------------------------------------------------------------------ parsing

    private static Map<String, Object> parse(List<String> lines)
    {
        Map<String, Object> map = new HashMap<>();
        String section = "";
        StringBuilder pending = null;
        String pendingKey = null;
        for(String raw : lines)
        {
            String line = stripComment(raw).trim();
            if(pending != null)
            {
                pending.append(' ').append(line);
                if(balanced(pending))
                {
                    map.put(pendingKey, parseValue(pending.toString().trim()));
                    pending = null;
                }
                continue;
            }
            if(line.isEmpty())
            {
                continue;
            }
            if(line.startsWith("[") && line.endsWith("]") && !line.contains("="))
            {
                section = line.substring(1, line.length() - 1).trim();
                continue;
            }
            int eq = line.indexOf('=');
            if(eq <= 0)
            {
                continue;
            }
            String key = unquote(line.substring(0, eq).trim());
            String path = section.isEmpty() ? key : section + "." + key;
            StringBuilder value = new StringBuilder(line.substring(eq + 1).trim());
            if(balanced(value))
            {
                map.put(path, parseValue(value.toString()));
            }
            else
            {
                pending = value;
                pendingKey = path;
            }
        }
        return map;
    }

    private static String unquote(String key)
    {
        return key.length() >= 2 && key.startsWith("\"") && key.endsWith("\"") ? key.substring(1, key.length() - 1) : key;
    }

    private static String stripComment(String line)
    {
        boolean inString = false;
        for(int i = 0; i < line.length(); i++)
        {
            char c = line.charAt(i);
            if(c == '\\' && inString)
            {
                i++;
            }
            else if(c == '"')
            {
                inString = !inString;
            }
            else if(c == '#' && !inString)
            {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static boolean balanced(CharSequence value)
    {
        int depth = 0;
        boolean inString = false;
        for(int i = 0; i < value.length(); i++)
        {
            char c = value.charAt(i);
            if(c == '\\' && inString)
            {
                i++;
            }
            else if(c == '"')
            {
                inString = !inString;
            }
            else if(!inString && c == '[')
            {
                depth++;
            }
            else if(!inString && c == ']')
            {
                depth--;
            }
        }
        return depth <= 0;
    }

    @Nullable
    private static Object parseValue(String value)
    {
        if(value.startsWith("[") && value.endsWith("]"))
        {
            List<Object> list = new ArrayList<>();
            String inner = value.substring(1, value.length() - 1);
            StringBuilder current = new StringBuilder();
            boolean inString = false;
            for(int i = 0; i < inner.length(); i++)
            {
                char c = inner.charAt(i);
                if(c == '\\' && inString && i + 1 < inner.length())
                {
                    current.append(c).append(inner.charAt(++i));
                    continue;
                }
                if(c == '"')
                {
                    inString = !inString;
                }
                if(c == ',' && !inString)
                {
                    addElement(list, current.toString());
                    current.setLength(0);
                }
                else
                {
                    current.append(c);
                }
            }
            addElement(list, current.toString());
            return list;
        }
        if(value.length() >= 2 && value.startsWith("\"") && value.endsWith("\""))
        {
            StringBuilder sb = new StringBuilder();
            for(int i = 1; i < value.length() - 1; i++)
            {
                char c = value.charAt(i);
                if(c == '\\' && i + 1 < value.length() - 1)
                {
                    char next = value.charAt(++i);
                    sb.append(switch(next)
                    {
                        case 'n' -> '\n';
                        case 't' -> '\t';
                        default -> next;
                    });
                }
                else
                {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
        if(value.equals("true") || value.equals("false"))
        {
            return Boolean.parseBoolean(value);
        }
        try
        {
            if(!value.contains(".") && !value.contains("e") && !value.contains("E"))
            {
                return Long.parseLong(value);
            }
            return Double.parseDouble(value);
        }
        catch(NumberFormatException e)
        {
            return null;
        }
    }

    private static void addElement(List<Object> list, String element)
    {
        String trimmed = element.trim();
        if(!trimmed.isEmpty())
        {
            list.add(parseValue(trimmed));
        }
    }

    // ------------------------------------------------------------------ values

    public static class ConfigValue<T>
    {
        private final String path;
        @Nullable
        private final String comment;
        @Nullable
        private final String range;
        private final T defaultValue;
        private final Function<Object, T> reader;
        private volatile T value;

        private ConfigValue(String path, @Nullable String comment, @Nullable String range, T defaultValue, Function<Object, T> reader)
        {
            this.path = path;
            this.comment = comment;
            this.range = range;
            this.defaultValue = defaultValue;
            this.reader = reader;
            this.value = defaultValue;
        }

        public T get()
        {
            return value;
        }

        public T getDefault()
        {
            return defaultValue;
        }

        /** Sets the value read from the file; false (and the default is used) if it was missing or invalid. */
        private boolean set(@Nullable Object raw)
        {
            T read = raw == null ? null : reader.apply(raw);
            value = read != null ? read : defaultValue;
            return read != null;
        }
    }

    public static final class BooleanValue extends ConfigValue<Boolean>
    {
        private BooleanValue(String path, @Nullable String comment, boolean defaultValue)
        {
            super(path, comment, null, defaultValue, o -> o instanceof Boolean b ? b : null);
        }
    }

    public static final class DoubleValue extends ConfigValue<Double>
    {
        private DoubleValue(String path, @Nullable String comment, double defaultValue, double min, double max)
        {
            super(path, comment, min + " ~ " + max, defaultValue, o -> o instanceof Number n && n.doubleValue() >= min && n.doubleValue() <= max ? n.doubleValue() : null);
        }
    }

    public static final class IntValue extends ConfigValue<Integer>
    {
        private IntValue(String path, @Nullable String comment, int defaultValue, int min, int max)
        {
            super(path, comment, min + " ~ " + max, defaultValue, o -> o instanceof Long n && n >= min && n <= max ? n.intValue() : null);
        }
    }

    // ------------------------------------------------------------------ builder

    public static final class Builder
    {
        private final SimpleConfig config = new SimpleConfig();
        private final List<String> sections = new ArrayList<>();
        @Nullable
        private String comment;

        public Builder push(String section)
        {
            sections.add(section);
            return this;
        }

        public Builder pop()
        {
            sections.removeLast();
            return this;
        }

        public Builder comment(String comment)
        {
            this.comment = comment;
            return this;
        }

        private String path(String key)
        {
            return sections.isEmpty() ? key : String.join(".", sections) + "." + key;
        }

        private <V extends ConfigValue<?>> V add(V value)
        {
            config.values.add(value);
            comment = null;
            return value;
        }

        public ConfigValue<String> define(String key, String defaultValue)
        {
            return add(new ConfigValue<>(path(key), comment, null, defaultValue, o -> o instanceof String s ? s : null));
        }

        public BooleanValue define(String key, boolean defaultValue)
        {
            return add(new BooleanValue(path(key), comment, defaultValue));
        }

        public DoubleValue defineInRange(String key, double defaultValue, double min, double max)
        {
            return add(new DoubleValue(path(key), comment, defaultValue, min, max));
        }

        public IntValue defineInRange(String key, int defaultValue, int min, int max)
        {
            return add(new IntValue(path(key), comment, defaultValue, min, max));
        }

        /** A list of strings (empty allowed); invalid elements make the whole list fall back to the default. */
        public ConfigValue<List<? extends String>> defineStringList(String key, List<String> defaultValue, Predicate<String> validator)
        {
            return add(new ConfigValue<List<? extends String>>(path(key), comment, null, List.copyOf(defaultValue), o -> readList(o, e -> e instanceof String s && validator.test(s) ? s : null, true)));
        }

        /** A non empty list of integers. */
        public ConfigValue<List<? extends Integer>> defineIntList(String key, List<Integer> defaultValue, Predicate<Integer> validator)
        {
            return add(new ConfigValue<List<? extends Integer>>(path(key), comment, null, List.copyOf(defaultValue), o -> readList(o, e -> e instanceof Long l && l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE && validator.test(l.intValue()) ? l.intValue() : null, false)));
        }

        public SimpleConfig build()
        {
            return config;
        }
    }

    @Nullable
    private static <E> List<E> readList(Object raw, Function<Object, E> element, boolean allowEmpty)
    {
        if(!(raw instanceof List<?> list) || (!allowEmpty && list.isEmpty()))
        {
            return null;
        }
        List<E> out = new ArrayList<>();
        for(Object o : list)
        {
            E e = o == null ? null : element.apply(o);
            if(e == null)
            {
                return null;
            }
            out.add(e);
        }
        return List.copyOf(out);
    }
}
