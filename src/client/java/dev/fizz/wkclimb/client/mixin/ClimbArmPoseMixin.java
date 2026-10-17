package dev.fizz.wkclimb.client.mixin;

import dev.fizz.wkclimb.client.ClimbPoses;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A climbing player's arms reach up to their holds, as other players (and you in third person) see them. */
@Mixin(PlayerModel.class)
public abstract class ClimbArmPoseMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void wkclimb$reachForHolds(AvatarRenderState state, CallbackInfo ci) {
		ClimbPoses.poseArms((PlayerModel) (Object) this, state);
	}
}
