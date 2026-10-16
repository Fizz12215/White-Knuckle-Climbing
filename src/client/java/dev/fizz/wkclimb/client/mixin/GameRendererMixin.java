package dev.fizz.wkclimb.client.mixin;

import dev.fizz.wkclimb.client.ClimbDamage;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * update() rebuilds the frame's list of post effects (end of frame, the player's, the spectated mob's);
 * the damage look joins it while it is on screen.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Shadow @Final private List<Identifier> requestedPostEffects;

	@Inject(method = "update", at = @At("TAIL"))
	private void wkclimb$requestDamageLook(DeltaTracker delta, CallbackInfo ci) {
		if (ClimbDamage.gradeRequested()) requestedPostEffects.add(ClimbDamage.GRADE);
	}
}
