package dev.fizz.wkclimb.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.fizz.wkclimb.ClimbConfig;
import dev.fizz.wkclimb.WKClimb;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.sdl.SDLScancode;

public class WKClimbClient implements ClientModInitializer {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(WKClimb.id("climbing"));
	public static KeyMapping toggleKey;
	/** White Knuckle's Focus (F in the game). Unbound by default because F is Minecraft's swap-hands key. */
	public static KeyMapping focusKey;
	/** False while connected to a server that doesn't have the mod (climbing is then kept off). */
	private static boolean serverHasMod = true;
	private static boolean enabledBeforeUnsupported;

	@Override
	public void onInitializeClient() {
		ClimbConfig.load();
		ClimbController.INSTANCE.setEnabled(ClimbConfig.enabledOnStart);

		// The White Knuckle pack can be switched on or off at any time (the setup screen, Options >
		// Resource Packs), so the cached "are its sprites there?" answers are dropped on every reload.
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(WKClimb.id("sprite_checks"),
				(ResourceManagerReloadListener) manager -> {
					ClimbHands.forgetTextures();
					ClimbDamage.forgetAvailable();
				});

		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.wkclimb.toggle", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_R, CATEGORY));

		focusKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.wkclimb.focus", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_UNKNOWN, CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ClimbKeybinds.applyOnce(client);
			dev.fizz.wkclimb.client.assets.AssetPack.tick(client);
			ClimbCrosshair.setFocus(client.gui.screen() == null && focusKey.isDown());
			if (client.player != null && !client.isPaused()) ClimbHands.tick(client.player);
			while (toggleKey.consumeClick()) {
				boolean on = !ClimbController.INSTANCE.isEnabled();
				if (on && !serverHasMod) {
					if (client.player != null) client.player.sendOverlayMessage(Component.translatable("wkclimb.server_missing.short"));
					continue;
				}
				ClimbController.INSTANCE.setEnabled(on);
				if (client.player != null) {
					client.player.sendOverlayMessage(Component.translatable(on ? "wkclimb.hands.on" : "wkclimb.hands.off"));
				}
			}
		});

		HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR, WKClimb.id("hands"), ClimbHands::render);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR, WKClimb.id("damage"), ClimbDamage::render);
		// White Knuckle has no hotbar, hearts, hunger, armour, air or XP bars: hide them in WK mode.
		for (var id : java.util.List.of(VanillaHudElements.HOTBAR, VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR,
				VanillaHudElements.ARMOR_BAR, VanillaHudElements.AIR_BAR, VanillaHudElements.EXPERIENCE_LEVEL,
				VanillaHudElements.INFO_BAR, VanillaHudElements.HELD_ITEM_TOOLTIP)) {
			HudElementRegistry.replaceElement(id, vanilla -> (g, delta) -> {
				if (!ClimbController.INSTANCE.isEnabled()) vanilla.extractRenderState(g, delta);
			});
		}
		// The climbing movement needs the mod on the server: without it a hanging player looks like
		// they are flying and gets kicked (or flagged by anti-cheat). Single-player and LAN hosts
		// always have it; elsewhere, check that the server speaks the mod's channel.
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			serverHasMod = client.isLocalServer()
					|| net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(dev.fizz.wkclimb.net.ClimbStatePayload.TYPE);
			if (!serverHasMod) {
				enabledBeforeUnsupported = ClimbController.INSTANCE.isEnabled();
				ClimbController.INSTANCE.setEnabled(false);
				client.execute(() -> {
					if (client.player != null) client.player.sendSystemMessage(Component.translatable("wkclimb.server_missing"));
				});
			}
			ClimbController.INSTANCE.onJoin();
		});
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			if (!serverHasMod) ClimbController.INSTANCE.setEnabled(enabledBeforeUnsupported);
			serverHasMod = true;
		});
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				dev.fizz.wkclimb.net.FallGripPayload.TYPE,
				(payload, context) -> ClimbController.INSTANCE.onFallHurt(payload.grab(), payload.land()));
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (g, delta) -> {
			if (!ClimbCrosshair.render(g, delta)) vanilla.extractRenderState(g, delta);
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, WKClimb.id("hand_icons"), ClimbHud::render);
	}
}
