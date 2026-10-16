package dev.fizz.wkclimb.client.assets;

import dev.fizz.wkclimb.assets.GameLocator;
import dev.fizz.wkclimb.assets.PortalFolderPicker;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * The system's own folder picker, for choosing White Knuckle's folder.
 *  - Linux (also in a Flatpak): the desktop portal. In a Flatpak this is what lets the sandbox read the
 *    folder chosen, with no command and no launcher restart.
 *  - Windows, or Linux without a portal: SDL3's folder dialog (Minecraft 26.3 runs on SDL3 and no longer
 *    ships tinyfd): the native dialog on Windows. Not inside a Flatpak, where only the portal can grant
 *    the sandbox a folder.
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
			Result result = pick(mc, title);
			mc.execute(() -> {
				if (wasFullscreen && !mc.options.fullscreen().get()) mc.options.fullscreen().set(true);
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

	/** Puts a fullscreen game in a window, as F11 does; true if it was fullscreen (restored afterwards). */
	static boolean leaveFullscreen(Minecraft mc) {
		if (!mc.options.fullscreen().get()) return false;
		mc.options.fullscreen().set(false);
		return true;
	}

	private static Result pick(Minecraft mc, String title) {
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
			return sdlPick(mc, start);
		} catch (Throwable e) {
			LOG.warn("The folder picker didn't work", e);
			return new Result.Failed(e.toString());
		}
	}

	/**
	 * SDL3's folder dialog. SDL wants it opened from the main thread; the answer comes to a callback
	 * (on any thread): a NULL list on error, an empty list when cancelled, else the chosen path.
	 */
	private static Result sdlPick(Minecraft mc, Path start) throws Exception {
		CompletableFuture<Result> done = new CompletableFuture<>();
		SDL_DialogFileCallback callback = SDL_DialogFileCallback.create((userdata, filelist, filter) -> {
			try { // always answer, or the picker thread would wait forever
				if (filelist == 0L) {
					String error = SDLError.SDL_GetError();
					done.complete(new Result.Failed(error == null || error.isBlank() ? "SDL's folder dialog failed" : error));
					return;
				}
				long first = MemoryUtil.memGetAddress(filelist);
				done.complete(first == 0L ? new Result.Cancelled() : new Result.Chosen(Path.of(MemoryUtil.memUTF8(first))));
			} catch (Throwable e) {
				done.complete(new Result.Failed(e.toString()));
			}
		});
		try {
			// SDL takes what follows the last separator as a file name (Windows: steamapps with "common"
			// typed in), so a folder to open needs a separator at the end, as tinyfd did
			String location = start == null ? null : start + File.separator;
			mc.execute(() -> {
				try {
					SDLDialog.SDL_ShowOpenFolderDialog(callback, 0L, mc.getWindow().handle(), location, false);
				} catch (Throwable e) {
					LOG.warn("SDL's folder dialog didn't open", e);
					done.complete(new Result.Failed(e.toString()));
				}
			});
			return done.get();
		} finally {
			callback.free();
		}
	}
}
