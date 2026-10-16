package dev.fizz.wkclimb.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.fizz.wkclimb.client.ClimbCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** White Knuckle's camera: head-bob, sprint roll and shake replace Minecraft's (the FOV: CameraFovMixin). */
@Mixin(GameRenderer.class)
public abstract class CameraMixin {
	@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
	private void wkclimb$bobHurt(CameraRenderState camera, PoseStack pose, CallbackInfo ci) {
		ClimbCamera.frame();
		var player = Minecraft.getInstance().player;
		if (ClimbCamera.active() && player != null && !player.isDeadOrDying()) {
			ClimbCamera.apply(pose);
			ci.cancel();
		}
	}

	@Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
	private void wkclimb$bobView(CameraRenderState camera, PoseStack pose, CallbackInfo ci) {
		if (ClimbCamera.active()) ci.cancel();
	}
}
