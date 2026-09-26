package com.snok.client.integration;

import com.snok.client.config.VerdConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu entrypoint: wires the Verdiumuim button in the Mods list to the
 * tabbed Cloth Config screen.
 */
public class VerdiumuimModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return VerdConfigScreen::build;
	}
}
