package com.snok.client.render;

import com.snok.client.gl.GlProbe;
import com.snok.client.gl.ShaderProgram;
import com.snok.log.PerfLog;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_TRIANGLE_STRIP;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.glDrawArraysInstanced;
import static org.lwjgl.opengl.GL33.glVertexAttribDivisor;

/**
 * GL 3.3-compatible batch renderer (LEGACY tier). Same greedy-meshed packed
 * quads as the MODERN path, but expanded to per-instance attributes on the
 * CPU instead of SSBOs, and drawn with one glDrawArraysInstanced call —
 * no MDI, no SSBOs, no gl_DrawID required.
 *
 * Per-instance attributes (12 floats):
 *   aOriginDir: section-local quad origin xyz + face dir
 *   aDims:      merged width, height, world origin x, world origin y
 *   aMisc:      world origin z, sink offset, -, -
 */
public final class LegacyBatchRenderer {
	private int vao;
	private int instanceVbo;
	private ShaderProgram program;

	private int instanceCapacity = 1 << 14;
	private int instanceCount;
	private boolean dirty = true;

	private final FloatBuffer matBuf = BufferUtils.createFloatBuffer(16);
	private FloatBuffer instanceData = MemoryUtil.memAllocFloat(instanceCapacity * 12);

	private static final int STRIDE_FLOATS = 12;

	public LegacyBatchRenderer() {
		vao = glGenVertexArrays();
		glBindVertexArray(vao);

		instanceVbo = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, instanceVbo);
		glBufferData(GL_ARRAY_BUFFER, (long) instanceCapacity * STRIDE_FLOATS * 4, GL_DYNAMIC_DRAW);

		setupAttrib(1, 0);  // aOriginDir
		setupAttrib(2, 4);  // aDims
		setupAttrib(3, 8);  // aMisc
		for (int i = 1; i <= 3; i++) glVertexAttribDivisor(i, 1);

		glBindVertexArray(0);
		program = new ShaderProgram("terrain_legacy",
				loadShader("terrain_legacy.vert"), loadShader("terrain_legacy.frag"));
		PerfLog.info(PerfLog.Cat.BUFFERS, "legacy batch renderer ready (GL 3.3 path), instanceCap=%d", instanceCapacity);
	}

	private void setupAttrib(int index, int offsetFloats) {
		glEnableVertexAttribArray(index);
		glVertexAttribPointer(index, 4, GL_FLOAT, false, STRIDE_FLOATS * 4, (long) offsetFloats * 4);
	}

	private static String loadShader(String file) {
		try (var in = LegacyBatchRenderer.class.getResourceAsStream("/assets/verdiumuim/shaders/" + file)) {
			return new String(in.readAllBytes());
		} catch (Exception e) {
			throw new IllegalStateException("missing shader " + file, e);
		}
	}

	public void markDirty() {
		dirty = true;
	}

	/** Flatten all committed meshes into the per-instance buffer. */
	public void reloadFromStore(SectionStore store) {
		if (!dirty) return;

		int needed = Math.max(1024, store.approxQuadCount());
		if (instanceData.capacity() < needed * STRIDE_FLOATS) {
			MemoryUtil.memFree(instanceData);
			instanceData = MemoryUtil.memAllocFloat(needed * STRIDE_FLOATS * 2);
		}
		instanceData.clear();
		instanceCount = 0;

		store.forEachMesh((key, mesh) -> {
			if (instanceData.remaining() < mesh.quads().length * STRIDE_FLOATS) return;
			for (int packed : mesh.quads()) {
				int x = com.snok.mesh.PackedQuad.x(packed);
				int y = com.snok.mesh.PackedQuad.y(packed);
				int z = com.snok.mesh.PackedQuad.z(packed);
				int dir = com.snok.mesh.PackedQuad.dir(packed);
				int w = com.snok.mesh.PackedQuad.w(packed) + 1;
				int h = com.snok.mesh.PackedQuad.h(packed) + 1;

				instanceData.put(x).put(y).put(z).put(dir);
				instanceData.put(w).put(h).put(mesh.originX()).put(mesh.originY());
				instanceData.put(mesh.originZ()).put(mesh.sinkOffset()).put(0).put(0);
				instanceCount++;
			}
		});
		dirty = false;
		PerfLog.info(PerfLog.Cat.RENDERER, "legacy commit: %d quads as instances", instanceCount);
	}

	/** One instanced draw call for every committed quad. */
	public void renderBatch(Matrix4f view, Matrix4f proj) {
		if (instanceCount == 0) return;

		glBindVertexArray(vao);
		if (dirty) return; // defensive

		glBindBuffer(GL_ARRAY_BUFFER, instanceVbo);
		if (instanceCount * STRIDE_FLOATS > instanceData.capacity()) return;
		glBufferData(GL_ARRAY_BUFFER, (long) instanceCapacity * STRIDE_FLOATS * 4, GL_DYNAMIC_DRAW);
		instanceData.flip();
		glBufferSubData(GL_ARRAY_BUFFER, 0, instanceData);

		program.use();
		matBuf.clear();
		view.get(matBuf);
		matBuf.flip();
		glUniformMatrix4fv(program.uniform("uView"), false, matBuf);
		matBuf.clear();
		proj.get(matBuf);
		matBuf.flip();
		glUniformMatrix4fv(program.uniform("uProj"), false, matBuf);
		glUniform1f(program.uniform("uSinkScale"), 1.0f);

		// THE draw call: every greedy-meshed quad, one invocation.
		glDrawArraysInstanced(GL_TRIANGLE_STRIP, 0, 4, instanceCount);
		glBindVertexArray(0);

		PerfLog.info(PerfLog.Cat.RENDERER, "legacy batch: %d quads, 1 draw call", instanceCount);
	}

	public void ensureCapacity() {
		// capacity handled lazily in reloadFromStore
	}

	public boolean isEnabled() {
		return GlProbe.tier() != GlProbe.Tier.FALLBACK;
	}

	public void destroy() {
		if (vao != 0) glDeleteVertexArrays(vao);
		if (instanceVbo != 0) glDeleteBuffers(instanceVbo);
		if (program != null) program.close();
		vao = instanceVbo = 0;
		MemoryUtil.memFree(instanceData);
	}
}
