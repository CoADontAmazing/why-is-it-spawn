package net.coazin.wiis.config;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.List;
import net.coazin.wiis.MainMod;
import net.coazin.wiis.util.*;
import net.fabricmc.loader.FabricLoader;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;

public class ConfigLoader {
	private JsonObject root = null;
	private Path configpath;
	private static String RULES = "entity_rules";
	private static String ENABLED = "enabled";
	private static String NOT_INCLUDE = "not_include";
	private boolean enable = false;
	private Identifier CHANNEL = null;
	public static Gson BUILDER = new GsonBuilder().setPrettyPrinting().create();

	public static ConfigLoader initialize(String modid) {
		return new ConfigLoader().path(getConfigPath(modid)).tryLoadLocal();
	}

	public void bindChannel(Identifier channel) {
		this.CHANNEL = channel;
	}

	public Identifier getChannel() {
		return this.CHANNEL;
	}

	public ConfigLoader path(Path file) {
		this.configpath = file;
		return this;
	}

	public Path path() {
		return this.configpath;
	}

	public JsonObject json() {
		return this.root;
	}

	public boolean isEnable() {
		return this.root.get(ENABLED).getAsBoolean();
	}

	public void setEnable(boolean state) {
		this.enable = state;
		this.root.remove(ENABLED);
		this.root.addProperty(ENABLED,this.enable);
	}

	public void load(JsonObject json) {
		this.root = json;
	}

	public boolean loadFile(Path file) {
		try {
			BufferedReader br = Files.newBufferedReader(file);
			this.root = JsonParser.parseReader(br).getAsJsonObject();
			br.close();
			return true;
		} catch (Exception exception) {}
		return false;
	}

	public boolean loadFile() {
		return loadFile(this.configpath);
	}

	public ConfigLoader tryLoadLocal() {
		try {
			BufferedReader br = Files.newBufferedReader(this.configpath);
			this.root = JsonParser.parseReader(br).getAsJsonObject();
			br.close();
		} catch (Exception exception) {
			try {
				BufferedWriter bw = Files.newBufferedWriter(this.configpath);
				this.root = newConfigFields(CommonUtil.allMobList(), List.of("player"));
				BUILDER.toJson(this.root, bw);
				bw.close();
			} catch (Exception e) {
			}
		}
		return this;
	}

	public boolean save(JsonObject json) {
		try {
			BufferedWriter bw = Files.newBufferedWriter(configpath);
			this.root = json;
			BUILDER.toJson(this.root, bw);
			bw.close();
			return true;
		} catch (Exception exception) {}
		return false;
	}

	public void save() {
		save(this.root);
	}

	public void delete() {
		try {
			Files.delete(configpath);
		} catch (IOException e) {
		}
	}

	public static JsonObject newConfigFields(List<Identifier> list, List<String> dontinclude) {
		JsonObject json = new JsonObject();
		json.addProperty(ENABLED,false);
		json.addProperty(NOT_INCLUDE,"player");
		JsonObject rules = new JsonObject();
		list.forEach((s) -> {
			if (!dontinclude.contains(s.getPath()))
				rules.add(id(s), newFields(new JsonObject()));
		});
		json.add(RULES,rules);
		return json;
	}

	public static JsonObject newFields(JsonObject object) {
		object.addProperty("allowSpawners", true);
		object.addProperty("allowSpawnEggs", true);
		object.addProperty("allowNatural", true);
		object.addProperty("allowConversions", true);
		object.addProperty("allowCommands", true);
		object.addProperty("disabled", false);
		return object;
	}

	public boolean isAllowedMob(Entity entity, SpawnReason reason) {
		return isAllowedMob(EntityType.getId(entity.getType()), reason);
	}

	public boolean isAllowedMob(Identifier ID, SpawnReason reason) {
		if (!enable) return true;
		if (isDisabledMob(ID)) return false;
		String path = id(ID);
		if (!this.root.has(path)) return true;
		JsonObject mob = this.root.get(RULES).getAsJsonObject().get(path).getAsJsonObject();		
		switch (reason) {
		case SPAWNER:
			return mob.get("allowSpawners").getAsBoolean();
		case SPAWN_EGG:
			return mob.get("allowSpawnEggs").getAsBoolean();
		case NATURAL:
			return mob.get("allowNatural").getAsBoolean();
		case CONVERSION:
			return mob.get("allowConversions").getAsBoolean();
		case COMMAND:
			return mob.get("allowCommands").getAsBoolean();
		}
		return true;
	}

	public boolean isDisabledMob(Identifier ID) {
		if (!enable) return false;
		String path = id(ID);
		if (!this.root.get(RULES).getAsJsonObject().has(path)) return false;
		return this.root.get(RULES).getAsJsonObject().get(path).getAsJsonObject().get("disabled").getAsBoolean();
	}

	public boolean isDisabledMob(Entity entity) {
		return isDisabledMob(EntityType.getId(entity.getType()));
	}

	public static String id(Identifier id) {
		return id.getNamespace() + "/" + id.getPath();
	}

	public static Path getConfigPath(String id) {
		return FabricLoader.INSTANCE.getConfigDir().resolve(id + ".json");
	}

	public JsonObject entry(String key) {
		return this.root.get(RULES).getAsJsonObject().get(key).getAsJsonObject();
	}

	public void entry(String key, JsonObject object) {
		this.root.get(RULES).getAsJsonObject().remove(key);
		this.root.get(RULES).getAsJsonObject().add(key, object);
		save(this.root);
	}
	
	public void replace(String namespace, String key, boolean value){
		if (!this.root.get(RULES).getAsJsonObject().has(namespace)) return;
		JsonObject obj = this.root.get(RULES).getAsJsonObject().get(namespace).getAsJsonObject();
		if (!obj.has(key) || obj.get(key).getAsBoolean() == value) return;
		obj.remove(key);
		obj.addProperty(key,value);
		save(this.root);
	}
}