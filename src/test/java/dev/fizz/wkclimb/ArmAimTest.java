package dev.fizz.wkclimb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ArmAimTest {
	@Test
	void straightUp() {
		float[] a = ArmAim.angles(0, 1, 0);
		assertEquals(-Math.PI, a[0], 1e-5);
		assertEquals(0, a[1], 1e-6);
	}

	@Test
	void straightAhead() {
		// like a zombie's arms: xRot -90 degrees
		float[] a = ArmAim.angles(0, 0, 2);
		assertEquals(-Math.PI / 2, a[0], 1e-5);
		assertEquals(0, a[1], 1e-5);
	}

	@Test
	void pointsWhereAsked() {
		double[][] spots = { { 1, 0, 0 }, { -1, 0, 0 }, { 0.3, 1, 0.5 }, { -0.4, 0.8, 0.6 }, { 0.2, -0.5, 1 }, { 0, 0.5, -1 } };
		for (double[] s : spots) {
			float[] a = ArmAim.angles(s[0], s[1], s[2]);
			double len = Math.sqrt(s[0] * s[0] + s[1] * s[1] + s[2] * s[2]);
			assertArrayEquals(new double[] { s[0] / len, s[1] / len, s[2] / len }, ArmAim.direction(a[0], a[1]), 1e-5);
		}
	}

	@Test
	void spotAtTheShoulder() {
		assertNull(ArmAim.angles(0, 0, 0));
	}
}
