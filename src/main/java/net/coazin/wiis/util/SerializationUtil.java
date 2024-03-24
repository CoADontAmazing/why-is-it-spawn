package net.coazin.wiis.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.coazin.wiis.MainMod;
import net.minecraft.nbt.NbtCompound;

public class SerializationUtil {
	public static JsonObject toJson(NbtCompound nbt) {
		return JsonParser.parseString(nbt.getString(MainMod.ID)).getAsJsonObject();
	}

	public static NbtCompound toNbt(JsonObject object) {
		NbtCompound nbt = new NbtCompound();
		nbt.putString(MainMod.ID, object.toString());
		return nbt;
	}
}
