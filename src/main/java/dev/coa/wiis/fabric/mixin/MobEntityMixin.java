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
public abstract class MobEntityMixin {
	@Inject(method = "initialize", at = @At("HEAD"), cancellable = true)
	private void wiis$init(ServerWorldAccess swa, LocalDifficulty d, SpawnReason reason, EntityData ed, NbtCompound nbt, CallbackInfoReturnable<EntityData> cir) {
		if (FabricWIIS.isEnabled() && !FabricWIIS.CONFIG.allowSpawn((MobEntity) (Object) this, reason, getMobWorld())) {
			FabricWIIS.debug("at: MobEntity.initialize(mob: " + this + ", spawnReason: " + reason + ") removed");
			((MobEntity) (Object) this).discard();
		}
	}

	@Inject(method = "checkDespawn", at = @At("HEAD"), cancellable = true)
	private void wiis$tryDespawn(CallbackInfo i) {
		if (FabricWIIS.isEnabled() && !FabricWIIS.CONFIG.allowSpawn((MobEntity) (Object) this, null, getMobWorld())) {
			FabricWIIS.debug("at: MobEntity.checkDespawn(mob: " + this + ") removed");
			((MobEntity) (Object) this).discard();
			i.cancel();
		}
	}

	protected World getMobWorld() {
		return ((MobEntity) (Object) this).getWorld();
	}
}
