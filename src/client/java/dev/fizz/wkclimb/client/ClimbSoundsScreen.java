package dev.fizz.wkclimb.client;

import dev.fizz.wkclimb.ClimbConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

import static dev.fizz.wkclimb.client.ClimbSettingsScreen.volume;

/**
 * The volume of the mod's sounds (ClimbSounds.Group), opened from the settings screen's Sounds... button.
 * Applies at once; saved on close.
 */
public class ClimbSoundsScreen extends OptionsSubScreen {
	public ClimbSoundsScreen(Screen parent) {
		super(parent, Minecraft.getInstance().options, Component.translatable("wkclimb.sounds.title"));
	}

	@Override
	protected void addOptions() {
		list.addBig(volume("sound_volume", ClimbConfig.soundVolume, v -> ClimbConfig.soundVolume = v));
		list.addSmall(
				volume("grabbing_volume", ClimbConfig.grabbingVolume, v -> ClimbConfig.grabbingVolume = v),
				volume("landing_volume", ClimbConfig.landingVolume, v -> ClimbConfig.landingVolume = v),
				volume("footsteps_volume", ClimbConfig.footstepsVolume, v -> ClimbConfig.footstepsVolume = v),
				volume("wind_volume", ClimbConfig.windVolume, v -> ClimbConfig.windVolume = v),
				volume("hurt_volume", ClimbConfig.hurtVolume, v -> ClimbConfig.hurtVolume = v),
				volume("low_health_volume", ClimbConfig.lowHealthVolume, v -> ClimbConfig.lowHealthVolume = v),
				volume("fatigue_volume", ClimbConfig.fatigueVolume, v -> ClimbConfig.fatigueVolume = v),
				volume("ice_volume", ClimbConfig.iceVolume, v -> ClimbConfig.iceVolume = v));
	}

	@Override
	public void removed() {
		super.removed();
		ClimbConfig.save();
	}
}
