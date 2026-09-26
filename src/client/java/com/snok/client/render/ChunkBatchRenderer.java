package com.snok.client.render;

import com.snok.client.gl.ShaderProgram;
import com.snok.config.VerdConfig;
import com.snok.log.PerfLog;
import com.snok.mesh.PackedQuad;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL43.*;

/**
 * Single-command terrain batch. One VAO; per-frame section commands + origins
 * + quads go into SSBO 0 / an indirect buffer; one glMultiDrawElementsIndirect
 * draws every visible section as 4-vertex triangle strips expanded
 * procedurally in terrain.vert.
 */
public final class ChunkBatchRenderer {
	public static final int SECTION_SSBO_BINDING = 0;
	private static final int COMMAND_INTS = 5;

	private int vao;
	private int ebo;
	private int indirect;
	private ShaderProgram program;

	private int sectionCapacity = 4096;
	private int quadCapacity = 1 << 16;

	/** Committed GPU mirror, rebuilt from the store when meshes change. */
	private List<CommittedSection> committed = new ArrayList<>();
	private IntBuffer quadMirror = MemoryUtil.memAllocInt(4096);
	private int totalQuads = 0;
	private boolean dirty = true;

	private static final class CommittedSection {
		final int firstQuad;
		final int quadCount;
		final int worldX, worldY, worldZ;
		final float sinkOffset;

		CommittedSection(int firstQuad, int quadCount, int x, int y, int z, float sink) {
			this.firstQuad = firstQuad;
			this.quadCount = quadCount;
			this.worldX = x;
			this.worldY = y;
			this.worldZ = z;
			this.sinkOffset = sink;
		}
	}

	private final FloatBuffer matBuf = BufferUtils.createFloatBuffer(16);

	public ChunkBatchRenderer() {
		vao = glGenVertexArrays();
		glBindVertexArray(vao);

		ebo = glGenBuffers();
		glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
		buildIdentityIndexBuffer();

		indirect = glGenBuffers();
		glBindBuffer(GL_DRAW_INDIRECT_BUFFER, indirect);
		glBufferData(GL_DRAW_INDIRECT_BUFFER, (long) sectionCapacity * COMMAND_INTS * 4, GL_DYNAMIC_DRAW);
		glBindBuffer(GL_SHADER_STORAGE_BUFFER, indirect);
		glBufferData(GL_SHADER_STORAGE_BUFFER, ssboBytes(sectionCapacity), GL_DYNAMIC_DRAW);
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, SECTION_SSBO_BINDING, indirect);

		program = new ShaderProgram("terrain", loadShader("terrain.vert"), loadShader("terrain.frag"));

		glBindVertexArray(0);
		PerfLog.info(PerfLog.Cat.BUFFERS, "batch renderer ready: quadCap=%d sectionCap=%d",
				quadCapacity, sectionCapacity);
	}

	private static long ssboBytes(int cap) {
		return (long) (cap + 1) * 16 + (long) cap * 2 * PackedQuad.BYTES * 2;
	}

	private void buildIdentityIndexBuffer() {
		IntBuffer idx = MemoryUtil.memAllocInt(quadCapacity * 4);
		for (int i = 0; i < quadCapacity * 4; i++) idx.put(i);
		idx.flip();
		glBufferData(GL_ELEMENT_ARRAY_BUFFER, idx, GL_STATIC_DRAW);
		MemoryUtil.memFree(idx);
	}

	private static String loadShader(String file) {
		try (var in = ChunkBatchRenderer.class.getResourceAsStream("/assets/verdiumuim/shaders/" + file)) {
			return new String(in.readAllBytes());
		} catch (Exception e) {
			throw new IllegalStateException("missing shader " + file, e);
		}
	}

	/** Mark GPU data stale; next renderBatch reloads from the store. */
	public void markDirty() {
		dirty = true;
	}

	/** Rebuild the CPU mirror of committed sections from the store. */
	public void reloadFromStore(SectionStore store) {
		if (!dirty) return;

		List<CommittedSection> next = new ArrayList<>(store.size());
		int neededQuads = Math.max(4096, store.approxQuadCount() * 2);
		if (quadMirror.capacity() < neededQuads) {
			MemoryUtil.memFree(quadMirror);
			quadMirror = MemoryUtil.memAllocInt(neededQuads);
		}
		quadMirror.clear();
		totalQuads = 0;

		store.forEachMesh((key, mesh) -> {
			if (mesh.quads().length == 0) return;
			int firstQuad = totalQuads;
			for (int packed : mesh.quads()) {
				if (quadMirror.remaining() < 1) return;
				quadMirror.put(packed);
				totalQuads++;
			}
			next.add(new CommittedSection(firstQuad, mesh.quads().length,
					mesh.originX(), mesh.originY(), mesh.originZ(), mesh.sinkOffset()));
		});
		committed = next;
		dirty = false;
		PerfLog.info(PerfLog.Cat.RENDERER, "committed %d sections / %d quads to GPU mirror",
				committed.size(), totalQuads);
	}

	/**
	 * Upload visible-section commands + SSBO data and issue the single MDI
	 * draw. Returns sections drawn.
	 */
	public int renderBatch(Matrix4f view, Matrix4f proj, float camX, float camY, float camZ, float maxDistSq) {
		if (committed.isEmpty()) return 0;

		// Distance cull into the frame list.
		List<CommittedSection> visible = new ArrayList<>(committed.size());
		for (CommittedSection s : committed) {
			float dx = s.worldX + 8 - camX;
			float dy = s.worldY + 8 - camY;
			float dz = s.worldZ + 8 - camZ;
			if (dx * dx + dy * dy + dz * dz <= maxDistSq) visible.add(s);
		}
		int n = visible.size();
		if (n == 0) return 0;
		if (n > sectionCapacity) growSections(n);

		// Build SSBO: header, section records, quads of visible sections.
		int visQuads = 0;
		for (CommittedSection s : visible) visQuads += s.quadCount;

		IntBuffer ssbo = MemoryUtil.memAllocInt(4 + n * 4 + visQuads * 4);
		ssbo.put(n).put(0).put(0).put(0);
		for (CommittedSection s : visible) {
			ssbo.put(s.worldX).put(s.worldY).put(s.worldZ);
			ssbo.put(Float.floatToRawIntBits(s.sinkOffset));
		}
		// Emit quads with visible-local section ids.
		int visIdx = 0;
		for (CommittedSection s : visible) {
			for (int q = 0; q < s.quadCount; q++) {
				ssbo.put(visIdx);
				ssbo.put(quadMirror.get(s.firstQuad + q));
			}
			visIdx++;
		}
		ssbo.flip();

		IntBuffer cmds = MemoryUtil.memAllocInt(n * COMMAND_INTS);
		int quadCursor = 0;
		for (CommittedSection s : visible) {
			cmds.put(s.quadCount * 4)   // count: 4 verts per quad
					.put(1)                  // instanceCount
					.put(0)                  // firstIndex
					.put(quadCursor * 4)     // baseVertex
					.put(0);                 // baseInstance
			quadCursor += s.quadCount;
		}
		cmds.flip();

		glBindBuffer(GL_SHADER_STORAGE_BUFFER, indirect);
		glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, ssbo);
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, SECTION_SSBO_BINDING, indirect);

		glBindBuffer(GL_DRAW_INDIRECT_BUFFER, indirect);
		glBufferSubData(GL_DRAW_INDIRECT_BUFFER, 0, cmds);

		glBindVertexArray(vao);
		program.use();
		matBuf.clear();
		view.get(matBuf);
		matBuf.flip();
		glUniformMatrix4fv(program.uniform("uView"), false, matBuf);
		matBuf.clear();
		proj.get(matBuf);
		matBuf.flip();
		glUniformMatrix4fv(program.uniform("uProj"), false, matBuf);

		glEnable(GL_PRIMITIVE_RESTART_FIXED_INDEX);
		glMultiDrawElementsIndirect(GL_TRIANGLE_STRIP, GL_UNSIGNED_INT, 0, n, 0);
		glDisable(GL_PRIMITIVE_RESTART_FIXED_INDEX);
		glBindVertexArray(0);

		MemoryUtil.memFree(ssbo);
		MemoryUtil.memFree(cmds);

		PerfLog.info(PerfLog.Cat.RENDERER, "MDI batch: %d sections, %d quads, 1 draw call", n, visQuads);
		return n;
	}

	private void growSections(int min) {
		sectionCapacity = Math.max(sectionCapacity * 2, min);
		glBindBuffer(GL_DRAW_INDIRECT_BUFFER, indirect);
		glBufferData(GL_DRAW_INDIRECT_BUFFER, (long) sectionCapacity * COMMAND_INTS * 4, GL_DYNAMIC_DRAW);
		glBindBuffer(GL_SHADER_STORAGE_BUFFER, indirect);
		glBufferData(GL_SHADER_STORAGE_BUFFER, ssboBytes(sectionCapacity), GL_DYNAMIC_DRAW);
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, SECTION_SSBO_BINDING, indirect);
		PerfLog.info(PerfLog.Cat.BUFFERS, "grew section capacity to %d", sectionCapacity);
	}

	public boolean isEnabled() {
		return VerdConfig.get().enabled && !VerdConfig.get().glFallback;
	}

	public void destroy() {
		if (vao != 0) glDeleteVertexArrays(vao);
		if (ebo != 0) glDeleteBuffers(ebo);
		if (indirect != 0) glDeleteBuffers(indirect);
		if (program != null) program.close();
		vao = ebo = indirect = 0;
		MemoryUtil.memFree(quadMirror);
	}
}
