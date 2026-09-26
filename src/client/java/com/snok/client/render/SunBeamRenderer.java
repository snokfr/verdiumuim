package com.snok.client.render;

import com.snok.client.gl.ShaderProgram;
import com.snok.config.VerdConfig;
import com.snok.log.PerfLog;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.render.state.CameraRenderState;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_ONE;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TRIANGLE_STRIP;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glDepthMask;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glBufferSubData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.glDisableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;
import static org.lwjgl.opengl.GL31.glDrawArraysInstanced;
import static org.lwjgl.opengl.GL33.glVertexAttribDivisor;

/**
 * 2D volumetric sun beams (GL 3.3 compatible). The scanner generates beam
 * columns at shadow edges with a 16-sample opacity estimate; this renderer
 * draws them as additive billboarded planes. Temporal smoothing happens
 * CPU-side (per-beam exponential average) so foliage flicker doesn't flicker
 * the beams - no SSBO needed on the legacy path.
 */
public final class SunBeamRenderer {
	public static final int MAX_BEAMS = 4096;
	private static final int STRIDE_FLOATS = 4; // x, y, z, opacity

	private int vao;
	private int instanceVbo;
	private ShaderProgram program;
	private boolean ready;

	private final FloatBuffer beamUpload = MemoryUtil.memAllocFloat(MAX_BEAMS * STRIDE_FLOATS);
	private final FloatBuffer matBuf = BufferUtils.createFloatBuffer(16);
	private final float[] accum = new float[MAX_BEAMS];

	public void init() {
		vao = glGenVertexArrays();
		glBindVertexArray(vao);

		instanceVbo = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, instanceVbo);
		glBufferData(GL_ARRAY_BUFFER, (long) MAX_BEAMS * STRIDE_FLOATS * 4, GL_DYNAMIC_DRAW);

		glEnableVertexAttribArray(0);
		glVertexAttribPointer(0, 3, GL_FLOAT, false, STRIDE_FLOATS * 4, 0);
		glEnableVertexAttribArray(1);
		glVertexAttribPointer(1, 1, GL_FLOAT, false, STRIDE_FLOATS * 4, 3 * 4);
		glVertexAttribDivisor(0, 1);
		glVertexAttribDivisor(1, 1);

		glBindVertexArray(0);

		program = new ShaderProgram("sunbeam", load("sunbeam.vert"), load("sunbeam.frag"));
		ready = true;
		PerfLog.info(PerfLog.Cat.VOLUMETRICS, "sun beam renderer ready (GL 3.3 path), capacity %d beams", MAX_BEAMS);
	}

	private static String load(String file) {
		try (var in = SunBeamRenderer.class.getResourceAsStream("/assets/verdiumuim/shaders/" + file)) {
			return new String(in.readAllBytes());
		} catch (Exception e) {
			throw new IllegalStateException("missing shader " + file, e);
		}
	}

	public void beginFrame() {
		beamUpload.clear();
	}

	public void addBeam(int x, int y, int z, int extent, float opacity) {
		if (beamUpload.remaining() < STRIDE_FLOATS) return;
		beamUpload.put(x).put(y).put(z).put(opacity);
	}

	public void render(WorldRenderContext ctx, Matrix4f proj) {
		if (!ready) return;
		CameraRenderState cam = ctx.worldState().cameraRenderState;
		if (cam == null || cam.pos == null) return;

		int beams = Math.min(beamUpload.position() / STRIDE_FLOATS, MAX_BEAMS);
		if (beams == 0) return;

		// Temporal smoothing (CPU exponential average per beam slot).
		float blend = 0.1f;
		for (int i = 0; i < beams; i++) {
			float target = beamUpload.get(i * STRIDE_FLOATS + 3);
			accum[i] += (target - accum[i]) * blend;
			beamUpload.put(i * STRIDE_FLOATS + 3, accum[i]);
		}

		glBindVertexArray(vao);
		glBindBuffer(GL_ARRAY_BUFFER, instanceVbo);
		beamUpload.flip();
		glBufferSubData(GL_ARRAY_BUFFER, 0, beamUpload);

		Matrix4f view = new Matrix4f().setLookAt(
				(float) cam.pos.x, (float) cam.pos.y, (float) cam.pos.z,
				(float) cam.pos.x, (float) cam.pos.y, (float) cam.pos.z - 1,
				0, 1, 0);

		program.use();
		matBuf.clear();
		view.get(matBuf);
		matBuf.flip();
		glUniformMatrix4fv(program.uniform("uView"), false, matBuf);
		matBuf.clear();
		proj.get(matBuf);
		matBuf.flip();
		glUniformMatrix4fv(program.uniform("uProj"), false, matBuf);
		glUniform3f(program.uniform("uCamPos"), (float) cam.pos.x, (float) cam.pos.y, (float) cam.pos.z);
		glUniform1f(program.uniform("uIntensity"), VerdConfig.get().sunBeamIntensity);
		// Per-draw average opacity; per-beam values ride the instance buffer.
		glUniform1f(program.uniform("uOpacity"), 0.8f);

		glEnable(GL_BLEND);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE);
		glDisable(GL_DEPTH_TEST);
		glDepthMask(false);
		glDrawArraysInstanced(GL_TRIANGLE_STRIP, 0, 4, beams);
		glDepthMask(true);
		glEnable(GL_DEPTH_TEST);
		glDisable(GL_BLEND);
		glBindVertexArray(0);

		PerfLog.info(PerfLog.Cat.VOLUMETRICS, "rendered %d beams", beams);
	}

	private void glUniformMatrix4fv(int loc, boolean transpose, FloatBuffer m) {
		org.lwjgl.opengl.GL20.glUniformMatrix4fv(loc, transpose, m);
	}

	private void glUniform3f(int loc, float x, float y, float z) {
		org.lwjgl.opengl.GL20.glUniform3f(loc, x, y, z);
	}

	public void destroy() {
		if (!ready) return;
		glDeleteVertexArrays(vao);
		glDeleteBuffers(instanceVbo);
		if (program != null) program.close();
		MemoryUtil.memFree(beamUpload);
		ready = false;
	}
}
