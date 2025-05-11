package dev.coa.wiis;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

import static dev.coa.wiis.WIIS.*;

public class Config {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final String REGEX_TAG = "?";

    public boolean enabled;
    public boolean autosave = true;
    public boolean debug = false;
    public int permissionLevel = 4;

    public final Map<String, Entry> entities = new HashMap<>();

    public static boolean validate(String raw, String validator) {
        if (raw.startsWith(REGEX_TAG)) return validator.matches(raw.substring(1));
        return validator.equals(raw);
    }

    public static String toKebabCase(String string) {
        return string.toLowerCase().replace(" ", "-").replace("_", "-");
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

    public List<Map.Entry<String, Entry>> findEntries(String validator) {
        List<Map.Entry<String, Entry>> entryList = new ArrayList<>();
        for (Map.Entry<String, Entry> entry : entities.entrySet()) {
            if (validate(entry.getKey(), validator)) entryList.add(entry);
        }

        return entryList;
    }

    public boolean hasEntry(String id) {
        for (Map.Entry<String, Entry> entry : entities.entrySet()) {
            if (validate(entry.getKey(), id)) return true;
        }
        return false;
    }

    public void editEntry(String id, Consumer<Entry> consumer) {
        for (Map.Entry<String, Entry> entry : entities.entrySet()) {
            if (validate(entry.getKey(), id))
                consumer.accept(entry.getValue());
        }
    }

    public boolean allowSpawn(String id, Object reason, Object world, Object biome) {
        for (Map.Entry<String, Entry> entry : entities.entrySet()) {
            if (validate(entry.getKey(), id))
                return entry.getValue().allowSpawn(reason, world, biome);
        }
        return true;
    }

    public void save(Path path) {
        if (path == null) return;
        try {
            if (!Files.exists(path.getParent())) Files.createDirectories(path.getParent());
            final BufferedWriter writer = Files.newBufferedWriter(path);
            GSON.toJson(this, writer);
            writer.close();
        } catch (Exception ex) {
            LOGGER.warn("[" + ID.toUpperCase() + "]: ", ex);
        }
    }

    public static class World extends ElementSettings {
        public Map<String, Biome> biomes;

        public World() {
            this(new HashMap<>());
        }

        public World(Map<String, Biome> biomes) {
            this.biomes = new HashMap<>(biomes);
        }

        public Biome newBiome(String name) {
            Biome biome = new Biome();
            if (biomes == null) this.biomes = new HashMap<>();
            biomes.put(name, biome);
            return biome;
        }

        public void removeBiome(String name) {
            if (biomes != null) biomes.remove(name);
        }

        public boolean allowSpawn(Object reason, Object biome) {
            if (biome != null) biome = biome.toString();
            if (biomes != null && (biome == null? "" : biome.toString()).startsWith(REGEX_TAG))
                for (Map.Entry<String, Biome> entry : biomes.entrySet())
                    if (validate(entry.getKey(), biome.toString())) return allowSpawn(reason) && (biomes.containsKey(biome) ? biomes.get(biome).allowSpawn(reason) : true);
            return allowSpawn(reason) && (biomes != null && biomes.containsKey(biome) ? biomes.get(biome).allowSpawn(reason) : true);
        }

        public static class Biome extends ElementSettings {}
    }

    public static class Entry extends ElementSettings {
        public Map<String, World> worlds;

        public Entry() {
            this(new HashMap<>());
        }

        public Entry(Map<String, World> worlds) {
            this.worlds = worlds;
        }

        public World newWorld(String name) {
            World world = new World();
            if (worlds == null) this.worlds = new HashMap<>();
            worlds.put(name, world);
            return world;
        }

        public World.Biome newBiome(String worldName, String biomeName) {
            World world = worlds.containsKey(worldName)? worlds.get(worldName) : newWorld(worldName);
            return world.newBiome(biomeName);
        }

        public void removeWorld(String name) {
            if (worlds != null) worlds.remove(name);
        }

        public boolean allowSpawn(Object reason, Object world, Object biome) {
            if (world != null) world = world.toString();
            if (worlds != null && (world == null? "" : world.toString()).startsWith(REGEX_TAG))
                for (Map.Entry<String, World> entry : worlds.entrySet())
                    if (validate(entry.getKey(), world.toString())) return allowSpawn(reason) && (worlds.containsKey(world)? worlds.get(world).allowSpawn(reason, biome) : true);
            return allowSpawn(reason) && (worlds.containsKey(world)? worlds.get(world).allowSpawn(reason, biome) : true);
        }
    }

    public static abstract class ElementSettings {
        private List<String> discardReasons;
        public Float chance;
        public Boolean discard;

        public void discardReason(Object reason, boolean add) {
            if (reason == null) return;
            if (discardReasons == null) discardReasons = new ArrayList<>();
            reason = toKebabCase(reason.toString());
            if (add) discardReasons.add(reason.toString());
            else discardReasons.remove(reason.toString());
        }

        public boolean isDiscarded(Object reason) {
            if (reason == null) return false;
            if (discardReasons == null) return false;
            reason = toKebabCase(reason.toString());
            return discardReasons.contains(reason.toString());
        }

        public List<String> discardReasons() {
            return List.copyOf(discardReasons);
        }

        public boolean isDiscarded() {
            return discard != null && discard;
        }

        public float chance() {
            return chance != null? chance : 1f;
        }

        public boolean allowSpawn(Object reason) {
            if (isDiscarded()) return false;
            return (chance == null? true : Math.random() <= chance) && !isDiscarded(reason);
        }
    }
}