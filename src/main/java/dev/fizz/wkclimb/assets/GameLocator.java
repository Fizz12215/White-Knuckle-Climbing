package dev.fizz.wkclimb.assets;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Finds White Knuckle the way the extractor does (steam_roots / steam_libraries / find_game_dir in
 * tools/extract_wk_assets.py): the usual Steam folders, every library listed in their
 * libraryfolders.vdf, and on Windows the SteamPath from the registry.
 */
public final class GameLocator {
	/** Where Steam itself usually lives, as in the extractor's STEAM_ROOTS. */
	static final List<String> STEAM_ROOTS = List.of(
			"~/.local/share/Steam",
			"~/.steam/steam",
			"~/.steam/root",
			"~/.var/app/com.valvesoftware.Steam/.local/share/Steam", // Flatpak Steam
			"~/snap/steam/common/.local/share/Steam",                  // Snap Steam
			"C:/Program Files (x86)/Steam",
			"C:/Program Files/Steam");
	static final String GAME_FOLDER = "White Knuckle";
	static final String DATA_FOLDER = "White Knuckle_Data";

	private GameLocator() {}

	/**
	 * The White Knuckle install folder (the one holding White Knuckle_Data), or empty.
	 * @param configured wk_game_dir from the config; when set, only that folder is tried
	 */
	public static Optional<Path> find(String configured) {
		return search(configured).map(GameLocator::asOnDisk);
	}

	private static Optional<Path> search(String configured) {
		if (configured != null && !configured.isBlank()) return gameDir(expand(configured.trim(), home()));
		boolean windows = isWindows();
		String registry = windows ? registrySteamPath() : null;
		List<Path> candidates = new ArrayList<>();
		for (Path lib : steamLibraries(steamRoots(home(), registry))) {
			candidates.add(lib.resolve("steamapps").resolve("common").resolve(GAME_FOLDER));
		}
		for (Path c : candidates) {
			Optional<Path> game = gameDir(c);
			if (game.isPresent()) return game;
		}
		return Optional.empty();
	}

	/**
	 * A folder the player picked: White Knuckle's install folder, its _Data folder, or a folder that
	 * holds it (steamapps/common, or steamapps).
	 */
	public static Optional<Path> fromChosenFolder(Path chosen) {
		for (Path p : List.of(chosen, chosen.resolve(GAME_FOLDER), chosen.resolve("common").resolve(GAME_FOLDER))) {
			Optional<Path> game = gameDir(p);
			if (game.isPresent()) return game.map(GameLocator::asOnDisk);
		}
		return Optional.empty();
	}

	/**
	 * The folder as the file system spells it. Steam's registry SteamPath is all lower case with
	 * forward slashes ("c:/program files (x86)/steam"), and Windows paths compare case-insensitively,
	 * so a found folder would otherwise be shown in that spelling.
	 */
	static Path asOnDisk(Path p) {
		try {
			return p.toRealPath();
		} catch (IOException | SecurityException e) {
			return p;
		}
	}

	/** Where the folder pickers start: the first Steam library's steamapps/common that exists. */
	public static Optional<Path> pickerStart() {
		String registry = isWindows() ? registrySteamPath() : null;
		for (Path lib : steamLibraries(steamRoots(home(), registry))) {
			Path common = lib.resolve("steamapps").resolve("common");
			if (Files.isDirectory(common)) return Optional.of(common);
		}
		return Optional.empty();
	}

	/** The install folder if this is White Knuckle's install folder or its _Data folder. */
	static Optional<Path> gameDir(Path p) {
		if (Files.exists(p.resolve(DATA_FOLDER).resolve("level3"))) return Optional.of(p);
		if (p.getFileName() != null && p.getFileName().toString().equals(DATA_FOLDER) && Files.exists(p.resolve("level3"))) {
			return Optional.of(p.getParent());
		}
		return Optional.empty();
	}

	static List<Path> steamRoots(Path home, String registrySteamPath) {
		List<Path> roots = new ArrayList<>();
		if (registrySteamPath != null && !registrySteamPath.isBlank()) roots.add(Path.of(registrySteamPath));
		for (String r : STEAM_ROOTS) roots.add(expand(r, home));
		return roots;
	}

	/** Every Steam library folder: the existing roots plus the paths in their libraryfolders.vdf. */
	static List<Path> steamLibraries(List<Path> roots) {
		List<Path> libraries = new ArrayList<>();
		for (Path root : roots) {
			if (!Files.isDirectory(root)) continue;
			addUnique(libraries, root);
			Path vdf = root.resolve("steamapps").resolve("libraryfolders.vdf");
			try {
				// decoded leniently (bad bytes become U+FFFD), like the extractor's errors="replace"
				for (Path lib : libraryPaths(new String(Files.readAllBytes(vdf), StandardCharsets.UTF_8))) addUnique(libraries, lib);
			} catch (IOException e) {
				// no vdf: just the root
			}
		}
		return libraries;
	}

	/** The "path" entries of a libraryfolders.vdf, whose lines look like:  "path"    "D:\\SteamLibrary" */
	static List<Path> libraryPaths(String vdf) {
		List<Path> paths = new ArrayList<>();
		for (String line : vdf.split("\\R")) {
			String[] parts = line.strip().split("\"");
			if (parts.length >= 4 && parts[1].equals("path")) {
				try {
					paths.add(Path.of(parts[3].replace("\\\\", "\\")));
				} catch (java.nio.file.InvalidPathException e) {
					// a Windows path read on Linux, or garbage: skip
				}
			}
		}
		return paths;
	}

	/** The SteamPath value from `reg query HKCU\Software\Valve\Steam /v SteamPath` output. */
	static String parseRegQuery(String output) {
		for (String line : output.split("\\R")) {
			String t = line.strip();
			int i = t.indexOf("REG_SZ");
			if (t.startsWith("SteamPath") && i > 0) return t.substring(i + "REG_SZ".length()).strip();
		}
		return null;
	}

	static String registrySteamPath() {
		try {
			Process p = new ProcessBuilder("reg", "query", "HKCU\\Software\\Valve\\Steam", "/v", "SteamPath")
					.redirectErrorStream(true).start();
			String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			if (!p.waitFor(5, TimeUnit.SECONDS)) p.destroyForcibly();
			return parseRegQuery(out);
		} catch (IOException e) {
			return null;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}
	}

	/**
	 * The Flatpak app id (e.g. org.prismlauncher.PrismLauncher) when Minecraft runs inside a Flatpak
	 * sandbox, which can't see the Steam folder unless the user allows it.
	 */
	public static Optional<String> flatpakAppId() {
		return flatpakAppId(Path.of("/.flatpak-info"));
	}

	static Optional<String> flatpakAppId(Path info) {
		if (!Files.isRegularFile(info)) return Optional.empty();
		try {
			boolean inApplication = false;
			for (String line : Files.readAllLines(info, StandardCharsets.UTF_8)) {
				String t = line.strip();
				if (t.startsWith("[")) inApplication = t.equals("[Application]");
				else if (inApplication && t.startsWith("name=")) return Optional.of(t.substring(5).strip());
			}
		} catch (IOException e) {
			// unreadable: still a sandbox
		}
		return Optional.of("");
	}

	/** White Knuckle in the default Steam library, as flatpak writes it ("~/" is expanded by flatpak). */
	static final String FLATPAK_GAME_PATH = "~/.local/share/Steam/steamapps/common/White Knuckle";

	/**
	 * The fallback command that lets a Flatpak app read White Knuckle's folder. Only that folder: the
	 * whole Steam folder would also expose Steam's login and settings files to every mod.
	 */
	public static String flatpakOverrideCommand(String appId) {
		return "flatpak override --user --filesystem=\"" + FLATPAK_GAME_PATH + ":ro\" " + appOrPlaceholder(appId);
	}

	/** Takes that access away again. */
	public static String flatpakUndoCommand(String appId) {
		return "flatpak override --user --nofilesystem=\"" + FLATPAK_GAME_PATH + "\" " + appOrPlaceholder(appId);
	}

	/**
	 * The document id in a path the Flatpak document portal handed out (/run/user/<uid>/doc/<id>/...),
	 * for `flatpak document-unexport --doc-id=<id>`.
	 */
	public static Optional<String> documentPortalId(Path p) {
		String s = p.toString().replace('\\', '/');
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("^/run/(?:user/\\d+|flatpak)/doc/([^/]+)/").matcher(s + "/");
		return m.find() ? Optional.of(m.group(1)) : Optional.empty();
	}

	private static String appOrPlaceholder(String appId) {
		return appId.isEmpty() ? "<launcher app id>" : appId;
	}

	static Path expand(String path, Path home) {
		if (path.equals("~")) return home;
		if (path.startsWith("~/")) return home.resolve(path.substring(2));
		return Path.of(path);
	}

	private static void addUnique(List<Path> list, Path p) {
		if (!list.contains(p)) list.add(p);
	}

	static Path home() {
		return Path.of(System.getProperty("user.home"));
	}

	public static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows");
	}
}
