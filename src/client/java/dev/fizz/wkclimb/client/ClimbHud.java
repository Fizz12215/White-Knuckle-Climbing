package dev.fizz.wkclimb.client;

import dev.fizz.wkclimb.sim.ClimbSim;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The two hand icons beside the crosshair, as in White Knuckle: open when a ledge is in reach, a fist
 * while holding, tinted toward red as that hand's grip runs out. They're White Knuckle's own icons, so
 * nothing is drawn without the asset pack (Minecraft's crosshair stays then).
 */
public final class ClimbHud {
	private ClimbHud() {}

	public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		ClimbController ctl = ClimbController.INSTANCE;
		if (!ctl.isEnabled() || mc.player == null || mc.gui.hud.isHidden() || !ClimbHands.haveTextures()) return;
		renderIcons(g, ctl, ctl.hovered() != null);
	}

	/** Icon sizes in the extracted pack (hand-open 108x128, hand-grabbed 100x106). */
	private static final int OPEN_W = 108, OPEN_H = 128, GRAB_W = 100, GRAB_H = 106;

	/**
	 * The interaction icons by the crosshair (Hand.uiInteract): hand-open when a ledge is in reach,
	 * hand-grabbed while holding, coloured Color.Lerp(red, white, round(grip% * 6) / 6).
	 * Interact_L / Interact_R sit at x -40 / +40 in 64x64 boxes (stretched to fill) under InteractHands,
	 * which is scaled by handIconScale (default 0.8). Interact_L is the mirrored one (scale x -1).
	 * Sizes are White Knuckle UI units (see ClimbCrosshair.uiUnit).
	 */
	private static final double HAND_ICON_SCALE = 0.8;
	/** InteractHands' anchored x position in the crosshair group. */
	private static final double INTERACT_HANDS_X = 0.15436;

	private static void renderIcons(GuiGraphicsExtractor g, ClimbController ctl, boolean hover) {
		double unit = ClimbCrosshair.uiUnit() * HAND_ICON_SCALE;
		float box = (float) (64 * unit);
		float centerX = g.guiWidth() / 2f + (float) (INTERACT_HANDS_X * unit), centerY = g.guiHeight() / 2f;
		for (int h = 0; h < 2; h++) {
			ClimbSim.Hand hand = ctl.sim.hands[h];
			boolean grabbed = hand.isHanging();
			// Visibility as in InteractCheck: shown while holding (the hover pass is skipped then); else
			// only when a handhold is in reach and the hand can grab (grabWait <= 0), or for the grab
			// memory (grabForgive) after looking away. During the cooldown after letting go it is hidden.
			boolean shown = grabbed
					|| (hover && hand.grabWait() <= 0)
					|| (ctl.hasGrabMemory(h) && hand.grabWait() < 0);
			if (!shown) continue;
			int tw = grabbed ? GRAB_W : OPEN_W, th = grabbed ? GRAB_H : OPEN_H;
			double t = Math.round(hand.gripPercent() * 6) / 6.0;
			int gb = (int) Math.round(255 * t);
			int color = 0xFF000000 | (255 << 16) | (gb << 8) | gb;
			boolean left = h == ClimbSim.LEFT;
			var texture = ClimbHands.tex(grabbed ? "icon_grabbed" : "icon_open");
			var pose = g.pose();
			pose.pushMatrix();
			pose.translate(centerX + (float) ((left ? -40 : 40) * unit), centerY);
			// Image type Simple, preserveAspect off: the sprite is stretched over the 64x64 box.
			pose.scale(box / tw, box / th);
			if (left) {
				g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, texture, -tw / 2, -th / 2, tw, 0, tw, th, -tw, th, tw, th, color);
			} else {
				g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, texture, -tw / 2, -th / 2, 0, 0, tw, th, tw, th, tw, th, color);
			}
			pose.popMatrix();
		}
	}
}
