package dev.fizz.wkclimb.mixin;

import dev.fizz.wkclimb.WKClimb;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No hunger, starvation or food-based regen in White Knuckle mode (WKHealth does the healing). */
@Mixin(FoodData.class)
public abstract class FoodDataMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void wkclimb$noHunger(ServerPlayer player, CallbackInfo ci) {
		if (WKClimb.isWkMode(player)) ci.cancel();
	}
}
