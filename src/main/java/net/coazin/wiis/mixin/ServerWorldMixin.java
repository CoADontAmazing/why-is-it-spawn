package net.coazin.wiis.mixin;

import net.coazin.wiis.MainMod;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerWorld.class)
public class ServerWorldMixin {
	@Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
	private void onPutEntity(Entity entity, CallbackInfoReturnable<Boolean> ci) {
		if (MainMod.config.isEnable()){
		  if (MainMod.config.isDisabledMob(entity)) {
		  	if (!entity.isRemoved()) entity.discard();
			  ci.setReturnValue(false);
		  }
		  if (entity.isRemoved()) ci.setReturnValue(false);
		}
	}
}
