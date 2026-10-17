package dev.fizz.wkclimb.client;

import dev.fizz.wkclimb.ClimbConfig;
import dev.fizz.wkclimb.client.assets.AssetPack;
import dev.fizz.wkclimb.client.assets.AssetSetupScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/**
 * The mod's settings (config/wkclimb.properties) in game, opened from Mod Menu's Config button. Each
 * option writes its ClimbConfig field as it changes, so it applies at once; the file is saved on close.
 */
public class ClimbSettingsScreen extends OptionsSubScreen {
	/** The FOV slider's lowest step, shown as "Minecraft's" and saved as 0. */
	private static final int FOV_VANILLA = 29;

	public ClimbSettingsScreen(Screen parent) {
		super(parent, Minecraft.getInstance().options, Component.translatable("wkclimb.settings.title"));
	}

	@Override
	protected void addOptions() {
		list.addHeader(Component.translatable("wkclimb.settings.climbing"));
		list.addSmall(
				toggle("ledges_only", ClimbConfig.ledgesOnly, v -> ClimbConfig.ledgesOnly = v),
				toggle("handholds_only", ClimbConfig.handholdsOnly, v -> ClimbConfig.handholdsOnly = v),
				// 0.5x to 2x in steps of 0.05
				slider("reach_multiplier", 10, 40, (int) Math.round(ClimbConfig.reachMultiplier * 20),
						v -> Component.literal(String.format("%.2fx", v / 20.0)), v -> ClimbConfig.reachMultiplier = v / 20.0),
				toggle("exact_step_height", ClimbConfig.exactStepHeight, v -> ClimbConfig.exactStepHeight = v));

		list.addHeader(Component.translatable("wkclimb.settings.camera"));
		int fov = ClimbConfig.fov <= 0 ? FOV_VANILLA : (int) Math.clamp(Math.round(ClimbConfig.fov), FOV_VANILLA + 1, 110);
		list.addSmall(
				slider("fov", FOV_VANILLA, 110, fov,
						v -> v == FOV_VANILLA ? Component.translatable("wkclimb.settings.fov.vanilla") : Component.literal(Integer.toString(v)),
						v -> ClimbConfig.fov = v == FOV_VANILLA ? 0 : v),
				toggle("hide_vanilla_hand", ClimbConfig.hideVanillaHand, v -> ClimbConfig.hideVanillaHand = v),
				toggle("enabled_on_start", ClimbConfig.enabledOnStart, v -> ClimbConfig.enabledOnStart = v));

		list.addHeader(Component.translatable("wkclimb.settings.sound"));
		list.addSmall(
				slider("low_health_volume", 0, 100, (int) Math.round(ClimbConfig.lowHealthVolume * 100),
						v -> v == 0 ? Component.translatable("options.off") : Component.literal(v + "%"),
						v -> ClimbConfig.lowHealthVolume = v / 100.0));

		list.addHeader(Component.translatable("wkclimb.settings.world"));
		// 0.5 to 1.2 blocks per unit in steps of 0.05
		list.addBig(slider("blocks_per_unit", 10, 24, (int) Math.clamp(Math.round(ClimbConfig.blocksPerUnit * 20), 10, 24),
				v -> Component.literal(String.format("%.2f", v / 20.0)), v -> ClimbConfig.blocksPerUnit = v / 20.0));

		list.addHeader(Component.translatable("wkclimb.settings.assets"));
		list.addSmall(
				toggle("asset_prompt", ClimbConfig.assetPrompt, v -> ClimbConfig.assetPrompt = v).createButton(options),
				Button.builder(Component.translatable("wkclimb.settings.build"), b -> openAssetSetup())
						.tooltip(Tooltip.create(Component.translatable("wkclimb.settings.build.tooltip"))).build());
	}

	private void openAssetSetup() {
		ClimbConfig.save(); // the setup screen reloads the file (to pick up a hand-edited wk_game_dir)
		minecraft.gui.setScreen(new AssetSetupScreen(this, AssetPack.check(minecraft.getResourceManager())));
	}

	@Override
	public void removed() {
		super.removed();
		ClimbConfig.save();
	}

	/** An on/off option; its label and tooltip come from wkclimb.settings.<name>(.tooltip). */
	private static OptionInstance<Boolean> toggle(String name, boolean value, java.util.function.Consumer<Boolean> set) {
		String key = "wkclimb.settings." + name;
		return OptionInstance.createBoolean(key, OptionInstance.cachedConstantTooltip(Component.translatable(key + ".tooltip")),
				value, set::accept);
	}

	/** A whole-number slider from min to max, shown with label and saved with set. */
	private static OptionInstance<Integer> slider(String name, int min, int max, int value, IntFunction<Component> label, IntConsumer set) {
		String key = "wkclimb.settings." + name;
		return new OptionInstance<>(key, OptionInstance.cachedConstantTooltip(Component.translatable(key + ".tooltip")),
				(caption, v) -> Options.genericValueLabel(caption, label.apply(v)),
				new OptionInstance.IntRange(min, max), Math.clamp(value, min, max), set::accept);
	}
}
