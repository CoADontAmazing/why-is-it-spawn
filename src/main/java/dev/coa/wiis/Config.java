package dev.coa.wiis;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
//import java.util.function.Predicate;

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
                WIIS.LOGGER.warn("[" + WIIS.ID.toUpperCase() + "]: ", ex);
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

    public boolean allowSpawn(String id, String reason, String world/*, Predicate<List<String>> nbtPredicate*/) {
        for (Map.Entry<String, Entry> entry : entities.entrySet()) {
            if (validate(entry.getKey(), id)) {
                Entry idEntry = entry.getValue();
                return !(idEntry.despawnInstantly || idEntry.excludedReasons.contains(reason) || idEntry.excludedWorlds.contains(world))/* && !nbtPredicate.test(idEntry.excludedNbt)*/;
            }
        }
        return true;
    }

    public boolean allowSpawn(String id, Object reason, String world/*, Predicate<List<String>> nbtPredicate*/) {
        return allowSpawn(id, reason == null? null: toKebabCase(reason.toString()), world/*, nbtPredicate*/);
    }

//    public boolean allowSpawn(String id, String reason) {
//        return allowSpawn(id, reason, /*nbt -> false*/);
//    }
//
//    public boolean allowSpawn(String id, Object reason) {
//        return allowSpawn(id, reason, /*nbt -> false*/);
//    }

    public void save(Path path) {
        if (path == null) return;
        try {
            if (!Files.exists(path.getParent())) Files.createDirectories(path.getParent());
            final BufferedWriter writer = Files.newBufferedWriter(path);
            GSON.toJson(this, writer);
            writer.close();
        } catch (Exception ex) {
            WIIS.LOGGER.warn("[" + WIIS.ID.toUpperCase() + "]: ", ex);
        }
    }

    public static final class Entry {
        private final List<String> excludedReasons;
        private final List<String> excludedWorlds;
        private Boolean despawnInstantly;

        public Entry(List<String> excludedReasons, List<String> excludedWorlds/*, List<String> excludedNbt*/, Boolean despawnInstantly) {
            this.excludedReasons = excludedReasons;
            this.excludedWorlds = excludedWorlds;
            this.despawnInstantly = despawnInstantly;
        }

        public Entry(List<String> excludedReasons, Boolean despawnInstantly) {
                this(excludedReasons, new ArrayList<>(), despawnInstantly);
        }

        public Entry(Boolean despawnInstantly) {
            this(new ArrayList<>(), despawnInstantly);
        }

        public void excludeReason(String reason, boolean exclude) {
            if (reason == null) return;
            if (exclude) excludedReasons.add(reason);
            else excludedReasons.remove(reason);
        }

        public void excludeReason(Object object, boolean exclude) {
            excludeReason(object == null ? null : toKebabCase(object.toString()), exclude);
        }

        public List<String> excludedReasons() {
            return excludedReasons;
        }

        public List<String> excludedWorlds() {
            return excludedWorlds;
        }

        public Boolean despawnInstantly() {
            return despawnInstantly;
        }

        public void despawnInstantly(Boolean enable) {
            this.despawnInstantly = enable;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (Entry) obj;
            return Objects.equals(this.excludedReasons, that.excludedReasons) &&
                    Objects.equals(this.excludedWorlds, that.excludedWorlds) &&
                    Objects.equals(this.despawnInstantly, that.despawnInstantly);
        }

        @Override
        public String toString() {
            return "Entry[" +
                    "excludedReasons=" + excludedReasons + ", " +
                    "excludedWorlds=" + excludedWorlds + ", " +
                    "despawnInstantly=" + despawnInstantly + ']';
        }
    }

    public static void main(String[] args) {
        Config config1 = GSON.fromJson("{\"enabled\": true, \"autosave\": true, \"debug\": false, \"permissionLevel\": 4, \"entities\": {\"minecraft:axolotl\": { \"excludedReasons\": [\"natural\", \"breeding\"], \"despawnInstantly\": true} } }", Config.class);
        System.out.println(!config1.allowSpawn("minecraft:axolotl", null, null));
    }
}
