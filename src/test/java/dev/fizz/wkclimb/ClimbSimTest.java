package dev.fizz.wkclimb;

import dev.fizz.wkclimb.sim.ClimbSim;
import dev.fizz.wkclimb.sim.Handhold;
import dev.fizz.wkclimb.sim.V3;
import dev.fizz.wkclimb.sim.WK;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless checks of the ported climbing rules. Expected numbers are worked out by hand from the
 * decompiled White Knuckle code and the extracted prefab values, not from this implementation.
 */
class ClimbSimTest {
	/** Open air with an optional wall: the capsule may not go past wallX on +X. No floor. */
	static final class TestWorld implements ClimbSim.Collider {
		V3 pos;
		final Double wallX;
		static final double R = WK.CCON_RADIUS * WK.PLAYER_SCALE;

		TestWorld(V3 pos, Double wallX) { this.pos = pos; this.wallX = wallX; }

		@Override public V3 center() { return pos; }

		@Override public ClimbSim.MoveResult move(V3 d) {
			V3 target = pos.add(d);
			V3 normal = null;
			if (wallX != null && target.x() > wallX - R) {
				target = new V3(wallX - R, target.y(), target.z());
				normal = new V3(-1, 0, 0);
			}
			V3 actual = target.sub(pos);
			pos = target;
			return new ClimbSim.MoveResult(actual, false, normal);
		}
	}

	/** A floor (top at y = 0) that ends at x = 0: walking towards +X steps off its edge. */
	static final class LedgeWorld implements ClimbSim.Collider {
		V3 pos;
		boolean grounded = true;

		LedgeWorld(V3 pos) { this.pos = pos; }

		@Override public V3 center() { return pos; }

		@Override public ClimbSim.MoveResult move(V3 d) {
			V3 target = pos.add(d);
			V3 normal = null;
			grounded = false;
			if (target.x() < 0 && target.y() <= 0) {
				target = new V3(target.x(), 0, target.z());
				grounded = true;
				normal = new V3(0, 1, 0);
			}
			V3 actual = target.sub(pos);
			pos = target;
			return new ClimbSim.MoveResult(actual, grounded, normal);
		}
	}

	static ClimbSim.Input input(double x, double y, V3 cam, boolean jump, boolean wallAhead) {
		return new ClimbSim.Input(x, y, cam, new V3(1, 0, 0), new V3(0, 0, -1), jump, false, false, wallAhead, null);
	}

	static void steps(ClimbSim sim, TestWorld w, int n, ClimbSim.Input in) {
		for (int i = 0; i < n; i++) {
			sim.updateHands(false, WK.FIXED_DT);
			sim.movement(in, w);
		}
	}

	@Test
	void oneHandGripLastsTenSecondsAirborne() {
		ClimbSim sim = new ClimbSim();
		TestWorld w = new TestWorld(V3.ZERO, null);
		sim.grab(ClimbSim.LEFT, new V3(0, 1.2, 0), Handhold.DEFAULT);
		int stepsHeld = 0;
		while (sim.anyHanging() && stepsHeld < 2000) {
			steps(sim, w, 1, input(0, 0, new V3(1, 0, 0), false, false));
			stepsHeld++;
		}
		// Drains 1/s from 10 and drops at <= 0.1 -> 9.9 s = 495 steps.
		assertEquals(495, stepsHeld, 2);
		assertTrue(sim.drainEvents().stream().anyMatch(e -> e.type() == ClimbSim.EventType.FATIGUE_DROP));
		assertTrue(sim.hands[0].grabWait() > 0.9, "fatigue drop sets a 1 s grab cooldown");
	}

	@Test
	void twoHandsHalveTheDrain() {
		ClimbSim sim = new ClimbSim();
		TestWorld w = new TestWorld(V3.ZERO, null);
		sim.grab(ClimbSim.LEFT, new V3(-0.2, 1.2, 0), Handhold.DEFAULT);
		sim.grab(ClimbSim.RIGHT, new V3(0.2, 1.2, 0), Handhold.DEFAULT);
		steps(sim, w, 250, input(0, 0, new V3(1, 0, 0), false, false));
		// 5 s at 0.5/s per hand.
		assertEquals(7.5, sim.hands[0].grip(), 0.05);
		assertEquals(7.5, sim.hands[1].grip(), 0.05);
	}

	@Test
	void climbingJumpReleasesAndStrainsBothHands() {
		ClimbSim sim = new ClimbSim();
		TestWorld w = new TestWorld(V3.ZERO, null);
		sim.grab(ClimbSim.LEFT, new V3(0, 1.0, 0), Handhold.DEFAULT);
		sim.grab(ClimbSim.RIGHT, new V3(0, 1.0, 0), Handhold.DEFAULT);
		steps(sim, w, 1, input(0, 0, new V3(1, 0, 0), false, false));
		double gripBefore = sim.hands[0].grip();
		sim.updateHands(false, WK.FIXED_DT);
		sim.movement(input(0, 0, new V3(1, 0, 0), true, false), w);
		assertFalse(sim.anyHanging());
		// climbJumpDrain 1.0 * 0.48 for a two-handed jump, plus one step of drain (0.5 * 0.02).
		assertEquals(gripBefore - 0.48 - 0.01, sim.hands[0].grip(), 1e-6);
		// Jump runs after gravity and drag: vel.y = max(vel.y, jumpHeight * -gravity) = 0.08.
		assertEquals(0.08, sim.velocity().y(), 1e-9);
		assertTrue(sim.hands[0].grabWait() > 0.2, "release adds 0.25 s grab wait");
	}

	@Test
	void hangingSettlesNearTheSlackLength() {
		ClimbSim sim = new ClimbSim();
		TestWorld w = new TestWorld(new V3(0, -0.5, 0), null);
		V3 hold = new V3(0, 1.0, 0);
		sim.grab(ClimbSim.LEFT, hold, Handhold.DEFAULT);
		sim.grab(ClimbSim.RIGHT, hold, Handhold.DEFAULT);
		steps(sim, w, 400, input(0, 0, new V3(1, 0, 0), false, false));
		double dist = hold.sub(w.pos).length();
		assertTrue(sim.anyHanging());
		assertTrue(dist > WK.HANG_SLACK && dist < WK.HANG_SLACK + 0.3, "dist=" + dist);
		assertTrue(sim.velocity().length() < 0.002, "vel=" + sim.velocity());
	}

	@Test
	void pressingForwardOnAWallClimbsAboveTheHold() {
		ClimbSim sim = new ClimbSim();
		TestWorld w = new TestWorld(new V3(0, 0, 0), 0.6);
		V3 hold = new V3(0.6, 1.0, 0);
		sim.grab(ClimbSim.LEFT, hold, Handhold.DEFAULT);
		sim.grab(ClimbSim.RIGHT, hold, Handhold.DEFAULT);
		steps(sim, w, 20, input(0, 1, new V3(1, 0, 0), false, false));
		double startY = w.pos.y();
		steps(sim, w, 150, input(0, 1, new V3(0.9, 0.436, 0).normalized(), false, true));
		assertTrue(w.pos.y() > startY + 0.5, "climbed from " + startY + " to " + w.pos.y());
		assertTrue(w.pos.y() > hold.y() - 0.2, "reached the hold height, y=" + w.pos.y());
	}

	@Test
	void icyHoldSlipsAfterTwoSecondsLikeChimney() {
		ClimbSim sim = new ClimbSim();
		TestWorld w = new TestWorld(V3.ZERO, null);
		sim.grab(ClimbSim.LEFT, new V3(0, 1.2, 0), Handhold.ICY);
		int n = 0;
		while (sim.anyHanging() && n < 1000) { steps(sim, w, 1, input(0, 0, new V3(1, 0, 0), false, false)); n++; }
		// Chimney ice: maxHoldTime 2 s, sliding 0.3 units/s meanwhile
		assertEquals(100, n, 2);
		assertTrue(sim.drainEvents().stream().anyMatch(e -> e.type() == ClimbSim.EventType.ICY_SLIP));
	}

	/** Flat floor at y = 0 (capsule centre may not go below half the player height). */
	static final class Floor implements ClimbSim.Collider {
		V3 pos = new V3(0, WK.PLAYER_HEIGHT / 2, 0);
		@Override public V3 center() { return pos; }
		@Override public ClimbSim.MoveResult move(V3 d) {
			V3 target = pos.add(d);
			boolean grounded = false;
			V3 normal = null;
			if (target.y() <= WK.PLAYER_HEIGHT / 2) {
				target = target.withY(WK.PLAYER_HEIGHT / 2);
				grounded = true;
				normal = V3.UP;
			}
			V3 actual = target.sub(pos);
			pos = target;
			return new ClimbSim.MoveResult(actual, grounded, normal);
		}
	}

	static ClimbSim.Input walk(double forward, boolean sprint, boolean crouch, boolean jump, ClimbSim.Footstep floor) {
		return new ClimbSim.Input(0, forward, new V3(1, 0, 0), new V3(1, 0, 0), new V3(0, 0, -1), jump, crouch, sprint, false, floor);
	}

	static double groundSpeed(ClimbSim sim, Floor f, ClimbSim.Input in, int steps) {
		for (int i = 0; i < steps; i++) { sim.updateHands(sim.isGrounded(), WK.FIXED_DT); sim.movement(in, f); }
		return sim.velocity().x();
	}

	@Test
	void groundSpeedsMatchWhiteKnuckle() {
		// Per step: vel += a (a = speed*dt*0.15), then drag removes 8*15*vel^2*dt. Settles where
		// a = k*(v+a)^2 with k = 120*dt, so v = sqrt(a/k) - a (speed measured after the drag). Gravity
		// still adds -0.004 to vel.y on the ground before the floor cancels it, which adds a little drag.
		assertEquals(terminal(0.85), groundSpeed(new ClimbSim(), new Floor(), walk(1, false, false, false, null), 300), 5e-4);
		assertEquals(terminal(2.5), groundSpeed(new ClimbSim(), new Floor(), walk(1, true, false, false, null), 300), 5e-4);
		assertEquals(terminal(0.3), groundSpeed(new ClimbSim(), new Floor(), walk(1, false, true, false, null), 300), 5e-4);
	}

	static double terminal(double speed) {
		double a = speed * WK.FIXED_DT * 0.15, k = 8 * 15 * WK.FIXED_DT;
		return Math.sqrt(a / k) - a;
	}

	@Test
	void groundJumpIsJumpHeightTimesGravity() {
		ClimbSim sim = new ClimbSim();
		Floor f = new Floor();
		groundSpeed(sim, f, walk(0, false, false, false, null), 20);
		sim.updateHands(true, WK.FIXED_DT);
		sim.movement(walk(0, false, false, true, null), f);
		assertEquals(0.08, sim.velocity().y(), 0.005);
		double top = 0;
		for (int i = 0; i < 200; i++) { sim.updateHands(sim.isGrounded(), WK.FIXED_DT); sim.movement(walk(0, false, false, true, null), f); top = Math.max(top, f.pos.y()); }
		double apex = top - WK.PLAYER_HEIGHT / 2;
		// 14 u/s up against 35 u/s^2 gravity and quadratic air drag: a bit under 2.8 units
		assertTrue(apex > 2.0 && apex < 2.8, "apex " + apex);
		assertTrue(sim.isGrounded(), "holding jump does not bunny-hop: must release first");
	}

	@Test
	void iceFloorSlides() {
		ClimbSim.Footstep ice = new ClimbSim.Footstep("ice", 0.05, 0.2, 0.6);
		ClimbSim normal = new ClimbSim(), icy = new ClimbSim();
		Floor fn = new Floor(), fi = new Floor();
		groundSpeed(normal, fn, walk(1, false, false, false, null), 200);
		groundSpeed(icy, fi, walk(1, false, false, false, ice), 200);
		double xn = fn.pos.x(), xi = fi.pos.x();
		groundSpeed(normal, fn, walk(0, false, false, false, null), 100);
		groundSpeed(icy, fi, walk(0, false, false, false, ice), 100);
		double slideNormal = fn.pos.x() - xn, slideIce = fi.pos.x() - xi;
		assertTrue(slideIce > 5 * slideNormal, "ice slide " + slideIce + " vs " + slideNormal);
	}

	static double swingAmplitude(Handhold hh) {
		ClimbSim sim = new ClimbSim();
		TestWorld w = new TestWorld(new V3(0, -WK.HANG_SLACK, 0), null);
		sim.grab(ClimbSim.LEFT, V3.ZERO, hh);
		sim.grab(ClimbSim.RIGHT, V3.ZERO, hh);
		sim.setVelocity(new V3(0.06, 0, 0));
		steps(sim, w, 100, input(0, 0, new V3(1, 0, 0), false, false));
		double max = 0;
		for (int i = 0; i < 100; i++) {
			steps(sim, w, 1, input(0, 0, new V3(1, 0, 0), false, false));
			max = Math.max(max, Math.abs(w.pos.x()));
		}
		return max;
	}

	@Test
	void barsSwingFreely() {
		// Handhold-Bar drag 4*8 = 32 against the usual 10*8 = 80: the swing lasts much longer
		double bar = swingAmplitude(Handhold.BAR), normal = swingAmplitude(Handhold.DEFAULT);
		assertTrue(bar > 1.5 * normal, "bar swing " + bar + " vs normal " + normal);
	}

	/**
	 * Hanging against a wall, the friction branch reads the sprint *state* (IsSprintHeld: sprint key with
	 * the movement keys pushed past 0.8). The sprint key alone, with no movement keys, is not sprinting,
	 * so the body slides exactly as with no keys at all.
	 */
	@Test
	void sprintKeyAloneDoesNotLoosenWallFriction() {
		double[] y = new double[2];
		int[] hits = new int[2];
		for (int run = 0; run < 2; run++) {
			ClimbSim sim = new ClimbSim();
			double wallX = 1.0;
			TestWorld world = new TestWorld(new V3(wallX - TestWorld.R, 0, 0), wallX);
			ClimbSim.Collider counting = new ClimbSim.Collider() {
				@Override public V3 center() { return world.center(); }

				@Override public ClimbSim.MoveResult move(V3 d) {
					ClimbSim.MoveResult r = world.move(d);
					if (r.hitNormal() != null) hits[0]++;
					return r;
				}
			};
			// a hold on the wall face, high enough above to pull the body into the wall
			sim.grab(ClimbSim.LEFT, new V3(wallX, 1.6, 0), Handhold.DEFAULT);
			boolean sprintKey = run == 1;
			ClimbSim.Input in = new ClimbSim.Input(0, 0, new V3(1, 0, 0), new V3(1, 0, 0), new V3(0, 0, -1),
					false, false, sprintKey, true, null);
			hits[0] = 0;
			for (int i = 0; i < 60; i++) {
				sim.updateHands(false, WK.FIXED_DT);
				sim.movement(in, counting);
			}
			y[run] = world.pos.y();
			hits[run] = hits[0];
		}
		assertTrue(hits[0] > 0 && hits[1] > 0, "the body was against the wall");
		assertEquals(y[0], y[1], 1e-12, "sprint key without movement keys changed the wall friction");
	}

	/**
	 * Movement(): grounded at the last move, not grounded now, and falling -> vel.y = 0. So a walk off an
	 * edge starts the fall from rest, instead of keeping the -0.05 "stick to the ground" speed.
	 */
	@Test
	void walkingOffAnEdgeStartsTheFallFromRest() {
		ClimbSim sim = new ClimbSim();
		LedgeWorld w = new LedgeWorld(new V3(-0.3, 0, 0));
		ClimbSim.Input walk = input(0, 1, new V3(1, 0, 0), false, false);
		double firstAirborneVy = Double.NaN;
		boolean wasGrounded = true;
		for (int i = 0; i < 200 && Double.isNaN(firstAirborneVy); i++) {
			sim.updateHands(w.grounded, WK.FIXED_DT);
			sim.movement(walk, w);
			if (wasGrounded && !w.grounded) {
				// this step moved past the edge; the next step is the first one that starts airborne
				sim.updateHands(w.grounded, WK.FIXED_DT);
				sim.movement(walk, w);
				firstAirborneVy = sim.velocity().y();
			}
			wasGrounded = w.grounded;
		}
		assertFalse(Double.isNaN(firstAirborneVy), "walked off the edge");
		// from rest, one step of gravity: -0.2 * 0.02 = -0.004 (drag is negligible at this speed)
		assertEquals(-0.004, firstAirborneVy, 0.001);
	}
}
