package dev.fizz.wkclimb.client;

import dev.fizz.wkclimb.WKClimb;
import dev.fizz.wkclimb.sim.ClimbSim;
import dev.fizz.wkclimb.sim.WK;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * White Knuckle's player sound sets (ENT_Player.clipHandler). The audio itself comes from the
 * "White Knuckle Assets" resource pack made by tools/extract_wk_assets.py; without it, each sound
 * falls back to a vanilla one. Volumes are in the pack's sounds.json; pitch ranges are the sets'
 * minPitch/maxPitch.
 */
public final class ClimbSounds {
	private ClimbSounds() {}

	public record Sound(SoundEvent event, float minPitch, float maxPitch, SoundEvent fallback, float fallbackVolume) {}

	public static final Sound HANDHOLD = sound("climbing.handhold", 1.0f, 1.1f, SoundEvents.STONE_HIT, 0.4f);
	public static final Sound HANDHOLD_CHAIN = sound("climbing.handhold_chain", 1.0f, 1.1f, SoundEvents.CHAIN_HIT, 0.4f);
	/** PlayHandholdSound("metafungus"): fungus growths. Falls back to HANDHOLD, as the game does without the set. */
	public static final Sound HANDHOLD_METAFUNGUS = sound("climbing.handhold_metafungus", 1.0f, 1.1f, null, 0f);
	public static final Sound DROP = sound("climbing.drop", 0.99f, 1.1f, SoundEvents.STONE_STEP, 0.2f);
	public static final Sound FATIGUE_DROP = sound("fatigue.drop", 0.95f, 1.05f, SoundEvents.PLAYER_ATTACK_WEAK, 0.7f);
	public static final Sound JUMP = sound("movement.jump", 0.95f, 1.03f, null, 0f);
	public static final Sound MANTLE = sound("movement.mantle", 1.0f, 1.0f, null, 0f);
	public static final Sound LAND_SOFT = sound("movement.land_soft", 0.96f, 1.03f, null, 0f);
	public static final Sound LAND_MEDIUM = sound("movement.land_medium", 0.95f, 1.0f, null, 0f);
	public static final Sound LAND_HEAVY = sound("movement.land_heavy", 0.95f, 1.0f, null, 0f);
	public static final Sound ICY_SLIP = sound("icy.slip", 1.0f, 1.0f, SoundEvents.GLASS_STEP, 0.6f);
	public static final Sound ICY_SHAKE = sound("icy.shake", 1.0f, 1.0f, SoundEvents.GLASS_HIT, 0.4f);
	public static final Sound HURT = sound("player.hurt", 0.95f, 1.05f, null, 0f);
	public static final Sound HURT_FALLING = sound("player.hurt_falling", 0.95f, 1.05f, null, 0f);
	public static final Sound HURT_HEAT = sound("player.hurt_heat", 0.95f, 1.05f, null, 0f);
	public static final Sound DEATH = sound("player.death", 0.99f, 1.0f, null, 0f);
	public static final Sound FATIGUE_LEVEL = sound("fatigue.level", 0.98f, 1.0f, null, 0f);
	/** ENT_Player's looping AudioSources: windAudio (pitch 1.2), damageAudio and dyingAudio (pitch 1). */
	public static final Sound WIND = sound("movement.wind", 1.2f, 1.2f, null, 0f);
	public static final Sound HEARTBEAT = sound("player.heartbeat", 1.0f, 1.0f, null, 0f);
	public static final Sound MORTAL_WOUND = sound("player.mortal_wound", 1.0f, 1.0f, null, 0f);

	private static Sound sound(String path, float minPitch, float maxPitch, SoundEvent fallback, float fallbackVolume) {
		return new Sound(SoundEvent.createVariableRangeEvent(WKClimb.id(path)), minPitch, maxPitch, fallback, fallbackVolume);
	}

	/** FootstepAudio: one of the floor material's clips, at its volume (sounds.json) and pitch range. */
	public static void footstep(LocalPlayer player, ClimbFootsteps.Material material) {
		Sound s = new Sound(SoundEvent.createVariableRangeEvent(WKClimb.id("footstep." + material.footstep().name())),
				material.minPitch(), material.maxPitch(), null, 0f);
		play(player, s);
	}

	/** True when the extracted pack provides the footstep sounds, so vanilla step sounds are replaced. */
	public static boolean haveFootsteps() {
		return Minecraft.getInstance().getSoundManager().getSoundEvent(WKClimb.id("footstep.generic")) != null;
	}

	public static boolean available(Sound s) {
		return Minecraft.getInstance().getSoundManager().getSoundEvent(s.event().location()) != null;
	}

	private static String lastPlayed = "";

	/** Test support: the sound event the last one-shot played. */
	public static String lastPlayedForTest() {
		return lastPlayed;
	}

	/** Test support: volumes of the wind, heartbeat and mortal-wound loops (-1 when not playing). */
	public static float[] loopVolumesForTest() {
		return new float[] { loopVolume(wind), loopVolume(heartbeat), loopVolume(mortalWound) };
	}

	private static float loopVolume(Loop loop) {
		return playing(loop) ? loop.rawVolume() : -1;
	}

	public static void play(LocalPlayer player, Sound s) {
		RandomSource random = player.getRandom();
		float pitch = s.minPitch() + random.nextFloat() * (s.maxPitch() - s.minPitch());
		SoundEvent event = s.event();
		float volume = 1.0f;
		if (!available(s)) {
			if (s.fallback() == null) return;
			event = s.fallback();
			volume = s.fallbackVolume();
		}
		lastPlayed = event.location().toString();
		Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(event, SoundSource.PLAYERS, volume, pitch,
				random, player.getX(), player.getEyeY(), player.getZ()));
	}

	/** Lerp factors per 20 Hz tick for the game's per-frame / per-step volume lerps. */
	private static final float FATIGUE_RATE = 0.15f;      // Lerp(v, target, deltaTime * 3)
	private static final float WIND_RATE = 0.2316f;       // Lerp(v, target, fixedDeltaTime * 5) at 50 Hz
	private static final float HEALTH_LOOP_RATE = 0.0952f; // Lerp(v, target, deltaTime * 2)

	private static Loop fatigue, wind, heartbeat, mortalWound;
	/** WindSound(): set when a fall starts (vertical speed below -2), cleared on landing. */
	private static boolean fallingAudio;

	/**
	 * The "fatigue:level" loop: while hanging its volume eases toward
	 * (1 - lowestGrip / max - 0.5) * 2 (so it starts at half grip and is full when empty), else to 0.
	 * The set's maxVolume (0.35) is applied by sounds.json.
	 */
	public static void updateFatigue(LocalPlayer player, ClimbSim sim) {
		if (!available(FATIGUE_LEVEL)) return;
		double lowest = Math.min(sim.hands[0].grip(), sim.hands[1].grip());
		float target = sim.anyHanging() ? (float) Math.max(0, (1 - lowest / WK.GRIP_STRENGTH_MAX - 0.5) * 2) : 0f;
		if (!playing(fatigue)) {
			if (target <= 0.001f) return;
			fatigue = start(FATIGUE_LEVEL, player, FATIGUE_RATE, true);
		}
		fatigue.target = target;
	}

	/**
	 * ENT_Player.WindSound(): the falling wind, volume max((-vy - 2) * 0.03, 0) for the body's vertical
	 * speed vy (units/s). It restarts from the beginning when a fall starts and fades out after landing.
	 */
	public static void updateWind(LocalPlayer player, double verticalSpeed, boolean grounded, boolean sliding, boolean enabled) {
		if (!available(WIND)) return;
		boolean alive = enabled && !player.isDeadOrDying();
		if (alive && verticalSpeed < -2 && !fallingAudio) {
			fallingAudio = true;
			if (playing(wind)) wind.end();
			wind = start(WIND, player, WIND_RATE, true);
		} else if (fallingAudio && grounded && !sliding) {
			fallingAudio = false;
		}
		if (wind != null) wind.target = alive ? (float) Math.max((-verticalSpeed - 2) * 0.03, 0) : 0f;
	}

	/**
	 * ENT_Player.LateUpdate: the near-death loops follow health. damageAudio (heartbeat) eases toward
	 * damageFXCurve(health) * 0.8, dyingAudio (mortal wound) toward dyingAudioCurve(health) * 0.8, both
	 * times the low_health_volume setting (1 = the game's level).
	 * The two are layers of one piece, so they start and stop together and stay in step.
	 */
	public static void updateHealthLoops(LocalPlayer player, boolean enabled) {
		if (!available(HEARTBEAT) || !available(MORTAL_WOUND)) return;
		double health = player.getMaxHealth() > 0 ? player.getHealth() / player.getMaxHealth() : 1;
		double setting = dev.fizz.wkclimb.ClimbConfig.lowHealthVolume;
		float heartTarget = enabled ? (float) (ClimbDamage.damageFxCurve(health) * 0.8 * setting) : 0f;
		float woundTarget = enabled ? (float) (ClimbDamage.dyingAudioCurve(health) * 0.8 * setting) : 0f;
		if (!playing(heartbeat) || !playing(mortalWound)) {
			if (heartTarget <= 0.001f && woundTarget <= 0.001f) return;
			if (heartbeat != null) heartbeat.end();
			if (mortalWound != null) mortalWound.end();
			heartbeat = start(HEARTBEAT, player, HEALTH_LOOP_RATE, false);
			mortalWound = start(MORTAL_WOUND, player, HEALTH_LOOP_RATE, false);
		}
		heartbeat.target = heartTarget;
		mortalWound.target = woundTarget;
		if (heartTarget <= 0.001f && woundTarget <= 0.001f && heartbeat.quiet() && mortalWound.quiet()) {
			heartbeat.end();
			mortalWound.end();
		}
	}

	private static boolean playing(Loop loop) {
		return loop != null && !loop.isStopped() && Minecraft.getInstance().getSoundManager().isActive(loop);
	}

	private static Loop start(Sound sound, LocalPlayer player, float rate, boolean stopWhenQuiet) {
		Loop loop = new Loop(sound, player, rate, stopWhenQuiet);
		Minecraft.getInstance().getSoundManager().play(loop);
		return loop;
	}

	/** A looping, listener-relative sound whose volume eases toward a target each tick. */
	private static final class Loop extends AbstractTickableSoundInstance {
		private final LocalPlayer player;
		private final float rate;
		private final boolean stopWhenQuiet;
		float target;

		Loop(Sound sound, LocalPlayer player, float rate, boolean stopWhenQuiet) {
			super(sound.event(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
			this.player = player;
			this.rate = rate;
			this.stopWhenQuiet = stopWhenQuiet;
			this.looping = true;
			this.delay = 0;
			this.volume = 0.01f;
			this.pitch = sound.minPitch();
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
		}

		boolean quiet() {
			return volume < 0.005f;
		}

		float rawVolume() {
			return volume;
		}

		void end() {
			stop();
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}

		@Override
		public void tick() {
			volume += (target - volume) * rate;
			if (player.isRemoved() || (stopWhenQuiet && target <= 0.001f && quiet())) stop();
		}
	}
}
