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
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_ONE;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glDepthMask;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL43.*;

/**
 * 2D volumetric sun beams. The scanner generates beam columns at shadow
 * edges with a 16-sample opacity estimate; this renderer draws them as
 * additive 2D planes and keeps a temporal accumulator SSBO so per-beam
 * opacity eases between scans (no foliage flicker).
 */
public final class SunBeamRenderer {
	public static final int MAX_BEAMS = 4096;
	private static final int BEAM_INTS = 4; // x, y, z, opacityBits

	private int vao;
	private int beamSsbo;
	private int accumSsbo;
	private ShaderProgram program;
	private boolean ready;

	private final IntBuffer beamUpload = MemoryUtil.memAllocInt(MAX_BEAMS * BEAM_INTS);
	private final FloatBuffer matBuf = BufferUtils.createFloatBuffer(16);
	private final float[] accum = new float[MAX_BEAMS];

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

	public void destroy() {
		if (!ready) return;
		glDeleteVertexArrays(vao);
		glDeleteBuffers(beamSsbo);
		glDeleteBuffers(accumSsbo);
		if (program != null) program.close();
		MemoryUtil.memFree(beamUpload);
		ready = false;
	}

	public void beginFrame() {
		beamUpload.clear();
	}

	public void addBeam(int x, int y, int z, int extent, float opacity) {
		if (beamUpload.remaining() < BEAM_INTS) return;
		beamUpload.put(x).put(y).put(z).put(Float.floatToRawIntBits(opacity));
	}

	public void render(WorldRenderContext ctx, Matrix4f proj) {
		if (!ready) return;
		if (!ready) return;
		CameraRenderState cam = ctx.worldState().cameraRenderState;
		if (cam == null || cam.pos == null) return;

		int beams = Math.min(beamUpload.position() / BEAM_INTS, MAX_BEAMS);
		if (beams == 0) return;

		// Temporal smoothing of per-beam opacity (CPU mirror of the SSBO
		// accumulator; uploaded so the shader can blend against it).
		float blend = 0.1f;
		FloatBuffer accumBuf = MemoryUtil.memAllocFloat(beams);
		for (int i = 0; i < beams; i++) {
			float target = Float.intBitsToFloat(beamUpload.get(i * BEAM_INTS + 3));
			accum[i] += (target - accum[i]) * blend;
			accumBuf.put(accum[i]);
		}
		accumBuf.flip();
		glBindBuffer(GL_SHADER_STORAGE_BUFFER, accumSsbo);
		glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, accumBuf);
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, accumSsbo);
		MemoryUtil.memFree(accumBuf);

		glBindBuffer(GL_SHADER_STORAGE_BUFFER, beamSsbo);
		glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, beamUpload.flip());

		Matrix4f view = new Matrix4f().setLookAt(
				(float) cam.pos.x, (float) cam.pos.y, (float) cam.pos.z,
				(float) cam.pos.x, (float) cam.pos.y, (float) cam.pos.z - 1,
				0, 1, 0);

		glBindVertexArray(vao); // empty VAO: fully procedural + SSBO driven
		program.use();
		matBuf.clear();
		view.get(matBuf);
		matBuf.flip();
		org.lwjgl.opengl.GL20.glUniformMatrix4fv(program.uniform("uView"), false, matBuf);
		matBuf.clear();
		proj.get(matBuf);
		matBuf.flip();
		org.lwjgl.opengl.GL20.glUniformMatrix4fv(program.uniform("uProj"), false, matBuf);
		org.lwjgl.opengl.GL20.glUniform1f(program.uniform("uIntensity"), VerdConfig.get().sunBeamIntensity);

		glEnable(GL_BLEND);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE);
		glDepthMask(false);
		// 4-vertex strip per beam; gl_VertexID corners, gl_DrawID -> beam data.
		glDrawArraysInstanced(GL_TRIANGLE_STRIP, 0, 4, beams);
		glDepthMask(true);
		glDisable(GL_BLEND);
		glBindVertexArray(0);
	}
}
