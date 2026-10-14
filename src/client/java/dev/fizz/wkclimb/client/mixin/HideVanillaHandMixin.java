package dev.fizz.wkclimb.client.mixin;

import dev.fizz.wkclimb.ClimbConfig;
import dev.fizz.wkclimb.client.ClimbController;
import dev.fizz.wkclimb.client.ClimbHands;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** White Knuckle's hands replace Minecraft's arm and held item while climbing hands are on. */
@Mixin(GameRenderer.class)
public abstract class HideVanillaHandMixin {
	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void wkclimb$hideHand(float partialTick, boolean sleeping, Matrix4f projection, CallbackInfo ci) {
		if (ClimbConfig.hideVanillaHand && ClimbController.INSTANCE.isEnabled() && ClimbHands.isActive()) ci.cancel();
	}
}
