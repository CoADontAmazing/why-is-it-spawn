package dev.coa.wiis.fabric.mixin;

import dev.coa.wiis.fabric.FabricWIIS;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerWorld.class)
public class ServerWorldMixin {
	@Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
	private void wiis$onAddEntity(Entity entity, CallbackInfoReturnable<Boolean> ci) {
		if (!FabricWIIS.CONFIG.allowSpawn(entity, null)) {
			//WIIS.debug("debug at: ServerWorld.addEntity() entity: " + entity + " removed");
			if (!entity.isRemoved()) entity.discard();
			if (entity.isRemoved()) ci.setReturnValue(false);
		}
	}
}