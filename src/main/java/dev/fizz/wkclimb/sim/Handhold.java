package dev.fizz.wkclimb.sim;

/**
 * The CL_Handhold fields the climbing code reads. Defaults are the most common prefab values
 * (about 1,900 of the ~2,600 handholds in sharedassets1); CLIMB_SLOW (climbMult 0.8) is the second
 * most common. ICY is the Chimney mode ice, read from the CL_Handhold_Icy prefabs in sharedassets1.
 */
public record Handhold(
		double dragMult,
		double climbMult,
		double climbGrav,
		double strainRate,
		double jumpMult,
		double jumpStrainMult,
		double speedMult,
		double hangVelAdjust,
		boolean dropOnJump,
		boolean icy,
		double icyMaxHoldTime,
		double icySlipSpeed,
		double icyShakeTime,
		double icyShakeAmount) {

	public static final Handhold DEFAULT = new Handhold(10, 1, 0, 1, 1, 1, 1, 1, true, false, 0, 0, 0, 0);
	public static final Handhold CLIMB_SLOW = new Handhold(10, 0.8, 0, 1, 1, 1, 1, 1, true, false, 0, 0, 0, 0);
	/**
	 * The "Handhold-Bar" preset: dragMult 4 (against 10) so a hanging body swings like a pendulum and
	 * keeps its momentum, and climbMult 0.1 so you hang and swing rather than climb.
	 */
	public static final Handhold BAR = new Handhold(4, 0.1, 0, 1, 1, 1, 1, 1, true, false, 0, 0, 0, 0);
	/** The Chimney ice (314 of 329 CL_Handhold_Icy prefabs): maxHoldTime 2, slipSpeed 0.3, shake after 1 s at 0.5. */
	public static final Handhold ICY = new Handhold(10, 1, 0, 1, 1, 1, 1, 1, true, true, 2.0, 0.3, 1.0, 0.5);
}
