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
		Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(event, SoundSource.PLAYERS, volume, pitch,
				random, player.getX(), player.getEyeY(), player.getZ()));
	}

	private static FatigueLoop fatigue;

	/**
	 * The "fatigue:level" loop: while hanging its volume eases toward
	 * (1 - lowestGrip / max - 0.5) * 2 (so it starts at half grip and is full when empty), else to 0.
	 * The set's maxVolume (0.35) is applied by sounds.json.
	 */
	public static void updateFatigue(LocalPlayer player, ClimbSim sim) {
		if (!available(FATIGUE_LEVEL)) return;
		double lowest = Math.min(sim.hands[0].grip(), sim.hands[1].grip());
		float target = sim.anyHanging() ? (float) Math.max(0, (1 - lowest / WK.GRIP_STRENGTH_MAX - 0.5) * 2) : 0f;
		var manager = Minecraft.getInstance().getSoundManager();
		if (fatigue == null || fatigue.isStopped() || !manager.isActive(fatigue)) {
			if (target <= 0.001f) return;
			fatigue = new FatigueLoop(player);
			manager.play(fatigue);
		}
		fatigue.target = target;
	}

	private static final class FatigueLoop extends AbstractTickableSoundInstance {
		private final LocalPlayer player;
		float target;

		FatigueLoop(LocalPlayer player) {
			super(FATIGUE_LEVEL.event(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
			this.player = player;
			this.looping = true;
			this.delay = 0;
			this.volume = 0.01f;
			this.pitch = 0.99f;
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}

		@Override
		public void tick() {
			// Mathf.Lerp(current, target, deltaTime * 3) per frame, approximated per 20 Hz tick.
			volume += (target - volume) * 0.15f;
			if (player.isRemoved() || (target <= 0.001f && volume < 0.01f)) stop();
		}
	}
}
