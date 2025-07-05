package dev.coa.wiis.fabric;

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

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static dev.coa.wiis.WIIS.ID;
import static dev.coa.wiis.WIIS.NAME;

public class Fabrec {
    public static class FabricSettings extends dev.coa.wiis.Config.BasicSettings {}

    public static class Biome extends FabricSettings implements dev.coa.wiis.Config.Biome {}

    public static class World extends FabricSettings implements dev.coa.wiis.Config.World<Fabrec.Biome> {
        private final Map<String, Biome> biomeMap = new HashMap<>();

        public Biome createBiome(String name) {
            return addBiome(name, new Biome());
        }

        @Override
        public @NotNull Map<String, Biome> biomes() {
            return biomeMap;
        }
    }

    public static class Entry extends FabricSettings implements dev.coa.wiis.Config.Entry<Fabrec.World> {
        private final Map<String, World> worldMap = new HashMap<>();

        public World createWorld(String name) {
            return addWorld(name, new World());
        }

        public Biome addBiome(String world, String name, Biome biome) {
            return getWorld(world).orElse(createWorld(name)).addBiome(name, biome);
        }

        public Biome addBiome(String name, Biome biome) {
            return anyWorld().orElse(createWorld(Config.REGEX_ANY)).addBiome(name, biome);
        }

        @Override
        public @NotNull Map<String, World> worlds() {
            return worldMap;
        }
    }

    public static class Config extends dev.coa.wiis.Config<Entry> {
        public void editEntry(Identifier id, Consumer<Fabrec.Entry> consumer) {
            editEntry(id.toString(), consumer);
        }

        public void editEntry(EntityType<?> entityType, Consumer<Fabrec.Entry> consumer) {
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
    }

    public static MutableText fancyKey(String key) {
        return Text.literal(key).formatted(Formatting.GREEN);
    }

    public static MutableText fancyValue(Object value) {
        return value instanceof Text text? (MutableText) text : Text.literal(value.toString()).formatted((value instanceof Number? Formatting.RED : value instanceof String? Formatting.YELLOW : value instanceof Boolean? Formatting.LIGHT_PURPLE: Formatting.AQUA));
    }

    public static MutableText fancyEntry(String key, Object value) {
        return Text.empty().append(fancyKey(key)).append(": ").append(fancyValue(value));
    }

    @SuppressWarnings({"rawtypes"})
    public static MutableText fancyObject(Map<?, ?> map) {
        final MutableText text = Text.empty();
        text.append("{");
        final Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();
        iterator.forEachRemaining(entry -> {
            text.append(fancyEntry(entry.getKey().toString(), entry.getValue() instanceof Map map1? fancyObject(map1) : entry.getValue() instanceof Collection collection? fancyArray(collection) : entry.getValue()));
            if (iterator.hasNext()) text.append(Text.literal(", ").formatted(Formatting.GOLD));
        });
        return text.append("}");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static MutableText fancyArray(Collection collection) {
        final MutableText text = Text.empty();
        text.append("[");
        final Iterator iterator = collection.iterator();
        iterator.forEachRemaining(entry -> {
            text.append(fancyValue(entry));
            if (!iterator.hasNext()) text.append(Text.literal(", ").formatted(Formatting.GOLD));
        });
        return text.append("]");
    }

    public static final BiFunction<Boolean, Boolean, MutableText> ENABLED_TEXT = (set, enable) -> Text.translatable("wiis.enabled." + (set? "set" : "get"), enable);
    public static final BiFunction<Boolean, Integer, MutableText> PERMISSION_LEVEL_TEXT = (set, level) -> Text.translatable("wiis.permissionLevel." + (set? "set" : "get"), level);
    public static final BiFunction<Boolean, Boolean, MutableText> AUTOSAVE_TEXT = (set, enable) -> Text.translatable("wiis.autosave." + (set? "set" : "get"), enable);

    public static final Supplier<MutableText> INFO_TEXT = () -> Text.literal( ID.toUpperCase() + " (" + NAME + ")" + Formatting.GREEN + " v" + VERSION + (CONFIG.debug? Formatting.GOLD + " (debug) " : " ") + Formatting.RESET).append(Text.translatable("wiis.info")).append("\n")
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
    public static final BiFunction<Integer, String, MutableText> RESTORE_TEXT = (count, elementPath) -> {
        if (count != null && elementPath != null) return Text.translatable("wiis.restore.with_regex", count, elementPath);
        else if (count == null) return Text.translatable("wiis.restore.single", elementPath);
        else if (elementPath == null) {
            if (count == 0) return Text.translatable("wiis.restore.empty");
            return Text.translatable("wiis.restore.all", count);
        }
        else return Text.empty();
    };

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static final BiFunction<String, FabricWIIS.Config.Entry, MutableText> QUERY_ENTRY_TEXT = (entryName, entry) -> {
        Map map = Config.GSON.fromJson(Config.GSON.toJson(entry), Map.class);
        return Text.empty().append(Text.translatable("wiis.query", entryName)).append(FabricWIIS.Config.fancyObject(map));
    };

    public static final BiFunction<String, Text, MutableText> QUERY_DISCARDREASONS_TEXT = (entry, text) -> Text.empty().append(Text.translatable("wiis.query.discardreasons", entry)).append(text);

    public static final Function<Object[], MutableText> MODIFY_DISCARDREASON_TEXT = (args) -> Text.translatable("wiis.modify.discardreason." + (args[2].equals(true)? "add" : "remove"), args[0], args[1]);
    public static final Function<Object[], MutableText> QUERY_DISCARDREASON_TEXT = (args) -> Text.translatable("wiis.query.discardreason.contains." + (args[2].equals(true)? "success" : "fail"), args[0], args[1]);

    public static final BiFunction<String, Float, MutableText> MODIFY_CHANCE_TEXT = (valuePath, value) -> Text.translatable("wiis.modify.chance", valuePath, value);
    public static final BiFunction<String, Float, MutableText> QUERY_CHANCE_TEXT = (valuePath, value) -> Text.translatable("wiis.query.chance", valuePath, value);

    public static final BiFunction<String, Boolean, MutableText> MODIFY_DISCARD_TEXT = (valuePath, value) -> Text.translatable("wiis.modify.discard", valuePath, value);
    public static final BiFunction<String, Boolean, MutableText> QUERY_DISCARD_TEXT = (valuePath, value) -> Text.translatable("wiis.query.discard", valuePath, value);

    public static final Function<String, MutableText> ENTRY_NOT_FOUND_TEXT = key -> Text.translatable("wiis.entry_not_found", key).formatted(Formatting.RED);
    public static final MutableText UNAVAILABLE_TEXT = Text.translatable("wiis.unavailable").formatted(Formatting.RED);

}
