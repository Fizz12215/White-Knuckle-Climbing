package dev.fizz.wkclimb;

import dev.fizz.wkclimb.net.FallGripPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * White Knuckle's health rules for players in White Knuckle mode, run on the server
 * (ENT_Player: maxHealth 5, healingRate 0.25, healDelay 5, breathMax 10, FallingDamage()).
 * Minecraft health is 20, so every White Knuckle amount is scaled by 4. Other damage (mobs, fire,
 * lava...) keeps Minecraft's amounts, which then read as White Knuckle health on the same scale.
 *
 *  - regen: 0.25 WK health/s once 5 s have passed since the last damage; hunger is switched off;
 *  - falls: a fall starts when the body drops faster than 3 units/s (not hanging or swimming). Past
 *    the 50-unit safe distance (40 blocks) it hurts 0.1 every second on the way down and kills past
 *    200 units. On stopping, ceil((d - 60) / 15) + 1 damage if d > 50: catching a hold mid-fall
 *    ("fall-grab") also costs each holding hand 3 grip; a fall that ends in mid-air (water) is halved;
 *  - breath: 10 s underwater, refilling at 5/s, then 0.25 damage every second.
 */
public final class WKHealth {
	private WKHealth() {}

	public static final double SCALE = 20.0 / 5.0;
	private static final double MAX_FALL = dev.fizz.wkclimb.sim.WK.MAX_FALL_DISTANCE;
	private static final double BLOCKS_PER_UNIT = 0.8;
	private static final double DT = 0.05;

	private static final class State {
		boolean falling;
		double fallStartY, fallHurtTime, lastY = Double.NaN;
		net.minecraft.world.phys.Vec3 lastPos = net.minecraft.world.phys.Vec3.ZERO;
		double healTimer;
		double breath = 10, breathHurtTimer;
	}

	private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

	/** Test support: set the remaining breath (seconds). */
	public static void setBreath(UUID id, double seconds) {
		STATES.computeIfAbsent(id, k -> new State()).breath = seconds;
	}

	public static void forget(UUID id) {
		STATES.remove(id);
	}

	/** ENT_Player.Damage: any damage that lands restarts the heal delay. */
	public static void onDamaged(ServerPlayer player) {
		STATES.computeIfAbsent(player.getUUID(), k -> new State()).healTimer = 5.0;
	}

	/** Moves faster than this many blocks in one tick are teleports or respawns, not falls. */
	private static final double TELEPORT_BLOCKS_PER_TICK = 5.0;

	public static void tick(ServerPlayer player, boolean hanging) {
		State s = STATES.computeIfAbsent(player.getUUID(), k -> new State());
		ServerLevel level = player.level();
		if (player.isDeadOrDying() || player.isSpectator() || player.isCreative()) {
			s.falling = false;
			s.lastY = player.getY() / BLOCKS_PER_UNIT;
			s.lastPos = player.position();
			return;
		}

		// Hunger off: keep the bar full and saturated (natural regen is cancelled in FoodDataMixin).
		player.getFoodData().setFoodLevel(20);
		player.getFoodData().setSaturation(5.0f);

		// Regen
		s.healTimer -= DT;
		if (s.healTimer <= 0 && player.getHealth() < player.getMaxHealth()) {
			player.heal((float) (0.25 * SCALE * DT));
		}

		// Breath
		boolean underwater = player.isEyeInFluid(FluidTags.WATER) && !player.hasEffect(MobEffects.WATER_BREATHING)
				&& !player.hasEffect(MobEffects.CONDUIT_POWER) && !player.getAbilities().invulnerable;
		if (!underwater) {
			s.breath = Math.min(10, s.breath + DT * 5);
			s.breathHurtTimer = 0;
		} else {
			s.breath = Math.max(0, s.breath - DT);
			if (s.breath <= 0) {
				if (s.breathHurtTimer > 0) {
					s.breathHurtTimer -= DT;
				} else {
					s.breathHurtTimer = 1;
					player.hurtServer(level, player.damageSources().drown(), (float) (0.25 * SCALE));
				}
			}
		}
		player.setAirSupply((int) Math.round(player.getMaxAirSupply() * s.breath / 10.0));

		// Falling
		double y = player.getY() / BLOCKS_PER_UNIT;
		double vy = Double.isNaN(s.lastY) ? 0 : (y - s.lastY) / DT;
		boolean teleported = !Double.isNaN(s.lastY)
				&& (Math.abs(y - s.lastY) * BLOCKS_PER_UNIT > TELEPORT_BLOCKS_PER_TICK
				|| player.position().distanceTo(s.lastPos) > TELEPORT_BLOCKS_PER_TICK);
		s.lastY = y;
		s.lastPos = player.position();
		if (teleported) {
			// ResetFallingHeight(): a teleport or respawn starts a new fall measurement
			s.falling = false;
			s.fallHurtTime = 0;
			return;
		}
		boolean swimming = player.isInWater() || player.isInLava();
		if (vy < -3 && !hanging && !swimming) {
			if (!s.falling) {
				s.falling = true;
				s.fallStartY = y;
			}
			double d = Math.max(s.fallStartY - y, 0);
			if (d > MAX_FALL) {
				if (s.fallHurtTime > 0) {
					s.fallHurtTime -= DT;
				} else {
					s.fallHurtTime = 1;
					player.hurtServer(level, player.damageSources().fall(), (float) (0.1 * SCALE));
				}
				if (d > MAX_FALL * 4) player.hurtServer(level, player.damageSources().fall(), Float.MAX_VALUE);
			} else {
				s.fallHurtTime = 0;
			}
			return;
		}
		if (s.falling) {
			double d = Math.max(s.fallStartY - y, 0);
			double amount = Math.ceil((d - (MAX_FALL + 10)) / 15) + 1;
			if (d > MAX_FALL) {
				boolean grab = hanging, land = !grab && player.onGround();
				if (!grab && !land) amount /= 2;
				player.hurtServer(level, player.damageSources().fall(), (float) (amount * SCALE));
				if (ServerPlayNetworking.canSend(player, FallGripPayload.TYPE)) {
					ServerPlayNetworking.send(player, new FallGripPayload(grab, land));
				}
			}
		}
		s.falling = false;
	}
}
