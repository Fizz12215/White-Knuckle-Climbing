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
	/** Hide White Knuckle's hands; Minecraft's own arm and held item show instead. Off: WK's hands replace Minecraft's. */
	public static boolean hideWkHands = false;
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
		hideWkHands = Boolean.parseBoolean(p.getProperty("hide_wk_hands", Boolean.toString(hideWkHands)));
		fov = parse(p, "fov", fov);
		exactStepHeight = Boolean.parseBoolean(p.getProperty("exact_step_height", Boolean.toString(exactStepHeight)));
		lowHealthVolume = volumeFrom(p.getProperty("low_health_volume"), lowHealthVolume);
		// 0.5.0 halved the low-health loops by default; back to the game's level (the owner's call)
		if (p.getProperty("sound_volume") == null && lowHealthVolume == 0.5) lowHealthVolume = 1.0;
		soundVolume = volumeFrom(p.getProperty("sound_volume"), soundVolume);
		grabbingVolume = volumeFrom(p.getProperty("grabbing_volume"), grabbingVolume);
		landingVolume = volumeFrom(p.getProperty("landing_volume"), landingVolume);
		footstepsVolume = volumeFrom(p.getProperty("footsteps_volume"), footstepsVolume);
		windVolume = volumeFrom(p.getProperty("wind_volume"), windVolume);
		hurtVolume = volumeFrom(p.getProperty("hurt_volume"), hurtVolume);
		fatigueVolume = volumeFrom(p.getProperty("fatigue_volume"), fatigueVolume);
		iceVolume = volumeFrom(p.getProperty("ice_volume"), iceVolume);
		wkKeybindsApplied = Boolean.parseBoolean(p.getProperty("wk_keybinds_applied", Boolean.toString(wkKeybindsApplied)));
		assetPrompt = Boolean.parseBoolean(p.getProperty("asset_prompt", Boolean.toString(assetPrompt)));
		wkGameDir = gameDirFrom(text, p.getProperty("wk_game_dir", wkGameDir)).strip();
		save();
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
		values.put("hide_wk_hands", Boolean.toString(hideWkHands));
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

	/** A volume setting: a number from 0 to 2 (clamped); def if missing or not a number. */
	static double volumeFrom(String raw, double def) {
		if (raw == null) return def;
		try {
			double v = Double.parseDouble(raw.strip());
			return Double.isNaN(v) ? def : Math.clamp(v, 0.0, 2.0);
		} catch (NumberFormatException e) {
			return def;
		}
	}

	private static double parse(Properties p, String key, double def) {
		try {
			return Double.parseDouble(p.getProperty(key, Double.toString(def)));
		} catch (NumberFormatException e) {
			return def;
		}
	}
}
