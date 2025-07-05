package dev.coa.wiis;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

import com.google.gson.*;
import org.jetbrains.annotations.NotNull;

import static dev.coa.wiis.WIIS.ID;
import static dev.coa.wiis.WIIS.LOGGER;

@SuppressWarnings("rawtypes")
public class Config<E extends Config.Entry> {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final String REGEX_TAG = "?";
    public static final String REGEX_ANY = "?.*";

    public boolean enabled;
    public boolean autosave = true;
    public boolean debug = false;
    public int permissionLevel = 4;

    public final Map<String, E> entities = new HashMap<>();

    public static String toKebabCase(String string) {
        return string.toLowerCase().replace(" ", "-").replace("_", "-");
    }

    public static boolean validate(String raw, String validator) {
        return raw.startsWith(REGEX_TAG)? validator.matches(raw.substring(1)) : validator.equals(raw);
    }

    public static <C extends Config> C load(Path path, Class<C> type) {
        if (Files.exists(path)) {
            try (final BufferedReader reader = Files.newBufferedReader(path)) {
                return GSON.fromJson(reader, type);
            } catch (Exception ex) {
                LOGGER.warn("[" + ID.toUpperCase() + "]: ", ex);
            }
        }
        return type.cast(new Config());
    }

    public List<Map.Entry<String, E>> findEntries(String validator) {
        List<Map.Entry<String, E>> entries = new ArrayList<>();

        entities.entrySet().forEach(entity -> {
            if (validate(entity.getKey(), validator)) entries.add(entity);
        });

        return entries;
    }

    public void editEntry(String entry, Consumer<E> consumer) {
        entities.forEach((key, value) -> {
            if (validate(key, entry)) consumer.accept(value);
        });
    }

    public boolean hasEntry(String entry) {
        for (Map.Entry<String, E> entity : entities.entrySet()) if (validate(entity.getKey(), entry)) return true;
        return false;
    }

    public boolean canSpawn(String id, Object reason, Object world, Object biome) {
        for (Map.Entry<String, E> entry : entities.entrySet()) if (validate(entry.getKey(), id)) return entry.getValue().canSpawn(reason, world, biome);
        return true;
    }

    public JsonObject toJson() {
        return null;
    }

    public void save(Path path) {
        if (path != null) try {
            if (!Files.exists(path.getParent())) Files.createDirectories(path.getParent());
            final BufferedWriter writer = Files.newBufferedWriter(path);
            GSON.toJson(toJson(), writer);
            writer.close();
        } catch (Exception ex) {
            LOGGER.warn("[" + ID.toUpperCase() + "]: ", ex);
        }
    }

    public static interface Biome extends ISetting {}

    public static interface World<B extends Biome> extends ISetting {
        default B addBiome(String name, B biome) {
            biomes().putIfAbsent(name, biome);
            return biome;
        }

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

        default void writeJson(JsonObject json) {
            ISetting.super.writeJson(json);

        }

        default boolean canSpawn(Object reason, Object biome) {
            var rawBiome = biome == null? REGEX_ANY : biome instanceof String s? s : biome.toString();
            var canSpawn = canSpawn(reason);

            if (rawBiome.startsWith(REGEX_TAG))
                for (Map.Entry<String, B> entry : biomes().entrySet())
                    if (validate(entry.getKey(), rawBiome)) return canSpawn && entry.getValue().canSpawn(reason);
            return canSpawn && (!biomes().containsKey(rawBiome) || biomes().get(rawBiome).canSpawn(reason));
        }
    }

    @SuppressWarnings("rawtypes")
    public static interface Entry<W extends World> extends ISetting {
        default W addWorld(String name, W world) {
            worlds().putIfAbsent(name, world);
            return world;
        }

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

        default void writeJson(JsonObject json) {
            ISetting.super.writeJson(json);

        }

        default boolean canSpawn(Object reason, Object world, Object biome) {
            var rawWorld = world == null? REGEX_ANY : world instanceof String s? s : world.toString();
            var canSpawn = canSpawn(reason);

            if (rawWorld.startsWith(REGEX_TAG))
                for (Map.Entry<String, W> entry : worlds().entrySet())
                    if (validate(entry.getKey(), rawWorld)) return canSpawn && entry.getValue().canSpawn(reason, biome);
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
        public Float chance;
        public Boolean discard;

        @Override
        public @NotNull List<String> discardReasons() {
            return discardReasons;
        }

        @Override
        public float chance() {
            return chance != null? chance : 1f;
        }

        @Override
        public boolean isDiscarded() {
            return discard != null && discard;
        }

        @Override
        public boolean canSpawn(Object reason) {
            if (isDiscarded()) return false;
            return (chance == null || Math.random() <= chance) && !isDiscardedBy(reason);
        }

        @Override
        public void writeJson(JsonObject json) {

        }
    }
}