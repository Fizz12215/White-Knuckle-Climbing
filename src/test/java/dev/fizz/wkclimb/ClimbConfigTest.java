package dev.fizz.wkclimb;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** wk_game_dir is read as the player typed it, backslashes included (reported by the Windows test). */
class ClimbConfigTest {
	private static String read(String file) throws IOException {
		Properties p = new Properties();
		p.load(new StringReader(file));
		return ClimbConfig.gameDirFrom(file, p.getProperty("wk_game_dir", ""));
	}

	@Test
	void volumeSetting() {
		assertEquals(0.5, ClimbConfig.volumeFrom(null, 0.5, 2.0));
		assertEquals(0.25, ClimbConfig.volumeFrom(" 0.25 ", 0.5, 2.0));
		assertEquals(1.5, ClimbConfig.volumeFrom("1.5", 0.5, 2.0));
		assertEquals(2.0, ClimbConfig.volumeFrom("3", 0.5, 2.0));
		assertEquals(0.0, ClimbConfig.volumeFrom("-1", 0.5, 2.0));
		assertEquals(0.5, ClimbConfig.volumeFrom("loud", 0.5, 2.0));
		assertEquals(0.5, ClimbConfig.volumeFrom("NaN", 0.5, 2.0));
		assertEquals(1.25, ClimbConfig.volumeFrom("2.0", 0.5, ClimbConfig.MAX_LOW_HEALTH));
	}

	@Test
	void plusOrMinusSlider() {
		assertEquals(0.0, ClimbConfig.sliderToVolume(-100, 2.0));
		assertEquals(0.6, ClimbConfig.sliderToVolume(-40, 1.25), 1e-9);
		assertEquals(1.0, ClimbConfig.sliderToVolume(0, 1.25));
		assertEquals(1.125, ClimbConfig.sliderToVolume(50, 1.25), 1e-9);
		assertEquals(2.0, ClimbConfig.sliderToVolume(100, 2.0));
		for (double max : new double[] {1.0, 1.25, 1.5, 2.0})
			for (int pos = -100; pos <= (max > 1 ? 100 : 0); pos++)
				assertEquals(pos, ClimbConfig.volumeToSlider(ClimbConfig.sliderToVolume(pos, max), max));
		assertEquals(0, ClimbConfig.volumeToSlider(1.5, 1.0)); // ice: nothing above normal
	}

	@Test
	void windowsPathTypedWithBackslashes() throws IOException {
		// Properties alone would give "D:SteamLibrarysteamappscommonWhite Knuckle"
		assertEquals("D:\\SteamLibrary\\steamapps\\common\\White Knuckle",
				read("fov=90.0\nwk_game_dir=D:\\SteamLibrary\\steamapps\\common\\White Knuckle\n"));
	}

	@Test
	void forwardSlashesAndLinuxPaths() throws IOException {
		assertEquals("C:/Program Files (x86)/Steam/steamapps/common/White Knuckle",
				read("wk_game_dir=C:/Program Files (x86)/Steam/steamapps/common/White Knuckle\r\n"));
		assertEquals("/run/user/1000/doc/abc/White Knuckle", read("wk_game_dir = /run/user/1000/doc/abc/White Knuckle  \n"));
	}

	@Test
	void theEscapedFormOlderVersionsSaved() throws IOException {
		assertEquals("C:\\Program Files (x86)\\Steam\\steamapps\\common\\White Knuckle",
				read("wk_game_dir=C\\:\\\\Program Files (x86)\\\\Steam\\\\steamapps\\\\common\\\\White Knuckle\n"));
	}

	@Test
	void missingOrEmpty() throws IOException {
		assertEquals("", read("fov=90.0\n"));
		assertEquals("", read("wk_game_dir=\n"));
	}
}
