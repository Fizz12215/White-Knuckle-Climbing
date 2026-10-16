package dev.fizz.wkclimb.client.assets;

import dev.fizz.wkclimb.ClimbConfig;
import dev.fizz.wkclimb.assets.ExtractorSetup;
import dev.fizz.wkclimb.assets.GameLocator;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Builds the White Knuckle Assets pack from the player's own copy of the game by running the bundled
 * extractor (tools/extract_wk_assets.py, packed into the jar under wkclimb/extractor/), on a background
 * thread. It first finds White Knuckle (GameLocator) and a Python 3.10 - 3.14 (ExtractorSetup), works
 * out which steps are needed, and then waits for the player's OK (state CONFIRM) before each one:
 *  1. CREATE_VENV, 2. INSTALL_LIBRARIES: the private Python folder, in the same place the beta scripts
 *     use (so a setup made by either is reused), and the pinned libraries from requirements.txt;
 *  3. BUILD_PACK: the extractor, writing straight into Minecraft's resourcepacks folder;
 *  4. ENABLE_PACK: switch the pack on (client thread).
 * Everything the tools print goes to logs/wkclimb-assets.log.
 */
public final class AssetBuilder {
	public static final AssetBuilder INSTANCE = new AssetBuilder();
	private static final Logger LOG = LoggerFactory.getLogger("wkclimb");
	private static final int TAIL_LINES = 8;

	public enum State {
		IDLE,
		/** Finding the game and Python, working out the steps. */
		CHECKING,
		/** Waiting for the player's OK for {@link #step()}. */
		CONFIRM,
		/** Doing {@link #step()}. */
		RUNNING,
		DONE,
		FAILED,
		/** No Python 3.10 - 3.14 found. */
		NO_PYTHON,
		/** Python is there but can't make a venv (Debian/Ubuntu without python3-venv). */
		NO_VENV,
		/** White Knuckle wasn't found. */
		NO_GAME;

		/** A build is under way (possibly waiting for the player). */
		public boolean active() {
			return this == CHECKING || this == CONFIRM || this == RUNNING;
		}
	}

	public enum Step { CREATE_VENV, INSTALL_LIBRARIES, BUILD_PACK, ENABLE_PACK }

	/** What a build will touch, for the confirmation screens. */
	public record Plan(Path game, ExtractorSetup.Python python, Path venv, Path pack, List<Step> steps) {}

	private volatile State state = State.IDLE;
	private volatile Plan plan;
	private volatile int stepIndex;
	private volatile String lastLine = "";
	private final Deque<String> tail = new ArrayDeque<>();
	private volatile Process process;
	private volatile boolean cancelled;
	private volatile CompletableFuture<Boolean> confirmation;
	private Consumer<State> onFinished = s -> {};

	private AssetBuilder() {}

	public State state() {
		return state;
	}

	public Plan plan() {
		return plan;
	}

	/** The step being confirmed or run. */
	public Step step() {
		Plan p = plan;
		return p == null ? null : p.steps().get(stepIndex);
	}

	public int stepNumber() {
		return stepIndex + 1;
	}

	public String lastLine() {
		return lastLine;
	}

	public synchronized List<String> tail() {
		return new ArrayList<>(tail);
	}

	public static Path logFile() {
		return FabricLoader.getInstance().getGameDir().resolve("logs").resolve("wkclimb-assets.log");
	}

	/**
	 * Starts a build unless one is under way.
	 * @param onFinished called on the client thread with the final state
	 */
	public synchronized void start(Consumer<State> onFinished) {
		if (state.active()) return;
		this.onFinished = onFinished;
		cancelled = false;
		plan = null;
		stepIndex = 0;
		lastLine = "";
		tail.clear();
		state = State.CHECKING;
		Thread t = new Thread(this::run, "wkclimb-asset-builder");
		t.setDaemon(true);
		t.start();
	}

	/** The player's answer to the step being confirmed. */
	public void answer(boolean go) {
		CompletableFuture<Boolean> c = confirmation;
		if (c != null) c.complete(go);
	}

	/** Stops a build: declines a waiting step, or kills the running tool. */
	public void cancel() {
		cancelled = true;
		answer(false);
		Process p = process;
		if (p != null) {
			p.descendants().forEach(ProcessHandle::destroyForcibly);
			p.destroyForcibly();
		}
	}

	/** Test support: show a state without running anything. */
	public synchronized void setStateForTest(State s, Plan p, int step, List<String> lines) {
		tail.clear();
		tail.addAll(lines);
		lastLine = lines.isEmpty() ? "" : lines.get(lines.size() - 1);
		plan = p;
		stepIndex = step;
		state = s;
	}

	/** Back to IDLE after a finished or failed build (so the offer screen shows again). */
	public void reset() {
		if (!state.active()) state = State.IDLE;
	}

	private void run() {
		State result;
		try (Writer log = Files.newBufferedWriter(prepareLog(), StandardCharsets.UTF_8)) {
			result = build(log);
		} catch (Exception e) {
			LOG.error("Building the White Knuckle assets failed", e);
			addLine(null, "Error: " + e);
			result = State.FAILED;
		}
		if (cancelled) result = State.IDLE;
		state = result;
		final State finished = result;
		Minecraft.getInstance().execute(() -> onFinished.accept(finished));
	}

	private State build(Writer log) throws Exception {
		boolean windows = GameLocator.isWindows();
		Map<String, String> env = System.getenv();

		Optional<Path> game = GameLocator.find(ClimbConfig.wkGameDir);
		if (game.isEmpty()) {
			addLine(log, "White Knuckle not found" + (ClimbConfig.wkGameDir.isEmpty() ? "" : " in wk_game_dir: " + ClimbConfig.wkGameDir));
			return State.NO_GAME;
		}
		addLine(log, "White Knuckle: " + game.get());

		Optional<ExtractorSetup.Python> python = ExtractorSetup.findPython(windows, env);
		if (python.isEmpty()) {
			addLine(log, "No Python 3.10 - 3.14 found");
			return State.NO_PYTHON;
		}
		addLine(log, "Python " + python.get().version() + ": " + python.get().executable());

		Path venv = ExtractorSetup.venvDir(windows, env, Path.of(System.getProperty("user.home")));
		Path venvPython = ExtractorSetup.venvPython(venv, windows);
		Path marker = ExtractorSetup.marker(venv, python.get().version(), windows);
		byte[] requirements = bundled("requirements.txt");
		String markerText = ExtractorSetup.markerText(requirements);
		String existing = Files.isRegularFile(marker) ? Files.readString(marker).strip() : null;

		List<Step> steps = new ArrayList<>();
		for (ExtractorSetup.SetupStep s : ExtractorSetup.stepsNeeded(existing, markerText, Files.isRegularFile(venvPython))) {
			steps.add(s == ExtractorSetup.SetupStep.CREATE_VENV ? Step.CREATE_VENV : Step.INSTALL_LIBRARIES);
		}
		steps.add(Step.BUILD_PACK);
		steps.add(Step.ENABLE_PACK);
		plan = new Plan(game.get(), python.get(), venv, AssetPack.packDir(), List.copyOf(steps));

		for (int i = 0; i < steps.size(); i++) {
			stepIndex = i;
			if (!confirm()) return State.IDLE;
			state = State.RUNNING;
			State failed = switch (steps.get(i)) {
				case CREATE_VENV -> createVenv(log, python.get(), venv, venvPython);
				case INSTALL_LIBRARIES -> installLibraries(log, venv, venvPython, marker, markerText, requirements);
				case BUILD_PACK -> buildPack(log, venv, venvPython, game.get());
				case ENABLE_PACK -> enablePack(log);
			};
			if (failed != null) return failed;
		}
		return State.DONE;
	}

	/** Waits for the player's Continue (true) or Stop (false). */
	private boolean confirm() throws Exception {
		CompletableFuture<Boolean> c = new CompletableFuture<>();
		confirmation = c;
		state = State.CONFIRM;
		if (cancelled) return false;
		boolean go = c.get();
		confirmation = null;
		return go && !cancelled;
	}

	private State createVenv(Writer log, ExtractorSetup.Python python, Path venv, Path venvPython) throws IOException, InterruptedException {
		deleteVenv(venv);
		addLine(log, "Making the private Python folder " + venv);
		if (run(log, List.of(python.executable().toString(), "-I", "-m", "venv", venv.toString())) != 0) {
			deleteVenv(venv);
			return tailMentionsVenv() ? State.NO_VENV : State.FAILED;
		}
		if (!Files.isRegularFile(venvPython)) {
			// Microsoft Store Python, for one, silently writes AppData somewhere else
			addLine(log, "The folder was made, but its Python isn't at " + venvPython + ". Python from the Microsoft Store does this:"
					+ " install Python from python.org instead (the Install Python button does).");
			return State.FAILED;
		}
		return null;
	}

	private State installLibraries(Writer log, Path venv, Path venvPython, Path marker, String markerText, byte[] requirements)
			throws IOException, InterruptedException {
		Path req = venv.resolve("extractor").resolve("requirements.txt");
		Files.createDirectories(req.getParent());
		Files.write(req, requirements);
		addLine(log, "Downloading the Python libraries from pypi.org");
		// --only-binary: never try to compile anything (that needs a C compiler most PCs don't have)
		if (run(log, List.of(venvPython.toString(), "-I", "-m", "pip", "install", "--quiet", "--disable-pip-version-check",
				"--only-binary=:all:", "-r", req.toString())) != 0) return State.FAILED;
		Files.writeString(marker, markerText);
		return null;
	}

	private State buildPack(Writer log, Path venv, Path venvPython, Path game) throws IOException, InterruptedException {
		Path script = venv.resolve("extractor").resolve("extract_wk_assets.py");
		Files.createDirectories(script.getParent());
		Files.write(script, bundled("extract_wk_assets.py"));
		Path pack = AssetPack.packDir();
		addLine(log, "Extracting into " + pack);
		// -u: print as it goes; -X utf8: paths with any characters print fine on Windows too
		int code = run(log, List.of(venvPython.toString(), "-I", "-u", "-X", "utf8", script.toString(),
				"--out", pack.toString(), "--game", game.toString()));
		if (code != 0 || !Files.isRegularFile(pack.resolve("pack.mcmeta"))) return State.FAILED;
		return null;
	}

	private State enablePack(Writer log) throws Exception {
		addLine(log, "Switching the pack on");
		CompletableFuture<CompletableFuture<Void>> started = new CompletableFuture<>();
		Minecraft.getInstance().execute(() -> {
			try {
				started.complete(AssetPack.enable(Minecraft.getInstance()));
			} catch (Throwable e) {
				started.completeExceptionally(e);
			}
		});
		started.get().get(); // the resource reload
		return null;
	}

	/** Runs a command, streaming its output into the log; returns the exit code. */
	private int run(Writer log, List<String> cmd) throws IOException, InterruptedException {
		if (cancelled) return -1;
		log.write("$ " + String.join(" ", cmd) + "\n");
		ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
		Process p = pb.start();
		process = p;
		p.getOutputStream().close();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = r.readLine()) != null) {
				if (!line.isBlank()) addLine(log, line);
			}
		}
		int code = p.waitFor();
		process = null;
		log.write("(exit code " + code + ")\n");
		log.flush();
		return cancelled ? -1 : code;
	}

	private synchronized void addLine(Writer log, String line) {
		lastLine = line.strip();
		tail.addLast(lastLine);
		while (tail.size() > TAIL_LINES) tail.removeFirst();
		if (log != null) {
			try {
				log.write(line + "\n");
				log.flush();
			} catch (IOException ignored) {
				// the log is a convenience
			}
		}
		LOG.info("[assets] {}", line);
	}

	/** Debian and Ubuntu split venv off into python3-venv; elsewhere (and on Windows) it's always there. */
	private boolean tailMentionsVenv() {
		return !GameLocator.isWindows() && tail().stream().anyMatch(l -> l.contains("ensurepip is not available") || l.contains("No module named venv"));
	}

	private static Path prepareLog() throws IOException {
		Path log = logFile();
		Files.createDirectories(log.getParent());
		return log;
	}

	/** A file packed into the jar under wkclimb/extractor/. */
	private static byte[] bundled(String name) throws IOException {
		Path p = FabricLoader.getInstance().getModContainer("wkclimb").orElseThrow()
				.findPath("wkclimb/extractor/" + name)
				.orElseThrow(() -> new IOException("the mod jar has no wkclimb/extractor/" + name));
		return Files.readAllBytes(p);
	}

	/** Deletes the private Python folder (only ever the one named wkclimb-extractor). */
	private static void deleteVenv(Path venv) throws IOException {
		if (!Files.exists(venv)) return;
		if (venv.getFileName() == null || !venv.getFileName().toString().equals("wkclimb-extractor")) {
			throw new IOException("refusing to delete " + venv);
		}
		try (Stream<Path> files = Files.walk(venv)) {
			for (Path f : (Iterable<Path>) files.sorted(Comparator.reverseOrder())::iterator) Files.deleteIfExists(f);
		}
	}
}
