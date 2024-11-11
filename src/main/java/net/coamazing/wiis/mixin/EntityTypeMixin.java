package net.coamazing.wiis.mixin;

import java.util.function.Consumer;
import net.coamazing.wiis.WIIS;
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
	private <T extends Entity> void wiis$onCreate(ServerWorld world, NbtCompound itemNbt, Consumer<T> afterConsumer,
			BlockPos pos, SpawnReason reason, boolean alignPosition, boolean invertY, CallbackInfoReturnable<T> ci) {
		if (!WIIS.CONFIG.getState(world, (EntityType) (Object) this, reason)) {
			WIIS.debug("[#onCreate] entityType: " + (EntityType) (Object) this + " removed by reason: " + reason);
			ci.setReturnValue(null);
		}
	}
}