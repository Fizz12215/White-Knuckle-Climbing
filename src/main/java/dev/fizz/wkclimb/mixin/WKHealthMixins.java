package dev.fizz.wkclimb.mixin;

import dev.fizz.wkclimb.WKClimb;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * White Knuckle's fall damage and regen (WKHealth) replace Minecraft's for players in White Knuckle
 * mode: no vanilla fall damage, and no Peaceful-difficulty regeneration (tickRegeneration).
 */
@Mixin(Player.class)
public abstract class WKHealthMixins {
	@Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
	private void wkclimb$noVanillaFallDamage(double distance, float multiplier, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
		if (WKClimb.isWkMode((Player) (Object) this)) cir.setReturnValue(false);
	}
}
