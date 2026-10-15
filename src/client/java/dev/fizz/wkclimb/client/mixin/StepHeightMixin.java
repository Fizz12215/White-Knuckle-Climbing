package dev.fizz.wkclimb.client.mixin;

import dev.fizz.wkclimb.ClimbConfig;
import dev.fizz.wkclimb.client.ClimbController;
import dev.fizz.wkclimb.sim.WK;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Config "exact_step_height": White Knuckle's CharacterController steps up only 0.15 x 1.5 = 0.225 units
 * (0.18 blocks), so slabs and stairs must be jumped onto. Off by default (Minecraft's 0.6).
 * Client only: the server's taller step is more permissive, so it can always replay the client's moves.
 */
@Mixin(LivingEntity.class)
public abstract class StepHeightMixin {
	@Inject(method = "maxUpStep", at = @At("HEAD"), cancellable = true)
	private void wkclimb$stepHeight(CallbackInfoReturnable<Float> cir) {
		if (ClimbConfig.exactStepHeight && (Object) this instanceof LocalPlayer && ClimbController.INSTANCE.isEnabled()) {
			cir.setReturnValue((float) (WK.STEP_OFFSET * WK.PLAYER_SCALE * ClimbConfig.blocksPerUnit));
		}
	}
}
