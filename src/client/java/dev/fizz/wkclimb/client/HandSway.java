package dev.fizz.wkclimb.client;

import java.util.Random;

/**
 * Port of White Knuckle's ViewSway (the component on Item_Hand_Left / Item_Hand_Right) and the
 * ViewSwayParameters sets on ENT_Player. Positions are in the hand target's local space (under the
 * 1.25-scaled Inventory-Root); rotation is the z tilt in degrees.
 */
public final class HandSway {
	/** A two-key Unity AnimationCurve, evaluated as the cubic Bezier Unity builds from slopes and weights. */
	public record Curve(double outSlope, double outWeight, double inSlope, double inWeight) {
		/** Linear(0,0,1,1) with slope 1 at both ends (non-weighted keys use 1/3 weights). */
		public static final Curve LINEAR = new Curve(1, 1 / 3.0, 1, 1 / 3.0);

		public double evaluate(double t) {
			if (t <= 0) return 0;
			if (t >= 1) return 1;
			double x1 = outWeight, y1 = outWeight * outSlope;
			double x2 = 1 - inWeight, y2 = 1 - inWeight * inSlope;
			double lo = 0, hi = 1, u = t;
			for (int i = 0; i < 30; i++) {
				u = (lo + hi) / 2;
				if (bezier(u, x1, x2) < t) lo = u;
				else hi = u;
			}
			return bezier(u, y1, y2);
		}

		private static double bezier(double u, double p1, double p2) {
			double v = 1 - u;
			return 3 * v * v * u * p1 + 3 * v * u * u * p2 + u * u * u;
		}
	}

	public record Params(double bobFrequencyMultiplier, double bobOffsetMult, double bobSmoothing,
			double offX, double offY, double offZ, double bobBaseRotation, Curve curve,
			double bobHorizontalFrequency, double bobVerticalFrequency, double bobHorizontalAmplitude,
			double bobVerticalAmplitude, double bobSway, double bobSwayFrequency) {}

	/** The ViewSway's own parameters on both hand models. */
	public static final Params BASE = new Params(1, 1, 5, 0, 0.025, 0, 0, Curve.LINEAR, 2, 4, 0.005, 0.01, 0, 4);
	/** ENT_Player.sprintViewSwayParameters. */
	public static final Params SPRINT = new Params(-1, 0.66, 6, 0.02, -0.22, -0.02, 15,
			new Curve(-0.017503351, 0.5581395, -1.0029129e-6, 0.47545224), 10, 5, -0.01, 0.04, 1.3, 10);
	/** ENT_Player.airbornViewSwayParameters. */
	public static final Params AIRBORNE = new Params(1, 1, 4, -0.02, 0, -0.02, 5, Curve.LINEAR, 8, 4, 0.013, 0.015, 1, 6);
	/** ENT_Player.longFallViewSwayParameters. */
	public static final Params LONG_FALL = new Params(1, 1, 6, -0.02, 0, -0.02, 5, Curve.LINEAR, 16, 8, 0.013, 0.015, 1, 6);

	private static final double IMPULSE_RECOVERY = 8;

	private final double side;           // -1 left hand (id 0), +1 right hand
	private final double animationOffset; // 0 left, 0.5 right
	private final Random random = new Random();
	public double x, y, z, rot;
	private double impulseX, impulseY, impulseZ, shake;

	public HandSway(boolean left) {
		this.side = left ? -1 : 1;
		this.animationOffset = left ? 0 : 0.5;
	}

	public void addImpulse(double ix, double iy, double iz) {
		impulseX += ix;
		impulseY += iy;
		impulseZ += iz;
	}

	public double shakeAmount() {
		return shake;
	}

	public void shake(double amount) {
		shake += amount;
	}

	/** ViewSway.Update. speed is the body speed in WK units/s, time in seconds. */
	public void update(Params p, double speed, double time, double dt) {
		shake = lerp(shake, 0, dt * 8);
		double rock = lerp(shake, 0, dt * 4);
		double vx = p.offX() * side, vy = p.offY(), vz = p.offZ();
		double value = Math.max(0, Math.min(8, speed));
		double bobTimer = time + p.bobOffsetMult() * animationOffset;
		double swayTarget = 0;
		double tx, ty, tz, smoothing = Math.min(1, dt * p.bobSmoothing());
		if (value > 0.1) {
			double fm = p.bobFrequencyMultiplier();
			double h = Math.sin(bobTimer * p.bobHorizontalFrequency() * fm);
			h = (p.curve().evaluate(h / 2 + 0.5) - 0.5) * 2;
			double hx = h * p.bobHorizontalAmplitude() * value;
			if (side < 0) hx = -hx;
			double v = p.curve().evaluate(Math.abs(Math.sin(bobTimer * p.bobVerticalFrequency() * fm)));
			double vyBob = v * p.bobVerticalAmplitude() * value;
			double sway = Math.sin(bobTimer * p.bobSwayFrequency() * fm) * value * p.bobSway();
			tx = hx + impulseX + vx;
			ty = vyBob + impulseY + vy;
			tz = impulseZ + vz;
			swayTarget = sway * p.bobSway() * -side * fm;
		} else {
			tx = vx + impulseX;
			ty = vy + impulseY;
			tz = vz + impulseZ;
		}
		double[] jitter = insideUnitSphere(shake, 0.5);
		x = lerp(x, tx, smoothing) + jitter[0];
		y = lerp(y, ty, smoothing) + jitter[1];
		z = lerp(z, tz, smoothing) + jitter[2];

		double rotJitter = Math.max(-20.5, Math.min(20.5, (random.nextDouble() * 2 - 1) * shake * 30));
		double rotTarget = swayTarget + rotJitter + Math.sin(rock + time) + p.bobBaseRotation() * side;
		rot = lerp(rot, rotTarget, Math.min(1, dt * 6));

		double k = Math.min(1, dt * IMPULSE_RECOVERY);
		impulseX = lerp(impulseX, 0, k);
		impulseY = lerp(impulseY, 0, k);
		impulseZ = lerp(impulseZ, 0, k);
	}

	private double[] insideUnitSphere(double scale, double maxLen) {
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

	private static double lerp(double a, double b, double t) {
		return a + (b - a) * Math.max(0, Math.min(1, t));
	}
}
