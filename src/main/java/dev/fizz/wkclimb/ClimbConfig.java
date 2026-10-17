package dev.fizz.wkclimb;

import dev.fizz.wkclimb.assets.GameLocator;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** config/wkclimb.properties */
public final class ClimbConfig {
	private static final Logger LOG = LoggerFactory.getLogger("wkclimb");

	/** Minecraft blocks per White Knuckle unit. WK's capsule is 2.25 units tall, Steve is 1.8 blocks: 0.8. */
	public static double blocksPerUnit = 0.8;
	/** Multiplier on White Knuckle's 2.5-unit reach (2 blocks at the default scale). */
	public static double reachMultiplier = 1.0;
	/** false: every solid block is a handhold (like WK's "grab anything"). true: only #wkclimb:handholds. */
	public static boolean handholdsOnly = false;
	/** Start the game with climbing hands enabled. */
	public static boolean enabledOnStart = true;
	/** Only the top edges of blocks (ledges) can be grabbed, not the sides. #wkclimb:grab_anywhere is exempt. */
	public static boolean ledgesOnly = true;
	/**
	 * First-person hands: false = White Knuckle's (the pack's sprites), true = two Minecraft arms in your
	 * skin, moving the same way. Saved as hands=wk|minecraft. Without the pack, Minecraft's are used anyway.
	 */
	public static boolean minecraftHands = false;
	/** Set once the player has picked their hands in the first-time setup. */
	public static boolean handsChosen = false;
	/** Field of view while the hands are on: White Knuckle's default playerFOV (vertical, like Minecraft's). 0 = use Minecraft's setting. */
	public static double fov = 90;
	/** White Knuckle's 0.18-block step height in climbing mode (slabs and stairs must be jumped); else Minecraft's 0.6. */
	public static boolean exactStepHeight = false;
	/**
	 * Volume of all the mod's sounds, then of each group (Sounds menu): 1 = White Knuckle's level, 0 = off,
	 * up to 2 = twice as loud (Minecraft caps each sound at full volume).
	 */
	public static double soundVolume = 1.0;
	public static double grabbingVolume = 1.0;
	public static double landingVolume = 1.0;
	public static double footstepsVolume = 1.0;
	public static double windVolume = 1.0;
	public static double hurtVolume = 1.0;
	/** The low-health heartbeat and mortal-wound loops. */
	public static double lowHealthVolume = 1.0;
	public static double fatigueVolume = 1.0;
	public static double iceVolume = 1.0;
	/**
	 * How much louder than the game each group can really get: Minecraft caps every sound at full volume
	 * (instance volume x sounds.json volume), so the max is 1 / the loudest it plays at normally, up to 2x.
	 * Grabbing (json 0.2 at most), landings (0.5), footsteps (0.3), hurt (0.5): 2x. Fatigue: the slip-off
	 * is 0.65, so 1.5x. Wind (about 0.8 on long falls) and the low-health loops (0.8 at 0 health): 1.25x.
	 * Ice: json 1.0, so it can only go quieter.
	 */
	public static final double MAX_SOUND = 2.0, MAX_GRABBING = 2.0, MAX_LANDING = 2.0, MAX_FOOTSTEPS = 2.0,
			MAX_WIND = 1.25, MAX_HURT = 2.0, MAX_LOW_HEALTH = 1.25, MAX_FATIGUE = 1.5, MAX_ICE = 1.0;
	/** Set once White Knuckle's default key bindings have been applied (set false to apply them again). */
	public static boolean wkKeybindsApplied = false;
	/** Offer to build White Knuckle's hands and sounds when the pack is missing or out of date (each step is confirmed). */
	public static boolean assetPrompt = true;
	/** White Knuckle's install folder, if it isn't in the usual Steam libraries (set by the folder picker). Empty = search. */
	public static String wkGameDir = "";

	private ClimbConfig() {}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("wkclimb.properties");
		Properties p = new Properties();
		String text = "";
		if (Files.exists(path)) {
			try (Reader r = Files.newBufferedReader(path)) {
				text = Files.readString(path);
				p.load(r);
			} catch (IOException e) {
				LOG.warn("Could not read {}", path, e);
			}
		}
		blocksPerUnit = parse(p, "blocks_per_unit", blocksPerUnit);
		reachMultiplier = parse(p, "reach_multiplier", reachMultiplier);
		handholdsOnly = Boolean.parseBoolean(p.getProperty("handholds_only", Boolean.toString(handholdsOnly)));
		enabledOnStart = Boolean.parseBoolean(p.getProperty("enabled_on_start", Boolean.toString(enabledOnStart)));
		ledgesOnly = Boolean.parseBoolean(p.getProperty("ledges_only", Boolean.toString(ledgesOnly)));
		minecraftHands = minecraftHandsFrom(p.getProperty("hands"), p.getProperty("hide_wk_hands"), minecraftHands);
		handsChosen = Boolean.parseBoolean(p.getProperty("hands_chosen", Boolean.toString(handsChosen)));
		fov = parse(p, "fov", fov);
		exactStepHeight = Boolean.parseBoolean(p.getProperty("exact_step_height", Boolean.toString(exactStepHeight)));
		lowHealthVolume = volumeFrom(p.getProperty("low_health_volume"), lowHealthVolume, MAX_LOW_HEALTH);
		// 0.5.0 halved the low-health loops by default; back to the game's level (the owner's call)
		if (p.getProperty("sound_volume") == null && lowHealthVolume == 0.5) lowHealthVolume = 1.0;
		soundVolume = volumeFrom(p.getProperty("sound_volume"), soundVolume, MAX_SOUND);
		grabbingVolume = volumeFrom(p.getProperty("grabbing_volume"), grabbingVolume, MAX_GRABBING);
		landingVolume = volumeFrom(p.getProperty("landing_volume"), landingVolume, MAX_LANDING);
		footstepsVolume = volumeFrom(p.getProperty("footsteps_volume"), footstepsVolume, MAX_FOOTSTEPS);
		windVolume = volumeFrom(p.getProperty("wind_volume"), windVolume, MAX_WIND);
		hurtVolume = volumeFrom(p.getProperty("hurt_volume"), hurtVolume, MAX_HURT);
		fatigueVolume = volumeFrom(p.getProperty("fatigue_volume"), fatigueVolume, MAX_FATIGUE);
		iceVolume = volumeFrom(p.getProperty("ice_volume"), iceVolume, MAX_ICE);
		wkKeybindsApplied = Boolean.parseBoolean(p.getProperty("wk_keybinds_applied", Boolean.toString(wkKeybindsApplied)));
		assetPrompt = Boolean.parseBoolean(p.getProperty("asset_prompt", Boolean.toString(assetPrompt)));
		wkGameDir = gameDirFrom(text, p.getProperty("wk_game_dir", wkGameDir)).strip();
		save();
	}

	/** hands=wk|minecraft; before it existed, hide_wk_hands=true showed Minecraft's arm, so it means Minecraft hands. */
	static boolean minecraftHandsFrom(String hands, String hideWkHands, boolean current) {
		if (hands != null) return hands.strip().equalsIgnoreCase("minecraft");
		if (hideWkHands != null) return Boolean.parseBoolean(hideWkHands.strip());
		return current;
	}

	/**
	 * wk_game_dir as the player wrote it. A .properties file treats a backslash as an escape, which would
	 * eat a Windows path typed with backslashes (C:\Games\White Knuckle becomes C:GamesWhite Knuckle),
	 * so the raw line is used, unless it's in the escaped form older versions saved (C\:\\Games...).
	 */
	static String gameDirFrom(String fileText, String parsed) {
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^[ \\t]*wk_game_dir[ \\t]*[=:][ \\t]*(.*?)[ \\t]*\\r?$").matcher(fileText);
		if (!m.find()) return parsed;
		String raw = m.group(1);
		return raw.contains("\\\\") || raw.contains("\\:") ? parsed : raw;
	}

	public static void save() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("wkclimb.properties");
		// Written by hand rather than with Properties.store: a stable order, and paths readable as
		// written (store would turn C:/Games into C\:/Games). On Windows, wk_game_dir is saved with /.
		Map<String, String> values = new LinkedHashMap<>();
		values.put("blocks_per_unit", Double.toString(blocksPerUnit));
		values.put("reach_multiplier", Double.toString(reachMultiplier));
		values.put("handholds_only", Boolean.toString(handholdsOnly));
		values.put("enabled_on_start", Boolean.toString(enabledOnStart));
		values.put("ledges_only", Boolean.toString(ledgesOnly));
		values.put("hands", minecraftHands ? "minecraft" : "wk");
		values.put("hands_chosen", Boolean.toString(handsChosen));
		values.put("fov", Double.toString(fov));
		values.put("exact_step_height", Boolean.toString(exactStepHeight));
		values.put("sound_volume", Double.toString(soundVolume));
		values.put("grabbing_volume", Double.toString(grabbingVolume));
		values.put("landing_volume", Double.toString(landingVolume));
		values.put("footsteps_volume", Double.toString(footstepsVolume));
		values.put("wind_volume", Double.toString(windVolume));
		values.put("hurt_volume", Double.toString(hurtVolume));
		values.put("low_health_volume", Double.toString(lowHealthVolume));
		values.put("fatigue_volume", Double.toString(fatigueVolume));
		values.put("ice_volume", Double.toString(iceVolume));
		values.put("wk_keybinds_applied", Boolean.toString(wkKeybindsApplied));
		values.put("asset_prompt", Boolean.toString(assetPrompt));
		values.put("wk_game_dir", GameLocator.isWindows() ? wkGameDir.replace('\\', '/') : wkGameDir);
		try (Writer w = Files.newBufferedWriter(path)) {
			w.write("# White Knuckle Climbing (README.md, \"Settings\", explains each one)\n");
			for (Map.Entry<String, String> e : values.entrySet()) w.write(e.getKey() + "=" + e.getValue() + "\n");
		} catch (IOException e) {
			LOG.warn("Could not write {}", path, e);
		}
	}

	/** A volume setting: a number from 0 to max (clamped); def if missing or not a number. */
	static double volumeFrom(String raw, double def, double max) {
		if (raw == null) return def;
		try {
			double v = Double.parseDouble(raw.strip());
			return Double.isNaN(v) ? def : Math.clamp(v, 0.0, max);
		} catch (NumberFormatException e) {
			return def;
		}
	}

	/**
	 * The Sounds menu's "plus or minus" slider, from -100 (off) through 0 (the game's volume) to 100 (max),
	 * or to 0 when max is 1 (it can only go quieter). Left half: off to normal; right half: normal to max.
	 */
	public static double sliderToVolume(int pos, double max) {
		return pos <= 0 ? 1 + pos / 100.0 : 1 + pos / 100.0 * (max - 1);
	}

	public static int volumeToSlider(double volume, double max) {
		if (volume <= 1 || max <= 1) return (int) Math.round((Math.min(volume, 1) - 1) * 100);
		return (int) Math.round(Math.min((volume - 1) / (max - 1), 1) * 100);
	}

	private static double parse(Properties p, String key, double def) {
		try {
			return Double.parseDouble(p.getProperty(key, Double.toString(def)));
		} catch (NumberFormatException e) {
			return def;
		}
	}
}
