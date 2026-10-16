package dev.fizz.wkclimb.client.assets;

import com.google.gson.JsonParser;
import dev.fizz.wkclimb.ClimbConfig;
import dev.fizz.wkclimb.WKClimb;
import dev.fizz.wkclimb.assets.ExtractorSetup;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The White Knuckle Assets resource pack: whether it's there and current, when to offer building it,
 * and switching it on once built.
 */
public final class AssetPack {
	public static final String NAME = "White Knuckle Assets";
	/** Its id in Minecraft's pack list (a folder in resourcepacks/). */
	public static final String ID = "file/" + NAME;
	private static final Identifier HAND = WKClimb.id("textures/gui/hands/grab.png");
	private static final Identifier INFO = WKClimb.id("wk_pack_info.json");
	private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId(6000L);

	public enum Status { MISSING, OUTDATED, OK }

	private static boolean checkedThisSession;

	private AssetPack() {}

	public static Path packDir() {
		return Minecraft.getInstance().getResourcePackDirectory().resolve(NAME);
	}

	/** Judged from the loaded resources, so a pack under any name (e.g. a copied .zip) counts. */
	public static Status check(ResourceManager resources) {
		if (resources.getResource(HAND).isEmpty()) return Status.MISSING;
		return version(resources) >= ExtractorSetup.PACK_VERSION ? Status.OK : Status.OUTDATED;
	}

	/** The pack's content version; packs from before 0.3.2 have no info file (0). */
	static int version(ResourceManager resources) {
		Optional<Resource> info = resources.getResource(INFO);
		if (info.isEmpty()) return 0;
		try (Reader r = info.get().openAsReader()) {
			return JsonParser.parseReader(r).getAsJsonObject().get("version").getAsInt();
		} catch (Exception e) {
			return 0;
		}
	}

	/** Once per session, at the title screen: offer to build a missing or out-of-date pack. */
	public static void tick(Minecraft mc) {
		if (checkedThisSession || !(mc.gui.screen() instanceof TitleScreen) || mc.gui.overlay() != null) return;
		checkedThisSession = true;
		if (FabricLoader.getInstance().isModLoaded("wkclimb-gametest")) return; // keep the tests' screens as they are
		offer(mc);
	}

	/**
	 * Opens the setup screen if the pack is missing or out of date (unless the player said "Don't ask
	 * again"). Nothing happens without the player's OK: every step has its own confirmation.
	 */
	public static void offer(Minecraft mc) {
		Status status = check(mc.getResourceManager());
		if (status == Status.OK || !ClimbConfig.assetPrompt || AssetBuilder.INSTANCE.state().active()) return;
		mc.gui.setScreen(new AssetSetupScreen(mc.gui.screen(), status));
	}

	/** Is the pack in the active list? */
	public static boolean isOn(Minecraft mc) {
		return mc.getResourcePackRepository().getSelectedIds().contains(ID);
	}

	/**
	 * Switches the pack on and reloads resources (also when it was already on, so a rebuilt pack is
	 * used). Client thread; returns the reload.
	 */
	public static CompletableFuture<Void> enable(Minecraft mc) {
		var repo = mc.getResourcePackRepository();
		repo.reload();
		if (!isOn(mc)) {
			repo.addPack(ID);
			mc.options.updateResourcePacks(repo); // saves options.txt and reloads, since the list changed
			return mc.reloadResourcePacks(); // joins the reload already started
		}
		return mc.reloadResourcePacks();
	}

	/** When a build ends (client thread): say so if it worked. The setup screen shows anything else. */
	public static void onBuildFinished(AssetBuilder.State state) {
		if (state == AssetBuilder.State.DONE) toast(Minecraft.getInstance(), "wkclimb.assets.toast.done", "wkclimb.assets.toast.done.detail");
	}

	static void toast(Minecraft mc, String title, String detail) {
		SystemToast.addOrUpdate(mc.gui.toastManager(), TOAST, Component.translatable(title), Component.translatable(detail));
	}
}
