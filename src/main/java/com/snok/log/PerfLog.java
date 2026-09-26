package com.snok.log;

import com.snok.config.VerdConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Category-gated debug logger. Every category is a boolean in the config,
 * so users can watch GPU buffer allocations without drowning in LOD spam.
 */
public final class PerfLog {
	public enum Cat {
		RENDERER, BUFFERS, SHADERS, LOD, VOLUMETRICS
	}

	private static final Logger LOG = LoggerFactory.getLogger("Verdiumuim");

	private PerfLog() {
	}

	public static boolean enabled(Cat cat) {
		VerdConfig cfg = VerdConfig.get();
		if (cfg.glFallback) return false;
		return switch (cat) {
			case RENDERER -> cfg.logRenderer;
			case BUFFERS -> cfg.logBufferAllocations || cfg.logFenceWaits;
			case SHADERS -> cfg.logShaderCompiles;
			case LOD -> cfg.logLod;
			case VOLUMETRICS -> cfg.logVolumetrics;
		};
	}

	public static void info(Cat cat, String msg, Object... args) {
		if (enabled(cat)) LOG.info("[{}] {}", cat, msg.formatted(args));
	}

	public static void warn(Cat cat, String msg, Object... args) {
		if (enabled(cat)) LOG.warn("[{}] {}", cat, msg.formatted(args));
	}

	/** Always logged regardless of category toggles - real problems only. */
	public static void error(String msg, Throwable t) {
		LOG.error(msg, t);
	}
}
