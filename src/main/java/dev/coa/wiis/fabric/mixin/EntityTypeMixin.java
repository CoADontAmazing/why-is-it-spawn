package dev.coa.wiis.fabric.mixin;

import java.util.function.Consumer;
import dev.coa.wiis.fabric.FabricWIIS;
import net.minecraft.entity.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityType.class)
public class EntityTypeMixin {
	@Inject(method = "create", at = @At("HEAD"), cancellable = true)
	private <T extends Entity> void wiis$create(ServerWorld world, NbtCompound itemNbt, Consumer<T> afterConsumer,
			BlockPos pos, SpawnReason reason, boolean alignPosition, boolean invertY, CallbackInfoReturnable<T> ci) {
		if (FabricWIIS.isEnabled() && !FabricWIIS.CONFIG.canSpawn(EntityType.class.cast(this), reason, world, world.getBiome(pos).getKey().get())) {
			FabricWIIS.debug("at: EntityType.create(type: " + EntityType.getId(EntityType.class.cast(this)) + ", spawnReason: " + reason + ") removed");
			ci.setReturnValue(null);
		}
	}
}