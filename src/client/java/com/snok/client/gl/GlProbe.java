package com.snok.client.gl;

import com.snok.config.VerdConfig;
import com.snok.log.PerfLog;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.opengl.GL43.*;

/**
 * One-time capability probe. MDI + SSBO paths require OpenGL 4.3+;
 * anything below (common on old Intel iGPUs) flips the config into
 * permanent vanilla fallback so the game keeps running.
 */
public final class GlProbe {
	private static final Logger LOG = LoggerFactory.getLogger("Verdiumuim/GL");
	private static boolean probed = false;
	private static boolean mdiSupported = false;
	private static boolean ssboSupported = false;
	private static boolean debugSupported = false;

	private GlProbe() {
	}

	public static void probe() {
		if (probed) return;
		probed = true;

		GLCapabilities caps = GL.getCapabilities();
		String renderer = glGetString(GL_RENDERER);
		String version = glGetString(GL_VERSION);
		LOG.info("GL probe: {} on {}", renderer, version);

		mdiSupported = caps.OpenGL43 || caps.GL_ARB_multi_draw_indirect;
		ssboSupported = caps.OpenGL43 || caps.GL_ARB_shader_storage_buffer_object;
		// gl_DrawID comes from a separate extension; some 3.3-era Intel drivers
		// expose MDI/SSBO ARBs but not this one, so check it explicitly.
		boolean drawIdSupported = caps.OpenGL43 || caps.GL_ARB_shader_draw_parameters;
		debugSupported = !VerdConfig.get().disableGlDebug && caps.GL_KHR_debug;

		PerfLog.info(PerfLog.Cat.SHADERS, "caps: mdi=%s ssbo=%s khr_debug=%s",
				mdiSupported, ssboSupported, debugSupported);

		if (!mdiSupported || !ssboSupported || !drawIdSupported) {
			VerdConfig.get().glFallback = true;
			LOG.warn("OpenGL 4.3 pipeline unavailable (mdi={} ssbo={} gl_DrawID={}) - vanilla render fallback active. "
					+ "On Intel iGPUs, updated drivers or Linux/Mesa (GL 4.5+) enable the optimized path.",
				mdiSupported, ssboSupported, drawIdSupported);
		}
	}

	public static boolean mdiSupported() {
		return mdiSupported;
	}

	public static boolean ssboSupported() {
		return ssboSupported;
	}

	public static boolean debugEnabled() {
		return debugSupported;
	}
}
