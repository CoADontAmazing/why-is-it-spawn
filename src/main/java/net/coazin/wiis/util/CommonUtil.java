package net.coazin.wiis.util;

import java.util.ArrayList;
import net.coazin.wiis.MainMod;
import net.minecraft.registry.Registries;
import java.util.List;
import net.minecraft.util.Identifier;

public class CommonUtil {
	public static List<Identifier> allMobList() {
		List<Identifier> list = new ArrayList<>();
		for (Identifier id : Registries.ENTITY_TYPE.getIds()) {
			if (id != null)
				list.add(id);
		}
		return list;
	}
	
	public static Identifier id(String key){
		return new Identifier(MainMod.ID,key);
	}
}
