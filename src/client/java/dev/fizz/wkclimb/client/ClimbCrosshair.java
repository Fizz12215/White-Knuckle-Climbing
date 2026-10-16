package dev.fizz.wkclimb.client;

import dev.fizz.wkclimb.WKClimb;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * White Knuckle's crosshair (UI_CrosshairController), drawn in place of Minecraft's while the hands are on:
 * the "base" sprite at 64 UI units, and the "focus" ring, whose scale goes num^3 * 64 as num eases 0 -> 1
 * over 0.13 s while Focus is held.
 *
 * UI units follow the game's UI_CanvasScaler on the main Canvas (constant pixel size, scale factor
 * lerp(0, 4.5, width / 6000) * UIScale 0.75, x0.6 on screens wider than 2.5:1), converted to GUI units.
 */
public final class ClimbCrosshair {
	private ClimbCrosshair() {}

	private static final Identifier BASE = WKClimb.id("textures/gui/crosshair_base.png");
	private static final Identifier FOCUS = WKClimb.id("textures/gui/crosshair_focus.png");
	private static final int BASE_TEX = 512, FOCUS_TEX = 158;
	private static final double FOCUS_INTERP_TIME = 0.13;

	private static boolean focusActive;
	private static double focusTimeBegan = -1e9;

	/** One White Knuckle UI unit in Minecraft GUI units. */
	public static double uiUnit() {
		Minecraft mc = Minecraft.getInstance();
		int width = mc.getWindow().getWidth(), height = mc.getWindow().getHeight();
		double scale = Math.min(1, width / 6000.0) * 4.5 * 0.75;
		if (height > 0 && width / height > 2.5) scale *= 0.6; // integer division, as in the game
		return scale / mc.getWindow().getGuiScale();
	}

	public static boolean available() {
		return Minecraft.getInstance().getResourceManager().getResource(BASE).isPresent();
	}

	/** SetCrosshairFocus. */
	public static void setFocus(boolean state) {
		if (focusActive != state) {
			focusActive = state;
			focusTimeBegan = System.nanoTime() / 1e9;
		}
	}

	/** @return true if the White Knuckle crosshair was drawn (so the vanilla one should not be). */
	public static boolean render(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (!ClimbController.INSTANCE.isEnabled() || mc.player == null || !ClimbHands.haveTextures() || !available()) return false;
		if (!mc.options.getCameraType().isFirstPerson() || mc.gui.hud.isHidden()) return true;
		double unit = uiUnit();
		float cx = g.guiWidth() / 2f, cy = g.guiHeight() / 2f;

		double guiScale = mc.getWindow().getGuiScale();
		draw(g, "base", BASE_TEX, cx, cy, 64 * unit, guiScale);

		double t = Math.min(1, Math.max(0, (System.nanoTime() / 1e9 - focusTimeBegan) / FOCUS_INTERP_TIME));
		double num = focusActive ? t : 1 - t;
		double focusSize = num * num * num * 64 * unit;
		if (focusSize > 0.5) draw(g, "focus", FOCUS_TEX, cx, cy, focusSize, guiScale);
		return true;
	}

	/** Draws the smallest pre-filtered copy at least as big as the on-screen size (a mipmap stand-in). */
	private static void draw(GuiGraphicsExtractor g, String id, int fullSize, float cx, float cy, double size, double guiScale) {
		double pixels = size * guiScale;
		int texSize = fullSize;
		for (int s : new int[] { 32, 64, 128, 256 }) {
			if (s < fullSize && s >= pixels && Minecraft.getInstance().getResourceManager()
					.getResource(WKClimb.id("textures/gui/crosshair_" + id + "_" + s + ".png")).isPresent()) {
				texSize = s;
				break;
			}
		}
		Identifier tex = texSize == fullSize ? WKClimb.id("textures/gui/crosshair_" + id + ".png")
				: WKClimb.id("textures/gui/crosshair_" + id + "_" + texSize + ".png");
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(cx, cy);
		float s = (float) (size / texSize);
		pose.scale(s, s);
		g.blit(RenderPipelines.GUI_TEXTURED, tex, -texSize / 2, -texSize / 2, 0, 0, texSize, texSize, texSize, texSize);
		pose.popMatrix();
	}
}
