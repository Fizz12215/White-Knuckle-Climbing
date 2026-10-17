package dev.fizz.wkclimb.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu's Config button for this mod. Only loaded when Mod Menu is installed (its "modmenu" entrypoint). */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ClimbSettingsScreen::new;
	}
}
