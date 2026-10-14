package dev.fizz.wkclimb.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import dev.fizz.wkclimb.ClimbConfig;
import dev.fizz.wkclimb.WKClimb;
import dev.fizz.wkclimb.client.mixin.GameRendererInvoker;
import dev.fizz.wkclimb.sim.ClimbSim;
import dev.fizz.wkclimb.sim.WK;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * White Knuckle's first-person hands, drawn with the game's own sprites (from the extracted pack).
 *
 * Everything is projected with the camera's real FOV (White Knuckle's hands use the same FOV as the
 * world). Placement follows the player prefab: each hand rests at camera-space (-/+0.475, -0.52, 0.503)
 * (Left/Right_Hand_Target x 0.38 under the 1.25-scaled Inventory-Root, Inventory-Hand-Root at
 * y -0.52 z 0.503), is 1.28 * 0.32 * 1.25 = 0.512 units tall, and the right hand is the same sprite
 * mirrored (scale x -1). While hanging the hand sits on the hold point in the world
 * (HandAnimation: handModel.position = hold + insideUnitSphere * (1 - grip%) * 0.02).
 *
 * Each hand's red comes from its own grip (the game gives each hand its own material copy through
 * the default hand cosmetic), drawn per pixel by shaders/core/hand.fsh with SHDR_Base_Inventory's
 * stamina rules; see there.
 */
public final class ClimbHands {
	private ClimbHands() {}

	private static final String[] POSES = { "normal", "open", "grab", "crouch", "sprint" };
	private static final double REST_Y = -0.52, REST_Z = 0.503;
	private static final double HAND_SIZE_CAMERA = 1.28 * 0.32 * 1.25;
	/** ENT_Player.invCamFovScaleAdjust / invCamFovHeightAdjust (player prefab). */
	private static final double INV_CAM_FOV_SCALE_ADJUST = 0.015, INV_CAM_FOV_HEIGHT_ADJUST = -0.01;
	private static final int TEX = 128;

	private static final HandSway[] sway = { new HandSway(true), new HandSway(false) };
	private static final Random shake = new Random();
	private static long lastNanos;
	private static boolean texturesChecked, haveTextures;
	private static int checkCountdown;

	// Body motion, sampled each tick (WK units).
	private static double speed, verticalSpeed, timeSinceGrounded, timeSinceSprinting, airTime, lastVerticalSpeed;
	private static boolean wasGrounded = true;
	private static Vec3 lastPos;

	/** Per-tick motion bookkeeping, plus the jump / landing impulses (ViewSway.RecieveGlobalImpulse). */
	public static void tick(LocalPlayer player) {
		Vec3 pos = player.position();
		double k = ClimbConfig.blocksPerUnit;
		if (lastPos == null) lastPos = pos;
		Vec3 d = pos.subtract(lastPos);
		lastPos = pos;
		lastVerticalSpeed = verticalSpeed;
		speed = d.length() * 20 / k;
		verticalSpeed = d.y * 20 / k;
		boolean grounded = player.onGround();
		boolean hanging = ClimbController.INSTANCE.sim.anyHanging();

		if (grounded) timeSinceGrounded = 0;
		else timeSinceGrounded += 0.05;
		if (player.isSprinting()) {
			if (grounded && speed > 0.01) timeSinceSprinting += 0.05;
		} else {
			timeSinceSprinting = 0;
		}

		if (wasGrounded && !grounded && verticalSpeed > 0.5 && !hanging) onJump();
		if (!wasGrounded && grounded && ClimbController.INSTANCE.isEnabled()) {
			// LandingAudio(): touching down while holding on is a mantle; otherwise lastVel.y is
			// compared with softLandCutoff 2, mediumLandCutoff 13 and heavyLandCutoff 23.
			double vy = Math.min(lastVerticalSpeed, verticalSpeed);
			if (hanging && airTime > 0.2) {
				ClimbSounds.play(player, ClimbSounds.MANTLE);
			} else if (vy < -2 && (airTime > 0.2 || hanging)) {
				if (vy < -23) {
					ClimbSounds.play(player, ClimbSounds.LAND_HEAVY);
					ClimbCamera.shake(0.05);
					impulse(-0.5);
				} else if (vy < -13) {
					ClimbSounds.play(player, ClimbSounds.LAND_MEDIUM);
					ClimbCamera.shake(0.01);
					impulse(-0.3);
				} else {
					ClimbSounds.play(player, ClimbSounds.LAND_SOFT);
					impulse(-0.12);
				}
			}
		}
		airTime = grounded ? 0 : airTime + 0.05;
		wasGrounded = grounded;
	}

	/** Body speed in WK units/s (cCon.velocity.magnitude), sampled per tick. */
	public static double speed() {
		return speed;
	}

	/** Hand.ShakeHand on both hands. */
	public static void shakeHands(double amount) {
		for (HandSway s : sway) s.shake(amount);
	}

	/** Jump(): ViewSway.RecieveGlobalImpulse(up * 0.2). */
	public static void onJump() {
		impulse(0.2);
	}

	private static void impulse(double up) {
		for (HandSway s : sway) s.addImpulse(0, up, 0);
	}

	/** HandAnimation's choice of ViewSway parameters for a free hand. */
	private static HandSway.Params params(LocalPlayer player, boolean buttonHeld) {
		if (timeSinceGrounded >= WK.COYOTE_TIME && verticalSpeed < 0) {
			return player.fallDistance / ClimbConfig.blocksPerUnit > 15 ? HandSway.LONG_FALL : HandSway.AIRBORNE;
		}
		if (player.isSprinting() && timeSinceSprinting > 0.1 && timeSinceGrounded < 0.1 && !buttonHeld) return HandSway.SPRINT;
		return HandSway.BASE;
	}

	static ResourceLocation tex(String name) {
		return WKClimb.id("textures/gui/hands/" + name + ".png");
	}

	/** True when the extracted sprites are loaded, so these hands replace the vanilla arm. */
	public static boolean isActive() {
		return haveTextures();
	}

	static boolean haveTextures() {
		if (!texturesChecked || --checkCountdown <= 0) {
			haveTextures = Minecraft.getInstance().getResourceManager().getResource(tex("grab")).isPresent();
			texturesChecked = true;
			checkCountdown = 200;
		}
		return haveTextures;
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		ClimbController ctl = ClimbController.INSTANCE;
		LocalPlayer player = mc.player;
		if (!ctl.isEnabled() || player == null || mc.options.hideGui || !mc.options.getCameraType().isFirstPerson()) return;
		if (!haveTextures()) return;

		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 0.016f : Math.min((now - lastNanos) / 1e9f, 0.1f);
		lastNanos = now;

		int w = g.guiWidth(), h = g.guiHeight();
		float partial = delta.getGameTimeDeltaPartialTick(false);
		Camera camera = mc.gameRenderer.getMainCamera();
		double fov = ((GameRendererInvoker) mc.gameRenderer).wkclimb$getFov(camera, partial, true);
		double tanFov = Math.tan(Math.toRadians(fov / 2));
		double aspect = (double) w / h;
		// SetCameraFov: the hand rig scales and drops as the FOV widens past 90 (sprinting)
		double rigScale = 1 + (fov - 90) * INV_CAM_FOV_SCALE_ADJUST;
		double rigLift = (fov - 90) * INV_CAM_FOV_HEIGHT_ADJUST;
		double rigToBlocks = WK.PLAYER_SCALE * ClimbConfig.blocksPerUnit;
		Vec3[] basis = cameraBasis(camera);

		double time = (System.nanoTime() / 1e9) % 10000;
		for (int i = 0; i < 2; i++) {
			ClimbSim.Hand hand = ctl.sim.hands[i];
			double amount = 1 - hand.gripPercent();
			boolean left = i == ClimbSim.LEFT;
			double side = left ? -1 : 1;
			boolean held = buttonHeld(mc, i);
			HandSway.Params params = params(player, held);
			if (!hand.isHanging()) sway[i].shake(amount * dt * 0.04);
			else if (hand.isShaking()) sway[i].shake(hand.handhold().icyShakeAmount() * dt);
			sway[i].update(params, speed, time, dt);

			if (hand.isHanging()) {
				// HandAnimation puts the hand model on the hold; the ViewSway keeps lerping from there,
				// so on release the hand eases back to rest through camera space.
				Vec3 hold = ClimbController.toMcPublic(hand.holdPoint());
				double jitter = (amount + sway[i].shakeAmount() * 10) * 0.02 * ClimbConfig.blocksPerUnit;
				hold = hold.add(gauss() * jitter, gauss() * jitter, gauss() * jitter);
				Vec3 v = hold.subtract(camera.position());
				double ux = v.dot(basis[0]) / rigToBlocks, uy = v.dot(basis[1]) / rigToBlocks, uz = v.dot(basis[2]) / rigToBlocks;
				sway[i].x = ((ux - 0) / rigScale) / 1.25 - side * 0.38;
				sway[i].y = ((uy - REST_Y) / rigScale - rigLift) / 1.25;
				sway[i].z = ((uz - REST_Z) / rigScale) / 1.25;
				sway[i].rot = 0;
			}
			// Hand model position in the camera rig: Inventory-Hand-Root (0, -0.52, 0.503), scaled by the
			// FOV adjustment, containing Inventory-Root (lifted by the FOV adjustment, scale 1.25),
			// containing Left/Right_Hand_Target (x -/+0.38), containing the hand model (the sway).
			double cx = rigScale * (1.25 * (side * 0.38 + sway[i].x));
			double cy = REST_Y + rigScale * (rigLift + 1.25 * sway[i].y);
			double cz = REST_Z + rigScale * (1.25 * sway[i].z);
			if (cz < 0.05) continue;
			float x = (float) (w / 2.0 * (1 + cx / (cz * tanFov * aspect)));
			float y = (float) (h / 2.0 * (1 - cy / (cz * tanFov)));
			float size = (float) (rigScale * HAND_SIZE_CAMERA / (cz * tanFov) * h / 2);
			float rot = (float) sway[i].rot;

			String pose = pose(player, hand, held);
			int px = Math.round(size);
			var pose2d = g.pose();
			pose2d.pushMatrix();
			pose2d.translate(x, y);
			// Unity z rotation is counter-clockwise on screen; GUI y points down.
			pose2d.rotate((float) -Math.toRadians(rot));
			int o = -px / 2;
			blit(g, tex(pose), o, o, px, !left, (float) amount);
			pose2d.popMatrix();
		}
	}

	private static boolean buttonHeld(Minecraft mc, int i) {
		return mc.screen == null && (i == 0 ? mc.options.keyAttack.isDown() : mc.options.keyUse.isDown());
	}

	/** HandAnimation's pose choice. */
	private static String pose(LocalPlayer player, ClimbSim.Hand hand, boolean held) {
		if (hand.isHanging()) return "grab";
		if (held) return "open";
		if (player.isCrouching()) return "crouch";
		if (player.isSprinting() && timeSinceSprinting > 0.3 && speed > 0.3 && timeSinceGrounded < WK.COYOTE_TIME) return "sprint";
		return POSES[0];
	}

	/**
	 * The hands are drawn by shaders/core/hand.fsh: the game's stamina red, its material tint and the
	 * damage look (the game's hands are in the world, under its grade and hurt vignette).
	 */
	private static final RenderPipeline HAND = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
			.withLocation(WKClimb.id("pipeline/hand"))
			.withFragmentShader(WKClimb.id("core/hand"))
			.withUniform("Globals", UniformType.UNIFORM_BUFFER)
			.build());

	/** staminaUsed: 1 - grip%, the game's _StaminaAmount for this hand. */
	private static void blit(GuiGraphics g, ResourceLocation texture, int x, int y, int size, boolean mirror, float staminaUsed) {
		if (size <= 0) return;
		int color = ClimbDamage.handColor(staminaUsed);
		if (mirror) {
			g.blit(HAND, texture, x, y, TEX, 0, size, size, -TEX, TEX, TEX, TEX, color);
		} else {
			g.blit(HAND, texture, x, y, 0, 0, size, size, TEX, TEX, TEX, TEX, color);
		}
	}

	/** Camera right, up and forward vectors in world space. */
	static Vec3[] cameraBasis(Camera camera) {
		Vec3 forward = Vec3.directionFromRotation(camera.getXRot(), camera.getYRot());
		double yaw = Math.toRadians(camera.getYRot());
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		return new Vec3[] { right, right.cross(forward), forward };
	}

	/** World point to GUI coordinates {x, y, depth}, or null if behind the camera. */
	static float[] project(Camera camera, Vec3 point, double tanHalfFov, double aspect, int w, int h) {
		Vec3 v = point.subtract(camera.position());
		Vec3 forward = Vec3.directionFromRotation(camera.getXRot(), camera.getYRot());
		double yaw = Math.toRadians(camera.getYRot());
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		Vec3 up = right.cross(forward);
		double z = v.dot(forward);
		if (z < 0.05) return null;
		double ndcX = v.dot(right) / (z * tanHalfFov * aspect);
		double ndcY = v.dot(up) / (z * tanHalfFov);
		return new float[] { (float) (w / 2.0 * (1 + ndcX)), (float) (h / 2.0 * (1 - ndcY)), (float) z };
	}

	private static double gauss() {
		return Mth.clamp(shake.nextGaussian() * 0.5, -1, 1);
	}
}
