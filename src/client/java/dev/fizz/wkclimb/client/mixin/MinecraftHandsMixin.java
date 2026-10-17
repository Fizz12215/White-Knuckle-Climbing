package dev.fizz.wkclimb.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.fizz.wkclimb.ClimbConfig;
import dev.fizz.wkclimb.client.ClimbController;
import dev.fizz.wkclimb.client.ClimbHands;
import dev.fizz.wkclimb.sim.WK;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft hands: two arms in the player's skin, each with its hand where White Knuckle's hand would be
 * (ClimbHands.frames: rest, sway, reaching, holding on), the arm coming up from below the screen.
 * Held items aren't drawn, as with White Knuckle's hands.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class MinecraftHandsMixin {
	/** Where each arm comes from, in view space (blocks; x right, y up, -z ahead), for the right arm. */
	private static final float ANCHOR_X = 0.72f, ANCHOR_Y = -1.35f, ANCHOR_Z = 0.05f;
	/** From the shoulder pivot to the hand spot, in model pixels: the fist reaches a little past it, so it shows above the bottom of the screen. */
	private static final float FIST = 4;
	/** Arm size against Minecraft's own (times the hand rig's scale). */
	private static final float ARM_SCALE = 1.0f;
	/** Each pose's turn of the arm about its length (degrees, for the right arm). */
	private static final float OPEN_TWIST = -25, GRAB_TWIST = 20;

	@Shadow
	private void renderPlayerHand(PoseStack poseStack, SubmitNodeCollector collector, int light, HumanoidArm arm, PlayerRenderState state) {}

	@Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void wkclimb$minecraftHands(float partialTick, PoseStack poseStack, SubmitNodeCollector collector,
			PlayerRenderState state, FirstPersonHandsAndItemsRenderState handsState, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (!ClimbController.INSTANCE.isEnabled() || !ClimbHands.useMinecraftHands()) return;
		ci.cancel();
		if (mc.player == null || mc.player.isInvisible()) return;

		// The hands are drawn with their own projection (the HUD FOV); scale across the screen so each
		// hand lands where White Knuckle's rig puts it under the world's FOV.
		float hudFov = ((CameraInvoker) mc.gameRenderer.mainCamera()).wkclimb$calculateHudFov(partialTick);
		double k = WK.PLAYER_SCALE * ClimbConfig.blocksPerUnit;
		int light = state.avatarRenderState.lightCoords;
		for (int i = 0; i < 2; i++) {
			ClimbHands.Frame f = ClimbHands.frames[i];
			if (!f.visible) continue;
			boolean right = i == 1;
			float side = right ? 1 : -1;
			float r = (float) (Math.tan(Math.toRadians(hudFov / 2)) / Math.tan(Math.toRadians(f.fov / 2)));
			Vector3f hand = new Vector3f((float) (f.x * k), (float) (f.y * k), (float) (-f.z * k));
			Vector3f dir = new Vector3f(hand).sub(side * ANCHOR_X, ANCHOR_Y, ANCHOR_Z).normalize();
			// The arm's front faces back toward the camera, rolled with the hand and turned by the pose.
			Vector3f toCamera = new Vector3f(0, 0, 1);
			Vector3f front = toCamera.sub(new Vector3f(dir).mul(toCamera.dot(dir))).normalize();
			float twist = switch (f.pose) {
				case "open" -> OPEN_TWIST;
				case "grab" -> GRAB_TWIST;
				default -> 0;
			};
			front.rotateAxis((float) Math.toRadians(f.rot + side * twist), dir.x, dir.y, dir.z);
			// Model space: +y runs down the arm to the hand, -z is the arm's front.
			Vector3f my = dir, mz = new Vector3f(front).negate(), mx = new Vector3f(my).cross(mz);
			Matrix4f turn = new Matrix4f(
					mx.x, mx.y, mx.z, 0,
					my.x, my.y, my.z, 0,
					mz.x, mz.y, mz.z, 0,
					0, 0, 0, 1);
			float scale = (float) (ARM_SCALE * f.scale);
			Vector3f shoulder = new Vector3f(hand).sub(new Vector3f(dir).mul(FIST / 16 * scale));

			poseStack.pushPose();
			poseStack.scale(r, r, 1);
			poseStack.translate(shoulder.x, shoulder.y, shoulder.z);
			poseStack.mulPose(turn);
			poseStack.scale(scale, scale, scale);
			// the arm part moves itself to its shoulder pivot (x -/+5, y 2 pixels): undo that
			poseStack.translate(side * 5 / 16f, -2 / 16f, 0);
			renderPlayerHand(poseStack, collector, light, right ? HumanoidArm.RIGHT : HumanoidArm.LEFT, state);
			poseStack.popPose();
		}
	}
}
