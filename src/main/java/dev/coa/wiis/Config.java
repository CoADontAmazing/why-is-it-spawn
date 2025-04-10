package dev.coa.wiis;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.entity.SpawnReason;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class Config {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final String REGEX_TAG = "?";

    public boolean enabled;
    public boolean autosave;
    public int permissionLevel = 4;

    public final Map<String, Entry> entities = new HashMap<>();

    public static boolean validate(String raw, String validator) {
        if (raw.startsWith(REGEX_TAG)) return validator.matches(raw.substring(1));
        return validator.equals(raw);
    }

    public static Config load(Path path) {
        if (Files.exists(path)) {
            try (final BufferedReader reader = Files.newBufferedReader(path)) {
                return GSON.fromJson(reader, Config.class);
            } catch (Exception ex) {
                WIIS.LOGGER.warn("[" + WIIS.ID.toUpperCase() + "] ", ex);
            }
        }
        return new Config();
    }

    public void editEntry(String id, Consumer<Entry> consumer) {
        for (Map.Entry<String, Entry> entry : entities.entrySet()) {
            if (validate(entry.getKey(), id))
                consumer.accept(entry.getValue());
        }
    }

    public boolean allowSpawn(String id, String reason, Predicate<List<String>> nbtPredicate) {
        for (Map.Entry<String, Entry> entry : entities.entrySet()) {
            if (validate(entry.getKey(), id)) {
                Entry idEntry = entry.getValue();
                return !idEntry.despawnInstantly && !idEntry.excludedReasons.contains(reason) && !nbtPredicate.test(idEntry.excludedNbt);
            }
        }
        return true;
    }

    public boolean allowSpawn(String id, Object reason, Predicate<List<String>> nbtPredicate) {
        return allowSpawn(id, reason == null? null: reason.toString().toLowerCase().replace(" ", "-").replace("_", "-"), nbtPredicate);
    }

    public boolean allowSpawn(String id, String reason) {
        return allowSpawn(id, reason, nbt -> false);
    }

    public boolean allowSpawn(String id, Object reason) {
        return allowSpawn(id, reason, nbt -> false);
    }

    public void save(Path path) {
        if (path == null) return;
        try {
            if (!Files.exists(path.getParent())) Files.createDirectories(path.getParent());
            final BufferedWriter writer = Files.newBufferedWriter(path);
            GSON.toJson(this, writer);
            writer.close();
        } catch (Exception ex) {
            WIIS.LOGGER.warn("[" + WIIS.ID.toUpperCase() + "] ", ex);
        }
    }

    public record Entry(List<String> excludedReasons, List<String> excludedNbt, Boolean despawnInstantly) {
        public Entry(List<String> excludedReasons, Boolean despawnInstantly) {
            this(excludedReasons, new ArrayList<>(), despawnInstantly);
        }

        public Entry(Boolean despawnInstantly) {
            this(new ArrayList<>(), despawnInstantly);
        }

        public void excludeReason(String reason, boolean exclude) {
            if (exclude) excludedReasons.add(reason);
            else excludedReasons.remove(reason);
        }

        public void excludeReason(Object object, boolean exclude) {
            excludeReason(object == null? null: object.toString().toLowerCase().replace(" ", "-").replace("_", "-"), exclude);
        }
    }

    public static void main(String[] args) {
        Config config = new Config();

        Entry entry = new Entry(false);

        entry.excludeReason(SpawnReason.NATURAL, true);
        entry.excludeReason(SpawnReason.EVENT, true);
        entry.excludeReason(SpawnReason.SPAWN_EGG, true);
        entry.excludeReason(SpawnReason.COMMAND, true);
        entry.excludeReason("hello", true);

        config.entities.put("minecraft:pigman", entry);

        JsonObject jsonObject = GSON.toJsonTree(config).getAsJsonObject();

        System.out.println(GSON.toJson(config));

        Config config1 = GSON.fromJson(jsonObject, Config.class);
        config1.entities.get("minecraft:pigman").excludeReason("hello", false);

        System.out.println(!config1.allowSpawn("minecraft:pigman", "hello"));
    }
}
