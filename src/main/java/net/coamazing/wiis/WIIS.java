package net.coamazing.wiis;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.gamerule.v1.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import org.slf4j.*;

public class WIIS implements ModInitializer {
	public static final String ID = "wiis", NAME = "Why Is It Spawn", VERSION = "v1";
	public static final Logger LOGGER = LoggerFactory.getLogger(ID.toUpperCase());
	public static final GameRules.Key<GameRules.BooleanRule> DO_USE_WIIS = GameRuleRegistry.register("doUseWIIS",
			GameRules.Category.MISC, GameRuleFactory.createBooleanRule(false));
	public static Config CONFIG = new Config();
	public static final int PERMISSION_LEVEL = 4;
	public static boolean isDev = false;
	
	@Override
	public void onInitialize() {
		if (isDev = FabricLoader.getInstance().isDevelopmentEnvironment()) debug("[" + ID.toUpperCase() + "]: logging is enabled in the dev env!");
		ServerLifecycleEvents.SERVER_STARTED.register(CONFIG::load);
		LOGGER.info("[" + ID.toUpperCase() + "]: loading!");
		Config.registerCommands();
	}
	
	public static boolean doUseWiis(World world) {
		return world.getGameRules().getBoolean(DO_USE_WIIS);
	}
	
	public static void debug(String msg) {
		if (isDev) LOGGER.info(msg);
	}
}
