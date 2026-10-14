package dev.fizz.wkclimb;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
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
	/** Set once White Knuckle's default key bindings have been applied (set false to apply them again). */
	public static boolean wkKeybindsApplied = false;

	private ClimbConfig() {}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("wkclimb.properties");
		Properties p = new Properties();
		if (Files.exists(path)) {
			try (Reader r = Files.newBufferedReader(path)) {
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
		wkKeybindsApplied = Boolean.parseBoolean(p.getProperty("wk_keybinds_applied", Boolean.toString(wkKeybindsApplied)));
		save();
	}

	public static void save() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("wkclimb.properties");
		Properties p = new Properties();

		p.setProperty("blocks_per_unit", Double.toString(blocksPerUnit));
		p.setProperty("reach_multiplier", Double.toString(reachMultiplier));
		p.setProperty("handholds_only", Boolean.toString(handholdsOnly));
		p.setProperty("enabled_on_start", Boolean.toString(enabledOnStart));
		p.setProperty("ledges_only", Boolean.toString(ledgesOnly));
		p.setProperty("hide_vanilla_hand", Boolean.toString(hideVanillaHand));
		p.setProperty("fov", Double.toString(fov));
		p.setProperty("wk_keybinds_applied", Boolean.toString(wkKeybindsApplied));
		try (Writer w = Files.newBufferedWriter(path)) {
			p.store(w, "White Knuckle Climbing");
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
