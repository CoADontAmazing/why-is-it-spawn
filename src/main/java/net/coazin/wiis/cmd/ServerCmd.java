package net.coazin.wiis.cmd;

import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;

import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import java.util.List;
import net.coazin.wiis.MainMod;
import net.coazin.wiis.config.ConfigLoader;
import net.coazin.wiis.util.CommonUtil;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.EnumArgumentType;
import net.minecraft.command.argument.RegistryEntryArgumentType;
import net.minecraft.command.suggestion.SuggestionProviders;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.ResourceType;
import net.minecraft.server.command.ServerCommandSource;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;

import static net.minecraft.server.command.CommandManager.*;
import net.minecraft.text.Text;

public class ServerCmd {   
	public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess cra) {
		dispatcher.register(literal(MainMod.ID).executes(c -> info(c))
		  .then(literal("spawnrule").requires(c -> c.getPlayer().hasPermissionLevel(4))
		  	.then(argument("mob",RegistryEntryArgumentType.registryEntry(cra, RegistryKeys.ENTITY_TYPE))
			  	.suggests(SuggestionProviders.SUMMONABLE_ENTITIES)
			  	.then(literal("get").executes(c -> get(c)))
				  .then(literal("set")
				  	.then(argument("key", StringArgumentType.word())
					  	.then(argument("value", BoolArgumentType.bool())
						  	.executes(c -> set(c)))))
				  .then(literal("reset").executes(c -> reset(c)))))
		  .then(literal("resetAll").requires(c -> c.getPlayer().hasPermissionLevel(4)).executes(c -> resetAll(c)))
		  .then(literal("fromFile").requires(c -> c.getPlayer().hasPermissionLevel(4)).executes(c -> reload(c)))
		  .then(literal("disable").requires(c -> c.getPlayer().hasPermissionLevel(4)).executes(c -> modstate(c,false)))
		  .then(literal("enable").requires(c -> c.getPlayer().hasPermissionLevel(4)).executes(c -> modstate(c,true))));
    }
 
	private static int get(CommandContext<ServerCommandSource> context ) throws CommandSyntaxException{
		String mob = EntityType.getId(RegistryEntryArgumentType.getEntityType(context,"mob").value()).toString().replace(":","/");
		context.getSource().sendMessage(Text.of("Json of " + mob + "\n" + MainMod.config.entry(mob).toString()));		
		return 1;
	}
	
	private static int set(CommandContext<ServerCommandSource> context)throws CommandSyntaxException{
		String mob = EntityType.getId(RegistryEntryArgumentType.getEntityType(context,"mob").value()).toString().replace(":","/");
		String key = StringArgumentType.getString(context,"key");
		boolean val = BoolArgumentType.getBool(context,"value");
		MainMod.config.replace(mob,key,val);
		context.getSource().sendMessage(Text.of("Rewrite property in " + mob + " " + key + " on " + val));
		return 1;
	}
	private static int reset(CommandContext<ServerCommandSource> context)throws CommandSyntaxException{
		String mob = EntityType.getId(RegistryEntryArgumentType.getEntityType(context,"mob").value()).toString().replace(":","/");
		MainMod.config.entry(mob,ConfigLoader.newFields(new JsonObject()));
		context.getSource().sendMessage(Text.translatable("wiis.command.reset").append(" " + mob));
		return 1;
	}
	private static int reload(CommandContext<ServerCommandSource> context){
		MainMod.config.loadFile();
		context.getSource().sendMessage(Text.translatable("wiis.command.reload"));
		return 1;
	}
	private static int modstate(CommandContext<ServerCommandSource> context, boolean bool){
		MainMod.config.setEnable(bool);
		if (bool){
			context.getSource().sendMessage(Text.translatable("wiis.command.enable"));
		} else {
			context.getSource().sendMessage(Text.translatable("wiis.command.disable"));
		}
		return 1;
	}
	private static int resetAll(CommandContext<ServerCommandSource> context){
		MainMod.config.save(ConfigLoader.newConfigFields(CommonUtil.allMobList(),List.of("player")));
		context.getSource().sendMessage(Text.translatable("wiis.command.resetAll"));
		return 1;
	}
	private static int info(CommandContext<ServerCommandSource> context){
		context.getSource().sendMessage(Text.translatable("wiis.command.info"));
		return 0;
	}
}
