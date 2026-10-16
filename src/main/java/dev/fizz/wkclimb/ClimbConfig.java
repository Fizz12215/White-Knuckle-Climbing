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
	/** Hide Minecraft's own arm and held item while climbing hands are on. */
	public static boolean hideVanillaHand = true;
	/** Field of view while the hands are on: White Knuckle's default playerFOV (vertical, like Minecraft's). 0 = use Minecraft's setting. */
	public static double fov = 90;
	/** White Knuckle's 0.18-block step height in climbing mode (slabs and stairs must be jumped); else Minecraft's 0.6. */
	public static boolean exactStepHeight = false;
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
		hideVanillaHand = Boolean.parseBoolean(p.getProperty("hide_vanilla_hand", Boolean.toString(hideVanillaHand)));
		fov = parse(p, "fov", fov);
		exactStepHeight = Boolean.parseBoolean(p.getProperty("exact_step_height", Boolean.toString(exactStepHeight)));
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
		values.put("hide_vanilla_hand", Boolean.toString(hideVanillaHand));
		values.put("fov", Double.toString(fov));
		values.put("exact_step_height", Boolean.toString(exactStepHeight));
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

	private static double parse(Properties p, String key, double def) {
		try {
			return Double.parseDouble(p.getProperty(key, Double.toString(def)));
		} catch (NumberFormatException e) {
			return def;
		}
	}
}
