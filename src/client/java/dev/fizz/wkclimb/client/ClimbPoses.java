package dev.fizz.wkclimb.client;

import dev.fizz.wkclimb.ArmAim;
import dev.fizz.wkclimb.net.ClimbPosePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where each player's hands hold on, so the player model can show the arms reaching up to the holds:
 * other players' from the server (ClimbPosePayload), the local player's straight from ClimbController
 * (seen in third person).
 */
public final class ClimbPoses {
	private ClimbPoses() {}

	private static final Map<Integer, ClimbPosePayload> REMOTE = new ConcurrentHashMap<>();

	/** Player-model arm pivots: 5 pixels either side of the middle, 22 pixels above the feet. */
	private static final double SHOULDER_SIDE = 5 / 16.0, SHOULDER_UP = 22 / 16.0;

	public static void receive(ClimbPosePayload pose) {
		if (pose.leftHolding() || pose.rightHolding()) REMOTE.put(pose.entityId(), pose);
		else REMOTE.remove(pose.entityId());
	}

	public static void clear() {
		REMOTE.clear();
	}

	/** {left, right} hold spots for this entity (null = not holding), or null when neither hand holds. */
	private static Vec3[] holds(int entityId) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && mc.player.getId() == entityId) {
			Vec3 left = ClimbController.INSTANCE.holdSpot(0), right = ClimbController.INSTANCE.holdSpot(1);
			return left == null && right == null ? null : new Vec3[] { left, right };
		}
		ClimbPosePayload pose = REMOTE.get(entityId);
		if (pose == null) return null;
		return new Vec3[] { pose.leftHolding() ? pose.leftHold() : null, pose.rightHolding() ? pose.rightHold() : null };
	}

	/** Turns the model's arms toward the holds (after Minecraft has posed them). */
	public static void poseArms(PlayerModel model, AvatarRenderState state) {
		Vec3[] holds = holds(state.id);
		if (holds == null) return;
		double yaw = Math.toRadians(state.bodyRot);
		double fx = -Math.sin(yaw), fz = Math.cos(yaw); // forward
		double rx = -Math.cos(yaw), rz = -Math.sin(yaw); // the player's right
		aim(model.leftArm, holds[0], -1, state, fx, fz, rx, rz);
		aim(model.rightArm, holds[1], 1, state, fx, fz, rx, rz);
	}

	/** side: +1 for the right shoulder, -1 for the left. */
	private static void aim(ModelPart arm, Vec3 hold, int side, AvatarRenderState state, double fx, double fz, double rx, double rz) {
		if (hold == null) return;
		double s = state.scale;
		double sx = state.x + rx * SHOULDER_SIDE * side * s;
		double sy = state.y + SHOULDER_UP * s;
		double sz = state.z + rz * SHOULDER_SIDE * side * s;
		double dx = hold.x - sx, dy = hold.y - sy, dz = hold.z - sz;
		float[] a = ArmAim.angles(dx * rx + dz * rz, dy, dx * fx + dz * fz);
		if (a == null) return;
		arm.xRot = a[0];
		arm.yRot = a[1];
		arm.zRot = 0;
	}
}
