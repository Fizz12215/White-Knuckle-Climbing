package dev.fizz.wkclimb.mixin;

import dev.fizz.wkclimb.WKClimb;
import dev.fizz.wkclimb.WKHealth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Damage that lands restarts White Knuckle's 5 s heal delay, and Minecraft's Peaceful-difficulty
 * regeneration is off in White Knuckle mode (WKHealth does the healing).
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerHurtMixin {
	@Inject(method = "hurtServer", at = @At("RETURN"))
	private void wkclimb$hurt(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		if (cir.getReturnValueZ() && WKClimb.isWkMode(self)) WKHealth.onDamaged(self);
	}

	@Inject(method = "tickRegeneration", at = @At("HEAD"), cancellable = true)
	private void wkclimb$noPeacefulRegen(CallbackInfo ci) {
		if (WKClimb.isWkMode((ServerPlayer) (Object) this)) ci.cancel();
	}
}
