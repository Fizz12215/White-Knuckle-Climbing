package dev.fizz.wkclimb.sim;

/**
 * White Knuckle tuning values. Everything here is in White Knuckle's own units and step rate.
 *
 * Provenance: values marked "prefab" were read from the ENT_Player MonoBehaviour serialized in the
 * game's level3 scene (they override the C# field initialisers). Values marked "code" are literals
 * in ENT_Player.Movement / Hang / HandStamina / Jump. Fixed timestep is from globalgamemanagers
 * (TimeManager: 2822399 / 141120000 s = 0.02 s).
 */
public final class WK {
	private WK() {}

	/** Unity FixedUpdate step (TimeManager). */
	public static final double FIXED_DT = 0.02;
	/** cCon.Move(vel * fixedDeltaTime * 175f): "vel" is not m/s, displacement per step is vel*dt*175. */
	public static final double MOVE_SCALE = 175.0;

	// prefab
	public static final double SPEED = 0.85;
	public static final double SPRINT_SPEED = 2.5;
	public static final double CROUCH_SPEED = 0.3;
	public static final double FOOTSTEP_SPEED = 0.8;
	public static final double FOOTSTEP_CROUCH_SPEED = 1.0;
	public static final double FOOTSTEP_SPRINT_SPEED = 0.6;
	public static final double SOFT_LAND_CUTOFF = 2.0;
	public static final double MEDIUM_LAND_CUTOFF = 13.0;
	public static final double HEAVY_LAND_CUTOFF = 23.0;
	public static final double JUMP_HEIGHT = 0.4;
	public static final double GRAVITY = -0.2;
	public static final double DRAG = 8.0;
	public static final double FRICTION_MULT = 100.0;
	public static final double WALL_FRICTION_MULT = 10.0;
	public static final double AIR_CONTROL = 0.3;
	public static final double COYOTE_TIME = 0.2;
	public static final double SLOPE_LIMIT = 55.0;
	public static final double GRIP_STRENGTH_MAX = 10.0;   // gripStrengthTimer
	public static final double CLIMB_JUMP_DRAIN = 1.0;
	public static final double INTERACT_DISTANCE = 2.5;
	public static final double GRAB_FORGIVE = 0.2;
	public static final double PLAYER_SCALE = 1.5;
	public static final double STANDING_HEIGHT = 1.5;
	public static final double CCON_RADIUS = 0.3;           // set every FixedUpdate in code

	// code
	public static final double HANG_SLACK = 1.2;            // Hang(): num2
	public static final double HANG_WALL_PUSH = 0.09;       // zero += hitNormal * 0.09
	public static final double CLIMB_ACCEL = 0.015;
	public static final double CLIMB_MAX = 0.02;
	public static final double GRIP_LOSS_ONE_HAND = 1.0;    // gripStrengthLossRate
	public static final double GRIP_LOSS_TWO_HANDS = 0.5;
	public static final double GRIP_REGEN_GROUNDED = 0.9;
	public static final double GRIP_REGEN_AIR = 0.75;
	public static final double GRIP_REGEN_HANGING_GROUNDED = 0.75;
	public static final double GRIP_FATIGUE_DROP = 0.1;
	public static final double FATIGUE_GRAB_WAIT = 1.0;
	public static final double RELEASE_GRAB_WAIT = 0.25;
	public static final double REGEN_WAIT = 0.17;
	public static final double BOTH_HANDS_JUMP_STRAIN = 0.48;
	public static final double HIT_TIME = 0.1;

	/** World-space height of the WK player capsule: standingHeight * playerScale. */
	public static final double PLAYER_HEIGHT = STANDING_HEIGHT * PLAYER_SCALE;
}
