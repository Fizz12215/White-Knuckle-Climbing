package dev.fizz.wkclimb.client;

import dev.fizz.wkclimb.sim.ClimbSim;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The two hand icons beside the crosshair, as in White Knuckle: open when free, a fist while holding,
 * tinted toward red as that hand's grip runs out and shaking when it is nearly gone.
 * The pixel art is drawn here in code (no game assets).
 */
public final class ClimbHud {
	private ClimbHud() {}

	private static final String[] OPEN = {
			".x.x.x....",
			".x.x.x.x..",
			".x.x.x.x..",
			".xxxxxxx.x",
			"xxxxxxxxxx",
			"xxxxxxxxx.",
			".xxxxxxx..",
			"..xxxxx...",
			"..xxxxx...",
	};
	private static final String[] FIST = {
			"..........",
			"..........",
			".xxxxxx...",
			"xxxxxxxx..",
			"xxxxxxxxx.",
			"xxxxxxxxx.",
			".xxxxxxx..",
			"..xxxxx...",
			"..xxxxx...",
	};
	private static final int PX = 2;

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		ClimbController ctl = ClimbController.INSTANCE;
		if (!ctl.isEnabled() || mc.player == null || mc.options.hideGui) return;

		int cx = g.guiWidth() / 2;
		int cy = g.guiHeight() / 2;
		boolean hover = ctl.hovered() != null;
		if (ClimbHands.haveTextures()) {
			renderIcons(g, ctl, cx, cy, hover);
			return;
		}
		for (int h = 0; h < 2; h++) {
			ClimbSim.Hand hand = ctl.sim.hands[h];
			double grip = hand.gripPercent();
			boolean active = hand.isHanging() || hover;
			int alpha = active ? 0xF0 : 0x70;
			int rgb;
			if (!hand.isHanging() && hand.grabWait() > 0) {
				rgb = 0x808080;
			} else {
				double t = 1 - grip;
				int gb = (int) (255 * (1 - t * 0.85));
				rgb = (255 << 16) | (gb << 8) | gb;
			}
			int color = (alpha << 24) | rgb;
			int shadow = (alpha << 24) | 0x202020;

			int width = OPEN[0].length() * PX;
			int x = h == ClimbSim.LEFT ? cx - 14 - width : cx + 14;
			int y = cy - OPEN.length * PX / 2;
			boolean shaking = hand.isHanging() && (grip < 0.3 || hand.isShaking());
			if (shaking) {
				x += mc.player.getRandom().nextInt(3) - 1;
				y += mc.player.getRandom().nextInt(3) - 1;
			}
			String[] art = hand.isHanging() ? FIST : OPEN;
			boolean mirror = h == ClimbSim.LEFT;
			drawArt(g, art, x + 1, y + 1, mirror, shadow);
			drawArt(g, art, x, y, mirror, color);

			int barY = y + art.length * PX + 3;
			g.fill(x, barY, x + width, barY + 2, (alpha << 24) | 0x202020);
			g.fill(x, barY, x + (int) Math.round(width * grip), barY + 2, color);
		}
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

	private static void renderIcons(GuiGraphics g, ClimbController ctl, int cx, int cy, boolean hover) {
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

	private static void drawArt(GuiGraphics g, String[] art, int x, int y, boolean mirror, int color) {
		for (int row = 0; row < art.length; row++) {
			String line = art[row];
			for (int col = 0; col < line.length(); col++) {
				if (line.charAt(col) != 'x') continue;
				int c = mirror ? line.length() - 1 - col : col;
				g.fill(x + c * PX, y + row * PX, x + c * PX + PX, y + row * PX + PX, color);
			}
		}
	}
}
