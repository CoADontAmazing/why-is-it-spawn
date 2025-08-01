package dev.coa.wiis.fabric;

import com.google.gson.JsonObject;

import static dev.coa.wiis.fabric.FabricWIIS.*;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.*;

public class FabricConfig extends dev.coa.wiis.Config<FabricConfig.FabricEntry> {
    public void editEntry(Identifier id, Consumer<FabricEntry> consumer) {
        editEntry(id.toString(), consumer);
    }

    public void editEntry(EntityType<?> entityType, Consumer<FabricEntry> consumer) {
        editEntry(EntityType.getId(entityType), consumer);
    }

    public boolean canSpawn(Identifier id, SpawnReason reason, net.minecraft.world.World world, RegistryKey<net.minecraft.world.biome.Biome> biome) {
        return canSpawn(id.toString(), reason, world.getDimensionKey().getValue(), biome.getValue());
    }

    public boolean canSpawn(Entity entity, SpawnReason reason, net.minecraft.world.World world, RegistryKey<net.minecraft.world.biome.Biome> biome) {
        return canSpawn(entity.getType(), reason, world, biome);
    }

    public boolean canSpawn(EntityType<?> entityType, SpawnReason reason, net.minecraft.world.World world, RegistryKey<net.minecraft.world.biome.Biome> biome) {
        return canSpawn(EntityType.getId(entityType), reason, world, biome);
    }

    @Override
    public FabricEntry newEntry(JsonObject json) {
        var e = new FabricEntry();
        e.readJson(json);
        return e;
    }

    public static class FabricSettings extends dev.coa.wiis.Config.BasicSettings {}

    public static class FabricBiome extends FabricSettings implements dev.coa.wiis.Config.Biome {}

    public static class FabricWorld extends FabricSettings implements dev.coa.wiis.Config.World<FabricBiome> {
        private final Map<String, FabricBiome> biomeMap = new HashMap<>();

        public FabricBiome createBiome(String name) {
            return addBiome(name, new FabricBiome());
        }

        @Override
        public FabricBiome newBiome(JsonObject json) {
            var b = new FabricBiome();
            b.readJson(json);
            return b;
        }

        @Override
        public @NotNull Map<String, FabricBiome> biomes() {
            return biomeMap;
        }
    }

    public static class FabricEntry extends FabricSettings implements dev.coa.wiis.Config.Entry<FabricWorld> {
        private final Map<String, FabricWorld> worldMap = new HashMap<>();

        public FabricWorld createWorld(String name) {
            return addWorld(name, new FabricWorld());
        }

        public FabricBiome addBiome(String world, String name, FabricBiome biome) {
            return getWorld(world).orElse(createWorld(name)).addBiome(name, biome);
        }

        public FabricBiome addBiome(String name, FabricBiome biome) {
            return anyWorld().orElse(createWorld(REGEX_ANY)).addBiome(name, biome);
        }

        @Override
        public FabricWorld newWorld(JsonObject json) {
            var w = new FabricWorld();
            w.readJson(json);
            return w;
        }

        @Override
        public @NotNull Map<String, FabricWorld> worlds() {
            return worldMap;
        }
    }

    public static MutableText fancyKey(String key) {
        return Text.literal(key).formatted(Formatting.GREEN);
    }

    @SuppressWarnings("rawtypes")
    public static MutableText fancyValue(Object value) {
        return value instanceof Text text? (MutableText) text : value instanceof Map map? fancyMap(map) : value instanceof Collection<?> collection? fancyCollection(collection) : Text.literal(value.toString()).formatted((value instanceof Number? Formatting.RED : value instanceof String? Formatting.YELLOW : value instanceof Boolean? Formatting.LIGHT_PURPLE: Formatting.AQUA));
    }

    public static MutableText fancyEntry(String key, Object value) {
        return Text.empty().append(fancyKey(key)).append(": ").append(fancyValue(value));
    }

    public static MutableText fancyMap(Map<?, ?> map) {
        final MutableText text = Text.empty();
        text.append("{");
        final Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();
        iterator.forEachRemaining(entry -> {
            text.append(fancyEntry(entry.getKey().toString(), entry.getValue()));
            if (iterator.hasNext()) text.append(WIIS_SEPARATOR);
        });
        return text.append("}");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static MutableText fancyCollection(Collection<?> collection) {
        final MutableText text = Text.empty();
        text.append("[");
        final Iterator iterator = collection.iterator();
        iterator.forEachRemaining(entry -> {
            text.append(fancyValue(entry));
            if (!iterator.hasNext()) text.append(WIIS_SEPARATOR);
        });
        return text.append("]");
    }

    public static final Text WIIS_SEPARATOR = Text.literal(", ").formatted(Formatting.GOLD);

    public static final BiFunction<Boolean, Boolean, MutableText> ENABLED_TEXT = (set, enable) -> Text.translatable("wiis.enabled." + (set? "set" : "get"), enable);
    public static final BiFunction<Boolean, Integer, MutableText> PERMISSION_LEVEL_TEXT = (set, level) -> Text.translatable("wiis.permissionLevel." + (set? "set" : "get"), level);
    public static final BiFunction<Boolean, Boolean, MutableText> AUTOSAVE_TEXT = (set, enable) -> Text.translatable("wiis.autosave." + (set? "set" : "get"), enable);

    public static final Supplier<MutableText> INFO_TEXT = () -> Text.literal( ID.toUpperCase() + " (" + NAME + ")" + Formatting.GREEN + " v" + VERSION + (FabricWIIS.CONFIG.debug? Formatting.GOLD + " (debug) " : " ") + Formatting.RESET).append(Text.translatable("wiis.info")).append("\n")
            .append(Text.literal("Modrinth")
                    .styled(e -> e.withColor(0x23D86F).withUnderline(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://modrinth.com/mod/wiis"))
                    )
            ).append(" | ").append(Text.literal("CurseForge")
                    .styled(e -> e.withColor(0xF06030).withUnderline(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://www.curseforge.com/minecraft/mc-mods/wiis"))
                    )
            );

    public static final MutableText RELOAD_TEXT = Text.translatable("wiis.reload");
    public static final BiFunction<Integer, String, MutableText> RESTORE_TEXT = (count, key) -> {
        if (count != null && key != null) return Text.translatable("wiis.restore.with_regex", count, key);
        else if (count == null) return Text.translatable("wiis.restore.single", key);
        else {
            if (count == 0) return Text.translatable("wiis.restore.none");
            return Text.translatable("wiis.restore.all", count);
        }
    };

    public static final BiFunction<String, FabricSettings, MutableText> QUERY_ENTRY_TEXT = (entryKey, entry) -> Text.empty().append(Text.translatable("wiis.query", entryKey)).append(fancyMap(GSON.fromJson(entry.toJson(), Map.class)));

    public static final BiFunction<String, Text, MutableText> QUERY_DISCARDREASONS_TEXT = (entry, text) -> Text.empty().append(Text.translatable("wiis.query.discardreason.all", entry)).append(text);

    public static final Function<Object[], MutableText> MODIFY_DISCARDREASON_TEXT = (args) -> Text.translatable("wiis.modify.discardreason." + (args[2].equals(true)? "add" : "remove"), args[0], args[1]);
    public static final Function<Object[], MutableText> QUERY_DISCARDREASON_TEXT = (args) -> Text.translatable("wiis.query.discardreason." + (args[2].equals(true)? "present" : "missing"), args[0], args[1]);

    public static final BiFunction<String, Float, MutableText> MODIFY_CHANCE_TEXT = (valuePath, value) -> Text.translatable("wiis.modify.chance", valuePath, value);
    public static final BiFunction<String, Float, MutableText> QUERY_CHANCE_TEXT = (valuePath, value) -> Text.translatable("wiis.query.chance", valuePath, value);

    public static final BiFunction<String, Boolean, MutableText> MODIFY_DISCARD_TEXT = (valuePath, value) -> Text.translatable("wiis.modify.discard", valuePath, value);
    public static final BiFunction<String, Boolean, MutableText> QUERY_DISCARD_TEXT = (valuePath, value) -> Text.translatable("wiis.query.discard", valuePath, value);

    public static final Function<String, MutableText> ENTRY_NOT_FOUND_TEXT = key -> Text.translatable("wiis.entry_not_found", key).formatted(Formatting.RED);
    public static final MutableText UNAVAILABLE_TEXT = Text.translatable("wiis.unavailable").formatted(Formatting.RED);
}