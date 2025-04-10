package dev.coa.wiis.fabric;

import dev.coa.wiis.WIIS;
import net.fabricmc.api.ModInitializer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class FabricWIIS implements WIIS, ModInitializer {
    public static Config CONFIG = new Config();

    @Override
    public void onInitialize() {
        registerCommands();
    }

    @Override
    public void registerCommands() {

    }

    public static int ifAvailable(Runnable ifOn, Runnable ifOff) {
        if (isEnabled()) ifOn.run();
        else ifOff.run();
        return isEnabled()? 1 : 0;
    }

    public static boolean isEnabled() {
        return CONFIG.enabled;
    }

    public static class Config extends dev.coa.wiis.Config {
        public void editEntity(Identifier id, Consumer<Entry> consumer) {
            editEntry(id.toString(), consumer);
        }

        public void editEntity(EntityType<?> entityType, Consumer<Entry> consumer) {
            editEntity(EntityType.getId(entityType), consumer);
        }

        public boolean allowSpawn(Identifier id, SpawnReason reason) {
            return allowSpawn(id.toString(), reason);
        }

        public boolean allowSpawn(Entity entity, SpawnReason reason) {
            return allowSpawn(entity.getType(), reason);
        }

        public boolean allowSpawn(EntityType<?> entityType, SpawnReason reason) {
            return allowSpawn(EntityType.getId(entityType), reason);
        }

        public static MutableText fancyEntry(String key, boolean value) {
            return Text.empty().append(Text.literal(key).formatted(Formatting.GREEN)).append(": ").append(Text.literal(value + "").formatted(Formatting.LIGHT_PURPLE));
        }

        public static MutableText fancyEntries(List<Map.Entry<String, Boolean>> entries) {
            MutableText text = Text.empty();
            text.append("{");
            Iterator<Map.Entry<String, Boolean>> iterator = entries.iterator();
            iterator.forEachRemaining(entry -> {
                text.append(fancyEntry(entry.getKey(), entry.getValue()));
                if (iterator.hasNext()) text.append(Text.literal(", ").formatted(Formatting.GOLD));
            });
            return text.append("}");
        }
    }

    //public static MutableText WIIS_UNAVAILABLE = Text.translatable("wiis.unavailable").formatted(Formatting.RED);
    //public static MutableText ENTRY_NOTFOUND = Text.translatable("wiis.entry_notfound").formatted(Formatting.RED);
}
