package com.snok.client;

import com.snok.client.gl.AsyncMeshUploader;
import com.snok.client.gl.FencePool;
import com.snok.client.gl.GlProbe;
import com.snok.client.render.ChunkBatchRenderer;
import com.snok.client.render.LodManager;
import com.snok.client.render.SunBeamRenderer;
import com.snok.config.VerdConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint: probes GL capabilities, builds the render pipeline
 * (batch renderer + async uploader + LOD + sun beams), and owns the
 * per-frame frame hooks. Every subsystem is lazily constructed so a probe
 * failure keeps the game fully playable on the vanilla path.
 */
public class VerdiumuimClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("Verdiumuim");

	private static ChunkBatchRenderer batchRenderer;
	private static FencePool fencePool;
	private static AsyncMeshUploader meshUploader;
	private static SunBeamRenderer sunBeams;

	@Override
	public void onInitializeClient() {
		VerdConfig.get(); // force config load before any subsystem reads it

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// Lazy GL init on the first tick: the context is guaranteed alive here.
			if (batchRenderer == null && VerdConfig.get().enabled && !VerdConfig.get().glFallback) {
				initPipeline();
			}
		});

		LOGGER.info("Verdiumuim client init complete (pipeline deferred to first tick)");
	}

	private static void initPipeline() {
		try {
			GlProbe.probe();
			if (VerdConfig.get().glFallback) return;

			batchRenderer = new ChunkBatchRenderer();
			fencePool = FencePool.fromConfig();
			meshUploader = new AsyncMeshUploader(fencePool);
			sunBeams = new SunBeamRenderer();
			sunBeams.init();
			LOGGER.info("Verdiumuim render pipeline active: MDI batching + async meshing + LOD + sun beams");
		} catch (Exception e) {
			LOGGER.error("Pipeline init failed - falling back to vanilla rendering", e);
			VerdConfig.get().glFallback = true;
			destroyPipeline();
		}
	}

	private static void destroyPipeline() {
		if (meshUploader != null) meshUploader.shutdown();
		if (sunBeams != null) sunBeams.destroy();
		if (batchRenderer != null) batchRenderer.destroy();
		meshUploader = null;
		sunBeams = null;
		batchRenderer = null;
		fencePool = null;
	}

	public static ChunkBatchRenderer batchRenderer() {
		return batchRenderer;
	}

	public static AsyncMeshUploader meshUploader() {
		return meshUploader;
	}

	public static SunBeamRenderer sunBeams() {
		return sunBeams;
	}

	public static LodManager lodManager() {
		return new LodManager(); // stateless helper; cheap to hand out
	}
}
