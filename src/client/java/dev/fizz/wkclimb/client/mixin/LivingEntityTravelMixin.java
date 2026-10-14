package dev.fizz.wkclimb.client.mixin;

import dev.fizz.wkclimb.client.ClimbController;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityTravelMixin {
	@Inject(method = "travel", at = @At("HEAD"), cancellable = true)
	private void wkclimb$travel(Vec3 input, CallbackInfo ci) {
		if ((Object) this instanceof LocalPlayer player && ClimbController.INSTANCE.travel(player)) {
			ci.cancel();
		}
	}
}
