package net.coazin.wiis;

import java.util.ArrayList;
import java.util.List;
import net.coazin.wiis.cmd.ServerCmd;
import net.coazin.wiis.networking.NetWorkHandler;
import net.coazin.wiis.config.ConfigLoader;
import net.coazin.wiis.util.CommonUtil;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainMod implements ModInitializer {
	public static final String ID = "wiis";
	public static final String NAME = "Why Is It Spawn";
	public static Logger LOGGER = LoggerFactory.getLogger(ID);
	public static final Identifier MAIN_CHANNEL = CommonUtil.id("config_sync");
	public static KeyBinding key = new KeyBinding("wiis.config.open", GLFW.GLFW_KEY_W, "key.categories.misc");
	public static ConfigLoader config;
	
	@Override
	public void onInitialize() {
		LOGGER.info(ID.toUpperCase() + " updates configs!");
		
		config = ConfigLoader.initialize(ID);
		config.bindChannel(MAIN_CHANNEL);
		
		LOGGER.info(ID.toUpperCase() + " setup synchronisation!");
		NetWorkHandler.onServerReceive();
		NetWorkHandler.onClientReceive();
		
		CommandRegistrationCallback.EVENT.register((d,r,v) -> ServerCmd.register(d,r));
		
		KeyBindingHelper.registerKeyBinding(key);
		
		ClientTickEvents.START_CLIENT_TICK.register((client) -> {
			if (key.wasPressed()){
				if (client.player.hasPermissionLevel(4)){
					 if (!config.isEnable()) client.player.networkHandler.sendChatCommand("wiis enable");
					  else client.player.networkHandler.sendChatCommand("wiis disable");
				} else {
					client.player.sendMessage(Text.of("You can't edit that'"));
				}
			}
			NetWorkHandler.tryBroadcast(client);
		});
		
		LOGGER.info(ID.toUpperCase() + " start work!");
	}
}
