package dev.coa.wiis.fabric;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import dev.coa.wiis.WIIS;
import static dev.coa.wiis.fabric.FabricConfig.*;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.MathHelper;
import static net.minecraft.server.command.CommandManager.*;

import java.nio.file.Path;
import java.util.*;
import java.util.function.*;

public class FabricWIIS extends WIIS implements ModInitializer {
    public static FabricConfig CONFIG = new FabricConfig();

    public static void debug(String s) {
        if (CONFIG.debug) LOGGER.info("[{}]: {}", ID.toUpperCase(), s);
    }

    @Override
    public void onInitialize() {
        setInstance(this);
        LOGGER.info("[{}]: init...", ID.toUpperCase());

        ServerLifecycleEvents.SERVER_STARTED.register(server -> CONFIG = load(getConfigPath(server), FabricConfig.class));
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
    public FabricConfig getConfig() {
        return CONFIG;
    }

    private static int reload(CommandContext<ServerCommandSource> context) {
        CONFIG = load(getConfigPath(context.getSource().getServer()), FabricConfig.class);
        context.getSource().sendMessage(RELOAD_TEXT);
        return 0;
    }

    private static int info(CommandContext<ServerCommandSource> context) {
        context.getSource().sendMessage(INFO_TEXT.get());
        return 0;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends ArgumentBuilder> T appendModifySettings(T arg, Function<CommandContext<ServerCommandSource>, String> entryNameGetter, int level) {
        return (T) arg.then(literal("discardreason")
                    .then(argument("reason", StringArgumentType.word())
                        .suggests((context, builder) -> CommandSource.suggestMatching(Arrays.stream(SpawnReason.values()).map(reason -> toKebabCase(reason.toString())), builder))
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
                                .suggests((context, builder) -> CommandSource.suggestMatching(Arrays.stream(SpawnReason.values()).map(reason -> toKebabCase(reason.toString())), builder))
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

    public static int query(CommandContext<ServerCommandSource> context, String entryKey, int modeId, int level) {
        FabricEntry entry = CONFIG.entries.get(entryKey);
        String world, biome, path = entryKey;
        FabricSettings elementSettings = entry;

        if (entry == null) {
            context.getSource().sendMessage(ENTRY_NOT_FOUND_TEXT.apply(entryKey));
            return 0;
        }

        if (level >= 1) {
            world = StringArgumentType.getString(context, "world");
            path += ".world[" + world + "]";
            elementSettings = ((FabricEntry) elementSettings).worlds().get(world);
        }
        if (level == 2) {
            biome = StringArgumentType.getString(context, "biome");
            path += ".biome[" + biome + "]";
            elementSettings = ((FabricWorld) elementSettings).biomes().get(biome);
        }

        if (modeId == 0) context.getSource().sendMessage(QUERY_ENTRY_TEXT.apply(entryKey, entry));
        else if (modeId == 1) {
            String reason = StringArgumentType.getString(context, "reason");
            boolean discard = elementSettings.isDiscardedBy(reason);
            context.getSource().sendMessage(QUERY_DISCARDREASON_TEXT.apply(new Object[]{path, reason, discard}));
            return discard? 1 : 0;
        } else if (modeId == 2) context.getSource().sendMessage(QUERY_DISCARDREASONS_TEXT.apply(path, fancyCollection(elementSettings.discardReasons())));
        else if (modeId == 3) {
            boolean discard = elementSettings.isDiscarded();
            context.getSource().sendMessage(QUERY_DISCARD_TEXT.apply(path, discard));
            return discard? 1 : 0;
        }
        else if (modeId == 4) context.getSource().sendMessage(QUERY_CHANCE_TEXT.apply(path, elementSettings.chance()));

        return 1;
    }

    public static int modify(CommandContext<ServerCommandSource> context, String entryKey, int modeId, int level) {
        FabricEntry entry = CONFIG.entries.computeIfAbsent(entryKey, k -> new FabricEntry());
        String world, biome, path = entryKey;
        FabricSettings elementSettings = entry;

        if (level >= 1) {
            world = StringArgumentType.getString(context, "world");
            path += ".world[" + world + "]";
            elementSettings = ((FabricEntry) elementSettings).worlds().get(world);
        }
        if (level == 2) {
            biome = StringArgumentType.getString(context, "biome");
            path += ".biome[" + biome + "]";
            elementSettings = ((FabricWorld) elementSettings).biomes().get(biome);
        }

        if (modeId == 0) {
            String reason = StringArgumentType.getString(context, "reason");
            boolean add = BoolArgumentType.getBool(context, "add");
            elementSettings.discardReason(reason, add);
            context.getSource().sendMessage(MODIFY_DISCARDREASON_TEXT.apply(new Object[]{path, reason, add}));
        } else if (modeId == 1) {
            elementSettings.discard = BoolArgumentType.getBool(context, "enable");
            context.getSource().sendMessage(MODIFY_DISCARD_TEXT.apply(path, elementSettings.isDiscarded()));
        } else if (modeId == 2) {
            elementSettings.chance = FloatArgumentType.getFloat(context, "chance");
            context.getSource().sendMessage(MODIFY_CHANCE_TEXT.apply(path, elementSettings.chance()));
        }

        if (CONFIG.autosave) CONFIG.save(getConfigPath(context.getSource().getServer()));
        return 0;
    }

    private static int restore(CommandContext<ServerCommandSource> context, int type, boolean all) {
        if (all) {
            int count = CONFIG.entries.size();
            CONFIG.entries.clear();
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
            if (path.startsWith(REGEX_TAG)) {
                var entries = CONFIG.findEntries(path);
                entries.forEach(entry -> CONFIG.entries.remove(entry.getKey()));

                context.getSource().sendMessage(RESTORE_TEXT.apply(entries.size(), path));
                if (CONFIG.autosave) CONFIG.save(getConfigPath(context.getSource().getServer()));
                return entries.size();
            } else {
                if (CONFIG.entries.remove(path) != null) {
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
}