package dev.fizz.wkclimb.client.mixin;

import dev.fizz.wkclimb.client.ClimbController;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** With climbing hands on, the mouse buttons are hands: no attacking, mining or using items. */
@Mixin(Minecraft.class)
public abstract class MinecraftInputMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void wkclimb$startAttack(CallbackInfoReturnable<Boolean> cir) {
		if (ClimbController.INSTANCE.isEnabled()) cir.setReturnValue(false);
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void wkclimb$continueAttack(boolean leftClick, CallbackInfo ci) {
		if (ClimbController.INSTANCE.isEnabled()) ci.cancel();
	}

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void wkclimb$startUseItem(CallbackInfo ci) {
		if (ClimbController.INSTANCE.isEnabled()) ci.cancel();
	}
}
