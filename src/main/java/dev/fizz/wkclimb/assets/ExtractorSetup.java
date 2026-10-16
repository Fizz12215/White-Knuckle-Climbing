package dev.fizz.wkclimb.assets;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Where and how the mod sets up the Python extractor. It follows beta/get-white-knuckle-assets.sh and
 * "Get White Knuckle Assets (Windows).bat", so the mod and the scripts share one private Python folder.
 */
public final class ExtractorSetup {
	/** The pack contents this mod expects; equals PACK_CONTENT_VERSION in tools/extract_wk_assets.py. */
	public static final int PACK_VERSION = 2;
	/** Python versions the extractor's libraries ship ready-made for, in the scripts' order of preference. */
	static final List<String> VERSIONS = List.of("3.13", "3.12", "3.14", "3.11", "3.10");
	/** Prints the version, then the real interpreter path (py.exe hands over to python.exe). */
	static final String PROBE = "import sys; print('%d.%d' % sys.version_info[:2]); print(sys.executable)";

	public record Python(String version, Path executable) {}

	private ExtractorSetup() {}

	/** The private Python folder: %LOCALAPPDATA%\wkclimb-extractor, or $XDG_DATA_HOME/wkclimb-extractor. */
	public static Path venvDir(boolean windows, Map<String, String> env, Path home) {
		Path base;
		if (windows) {
			String local = env.get("LOCALAPPDATA");
			base = local != null && !local.isBlank() ? Path.of(local) : home.resolve("AppData").resolve("Local");
		} else {
			String xdg = env.get("XDG_DATA_HOME");
			base = xdg != null && !xdg.isBlank() ? Path.of(xdg) : home.resolve(".local").resolve("share");
		}
		return base.resolve("wkclimb-extractor");
	}

	public static Path venvPython(Path venv, boolean windows) {
		return windows ? venv.resolve("Scripts").resolve("python.exe") : venv.resolve("bin").resolve("python");
	}

	/** The scripts' "setup finished" marker for this Python version. */
	public static Path marker(Path venv, String version, boolean windows) {
		return venv.resolve("setup-ok-" + version + (windows ? ".txt" : ""));
	}

	/**
	 * Rough sizes, for telling the player before anything happens: the download (pip download of the
	 * pinned wheels) and the private folder once set up (Linux, Python 3.14: 183 MB; Windows 11,
	 * Python 3.13: 126 MB; measured 2026-10-16).
	 */
	public static final int DOWNLOAD_MB_WINDOWS = 35, DOWNLOAD_MB_LINUX = 45, INSTALLED_MB_WINDOWS = 130, INSTALLED_MB_LINUX = 185;

	/** The setup steps a build still needs before it can run the extractor. */
	public enum SetupStep { CREATE_VENV, INSTALL_LIBRARIES }

	/**
	 * Which setup steps are needed, from the marker (null if missing) and whether the private folder's
	 * python exists. A current marker needs nothing; a stale one (other requirements, or written by the
	 * beta scripts) reinstalls the libraries; no marker or no python makes the folder from scratch.
	 */
	public static List<SetupStep> stepsNeeded(String marker, String expectedMarker, boolean venvPythonExists) {
		if (expectedMarker.equals(marker) && venvPythonExists) return List.of();
		if (marker != null && venvPythonExists) return List.of(SetupStep.INSTALL_LIBRARIES);
		return List.of(SetupStep.CREATE_VENV, SetupStep.INSTALL_LIBRARIES);
	}

	/** What the mod writes into the marker: which requirements.txt was installed. */
	public static String markerText(byte[] requirements) {
		try {
			byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(requirements);
			return "requirements " + java.util.HexFormat.of().formatHex(d, 0, 8);
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Interpreters to try, most preferred first. On Windows there's deliberately no bare "python": that
	 * name can be the Microsoft Store's Python, whose writes to AppData are silently moved elsewhere, so
	 * the private folder would end up where nothing can find it. The beta .bat avoids it the same way.
	 */
	static List<List<String>> candidates(boolean windows, Map<String, String> env) {
		List<List<String>> out = new ArrayList<>();
		if (windows) {
			String local = env.getOrDefault("LOCALAPPDATA", "");
			String programFiles = env.getOrDefault("ProgramFiles", "");
			for (String v : VERSIONS) {
				String folder = "Python" + v.replace(".", "");
				out.add(List.of("py", "-" + v));
				// Where python.org's installer (also used by winget) puts Python, for a per-user or an
				// all-users install: usable straight after installing, before this process sees a new PATH.
				if (!local.isBlank()) {
					out.add(List.of(Path.of(local, "Programs", "Python", "Launcher", "py.exe").toString(), "-" + v));
					out.add(List.of(Path.of(local, "Programs", "Python", folder, "python.exe").toString()));
				}
				if (!programFiles.isBlank()) out.add(List.of(Path.of(programFiles, folder, "python.exe").toString()));
			}
		} else {
			for (String v : VERSIONS) out.add(List.of("python" + v));
			out.add(List.of("python3"));
		}
		return out;
	}

	/** The probe's output (version line, then path line), if it's a version the extractor supports. */
	static Optional<Python> parseProbe(String output) {
		String[] lines = output.strip().split("\\R");
		if (lines.length < 2) return Optional.empty();
		String version = lines[0].strip();
		String exe = lines[lines.length - 1].strip();
		if (!VERSIONS.contains(version) || exe.isEmpty()) return Optional.empty();
		try {
			return Optional.of(new Python(version, Path.of(exe)));
		} catch (java.nio.file.InvalidPathException e) {
			return Optional.empty();
		}
	}

	/** The first supported Python on this computer, in the scripts' order. */
	public static Optional<Python> findPython(boolean windows, Map<String, String> env) {
		for (List<String> candidate : candidates(windows, env)) {
			List<String> cmd = new ArrayList<>(candidate);
			cmd.addAll(List.of("-I", "-c", PROBE));
			try {
				Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
				p.getOutputStream().close();
				// The output is two short lines, so waiting before reading can't fill the pipe.
				if (!p.waitFor(15, TimeUnit.SECONDS)) {
					p.destroyForcibly();
					continue;
				}
				if (p.exitValue() != 0) continue;
				Optional<Python> found = parseProbe(new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
				if (found.isPresent()) return found;
			} catch (IOException e) {
				// not installed under this name
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return Optional.empty();
			}
		}
		return Optional.empty();
	}
}
