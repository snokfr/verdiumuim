package com.snok.client.config;

import com.snok.Verdiumuim;
import com.snok.config.VerdConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Tabbed Cloth Config screen. Tabs: Terrain Pipeline / Buffers / Shaders /
 * LOD / Volumetrics / Debug Logging. Saving applies live (buffer pool resize,
 * shader-flag reload) and persists to config/verdiumuim.json.
 */
public final class VerdConfigScreen {
	private VerdConfigScreen() {
	}

	public static Screen build(Screen parent) {
		VerdConfig cfg = VerdConfig.get();
		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Text.literal("Verdiumuim - Intel GPU Optimizer"))
				.setSavingRunnable(() -> {
					cfg.save();
					Verdiumuim.LOGGER.info("Verdiumuim config saved; pipeline flags applied");
				});
		ConfigEntryBuilder eb = builder.entryBuilder();

		// ------------------------------------------------------------------
		// Terrain pipeline
		// ------------------------------------------------------------------
		ConfigCategory terrain = builder.getOrCreateCategory(Text.literal("Terrain Pipeline"));
		terrain.addEntry(eb.startBooleanToggle(Text.literal("Master enable"), cfg.enabled)
				.setTooltip(Text.literal("Greedy meshing + packed quads + MDI batch. Turning off restores the vanilla renderer."))
				.setDefaultValue(true)
				.setSaveConsumer(v -> cfg.enabled = v)
				.build());
		terrain.addEntry(eb.startBooleanToggle(Text.literal("Greedy meshing"), cfg.greedyMeshing)
				.setTooltip(Text.literal("Merge coplanar voxel faces horizontally and vertically before upload."))
				.setDefaultValue(true)
				.setSaveConsumer(v -> cfg.greedyMeshing = v)
				.build());
		terrain.addEntry(eb.startBooleanToggle(Text.literal("Single-command batching (MDI)"), cfg.singleCommandBatching)
				.setTooltip(Text.literal("One glMultiDrawElementsIndirect call per frame instead of one draw per section."))
				.setDefaultValue(true)
				.setSaveConsumer(v -> cfg.singleCommandBatching = v)
				.build());
		terrain.addEntry(eb.startBooleanToggle(Text.literal("Triangle strips"), cfg.triangleStrips)
				.setTooltip(Text.literal("4-vertex strips with procedural corners in the vertex shader instead of 6-vertex quads."))
				.setDefaultValue(true)
				.setSaveConsumer(v -> cfg.triangleStrips = v)
				.build());

		// ------------------------------------------------------------------
		// Buffers
		// ------------------------------------------------------------------
		ConfigCategory buffers = builder.getOrCreateCategory(Text.literal("Buffers"));
		buffers.addEntry(eb.startIntSlider(Text.literal("Staging buffer ring size"), cfg.stagingBufferCount, 2, 8)
				.setTooltip(Text.literal("Persistent-mapped glFenceSync ring for async chunk mesh uploads."))
				.setDefaultValue(4)
				.setSaveConsumer(v -> cfg.stagingBufferCount = v)
				.build());
		buffers.addEntry(eb.startIntSlider(Text.literal("Staging buffer size (MiB)"), cfg.stagingBufferMib, 2, 32)
				.setDefaultValue(8)
				.setSaveConsumer(v -> cfg.stagingBufferMib = v)
				.build());

		// ------------------------------------------------------------------
		// Shaders
		// ------------------------------------------------------------------
		ConfigCategory shaders = builder.getOrCreateCategory(Text.literal("Shaders"));
		shaders.addEntry(eb.startBooleanToggle(Text.literal("Disable GL_KHR_debug callbacks"), cfg.disableGlDebug)
				.setTooltip(Text.literal("Turn off the GL debug message stream. Useful on broken Intel drivers."))
				.setDefaultValue(false)
				.setSaveConsumer(v -> cfg.disableGlDebug = v)
				.build());

		// ------------------------------------------------------------------
		// LOD
		// ------------------------------------------------------------------
		ConfigCategory lod = builder.getOrCreateCategory(Text.literal("LOD"));
		lod.addEntry(eb.startBooleanToggle(Text.literal("Dynamic LOD terrain"), cfg.lodEnabled)
				.setTooltip(Text.literal("2x-scaled distant terrain that sinks underground at the HQ boundary."))
				.setDefaultValue(true)
				.setSaveConsumer(v -> cfg.lodEnabled = v)
				.build());
		lod.addEntry(eb.startIntSlider(Text.literal("LOD start distance"), cfg.lodStartDistance, 32, 256)
				.setDefaultValue(96)
				.setSaveConsumer(v -> cfg.lodStartDistance = v)
				.build());
		lod.addEntry(eb.startIntSlider(Text.literal("LOD sink distance"), cfg.lodSinkDistance, 64, 384)
				.setTooltip(Text.literal("Distance at which LOD terrain is fully sunk underground - no transparency pop."))
				.setDefaultValue(128)
				.setSaveConsumer(v -> cfg.lodSinkDistance = v)
				.build());

		// ------------------------------------------------------------------
		// Volumetrics
		// ------------------------------------------------------------------
		ConfigCategory vol = builder.getOrCreateCategory(Text.literal("Volumetrics"));
		vol.addEntry(eb.startBooleanToggle(Text.literal("2D volumetric sun beams"), cfg.sunBeams)
				.setTooltip(Text.literal("Beam planes at shadow edges; 16-sample sun opacity averaged over time via SSBO."))
				.setDefaultValue(true)
				.setSaveConsumer(v -> cfg.sunBeams = v)
				.build());
		vol.addEntry(eb.startFloatField(Text.literal("Sun beam intensity"), cfg.sunBeamIntensity)
				.setTooltip(Text.literal("0.0 - 1.0 opacity multiplier."))
				.setDefaultValue(0.45f)
				.setSaveConsumer(v -> cfg.sunBeamIntensity = v)
				.build());

		// ------------------------------------------------------------------
		// Debug logging
		// ------------------------------------------------------------------
		ConfigCategory debug = builder.getOrCreateCategory(Text.literal("Debug Logging"));
		debug.addEntry(eb.startBooleanToggle(Text.literal("Renderer batch stats"), cfg.logRenderer)
				.setDefaultValue(false).setSaveConsumer(v -> cfg.logRenderer = v).build());
		debug.addEntry(eb.startBooleanToggle(Text.literal("GPU buffer allocations"), cfg.logBufferAllocations)
				.setDefaultValue(false).setSaveConsumer(v -> cfg.logBufferAllocations = v).build());
		debug.addEntry(eb.startBooleanToggle(Text.literal("Fence wait times"), cfg.logFenceWaits)
				.setDefaultValue(false).setSaveConsumer(v -> cfg.logFenceWaits = v).build());
		debug.addEntry(eb.startBooleanToggle(Text.literal("Shader compile steps"), cfg.logShaderCompiles)
				.setDefaultValue(false).setSaveConsumer(v -> cfg.logShaderCompiles = v).build());
		debug.addEntry(eb.startBooleanToggle(Text.literal("LOD rebuilds"), cfg.logLod)
				.setDefaultValue(false).setSaveConsumer(v -> cfg.logLod = v).build());
		debug.addEntry(eb.startBooleanToggle(Text.literal("Volumetric samples"), cfg.logVolumetrics)
				.setDefaultValue(false).setSaveConsumer(v -> cfg.logVolumetrics = v).build());

		return builder.build();
	}
}
