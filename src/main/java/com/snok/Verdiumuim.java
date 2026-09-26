package com.snok;

import com.snok.config.VerdConfig;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Verdiumuim implements ModInitializer {
	public static final String MOD_ID = "verdiumuim";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// Load config early so client-side subsystems read real values.
		VerdConfig.get();
		LOGGER.info("Verdiumuim {} loaded", "1.0.0");
	}

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}
}
