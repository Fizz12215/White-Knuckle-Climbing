package dev.fizz.wkclimb.sim;

/** Minimal immutable double vector so the simulation has no Minecraft dependency. */
public record V3(double x, double y, double z) {
	public static final V3 ZERO = new V3(0, 0, 0);
	public static final V3 UP = new V3(0, 1, 0);

	public V3 add(V3 o) { return new V3(x + o.x, y + o.y, z + o.z); }
	public V3 sub(V3 o) { return new V3(x - o.x, y - o.y, z - o.z); }
	public V3 mul(double s) { return new V3(x * s, y * s, z * s); }
	public double dot(V3 o) { return x * o.x + y * o.y + z * o.z; }
	public double lengthSqr() { return x * x + y * y + z * z; }
	public double length() { return Math.sqrt(lengthSqr()); }
	public boolean isZero() { return x == 0 && y == 0 && z == 0; }

	/** Unity's Vector3.normalized: returns zero for tiny vectors. */
	public V3 normalized() {
		double l = length();
		return l > 1e-5 ? mul(1.0 / l) : ZERO;
	}

	public V3 withY(double ny) { return new V3(x, ny, z); }

	/** Unity's Vector3.ClampMagnitude. */
	public V3 clampMagnitude(double max) {
		double l2 = lengthSqr();
		return l2 > max * max ? normalized().mul(max) : this;
	}

	/** Unity's Vector3.ProjectOnPlane. */
	public V3 projectOnPlane(V3 normal) {
		double n2 = normal.lengthSqr();
		if (n2 < 1e-12) return this;
		return sub(normal.mul(dot(normal) / n2));
	}

	public static V3 lerp(V3 a, V3 b, double t) {
		t = Math.max(0, Math.min(1, t));
		return a.add(b.sub(a).mul(t));
	}

	/** Unity's Vector3.Angle in degrees. */
	public static double angle(V3 a, V3 b) {
		double d = Math.sqrt(a.lengthSqr() * b.lengthSqr());
		if (d < 1e-15) return 0;
		return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, a.dot(b) / d))));
	}
}
