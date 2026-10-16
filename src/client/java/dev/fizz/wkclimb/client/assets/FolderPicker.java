package dev.fizz.wkclimb.client.assets;

import dev.fizz.wkclimb.assets.GameLocator;
import dev.fizz.wkclimb.assets.PortalFolderPicker;
import net.minecraft.client.Minecraft;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * The system's own folder picker, for choosing White Knuckle's folder.
 *  - Linux (also in a Flatpak): the desktop portal. In a Flatpak this is what lets the sandbox read the
 *    folder chosen, with no command and no launcher restart.
 *  - Windows, or Linux without a portal: LWJGL's tinyfd (a native dialog on Windows; zenity or kdialog
 *    on Linux). Not inside a Flatpak, where those only see the sandbox.
 * A fullscreen game is put in a window first, so the picker doesn't take the focus from fullscreen
 * Minecraft (switching away from fullscreen has frozen it on an NVIDIA + Wayland desktop), and goes
 * back to fullscreen afterwards.
 */
public final class FolderPicker {
	private static final Logger LOG = LoggerFactory.getLogger("wkclimb");

	public sealed interface Result {
		record Chosen(Path folder) implements Result {}
		record Cancelled() implements Result {}
		record Failed(String reason) implements Result {}
	}

	private static volatile PortalFolderPicker open;

	private FolderPicker() {}

	/** Opens the picker; onDone runs on the client thread. */
	public static void choose(Minecraft mc, String title, Consumer<Result> onDone) {
		boolean wasFullscreen = leaveFullscreen(mc);
		Thread t = new Thread(() -> {
			Result result = pick(title);
			mc.execute(() -> {
				if (wasFullscreen && !mc.getWindow().isFullscreen()) mc.getWindow().toggleFullScreen();
				onDone.accept(result);
			});
		}, "wkclimb-folder-picker");
		t.setDaemon(true);
		t.start();
	}

	/** Closes a portal picker that's still open. */
	public static void cancel() {
		PortalFolderPicker p = open;
		if (p != null) {
			try {
				p.close();
			} catch (Exception ignored) {
				// it's closing either way
			}
		}
	}

	/** Puts a fullscreen game in a window; true if it was fullscreen (the player's setting is untouched). */
	static boolean leaveFullscreen(Minecraft mc) {
		if (!mc.getWindow().isFullscreen()) return false;
		mc.getWindow().toggleFullScreen();
		return true;
	}

	private static Result pick(String title) {
		Path start = GameLocator.pickerStart().orElse(null);
		boolean flatpak = GameLocator.flatpakAppId().isPresent();
		if (!GameLocator.isWindows()) {
			try (PortalFolderPicker portal = PortalFolderPicker.connect()) {
				open = portal;
				PortalFolderPicker.Result r = portal.pickFolder(title, start);
				return r.outcome() == PortalFolderPicker.Outcome.CHOSEN ? new Result.Chosen(r.folder()) : new Result.Cancelled();
			} catch (Exception e) {
				LOG.warn("The desktop portal's folder picker didn't work", e);
				if (flatpak) return new Result.Failed(e.toString());
			} finally {
				open = null;
			}
		}
		try {
			String chosen = TinyFileDialogs.tinyfd_selectFolderDialog(title, start == null ? "" : start + File.separator);
			return chosen == null ? new Result.Cancelled() : new Result.Chosen(Path.of(chosen));
		} catch (Throwable e) {
			LOG.warn("The folder picker didn't work", e);
			return new Result.Failed(e.toString());
		}
	}
}
