package com.snok.client.gl;

import com.snok.config.VerdConfig;
import com.snok.log.PerfLog;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.opengl.GL43.*;

/**
 * One-time capability probe. Classifies the GPU into one of three tiers:
 *
 *  - MODERN (GL 4.3+): MDI batching + SSBOs + gl_DrawID - the full pipeline.
 *  - LEGACY (GL 3.3+): greedy meshing + packed quads + single-draw batching
 *    via instanced attributes and a texture buffer; no MDI/SSBO needed.
 *  - FALLBACK (below 3.3): vanilla rendering, mod stays non-intrusive.
 */
public final class GlProbe {
	public enum Tier { MODERN, LEGACY, FALLBACK }

	private static final Logger LOG = LoggerFactory.getLogger("Verdiumuim/GL");
	private static boolean probed;
	private static Tier tier = Tier.FALLBACK;
	private static String glVersion = "unknown";
	private static String glRenderer = "unknown";
	private static int glMajor, glMinor;

	private GlProbe() {
	}

	public static void probe() {
		if (probed) return;
		probed = true;

		GLCapabilities caps = GL.getCapabilities();
		glRenderer = glGetString(GL_RENDERER);
		glVersion = glGetString(GL_VERSION);
		parseVersion(glVersion);
		LOG.info("GL probe: {} | OpenGL {} ({}.{}-tier detection)", glRenderer, glVersion, glMajor, glMinor);

		// Tier selection uses CORE versions only. Several Intel Windows drivers
		// advertise ARB extension flags while their GLSL compiler rejects the
		// corresponding built-ins (gl_DrawID etc.), so ARB promotion is untrustworthy.
		boolean core43 = caps.OpenGL43;
		boolean gl33 = caps.OpenGL33 || (caps.GL_ARB_instanced_arrays && glMajor >= 3);

		if (core43) {
			tier = Tier.MODERN;
			LOG.info("Pipeline tier: MODERN (core GL 4.3: MDI + SSBO batching available)");
		} else if (gl33) {
			tier = Tier.LEGACY;
			LOG.info("Pipeline tier: LEGACY - GL {}.{} detected; greedy meshing + single-draw batching enabled, "
					+ "MDI/SSBO path unavailable", glMajor, glMinor);
		} else {
			tier = Tier.FALLBACK;
			LOG.warn("OpenGL 3.3 not available (detected {}.{}), vanilla render fallback active", glMajor, glMinor);
		}

		PerfLog.info(PerfLog.Cat.SHADERS, "caps: core43=%s gl33=%s tier=%s", core43, gl33, tier);
		VerdConfig.get().glFallback = tier == Tier.FALLBACK;
	}

	private static void parseVersion(String v) {
		try {
			String[] parts = v.split(" ")[0].split("\\.");
			glMajor = Integer.parseInt(parts[0]);
			glMinor = Integer.parseInt(parts[1]);
		} catch (Exception e) {
			glMajor = 0;
			glMinor = 0;
		}
	}

	public static Tier tier() {
		return tier;
	}

	public static boolean modernPipeline() {
		return tier == Tier.MODERN;
	}

	public static String glVersion() {
		return glVersion;
	}

	public static String glRenderer() {
		return glRenderer;
	}
}
