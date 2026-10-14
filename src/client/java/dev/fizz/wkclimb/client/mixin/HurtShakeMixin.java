package dev.fizz.wkclimb.client.mixin;

import dev.fizz.wkclimb.client.ClimbCamera;
import dev.fizz.wkclimb.client.ClimbController;
import dev.fizz.wkclimb.client.ClimbHands;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ENT_Player.Damage: getting hurt shakes the camera (Shake 0.1) and the hands (ShakeHand 0.05). */
@Mixin(LivingEntity.class)
public abstract class HurtShakeMixin {
	@Inject(method = "handleDamageEvent", at = @At("TAIL"))
	private void wkclimb$hurtShake(DamageSource source, CallbackInfo ci) {
		if ((Object) this instanceof LocalPlayer && ClimbController.INSTANCE.isEnabled()) {
			ClimbCamera.shake(0.1);
			ClimbHands.shakeHands(0.05);
			dev.fizz.wkclimb.client.ClimbDamage.onHurt((LocalPlayer) (Object) this, source);
		}
	}

	/** Entity event 3 is death: six blood splats (ENT_Player.Kill). */
	@Inject(method = "handleEntityEvent", at = @At("HEAD"))
	private void wkclimb$death(byte event, CallbackInfo ci) {
		if (event == 3 && (Object) this instanceof LocalPlayer && ClimbController.INSTANCE.isEnabled()) {
			dev.fizz.wkclimb.client.ClimbDamage.onDeath();
		}
	}
}
