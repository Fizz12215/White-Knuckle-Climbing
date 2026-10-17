package dev.fizz.wkclimb.client.mixin;

import dev.fizz.wkclimb.ClimbConfig;
import dev.fizz.wkclimb.client.ClimbController;
import dev.fizz.wkclimb.client.ClimbHands;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** White Knuckle's hands replace Minecraft's arm and held item while climbing hands are on (unless hide_wk_hands). */
@Mixin(GameRenderer.class)
public abstract class HideVanillaHandMixin {
	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void wkclimb$hideHand(CallbackInfo ci) {
		if (!ClimbConfig.hideWkHands && ClimbController.INSTANCE.isEnabled() && ClimbHands.isActive()) ci.cancel();
	}
}
