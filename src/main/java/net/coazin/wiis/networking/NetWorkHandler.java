package net.coazin.wiis.networking;

import net.coazin.wiis.MainMod;
import net.coazin.wiis.config.ConfigLoader;
import net.coazin.wiis.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public class NetWorkHandler {
	public static void onServerReceive() {
		ServerPlayNetworking.registerGlobalReceiver(MainMod.config.getChannel(), (server, player, serverhandler, buf, packetsender) -> {
			if (!server.isSingleplayer()) {
				if (PacketUtils.havePermission(player)) {
					MainMod.config.load(SerializationUtil.toJson(buf.readNbt()));
					PacketUtils.send2AllC(server, MainMod.config);
				}
			} else {
				MainMod.config.save(SerializationUtil.toJson(buf.readNbt()));
			}
		});
	}
	
	public static void onClientReceive() {
		ClientPlayNetworking.registerGlobalReceiver(MainMod.config.getChannel(), (client, handler, buf, sender) -> {
			MainMod.config.load(SerializationUtil.toJson(buf.readNbt()));
		});
	}

	public static void tryBroadcast(MinecraftClient client) {
		if(client.player != null && client.player.getWorld().isClient && MainMod.config.isEnable()) PacketUtils.send2S(MainMod.config);
	}
}
