package dev.fizz.wkclimb.client.assets;

import dev.fizz.wkclimb.ClimbConfig;
import dev.fizz.wkclimb.assets.ExtractorSetup;
import dev.fizz.wkclimb.assets.GameLocator;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Offers to build White Knuckle's hands and sounds and walks the player through it. Nothing happens on
 * their computer without an OK: each build step, the folder picker and the Python install first say
 * what they will do (with the real folders), how big it is, where it comes from and how to undo it.
 */
public class AssetSetupScreen extends Screen {
	private static final int WHITE = 0xFFFFFFFF, GREY = 0xFFA0A0A0, AQUA = 0xFF55FFFF, YELLOW = 0xFFFFFF55;
	private static final String PYTHON_DOWNLOAD = "https://www.python.org/downloads/windows/";
	private static final String WINGET_PYTHON = "winget install -e --id Python.Python.3.13 --accept-package-agreements --accept-source-agreements";

	/** What the screen shows: the build's state, or one of the screen's own pages. */
	enum View { OFFER, NOT_FOUND, PICKER_INFO, PICKER_OPEN, PICKER_WRONG, PICKER_FAILED, PYTHON_INFO,
		CHECKING, CONFIRM, RUNNING, NO_PYTHON, NO_VENV, FAILED }

	private final Screen parent;
	private final AssetPack.Status status;
	private final String flatpakApp;
	private final AssetBuilder builder = AssetBuilder.INSTANCE;
	private Optional<Path> game;
	/** One of the screen's own pages, or null to follow the build. */
	private View page;
	private Path wrongFolder;
	private String pickerError = "";
	private boolean pythonInstallStarted;
	private int pickerRun;

	private String shownKey = "";
	private HeaderAndFooterLayout layout;
	private ScrollableLayout scroll;
	private LinearLayout body;
	private MultiLineTextWidget liveLine;

	public AssetSetupScreen(Screen parent, AssetPack.Status status) {
		super(Component.translatable("wkclimb.assets.title"));
		this.parent = parent;
		this.status = status;
		this.game = GameLocator.find(ClimbConfig.wkGameDir);
		this.flatpakApp = GameLocator.flatpakAppId().orElse(null);
	}

	View view() {
		if (page != null) return page;
		return switch (builder.state()) {
			case IDLE, DONE -> game.isPresent() ? View.OFFER : View.NOT_FOUND;
			case NO_GAME -> View.NOT_FOUND;
			case CHECKING -> View.CHECKING;
			case CONFIRM -> View.CONFIRM;
			case RUNNING -> View.RUNNING;
			case NO_PYTHON -> View.NO_PYTHON;
			case NO_VENV -> View.NO_VENV;
			case FAILED -> View.FAILED;
		};
	}

	private String viewKey() {
		View v = view();
		return v == View.CONFIRM || v == View.RUNNING ? v + ":" + builder.stepNumber() : v.toString();
	}

	@Override
	protected void init() {
		shownKey = viewKey();
		liveLine = null;
		layout = new HeaderAndFooterLayout(this, 33, 57);
		layout.addTitleHeader(title, font);
		body = LinearLayout.vertical().spacing(6);
		// The column is always the full text width, so pages with only short lines are centred in it
		// too (the scroll area keeps this width and would otherwise put a narrow column at its left).
		body.addChild(SpacerElement.width(wrapWidth()));
		LinearLayout footer = layout.addToFooter(LinearLayout.vertical().spacing(4));
		footer.defaultCellSetting().alignHorizontallyCenter(); // a row with one button sits in the middle
		LinearLayout row1 = footer.addChild(LinearLayout.horizontal().spacing(8));
		LinearLayout row2 = footer.addChild(LinearLayout.horizontal().spacing(8));
		boolean windows = GameLocator.isWindows();

		switch (view()) {
			case OFFER -> {
				text(status == AssetPack.Status.OUTDATED ? "wkclimb.assets.offer.outdated" : "wkclimb.assets.offer.missing", WHITE);
				game.ifPresent(p -> path(p));
				text("wkclimb.assets.offer.steps", WHITE);
				text("wkclimb.assets.offer.safety", GREY);
				button(row1, "wkclimb.assets.start", b -> start());
				button(row1, "wkclimb.assets.not_now", b -> onClose());
				button(row2, "wkclimb.assets.never", b -> never());
			}
			case NOT_FOUND -> {
				if (!ClimbConfig.wkGameDir.isEmpty()) {
					// only that folder was tried: say so, rather than blame the Steam libraries
					text("wkclimb.assets.not_found.configured", WHITE);
					literal(ClimbConfig.wkGameDir, AQUA);
					text("wkclimb.assets.not_found.configured.fix", WHITE);
				} else if (flatpakApp != null) {
					text("wkclimb.assets.not_found.flatpak", WHITE);
				} else {
					text("wkclimb.assets.not_found", WHITE);
					text(windows ? "wkclimb.assets.not_found.config.windows" : "wkclimb.assets.not_found.config", GREY);
				}
				button(row1, "wkclimb.assets.choose_folder", b -> page(View.PICKER_INFO));
				button(row1, "wkclimb.assets.not_now", b -> onClose());
				button(row2, "wkclimb.assets.never", b -> never());
			}
			case PICKER_INFO -> {
				text("wkclimb.assets.picker.info", WHITE);
				text(flatpakApp != null ? "wkclimb.assets.picker.info.flatpak" : "wkclimb.assets.picker.info.saved", WHITE);
				if (minecraft.options.fullscreen().get()) text("wkclimb.assets.leaves_fullscreen", GREY);
				button(row1, "wkclimb.assets.open_picker", b -> openPicker());
				button(row1, "gui.back", b -> page(null));
			}
			case PICKER_OPEN -> {
				text("wkclimb.assets.picker.open", WHITE);
				button(row1, "wkclimb.assets.cancel", b -> {
					pickerRun++;
					FolderPicker.cancel();
					page(null);
				});
			}
			case PICKER_WRONG -> {
				text("wkclimb.assets.picker.wrong", WHITE);
				if (wrongFolder != null) path(wrongFolder);
				text("wkclimb.assets.picker.wrong.hint", GREY);
				button(row1, "wkclimb.assets.choose_again", b -> openPicker());
				button(row1, "gui.back", b -> page(null));
			}
			case PICKER_FAILED -> {
				text("wkclimb.assets.picker.failed", WHITE, pickerError);
				if (flatpakApp != null) {
					String command = GameLocator.flatpakOverrideCommand(flatpakApp);
					text("wkclimb.assets.flatpak.command", WHITE);
					literal(command, AQUA);
					text("wkclimb.assets.flatpak.command.risk", GREY);
					literal(GameLocator.flatpakUndoCommand(flatpakApp), AQUA);
					text("wkclimb.assets.flatpak.other_library", GREY);
					button(row1, "wkclimb.assets.copy", b -> copy(command));
				} else {
					text(windows ? "wkclimb.assets.picker.failed.config.windows" : "wkclimb.assets.picker.failed.config", WHITE);
					button(row1, "wkclimb.assets.open_config", b -> com.mojang.blaze3d.Blaze3D.openPath(FabricLoader.getInstance().getConfigDir()));
					button(row2, "wkclimb.assets.try_again", b -> start());
				}
				button(row1, "gui.back", b -> page(null));
			}
			case PYTHON_INFO -> {
				text("wkclimb.assets.python.info", WHITE);
				text("wkclimb.assets.python.info.after", WHITE);
				text("wkclimb.assets.python.info.remove", GREY);
				if (minecraft.options.fullscreen().get()) text("wkclimb.assets.leaves_fullscreen", GREY);
				button(row1, "wkclimb.assets.install_python", b -> installPython());
				button(row1, "gui.back", b -> page(null));
			}
			case CHECKING -> {
				text("wkclimb.assets.checking", WHITE);
				button(row1, "wkclimb.assets.cancel", b -> builder.cancel());
			}
			case CONFIRM -> {
				confirmText(builder.plan(), builder.step());
				button(row1, "wkclimb.assets.continue", b -> builder.answer(true));
				button(row1, "wkclimb.assets.stop", b -> builder.answer(false));
			}
			case RUNNING -> {
				AssetBuilder.Step step = builder.step();
				stepHeading(builder.plan(), step);
				text("wkclimb.assets.running." + key(step), WHITE);
				liveLine = body.addChild(new MultiLineTextWidget(grey(builder.lastLine()), font)
						.setMaxWidth(wrapWidth()).setCentered(true), s -> s.alignHorizontallyCenter());
				button(row1, "wkclimb.assets.cancel", b -> builder.cancel());
			}
			case NO_PYTHON -> {
				if (windows) {
					text(pythonInstallStarted ? "wkclimb.assets.no_python.windows.installing" : "wkclimb.assets.no_python.windows", WHITE);
					button(row1, "wkclimb.assets.install_python_more", b -> page(View.PYTHON_INFO));
					button(row1, "wkclimb.assets.python_org", b -> ConfirmLinkScreen.confirmLinkNow(this, java.net.URI.create(PYTHON_DOWNLOAD)));
				} else {
					text("wkclimb.assets.no_python.linux", WHITE);
					literal("Ubuntu / Debian / Mint:  sudo apt install python3 python3-venv", AQUA);
					literal("Fedora:  sudo dnf install python3", AQUA);
					literal("Arch / SteamOS:  sudo pacman -S python", AQUA);
				}
				button(row2, "wkclimb.assets.try_again", b -> start());
				button(row2, "gui.done", b -> onClose());
			}
			case NO_VENV -> {
				text("wkclimb.assets.no_venv", WHITE);
				literal("sudo apt install python3-venv", AQUA);
				button(row1, "wkclimb.assets.copy", b -> copy("sudo apt install python3-venv"));
				button(row1, "wkclimb.assets.try_again", b -> start());
				button(row2, "gui.done", b -> onClose());
			}
			case FAILED -> {
				text("wkclimb.assets.failed", WHITE);
				for (String l : builder.tail()) literal(l, GREY);
				text("wkclimb.assets.failed.report", WHITE);
				path(AssetBuilder.logFile());
				text("wkclimb.assets.failed.report.ask", WHITE);
				button(row1, "wkclimb.assets.try_again", b -> start());
				button(row1, "wkclimb.assets.open_log", b -> com.mojang.blaze3d.Blaze3D.openPath(AssetBuilder.logFile().getParent()));
				button(row2, "gui.done", b -> onClose());
			}
		}

		scroll = new ScrollableLayout(minecraft, body, 100);
		scroll.setMinWidth(wrapWidth() + 12);
		layout.addToContents(scroll);
		layout.visitWidgets(this::addRenderableWidget);
		repositionElements();
	}

	@Override
	protected void repositionElements() {
		if (layout == null) return;
		scroll.setMaxHeight(Math.max(40, layout.getContentHeight() - 8));
		layout.arrangeElements();
	}

	@Override
	public void tick() {
		if (builder.state() == AssetBuilder.State.DONE) {
			builder.reset();
			minecraft.gui.setScreen(parent);
			return;
		}
		if (!viewKey().equals(shownKey)) {
			rebuildWidgets();
		} else if (liveLine != null) {
			String dots = ".".repeat((int) (System.currentTimeMillis() / 400 % 4));
			liveLine.setMessage(grey(builder.lastLine().isEmpty() ? dots : builder.lastLine()));
			repositionElements();
		}
	}

	@Override
	public void onClose() {
		if (builder.state().active()) builder.cancel();
		else builder.reset();
		pickerRun++;
		FolderPicker.cancel();
		minecraft.gui.setScreen(parent);
	}

	// ---- actions ----

	private void page(View v) {
		page = v;
		rebuildWidgets();
	}

	private void start() {
		page = null;
		ClimbConfig.load(); // pick up a wk_game_dir edited while the game runs
		game = GameLocator.find(ClimbConfig.wkGameDir);
		builder.start(AssetPack::onBuildFinished);
		rebuildWidgets();
	}

	private void never() {
		ClimbConfig.assetPrompt = false;
		ClimbConfig.save();
		onClose();
	}

	private void copy(String s) {
		minecraft.keyboardHandler.setClipboard(s);
	}

	private void openPicker() {
		int run = ++pickerRun;
		page(View.PICKER_OPEN);
		FolderPicker.choose(minecraft, Component.translatable("wkclimb.assets.picker.title").getString(), result -> {
			if (run != pickerRun || minecraft.gui.screen() != this) return; // cancelled meanwhile
			switch (result) {
				case FolderPicker.Result.Chosen c -> {
					Optional<Path> found = GameLocator.fromChosenFolder(c.folder());
					if (found.isPresent()) {
						ClimbConfig.wkGameDir = found.get().toString();
						ClimbConfig.save();
						start(); // its steps each ask for an OK
					} else {
						wrongFolder = c.folder();
						page(View.PICKER_WRONG);
					}
				}
				case FolderPicker.Result.Cancelled x -> page(null);
				case FolderPicker.Result.Failed f -> {
					pickerError = f.reason();
					page(View.PICKER_FAILED);
				}
			}
		});
	}

	/** Opens a window that installs Python 3.13 with winget and stays open until a key is pressed. */
	private void installPython() {
		FolderPicker.leaveFullscreen(minecraft);
		try {
			new ProcessBuilder("cmd.exe", "/c", "start", "Install Python 3.13", "cmd.exe", "/c", WINGET_PYTHON + " & pause").start();
			pythonInstallStarted = true;
			page(null);
		} catch (IOException e) {
			ConfirmLinkScreen.confirmLinkNow(this, java.net.URI.create(PYTHON_DOWNLOAD));
		}
	}

	// ---- text ----

	private static String key(AssetBuilder.Step step) {
		return switch (step) {
			case CREATE_VENV -> "create_venv";
			case INSTALL_LIBRARIES -> "install_libraries";
			case BUILD_PACK -> "build_pack";
			case ENABLE_PACK -> "enable_pack";
		};
	}

	private void stepHeading(AssetBuilder.Plan plan, AssetBuilder.Step step) {
		Component name = Component.translatable("wkclimb.assets.step." + key(step));
		para(Component.translatable("wkclimb.assets.step_of", builder.stepNumber(), plan.steps().size(), name), YELLOW);
	}

	/** What a step will do, where, how big, and how to undo it. */
	private void confirmText(AssetBuilder.Plan plan, AssetBuilder.Step step) {
		stepHeading(plan, step);
		switch (step) {
			case CREATE_VENV -> {
				text("wkclimb.assets.confirm.create_venv", WHITE);
				path(plan.venv());
				text("wkclimb.assets.confirm.create_venv.python", WHITE, plan.python().version());
				path(plan.python().executable());
				text("wkclimb.assets.confirm.create_venv.nothing_else", WHITE);
				text("wkclimb.assets.undo.delete_venv", GREY);
			}
			case INSTALL_LIBRARIES -> {
				text("wkclimb.assets.confirm.install_libraries", WHITE);
				boolean win = GameLocator.isWindows();
				text("wkclimb.assets.confirm.install_libraries.size", WHITE,
						win ? ExtractorSetup.DOWNLOAD_MB_WINDOWS : ExtractorSetup.DOWNLOAD_MB_LINUX,
						win ? ExtractorSetup.INSTALLED_MB_WINDOWS : ExtractorSetup.INSTALLED_MB_LINUX);
				path(plan.venv());
				text("wkclimb.assets.confirm.install_libraries.time", GREY);
				text("wkclimb.assets.undo.delete_venv", GREY);
			}
			case BUILD_PACK -> {
				text("wkclimb.assets.confirm.build_pack.read", WHITE);
				path(plan.game());
				text("wkclimb.assets.confirm.build_pack.write", WHITE);
				path(plan.pack());
				text("wkclimb.assets.confirm.build_pack.size", WHITE);
				text("wkclimb.assets.undo.delete_pack", GREY);
				GameLocator.documentPortalId(plan.game()).ifPresent(id -> {
					text("wkclimb.assets.confirm.build_pack.portal", GREY);
					literal("flatpak document-unexport --doc-id=" + id, AQUA);
				});
			}
			case ENABLE_PACK -> {
				boolean on = AssetPack.isOn(minecraft);
				text(on ? "wkclimb.assets.confirm.enable_pack.reload" : "wkclimb.assets.confirm.enable_pack", WHITE);
				if (!on) text("wkclimb.assets.undo.enable_pack", GREY);
			}
		}
	}

	private int wrapWidth() {
		return Math.min(380, width - 40);
	}

	private void text(String key, int colour, Object... args) {
		para(Component.translatable(key, args), colour);
	}

	private void path(Path p) {
		literal(p.toString(), AQUA);
	}

	private void literal(String s, int colour) {
		para(Component.literal(s), colour);
	}

	private void para(Component c, int colour) {
		// 26.3's MultiLineTextWidget has no colour of its own: the colour goes in the text
		body.addChild(new MultiLineTextWidget(c.copy().withColor(colour & 0xFFFFFF), font).setMaxWidth(wrapWidth()).setCentered(true),
				s -> s.alignHorizontallyCenter());
	}

	private static Component grey(String s) {
		return Component.literal(s).withColor(GREY & 0xFFFFFF);
	}

	private void button(LinearLayout row, String key, Button.OnPress onPress) {
		row.addChild(Button.builder(Component.translatable(key), onPress).width(150).build());
	}

	/** Test support: show one of the screen's own pages. */
	public void showPageForTest(String name) {
		page = View.valueOf(name);
		if (page == View.PICKER_WRONG) wrongFolder = Path.of("/home/someone/Downloads");
		if (page == View.PICKER_FAILED) pickerError = "example: no folder picker";
		if (minecraft != null) rebuildWidgets();
	}
}
