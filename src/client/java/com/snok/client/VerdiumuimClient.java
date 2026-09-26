package com.snok.client;

import com.snok.client.gl.AsyncMeshUploader;
import com.snok.client.gl.FencePool;
import com.snok.client.gl.GlProbe;
import com.snok.client.render.ChunkBatchRenderer;
import com.snok.client.render.LegacyBatchRenderer;
import com.snok.client.render.PipelineHooks;
import com.snok.client.render.SectionStore;
import com.snok.client.render.SunBeamRenderer;
import com.snok.client.render.SunBeamScanner;
import com.snok.config.VerdConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint: probes GL capabilities, builds the render pipeline
 * (batch renderer + async uploader + section store + sun beams), registers
 * the frame hooks, and steps aside cleanly when Sodium owns terrain.
 */
public class VerdiumuimClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("Verdiumuim");

	private static final boolean SODIUM_PRESENT = FabricLoader.getInstance().isModLoaded("sodium");

	private static ChunkBatchRenderer batchRenderer;
	private static LegacyBatchRenderer legacyRenderer;
	private static SectionStore sectionStore;
	private static FencePool fencePool;
	private static AsyncMeshUploader meshUploader;
	private static SunBeamRenderer sunBeams;
	private static SunBeamScanner sunBeamScanner;

	/** Projection matrix captured from the GameRenderer each frame. */
	private static Matrix4f lastProjection;

	@Override
	public void onInitializeClient() {
		VerdConfig.get(); // force config load before any subsystem reads it
		announcedJoin = false;

		if (SODIUM_PRESENT) {
			LOGGER.info("Sodium detected: terrain batching disabled (Sodium owns the chunk pipeline); "
					+ "config GUI, logging, and sun beams remain active.");
		}

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (batchRenderer == null && legacyRenderer == null
					&& VerdConfig.get().enabled && !VerdConfig.get().glFallback) {
				initPipeline();
			}
			if (sunBeamScanner != null) sunBeamScanner.tick(client);
			if (!announcedJoin && client.world != null && client.player != null) {
				announcedJoin = true;
				announceGlInfo(client);
			}
			if (client.world == null) announcedJoin = false;
		});

		PipelineHooks.register();
		LOGGER.info("Verdiumuim client init complete (pipeline deferred to first tick, sodium={})", SODIUM_PRESENT);
	}

	private static void initPipeline() {
		try {
			GlProbe.probe();
			if (VerdConfig.get().glFallback) return;

			if (!SODIUM_PRESENT) {
				sectionStore = new SectionStore();
				fencePool = FencePool.fromConfig();
				meshUploader = new AsyncMeshUploader(fencePool, (key, x, y, z, quads) -> {
					if (sectionStore != null) {
						sectionStore.put(key, new SectionStore.SectionMesh(quads, x, y, z, 0, 0.0f));
					}
				});
				if (GlProbe.modernPipeline()) {
					batchRenderer = new ChunkBatchRenderer();
					LOGGER.info("Verdiumuim MODERN pipeline active: greedy meshing + MDI batching (GL 4.3+)");
				} else {
					legacyRenderer = new LegacyBatchRenderer();
					LOGGER.info("Verdiumuim LEGACY pipeline active: greedy meshing + single instanced draw (GL 3.3 path)");
				}
			}

			sunBeams = new SunBeamRenderer();
			sunBeams.init();
			sunBeamScanner = new SunBeamScanner();
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
		if (legacyRenderer != null) legacyRenderer.destroy();
		meshUploader = null;
		sunBeams = null;
		batchRenderer = null;
		legacyRenderer = null;
		sectionStore = null;
		fencePool = null;
	}

	public static ChunkBatchRenderer batchRenderer() {
		return batchRenderer;
	}

	public static LegacyBatchRenderer legacyRenderer() {
		return legacyRenderer;
	}

	public static SectionStore sectionStore() {
		return sectionStore;
	}

	public static AsyncMeshUploader meshUploader() {
		return meshUploader;
	}

	public static SunBeamRenderer sunBeams() {
		return sunBeams;
	}

	private static boolean announcedJoin = false;

	/** One-shot chat report of detected GL tier when the player enters a world. */
	private static void announceGlInfo(MinecraftClient client) {
		String tier;
		tier = switch (GlProbe.tier()) {
			case MODERN -> "MODERN (GL 4.3+): MDI batching + SSBOs active";
			case LEGACY -> "LEGACY (" + GlProbe.glVersion() + "): greedy meshing + single instanced draw active";
			case FALLBACK -> "FALLBACK: OpenGL " + GlProbe.glVersion() + " below 3.3 - vanilla rendering";
		};
		client.inGameHud.getChatHud().addMessage(
				Text.literal("[Verdiumuim] GPU: " + GlProbe.glRenderer() + " | Pipeline: " + tier));
		if (SODIUM_PRESENT) {
			client.inGameHud.getChatHud().addMessage(
					Text.literal("[Verdiumuim] Sodium detected - terrain batching handed off to Sodium; "
							+ "sun beams + config remain active"));
		}
	}

	public static boolean isSodiumPresent() {
		return SODIUM_PRESENT;
	}

	public static Matrix4f lastProjection() {
		return lastProjection;
	}

	public static void setLastProjection(Matrix4f m) {
		lastProjection = m;
	}
}
