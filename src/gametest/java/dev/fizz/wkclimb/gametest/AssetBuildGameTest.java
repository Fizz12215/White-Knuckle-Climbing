package dev.fizz.wkclimb.gametest;

import dev.fizz.wkclimb.ClimbConfig;
import dev.fizz.wkclimb.assets.ExtractorSetup;
import dev.fizz.wkclimb.client.ClimbHands;
import dev.fizz.wkclimb.client.assets.AssetBuilder;
import dev.fizz.wkclimb.client.assets.AssetPack;
import dev.fizz.wkclimb.client.assets.AssetSetupScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * The in-game Build button, end to end: runs the real extractor on the real White Knuckle install,
 * clicking Continue at every step's confirmation. Opt-in, since it needs the game and downloads Python
 * libraries. Point XDG_DATA_HOME at an empty folder to go through all four steps (a fresh private
 * Python folder) without touching the real one:
 *   WKCLIMB_TEST_BUILD=1 XDG_DATA_HOME=/tmp/somewhere ./gradlew runClientGameTest
 */
public class AssetBuildGameTest implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("wkclimb-gametest");
	/** Long enough for a first-time setup on a slow connection. */
	private static final int STEP_TIMEOUT_TICKS = 20 * 60 * 20;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (System.getenv("WKCLIMB_TEST_BUILD") == null) {
			LOG.info("[wkclimb] no WKCLIMB_TEST_BUILD, skipping the asset build test");
			return;
		}
		removePack(ctx);
		check(ctx, "no pack: status MISSING", mc -> AssetPack.check(mc.getResourceManager()) == AssetPack.Status.MISSING);
		Screen title = ctx.computeOnClient(mc -> mc.gui.screen());
		screenshotPages(ctx, title);

		// The real thing: offer, Start, then Continue at each step
		ctx.setScreen(() -> new AssetSetupScreen(title, AssetPack.Status.MISSING));
		ctx.waitTicks(3);
		ctx.takeScreenshot("wkclimb-assets-offer");
		ctx.clickScreenButton("wkclimb.assets.start");
		List<String> steps = continueThroughSteps(ctx, "first");
		LOG.info("[wkclimb] first build steps: {}", steps);
		ctx.waitFor(mc -> !(mc.gui.screen() instanceof AssetSetupScreen) && mc.gui.overlay() == null, 2400);
		check(ctx, "first build finished and the screen closed", mc -> AssetBuilder.INSTANCE.state() == AssetBuilder.State.IDLE);
		if (System.getenv("XDG_DATA_HOME") != null) {
			check(ctx, "a fresh private Python folder takes all four steps",
					mc -> steps.equals(List.of("CREATE_VENV", "INSTALL_LIBRARIES", "BUILD_PACK", "ENABLE_PACK")));
		}
		check(ctx, "built pack switched on and loaded", mc -> AssetPack.check(mc.getResourceManager()) == AssetPack.Status.OK
				&& mc.getResourcePackRepository().getSelectedIds().contains(AssetPack.ID)
				&& mc.options.resourcePacks.contains(AssetPack.ID));
		check(ctx, "WK hand sprites loaded", mc -> ClimbHands.isActive());
		ctx.takeScreenshot("wkclimb-assets-done");

		// An out-of-date pack brings the offer back (nothing rebuilds by itself), with only two steps
		CompletableFuture<Void> reload = ctx.computeOnClient(mc -> {
			write(AssetPack.packDir().resolve("assets/wkclimb/wk_pack_info.json"), "{\"version\": 0}");
			return mc.reloadResourcePacks();
		});
		ctx.waitFor(mc -> reload.isDone() && mc.gui.overlay() == null, 2400);
		check(ctx, "version 0 pack: status OUTDATED", mc -> AssetPack.check(mc.getResourceManager()) == AssetPack.Status.OUTDATED);
		ctx.runOnClient(AssetPack::offer);
		check(ctx, "out-of-date pack: the offer opens, nothing runs", mc -> mc.gui.screen() instanceof AssetSetupScreen
				&& AssetBuilder.INSTANCE.state() == AssetBuilder.State.IDLE);
		ctx.waitTicks(3);
		ctx.takeScreenshot("wkclimb-assets-offer-outdated");

		// Stop at the first step: nothing happens and the offer is back
		ctx.clickScreenButton("wkclimb.assets.start");
		ctx.waitFor(mc -> AssetBuilder.INSTANCE.state() == AssetBuilder.State.CONFIRM, 2400);
		ctx.waitTicks(3); // the screen shows the step's buttons on its next tick
		ctx.clickScreenButton("wkclimb.assets.stop");
		ctx.waitFor(mc -> AssetBuilder.INSTANCE.state() == AssetBuilder.State.IDLE, 600);
		check(ctx, "Stop: still out of date, offer shown", mc -> AssetPack.check(mc.getResourceManager()) == AssetPack.Status.OUTDATED
				&& mc.gui.screen() instanceof AssetSetupScreen);

		ctx.waitTicks(3);
		ctx.clickScreenButton("wkclimb.assets.start");
		List<String> rebuild = continueThroughSteps(ctx, "rebuild");
		check(ctx, "rebuild needs only build + switch on", mc -> rebuild.equals(List.of("BUILD_PACK", "ENABLE_PACK")));
		ctx.waitFor(mc -> !(mc.gui.screen() instanceof AssetSetupScreen) && mc.gui.overlay() == null, 2400);
		check(ctx, "rebuilt pack is current", mc -> AssetPack.check(mc.getResourceManager()) == AssetPack.Status.OK);

		removePack(ctx);
		AssetBuilder.INSTANCE.reset();
		ClimbConfig.assetPrompt = true;
		ClimbConfig.save();
		LOG.info("[wkclimb] asset build test passed");
	}

	/** Clicks Continue at every confirmation until the build ends; returns the steps confirmed. */
	private static List<String> continueThroughSteps(ClientGameTestContext ctx, String name) {
		List<String> steps = new ArrayList<>();
		while (true) {
			ctx.waitFor(mc -> AssetBuilder.INSTANCE.state() == AssetBuilder.State.CONFIRM || !AssetBuilder.INSTANCE.state().active(),
					STEP_TIMEOUT_TICKS);
			if (ctx.computeOnClient(mc -> AssetBuilder.INSTANCE.state()) != AssetBuilder.State.CONFIRM) break;
			String step = ctx.computeOnClient(mc -> AssetBuilder.INSTANCE.step().toString());
			steps.add(step);
			ctx.waitTicks(3);
			ctx.takeScreenshot("wkclimb-assets-" + name + "-confirm-" + step.toLowerCase());
			ctx.clickScreenButton("wkclimb.assets.continue");
			ctx.waitTicks(20);
			if (ctx.computeOnClient(mc -> AssetBuilder.INSTANCE.state()) == AssetBuilder.State.RUNNING) {
				ctx.takeScreenshot("wkclimb-assets-" + name + "-running-" + step.toLowerCase());
			}
		}
		LOG.info("[wkclimb] {} build finished as {}; last lines: {}", name, ctx.computeOnClient(mc -> AssetBuilder.INSTANCE.state()),
				AssetBuilder.INSTANCE.tail());
		return steps;
	}

	/** Every page, for a look at the layout (paths made long, as on Windows). */
	private static void screenshotPages(ClientGameTestContext ctx, Screen title) {
		Path longPath = Path.of("C:/Users/A Rather Long User Name/AppData/Local/wkclimb-extractor");
		AssetBuilder.Plan plan = new AssetBuilder.Plan(
				Path.of("D:/SteamLibrary/steamapps/common/White Knuckle"),
				new ExtractorSetup.Python("3.13", Path.of("C:/Users/A Rather Long User Name/AppData/Local/Programs/Python/Python313/python.exe")),
				longPath, Path.of("C:/Users/A Rather Long User Name/AppData/Roaming/.minecraft/resourcepacks/White Knuckle Assets"),
				List.of(AssetBuilder.Step.values()));
		for (int i = 0; i < 4; i++) {
			int step = i;
			ctx.runOnClient(mc -> AssetBuilder.INSTANCE.setStateForTest(AssetBuilder.State.CONFIRM, plan, step, List.of()));
			ctx.setScreen(() -> new AssetSetupScreen(title, AssetPack.Status.MISSING));
			ctx.waitTicks(3);
			ctx.takeScreenshot("wkclimb-assets-page-confirm-" + AssetBuilder.Step.values()[i].toString().toLowerCase());
		}
		String[][] states = { { "NO_GAME", "" }, { "NO_PYTHON", "" }, { "NO_VENV", "" },
				{ "FAILED", "Traceback (most recent call last):|  File \"extract_wk_assets.py\", line 451|RuntimeError: example failure" } };
		for (String[] s : states) {
			ctx.runOnClient(mc -> AssetBuilder.INSTANCE.setStateForTest(AssetBuilder.State.valueOf(s[0]), null, 0,
					s[1].isEmpty() ? List.of() : List.of(s[1].split("\\|"))));
			ctx.setScreen(() -> new AssetSetupScreen(title, AssetPack.Status.MISSING));
			ctx.waitTicks(3);
			ctx.takeScreenshot("wkclimb-assets-page-" + s[0].toLowerCase());
		}
		ctx.runOnClient(mc -> AssetBuilder.INSTANCE.setStateForTest(AssetBuilder.State.IDLE, null, 0, List.of()));
		for (String page : List.of("NOT_FOUND", "PICKER_INFO", "PICKER_OPEN", "PICKER_WRONG", "PICKER_FAILED", "PYTHON_INFO")) {
			ctx.setScreen(() -> {
				AssetSetupScreen screen = new AssetSetupScreen(title, AssetPack.Status.MISSING);
				screen.showPageForTest(page);
				return screen;
			});
			ctx.waitTicks(3);
			ctx.takeScreenshot("wkclimb-assets-page-" + page.toLowerCase());
		}
		ctx.setScreen(() -> title);
	}

	private static void removePack(ClientGameTestContext ctx) {
		CompletableFuture<Void> reload = ctx.computeOnClient(mc -> {
			var repo = mc.getResourcePackRepository();
			List<String> selected = new ArrayList<>(repo.getSelectedIds());
			selected.remove(AssetPack.ID);
			repo.setSelected(selected);
			mc.options.updateResourcePacks(repo);
			deleteTree(AssetPack.packDir());
			repo.reload();
			return mc.reloadResourcePacks();
		});
		ctx.waitFor(mc -> reload.isDone() && mc.gui.overlay() == null, 2400);
	}

	private static void deleteTree(Path dir) {
		if (!Files.exists(dir)) return;
		try (Stream<Path> files = Files.walk(dir)) {
			for (Path f : (Iterable<Path>) files.sorted(Comparator.reverseOrder())::iterator) Files.delete(f);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void write(Path file, String text) {
		try {
			Files.writeString(file, text);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void check(ClientGameTestContext ctx, String what, Predicate<Minecraft> ok) {
		boolean result = ctx.computeOnClient(ok::test);
		LOG.info("[wkclimb] {} {}", result ? "PASS" : "FAIL", what);
		if (!result) throw new AssertionError(what);
	}
}
