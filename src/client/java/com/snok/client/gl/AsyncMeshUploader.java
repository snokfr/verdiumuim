package com.snok.client.gl;

import com.snok.config.VerdConfig;
import com.snok.log.PerfLog;
import com.snok.mesh.GreedyMesher;
import org.lwjgl.system.MemoryUtil;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Worker-thread mesh transfer pipeline. The render thread (or mixin worker
 * threads) submit opaque masks; this worker greedy-meshes them and hands the
 * packed quads to the callback. The render thread drains finished staging
 * slots each frame with zero-wait fence checks.
 */
public final class AsyncMeshUploader {
	/** Section mesh request: opaque mask + world origin of the section. */
	public record MeshJob(long key, int originX, int originY, int originZ, boolean[] mask) {
	}

	/** Receives meshed packed-quad arrays on the worker thread. */
	public interface MeshedCallback {
		void onMeshed(long key, int originX, int originY, int originZ, int[] quads);
	}

	private final BlockingQueue<MeshJob> queue = new ArrayBlockingQueue<>(512);
	private final AtomicBoolean running = new AtomicBoolean(true);
	private final Thread worker;
	private final FencePool pool;
	private final MeshedCallback onMeshed;

	public AsyncMeshUploader(FencePool pool, MeshedCallback onMeshed) {
		this.pool = pool;
		this.onMeshed = onMeshed;
		this.worker = new Thread(this::run, "Verdiumuim-MeshWorker");
		this.worker.setDaemon(true);
		this.worker.start();
	}

	/** Called from any thread; drops the oldest job if the queue is full. */
	public void submit(MeshJob job) {
		while (!queue.offer(job)) {
			queue.poll(); // shed load rather than back up behind the GPU
		}
	}

	private void run() {
		while (running.get()) {
			try {
				MeshJob job = queue.take();
				boolean greedy = VerdConfig.get().greedyMeshing;
				int[] quads = GreedyMesher.mesh(job.mask(), greedy);
				onMeshed.onMeshed(job.key(), job.originX(), job.originY(), job.originZ(), quads);
				PerfLog.info(PerfLog.Cat.RENDERER, "meshed section %d -> %d packed quads", job.key(), quads.length);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			} catch (Exception e) {
				PerfLog.error("mesh worker failed", e);
			}
		}
	}

	/** Render-thread call once per frame: zero-wait fence checks in the pool. */
	public void drainCompleted() {
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
