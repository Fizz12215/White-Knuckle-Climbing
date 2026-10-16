package dev.fizz.wkclimb.assets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** The pure parts of the in-game asset build: finding the game, Python and the private Python folder. */
class AssetSetupTest {
	@Test
	void packVersionMatchesTheExtractor() throws IOException {
		String script = Files.readString(Path.of("tools/extract_wk_assets.py"));
		Matcher m = Pattern.compile("(?m)^PACK_CONTENT_VERSION = (\\d+)").matcher(script);
		assertTrue(m.find(), "PACK_CONTENT_VERSION in the extractor");
		assertEquals(ExtractorSetup.PACK_VERSION, Integer.parseInt(m.group(1)));
	}

	@Test
	void libraryFoldersVdf() {
		String vdf = """
				"libraryfolders"
				{
					"0"
					{
						"path"		"/home/someone/.local/share/Steam"
						"label"		""
						"apps"
						{
							"3195790"		"1234"
						}
					}
					"1"
					{
						"path"		"/mnt/games/SteamLibrary"
					}
				}
				""";
		assertEquals(List.of(Path.of("/home/someone/.local/share/Steam"), Path.of("/mnt/games/SteamLibrary")), GameLocator.libraryPaths(vdf));
	}

	@Test
	void windowsVdfPathsLoseTheirDoubledBackslashes() {
		List<Path> paths = GameLocator.libraryPaths("\t\t\"path\"\t\t\"D:\\\\SteamLibrary\"");
		assertEquals(1, paths.size());
		assertEquals("D:\\SteamLibrary", paths.get(0).toString().replace('/', '\\'));
	}

	@Test
	void registryOutput() {
		String out = "\r\nHKEY_CURRENT_USER\\Software\\Valve\\Steam\r\n    SteamPath    REG_SZ    c:/program files (x86)/steam\r\n\r\n";
		assertEquals("c:/program files (x86)/steam", GameLocator.parseRegQuery(out));
		assertNull(GameLocator.parseRegQuery("ERROR: The system was unable to find the specified registry key or value."));
	}

	@Test
	void findsTheGameInALibraryListedByTheVdf(@TempDir Path tmp) throws IOException {
		Path root = Files.createDirectories(tmp.resolve("Steam/steamapps"));
		Path library = tmp.resolve("Other Library");
		Files.writeString(root.resolve("libraryfolders.vdf"), "\"path\" \"" + library + "\"\n");
		Path game = library.resolve("steamapps/common/White Knuckle");
		Files.createDirectories(game.resolve("White Knuckle_Data"));
		Files.writeString(game.resolve("White Knuckle_Data/level3"), "");

		List<Path> libraries = GameLocator.steamLibraries(List.of(tmp.resolve("Steam"), tmp.resolve("missing")));
		assertEquals(List.of(tmp.resolve("Steam"), library), libraries);
		assertEquals(Optional.of(game), GameLocator.gameDir(library.resolve("steamapps/common/White Knuckle")));
		// wk_game_dir may name the install folder or its _Data folder
		assertEquals(Optional.of(game), GameLocator.find(game.toString()));
		assertEquals(Optional.of(game), GameLocator.find(game.resolve("White Knuckle_Data").toString()));
		assertEquals(Optional.empty(), GameLocator.find(library.toString()));
	}

	@Test
	void registrySteamComesFirstAndTildeExpands() {
		List<Path> roots = GameLocator.steamRoots(Path.of("/home/me"), "E:/Steam");
		assertEquals(Path.of("E:/Steam"), roots.get(0));
		assertEquals(Path.of("/home/me/.local/share/Steam"), roots.get(1));
	}

	@Test
	void pickerStart(@TempDir Path tmp) throws IOException {
		Path home = tmp.resolve("home");
		Path library = tmp.resolve("SteamLibrary");
		// nothing visible: a Flatpak still starts at Steam's usual place, unchecked; elsewhere no suggestion
		assertEquals(Optional.of(home.resolve(".local/share/Steam/steamapps/common")), GameLocator.pickerStart(List.of(library), true, home));
		assertEquals(Optional.empty(), GameLocator.pickerStart(List.of(library), false, home));
		Files.createDirectories(library.resolve("steamapps/common"));
		assertEquals(Optional.of(library.resolve("steamapps/common")), GameLocator.pickerStart(List.of(library), true, home));
	}

	@Test
	void flatpakInfo(@TempDir Path tmp) throws IOException {
		Path info = tmp.resolve(".flatpak-info");
		assertEquals(Optional.empty(), GameLocator.flatpakAppId(info));
		Files.writeString(info, "[Application]\nname=org.prismlauncher.PrismLauncher\nruntime=runtime/org.kde.Platform/x86_64/6.10\n\n[Instance]\nname=other\n");
		assertEquals(Optional.of("org.prismlauncher.PrismLauncher"), GameLocator.flatpakAppId(info));
		// only White Knuckle's folder: the whole Steam folder would expose Steam's login files to every mod
		assertEquals("flatpak override --user --filesystem=\"~/.local/share/Steam/steamapps/common/White Knuckle:ro\" org.prismlauncher.PrismLauncher",
				GameLocator.flatpakOverrideCommand("org.prismlauncher.PrismLauncher"));
		assertEquals("flatpak override --user --nofilesystem=\"~/.local/share/Steam/steamapps/common/White Knuckle\" org.prismlauncher.PrismLauncher",
				GameLocator.flatpakUndoCommand("org.prismlauncher.PrismLauncher"));
	}

	@Test
	void chosenFolder(@TempDir Path tmp) throws IOException {
		Path common = tmp.resolve("steamapps/common");
		Path game = common.resolve("White Knuckle");
		Files.createDirectories(game.resolve("White Knuckle_Data"));
		Files.writeString(game.resolve("White Knuckle_Data/level3"), "");
		assertEquals(Optional.of(game), GameLocator.fromChosenFolder(game));
		assertEquals(Optional.of(game), GameLocator.fromChosenFolder(game.resolve("White Knuckle_Data")));
		assertEquals(Optional.of(game), GameLocator.fromChosenFolder(common));
		assertEquals(Optional.of(game), GameLocator.fromChosenFolder(tmp.resolve("steamapps")));
		assertEquals(Optional.empty(), GameLocator.fromChosenFolder(tmp));
		assertEquals(Optional.empty(), GameLocator.fromChosenFolder(tmp.resolve("nowhere")));
	}

	@Test
	void foundFolderIsSpelledAsOnDisk(@TempDir Path tmp) throws IOException {
		// on Windows the registry's lower-case SteamPath gets its real capitals back; here, a link resolves
		Path game = tmp.resolve("Real/White Knuckle");
		Files.createDirectories(game.resolve("White Knuckle_Data"));
		Files.writeString(game.resolve("White Knuckle_Data/level3"), "");
		Path link = Files.createSymbolicLink(tmp.resolve("link"), tmp.resolve("Real"));
		assertEquals(Optional.of(game.toRealPath()), GameLocator.find(link.resolve("White Knuckle").toString()));
		assertEquals(Optional.of(game.toRealPath()), GameLocator.fromChosenFolder(link));
	}

	@Test
	void documentPortalIds() {
		assertEquals(Optional.of("8cJ03ilxS3sPC4s91IIZVQ"), GameLocator.documentPortalId(Path.of("/run/user/1000/doc/8cJ03ilxS3sPC4s91IIZVQ/White Knuckle")));
		assertEquals(Optional.of("abc"), GameLocator.documentPortalId(Path.of("/run/flatpak/doc/abc/White Knuckle")));
		assertEquals(Optional.empty(), GameLocator.documentPortalId(Path.of("/home/me/.local/share/Steam/steamapps/common/White Knuckle")));
	}

	@Test
	void setupStepsNeeded() {
		String want = ExtractorSetup.markerText("UnityPy==1\n".getBytes());
		var both = List.of(ExtractorSetup.SetupStep.CREATE_VENV, ExtractorSetup.SetupStep.INSTALL_LIBRARIES);
		assertEquals(List.of(), ExtractorSetup.stepsNeeded(want, want, true));
		assertEquals(List.of(ExtractorSetup.SetupStep.INSTALL_LIBRARIES), ExtractorSetup.stepsNeeded("ok", want, true)); // the .bat's marker
		assertEquals(List.of(ExtractorSetup.SetupStep.INSTALL_LIBRARIES), ExtractorSetup.stepsNeeded("", want, true)); // the .sh's marker
		assertEquals(both, ExtractorSetup.stepsNeeded(null, want, true)); // no marker: an unfinished setup
		assertEquals(both, ExtractorSetup.stepsNeeded(want, want, false)); // the folder's python is gone
	}

	@Test
	void requirementsArePinned() throws IOException {
		for (String line : Files.readAllLines(Path.of("tools/requirements.txt"))) {
			String req = line.strip();
			if (req.isEmpty() || req.startsWith("#")) continue;
			assertTrue(req.matches("[A-Za-z0-9_.-]+==[0-9][^;]*(;.*)?"), "not pinned to an exact version: " + req);
		}
	}

	@Test
	void pythonProbe() {
		assertEquals(Optional.of(new ExtractorSetup.Python("3.13", Path.of("/usr/bin/python3.13"))),
				ExtractorSetup.parseProbe("3.13\n/usr/bin/python3.13\n"));
		assertEquals(Optional.empty(), ExtractorSetup.parseProbe("3.15\n/usr/bin/python3.15\n")); // too new for the wheels
		assertEquals(Optional.empty(), ExtractorSetup.parseProbe("3.9\n/usr/bin/python3.9\n"));
		assertEquals(Optional.empty(), ExtractorSetup.parseProbe("Python was not found; run without arguments to install from the Microsoft Store"));
	}

	@Test
	void pythonCandidatesFollowTheScripts() {
		List<List<String>> linux = ExtractorSetup.candidates(false, Map.of());
		assertEquals(List.of("python3.13"), linux.get(0));
		assertEquals(List.of("python3.12"), linux.get(1));
		assertEquals(List.of("python3"), linux.get(linux.size() - 1));

		List<List<String>> windows = ExtractorSetup.candidates(true, Map.of("LOCALAPPDATA", "C:/Users/me/AppData/Local", "ProgramFiles", "C:/Program Files"));
		assertEquals(List.of("py", "-3.13"), windows.get(0));
		// never a bare "python": that can be the Microsoft Store's, which breaks the private folder
		assertTrue(windows.stream().noneMatch(c -> c.get(0).equals("python")));
		assertTrue(windows.stream().anyMatch(c -> c.get(0).replace('\\', '/').equals("C:/Program Files/Python313/python.exe")));
		// a fresh per-user install is found without a new PATH
		assertTrue(windows.stream().anyMatch(c -> c.get(0).replace('\\', '/').endsWith("Programs/Python/Python313/python.exe")));
		assertTrue(windows.stream().anyMatch(c -> c.get(0).replace('\\', '/').endsWith("Programs/Python/Launcher/py.exe")));
	}

	@Test
	void venvFolderIsTheScripts() {
		Path home = Path.of("/home/me");
		assertEquals(Path.of("/home/me/.local/share/wkclimb-extractor"), ExtractorSetup.venvDir(false, Map.of(), home));
		assertEquals(Path.of("/data/wkclimb-extractor"), ExtractorSetup.venvDir(false, Map.of("XDG_DATA_HOME", "/data"), home));
		assertEquals(Path.of("C:/Users/me/AppData/Local/wkclimb-extractor"),
				ExtractorSetup.venvDir(true, Map.of("LOCALAPPDATA", "C:/Users/me/AppData/Local"), home));
		Path venv = Path.of("/v");
		assertEquals(Path.of("/v/setup-ok-3.13"), ExtractorSetup.marker(venv, "3.13", false));
		assertEquals(Path.of("/v/setup-ok-3.13.txt"), ExtractorSetup.marker(venv, "3.13", true));
		assertEquals(Path.of("/v/bin/python"), ExtractorSetup.venvPython(venv, false));
		assertEquals(Path.of("/v/Scripts/python.exe"), ExtractorSetup.venvPython(venv, true));
		assertNotEquals(ExtractorSetup.markerText("UnityPy\n".getBytes()), ExtractorSetup.markerText("UnityPy>=2\n".getBytes()));
	}
}
