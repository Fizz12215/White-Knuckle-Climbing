package dev.fizz.wkclimb.client.mixin;

import dev.fizz.wkclimb.client.ClimbController;
import dev.fizz.wkclimb.client.ClimbSounds;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** White Knuckle's footsteps (played by the simulation's FootstepAudio) replace Minecraft's for the local player. */
@Mixin(Entity.class)
public abstract class StepSoundMixin {
	@Inject(method = "playStepSound", at = @At("HEAD"), cancellable = true)
	private void wkclimb$stepSound(BlockPos pos, BlockState state, CallbackInfo ci) {
		if ((Object) this instanceof LocalPlayer && ClimbController.INSTANCE.isDriving() && ClimbSounds.haveFootsteps()) ci.cancel();
	}
}
