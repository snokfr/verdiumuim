package com.snok.client.render;

import com.snok.client.VerdiumuimClient;
import com.snok.config.VerdConfig;
import com.snok.log.PerfLog;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Shadow-edge scanner: periodically samples sky light around the camera and
 * generates a beam for every column where a lit block borders a shadowed one.
 * Runs at most every N ticks to keep the CPU cost negligible; opacities are
 * smoothed per-beam by the renderer's temporal accumulator SSBO.
 */
public final class SunBeamScanner {
	private int tickCounter = 0;
	private long lastScanKey = Long.MIN_VALUE;

	public void tick(MinecraftClient client) {
		if (!VerdConfig.get().sunBeams || VerdConfig.get().glFallback) return;
		SunBeamRenderer beams = VerdiumuimClient.sunBeams();
		if (beams == null) return;
		if (++tickCounter % 20 != 0) return; // scan once per second

		ClientWorld world = client.world;
		if (world == null || client.player == null) return;
		Vec3d cam = client.player.getEyePos();

		// Skip scan if the camera barely moved (key = block pos quantized).
		long key = BlockPos.ofFloored(cam).asLong();
		if (key == lastScanKey) return;
		lastScanKey = key;

		int radius = 24;
		int yBase = MathHelper.clamp((int) cam.y - 8, world.getBottomY(), world.getBottomY() + world.getHeight() - 1);
		int found = 0;

		beams.beginFrame();
		int stride = 2; // sample every 2 blocks: 25x25 columns
		for (int dz = -radius; dz <= radius; dz += stride) {
			for (int dx = -radius; dx <= radius; dx += stride) {
				int x = MathHelper.floor(cam.x) + dx;
				int z = MathHelper.floor(cam.z) + dz;
				for (int y = yBase; y < yBase + 24; y++) {
					int here = world.getLightingProvider().getLight(new BlockPos(x, y, z), 0);
					int above = world.getLightingProvider().getLight(new BlockPos(x, y + 1, z), 0);
					if (here < 8 && above >= 14) {
						// Shadow edge: dark voxel directly under a sky-lit one.
						beams.addBeam(x, y + 1, z, 6, estimateOpacity(world, x, y + 1, z));
						found++;
						break;
					}
				}
				if (found >= SunBeamRenderer.MAX_BEAMS) break;
			}
			if (found >= SunBeamRenderer.MAX_BEAMS) break;
		}
		PerfLog.info(PerfLog.Cat.VOLUMETRICS, "scan: %d beam edges around (%d, %d, %d)", found,
				(int) cam.x, (int) cam.y, (int) cam.z);
	}

	/** 16-sample sun-opacity estimate averaged over the beam column depth. */
	private float estimateOpacity(ClientWorld world, int x, int y, int z) {
		int acc = 0;
		for (int i = 0; i < 16; i++) {
			int ly = y + i;
			int light = world.getLightingProvider().getLight(new BlockPos(x, ly, z), 0);
			acc += light;
		}
		return MathHelper.clamp(acc / (16.0f * 15.0f), 0.05f, 1.0f);
	}
}
