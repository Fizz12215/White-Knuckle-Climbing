package dev.fizz.wkclimb;

/**
 * The angles that turn a Minecraft player-model arm (hanging straight down at rest) to point at a spot.
 * The spot is given relative to the shoulder in the body's own directions: right, up and forward.
 *
 * Model space has y pointing down, the front at -z and the player's right at -x. A ModelPart turns by
 * xRot first, then yRot (zRot is left at 0), so the arm's direction is
 * (sin x * sin y, cos x, sin x * cos y). xRot is kept between -pi and 0 (the arm swings forward and up,
 * like a zombie's), which keeps the arm from twisting round.
 */
public final class ArmAim {
	private ArmAim() {}

	/** {xRot, yRot} in radians, or null when the spot is (almost) at the shoulder. */
	public static float[] angles(double right, double up, double forward) {
		double mx = -right, my = -up, mz = -forward;
		double len = Math.sqrt(mx * mx + my * my + mz * mz);
		if (len < 1e-4) return null;
		mx /= len;
		my /= len;
		mz /= len;
		double xRot = -Math.acos(Math.max(-1, Math.min(1, my)));
		double yRot = Math.abs(Math.sin(xRot)) < 1e-6 ? 0 : Math.atan2(-mx, -mz);
		return new float[] { (float) xRot, (float) yRot };
	}

	/** The model arm's direction for these angles, in the same right/up/forward terms (for tests). */
	public static double[] direction(float xRot, float yRot) {
		double mx = Math.sin(xRot) * Math.sin(yRot), my = Math.cos(xRot), mz = Math.sin(xRot) * Math.cos(yRot);
		return new double[] { -mx, -my, -mz };
	}
}
