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
	private void wiis$addEntity(Entity entity, CallbackInfoReturnable<Boolean> ci) {
		if (FabricWIIS.isEnabled() && !FabricWIIS.CONFIG.allowSpawn(entity, null, (ServerWorld) (Object) this)) {
			if (!entity.isRemoved()) {
				FabricWIIS.debug("at: ServerWorld.addEntity(entity: " + entity + ") removed");
				entity.discard();
			}
			if (entity.isRemoved()) ci.setReturnValue(false);
		}
	}
}