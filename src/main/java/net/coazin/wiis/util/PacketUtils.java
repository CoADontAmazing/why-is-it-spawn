package net.coazin.wiis.util;

import net.coazin.wiis.config.ConfigLoader;
import net.coazin.wiis.MainMod;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.network.PacketByteBuf;

public class PacketUtils {
	public static void send2C(ServerPlayerEntity player, ConfigLoader config) {
		PacketByteBuf packet = PacketByteBufs.create();
		packet.writeNbt(SerializationUtil.toNbt(config.json()));
		ServerPlayNetworking.send(player, MainMod.config.getChannel(), packet);
	}

	public static void send2S(ConfigLoader config) {
		PacketByteBuf packet = PacketByteBufs.create();
		packet.writeNbt(SerializationUtil.toNbt(config.json()));
		ClientPlayNetworking.send(MainMod.config.getChannel(), packet);
	}
	
	public static void send2AllC(MinecraftServer server, ConfigLoader config){
	  for (ServerPlayerEntity client_player : server.getPlayerManager().getPlayerList()) {
			send2C(client_player, config);
		}
	}
	
	public static boolean havePermission(ServerPlayerEntity player){
		return player.hasPermissionLevel(4);
	}
}
