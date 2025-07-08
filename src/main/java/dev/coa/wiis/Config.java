package dev.coa.wiis;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static dev.coa.wiis.WIIS.ID;
import static dev.coa.wiis.WIIS.LOGGER;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

@SuppressWarnings("rawtypes")
public abstract class Config<E extends Config.Entry> {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final String REGEX_TAG = "?";
    public static final String REGEX_ANY = "?.*";

    public boolean enabled;
    public boolean autosave = true;
    public boolean debug = false;
    public int permissionLevel = 4;

    public final Map<String, E> entries = new HashMap<>();

    public static String toKebabCase(String string) {
        return string.toLowerCase().replace(" ", "-").replace("_", "-");
    }

    public static boolean matches(String string, String regex) {
        return string.startsWith(REGEX_TAG)? regex.matches(string.substring(1)) : regex.equals(string);
    }

    public static <C extends Config> C load(Path path, Class<C> type) {
        if (Files.exists(path)) try (final BufferedReader reader = Files.newBufferedReader(path)){
            return load(reader, type);
        } catch (Exception ex) {
            LOGGER.warn("[{}]: ", ID.toUpperCase(), ex);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static <C extends Config> C load(Reader reader, Class<C> type) {
        try {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            C c = GSON.fromJson(json, type);
            if (json.has("entries")) json.getAsJsonObject("entries").asMap().forEach((k, v) -> c.entries.put(k, c.newEntry(v.getAsJsonObject())));
            return c;
        } catch (Exception ex) {
            LOGGER.warn("[{}]: ", ID.toUpperCase(), ex);
        }
        return null;
    }

    public static <C extends Config> C load(String string, Class<C> type) {
        return load(new StringReader(string), type);
    }

    public List<Map.Entry<String, E>> findEntries(String regex) {
        List<Map.Entry<String, E>> entries = new ArrayList<>();

        this.entries.entrySet().forEach(entity -> {
            if (matches(entity.getKey(), regex)) entries.add(entity);
        });

        return entries;
    }

    public void editEntry(String regex, Consumer<E> consumer) {
        entries.forEach((k, v) -> {
            if (matches(k, regex)) consumer.accept(v);
        });
    }

    public boolean hasEntry(String regex) {
        for (Map.Entry<String, E> entity : entries.entrySet()) if (matches(entity.getKey(), regex)) return true;
        return false;
    }

    public E setEntry(String key, E entry) {
        entries.put(key, entry);
        return entry;
    }

    public E addEntry(String key, E entry) {
        return entries.containsKey(key)? entries.get(key) : setEntry(key, entry);
    }

    public void removeEntries(String... keys) {
        for (String key : keys) entries.remove(key);
    }

    public boolean canSpawn(String regex, @Nullable Object reason, @Nullable Object world, @Nullable Object biome) {
        for (Map.Entry<String, E> entry : entries.entrySet()) if (matches(entry.getKey(), regex)) return entry.getValue().canSpawn(reason, world, biome);
        return true;
    }

    public abstract E newEntry(JsonObject json);

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("enabled", enabled);
        json.addProperty("autosave", autosave);
        json.addProperty("debug", debug);
        json.addProperty("permissionLevel", permissionLevel);

        JsonObject jsonEntities = new JsonObject();
        if (!entries.isEmpty()) entries.forEach((k, v) -> jsonEntities.add(k, v.toJson()));

        json.add("entries", jsonEntities);
        return json;
    }

    public void save(Path path) {
        if (path != null) try {
            if (!Files.exists(path.getParent())) Files.createDirectories(path.getParent());

            try (final BufferedWriter writer = Files.newBufferedWriter(path)) {
                GSON.toJson(toJson(), writer);
            }
        } catch (Exception ex) {
            LOGGER.warn("[{}]: ", ID.toUpperCase(), ex);
        }
    }

    public static interface Biome extends ISetting {}

    public static interface World<B extends Biome> extends ISetting {
        default B addBiome(String name, B biome) {
            biomes().putIfAbsent(name, biome);
            return biome;
        }

        B newBiome(JsonObject json);

        default void removeBiome(String name) {
            biomes().remove(name);
        }

        default Optional<B> getBiome(String name) {
            return Optional.ofNullable(biomes().get(name));
        }

        default Optional<B> anyBiome() {
            return getBiome(REGEX_ANY);
        }

        @NotNull Map<String, B> biomes();

        default void readJson(JsonObject json) {
            if (json.has("biomes")) json.getAsJsonObject("biomes").asMap().forEach((k, v) -> biomes().put(k, newBiome(v.getAsJsonObject())));
            ISetting.super.readJson(json);
        }

        default void writeJson(JsonObject json) {
            if (!biomes().isEmpty()) biomes().forEach((k, v) -> json.add(k, v.toJson()));
            ISetting.super.writeJson(json);
        }

        default boolean canSpawn(Object reason, Object biome) {
            var rawBiome = biome == null? REGEX_ANY : biome instanceof String s? s : biome.toString();
            var canSpawn = canSpawn(reason);

            if (rawBiome.startsWith(REGEX_TAG))
                for (Map.Entry<String, B> entry : biomes().entrySet())
                    if (matches(entry.getKey(), rawBiome)) return canSpawn && entry.getValue().canSpawn(reason);
            return canSpawn && (!biomes().containsKey(rawBiome) || biomes().get(rawBiome).canSpawn(reason));
        }
    }

    @SuppressWarnings("rawtypes")
    public static interface Entry<W extends World> extends ISetting {
        default W addWorld(String name, W world) {
            worlds().putIfAbsent(name, world);
            return world;
        }

        W newWorld(JsonObject json);

        default void removeWorld(String name) {
            worlds().remove(name);
        }

        default Optional<W> getWorld(String name) {
            return Optional.ofNullable(worlds().get(name));
        }

        default Optional<W> anyWorld() {
            return getWorld(REGEX_ANY);
        }

        @NotNull Map<String, W> worlds();

        default void readJson(JsonObject json) {
            if (json.has("worlds")) json.getAsJsonObject("worlds").asMap().forEach((k, v) -> worlds().put(k, newWorld(v.getAsJsonObject())));
            ISetting.super.readJson(json);
        }

        default void writeJson(JsonObject json) {
            if (!worlds().isEmpty()) worlds().forEach((k, v) -> json.add(k, v.toJson()));
            ISetting.super.writeJson(json);
        }

        default boolean canSpawn(Object reason, Object world, Object biome) {
            var rawWorld = world == null? REGEX_ANY : world instanceof String s? s : world.toString();
            var canSpawn = canSpawn(reason);

            if (rawWorld.startsWith(REGEX_TAG))
                for (Map.Entry<String, W> entry : worlds().entrySet())
                    if (matches(entry.getKey(), rawWorld)) return canSpawn && entry.getValue().canSpawn(reason, biome);
            return canSpawn && (!worlds().containsKey(rawWorld) || worlds().get(rawWorld).canSpawn(reason, biome));
        }
    }

    public static interface ISetting {
        default void discardReason(Object reason, boolean add) {
            if (reason == null) return;
            var rawReason = toKebabCase(reason instanceof String s? s : reason.toString());
            if (add) discardReasons().add(rawReason);
            else discardReasons().remove(rawReason);
        }

        default boolean isDiscardedBy(Object reason) {
            if (reason == null) return false;
            return discardReasons().contains(toKebabCase(reason instanceof String s? s : reason.toString()));
        }

        @NotNull List<String> discardReasons();

        float chance();

        boolean isDiscarded();

        boolean canSpawn(Object reason);

        default void readJson(JsonObject json) {
            if (this instanceof BasicSettings basicSettings) basicSettings.readJson(json);
        }

        default void writeJson(JsonObject json) {
            if (this instanceof BasicSettings basicSettings) basicSettings.writeJson(json);
        }

        default JsonObject toJson() {
            JsonObject json = new JsonObject();
            writeJson(json);
            return json;
        }
    }

    public static abstract class BasicSettings implements ISetting {
        private final List<String> discardReasons = new ArrayList<>();
        public float chance = 1f;
        public boolean discard;

        @Override
        public @NotNull List<String> discardReasons() {
            return discardReasons;
        }

        @Override
        public float chance() {
            return chance;
        }

        @Override
        public boolean isDiscarded() {
            return discard;
        }

        @Override
        public boolean canSpawn(Object reason) {
            if (isDiscarded()) return false;
            return (chance() != 1f || Math.random() <= chance()) && !isDiscardedBy(reason);
        }

        @Override
        public void readJson(JsonObject json) {
            if (json.has("chance")) chance = json.getAsJsonPrimitive("chance").getAsFloat();
            if (json.has("discardReasons")) discardReasons().addAll(GSON.fromJson(json.get("discardReasons"), new TypeToken<List<String>>() {}));
            if (json.has("discard")) discard = json.getAsJsonPrimitive("discard").getAsBoolean();
        }

        @Override
        public void writeJson(JsonObject json) {
            if (chance() != 1f) json.addProperty("chance", chance());
            if (!discardReasons().isEmpty()) json.add("discardReasons", GSON.toJsonTree(discardReasons()));
            if (isDiscarded()) json.addProperty("discard", isDiscarded());
        }
    }
}