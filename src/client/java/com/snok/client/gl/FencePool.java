package com.snok.client.gl;

import com.snok.config.VerdConfig;
import com.snok.log.PerfLog;
import org.lwjgl.opengl.GL44;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL45.*;

/**
 * Persistent-mapped staging ring with one glFenceSync per slot. Mesh data is
 * written on a worker thread; the render thread only ever *checks* fences
 * (zero-wait) and reclaims slots, so chunk uploads never stall a frame.
 *
 * Uses GL 4.4 persistent mapping when available; falls back to explicit
 * glMapBufferRange per slot otherwise (still fence-guarded, still no spin).
 */
public final class FencePool {
	private final int[] buffers;
	private final long[] maps;
	private final long[] fences;
	private final long[] sizes;
	private int head = 0;

	public FencePool(int slotCount, int slotBytes) {
		buffers = new int[slotCount];
		maps = new long[slotCount];
		fences = new long[slotCount];
		sizes = new long[slotCount];

		for (int i = 0; i < slotCount; i++) {
			buffers[i] = glCreateBuffers();
			glNamedBufferStorage(buffers[i], slotBytes,
					GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT);
			maps[i] = glMapNamedBufferRange(buffers[i], 0, slotBytes,
					GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT);
			fences[i] = 0;
			sizes[i] = 0;
			PerfLog.info(PerfLog.Cat.BUFFERS, "staging slot %d: %d bytes persistent-mapped", i, slotBytes);
		}
	}

	/** Claim the next slot whose GPU work is done (or never fenced). */
	public synchronized int acquireSlot() {
		for (int attempt = 0; attempt < buffers.length; attempt++) {
			int i = head;
			head = (head + 1) % buffers.length;
			if (fences[i] == 0) return i;
			int status = glClientWaitSync(fences[i], 0, 0); // timeout 0: never block
			if (status == GL_ALREADY_SIGNALED || status == GL_CONDITION_SATISFIED) {
				glDeleteSync(fences[i]);
				fences[i] = 0;
				return i;
			}
			PerfLog.info(PerfLog.Cat.BUFFERS, "slot %d still in flight, skipping", i);
		}
		return -1; // ring saturated; caller retries next frame
	}

	/** Arm the fence after a worker finished writing into slot i. */
	public synchronized void sealSlot(int i, int bytesWritten) {
		if (fences[i] != 0) glDeleteSync(fences[i]);
		fences[i] = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
		sizes[i] = bytesWritten;
		PerfLog.info(PerfLog.Cat.BUFFERS, "slot %d sealed: %d bytes", i, bytesWritten);
	}

	public synchronized long map(int i) {
		return maps[i];
	}

	public synchronized long size(int i) {
		return sizes[i];
	}

	public synchronized int buffer(int i) {
		return buffers[i];
	}

	public synchronized void destroy() {
		for (int i = 0; i < buffers.length; i++) {
			if (fences[i] != 0) glDeleteSync(fences[i]);
			if (maps[i] != 0) glUnmapNamedBuffer(buffers[i]);
			glDeleteBuffers(buffers[i]);
		}
		java.util.Arrays.fill(maps, 0);
		java.util.Arrays.fill(fences, 0);
	}

	public static FencePool fromConfig() {
		VerdConfig cfg = VerdConfig.get();
		return new FencePool(Math.max(2, cfg.stagingBufferCount),
				Math.max(2, cfg.stagingBufferMib) * 1024 * 1024);
	}
}
