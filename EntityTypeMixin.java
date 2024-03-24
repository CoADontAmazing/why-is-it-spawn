package net.coazin.wiis.mixin;

import java.util.function.Consumer;
import net.coazin.wiis.MainMod;
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
	private <T extends Entity> void createEntity(ServerWorld world, NbtCompound itemNbt, Consumer<T> afterConsumer, BlockPos pos,
			SpawnReason reason, boolean alignPosition, boolean invertY, CallbackInfoReturnable<T> ci) {
		if (MainMod.config.isEnable()){
			if(!MainMod.config.isAllowedMob(EntityType.getId((EntityType)(Object) this), reason)) ci.setReturnValue(null);
		}
	}
}