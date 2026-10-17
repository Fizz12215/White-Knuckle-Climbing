package dev.fizz.wkclimb.client;

import dev.fizz.wkclimb.ClimbConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

import java.util.List;

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
		list.addBig(new VolumeSlider("sound_volume", ClimbConfig.soundVolume, ClimbConfig.MAX_SOUND, v -> ClimbConfig.soundVolume = v));
		list.addSmall(List.<net.minecraft.client.gui.components.AbstractWidget>of(
				new VolumeSlider("grabbing_volume", ClimbConfig.grabbingVolume, ClimbConfig.MAX_GRABBING, v -> ClimbConfig.grabbingVolume = v),
				new VolumeSlider("landing_volume", ClimbConfig.landingVolume, ClimbConfig.MAX_LANDING, v -> ClimbConfig.landingVolume = v),
				new VolumeSlider("footsteps_volume", ClimbConfig.footstepsVolume, ClimbConfig.MAX_FOOTSTEPS, v -> ClimbConfig.footstepsVolume = v),
				new VolumeSlider("wind_volume", ClimbConfig.windVolume, ClimbConfig.MAX_WIND, v -> ClimbConfig.windVolume = v),
				new VolumeSlider("hurt_volume", ClimbConfig.hurtVolume, ClimbConfig.MAX_HURT, v -> ClimbConfig.hurtVolume = v),
				new VolumeSlider("low_health_volume", ClimbConfig.lowHealthVolume, ClimbConfig.MAX_LOW_HEALTH, v -> ClimbConfig.lowHealthVolume = v),
				new VolumeSlider("fatigue_volume", ClimbConfig.fatigueVolume, ClimbConfig.MAX_FATIGUE, v -> ClimbConfig.fatigueVolume = v),
				new VolumeSlider("ice_volume", ClimbConfig.iceVolume, ClimbConfig.MAX_ICE, v -> ClimbConfig.iceVolume = v)));
	}

	@Override
	public void removed() {
		super.removed();
		ClimbConfig.save();
	}
}
