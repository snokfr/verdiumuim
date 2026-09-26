package com.snok.client.render;

import com.snok.config.VerdConfig;
import com.snok.log.PerfLog;
import com.snok.mesh.GreedyMesher;
import com.snok.mesh.PackedQuad;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

/**
 * Dynamic LOD: distant sections render as 2x-scaled greedy meshes (one packed
 * quad covers a 2x2 face span). The "sink" trick hides the seam: a LOD section
 * near the HQ boundary is pushed underground by ramping its sink offset from 0
 * (start distance) to full depth (sink distance), so LOD terrain slides below
 * the high-quality shell with no transparency or popping.
 */
public final class LodManager {
	/** How deep a fully-sunk LOD section is pushed (below min world Y of the section). */
	private static final float FULL_SINK_BLOCKS = 20.0f;

	/**
	 * Sink offset for a section at the given horizontal distance to the camera.
	 * 0 before lodStartDistance, ramps linearly to FULL_SINK at lodSinkDistance.
	 */
	public static float sinkOffset(float distance) {
		VerdConfig cfg = VerdConfig.get();
		float start = cfg.lodStartDistance;
		float end = Math.max(cfg.lodSinkDistance, start + 1.0f);
		float t = MathHelper.clamp((distance - start) / (end - start), 0.0f, 1.0f);
		return t * t * FULL_SINK_BLOCKS; // ease-in: gentle at the seam, firm far away
	}

	public static boolean isLod(float distance) {
		VerdConfig cfg = VerdConfig.get();
		return cfg.lodEnabled && distance > cfg.lodStartDistance;
	}

	/**
	 * Mesh a LOD section: downsample the opaque mask 2x (majority vote) and
	 * greedy-merge it; quads are emitted at 2x scale by packing doubled spans.
	 */
	public static int[] meshLod(boolean[] opaque) {
		boolean[] down = downsample2x(opaque);
		return GreedyMesher.mesh(down, VerdConfig.get().greedyMeshing);
	}

	/** 2x majority-vote downsample of the 16^3 opaque mask to 8^3. */
	private static boolean[] downsample2x(boolean[] opaque) {
		int s = GreedyMesher.SECTION_SIZE;
		int h = s / 2;
		boolean[] out = new boolean[h * h * h];
		for (int z = 0; z < h; z++) {
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < h; x++) {
					int count = 0;
					for (int dz = 0; dz < 2; dz++)
						for (int dy = 0; dy < 2; dy++)
							for (int dx = 0; dx < 2; dx++)
								if (opaque[(z * 2 + dz) * 256 + (y * 2 + dy) * 16 + (x * 2 + dx)]) count++;
					out[z * h * h + y * h + x] = count >= 2; // majority
				}
			}
		}
		return out;
	}

	public static void logRebuild(BlockPos pos, int quads, float sink) {
		PerfLog.info(PerfLog.Cat.LOD, "lod rebuild @ (%d, %d, %d): %d quads, sink=%.1f",
				pos.getX(), pos.getY(), pos.getZ(), quads, sink);
	}
}
