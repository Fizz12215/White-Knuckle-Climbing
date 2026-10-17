package dev.fizz.wkclimb;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoldReachTest {
	private static final Vec3 FEET = new Vec3(10, 64, -5);

	@Test
	void holdsWithinReachArePassedOn() {
		assertTrue(WKClimb.withinReach(FEET, FEET.add(0, 3, -1)));
		// the longest reach: 6 blocks from the eye, 1.4 above the feet
		assertTrue(WKClimb.withinReach(FEET, FEET.add(0, 7.4, 0)));
	}

	@Test
	void nonsenseIsNot() {
		assertFalse(WKClimb.withinReach(FEET, FEET.add(0, 20, 0)));
		assertFalse(WKClimb.withinReach(FEET, new Vec3(Double.NaN, 64, -5)));
		assertFalse(WKClimb.withinReach(FEET, new Vec3(10, Double.POSITIVE_INFINITY, -5)));
	}
}
