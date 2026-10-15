package dev.fizz.wkclimb.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * A port of White Knuckle's two-handed climbing: ENT_Player.Movement, Hang, Jump, HandStamina and
 * StopInteraction, plus CL_Handhold_Icy. It runs in White Knuckle units at its 50 Hz fixed step and
 * knows nothing about Minecraft; the host supplies input, raycast results and a collider.
 *
 * Only the climbing-relevant paths are ported. Perks/buffs, grappling, swimming, items and props are
 * left out (every buff term in the original evaluates to its neutral value without perks).
 */
public final class ClimbSim {
	public static final int LEFT = 0, RIGHT = 1;

	public enum EventType { GRAB, RELEASE, FATIGUE_DROP, JUMP, ICY_SHAKE, ICY_SLIP, PULLED_OFF, FOOTSTEP }
	public record Event(EventType type, int hand) {}

	/** What the host sends each step. Vectors are world space; inputX is +right, inputY is +forward. */
	public record Input(
			double inputX, double inputY,
			V3 camForward, V3 bodyForward, V3 bodyRight,
			boolean jumpHeld, boolean crouchHeld, boolean sprintHeld,
			boolean wallAhead, Footstep footstep) {}

	/** ENT_Player.Footsteps: the floor material's friction, movement lerp and speed multiplier. */
	public record Footstep(String name, double friction, double moveLerp, double moveSpeed) {
		public static final Footstep GENERIC = new Footstep("generic", 1, 1, 1);
	}

	/** Result of moving the body through the host's world. */
	public record MoveResult(V3 actual, boolean grounded, V3 hitNormal) {}

	public interface Collider {
		/** Capsule centre in WK units. */
		V3 center();
		/** Move by a displacement in WK units, with collision. */
		MoveResult move(V3 displacement);
	}

	public static final class Hand {
		public final int id;
		boolean hanging;
		V3 holdPoint = V3.ZERO;
		Handhold handhold = Handhold.DEFAULT;
		double grip = WK.GRIP_STRENGTH_MAX;
		double grabWait;
		double regenWait;
		double holdTime;
		double icyTime;
		boolean icyShaking;

		Hand(int id) { this.id = id; }

		public boolean isHanging() { return hanging; }
		public V3 holdPoint() { return holdPoint; }
		public Handhold handhold() { return handhold; }
		public double grip() { return grip; }
		public double gripPercent() { return grip / WK.GRIP_STRENGTH_MAX; }
		public double grabWait() { return grabWait; }
		public boolean canGrab() { return !hanging && grabWait <= 0; }
		public boolean isShaking() { return icyShaking; }
	}

	/** Test support: both hands free, cooldowns cleared, grip set. */
	public void resetHands(double grip) {
		for (Hand h : hands) {
			if (h.hanging) release(h.id, false);
			h.grip = Math.min(grip, WK.GRIP_STRENGTH_MAX);
			h.grabWait = -0.01;
			h.regenWait = 0;
		}
		events.clear();
	}

	/** Test support: set one hand's grip. */
	public void setGripForTest(int hand, double grip) {
		hands[hand].grip = clamp(grip, 0, WK.GRIP_STRENGTH_MAX);
	}

	/** Hand.DamageGripStrength: lose grip; below 0.25 the hand is set to 0.5 and lets go. */
	public void damageGrip(int hand, double amount) {
		Hand h = hands[hand];
		if (amount <= 0) return;
		h.grip = clamp(h.grip - Math.abs(amount), 0, WK.GRIP_STRENGTH_MAX);
		if (h.grip < 0.25) {
			h.grip = 0.5;
			release(hand, true);
		}
	}

	public final Hand[] hands = { new Hand(LEFT), new Hand(RIGHT) };
	private final List<Event> events = new ArrayList<>();

	/** "vel" in ENT_Player: displacement per step is vel * dt * 175. */
	private V3 vel = V3.ZERO;
	private V3 moveAxis = V3.ZERO;
	private V3 climbDir = V3.UP;
	private V3 jumpDir = V3.UP;
	private V3 hitNormal = V3.ZERO;
	private boolean hasHit;
	private double hitTime;
	private boolean isGrounded;
	/**
	 * ENT_Player.isGrounded as it stood at the previous move: the grounded state from the start of that
	 * step (cleared by a jump). Movement() compares it with cCon.isGrounded to spot walking off an edge.
	 */
	private boolean groundedAtLastMove;
	private boolean isSliding;
	private double timeSinceGrounded;
	private boolean hasJumped;
	private boolean sprinting;
	private boolean crouching;
	private double footstepTime;
	/** Reach in WK units, for the over-stretch checks in Hang(). */
	private double interactDistance = WK.INTERACT_DISTANCE;

	public V3 velocity() { return vel; }
	public void setVelocity(V3 v) { vel = v; }
	public boolean isGrounded() { return isGrounded; }
	public double timeSinceGrounded() { return timeSinceGrounded; }
	public boolean isSliding() { return isSliding; }
	/** The FOV-widening sprint state: sprinting && IsGrounded(coyote) && !isSliding. */
	public boolean isSprintingOnGround() { return sprinting && timeSinceGrounded < WK.COYOTE_TIME && !isSliding; }
	public boolean isSprinting() { return sprinting; }
	public boolean isCrouching() { return crouching; }
	public void setInteractDistance(double d) { interactDistance = d; }
	public void setJumpHeldState(boolean held) { hasJumped = held; }

	public boolean anyHanging() { return hands[0].hanging || hands[1].hanging; }
	public boolean bothHanging() { return hands[0].hanging && hands[1].hanging; }

	public List<Event> drainEvents() {
		List<Event> out = new ArrayList<>(events);
		events.clear();
		return out;
	}

	/** Hand.GrabHold. The host has already checked the hand can grab and the target is a handhold. */
	public void grab(int hand, V3 point, Handhold handhold) {
		Hand h = hands[hand];
		h.hanging = true;
		h.holdPoint = point;
		h.handhold = handhold;
		h.icyTime = 0;
		h.icyShaking = false;
		events.add(new Event(EventType.GRAB, hand));
	}

	/** StopInteraction(hand, type, addWait). */
	public void release(int hand, boolean addWait) {
		Hand h = hands[hand];
		h.regenWait = WK.REGEN_WAIT;
		if (h.hanging) {
			h.hanging = false;
			if (addWait) h.grabWait += WK.RELEASE_GRAB_WAIT;
			events.add(new Event(EventType.RELEASE, hand));
		}
	}

	/** Player.DropHang: let go with every hanging hand, with a grip penalty and cooldown. */
	public void dropHang(double grabCooldown, double gripLoss) {
		for (Hand h : hands) {
			if (h.hanging) {
				release(h.id, true);
				h.grip -= gripLoss;
			}
			if (h.grabWait < grabCooldown) h.grabWait = grabCooldown;
		}
	}

	/**
	 * Per-hand part of a step: grab cooldown, icy handholds, and HandStamina. Runs every step,
	 * climbing or not, so grip regenerates while the host's own movement is in charge.
	 */
	public void updateHands(boolean grounded, double dt) {
		isGrounded = grounded;
		double gripLossRate = bothHanging() ? WK.GRIP_LOSS_TWO_HANDS : WK.GRIP_LOSS_ONE_HAND;
		for (Hand h : hands) {
			if (h.regenWait > 0) h.regenWait -= dt;
			if (h.grabWait >= 0) h.grabWait -= dt;
			if (h.hanging && h.handhold.icy()) icy(h, dt);

			if (h.hanging) {
				h.holdTime += dt;
				if (!grounded) {
					double strain = h.handhold.strainRate();
					h.grip = clamp(h.grip - dt * gripLossRate * strain, 0, WK.GRIP_STRENGTH_MAX);
				} else {
					h.grip = clamp(h.grip + dt * WK.GRIP_REGEN_HANGING_GROUNDED, 0, WK.GRIP_STRENGTH_MAX);
				}
			} else {
				h.holdTime = 0;
				if (grounded) {
					h.grip = clamp(h.grip + dt * WK.GRIP_REGEN_GROUNDED, 0, WK.GRIP_STRENGTH_MAX);
				} else if (h.regenWait <= 0) {
					h.grip = clamp(h.grip + dt * WK.GRIP_REGEN_AIR, 0, WK.GRIP_STRENGTH_MAX);
				}
			}
			if (h.grip <= WK.GRIP_FATIGUE_DROP && h.grabWait < 0.1) {
				if (h.hanging) events.add(new Event(EventType.FATIGUE_DROP, h.id));
				release(h.id, true);
				h.grabWait = WK.FATIGUE_GRAB_WAIT;
			}
		}
	}

	/** CL_Handhold_Icy.Update: the hold slides down, shakes, then slips. */
	private void icy(Hand h, double dt) {
		Handhold hh = h.handhold;
		boolean wasShaking = h.icyTime > hh.icyShakeTime();
		h.icyTime += dt;
		h.holdPoint = h.holdPoint.add(new V3(0, -dt * hh.icySlipSpeed(), 0));
		if (h.icyTime > hh.icyShakeTime()) {
			h.icyShaking = true;
			if (!wasShaking) events.add(new Event(EventType.ICY_SHAKE, h.id));
		}
		if (h.icyTime > hh.icyMaxHoldTime()) {
			release(h.id, true);
			events.add(new Event(EventType.ICY_SLIP, h.id));
		}
	}

	/** One ENT_Player.Movement() step, followed by the hitTime bookkeeping from LateUpdate. */
	public void movement(Input in, Collider body) {
		final double dt = WK.FIXED_DT;
		V3 up = V3.UP;

		Footstep floor = in.footstep() != null ? in.footstep() : Footstep.GENERIC;
		// Walking off an edge: grounded at the last move, not any more, falling -> the fall starts from rest.
		if (groundedAtLastMove && !isGrounded && vel.y() < 0 && !isSliding) vel = vel.withY(0);
		crouching = in.crouchHeld();
		// IsSprintHeld(): sprint key with the stick/keys pushed (inputAxis.magnitude > 0.8) and not crouching
		sprinting = in.sprintHeld() && Math.hypot(in.inputX(), in.inputY()) > 0.8 && !crouching;
		V3 b = (anyHanging() ? V3.ZERO : in.bodyForward().mul(in.inputY())).add(in.bodyRight().mul(in.inputX()));
		moveAxis = V3.lerp(moveAxis, b, dt * 100 * floor.moveLerp()).clampMagnitude(1);
		jumpDir = up;
		double curSpeed = WK.SPEED;
		double curDrag = WK.DRAG;
		boolean hanging = anyHanging();

		if (!isGrounded && !hanging && hitTime <= 0) hitNormal = up;

		double slopeAngle = V3.angle(up, hitNormal);
		isSliding = slopeAngle >= WK.SLOPE_LIMIT && slopeAngle <= 89.0;
		if (isSliding) {
			vel = vel.add(hitNormal.mul(dt * 0.5 * (slopeAngle / 90.0) * dt));
			curSpeed *= 0.5;
			double a = V3.angle(hitNormal, up);
			if (a < 90 && a > 20) jumpDir = V3.lerp(hitNormal, up, 0.9).normalized();
		}

		if (isGrounded) timeSinceGrounded = 0;
		else timeSinceGrounded += dt;

		vel = vel.add(up.mul(WK.GRAVITY * dt));

		if (isGrounded) {
			if (!isSliding) {
				vel = vel.withY(Math.max(vel.y(), -0.05));
				curDrag *= moveAxis.length() > 1e-6 ? 15.0 : WK.FRICTION_MULT * floor.friction();
			}
		} else {
			curSpeed *= WK.AIR_CONTROL;
			if (hanging && hitTime > 0) {
				// "sprinting" is IsSprintHeld(): the sprint key with the movement keys pushed, not crouching
				if (sprinting) {
					curDrag *= WK.WALL_FRICTION_MULT;
				} else if (in.crouchHeld()) {
					curDrag *= WK.WALL_FRICTION_MULT * 30;
					vel = V3.lerp(vel, V3.ZERO, dt * 5);
				} else {
					curDrag *= WK.WALL_FRICTION_MULT * 3;
				}
			}
		}

		// speed by stance (the camera-sway block of Movement)
		if (sprinting && timeSinceGrounded < WK.COYOTE_TIME && !isSliding) {
			if (isGrounded) curSpeed = WK.SPRINT_SPEED;
		} else if (crouching && !isSliding) {
			curSpeed = WK.CROUCH_SPEED;
		}
		footstepAudio(curSpeed, dt);

		double[] speedDrag = { curSpeed, curDrag };
		V3 pull = hang(in, body.center(), speedDrag);
		curSpeed = speedDrag[0];
		curDrag = speedDrag[1];

		vel = vel.add(moveAxis.mul(curSpeed * floor.moveSpeed()).add(pull.mul(1.5)).mul(dt * 0.15));
		double dragAmount = curDrag * vel.lengthSqr();
		vel = vel.add(vel.normalized().mul(-dragAmount * dt).clampMagnitude(vel.length() / 2));

		if (timeSinceGrounded < WK.COYOTE_TIME || anyHanging()) {
			if (in.jumpHeld() && !hasJumped) jump();
			else if (!in.jumpHeld()) hasJumped = false;
		}

		boolean verticalSlide = false;
		double intoWall = vel.normalized().dot(hitNormal);
		if (hitTime >= 0 && intoWall <= 0 && slopeAngle >= 85) {
			vel = vel.projectOnPlane(hitNormal);
			verticalSlide = true;
		}

		boolean groundedBeforeMove = isGrounded; // Jump() may have cleared it this step
		groundedAtLastMove = groundedBeforeMove;
		MoveResult r = body.move(vel.mul(dt * WK.MOVE_SCALE));
		if (r.hitNormal() != null) {
			// OnControllerColliderHit
			hitNormal = r.hitNormal();
			hasHit = true;
			hitTime = WK.HIT_TIME;
			if (vel.normalized().dot(hitNormal) <= 0 && V3.angle(up, hitNormal) > WK.SLOPE_LIMIT) {
				vel = vel.projectOnPlane(hitNormal);
				verticalSlide = true;
			}
		}
		isGrounded = r.grounded();
		// Movement() clamps with isGrounded as it was before cCon.Move (it isn't refreshed until next step)
		if (!verticalSlide && groundedBeforeMove) {
			V3 a = r.actual();
			vel = new V3(
					clamp(vel.x(), -Math.abs(a.x()), Math.abs(a.x())),
					clamp(vel.y(), -Math.abs(a.y()), Math.abs(a.y())),
					clamp(vel.z(), -Math.abs(a.z()), Math.abs(a.z())));
		}
		if (hitTime <= 0) isSliding = false;

		// LateUpdate
		if (hitTime > 0) {
			hitTime -= dt;
		} else {
			hitNormal = V3.ZERO;
			hasHit = false;
			climbDir = up;
			jumpDir = up;
		}
	}

	/** FootstepAudio(): a step every footstepSpeed / max(curSpeed, 1.2) seconds while moving on the ground. */
	private void footstepAudio(double curSpeed, double dt) {
		if (moveAxis.length() <= 0.5 || timeSinceGrounded >= 0.2) return;
		footstepTime -= Math.max(curSpeed, 1.2) * dt;
		if (footstepTime <= 0) {
			events.add(new Event(EventType.FOOTSTEP, -1));
			footstepTime = crouching ? WK.FOOTSTEP_CROUCH_SPEED : sprinting ? WK.FOOTSTEP_SPRINT_SPEED : WK.FOOTSTEP_SPEED;
		}
	}

	/** ENT_Player.Hang(). Returns the pull towards the holds; adjusts vel, curSpeed and curDrag. */
	private V3 hang(Input in, V3 center, double[] speedDrag) {
		V3 up = V3.UP;
		V3 pull = V3.ZERO;
		if (!anyHanging()) return pull;

		speedDrag[0] = WK.SPEED;
		double climb = 0;
		if (!isGrounded) {
			climbDir = hasHit ? mostYFacingPerpendicular(hitNormal) : up;
			if (hasHit && V3.angle(up.mul(-1), hitNormal) < 10) {
				climbDir = up.mul(-0.15);
			} else if (climbDir.sub(up).lengthSqr() > 1e-10) {
				climbDir = climbDir.add(hitNormal.mul(0.1));
			}
		} else {
			climbDir = up;
		}

		V3 strafe = in.bodyRight().mul(in.inputX() * 0.5);
		if (in.wallAhead()) {
			moveAxis = strafe.add(V3.lerp(in.camForward(), climbDir.mul(-1), 0.3).normalized().mul(in.inputY()));
		} else {
			moveAxis = strafe.add(in.camForward().add(climbDir).mul(in.inputY()));
		}

		double speedMult = 0;
		for (Hand h : hands) {
			if (!h.hanging) continue;
			Handhold hh = h.handhold;
			V3 toHold = h.holdPoint.sub(center);
			double dist = toHold.length();
			toHold = dist <= WK.HANG_SLACK ? V3.ZERO : toHold.normalized().mul(dist - WK.HANG_SLACK);
			if (dist > interactDistance * 1.25) {
				toHold = toHold.mul(4);
				if (dist > interactDistance * 2) {
					release(h.id, true);
					events.add(new Event(EventType.PULLED_OFF, h.id));
				}
			}
			pull = pull.add(toHold);
			if (!hitNormal.isZero()) pull = pull.add(hitNormal.mul(WK.HANG_WALL_PUSH));

			double lookUp = in.camForward().dot(up);
			if (lookUp >= 0) {
				climb = clamp(climb + in.inputY() * WK.CLIMB_ACCEL, 0, WK.CLIMB_MAX) * hh.climbMult();
			} else {
				double t = Math.abs(lookUp);
				double dir = in.inputY() + (-in.inputY() - in.inputY()) * t;
				climb = clamp(climb + dir * WK.CLIMB_ACCEL, 0, WK.CLIMB_MAX) * hh.climbMult();
			}
			double dt = WK.FIXED_DT;
			double lerpT = clamp(dt * 8 * clamp(h.holdTime, 0, 2) * hh.hangVelAdjust(), 0, 1);
			vel = new V3(
					vel.x() + climbDir.x() * climb * dt,
					vel.y() + (climb * climbDir.y() - vel.y()) * lerpT + hh.climbGrav() * dt,
					vel.z() + climbDir.z() * climb * dt);
			speedDrag[1] = Math.max(speedDrag[1], WK.DRAG * hh.dragMult());
			speedMult = hh.speedMult();
			if (moveAxis.length() < 0.05 && in.crouchHeld()) {
				vel = V3.lerp(vel.withY(0), V3.ZERO, dt * 5);
			}
		}
		speedDrag[0] *= speedMult;
		return pull;
	}

	/** ENT_Player.Jump() for the climbing case (no perks, no extra jumps). */
	private void jump() {
		boolean wasHanging = anyHanging();
		double jumpMultSum = 0;
		int hangingCount = 0;
		if (wasHanging) {
			double strain = bothHanging() ? WK.BOTH_HANDS_JUMP_STRAIN : 1.0;
			for (Hand h : hands) {
				if (!h.hanging) continue;
				hangingCount++;
				jumpMultSum += h.handhold.jumpMult();
				if (h.handhold.dropOnJump()) {
					h.grip -= WK.CLIMB_JUMP_DRAIN * h.handhold.jumpStrainMult() * strain;
					release(h.id, true);
				}
			}
		}
		double mult = hangingCount > 0 ? jumpMultSum / hangingCount : 1.0;
		// IsHanging() is read after the hands let go, so a normal climbing jump uses jumpDir.
		V3 dir = anyHanging() ? climbDir : jumpDir;
		V3 impulse = dir.normalized().mul(WK.JUMP_HEIGHT * -WK.GRAVITY * mult);

		if (vel.y() < 0) vel = vel.withY(0);
		if (climbDir.dot(V3.UP) > 0) climbDir = V3.UP;
		vel = new V3(vel.x() + impulse.x(), Math.max(vel.y(), impulse.y()), vel.z() + impulse.z());
		timeSinceGrounded = WK.COYOTE_TIME;
		hasJumped = true;
		isGrounded = false;
		events.add(new Event(EventType.JUMP, -1));
	}

	static V3 mostYFacingPerpendicular(V3 n) {
		return V3.UP.projectOnPlane(n.normalized()).normalized();
	}

	private static double clamp(double v, double lo, double hi) {
		return v < lo ? lo : (v > hi ? hi : v);
	}
}
