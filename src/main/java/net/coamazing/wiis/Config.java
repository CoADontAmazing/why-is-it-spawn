package net.coamazing.wiis;

import com.google.gson.*;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.*;
import net.minecraft.entity.*;
import net.minecraft.text.*;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraft.command.CommandSource;
import net.minecraft.command.suggestion.SuggestionProviders;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import static net.minecraft.server.command.CommandManager.*;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class Config extends HashMap<Identifier, Map<String, Boolean>> {
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	public static final String SHOULD_DESPAWN_KEY = "shouldDespawn";
	public static final Map<SpawnReason, String> ACCEPTED_REASONS = Util.make(() -> {
		Map<SpawnReason, String> map = new HashMap<>(SpawnReason.values().length);
		for (SpawnReason reason : SpawnReason.values()) {
			String rName = "";
			for (String string : reason.name().toLowerCase().split("_")) rName += (Character.toUpperCase(string.charAt(0)) + string.substring(1, string.length()));
			map.put(reason, Character.toLowerCase(rName.charAt(0)) + rName.substring(1, rName.length()));
		}
		return Map.copyOf(map);
	});
	public static final List<String> ACCEPTED_KEYS = Util.make(() -> {
		ArrayList<String> list = new ArrayList<>(SpawnReason.values().length + 1);
		list.add(SHOULD_DESPAWN_KEY);
		list.addAll(ACCEPTED_REASONS.values());
		return List.copyOf(list);
	});
	public static Predicate<ServerCommandSource> PERMISSION_FILTER = (x) -> x.getPlayer().hasPermissionLevel(WIIS.PERMISSION_LEVEL);
	public static Function<MinecraftServer, Path> WORLD_PATH_GETTER = (server) -> server.getSavePath(WorldSavePath.SESSION_LOCK).getParent().resolve("wiis-config").resolve("server.json");
	public static Path configPath;
	
	public boolean getState(World world, Entity entity, SpawnReason reason) {
		return getState(world, entity.getType(), reason);
	}
	
	public boolean getState(World world, EntityType entityType, SpawnReason reason) {
		if (!WIIS.doUseWiis(world)) return true;
		final Identifier id = EntityType.getId(entityType);
		final boolean shouldSpawn = !get(id, SHOULD_DESPAWN_KEY);
		return reason == null? shouldSpawn : get(id, ACCEPTED_REASONS.get(reason)) && shouldSpawn;
	}
	
	public boolean get(Identifier id, String key, boolean defaultValue) {
		if (id == null || key == null) return defaultValue;
		return containsKey(id) && get(id).containsKey(key)? get(id).get(key) : defaultValue;
	}
	
	public boolean get(Identifier id, String key) {
		return get(id, key, key != SHOULD_DESPAWN_KEY);
	}
	
	public void set(Identifier id, String key, boolean value) {
		if (id == null || key == null) return;
		putIfAbsent(id, new HashMap<>());
		get(id).put(key, value);
		save();
	}
	
	private void load(Path path) {
		if (!Files.exists(path)) {
			clear();
			return;
		}
		try {
			final BufferedReader reader = Files.newBufferedReader(path);
			clear();
			putAll(GSON.fromJson(reader, Config.class));
			reader.close();
		} catch (Exception ex) {}
	}
	
	public void load(MinecraftServer server) {
		load(configPath = WORLD_PATH_GETTER.apply(server));
	}
	
	public void save() {
		try {
			if (!Files.exists(configPath.getParent())) Files.createDirectories(configPath.getParent());
			final BufferedWriter writer = Files.newBufferedWriter(configPath);
			GSON.toJson(this, writer);
			writer.close();
		} catch (Exception ex) {}
	}
	
	public void clear(Identifier id) {
		remove(id);
		save();
	}
	
	public static void registerCommands() {
		CommandRegistrationCallback.EVENT.register((d, a, r) -> {
			d.register(
				literal("wiis-config").executes(Config::info)
					.then(literal("query").requires(PERMISSION_FILTER)
						.then(argument("mobId", IdentifierArgumentType.identifier())
						.suggests(SuggestionProviders.SUMMONABLE_ENTITIES).executes(s -> Config.query(s, null))
							.then(argument("key", StringArgumentType.word())
							.suggests((b, s) -> CommandSource.suggestMatching(ACCEPTED_KEYS.stream(), s)).executes(s -> Config.query(s, StringArgumentType.getString(s, "key"))))))
					.then(literal("modify").requires(PERMISSION_FILTER)
						.then(argument("mobId", IdentifierArgumentType.identifier())
						.suggests(SuggestionProviders.SUMMONABLE_ENTITIES)
							.then(argument("key", StringArgumentType.word()).suggests((b, s) -> CommandSource.suggestMatching(ACCEPTED_KEYS.stream(), s))
								.then(argument("value", BoolArgumentType.bool()).executes(Config::modify)))))
					.then(literal("clear").requires(PERMISSION_FILTER).executes(s -> Config.clear(s, null))
						.then(argument("mobId", IdentifierArgumentType.identifier()).suggests(SuggestionProviders.SUMMONABLE_ENTITIES).executes(s -> Config.clear(s, IdentifierArgumentType.getIdentifier(s, "mobId")))))
			);
		});
	}
	
	private static int info(CommandContext<ServerCommandSource> source) {
		source.getSource().getPlayer().sendMessage(Text.translatable("wiis-config.info", Formatting.GREEN + WIIS.VERSION + (WIIS.isDev? Formatting.GOLD + " (Launched In The Dev.Env)" : "")).append(" ").append(Text.literal("Modrinth Link").styled(e -> e.withColor(0x23D86F).withUnderline(true).withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://modrinth.com/mod/wiis")))));
		return 0;
	}
		
	private static int query(CommandContext<ServerCommandSource> source, String key) {
		if (!WIIS.doUseWiis(source.getSource().getWorld())) {
			source.getSource().getPlayer().sendMessage(NO_AVAILABLE_WIIS); return 0;
		}
		Identifier mobId = IdentifierArgumentType.getIdentifier(source, "mobId");
		MutableText text = Text.empty();
		if (key == null) {
			text.append("{");
			for (String aKey : ACCEPTED_KEYS){
				text.append(getFormattedEntry(aKey, WIIS.CONFIG.get(mobId, aKey)));
				if (ACCEPTED_KEYS.indexOf(aKey) + 1 != ACCEPTED_KEYS.size()) text.append(Text.literal(", ").formatted(Formatting.GOLD));
			}
			text.append("}");
		} else text.append(getFormattedEntry(key, WIIS.CONFIG.get(mobId, key)));
		source.getSource().getPlayer().sendMessage(Text.translatable("wiis-config.query", mobId.toString()).append(" ").append(text));
		return 1;
	}
		
	private static int modify(CommandContext<ServerCommandSource> source) {
		if (!WIIS.doUseWiis(source.getSource().getWorld())) {
			source.getSource().getPlayer().sendMessage(NO_AVAILABLE_WIIS); return 0;
		}
		Identifier mobId = IdentifierArgumentType.getIdentifier(source, "mobId");
		String key = StringArgumentType.getString(source, "key");
		boolean value = BoolArgumentType.getBool(source, "value");
		WIIS.CONFIG.set(mobId, key, value);
		source.getSource().getPlayer().sendMessage(Text.translatable("wiis-config.modify", mobId.toString(), Formatting.GREEN + key, Formatting.LIGHT_PURPLE + "" + value));
		return 1;
	}
		
	private static int clear(CommandContext<ServerCommandSource> source, Identifier mobId) {
		if (!WIIS.doUseWiis(source.getSource().getWorld())) {
			source.getSource().getPlayer().sendMessage(NO_AVAILABLE_WIIS); return 0;
		}
		MutableText text;
		if (mobId == null) {
			WIIS.CONFIG.clear();
			text = Text.translatable("wiis-config.clear_all");
		} else {
			WIIS.CONFIG.clear(mobId);
			text = Text.translatable("wiis-config.clear", mobId.toString());
		}
		source.getSource().getPlayer().sendMessage(text);
		return 1;
	}
	
	protected static MutableText getFormattedEntry(String key, boolean value) {
		return Text.empty().append(Text.literal(key).formatted(Formatting.GREEN)).append(": ").append(Text.literal(value + "").formatted(Formatting.LIGHT_PURPLE));
	}
	
	public static MutableText NO_AVAILABLE_WIIS = Text.translatable("wiis-config.no_available").formatted(Formatting.RED);
}
