package net.coamazing.wiis.mixin;

import net.coamazing.wiis.WIIS;
import net.minecraft.entity.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(MobEntity.class)
public class MobEntityMixin {

	@Inject(method = "initialize", at = @At("HEAD"), cancellable = true)
	private void wiis$onInit(ServerWorldAccess swa, LocalDifficulty d, SpawnReason reason, EntityData ed, NbtCompound nbt, CallbackInfoReturnable<EntityData> cir) {
		if (!WIIS.CONFIG.getState(((MobEntity)(Object) this).getWorld(), (MobEntity) (Object) this, reason)) {
			WIIS.debug("[#onInit] modEntity: " + (MobEntity) (Object) this + " removed by reason: " + reason);
			((MobEntity) (Object) this).remove(Entity.RemovalReason.DISCARDED);
		}
	}

	@Inject(method = "checkDespawn", at = @At("HEAD"), cancellable = true)
	private void wiis$onTryDespawn(CallbackInfo i) {
		if (!WIIS.CONFIG.getState(((MobEntity)(Object) this).getWorld(), (MobEntity) (Object) this, null)) {
			WIIS.debug("[#onTryDespawn] modEntity: " + (MobEntity) (Object) this + " removed");
			((MobEntity) (Object) this).discard();
			i.cancel();
		}
	}
}
