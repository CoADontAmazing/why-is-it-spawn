package net.coazin.wiis.mixin;

import net.coazin.wiis.MainMod;
import net.minecraft.entity.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(MobEntity.class)
public abstract class MobEntityMixin {
	@Inject(method = "initialize", at = @At("HEAD"), cancellable = true)
	private void onInitEvent(ServerWorldAccess swa, LocalDifficulty d, SpawnReason reason, EntityData ed, NbtCompound nbt, CallbackInfoReturnable<EntityData> ci) {
		if (MainMod.config.isEnable()) {
			if (!MainMod.config.isAllowedMob((MobEntity)(Object)this,reason)) ((MobEntity) (Object) this).remove(Entity.RemovalReason.DISCARDED);
		}
	}
	
	@Inject(method = "checkDespawn", at = @At("HEAD"), cancellable = true)
	private void onDespawnTry(CallbackInfo i){
		if (MainMod.config.isEnable() && MainMod.config.isDisabledMob((MobEntity)(Object)this)) {
			((MobEntity)(Object) this).discard();
			i.cancel();
		}
	}
}
