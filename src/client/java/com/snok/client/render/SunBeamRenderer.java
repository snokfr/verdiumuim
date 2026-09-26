package com.snok.client.render;

import com.snok.client.gl.ShaderProgram;
import com.snok.config.VerdConfig;
import com.snok.log.PerfLog;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL43.*;

/**
 * 2D volumetric sun beams. Beam planes are generated at shadow edges (where a
 * lit voxel column borders a shadowed one) and rendered with additive
 * blending. Opacity per beam uses 16 sunlight samples in the fragment shader;
 * a temporal accumulator SSBO blends the running average so swaying foliage
 * does not flicker the beams.
 */
public final class SunBeamRenderer {
	private static final int MAX_BEAMS = 4096;
	private static final int BEAM_INTS = 4; // x, y, extentW, extentD

	private int vao;
	private int beamSsbo;
	private int accumSsbo;
	private ShaderProgram program;
	private boolean ready;

	private final ByteBuffer beamUpload = MemoryUtil.memAlloc(MAX_BEAMS * BEAM_INTS * 4);

	public void init() {
		vao = glGenVertexArrays();
		beamSsbo = glGenBuffers();
		glBindBuffer(GL_SHADER_STORAGE_BUFFER, beamSsbo);
		glBufferData(GL_SHADER_STORAGE_BUFFER, (long) MAX_BEAMS * BEAM_INTS * 4, GL_DYNAMIC_DRAW);
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, beamSsbo);

		accumSsbo = glGenBuffers();
		glBindBuffer(GL_SHADER_STORAGE_BUFFER, accumSsbo);
		glBufferData(GL_SHADER_STORAGE_BUFFER, (long) MAX_BEAMS * 4, GL_DYNAMIC_DRAW);
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, accumSsbo);

		program = new ShaderProgram("sunbeam", load("sunbeam.vert"), load("sunbeam.frag"));
		ready = true;
		PerfLog.info(PerfLog.Cat.VOLUMETRICS, "sun beam renderer ready, capacity %d beams", MAX_BEAMS);
	}

	private static String load(String file) {
		try (var in = SunBeamRenderer.class.getResourceAsStream("/assets/verdiumuim/shaders/" + file)) {
			return new String(in.readAllBytes());
		} catch (Exception e) {
			throw new IllegalStateException("missing shader " + file, e);
		}
	}

	/** Frame hook: beams built by the shadow-edge scanner land here. */
	public void beginFrame() {
		beamUpload.clear();
	}

	public void addBeam(int x, int y, int z, int extentW, int extentD) {
		if (beamUpload.remaining() < BEAM_INTS * 4) return;
		beamUpload.putInt(x).putInt(y).putInt(z | (extentW << 16)).putInt(extentD);
	}

	public void render(FloatBuffer viewProj, float sunX, float sunY, float sunZ, float blendFactor) {
		if (!ready || !VerdConfig.get().sunBeams) return;
		int beams = beamUpload.position() / (BEAM_INTS * 4);
		if (beams == 0) return;

		glBindBuffer(GL_SHADER_STORAGE_BUFFER, beamSsbo);
		glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, beamUpload.flip());

		glBindVertexArray(vao); // empty VAO: everything is procedural + SSBO driven
		program.use();
		program.setMat4("uViewProj", viewProj);
		program.set3f("uSunDir", sunX, sunY, sunZ);
		program.set1f("uIntensity", VerdConfig.get().sunBeamIntensity);
		program.set1f("uBlendFactor", blendFactor);

		glEnable(GL_BLEND);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE); // additive-ish beams
		glDepthMask(false);
		// Procedural expansion: 4 verts per beam quad, gl_DrawID fetches beam data.
		glDrawArraysInstanced(GL_TRIANGLE_STRIP, 0, 4, beams);
		glDepthMask(true);
		glDisable(GL_BLEND);

		PerfLog.info(PerfLog.Cat.VOLUMETRICS, "rendered %d beams (blend %.2f)", beams, blendFactor);
	}

	public void destroy() {
		if (!ready) return;
		glDeleteVertexArrays(vao);
		glDeleteBuffers(beamSsbo);
		glDeleteBuffers(accumSsbo);
		program.close();
		MemoryUtil.memFree(beamUpload);
		ready = false;
	}
}
