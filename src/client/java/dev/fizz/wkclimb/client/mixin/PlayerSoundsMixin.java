package dev.fizz.wkclimb.client.mixin;

import dev.fizz.wkclimb.client.ClimbController;
import dev.fizz.wkclimb.client.ClimbSounds;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The White Knuckle climber's hurt / fall / burn / death voices replace Steve's for the local player
 * (only you hear them; other players hear vanilla).
 */
@Mixin(Player.class)
public abstract class PlayerSoundsMixin {
	@Inject(method = "getHurtSound", at = @At("HEAD"), cancellable = true)
	private void wkclimb$hurtSound(DamageSource source, CallbackInfoReturnable<SoundEvent> cir) {
		if (!((Object) this instanceof LocalPlayer) || !ClimbController.INSTANCE.isEnabled()) return;
		ClimbSounds.Sound s = source.is(DamageTypeTags.IS_FALL) ? ClimbSounds.HURT_FALLING
				: source.is(DamageTypeTags.IS_FIRE) ? ClimbSounds.HURT_HEAT : ClimbSounds.HURT;
		if (ClimbSounds.available(s)) cir.setReturnValue(s.event());
	}

	@Inject(method = "getDeathSound", at = @At("HEAD"), cancellable = true)
	private void wkclimb$deathSound(CallbackInfoReturnable<SoundEvent> cir) {
		if ((Object) this instanceof LocalPlayer && ClimbController.INSTANCE.isEnabled() && ClimbSounds.available(ClimbSounds.DEATH)) {
			cir.setReturnValue(ClimbSounds.DEATH.event());
		}
	}

}
