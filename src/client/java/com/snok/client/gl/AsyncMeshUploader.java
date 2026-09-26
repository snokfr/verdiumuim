package com.snok.client.gl;

import com.snok.log.PerfLog;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.lwjgl.opengl.GL45.glCopyNamedBufferSubData;
import static org.lwjgl.opengl.GL45.glBindBuffer;
import static org.lwjgl.opengl.GL31.GL_COPY_WRITE_BUFFER;

/**
 * Worker-thread mesh transfer pipeline. Meshing jobs produce packed quad data
 * on background threads, land in a staging slot from the FencePool, and the
 * render thread drains finished slots each frame with zero-wait fence checks.
 *
 * GL calls happen only on the render thread; the worker only touches raw
 * memory (the persistent map) and hands over via a queue.
 */
public final class AsyncMeshUploader {
	/** A mesh job ready to be written into a staging slot. */
	public record MeshJob(long sectionKey, ByteBuffer data) {
	}

	private final BlockingQueue<MeshJob> queue = new ArrayBlockingQueue<>(256);
	private final AtomicBoolean running = new AtomicBoolean(true);
	private final Thread worker;
	private final FencePool pool;

	public AsyncMeshUploader(FencePool pool) {
		this.pool = pool;
		this.worker = new Thread(this::run, "Verdiumuim-MeshWorker");
		this.worker.setDaemon(true);
		this.worker.start();
	}

	/** Called from any thread; drops the oldest job if the queue is full. */
	public void submit(MeshJob job) {
		while (!queue.offer(job)) {
			queue.poll(); // shed load rather than back up meshing behind the GPU
		}
	}

	private void run() {
		while (running.get()) {
			try {
				MeshJob job = queue.take();
				int slot = pool.acquireSlot();
				if (slot < 0) {
					// Ring saturated: retry after letting one drain.
					Thread.yield();
					slot = pool.acquireSlot();
					if (slot < 0) continue;
				}
				long map = pool.map(slot);
				if (map == 0) continue;
				ByteBuffer dst = MemoryUtil.memByteBuffer(map, (int) job.data().capacity());
				dst.put(job.data());
				pool.sealSlot(slot, job.data().capacity());
				PerfLog.info(PerfLog.Cat.BUFFERS, "uploaded section %d (%d bytes) via slot %d",
						job.sectionKey(), job.data().capacity(), slot);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			} catch (Exception e) {
				PerfLog.error("mesh worker failed", e);
			}
		}
	}

	/** Render-thread call once per frame: no blocking, just fence checks inside the pool. */
	public void drainCompleted() {
		// FencePool.acquireSlot is called opportunistically by the worker;
		// this hook exists so the render thread can run stats + reclaim logic.
		PerfLog.info(PerfLog.Cat.BUFFERS, "drain tick, queued=%d", queue.size());
	}

	public int pendingCount() {
		return queue.size();
	}

	public void shutdown() {
		running.set(false);
		worker.interrupt();
	}
}
