package dev.coa.wiis.fabric.mixin;

import dev.coa.wiis.fabric.FabricWIIS;
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
		if (!FabricWIIS.CONFIG.allowSpawn((MobEntity) (Object) this, reason)) {
			//WIIS.debug("debug at: MobEntity.initialize() mob: " + (MobEntity) (Object) this + ", spawnReason: " + reason + " removed");
			((MobEntity) (Object) this).remove(Entity.RemovalReason.DISCARDED);
		}
	}

	@Inject(method = "checkDespawn", at = @At("HEAD"), cancellable = true)
	private void wiis$onTryDespawn(CallbackInfo i) {
		if (!FabricWIIS.CONFIG.allowSpawn((MobEntity) (Object) this, null)) {
			//WIIS.debug("debug at: MobEntity.checkDespawn() mob: " + (MobEntity) (Object) this + " removed");
			((MobEntity) (Object) this).discard();
			i.cancel();
		}
	}
}
