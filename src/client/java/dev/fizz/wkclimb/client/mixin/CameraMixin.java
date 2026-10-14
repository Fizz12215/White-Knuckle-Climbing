package dev.fizz.wkclimb.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.fizz.wkclimb.client.ClimbCamera;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** White Knuckle's camera: FOV, head-bob, sprint roll and shake replace Minecraft's. */
@Mixin(GameRenderer.class)
public abstract class CameraMixin {
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void wkclimb$fov(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
		if (useFovSetting) cir.setReturnValue(ClimbCamera.fov(cir.getReturnValueF()));
	}

	@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
	private void wkclimb$bobHurt(PoseStack pose, float partialTick, CallbackInfo ci) {
		ClimbCamera.frame();
		var player = Minecraft.getInstance().player;
		if (ClimbCamera.active() && player != null && !player.isDeadOrDying()) {
			ClimbCamera.apply(pose);
			ci.cancel();
		}
	}

	@Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
	private void wkclimb$bobView(PoseStack pose, float partialTick, CallbackInfo ci) {
		if (ClimbCamera.active()) ci.cancel();
	}
}
