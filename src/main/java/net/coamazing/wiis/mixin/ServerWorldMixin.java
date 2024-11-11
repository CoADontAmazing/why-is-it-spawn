package net.coamazing.wiis.mixin;

import net.coamazing.wiis.WIIS;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerWorld.class)
public class ServerWorldMixin {
	@Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
	private void wiis$onAddEntity(Entity entity, CallbackInfoReturnable<Boolean> ci) {
		if (!WIIS.CONFIG.getState((ServerWorld) (Object) this, entity, null)) {
			WIIS.debug("[#onAddEntity] entity: " + entity + " removed");
			if (!entity.isRemoved()) entity.discard();
			if (entity.isRemoved()) ci.setReturnValue(false);
		}
	}
}