package dev.fizz.wkclimb.client.mixin;

import dev.fizz.wkclimb.ClimbConfig;
import dev.fizz.wkclimb.client.ClimbController;
import dev.fizz.wkclimb.sim.WK;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * White Knuckle's camera sits lower on the body than Minecraft's: "Main Cam Target" is 0.45 above the
 * capsule centre (x 1.5 scale), so the eye is 1.44 blocks up (Minecraft 1.62), and crouched (ChangeHeight:
 * 0.45 + crouchHeight - standingHeight) 0.84 (Minecraft 1.27). Only the local player's eye moves (camera,
 * reach, the hand rig); the hitbox and what the server and other players see are unchanged.
 */
@Mixin(Avatar.class)
public abstract class EyeHeightMixin {
	@Inject(method = "getDefaultDimensions", at = @At("RETURN"), cancellable = true)
	private void wkclimb$eyeHeight(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
		if (!((Object) this instanceof LocalPlayer) || !ClimbController.INSTANCE.isEnabled()) return;
		if (pose == Pose.STANDING || pose == Pose.CROUCHING) {
			double eye = WK.eyeHeight(pose == Pose.CROUCHING) * ClimbConfig.blocksPerUnit;
			cir.setReturnValue(cir.getReturnValue().withEyeHeight((float) eye));
		}
	}
}
