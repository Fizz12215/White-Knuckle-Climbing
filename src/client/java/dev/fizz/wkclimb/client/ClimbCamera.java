package dev.fizz.wkclimb.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.fizz.wkclimb.ClimbConfig;
import dev.fizz.wkclimb.sim.ClimbSim;
import dev.fizz.wkclimb.sim.WK;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.Random;

/**
 * White Knuckle's first-person camera, replacing Minecraft's view bobbing, sprint FOV and hurt tilt:
 *  - ENT_Player.MouseLook head-bob: on the ground the camera traces
 *    (sin(6b) * 0.5, cos(12b), 0) * 0.03 * min(speed, 1), with b advancing at clamp(speed / 2, 0, 1);
 *  - Movement's camSway roll while sprinting on the ground: sin(6t) * 0.3 * 2 degrees, easing back at 2/s;
 *  - FixedUpdate's FOV: playerFOV (White Knuckle's default 90, config "fov"), +15 while sprinting on the
 *    ground, smoothed at 5 per step-second;
 *  - CL_CameraControl shake: every 0.025 s a random offset (insideUnitSphere * shake, max 0.5) and tilt
 *    (insideUnitSphere * shake * 30 degrees, max 20.5), with shake decaying at 6/s.
 * Offsets are in WK camera units (scaled by the 1.5 player scale, then to blocks).
 */
public final class ClimbCamera {
	private ClimbCamera() {}

	private static final Random random = new Random();
	private static long lastNanos;
	private static double bobTime, bobX, bobY;
	private static double camSway;
	private static double smoothedFov = -1;
	private static double shake, shakeTime = 0.01;
	private static double shakeX, shakeY, shakeZ, shakePitch, shakeYaw, shakeRoll;

	public static boolean active() {
		Minecraft mc = Minecraft.getInstance();
		return ClimbController.INSTANCE.isEnabled() && mc.player != null && mc.options.getCameraType().isFirstPerson()
				&& !mc.player.isSpectator() && !mc.player.getAbilities().flying;
	}

	/** CL_CameraControl.Shake. */
	public static void shake(double amount) {
		shake += amount;
	}

	/** Advance the camera state; called once per rendered frame. */
	public static void frame() {
		long now = System.nanoTime();
		double dt = lastNanos == 0 ? 0.016 : Math.min((now - lastNanos) / 1e9, 0.1);
		lastNanos = now;
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.isPaused()) return;
		ClimbSim sim = ClimbController.INSTANCE.sim;
		double speed = ClimbHands.speed();
		double time = (now / 1e9) % 10000;

		// Head-bob (MouseLook)
		if (sim.timeSinceGrounded() < 0.2 && !sim.anyHanging()) {
			bobTime += dt * Math.max(0, Math.min(1, speed * 0.5));
			double amp = 0.03 * Math.min(speed, 1);
			bobX = Math.sin(bobTime * 6) * 0.5 * amp;
			bobY = Math.cos(bobTime * 12) * amp;
		} else {
			double k = Math.min(1, dt * 4);
			bobX += (0 - bobX) * k;
			bobY += (0 - bobY) * k;
		}

		// Sprint roll (camSway)
		camSway += (0 - camSway) * Math.min(1, dt * 2);
		if (sim.isSprintingOnGround() && speed > 0.1) {
			camSway = Math.sin(time * 6) * 0.3 * 2;
		}

		// FOV
		double base = ClimbConfig.fov > 0 ? ClimbConfig.fov : mc.options.fov().get();
		double target = sim.isSprintingOnGround() ? base + 15 : base;
		if (smoothedFov < 0) smoothedFov = base;
		smoothedFov += (target - smoothedFov) * (1 - Math.exp(-dt * 5.27));

		// Fatigue and icy-hold shake (HandAnimation, CL_Handhold_Icy)
		double fatigue = 0;
		for (ClimbSim.Hand h : sim.hands) {
			if (!h.isHanging()) continue;
			double num = 1 - h.gripPercent();
			if (num > 0.92) fatigue = Math.max(fatigue, 0.0005 * num);
			if (h.isShaking()) shake(0.0005);
		}
		shake(fatigue);

		// CL_CameraControl.LateUpdate
		shake += (0 - shake) * Math.min(1, dt * 6);
		shakeTime -= dt * 0.4;
		if (shakeTime <= 0 && shake > 0.001) {
			shakeTime = 0.01;
			double[] p = insideUnitSphere(shake, 0.5);
			shakeX = p[0];
			shakeY = p[1];
			shakeZ = p[2];
			double[] r = insideUnitSphere(shake * 30, 20.5);
			shakePitch = r[0];
			shakeYaw = r[1];
			shakeRoll = r[2];
		} else {
			shakePitch = shakeYaw = shakeRoll = 0;
			if (shake <= 0.001) shakeX = shakeY = shakeZ = 0;
		}
	}

	/** The current smoothed White Knuckle FOV, or -1 before the first frame. */
	public static double currentFov() {
		return smoothedFov;
	}

	/** The FOV Minecraft should render with, or the vanilla value when inactive. */
	public static float fov(float vanilla) {
		if (!active() || smoothedFov < 0) return vanilla;
		return (float) smoothedFov;
	}

	/** View-space transform in place of bobView (and of bobHurt's tilt while alive). */
	public static void apply(PoseStack pose) {
		double toBlocks = WK.PLAYER_SCALE * ClimbConfig.blocksPerUnit;
		// Moving the camera by +offset moves the view by -offset.
		pose.translate(-(bobX + shakeX) * toBlocks, -(bobY + shakeY) * toBlocks, -shakeZ * toBlocks);
		pose.mulPose(Axis.ZP.rotationDegrees((float) -(camSway + shakeRoll)));
		pose.mulPose(Axis.XP.rotationDegrees((float) shakePitch));
		pose.mulPose(Axis.YP.rotationDegrees((float) shakeYaw));
	}

	private static double[] insideUnitSphere(double scale, double maxLen) {
		double a, b, c;
		do {
			a = random.nextDouble() * 2 - 1;
			b = random.nextDouble() * 2 - 1;
			c = random.nextDouble() * 2 - 1;
		} while (a * a + b * b + c * c > 1);
		double len = Math.sqrt(a * a + b * b + c * c) * scale;
		double m = len > maxLen ? maxLen / len : 1;
		return new double[] { a * scale * m, b * scale * m, c * scale * m };
	}
}
