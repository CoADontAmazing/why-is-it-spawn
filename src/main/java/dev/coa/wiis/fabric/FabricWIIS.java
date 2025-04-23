package dev.coa.wiis.fabric;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.nio.file.Path;
import java.util.*;
import java.util.function.*;

import static net.minecraft.server.command.CommandManager.*;

public class FabricWIIS extends WIIS implements ModInitializer {
    public static Config CONFIG = new Config();

    public static void debug(String s) {
        if (CONFIG.debug) LOGGER.info("[" + ID.toUpperCase() + "]: " + s);
    }

    @Override
    public void onInitialize() {
        setInstance(this);
        LOGGER.info("[" + ID.toUpperCase() + "]: init...");
        ServerLifecycleEvents.SERVER_STARTED.register(server -> CONFIG = Config.load(getConfigPath(server), Config.class));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            if (CONFIG.autosave) CONFIG.save(getConfigPath(server));
        });
        registerCommands();
    }

    @Override
    public void registerCommands() {
        CommandRegistrationCallback.EVENT.register(((dispatcher, access, env) -> {
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
                                    .then(argument("entry", StringArgumentType.string())
                                            .executes(context -> query(context, 0,-1))
                                            .then(literal("exclude-reason")
                                                    .executes(context -> query(context, 0,0))
                                                    .then(argument("reasonKey", StringArgumentType.word())
                                                            .suggests((context, builder) -> CommandSource.suggestMatching(Arrays.stream(SpawnReason.values()).map(reason -> Config.toKebabCase(reason.toString())), builder))
                                                            .executes(context -> query(context, 0,-2))
                                                    )
                                            )
                                            .then(literal("despawnInstantly")
                                                    .executes(context -> query(context, 0, 2))
                                            )
                                    )
                                    .then(argument("type", IdentifierArgumentType.identifier())
                                            .suggests((context, builder) -> CommandSource.suggestMatching(Registries.ENTITY_TYPE.stream().map(type -> EntityType.getId(type).toString()), builder))
                                            .executes(context -> query(context, 1,-1))
                                            .then(literal("exclude-reason")
                                                    .executes(context -> query(context, 1,0))
                                                    .then(argument("reasonKey", StringArgumentType.word())
                                                            .suggests((context, builder) -> CommandSource.suggestMatching(Arrays.stream(SpawnReason.values()).map(reason -> Config.toKebabCase(reason.toString())), builder))
                                                            .executes(context -> query(context, 1,-2))
                                                    )
                                            )
                                            .then(literal("despawnInstantly")
                                                    .executes(context -> query(context, 1, 2))
                                            )
                                    )
                                    .then(argument("mob", EntityArgumentType.entity())
                                            .executes(context -> query(context, 2,-1))
                                            .then(literal("exclude-reason")
                                                    .executes(context -> query(context, 2,0))
                                                    .then(argument("reasonKey", StringArgumentType.word())
                                                            .suggests((context, builder) -> CommandSource.suggestMatching(Arrays.stream(SpawnReason.values()).map(reason -> Config.toKebabCase(reason.toString())), builder))
                                                            .executes(context -> query(context,2, -2))
                                                    )
                                            )
                                            .then(literal("despawnInstantly")
                                                    .executes(context -> query(context, 2,2))
                                            )
                                    )
                            )
                            .then(literal("modify").requires(FabricWIIS::hasPermission)
                                    .then(argument("entry", StringArgumentType.string())
                                            .then(literal("exclude-reason")
                                                    .then(argument("reasonKey", StringArgumentType.word())
                                                            .suggests((context, builder) -> CommandSource.suggestMatching(Arrays.stream(SpawnReason.values()).map(reason -> Config.toKebabCase(reason.toString())), builder))
                                                            .then(argument("exclude", BoolArgumentType.bool())
                                                                    .executes(context -> modify(context, 0,0))
                                                            )
                                                    )
                                            )
                                            .then(literal("despawnInstantly")
                                                    .then(argument("enable", BoolArgumentType.bool())
                                                            .executes(context -> modify(context, 0,2))
                                                    )
                                            )
                                    )
                                    .then(argument("type", IdentifierArgumentType.identifier())
                                            .suggests((context, builder) -> CommandSource.suggestMatching(Registries.ENTITY_TYPE.stream().map(type -> EntityType.getId(type).toString()), builder))
                                            .then(literal("exclude-reason")
                                                    .then(argument("reasonKey", StringArgumentType.word())
                                                            .suggests((context, builder) -> CommandSource.suggestMatching(Arrays.stream(SpawnReason.values()).map(reason -> Config.toKebabCase(reason.toString())), builder))
                                                            .then(argument("exclude", BoolArgumentType.bool())
                                                                    .executes(context -> modify(context, 1,0))
                                                            )
                                                    )
                                            )
                                            .then(literal("despawnInstantly")
                                                    .then(argument("enable", BoolArgumentType.bool())
                                                            .executes(context -> modify(context, 1,2))
                                                    )
                                            )
                                    )
                                    .then(argument("mob", EntityArgumentType.entity())
                                            .then(literal("exclude-reason")
                                                    .then(argument("reasonKey", StringArgumentType.word())
                                                            .suggests((context, builder) -> CommandSource.suggestMatching(Arrays.stream(SpawnReason.values()).map(reason -> Config.toKebabCase(reason.toString())), builder))
                                                            .then(argument("exclude", BoolArgumentType.bool())
                                                                    .executes(context -> modify(context, 2,0))
                                                            )
                                                    )
                                            )
                                            .then(literal("despawnInstantly")
                                                    .then(argument("enable", BoolArgumentType.bool())
                                                            .executes(context -> modify(context, 2,2))
                                                    )
                                            )
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
            );
        }));
    }

    @Override
    public dev.coa.wiis.Config getConfig() {
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

    private static int query(CommandContext<ServerCommandSource> context, int type, int mode) throws CommandSyntaxException {
        String entryId = type == 0? StringArgumentType.getString(context, "entry") : type == 1? IdentifierArgumentType.getIdentifier(context, "type").toString() : EntityType.getId(EntityArgumentType.getEntity(context, "mob").getType()).toString();
        Config.Entry entry = CONFIG.entities.get(entryId);
        if (entry == null) {
            context.getSource().sendMessage(ENTRY_NOT_FOUND_TEXT.apply(entryId));
            return 0;
        }

        if (mode == -1) context.getSource().sendMessage(QUERY_TEXT.apply(entryId, entry));
        else if (mode == -2) {
            String reason = StringArgumentType.getString(context, "reasonKey");
            boolean excluded = entry.excludedReasons().contains(reason);
            context.getSource().sendMessage(QUERY_REASON_TEXT.apply(new Object[]{entryId, reason, excluded}));
        } else if (mode == 0) context.getSource().sendMessage(QUERY_REASONS_TEXT.apply(entryId, Config.fancyArray(entry.excludedReasons())));
        else if (mode == 1) {
            // TODO
        } else if (mode == 2) {
            boolean despawnInstantly = entry.despawnInstantly();
            context.getSource().sendMessage(QUERY_DESPAWN_INSTANTLY_TEXT.apply(entryId, despawnInstantly));
        } else if (mode == 3) {
            // TODO
        }

        return 1;
    }

    private static int modify(CommandContext<ServerCommandSource> context, int type, int mode) throws CommandSyntaxException {
        String entryId = type == 0? StringArgumentType.getString(context, "entry") : type == 1? IdentifierArgumentType.getIdentifier(context, "type").toString() : EntityType.getId(EntityArgumentType.getEntity(context, "mob").getType()).toString();
        Config.Entry entry = CONFIG.entities.computeIfAbsent(entryId, key -> new Config.Entry(false));

        if (mode == 0) {
            String reason = StringArgumentType.getString(context, "reasonKey");
            boolean exclude = BoolArgumentType.getBool(context, "exclude");
            entry.excludeReason(reason, exclude);
            context.getSource().sendMessage(MODIFY_REASON_TEXT.apply(new Object[]{reason, entryId, exclude}));
        } else if (mode == 1) {
            // TODO
        } else if (mode == 2) {
            boolean despawnInstantly = BoolArgumentType.getBool(context, "enable");
            entry.despawnInstantly(despawnInstantly);
            context.getSource().sendMessage(MODIFY_DESPAWN_INSTANTLY_TEXT.apply(entryId, despawnInstantly));
        } else if (mode == 3) {
            // TODO
        }
        if (CONFIG.autosave) CONFIG.save(getConfigPath(context.getSource().getServer()));
        return 1;
    }

    private static int restore(CommandContext<ServerCommandSource> context, int type, boolean all) {
        if (all) {
            int count = CONFIG.entities.size();
            CONFIG.entities.clear();
            context.getSource().sendMessage(RESTORE_TEXT.apply(count, null));
            if (CONFIG.autosave) CONFIG.save(getConfigPath(context.getSource().getServer()));
            return count;
        } else {
            String path = "";
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

        public boolean allowSpawn(Identifier id, SpawnReason reason, World world) {
            return allowSpawn(id.toString(), reason, world.getDimensionKey().getValue().toString());
        }

        public boolean allowSpawn(Entity entity, SpawnReason reason, World world) {
            return allowSpawn(entity.getType(), reason, world);
        }

        public boolean allowSpawn(EntityType<?> entityType, SpawnReason reason, World world) {
            return allowSpawn(EntityType.getId(entityType), reason, world);
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
                if (iterator.hasNext()) text.append(Text.literal(", ").formatted(Formatting.GOLD));
            });
            return text.append("]");
        }
    }

    public static MutableText UNAVAILABLE_TEXT = Text.translatable("wiis.unavailable").formatted(Formatting.RED);
    public static MutableText RELOAD_TEXT = Text.translatable("wiis.reload");
    public static BiFunction<Integer, String, MutableText> RESTORE_TEXT = (count, entry) -> {
        if (count != null && entry != null) return Text.translatable("wiis.restore.with_regex", count, entry);
        else if (count == null) return Text.translatable("wiis.restore.single", entry);
        else if (entry == null) {
            if (count == 0) return Text.translatable("wiis.restore.empty");
            return Text.translatable("wiis.restore.all", count);
        }
        else return Text.empty();
    };
    public static BiFunction<Boolean, Boolean, MutableText> ENABLED_TEXT = (set, enable) -> set? Text.translatable("wiis.enabled.set", enable) : Text.translatable("wiis.enabled.get", enable);
    public static BiFunction<Boolean, Integer, MutableText> PERMISSION_LEVEL_TEXT = (set, level) -> set? Text.translatable("wiis.permissionLevel.set", level) : Text.translatable("wiis.permissionLevel.get", level);
    public static BiFunction<Boolean, Boolean, MutableText> AUTOSAVE_TEXT = (set, enable) -> set? Text.translatable("wiis.autosave.set", enable) : Text.translatable("wiis.autosave.get", enable);

    public static BiFunction<String, Config.Entry, MutableText> QUERY_TEXT = (key, entry) -> {
        Map<String, Object> map = new HashMap<>();
        map.put("excludedReasons", entry.excludedReasons());
        map.put("despawnInstantly", entry.despawnInstantly());
        return Text.empty().append(Text.translatable("wiis.query", key)).append(Config.fancyObject(map));
    };
    public static Function<Object[], MutableText> MODIFY_REASON_TEXT = args -> Text.translatable("wiis.modify.reason", args[0], args[1], args[2]);
    public static Function<Object[], MutableText> QUERY_REASON_TEXT = args -> Text.translatable("wiis.query.reason", args[0], args[1], args[2]);
    public static BiFunction<String, Text, MutableText> QUERY_REASONS_TEXT = (entry, text) -> Text.empty().append(Text.translatable("wiis.query.reasons", entry)).append(text);
    public static BiFunction<String, Boolean, MutableText> MODIFY_DESPAWN_INSTANTLY_TEXT = (entry, value) -> Text.translatable("wiis.modify.despawnInstantly", entry, value);
    public static BiFunction<String, Boolean, MutableText> QUERY_DESPAWN_INSTANTLY_TEXT = (entry, value) -> Text.translatable("wiis.query.despawnInstantly", entry, value);

    public static Supplier<MutableText> INFO_TEXT = () -> Text.literal( ID.toUpperCase() + " (" + NAME + ")" + Formatting.GREEN + " v" + VERSION + (CONFIG.debug? Formatting.GOLD + " (debug) " : " ") + Formatting.RESET).append(Text.translatable("wiis.info")).append("\n")
            .append(Text.literal("Modrinth")
                    .styled(e -> e.withColor(0x23D86F).withUnderline(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://modrinth.com/mod/wiis"))
                    )
            ).append(" | ").append(Text.literal("CurseForge")
                    .styled(e -> e.withColor(0xF06030).withUnderline(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://www.curseforge.com/minecraft/mc-mods/wiis"))
                    )
            );

    public static Function<String, MutableText> ENTRY_NOT_FOUND_TEXT = key -> Text.translatable("wiis.entry_not_found", key).formatted(Formatting.RED);
}