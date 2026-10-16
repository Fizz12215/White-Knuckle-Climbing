package dev.fizz.wkclimb.client;

import dev.fizz.wkclimb.WKClimb;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * White Knuckle's damage feedback, drawn over the view while the hands are on:
 *  - screen blood (ENT_Player.bloodEffect, FX_Player_BloodSplatter_Screen): 2 splats per hit, 6 on death;
 *    each a random tile of a 4x4 splatter sheet, tinted (0.53, 0.04, 0.08), 2-3 units wide, centred
 *    inside a 1.5-unit circle 0.548 in front of the camera (0.138 down), drifting down at up to
 *    0.03 units/s, living 1-2 s with alpha 0 -> 1 (1.5%) -> 1 (57%) -> 0, capped at 2 screen heights;
 *  - the "hurt" vignette (CL_UIManager): colour (0.60, 0.03, 0.03, 0.82) x amount, set to max(0.6, current)
 *    on a hit and easing toward 1 - health/max at fadeSpeed 0.8. Drawn by the post pass below, since the
 *    game blends it in linear light;
 *  - the four damage-direction indicators: red edge glows whose alpha is the hit direction in view
 *    space (left, top, right, bottom), fading at 1.25 per second;
 *  - damageFX, weighted by damageFXCurve(health/max) from about two-thirds health down: saturation
 *    x (1 -> 0.1) keeping brightness, and the picture brightened (damageFX.gamma). The grade and the
 *    vignette are one post pass (shaders/post/damage.fsh, maths in include/wk_damage.glsl), checked
 *    against screenshots of the game; the hands get the same per pixel (ClimbHands, core/hand.fsh).
 */
public final class ClimbDamage {
	private ClimbDamage() {}

	private static final ResourceLocation BLOOD = WKClimb.id("textures/gui/damage/blood_splatter.png");
	private static final ResourceLocation SIDE = WKClimb.id("textures/gui/damage/direction_side.png");
	private static final ResourceLocation TOP = WKClimb.id("textures/gui/damage/direction_top.png");
	private static final int BLOOD_TEX = 1024, BLOOD_TILE = 256;
	/**
	 * The game draws the screen blood with its Overlay Camera, whose field of view stays at 90 (only the
	 * main and hand cameras follow the FOV setting and the sprint widening), so the splats don't move or
	 * shrink when the FOV changes.
	 */
	private static final double OVERLAY_CAMERA_FOV = 90;
	/** The blood, drawn with the hurt vignette over it (shaders/core/blood.fsh). */
	private static final com.mojang.blaze3d.pipeline.RenderPipeline BLOOD_PIPELINE = RenderPipelines.register(
			com.mojang.blaze3d.pipeline.RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
					.withLocation(WKClimb.id("pipeline/blood"))
					.withFragmentShader(WKClimb.id("core/blood"))
					.withUniform("Globals", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
					.build());

	private record Splat(double x, double y, double size, int tile, double lifetime, double drift, double born) {}

	private static final List<Splat> splats = new ArrayList<>();
	private static final Random random = new Random();
	private static double hurtAmount, hurtTarget;
	private static final double[] direction = new double[4]; // left, top, right, bottom alpha
	private static long lastNanos;
	private static boolean available;
	private static int recheck;

	static boolean available() {
		if (--recheck <= 0) {
			available = Minecraft.getInstance().getResourceManager().getResource(BLOOD).isPresent();
			recheck = 200;
		}
		return available;
	}

	/** After a resource reload: look for the blood sprite again on the next call. */
	static void forgetAvailable() {
		recheck = 0;
	}

	public static int splatCount() { return splats.size(); }

	/** Test support: clear the vignette, indicators and blood. */
	public static void resetForTest() {
		hurtAmount = 0;
		hurtTarget = 0;
		java.util.Arrays.fill(direction, 0);
		splats.clear();
	}
	public static double hurtAmount() { return hurtAmount; }
	/** Test support: skip the vignette's easing and drop the blood and indicators, for a still frame. */
	public static void settleForTest() {
		hurtAmount = hurtTarget;
		java.util.Arrays.fill(direction, 0);
		splats.clear();
	}
	/** Test support: the look applied this frame (saturation, brightness, vignette). */
	public static float[] lookForTest() { return new float[] { handSaturation, handBright, handVignette }; }
	public static double directionAlpha(int i) { return direction[i]; }

	/** ENT_Player.Damage: hurt vignette, direction indicators and two blood splats. */
	public static void onHurt(LocalPlayer player, DamageSource source) {
		hurtAmount = Math.max(0.6, hurtAmount);
		Vec3 pos = source.getSourcePosition();
		if (pos != null) {
			Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
			Vec3[] basis = ClimbHands.cameraBasis(camera);
			Vec3 to = pos.subtract(camera.position()).normalize();
			Vec3 flat = to.subtract(basis[2].scale(to.dot(basis[2])));
			double x = flat.dot(basis[0]), y = flat.dot(basis[1]);
			direction[0] = clamp01(-x);
			direction[1] = clamp01(y);
			direction[2] = clamp01(x);
			direction[3] = clamp01(-y);
		}
		splatter(2);
	}

	public static void splatterPublic(int count) {
		splatter(count);
	}

	/** Kill(): six splats. */
	public static void onDeath() {
		splatter(6);
	}

	private static void splatter(int count) {
		double now = System.nanoTime() / 1e9;
		for (int i = 0; i < count; i++) {
			double r = 1.5 * Math.sqrt(random.nextDouble()), a = random.nextDouble() * Math.PI * 2;
			splats.add(new Splat(Math.cos(a) * r, Math.sin(a) * r, 2 + random.nextDouble(), random.nextInt(16),
					1 + random.nextDouble(), random.nextDouble() * 0.03, now));
		}
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (!ClimbController.INSTANCE.isEnabled() || player == null) {
			clearColorGrade();
			return;
		}
		long nanos = System.nanoTime();
		double dt = lastNanos == 0 ? 0.016 : Math.min((nanos - lastNanos) / 1e9, 0.1);
		lastNanos = nanos;
		double now = nanos / 1e9;
		int w = g.guiWidth(), h = g.guiHeight();
		double healthFrac = player.getMaxHealth() > 0 ? player.getHealth() / player.getMaxHealth() : 1;

		// hurt vignette amount (drawn by the post pass)
		hurtTarget = player.isDeadOrDying() ? hurtTarget : 1 - healthFrac;
		hurtAmount += (hurtTarget - hurtAmount) * Math.min(1, 0.8 * dt);
		updateColorGrade(mc, damageFxCurve(healthFrac), hurtAmount > 0.003 ? Math.min(1, hurtAmount) : 0);
		// Blood splats expire and the hit indicators fade even when they aren't drawn (no asset pack, or
		// the HUD hidden), so neither piles up.
		splats.removeIf(s -> (now - s.born()) / s.lifetime() >= 1);
		for (int i = 0; i < 4; i++) direction[i] = Math.max(0, direction[i] - dt * 1.25);
		if (!available() || mc.options.hideGui) return;

		// screen blood
		double fov = OVERLAY_CAMERA_FOV;
		int vignetteByte = Math.round(Mth.clamp(handVignette, 0, 1) * 255);
		double tanHalf = Math.tan(Math.toRadians(fov / 2)), aspect = (double) w / h, depth = 0.548;
		for (int i = splats.size() - 1; i >= 0; i--) {
			Splat s = splats.get(i);
			double age = (now - s.born()) / s.lifetime();
			double alpha = age < 0.0147 ? age / 0.0147 : age < 0.5735 ? 1 : 1 - (age - 0.5735) / (1 - 0.5735);
			double y = -0.138 + s.y() - s.drift() * (now - s.born());
			float cx = (float) (w / 2.0 * (1 + s.x() / (depth * tanHalf * aspect)));
			float cy = (float) (h / 2.0 * (1 - y / (depth * tanHalf)));
			float size = (float) Math.min(s.size() / (depth * tanHalf) * h / 2, 2.0 * h);
			// alpha: the splat's opacity (x 0.988, the particle colour's alpha); red: the vignette amount
			int color = ((int) (alpha * 0.988 * 255) << 24) | (vignetteByte << 16);
			var pose = g.pose();
			pose.pushMatrix();
			pose.translate(cx, cy);
			float scale = size / BLOOD_TILE;
			pose.scale(scale, scale);
			int u = (s.tile() % 4) * BLOOD_TILE, v = (s.tile() / 4) * BLOOD_TILE;
			g.blit(BLOOD_PIPELINE, BLOOD, -BLOOD_TILE / 2, -BLOOD_TILE / 2, u, v, BLOOD_TILE, BLOOD_TILE, BLOOD_TEX, BLOOD_TEX, color);
			pose.popMatrix();
		}

		// damage-direction indicators (left / right share a sprite, as do top / bottom)
		double unit = ClimbCrosshair.uiUnit();
		float sideW = (float) (620.68 * unit), topH = (float) (407.05 * unit);
		for (int i = 0; i < 4; i++) {
			if (direction[i] <= 0) continue;
			int color = ((int) (direction[i] * 255) << 24) | 0xFF0000;
			switch (i) {
				case 0 -> stretch(g, SIDE, 508, 1024, 0, 0, sideW, h, false, false, color);
				case 2 -> stretch(g, SIDE, 508, 1024, w - sideW, 0, sideW, h, true, false, color);
				case 1 -> stretch(g, TOP, 1024, 508, 0, 0, w, topH, false, false, color);
				default -> stretch(g, TOP, 1024, 508, 0, h - topH, w, topH, false, true, color);
			}
		}
	}

	/** ENT_Player.damageFXCurve (time, value, inSlope, outSlope), evaluated as Unity's Hermite curve. */
	private static final double[][] FX_KEYS = {
			{ -0.008, 1.021, -0.192, -0.192 }, { -0.001, 1.019, -0.192, -26.153 }, { 0.018, 0.53, -26.153, -0.809 },
			{ 0.674, -0.002, -0.809, -0.035 }, { 1.001, -0.013, -0.035, -0.035 } };

	static double damageFxCurve(double t) {
		return clamp01(evaluateCurve(FX_KEYS, t));
	}

	/** ENT_Player.dyingAudioCurve (time, value, inSlope, outSlope): the mortal-wound loop, from 25% health down. */
	private static final double[][] DYING_KEYS = {
			{ -0.0079, 0.9981, -0.0731, -0.0731 }, { 0.0342, 0.995, -0.0731, -4.5363 },
			{ 0.252, 0.007, -4.5363, -0.0236 }, { 1.0118, -0.0109, -0.0236, -0.0236 } };

	static double dyingAudioCurve(double t) {
		return clamp01(evaluateCurve(DYING_KEYS, t));
	}

	/** A Unity AnimationCurve of non-weighted keys: cubic Hermite between keys, clamped outside them. */
	private static double evaluateCurve(double[][] keys, double t) {
		if (t <= keys[0][0]) return keys[0][1];
		for (int i = 0; i < keys.length - 1; i++) {
			double[] a = keys[i], b = keys[i + 1];
			if (t > b[0]) continue;
			double dt = b[0] - a[0], u = (t - a[0]) / dt, u2 = u * u, u3 = u2 * u;
			return (2 * u3 - 3 * u2 + 1) * a[1] + (u3 - 2 * u2 + u) * dt * a[3]
					+ (-2 * u3 + 3 * u2) * b[1] + (u3 - u2) * dt * b[2];
		}
		return keys[keys.length - 1][1];
	}

	/**
	 * The damage look as one post effect (wkclimb:damage) whose uniforms are rewritten every frame, so
	 * it follows health smoothly: it fades out as health regenerates, as in the game.
	 */
	private static final ResourceLocation GRADE = WKClimb.id("damage");
	private static double appliedBlend;
	private static int appliedStep;
	/** What the hands are drawn with this frame (neutral when the effect isn't on screen). */
	private static float handSaturation = 1, handBright = 1, handVignette;

	private static void updateColorGrade(Minecraft mc, double blend, double vignette) {
		if (mc.player == null || mc.player.isSpectator()) blend = vignette = 0;
		handSaturation = 1;
		handBright = 1;
		handVignette = 0;
		var renderer = mc.gameRenderer;
		ResourceLocation current = renderer.currentPostEffect();
		boolean ours = GRADE.equals(current);
		if (blend <= 0.002 && vignette <= 0) {
			if (ours) renderer.clearPostEffect();
			appliedBlend = 0;
			appliedStep = 0;
			return;
		}
		if (current != null && !ours) return; // leave vanilla effects (spectating a creeper) alone
		if (!ours) ((dev.fizz.wkclimb.client.mixin.GameRendererInvoker) renderer).wkclimb$setPostEffect(GRADE);
		var chain = mc.getShaderManager().getPostChain(GRADE, net.minecraft.client.renderer.LevelTargetBundle.MAIN_TARGETS);
		if (chain == null) return;
		var passes = ((dev.fizz.wkclimb.client.mixin.PostChainAccessor) chain).wkclimb$passes();
		if (passes.isEmpty()) return;
		var buffer = ((dev.fizz.wkclimb.client.mixin.PostPassAccessor) passes.get(0)).wkclimb$customUniforms().get("DamageConfig");
		if (buffer == null) return;
		float saturation = (float) saturationFor(blend), bright = (float) brightnessFor(blend);
		try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
			java.nio.ByteBuffer data = com.mojang.blaze3d.buffers.Std140Builder.onStack(stack, 16)
					.putFloat(saturation)
					.putFloat(bright)
					.putFloat((float) vignette)
					.get();
			com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data);
		}
		handSaturation = saturation;
		handBright = bright;
		handVignette = (float) vignette;
		appliedBlend = blend;
		appliedStep = (int) Math.round(blend * 20);
	}

	public static double colorGradeBlend() { return appliedBlend; }

	/** damageFX.hsv.y = 0.1, lerped from the default 1 by the curve (FXManager.BlendFXData). */
	static double saturationFor(double blend) {
		return 1 - 0.9 * blend;
	}

	/**
	 * The game's post shader multiplies by 0.25 + 0.75 * (brightness setting + damageFX.gamma 2 x blend).
	 * Relative to full health at the default brightness (1): 1 + 1.5 x blend, up to about x1.8 near death.
	 */
	static double brightnessFor(double blend) {
		return (0.25 + 0.75 * (1 + 2 * blend)) / (0.25 + 0.75);
	}

	/**
	 * Vertex colour for a hand (core/hand.fsh): saturation, brightness - 1 and vignette, and in alpha
	 * the hand's stamina used, as 128 + 127 x amount (never 0, so the draw is never skipped).
	 */
	static int handColor(float staminaUsed) {
		int a = 128 + Math.round(Mth.clamp(staminaUsed, 0, 1) * 127), r = Math.round(Mth.clamp(handSaturation, 0, 1) * 255);
		int gr = Math.round(Mth.clamp(handBright - 1, 0, 1) * 255), b = Math.round(Mth.clamp(handVignette, 0, 1) * 255);
		return a << 24 | r << 16 | gr << 8 | b;
	}

	/** Turn the grade off (hands toggled off, or leaving the world). */
	public static void clearColorGrade() {
		Minecraft mc = Minecraft.getInstance();
		ResourceLocation current = mc.gameRenderer.currentPostEffect();
		if (GRADE.equals(current)) mc.gameRenderer.clearPostEffect();
		appliedStep = 0;
		appliedBlend = 0;
		handSaturation = 1;
		handBright = 1;
		handVignette = 0;
	}

	public static int colorGradeStep() { return appliedStep; }

	/** Draw a texture stretched over a rectangle, optionally mirrored (by flipping the UVs). */
	private static void stretch(GuiGraphics g, ResourceLocation tex, int tw, int th, float x, float y, float width, float height,
			boolean flipX, boolean flipY, int color) {
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(width / tw, height / th);
		g.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, flipX ? tw : 0, flipY ? th : 0, tw, th,
				flipX ? -tw : tw, flipY ? -th : th, tw, th, color);
		pose.popMatrix();
	}

	private static double clamp01(double v) {
		return v < 0 ? 0 : v > 1 ? 1 : v;
	}
}
