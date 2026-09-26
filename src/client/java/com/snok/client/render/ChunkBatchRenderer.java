package com.snok.client.render;

import com.snok.config.VerdConfig;
import com.snok.log.PerfLog;
import com.snok.mesh.PackedQuad;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL33.glVertexAttribDivisor;
import static org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER_BINDING;
import static org.lwjgl.opengl.GL43.*;

/**
 * Single-command terrain batch. All sections live in one VAO:
 *
 *  - VBO: packed quads (one int per merged face)
 *  - Per-quad attributes: vertexID (corner index), quadIndex (gl_InstanceID
 *    drives expansion in the vertex shader - see terrain.vert)
 *  - EBO: triangle-strip restart indices (4 verts/quad, restart between)
 *  - Indirect buffer: DrawElementsIndirectCommand array, one entry per
 *    visible section; a single glMultiDrawElementsIndirect draws everything
 *  - SSBO 0: per-section world origin + sink offset, indexed by gl_DrawID
 *
 * Intel-friendly properties: one VAO bind, one MDI call, no per-section GL
 * state changes, no mid-frame mapping.
 */
public final class ChunkBatchRenderer {
	/** gl_DrawID -> world origin (3 ints) + sink offset (1 float). */
	public static final int SECTION_SSBO_BINDING = 0;
	private static final int INITIAL_SECTIONS = 4096;
	private static final int COMMAND_INTS = 5; // count, instanceCount, firstIndex, baseVertex, baseInstance

	private int vao;
	private int vbo;      // packed quads
	private int ebo;      // strip indices
	private int indirect; // MDI command array
	private int sectionSsbo;

	private int sectionCapacity = INITIAL_SECTIONS;
	private int quadCapacity = 1 << 16;

	/** CPU-side mirror for building the frame's command list. */
	private final List<SectionDraw> visibleSections = new ArrayList<>(2048);

	public record SectionDraw(long sectionKey, int firstQuad, int quadCount,
	                          int worldX, int worldY, int worldZ, float sinkOffset) {
	}

	private final ByteBuffer cmdBuffer = MemoryUtil.memAlloc(INITIAL_SECTIONS * COMMAND_INTS * 4);
	private final ByteBuffer ssboBuffer = MemoryUtil.memAlloc(INITIAL_SECTIONS * 16);

	public ChunkBatchRenderer() {
		vao = glGenVertexArrays();
		glBindVertexArray(vao);

		vbo = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, vbo);
		glBufferData(GL_ARRAY_BUFFER, (long) quadCapacity * PackedQuad.BYTES, GL_DYNAMIC_DRAW);

		// Attribute 0: vertex corner id (procedural expansion in shader).
		glEnableVertexAttribArray(0);
		glVertexAttribPointer(0, 1, GL_INT, false, 4, 0);
		glVertexAttribDivisor(0, 0);

		ebo = glGenBuffers();
		glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
		buildStripIndexBuffer();

		indirect = glGenBuffers();
		glBindBuffer(GL_DRAW_INDIRECT_BUFFER, indirect);
		glBufferData(GL_DRAW_INDIRECT_BUFFER, (long) sectionCapacity * COMMAND_INTS * 4, GL_DYNAMIC_DRAW);

		sectionSsbo = glGenBuffers();
		glBindBuffer(GL_SHADER_STORAGE_BUFFER, sectionSsbo);
		glBufferData(GL_SHADER_STORAGE_BUFFER, (long) sectionCapacity * 16, GL_DYNAMIC_DRAW);
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, SECTION_SSBO_BINDING, sectionSsbo);

		glBindVertexArray(0);
		PerfLog.info(PerfLog.Cat.BUFFERS, "batch renderer ready: vao=%d quadCap=%d sectionCap=%d",
				vao, quadCapacity, sectionCapacity);
	}

	/** Strip index pattern: 0,1,2,3, RESTART, per quad. */
	private void buildStripIndexBuffer() {
		IntBuffer idx = MemoryUtil.memAllocInt(quadCapacity * 5);
		for (int q = 0; q < quadCapacity; q++) {
			int base = q * 4;
			idx.put(base).put(base + 1).put(base + 2).put(base + 3).put(0xFFFFFFFF);
		}
		idx.flip();
		glBufferData(GL_ELEMENT_ARRAY_BUFFER, idx, GL_DYNAMIC_DRAW);
		MemoryUtil.memFree(idx);
	}

	public void addSection(SectionDraw draw) {
		visibleSections.add(draw);
	}

	/** Upload command + SSBO arrays and issue the single MDI draw. */
	public void renderBatch() {
		int n = visibleSections.size();
		if (n == 0) return;
		if (n > sectionCapacity) growSectionCapacity(n);

		cmdBuffer.clear();
		ssboBuffer.clear();
		long totalQuads = 0;
		for (int i = 0; i < n; i++) {
			SectionDraw s = visibleSections.get(i);
			cmdBuffer.putInt(s.quadCount() * PackedQuad.VERTS) // count (indices)
					.putInt(1)                                  // instanceCount
					.putInt(s.firstQuad() * 5)                  // firstIndex (strip pattern stride)
					.putInt(s.firstQuad() * 4)                  // baseVertex
					.putInt(0);                                 // baseInstance
			ssboBuffer.putInt(s.worldX()).putInt(s.worldY()).putInt(s.worldZ());
			ssboBuffer.putFloat(s.sinkOffset());
			totalQuads += s.quadCount();
		}
		cmdBuffer.flip();
		ssboBuffer.flip();

		glBindBuffer(GL_DRAW_INDIRECT_BUFFER, indirect);
		glBufferSubData(GL_DRAW_INDIRECT_BUFFER, 0, cmdBuffer);

		glBindBuffer(GL_SHADER_STORAGE_BUFFER, sectionSsbo);
		glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, ssboBuffer);
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, SECTION_SSBO_BINDING, sectionSsbo);

		glBindVertexArray(vao);
		glEnable(GL_PRIMITIVE_RESTART);
		glPrimitiveRestartIndex(0xFFFFFFFF);
		glMultiDrawElementsIndirect(GL_TRIANGLE_STRIP, GL_UNSIGNED_INT, 0, n, 0);
		glDisable(GL_PRIMITIVE_RESTART);

		PerfLog.info(PerfLog.Cat.RENDERER, "MDI batch: %d sections, %d quads, 1 draw call", n, totalQuads);
	}

	public void clearFrame() {
		visibleSections.clear();
	}

	private void growSectionCapacity(int min) {
		int newCap = Math.max(sectionCapacity * 2, min);
		glBindBuffer(GL_DRAW_INDIRECT_BUFFER, indirect);
		glBufferData(GL_DRAW_INDIRECT_BUFFER, (long) newCap * COMMAND_INTS * 4, GL_DYNAMIC_DRAW);
		glBindBuffer(GL_SHADER_STORAGE_BUFFER, sectionSsbo);
		glBufferData(GL_SHADER_STORAGE_BUFFER, (long) newCap * 16, GL_DYNAMIC_DRAW);
		sectionCapacity = newCap;
		PerfLog.info(PerfLog.Cat.BUFFERS, "grew section capacity to %d", newCap);
	}

	/** Swap the packed-quad VBO contents (called from the async upload path). */
	public void replaceQuadData(ByteBuffer data, int quadCount) {
		if (quadCount > quadCapacity) {
			quadCapacity = Math.max(quadCapacity * 2, quadCount);
			glBindBuffer(GL_ARRAY_BUFFER, vbo);
			glBufferData(GL_ARRAY_BUFFER, (long) quadCapacity * PackedQuad.BYTES, GL_DYNAMIC_DRAW);
			glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
			buildStripIndexBuffer();
			PerfLog.info(PerfLog.Cat.BUFFERS, "grew quad capacity to %d", quadCapacity);
		}
		glBindBuffer(GL_ARRAY_BUFFER, vbo);
		glBufferSubData(GL_ARRAY_BUFFER, 0, data);
	}

	public boolean isEnabled() {
		return VerdConfig.get().enabled && !VerdConfig.get().glFallback;
	}

	public void destroy() {
		glDeleteVertexArrays(vao);
		glDeleteBuffers(vbo);
		glDeleteBuffers(ebo);
		glDeleteBuffers(indirect);
		glDeleteBuffers(sectionSsbo);
		MemoryUtil.memFree(cmdBuffer);
		MemoryUtil.memFree(ssboBuffer);
	}
}
