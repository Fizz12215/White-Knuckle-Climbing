package dev.fizz.wkclimb.client.mixin;

import dev.fizz.wkclimb.client.ClimbCamera;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** White Knuckle's FOV in place of the player's setting (26.3 works the FOV out on the camera). */
@Mixin(Camera.class)
public abstract class CameraFovMixin {
	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void wkclimb$fov(float partialTick, CallbackInfoReturnable<Float> cir) {
		cir.setReturnValue(ClimbCamera.fov(cir.getReturnValueF()));
	}
}
