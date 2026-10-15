package dev.fizz.wkclimb.client;

import dev.fizz.wkclimb.ClimbConfig;
import dev.fizz.wkclimb.WKClimb;
import dev.fizz.wkclimb.net.ClimbStatePayload;
import dev.fizz.wkclimb.sim.ClimbSim;
import dev.fizz.wkclimb.sim.Handhold;
import dev.fizz.wkclimb.sim.V3;
import dev.fizz.wkclimb.sim.WK;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Runs the White Knuckle climbing simulation for the local player. Called from LivingEntity.travel:
 * while a hand is hanging the simulation owns the player's movement, otherwise vanilla does.
 * The simulation runs at WK's 50 Hz, so each 20 Hz Minecraft tick runs 2 or 3 steps.
 */
public final class ClimbController {
	public static final ClimbController INSTANCE = new ClimbController();

	public final ClimbSim sim = new ClimbSim();
	private boolean enabled = ClimbConfig.enabledOnStart;
	private double accumulator;
	private boolean driving;

	private final BlockPos[] holdBlocks = new BlockPos[2];
	/** Where each hand first gripped (blocks), for checking the ledge is still open above. */
	private final Vec3[] holdGrip = new Vec3[2];
	private final V3[] memoryPoint = new V3[2];
	private final BlockPos[] memoryBlock = new BlockPos[2];
	private final double[] memoryTime = new double[2];

	/** What the crosshair is on this tick, for the HUD. */
	private Handhold hovered;
	private boolean lastSentHanging;
	private int resendTimer;

	private ClimbController() {}

	public boolean isEnabled() { return enabled; }
	public Handhold hovered() { return hovered; }
	/** InteractCheck's hold memory: a handhold seen in the last grabForgive (0.2 s) keeps the icon up. */
	public boolean hasGrabMemory(int hand) { return memoryTime[hand] > 0 && memoryBlock[hand] != null; }

	public void setEnabled(boolean on) {
		enabled = on;
		if (!on) releaseAll();
		// the eye height depends on climbing mode (EyeHeightMixin): apply it now, not at the next pose change
		var player = Minecraft.getInstance().player;
		if (player != null) player.refreshDimensions();
	}

	private void releaseAll() {
		for (int h = 0; h < 2; h++) {
			if (sim.hands[h].isHanging()) sim.release(h, false);
			holdBlocks[h] = null;
		}
	}

	private static double k() { return ClimbConfig.blocksPerUnit; }
	private static double reachUnits() { return WK.INTERACT_DISTANCE * ClimbConfig.reachMultiplier; }
	private static V3 toWk(Vec3 v) { return new V3(v.x / k(), v.y / k(), v.z / k()); }
	private static Vec3 toMc(V3 v) { return new Vec3(v.x() * k(), v.y() * k(), v.z() * k()); }
	public static Vec3 toMcPublic(V3 v) { return toMc(v); }

	/** WK "vel" (per-step units, see WK.MOVE_SCALE) to Minecraft blocks per tick, and back. */
	private static Vec3 velToMc(V3 vel) { return toMc(vel.mul(WK.MOVE_SCALE * 0.05)); }
	private static V3 velToWk(Vec3 delta) { return toWk(delta).mul(1.0 / (WK.MOVE_SCALE * 0.05)); }

	/**
	 * @return true when the simulation moved the player this tick and vanilla travel must be skipped.
	 * With the White Knuckle character on, the simulation owns all on-foot movement: walking,
	 * sprinting, crouching, jumping, falling and climbing (ENT_Player.Movement). Minecraft keeps
	 * swimming, flying, elytra and riding.
	 */
	public boolean travel(LocalPlayer player) {
		sim.setInteractDistance(reachUnits());
		boolean allowed = enabled && !player.isPassenger() && !player.isSpectator()
				&& !player.getAbilities().flying && !player.isFallFlying() && !player.isSleeping();
		if (!allowed && sim.anyHanging()) releaseAll();
		boolean drive = allowed && (sim.anyHanging() || !(player.isInWater() || player.isInLava()));
		if (!drive && driving) {
			player.setDeltaMovement(velToMc(sim.velocity()));
			driving = false;
		}

		accumulator += 0.05;
		int steps = (int) Math.floor(accumulator / WK.FIXED_DT + 1e-9);
		accumulator -= steps * WK.FIXED_DT;

		Vec3 start = player.position();
		Body body = new Body(player);
		for (int i = 0; i < steps; i++) {
			if (allowed) handleHands(player);
			sim.updateHands(player.onGround(), WK.FIXED_DT);
			if (drive) {
				if (!driving) {
					sim.setVelocity(velToWk(player.getDeltaMovement()));
					sim.setJumpHeldState(player.input.keyPresses.jump());
					driving = true;
				}
				sim.movement(input(player), body);
			}
		}
		playEvents(player);
		updateBarPassThrough(player);
		syncServer();
		if (!drive) return false;

		if (sim.anyHanging()) player.resetFallDistance();
		Vec3 moved = player.position().subtract(start);
		player.setDeltaMovement(moved);
		// Sprinting flag for hunger, particles and other players: WK's sprint while moving on foot.
		player.setSprinting(sim.isSprinting() && moved.horizontalDistanceSqr() > 1e-4);
		return true;
	}

	/** True while the simulation owns the player's movement and the player is in the air. */
	public boolean isAirborneFromClimb() { return driving && !sim.isGrounded(); }

	/** True while White Knuckle's movement is driving the player. */
	public boolean isDriving() { return driving; }

	private ClimbSim.Input input(LocalPlayer player) {
		var keys = player.input.keyPresses;
		// White Knuckle reads its two move axes separately and doesn't normalise them, so W+D is (1, 1):
		// a full-strength climb plus strafe. Minecraft's move vector is normalised (0.71, 0.71), so build
		// the axes from the keys; analog input (a controller mod) keeps Minecraft's vector.
		double inputX, inputY;
		if (keys.forward() || keys.backward() || keys.left() || keys.right()) {
			inputX = (keys.right() ? 1 : 0) - (keys.left() ? 1 : 0);
			inputY = (keys.forward() ? 1 : 0) - (keys.backward() ? 1 : 0);
		} else {
			Vec2 move = player.input.getMoveVector();
			inputX = -move.x;
			inputY = move.y;
		}
		double yaw = Math.toRadians(player.getYRot());
		V3 bodyForward = new V3(-Math.sin(yaw), 0, Math.cos(yaw));
		V3 bodyRight = new V3(-Math.cos(yaw), 0, -Math.sin(yaw));
		Vec3 look = player.getLookAngle();

		// Hang(): Physics.Raycast(cam, transform.forward, cCon.radius * 2.5). cCon.radius is the controller's
		// own (unscaled) 0.3, so the probe is 0.75 units long, not scaled by the 1.5 player scale.
		Vec3 eye = player.getEyePosition();
		double wallProbe = WK.CCON_RADIUS * 2.5 * k();
		Vec3 probeEnd = eye.add(bodyForward.x() * wallProbe, 0, bodyForward.z() * wallProbe);
		boolean wallAhead = player.level().clip(new ClipContext(eye, probeEnd, ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.BLOCK;

		return new ClimbSim.Input(inputX, inputY, new V3(look.x, look.y, look.z), bodyForward, bodyRight,
				keys.jump(), keys.shift(), keys.sprint(), wallAhead, ClimbFootsteps.under(player));
	}

	/** InteractCheck for both hands: hover, grab memory (grabForgive), press-hold grabbing and letting go. */
	private void handleHands(LocalPlayer player) {
		Minecraft mc = Minecraft.getInstance();
		Level level = player.level();
		Catch found = findHandhold(player);
		hovered = found == null ? null : found.kind();
		V3 hoverPoint = found == null ? null : toWk(found.point());
		BlockPos hoverBlock = found == null ? null : found.block();

		boolean noScreen = mc.screen == null;
		boolean[] held = {
				noScreen && mc.options.keyAttack.isDown(),
				noScreen && mc.options.keyUse.isDown()
		};
		for (int h = 0; h < 2; h++) {
			ClimbSim.Hand hand = sim.hands[h];
			if (hoverPoint != null) {
				memoryPoint[h] = hoverPoint;
				memoryBlock[h] = hoverBlock;
				memoryTime[h] = WK.GRAB_FORGIVE;
			} else if (memoryTime[h] > 0) {
				memoryTime[h] -= WK.FIXED_DT;
			}

			if (hand.isHanging()) {
				if (!held[h]) {
					sim.release(h, true);
					holdBlocks[h] = null;
				} else if (classify(level, holdBlocks[h]) == null || !stillGrippable(level, h)) {
					sim.release(h, false); // the hold broke (dropOnInactive)
					holdBlocks[h] = null;
				}
				continue;
			}
			if (!held[h]) continue;
			if (hoverPoint != null && hand.grabWait() <= 0) {
				sim.grab(h, hoverPoint, hovered);
				holdBlocks[h] = hoverBlock;
				holdGrip[h] = toMc(hoverPoint);
			} else if (memoryTime[h] > 0 && hand.grabWait() < 0 && memoryBlock[h] != null) {
				Handhold remembered = classify(level, memoryBlock[h]);
				if (remembered != null) {
					sim.grab(h, memoryPoint[h], remembered);
					holdBlocks[h] = memoryBlock[h];
					holdGrip[h] = toMc(memoryPoint[h]);
				}
			}
		}
	}

	/** What a hand would catch: the grip point, the block it belongs to, and its handhold kind. */
	public record Catch(Vec3 point, BlockPos block, Handhold kind, double along) {}

	/**
	 * Hand.EnqueueInteractCast + InteractCheck: a ray and a sphere cast from the camera over the reach,
	 * radius InteractSphereRadius() = clamp(speed * 0.03, 0.15, 0.3) units. The first handhold the sphere
	 * touches is caught, at Collider.ClosestPoint. A ledge handhold here is every exposed top edge of a
	 * block (open above it and open on that side), the way White Knuckle lines ledges with thin
	 * handhold colliders. Blocks in #wkclimb:grab_anywhere (and every block with ledges_only off) are
	 * caught where the ray hits them.
	 */
	public static Catch findHandhold(LocalPlayer player) {
		Level level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 dir = player.getLookAngle();
		double reach = reachUnits() * k();
		double speedUnits = player.getDeltaMovement().length() * 20 / k();
		double radius = Math.max(0.15, Math.min(0.3, speedUnits * 0.03)) * k();

		BlockHitResult direct = level.clip(new ClipContext(eye, eye.add(dir.scale(reach)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		double directDist = direct.getType() == HitResult.Type.BLOCK ? direct.getLocation().distanceTo(eye) : reach;
		Catch best = null;
		if (direct.getType() == HitResult.Type.BLOCK) {
			BlockPos pos = direct.getBlockPos();
			Handhold kind = classify(level, pos);
			if (kind != null && (!ClimbConfig.ledgesOnly || level.getBlockState(pos).is(WKClimb.GRAB_ANYWHERE))) {
				best = new Catch(direct.getLocation(), pos, kind, directDist);
			}
		}
		if (!ClimbConfig.ledgesOnly) return best;

		// The sphere stops where the ray is blocked, so nothing behind a wall can be caught.
		double castLength = Math.min(reach, directDist + radius);
		Vec3 castEnd = eye.add(dir.scale(castLength));
		AABB search = new AABB(eye, castEnd).inflate(radius + 1.0);
		for (BlockPos pos : BlockPos.betweenClosed(
				Mth.floor(search.minX), Mth.floor(search.minY), Mth.floor(search.minZ),
				Mth.floor(search.maxX), Mth.floor(search.maxY), Mth.floor(search.maxZ))) {
			if (level.getBlockState(pos).isAir()) continue;
			Handhold kind = classify(level, pos);
			if (kind == null) continue;
			if (level.getBlockState(pos).is(WKClimb.GRAB_ANYWHERE)) {
				// Buttons, ladders, vines, chains: the whole outline is a handhold collider.
				Catch c = sphereCastShape(level.getBlockState(pos).getShape(level, pos), pos, kind, eye, dir, castLength, radius);
				if (c != null && (best == null || c.along() < best.along())) best = c;
				continue;
			}
			// Ledges are the exposed top edges of the block's visible shape, box by box, so fence and wall
			// rails, stair steps, slabs and lanterns are caught where they are drawn (not at the taller
			// or blockier collision shape).
			for (AABB local : level.getBlockState(pos).getShape(level, pos).toAabbs()) {
				AABB box = local.move(pos);
				double top = box.maxY;
				for (Direction d : Direction.Plane.HORIZONTAL) {
					Vec3 a, b;
					switch (d) {
						case NORTH -> { a = new Vec3(box.minX, top, box.minZ); b = new Vec3(box.maxX, top, box.minZ); }
						case SOUTH -> { a = new Vec3(box.minX, top, box.maxZ); b = new Vec3(box.maxX, top, box.maxZ); }
						case WEST -> { a = new Vec3(box.minX, top, box.minZ); b = new Vec3(box.minX, top, box.maxZ); }
						default -> { a = new Vec3(box.maxX, top, box.minZ); b = new Vec3(box.maxX, top, box.maxZ); }
					}
					double[] st = closestSegmentParams(eye, castEnd, a, b);
					Vec3 onRay = eye.add(castEnd.subtract(eye).scale(st[0]));
					// Judge an end of the edge a hair inside it: an exact end lies on block boundaries, where the
					// probes would see "outside" every block (four floor blocks meeting at a corner would pass as a
					// ledge). A point just inside a real edge is still on it.
					double length = b.subtract(a).length();
					double nudge = length > 2 * END_NUDGE ? END_NUDGE / length : 0.5;
					Vec3 onEdge = a.add(b.subtract(a).scale(Mth.clamp(st[1], nudge, 1 - nudge)));
					if (onRay.distanceToSqr(onEdge) > radius * radius) continue;
					double along = st[0] * castLength;
					if (best != null && along >= best.along()) continue;
					if (!isLedgePoint(level, onEdge, d)) continue;
					best = new Catch(onEdge, pos.immutable(), kind, along);
				}
			}
		}
		return best;
	}

	/** First point along the cast where a sphere of this radius touches the shape; grip at ClosestPoint. */
	private static Catch sphereCastShape(VoxelShape shape, BlockPos pos, Handhold kind, Vec3 eye, Vec3 dir,
			double castLength, double radius) {
		if (shape.isEmpty()) return null;
		double step = Math.max(0.02, radius / 2);
		for (AABB box : shape.toAabbs()) {
			AABB b = box.move(pos);
			for (double t = 0; t <= castLength; t += step) {
				Vec3 p = eye.add(dir.scale(t));
				Vec3 q = new Vec3(Mth.clamp(p.x, b.minX, b.maxX), Mth.clamp(p.y, b.minY, b.maxY), Mth.clamp(p.z, b.minZ, b.maxZ));
				if (p.distanceToSqr(q) <= radius * radius) return new Catch(q, pos.immutable(), kind, t);
			}
		}
		return null;
	}

	/** Parameters (s on p0-p1, t on q0-q1) of the closest points between two segments. */
	private static double[] closestSegmentParams(Vec3 p0, Vec3 p1, Vec3 q0, Vec3 q1) {
		Vec3 d1 = p1.subtract(p0), d2 = q1.subtract(q0), r = p0.subtract(q0);
		double a = d1.dot(d1), e = d2.dot(d2), f = d2.dot(r);
		double s, t;
		if (a < 1e-12 && e < 1e-12) return new double[] { 0, 0 };
		if (a < 1e-12) {
			s = 0;
			t = Mth.clamp(f / e, 0, 1);
		} else {
			double c = d1.dot(r);
			if (e < 1e-12) {
				t = 0;
				s = Mth.clamp(-c / a, 0, 1);
			} else {
				double b = d1.dot(d2), denom = a * e - b * b;
				s = denom > 1e-12 ? Mth.clamp((b * f - c * e) / denom, 0, 1) : 0;
				t = (b * s + f) / e;
				if (t < 0) { t = 0; s = Mth.clamp(-c / a, 0, 1); }
				else if (t > 1) { t = 1; s = Mth.clamp((b - c) / a, 0, 1); }
			}
		}
		return new double[] { s, t };
	}

	/** How far the open-space probes sit from an edge (blocks). */
	private static final double PROBE = 0.04;
	/** How far inside an edge its ends are judged (blocks); see findHandhold. */
	private static final double END_NUDGE = 0.001;
	/** Room a hand needs along a ledge either side of the grip (blocks). */
	private static final double HAND_CLEARANCE = 0.1;

	/**
	 * Whether a point on a top edge is a real ledge you can hold from its open side, judged against the
	 * visible shapes of whatever blocks are there:
	 *  - just above it, a hair inward, is open: room for the fingers on top. This rejects edges covered
	 *    by another part of the same block (the stair's lower half under its upper step) or the block above;
	 *  - just outside it, a hair below the top, is open: there is a drop. This rejects the meeting point
	 *    of two joined blocks (two fence rails);
	 *  - just outside it, a hair above the top, is open: the hand reaches over from there. This rejects
	 *    an inner corner where a block overhangs the edge (a block's back edge under the front of one
	 *    standing one up and one back);
	 *  - the top stays open for a hand's half-width either way along the edge. This rejects the end of a
	 *    ledge pressed into the side of a taller neighbour (the other kind of inner corner).
	 */
	static boolean isLedgePoint(Level level, Vec3 edge, Direction outward) {
		Vec3 out = new Vec3(outward.getStepX(), 0, outward.getStepZ());
		Vec3 along = new Vec3(-out.z, 0, out.x);
		Vec3 above = edge.add(out.scale(-PROBE)).add(0, PROBE, 0);
		Vec3 beside = edge.add(out.scale(PROBE)).add(0, -PROBE, 0);
		Vec3 over = edge.add(out.scale(PROBE)).add(0, PROBE, 0);
		return !insideShape(level, above) && !insideShape(level, beside) && !insideShape(level, over)
				&& !insideShape(level, above.add(along.scale(HAND_CLEARANCE)))
				&& !insideShape(level, above.add(along.scale(-HAND_CLEARANCE)));
	}

	/**
	 * Whether a point is inside the visible shape of a block that has a collision box. Blocks you can
	 * walk through (one snow layer, leaf litter, petals, rails, plants) don't cover a ledge.
	 */
	private static boolean insideShape(Level level, Vec3 p) {
		BlockPos pos = BlockPos.containing(p);
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || state.getCollisionShape(level, pos).isEmpty()) return false;
		VoxelShape shape = state.getShape(level, pos);
		if (shape.isEmpty()) return false;
		double x = p.x - pos.getX(), y = p.y - pos.getY(), z = p.z - pos.getZ();
		for (AABB b : shape.toAabbs()) {
			if (x > b.minX && x < b.maxX && y > b.minY && y < b.maxY && z > b.minZ && z < b.maxZ) return true;
		}
		return false;
	}

	/** A held ledge stops being one if something is built over it (checked at the grip point). */
	private boolean stillGrippable(Level level, int hand) {
		BlockPos pos = holdBlocks[hand];
		if (!ClimbConfig.ledgesOnly || level.getBlockState(pos).is(WKClimb.GRAB_ANYWHERE) || holdGrip[hand] == null) return true;
		return !insideShape(level, holdGrip[hand].add(0, PROBE, 0));
	}

	/** Which kind of handhold a block is, or null if it can't be grabbed. */
	public static Handhold classify(Level level, BlockPos pos) {
		if (pos == null) return null;
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || state.is(WKClimb.UNCLIMBABLE)) return null;
		boolean tagged = state.is(WKClimb.HANDHOLDS);
		if (ClimbConfig.handholdsOnly && !tagged) return null;
		if (!tagged && state.getCollisionShape(level, pos).isEmpty()) return null;
		if (state.is(WKClimb.ICY)) return Handhold.ICY;
		if (WKClimb.isRod(state, level, pos)) return Handhold.BAR;
		if (state.is(WKClimb.SLOW)) return Handhold.CLIMB_SLOW;
		return Handhold.DEFAULT;
	}

	private void playEvents(LocalPlayer player) {
		for (ClimbSim.Event e : sim.drainEvents()) {
			int h = e.hand();
			BlockPos held = h >= 0 ? holdBlocks[h] : null;
			switch (e.type()) {
				case GRAB -> {
					// PlayHandholdSound(material): "handhold-<material>" if the game has that set, else "handhold"
					BlockPos pos = held != null ? held : BlockPos.containing(toMc(sim.hands[h].holdPoint()));
					BlockState state = player.level().getBlockState(pos);
					ClimbSounds.Sound sound = ClimbSounds.HANDHOLD;
					if (state.is(WKClimb.CHAIN_SOUND)) sound = ClimbSounds.HANDHOLD_CHAIN;
					else if (ClimbFootsteps.isMetafungus(state) && ClimbSounds.available(ClimbSounds.HANDHOLD_METAFUNGUS)) sound = ClimbSounds.HANDHOLD_METAFUNGUS;
					ClimbSounds.play(player, sound);
				}
				case RELEASE, PULLED_OFF -> ClimbSounds.play(player, ClimbSounds.DROP);
				case ICY_SLIP -> {
					ClimbSounds.play(player, ClimbSounds.ICY_SLIP);
					ClimbSounds.play(player, ClimbSounds.DROP);
				}
				case ICY_SHAKE -> ClimbSounds.play(player, ClimbSounds.ICY_SHAKE);
				case FATIGUE_DROP -> ClimbSounds.play(player, ClimbSounds.FATIGUE_DROP);
				case JUMP -> {
					ClimbSounds.play(player, ClimbSounds.JUMP);
					ClimbHands.onJump();
				}
				case FOOTSTEP -> ClimbSounds.footstep(player, ClimbFootsteps.material(player));
			}
		}
		ClimbSounds.updateFatigue(player, sim);
	}

	/** Ticks of bar pass-through left after letting go of a bar (so you can swing out past it). */
	private int barGrace;
	private boolean lastSentThrough;

	/** Rod-like blocks stop colliding while you swing from one, for 0.5 s after, and while you still overlap one. */
	private void updateBarPassThrough(LocalPlayer player) {
		boolean onBar = false;
		for (ClimbSim.Hand h : sim.hands) {
			if (h.isHanging() && h.handhold() == Handhold.BAR) onBar = true;
		}
		if (onBar) barGrace = 10;
		else if (barGrace > 0) barGrace--;
		boolean through = enabled && (onBar || barGrace > 0 || (WKClimb.clientThroughBars && overlapsBar(player)));
		WKClimb.clientThroughBars = through;
	}

	private static boolean overlapsBar(LocalPlayer player) {
		Level level = player.level();
		AABB box = player.getBoundingBox();
		for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ),
				Mth.floor(box.maxX), Mth.floor(box.maxY), Mth.floor(box.maxZ))) {
			BlockState state = level.getBlockState(pos);
			if (!WKClimb.isRod(state, level, pos)) continue;
			for (AABB b : state.getCollisionShape(level, pos, net.minecraft.world.phys.shapes.CollisionContext.empty()).toAabbs()) {
				if (b.move(pos).intersects(box)) return true;
			}
		}
		return false;
	}

	private boolean lastSentWkMode;
	private boolean needResend = true;

	/** After joining a world the server knows nothing yet: send the full state again. */
	public void onJoin() {
		needResend = true;
	}

	private void syncServer() {
		boolean hanging = sim.anyHanging();
		boolean through = WKClimb.clientThroughBars;
		boolean wkMode = enabled;
		WKClimb.clientWkMode = wkMode;
		if (needResend || hanging != lastSentHanging || through != lastSentThrough || wkMode != lastSentWkMode
				|| ((hanging || through || wkMode) && --resendTimer <= 0)) {
			if (ClientPlayNetworking.canSend(ClimbStatePayload.TYPE)) {
				ClientPlayNetworking.send(new ClimbStatePayload(hanging, through, wkMode));
				needResend = false;
			}
			lastSentHanging = hanging;
			lastSentThrough = through;
			lastSentWkMode = wkMode;
			resendTimer = 20;
		}
	}

	/** FallingDamage's fall-grab / fall-land results, sent by the server. */
	public void onFallHurt(boolean grab, boolean land) {
		if (grab) {
			for (ClimbSim.Hand h : sim.hands) {
				if (!h.isHanging()) continue;
				sim.damageGrip(h.id, 3);
				ClimbDamage.splatterPublic(2);
			}
		}
		if (land) ClimbDamage.splatterPublic(2);
	}

	/** The WK CharacterController, backed by Minecraft's entity collision. */
	private record Body(LocalPlayer player) implements ClimbSim.Collider {
		/**
		 * ENT_Player's transform.position: the middle of the standing capsule (its CharacterController
		 * centre is 0). Crouching shrinks the capsule but doesn't move the transform, so this stays at
		 * half the standing height even while Minecraft's crouching box is shorter.
		 */
		@Override
		public V3 center() {
			return toWk(player.position().add(0, WK.PLAYER_HEIGHT / 2.0 * k(), 0));
		}

		@Override
		public ClimbSim.MoveResult move(V3 displacement) {
			Vec3 wanted = toMc(displacement);
			Vec3 before = player.position();
			player.move(MoverType.SELF, wanted);
			Vec3 actual = player.position().subtract(before);

			double nx = blockedNormal(wanted.x, actual.x);
			double ny = blockedNormal(wanted.y, actual.y);
			double nz = blockedNormal(wanted.z, actual.z);
			V3 normal = null;
			if (nx != 0 || nz != 0) normal = new V3(nx, 0, nz).normalized();
			else if (ny != 0) normal = new V3(0, ny, 0);
			return new ClimbSim.MoveResult(toWk(actual), player.onGround(), normal);
		}

		/** A surface stopped motion along this axis: its normal points back against the motion. */
		private static double blockedNormal(double wanted, double actual) {
			if (Math.abs(wanted) < 1e-7 || Math.abs(actual - wanted) < 1e-6) return 0;
			return -Math.signum(wanted);
		}
	}
}
