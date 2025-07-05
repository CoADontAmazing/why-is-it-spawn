package dev.coa.wiis.fabric;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import dev.coa.wiis.Config;
import dev.coa.wiis.WIIS;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.biome.Biome;

import java.nio.file.Path;
import java.util.*;
import java.util.function.*;

import static net.minecraft.server.command.CommandManager.*;

public class FabricWIIS extends WIIS implements ModInitializer {
    public static Config CONFIG = new Config();

    public static void debug(String s) {
        if (CONFIG.debug) LOGGER.info("[{}]: {}", ID.toUpperCase(), s);
    }

    @Override
    public void onInitialize() {
        setInstance(this);
        LOGGER.info("[{}]: init...", ID.toUpperCase());
        ServerLifecycleEvents.SERVER_STARTED.register(server -> CONFIG = Config.load(getConfigPath(server), Config.class));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            if (CONFIG.autosave) CONFIG.save(getConfigPath(server));
        });
        registerCommands();
    }

    @Override
    public void registerCommands() {
        CommandRegistrationCallback.EVENT.register(((dispatcher, access, env) ->
            dispatcher.register(
                    literal("wiis").executes(FabricWIIS::info)
                            .then(literal("reload").requires(FabricWIIS::hasPermission)
                                    .executes(FabricWIIS::reload)
                            )
                            .then(literal("enabled").requires(FabricWIIS::hasPermission)
                                    .executes(context -> enabled(context, false))
                                    .then(argument("enable", BoolArgumentType.bool())
                                            .executes(context -> enabled(context, true))
                                    )
                            )
                            .then(literal("permissionLevel").requires(source -> source.hasPermissionLevel(4))
                                    .executes(context -> permissionLevel(context, false))
                                    .then(argument("level", IntegerArgumentType.integer(0, 4))
                                            .executes(context -> permissionLevel(context, true))
                                    )
                            )
                            .then(literal("autosave").requires(FabricWIIS::hasPermission)
                                    .executes(context -> autosave(context, false))
                                    .then(argument("enable", BoolArgumentType.bool())
                                            .executes(context -> autosave(context, true))
                                    )
                            )
                            .then(literal("query").requires(FabricWIIS::hasPermission)
                                    .then(
                                            createQuerySettings(argument("entry", StringArgumentType.string()), context -> StringArgumentType.getString(context, "entry"))
                                    )
                                    .then(
                                            createQuerySettings(argument("type", IdentifierArgumentType.identifier()), context -> IdentifierArgumentType.getIdentifier(context, "type").toString())
                                                    .suggests((context, builder) -> CommandSource.suggestMatching(Registries.ENTITY_TYPE.stream().map(type -> EntityType.getId(type).toString()), builder))
                                    )
                                    .then(
                                            createQuerySettings(argument("mob", EntityArgumentType.entity()), context -> {
                                                try {
                                                    return EntityType.getId(EntityArgumentType.getEntity(context, "mob").getType()).toString();
                                                } catch (CommandSyntaxException e) {
                                                    throw new RuntimeException(e);
                                                }
                                            })
                                    )
                            )
                            .then(literal("modify").requires(FabricWIIS::hasPermission)
                                    .then(
                                            createModifySettings(argument("entry", StringArgumentType.string()), context -> StringArgumentType.getString(context, "entry"))
                                    )
                                    .then(
                                            createModifySettings(argument("type", IdentifierArgumentType.identifier()), context -> IdentifierArgumentType.getIdentifier(context, "type").toString())
                                                    .suggests((context, builder) -> CommandSource.suggestMatching(Registries.ENTITY_TYPE.stream().map(type -> EntityType.getId(type).toString()), builder))
                                    )
                                    .then(
                                            createModifySettings(argument("mob", EntityArgumentType.entity()), context -> {
                                                try {
                                                    return EntityType.getId(EntityArgumentType.getEntity(context, "mob").getType()).toString();
                                                } catch (CommandSyntaxException e) {
                                                    throw new RuntimeException(e);
                                                }
                                            })
                                    )
                            )
                            .then(literal("restore").requires(FabricWIIS::hasPermission)
                                    .executes(context -> restore(context, 0, true))
                                    .then(argument("path", StringArgumentType.string())
                                            .executes(context -> restore(context, 0, false))
                                    )
                                    .then(argument("type", IdentifierArgumentType.identifier())
                                            .suggests((context, builder) -> CommandSource.suggestMatching(Registries.ENTITY_TYPE.stream().map(type -> EntityType.getId(type).toString()), builder))
                                            .executes(context -> restore(context, 1, false))
                                    )
                                    .then(argument("mob", EntityArgumentType.entity())
                                            .executes(context -> restore(context, 2,false))
                                    )
                            )
            )
        ));
    }

    @Override
    public Config getConfig() {
        return CONFIG;
    }

    private static int reload(CommandContext<ServerCommandSource> context) {
        CONFIG = Config.load(getConfigPath(context.getSource().getServer()), Config.class);
        context.getSource().sendMessage(RELOAD_TEXT);
        return 0;
    }

    private static int info(CommandContext<ServerCommandSource> context) {
        context.getSource().sendMessage(INFO_TEXT.get());
        return 0;
    }

    @SuppressWarnings({"unchecked"})
    private static <T extends ArgumentBuilder> T appendModifySettings(T arg, Function<CommandContext<ServerCommandSource>, String> entryNameGetter, int level) {
        return (T) arg.then(literal("discardreason")
                    .then(argument("reason", StringArgumentType.word())
                        .suggests((context, builder) -> CommandSource.suggestMatching(Arrays.stream(SpawnReason.values()).map(reason -> Config.toKebabCase(reason.toString())), builder))
                        .then(argument("add", BoolArgumentType.bool()).executes(context -> modify(context, entryNameGetter.apply(context), 0, level)))
                    )
                )
                .then(literal("chance")
                    .then(argument("chance", FloatArgumentType.floatArg(0))
                        .executes(context -> modify(context, entryNameGetter.apply(context), 2, level))
                    )
                )
                .then(literal("discard")
                    .then(argument("enable", BoolArgumentType.bool())
                        .executes(context -> modify(context, entryNameGetter.apply(context), 1, level))
                    )
                );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends ArgumentBuilder> T createModifySettings(T arg, Function<CommandContext<ServerCommandSource>, String> entryNameGetter) {
        return (T) appendModifySettings(arg, entryNameGetter, 0)
                .then(literal("worlds")
                    .then(
                        appendModifySettings(argument("world", StringArgumentType.string()), entryNameGetter, 1)
                        .then(literal("biomes")
                            .then(
                                appendModifySettings(argument("biome", StringArgumentType.string()), entryNameGetter, 2)
                            )
                        )
                    )
                );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends ArgumentBuilder> T appendQuerySettings(T arg, Function<CommandContext<ServerCommandSource>, String> entryNameGetter, int level) {
        return (T) arg.executes(context -> query(context, entryNameGetter.apply(context), 0, level))
                .then(literal("discardreason")
                        .executes(context -> query(context, entryNameGetter.apply(context), 2, level))
                        .then(argument("reason", StringArgumentType.word())
                                .suggests((context, builder) -> CommandSource.suggestMatching(Arrays.stream(SpawnReason.values()).map(reason -> Config.toKebabCase(reason.toString())), builder))
                                .executes(context -> query(context, entryNameGetter.apply(context), 1, level))
                        )
                )
                .then(literal("chance")
                        .executes(context -> query(context, entryNameGetter.apply(context), 4, level))
                )
                .then(literal("discard")
                        .executes(context -> query(context, entryNameGetter.apply(context), 3, level))
                );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends ArgumentBuilder> T createQuerySettings(T arg, Function<CommandContext<ServerCommandSource>, String> entryNameGetter) {
        return (T) appendQuerySettings(arg, entryNameGetter, 0)
                .then(literal("worlds")
                        .then(
                                appendQuerySettings(argument("world", StringArgumentType.string()), entryNameGetter, 1)
                                        .then(literal("biomes")
                                                .then(
                                                        appendModifySettings(argument("biome", StringArgumentType.string()), entryNameGetter, 2)
                                                )
                                        )
                        )
                );
    }

    public static int query(CommandContext<ServerCommandSource> context, String entryName, int modeId, int level) {
        dev.coa.wiis.Config.Entry entry = CONFIG.entities.get(entryName);
        String world, biome, valuePath = entryName;
        dev.coa.wiis.Config.ISetting elementSettings = entry;

        if (entry == null) {
            context.getSource().sendMessage(ENTRY_NOT_FOUND_TEXT.apply(entryName));
            return 0;
        }

        if (level >= 1) {
            world = StringArgumentType.getString(context, "world");
            valuePath += "." + world;
            elementSettings = (dev.coa.wiis.Config.ISetting) ((dev.coa.wiis.Config.Entry) elementSettings).worlds().get(world);
        }
        if (level == 2) {
            biome = StringArgumentType.getString(context, "biome");
            valuePath += "." + biome;
            elementSettings = (dev.coa.wiis.Config.ISetting) ((dev.coa.wiis.Config.World) elementSettings).biomes().get(biome);
        }

        if (modeId == 0) context.getSource().sendMessage(QUERY_ENTRY_TEXT.apply(entryName, entry));
        else if (modeId == 1) {
            String reason = StringArgumentType.getString(context, "reason");
            boolean discard = elementSettings.isDiscardedBy(reason);
            context.getSource().sendMessage(QUERY_DISCARDREASON_TEXT.apply(new Object[]{valuePath, reason, discard}));
            return discard? 1 : 0;
        } else if (modeId == 2) context.getSource().sendMessage(QUERY_DISCARDREASONS_TEXT.apply(valuePath, Config.fancyArray(elementSettings.discardReasons())));
        else if (modeId == 3) {
            boolean discard = elementSettings.isDiscarded();
            context.getSource().sendMessage(QUERY_DISCARD_TEXT.apply(valuePath, discard));
            return discard? 1 : 0;
        }
        else if (modeId == 4) context.getSource().sendMessage(QUERY_CHANCE_TEXT.apply(valuePath, elementSettings.chance()));

        return 1;
    }

    public static int modify(CommandContext<ServerCommandSource> context, String entryName, int modeId, int level) {
        dev.coa.wiis.Config.Entry entry = CONFIG.entities.computeIfAbsent(entryName, key -> new Config.Entry());
        String world, biome, valuePath = entryName;
        dev.coa.wiis.Config.BasicSettings elementSettings = (dev.coa.wiis.Config.BasicSettings) entry;

        if (level >= 1) {
            world = StringArgumentType.getString(context, "world");
            valuePath += "." + world;
            elementSettings = (dev.coa.wiis.Config.BasicSettings) ((dev.coa.wiis.Config.Entry) elementSettings).worlds().get(world);
        }
        if (level == 2) {
            biome = StringArgumentType.getString(context, "biome");
            valuePath += "." + biome;
            elementSettings = (dev.coa.wiis.Config.BasicSettings) ((dev.coa.wiis.Config.World) elementSettings).biomes().get(biome);
        }

        if (modeId == 0) {
            String reason = StringArgumentType.getString(context, "reason");
            boolean add = BoolArgumentType.getBool(context, "add");
            elementSettings.discardReason(reason, add);
            context.getSource().sendMessage(MODIFY_DISCARDREASON_TEXT.apply(new Object[]{valuePath, reason, add}));
        } else if (modeId == 1) {
            elementSettings.discard = BoolArgumentType.getBool(context, "enable");
            context.getSource().sendMessage(MODIFY_DISCARD_TEXT.apply(valuePath, elementSettings.isDiscarded()));
        } else if (modeId == 2) {
            elementSettings.chance = FloatArgumentType.getFloat(context, "chance");
            context.getSource().sendMessage(MODIFY_CHANCE_TEXT.apply(valuePath, elementSettings.chance()));
        }

        if (CONFIG.autosave) CONFIG.save(getConfigPath(context.getSource().getServer()));
        return 0;
    }

    private static int restore(CommandContext<ServerCommandSource> context, int type, boolean all) {
        if (all) {
            int count = CONFIG.entities.size();
            CONFIG.entities.clear();
            context.getSource().sendMessage(RESTORE_TEXT.apply(count, null));
            if (CONFIG.autosave) CONFIG.save(getConfigPath(context.getSource().getServer()));
            return count;
        } else {
            String path;
            try {
                path = type == 0? StringArgumentType.getString(context, "entry") : type == 1? IdentifierArgumentType.getIdentifier(context, "type").toString() : EntityType.getId(EntityArgumentType.getEntity(context, "mob").getType()).toString();
            } catch (CommandSyntaxException e) {
                throw new RuntimeException(e);
            }
            if (path.startsWith(Config.REGEX_TAG)) {
                List<Map.Entry<String, Config.Entry>> entries = CONFIG.findEntries(path);
                entries.forEach(entry -> CONFIG.entities.remove(entry.getKey()));
                context.getSource().sendMessage(RESTORE_TEXT.apply(entries.size(), path));
                if (CONFIG.autosave) CONFIG.save(getConfigPath(context.getSource().getServer()));
                return entries.size();
            } else {
                if (CONFIG.entities.remove(path) != null) {
                    context.getSource().sendMessage(RESTORE_TEXT.apply(null, path));
                    if (CONFIG.autosave) CONFIG.save(getConfigPath(context.getSource().getServer()));
                    return 1;
                } else context.getSource().sendMessage(ENTRY_NOT_FOUND_TEXT.apply(path));
            }
        }
        return 0;
    }

    private static int enabled(CommandContext<ServerCommandSource> context, boolean set) {
        if (set) {
            CONFIG.enabled = BoolArgumentType.getBool(context, "enable");
            if (CONFIG.autosave) CONFIG.save(getConfigPath(context.getSource().getServer()));
            context.getSource().sendMessage(ENABLED_TEXT.apply(true, CONFIG.enabled));
        } else context.getSource().sendMessage(ENABLED_TEXT.apply(false, CONFIG.enabled));
        return 1;
    }

    private static int permissionLevel(CommandContext<ServerCommandSource> context, boolean set) {
        if (set) {
            CONFIG.permissionLevel = IntegerArgumentType.getInteger(context, "level");
            if (CONFIG.autosave) CONFIG.save(getConfigPath(context.getSource().getServer()));
            context.getSource().sendMessage(PERMISSION_LEVEL_TEXT.apply(true, CONFIG.permissionLevel));
        } else context.getSource().sendMessage(PERMISSION_LEVEL_TEXT.apply(false, CONFIG.permissionLevel));
        return 1;
    }

    private static int autosave(CommandContext<ServerCommandSource> context, boolean set) {
        if (set) {
            CONFIG.autosave = BoolArgumentType.getBool(context, "enable");
            if (CONFIG.autosave) CONFIG.save(getConfigPath(context.getSource().getServer()));
            context.getSource().sendMessage(AUTOSAVE_TEXT.apply(true, CONFIG.autosave));
        } else context.getSource().sendMessage(AUTOSAVE_TEXT.apply(false, CONFIG.autosave));
        return 1;
    }

    public static boolean hasPermission(ServerCommandSource commandSource) {
        return commandSource.hasPermissionLevel(MathHelper.clamp(CONFIG.permissionLevel, 0, 4));
    }

    public static boolean hasPermission(PlayerEntity entity) {
        return entity.hasPermissionLevel(MathHelper.clamp(CONFIG.permissionLevel, 0, 4));
    }

    public static boolean isEnabled() {
        return CONFIG.enabled;
    }

    public static Path getConfigPath(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve(getConfigLocation());
    }

    public static class Config extends dev.coa.wiis.Config {
        public void editEntity(Identifier id, Consumer<Entry> consumer) {
            editEntry(id.toString(), consumer);
        }

        public void editEntity(EntityType<?> entityType, Consumer<Entry> consumer) {
            editEntity(EntityType.getId(entityType), consumer);
        }

        public boolean canSpawn(Identifier id, SpawnReason reason, net.minecraft.world.World world, RegistryKey<Biome> biome) {
            return canSpawn(id.toString(), reason, world.getDimensionKey().getValue(), biome.getValue());
        }

        public boolean canSpawn(Entity entity, SpawnReason reason, net.minecraft.world.World world, RegistryKey<Biome> biome) {
            return canSpawn(entity.getType(), reason, world, biome);
        }

        public boolean canSpawn(EntityType<?> entityType, SpawnReason reason, net.minecraft.world.World world, RegistryKey<Biome> biome) {
            return canSpawn(EntityType.getId(entityType), reason, world, biome);
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

        @SuppressWarnings({"rawtypes", "unchecked"})
        public static MutableText fancyObject(Map<String, Object> map) {
            MutableText text = Text.empty();
            text.append("{");
            Iterator<Map.Entry<String, Object>> iterator = map.entrySet().iterator();
            iterator.forEachRemaining(entry -> {
                text.append(fancyEntry(entry.getKey(), entry.getValue() instanceof Map map1? fancyObject(map1) : entry.getValue() instanceof Collection collection? fancyArray(collection) : entry.getValue()));
                if (iterator.hasNext()) text.append(Text.literal(", ").formatted(Formatting.GOLD));
            });
            return text.append("}");
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        public static MutableText fancyArray(Collection collection) {
            MutableText text = Text.empty();
            text.append("[");
            Iterator iterator = collection.iterator();
            iterator.forEachRemaining(entry -> {
                text.append(fancyValue(entry));
                if (!iterator.hasNext()) text.append(Text.literal(", ").formatted(Formatting.GOLD));
            });
            return text.append("]");
        }
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
    public static final BiFunction<String, Config.Entry, MutableText> QUERY_ENTRY_TEXT = (entryName, entry) -> {
        Map map = Config.GSON.fromJson(Config.GSON.toJson(entry), Map.class);
        return Text.empty().append(Text.translatable("wiis.query", entryName)).append(Config.fancyObject(map));
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