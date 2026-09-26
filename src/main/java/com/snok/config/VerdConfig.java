package com.snok.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.snok.Verdiumuim;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Verdiumuim configuration. Plain GSON - no AutoConfig dependency.
 * Every rendering feature has an independent toggle so users on unsupported
 * drivers can fall back to the vanilla path per-feature.
 */
public final class VerdConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("verdiumuim.json");

	// ------------------------------------------------------------------
	// Terrain pipeline
	// ------------------------------------------------------------------
	/** Master switch: greedy meshing + packed quads + MDI batch rendering. */
	public boolean enabled = true;
	/** Greedy-merge coplanar voxel faces horizontally and vertically. */
	public boolean greedyMeshing = true;
	/** Render all visible sections in a single glMultiDrawElementsIndirect call. */
	public boolean singleCommandBatching = true;
	/** 4-vertex triangle strips + procedural corner generation in the vertex shader. */
	public boolean triangleStrips = true;

	// ------------------------------------------------------------------
	// Buffers
	// ------------------------------------------------------------------
	/** Number of persistent-mapped staging buffers in the transfer ring. */
	public int stagingBufferCount = 4;
	/** Size in MiB of each staging buffer slot. */
	public int stagingBufferMib = 8;
	/** Log GPU buffer allocations (debug category). */
	public boolean logBufferAllocations = false;
	/** Log fence wait times and skipped-transfer statistics (debug category). */
	public boolean logFenceWaits = false;

	// ------------------------------------------------------------------
	// Shaders
	// ------------------------------------------------------------------
	/** Log shader compile/link steps (debug category). */
	public boolean logShaderCompiles = false;
	/** Force-disable GL_KHR_debug callbacks even when supported. */
	public boolean disableGlDebug = false;

	// ------------------------------------------------------------------
	// LOD
	// ------------------------------------------------------------------
	/** Build 2x-scaled LOD meshes for distant terrain. */
	public boolean lodEnabled = true;
	/** Distance (blocks) beyond which sections are rendered as LOD. */
	public int lodStartDistance = 96;
	/** Distance (blocks) at which LOD terrain fully sinks underground at the HQ boundary. */
	public int lodSinkDistance = 128;
	/** Log LOD rebuilds and sink offsets (debug category). */
	public boolean logLod = false;

	// ------------------------------------------------------------------
	// Volumetrics
	// ------------------------------------------------------------------
	/** 2D volumetric sun beams along shadow edges. */
	public boolean sunBeams = true;
	/** Sun beam opacity multiplier (0.0 - 1.0). */
	public float sunBeamIntensity = 0.45f;
	/** Log sun-beam sampling stats (debug category). */
	public boolean logVolumetrics = false;

	// ------------------------------------------------------------------
	// Renderer (master debug category)
	// ------------------------------------------------------------------
	/** Log render batch statistics: draw counts, quads, MDI command size. */
	public boolean logRenderer = false;

	/** Fallback one-way latch: set when the GL probe fails, never unset. */
	public transient boolean glFallback = false;

	// ------------------------------------------------------------------

	public static VerdConfig get() {
		return Holder.INSTANCE;
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());
			Files.writeString(PATH, GSON.toJson(this));
		} catch (IOException e) {
			Verdiumuim.LOGGER.error("Failed to save verdiumuim config", e);
		}
	}

	private static VerdConfig load() {
		VerdConfig cfg = new VerdConfig();
		if (Files.exists(PATH)) {
			try {
				VerdConfig loaded = GSON.fromJson(Files.readString(PATH), VerdConfig.class);
				if (loaded != null) cfg = loaded;
			} catch (Exception e) {
				Verdiumuim.LOGGER.error("Failed to read verdiumuim config, using defaults", e);
			}
		}
		return cfg;
	}

	private static final class Holder {
		private static final VerdConfig INSTANCE = load();
	}
}
