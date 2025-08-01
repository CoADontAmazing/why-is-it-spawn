package dev.coa.wiis.fabric;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

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
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
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

    public static final SuggestionProvider<ServerCommandSource> DIM_SUGGESTION = (context, builder) -> CommandSource.suggestIdentifiers(context.getSource().getWorldKeys().stream().map(RegistryKey::getValue), builder);
    public static final SuggestionProvider<ServerCommandSource> BIOME_SUGGESTION = (context, builder) -> CommandSource.suggestIdentifiers(context.getSource().getRegistryManager().get(RegistryKeys.BIOME).getKeys().stream().map(RegistryKey::getValue), builder);
    public static final SuggestionProvider<ServerCommandSource> SPAWNREASONS_SUGGESTION = (context, builder) -> CommandSource.suggestMatching(Arrays.stream(SpawnReason.values()).map(reason -> toKebabCase(reason.toString())), builder);

    public static void debug(String s) {
        if (CONFIG.debug) LOGGER.info("[{}]: {}", ID.toUpperCase(), s);
    }

    @Override
    public void onInitialize() {
        setInstance(this);
        LOGGER.info("[{}]: init...", ID.toUpperCase());

        ServerLifecycleEvents.SERVER_STARTED.register(FabricWIIS::loadFromServer);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            if (CONFIG.autosave) saveToServer(server);
        });

        registerCommands();
    }

    @Override
    public void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) ->
            dispatcher.register(
                    literal("wiis").executes(FabricWIIS::info)
                            .then(
                                    literal("reload").requires(FabricWIIS::hasPermission).executes(FabricWIIS::reload)
                            )
                            .then(
                                    literal("enabled").requires(FabricWIIS::hasPermission).executes(context -> enabled(context, false))
                                    .then(
                                            argument("enable", BoolArgumentType.bool()).executes(context -> enabled(context, true))
                                    )
                            )
                            .then(
                                    literal("permissionLevel").requires(source -> source.hasPermissionLevel(4)).executes(context -> permissionLevel(context, false))
                                    .then(
                                            argument("level", IntegerArgumentType.integer(0, 4)).executes(context -> permissionLevel(context, true))
                                    )
                            )
                            .then(
                                    literal("autosave").requires(FabricWIIS::hasPermission).executes(context -> autosave(context, false))
                                    .then(
                                            argument("enable", BoolArgumentType.bool()).executes(context -> autosave(context, true))
                                    )
                            )
                            .then(
                                    literal("query").requires(FabricWIIS::hasPermission)
                                    .then(
                                            rootQuerySettings(
                                                    argument("entry", StringArgumentType.string()).suggests((context, builder) -> CommandSource.suggestMatching(CONFIG.entries.keySet(), builder)), context -> StringArgumentType.getString(context, "entry")
                                            )
                                    )
                                    .then(
                                            rootQuerySettings(
                                                    argument("type", IdentifierArgumentType.identifier()), context -> IdentifierArgumentType.getIdentifier(context, "type").toString()
                                            ).suggests((context, builder) -> CommandSource.suggestMatching(Registries.ENTITY_TYPE.stream().map(type -> EntityType.getId(type).toString()), builder))
                                    )
                                    .then(
                                            rootQuerySettings(
                                                    argument("mob", EntityArgumentType.entity()), context -> {
                                                    try {
                                                        return EntityType.getId(EntityArgumentType.getEntity(context, "mob").getType()).toString();
                                                    } catch (CommandSyntaxException e) {
                                                        throw new RuntimeException(e);
                                                    }
                                                }
                                            )
                                    )
                            )
                            .then(
                                    literal("modify").requires(FabricWIIS::hasPermission)
                                    .then(
                                            rootModifySettings(
                                                    argument("entry", StringArgumentType.string()).suggests((context, builder) -> CommandSource.suggestMatching(CONFIG.entries.keySet(), builder)), context -> StringArgumentType.getString(context, "entry")
                                            )
                                    )
                                    .then(
                                            rootModifySettings(
                                                    argument("type", IdentifierArgumentType.identifier()), context -> IdentifierArgumentType.getIdentifier(context, "type").toString()
                                            ).suggests((context, builder) -> CommandSource.suggestMatching(Registries.ENTITY_TYPE.stream().map(type -> EntityType.getId(type).toString()), builder))
                                    )
                                    .then(
                                            rootModifySettings(
                                                    argument("mob", EntityArgumentType.entity()), context -> {
                                                    try {
                                                        return EntityType.getId(EntityArgumentType.getEntity(context, "mob").getType()).toString();
                                                    } catch (CommandSyntaxException e) {
                                                        throw new RuntimeException(e);
                                                    }
                                                }
                                            )
                                    )
                            )
                            .then(
                                    literal("restore").requires(FabricWIIS::hasPermission).executes(context -> restore(context, null))
                                    .then(
                                            argument("path", StringArgumentType.string()).suggests((context, builder) -> CommandSource.suggestMatching(CONFIG.entries.keySet().stream().map(k -> "\"" + k + "\""), builder)).executes(context -> restore(context, ArgType.STRING))
                                    )
                                    .then(
                                            argument("type", IdentifierArgumentType.identifier()).suggests((context, builder) -> CommandSource.suggestMatching(Registries.ENTITY_TYPE.stream().map(type -> EntityType.getId(type).toString()), builder)).executes(context -> restore(context, ArgType.IDENTIFIER))
                                    )
                                    .then(
                                            argument("mob", EntityArgumentType.entity()).executes(context -> restore(context, ArgType.ENTITY))
                                    )
                            )
            )
        );
    }

    @Override
    public FabricConfig getConfig() {
        return CONFIG;
    }

    public static void loadFromServer(MinecraftServer server) {
        load(getConfigPath(server), FabricConfig.class).ifPresent(config -> CONFIG = config);
    }

    public static void saveToServer(MinecraftServer server) {
        CONFIG.save(getConfigPath(server));
    }

    private static int reload(CommandContext<ServerCommandSource> context) {
        loadFromServer(context.getSource().getServer());
        context.getSource().sendMessage(RELOAD_TEXT);
        return 0;
    }

    private static int info(CommandContext<ServerCommandSource> context) {
        context.getSource().sendMessage(INFO_TEXT.get());
        return 0;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends ArgumentBuilder> T setModifySubSettings(T arg, Function<CommandContext<ServerCommandSource>, String> entryKeyGetter, Level level, ArgType worldType, ArgType biomeType) {
        return (T) arg.then(
                        literal("discardreason")
                        .then(
                                argument("reason", StringArgumentType.word()).suggests(SPAWNREASONS_SUGGESTION)
                                .then(
                                       argument("add", BoolArgumentType.bool()).executes(context -> modify(context, entryKeyGetter, SubCommand.DISCARD_REASON, level, worldType, biomeType))
                                )
                        )
                )
                .then(
                        literal("chance")
                        .then(
                                argument("chance", FloatArgumentType.floatArg(0)).executes(context -> modify(context, entryKeyGetter, SubCommand.CHANCE, level, worldType, biomeType))
                        )
                )
                .then(
                        literal("discard")
                        .then(
                                argument("enable", BoolArgumentType.bool()).executes(context -> modify(context, entryKeyGetter, SubCommand.DISCARD, level, worldType, biomeType))
                        )
                );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends ArgumentBuilder> T rootModifySettings(T arg, Function<CommandContext<ServerCommandSource>, String> entryKeyGetter) {
        return (T) setModifySubSettings(arg, entryKeyGetter, Level.ROOT, null, null)
                .then(worldModifySettings(entryKeyGetter));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends ArgumentBuilder> T worldModifySettings(Function<CommandContext<ServerCommandSource>, String> entryKeyGetter) {
        return (T) literal("worlds")
                    .then(
                        setModifySubSettings(argument("str", StringArgumentType.string()).suggests(DIM_SUGGESTION), entryKeyGetter, Level.WORLD, ArgType.STRING, null)
                        .then(biomeModifySettings(entryKeyGetter, ArgType.STRING))
                    )
                    .then(
                        setModifySubSettings(argument("id", IdentifierArgumentType.identifier()).suggests(DIM_SUGGESTION), entryKeyGetter, Level.WORLD, ArgType.IDENTIFIER, null)
                        .then(biomeModifySettings(entryKeyGetter, ArgType.IDENTIFIER))
                    );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends ArgumentBuilder> T biomeModifySettings(Function<CommandContext<ServerCommandSource>, String> entryKeyGetter, ArgType worldType) {
        return (T) literal("biomes")
                    .then(
                        setModifySubSettings(argument("str", StringArgumentType.string()).suggests(BIOME_SUGGESTION), entryKeyGetter, Level.BIOME, worldType, ArgType.STRING)
                    )
                    .then(
                        setModifySubSettings(argument("id", IdentifierArgumentType.identifier()).suggests(BIOME_SUGGESTION), entryKeyGetter, Level.BIOME, worldType, ArgType.IDENTIFIER)
                    );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends ArgumentBuilder> T setQuerySubSettings(T arg, Function<CommandContext<ServerCommandSource>, String> entryKeyGetter, Level level, ArgType worldType, ArgType biomeType) {
        return (T) arg.executes(context -> query(context, entryKeyGetter, SubCommand.NONE, level, worldType, biomeType))
                .then(
                        literal("discardreason").executes(context -> query(context, entryKeyGetter, SubCommand.PRINT_DISCARD_REASONS, level, worldType, biomeType))
                        .then(
                                argument("reason", StringArgumentType.word()).suggests(SPAWNREASONS_SUGGESTION).executes(context -> query(context, entryKeyGetter, SubCommand.DISCARD_REASON, level, worldType, biomeType))
                        )
                )
                .then(
                        literal("chance").executes(context -> query(context, entryKeyGetter, SubCommand.CHANCE, level, worldType, biomeType))
                )
                .then(
                        literal("discard").executes(context -> query(context, entryKeyGetter, SubCommand.DISCARD, level, worldType, biomeType))
                );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends ArgumentBuilder> T rootQuerySettings(T arg, Function<CommandContext<ServerCommandSource>, String> entryKeyGetter) {
        return (T) setQuerySubSettings(arg, entryKeyGetter, Level.ROOT, null, null)
                .then(worldQuerySettings(entryKeyGetter));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends ArgumentBuilder> T worldQuerySettings(Function<CommandContext<ServerCommandSource>, String> entryKeyGetter) {
        return (T) literal("worlds")
                    .then(
                        setQuerySubSettings(argument("str", StringArgumentType.string()).suggests(DIM_SUGGESTION), entryKeyGetter, Level.WORLD, ArgType.STRING, null)
                        .then(biomeQuerySettings(entryKeyGetter, ArgType.STRING))
                    )
                    .then(
                        setQuerySubSettings(argument("id", IdentifierArgumentType.identifier()).suggests(DIM_SUGGESTION), entryKeyGetter, Level.WORLD, ArgType.IDENTIFIER, null)
                        .then(biomeQuerySettings(entryKeyGetter, ArgType.IDENTIFIER))
                    );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends ArgumentBuilder> T biomeQuerySettings(Function<CommandContext<ServerCommandSource>, String> entryKeyGetter, ArgType worldType) {
        return (T) literal("biomes")
                .then(
                        setQuerySubSettings(argument("str", StringArgumentType.string()).suggests(BIOME_SUGGESTION), entryKeyGetter, Level.BIOME, worldType, ArgType.STRING)
                )
                .then(
                        setQuerySubSettings(argument("id", IdentifierArgumentType.identifier()).suggests(BIOME_SUGGESTION), entryKeyGetter, Level.BIOME, worldType, ArgType.IDENTIFIER)
                );
    }

    @SuppressWarnings({"all"})
    public static int query(CommandContext<ServerCommandSource> context, Function<CommandContext<ServerCommandSource>, String> entryKeyGetter, SubCommand subCommand, Level level, ArgType worldType, ArgType biomeType) {
        final String entryKey = entryKeyGetter.apply(context);
        final FabricEntry entry = CONFIG.entries.get(entryKey);

        String path = entryKey;
        FabricSettings elementSettings = entry;

        if (entry == null) {
            context.getSource().sendMessage(ENTRY_NOT_FOUND_TEXT.apply(entryKey));
            return 0;
        }

        if (Level.WORLD == level || Level.BIOME == level) {
            final String rawWorld = switch (worldType) {
                case IDENTIFIER -> IdentifierArgumentType.getIdentifier(context, "id").toString();
                default -> StringArgumentType.getString(context, "str");
            };

            path += ".world#" + rawWorld;
            elementSettings = ((FabricEntry) elementSettings).worlds().containsKey(rawWorld) ? ((FabricEntry) elementSettings).worlds().get(rawWorld) : elementSettings;
        }

        if (Level.BIOME == level) {
            final String rawBiome = switch (biomeType) {
                case IDENTIFIER -> IdentifierArgumentType.getIdentifier(context, "id").toString();
                default -> StringArgumentType.getString(context, "str");
            };

            path += ".biome#" + rawBiome;
            elementSettings = ((FabricWorld) elementSettings).biomes().containsKey(rawBiome) ? ((FabricWorld) elementSettings).biomes().get(rawBiome) : elementSettings;
        }

        if (SubCommand.NONE == subCommand) context.getSource().sendMessage(QUERY_ENTRY_TEXT.apply(entryKey, elementSettings));
        else if (SubCommand.DISCARD_REASON == subCommand) {
            String reason = StringArgumentType.getString(context, "reason");
            boolean discard = elementSettings.isDiscardedBy(reason);
            context.getSource().sendMessage(QUERY_DISCARDREASON_TEXT.apply(new Object[]{path, reason, discard}));
            return discard ? 1 : 0;
        } else if (SubCommand.PRINT_DISCARD_REASONS == subCommand)
            context.getSource().sendMessage(QUERY_DISCARDREASONS_TEXT.apply(path, fancyCollection(elementSettings.discardReasons())));
        else if (SubCommand.DISCARD == subCommand) {
            boolean discard = elementSettings.isDiscarded();
            context.getSource().sendMessage(QUERY_DISCARD_TEXT.apply(path, discard));
            return discard ? 1 : 0;
        } else if (SubCommand.CHANCE == subCommand)
            context.getSource().sendMessage(QUERY_CHANCE_TEXT.apply(path, elementSettings.chance()));

        return 1;
    }

    @SuppressWarnings("all")
    public static int modify(CommandContext<ServerCommandSource> context, Function<CommandContext<ServerCommandSource>, String> entryKeyGetter, SubCommand subCommand, Level level, ArgType worldType, ArgType biomeType) {
        final String entryKey = entryKeyGetter.apply(context);
        final FabricEntry entry = CONFIG.entries.computeIfAbsent(entryKey, k -> new FabricEntry());

        String path = entryKey;
        FabricSettings elementSettings = entry;

        if (Level.WORLD == level || Level.BIOME == level) {
            final String rawWorld = switch (worldType) {
                case IDENTIFIER -> IdentifierArgumentType.getIdentifier(context, "id").toString();
                default -> StringArgumentType.getString(context, "str");
            };

            path += "#world:" + rawWorld;
            elementSettings = ((FabricEntry) elementSettings).worlds().computeIfAbsent(rawWorld, k -> new FabricWorld());
        }
        if (Level.BIOME == level) {
            final String rawBiome = switch (biomeType) {
                case IDENTIFIER -> IdentifierArgumentType.getIdentifier(context, "id").toString();
                default -> StringArgumentType.getString(context, "str");
            };

            path += "#biome:" + rawBiome;
            elementSettings = ((FabricWorld) elementSettings).biomes().computeIfAbsent(rawBiome, k -> new FabricBiome());
        }

        if (SubCommand.DISCARD_REASON == subCommand) {
            String reason = StringArgumentType.getString(context, "reason");
            boolean add = BoolArgumentType.getBool(context, "add");
            elementSettings.discardReason(reason, add);
            context.getSource().sendMessage(MODIFY_DISCARDREASON_TEXT.apply(new Object[]{path, reason, add}));
        } else if (SubCommand.DISCARD == subCommand) {
            elementSettings.discard = BoolArgumentType.getBool(context, "enable");
            context.getSource().sendMessage(MODIFY_DISCARD_TEXT.apply(path, elementSettings.isDiscarded()));
        } else if (SubCommand.CHANCE == subCommand) {
            elementSettings.chance = FloatArgumentType.getFloat(context, "chance");
            context.getSource().sendMessage(MODIFY_CHANCE_TEXT.apply(path, elementSettings.chance()));
        }

        if (CONFIG.autosave) saveToServer(context.getSource().getServer());
        return 0;
    }

    private static int restore(CommandContext<ServerCommandSource> context, ArgType pathType) throws CommandSyntaxException {
        if (pathType != null) {
            String path = switch (pathType) {
                case STRING -> StringArgumentType.getString(context, "path");
                case IDENTIFIER -> IdentifierArgumentType.getIdentifier(context, "type").toString();
                case ENTITY -> EntityType.getId(EntityArgumentType.getEntity(context, "mob").getType()).toString();
            };

            if (!path.startsWith(REGEX_TAG + REGEX_TAG)) {
                var entries = CONFIG.findEntries(path);
                entries.forEach(entry -> CONFIG.entries.remove(entry.getKey()));

                context.getSource().sendMessage(RESTORE_TEXT.apply(entries.size(), path));
                if (CONFIG.autosave) saveToServer(context.getSource().getServer());
                return entries.size();
            } else {
                path = path.substring(1);
                if (CONFIG.entries.remove(path) != null) {
                    context.getSource().sendMessage(RESTORE_TEXT.apply(null, path));
                    if (CONFIG.autosave) saveToServer(context.getSource().getServer());
                    return 1;
                } else context.getSource().sendMessage(ENTRY_NOT_FOUND_TEXT.apply(path));
            }
        } else {
            final int count = CONFIG.entries.size();
            CONFIG.entries.clear();
            context.getSource().sendMessage(RESTORE_TEXT.apply(count, null));
            if (CONFIG.autosave) saveToServer(context.getSource().getServer());
            return count;
        }
        return 0;
    }

    private static int enabled(CommandContext<ServerCommandSource> context, boolean set) {
        if (set) {
            CONFIG.enabled = BoolArgumentType.getBool(context, "enable");
            if (CONFIG.autosave) saveToServer(context.getSource().getServer());
            context.getSource().sendMessage(ENABLED_TEXT.apply(true, CONFIG.enabled));
        } else context.getSource().sendMessage(ENABLED_TEXT.apply(false, CONFIG.enabled));
        return 1;
    }

    private static int permissionLevel(CommandContext<ServerCommandSource> context, boolean set) {
        if (set) {
            CONFIG.permissionLevel = IntegerArgumentType.getInteger(context, "level");
            if (CONFIG.autosave) saveToServer(context.getSource().getServer());
            context.getSource().sendMessage(PERMISSION_LEVEL_TEXT.apply(true, CONFIG.permissionLevel));
        } else context.getSource().sendMessage(PERMISSION_LEVEL_TEXT.apply(false, CONFIG.permissionLevel));
        return 1;
    }

    private static int autosave(CommandContext<ServerCommandSource> context, boolean set) {
        if (set) {
            CONFIG.autosave = BoolArgumentType.getBool(context, "enable");
            if (CONFIG.autosave) saveToServer(context.getSource().getServer());
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

    private enum SubCommand {
        NONE,
        DISCARD_REASON,
        PRINT_DISCARD_REASONS,
        CHANCE,
        DISCARD
    }

    private enum Level {
        ROOT,
        WORLD,
        BIOME
    }

    private enum ArgType {
        ENTITY,
        IDENTIFIER,
        STRING
    }
}