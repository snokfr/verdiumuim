package com.snok.mesh;

import java.util.ArrayList;
import java.util.List;

/**
 * Greedy mesher: merges coplanar voxel faces along both sweep axes per face
 * direction, emitting the smallest set of maximal rectangles. Output is one
 * 32-bit packed quad per merged face (see {@link PackedQuad}).
 *
 * The section is a flat 16x16x16 opaque/transparent mask; the caller
 * (SectionBuildDispatcher mixin) fills it from the real block view.
 */
public final class GreedyMesher {
	public static final int SECTION_SIZE = 16;

	/**
	 * Merge faces. blockOpaque[z * 256 + y * 16 + x] == true means solid.
	 * Returns packed quads, section-local coordinates.
	 */
	public static int[] mesh(boolean[] opaque, boolean greedy) {
		List<Integer> out = new ArrayList<>(256);

		for (int dir = 0; dir < 6; dir++) {
			sweepDirection(opaque, greedy, dir, out);
		}

		int[] result = new int[out.size()];
		for (int i = 0; i < result.length; i++) result[i] = out.get(i);
		return result;
	}

	private static void sweepDirection(boolean[] opaque, boolean greedy, int dir, List<Integer> out) {
		// For each axis-aligned direction, walk slices along the face normal
		// and greedily merge the 2D face mask in slice space.
		for (int slice = 0; slice < SECTION_SIZE; slice++) {
			boolean[][] mask = sliceMask(opaque, dir, slice);
			greedyMerge(mask, greedy, dir, slice, out);
		}
	}

	/** Build the 2D visible-face mask for one slice of one direction. */
	private static boolean[][] sliceMask(boolean[] opaque, int dir, int slice) {
		boolean[][] mask = new boolean[SECTION_SIZE][SECTION_SIZE];
		for (int u = 0; u < SECTION_SIZE; u++) {
			for (int v = 0; v < SECTION_SIZE; v++) {
				int x, y, z, nx, ny, nz;
				switch (dir) {
					case PackedQuad.DIR_POS_Y -> {
						x = u; z = v; y = slice;
						nx = x; nz = z; ny = slice + 1;
					}
					case PackedQuad.DIR_NEG_Y -> {
						x = u; z = v; y = slice;
						nx = x; nz = z; ny = slice - 1;
					}
					case PackedQuad.DIR_POS_X -> {
						y = u; z = v; x = slice;
						ny = y; nz = z; nx = slice + 1;
					}
					case PackedQuad.DIR_NEG_X -> {
						y = u; z = v; x = slice;
						ny = y; nz = z; nx = slice - 1;
					}
					case PackedQuad.DIR_POS_Z -> {
						x = u; y = v; z = slice;
						nx = x; ny = y; nz = slice + 1;
					}
					default -> { // DIR_NEG_Z
						x = u; y = v; z = slice;
						nx = x; ny = y; nz = slice - 1;
					}
				}
				if (!inBounds(x, y, z)) continue;
				boolean faceVisible = opaque[idx(x, y, z)]
						&& (!inBounds(nx, ny, nz) || !opaque[idx(nx, ny, nz)]);
				mask[u][v] = faceVisible;
			}
		}
		return mask;
	}

	/** Classic 2D greedy rectangle merge over the slice mask. */
	private static void greedyMerge(boolean[][] mask, boolean greedy, int dir, int slice, List<Integer> out) {
		int maxSpan = greedy ? 4 : 1; // packed w/h fields hold 0..3 => spans 1..4

		for (int v = 0; v < SECTION_SIZE; v++) {
			for (int u = 0; u < SECTION_SIZE; u++) {
				if (!mask[u][v]) continue;

				// Grow width.
				int w = 1;
				while (greedy && u + w < SECTION_SIZE && w < maxSpan && mask[u + w][v]) w++;
				// Grow height over the full width strip.
				int h = 1;
				while (greedy && v + h < SECTION_SIZE && h < maxSpan && rowAllTrue(mask, u, v + h, w)) h++;

				// Map (u, v) slice-space back to section space per axis pair.
				int ox, oy, oz;
				switch (dir) {
					case PackedQuad.DIR_POS_X, PackedQuad.DIR_NEG_X -> { ox = slice; oy = u; oz = v; }
					case PackedQuad.DIR_POS_Y, PackedQuad.DIR_NEG_Y -> { ox = u; oy = slice; oz = v; }
					default -> { ox = u; oy = v; oz = slice; }
				}
				out.add(PackedQuad.pack(ox, oy, oz, dir, w - 1, h - 1));

				for (int dv = 0; dv < h; dv++)
					for (int du = 0; du < w; du++)
						mask[u + du][v + dv] = false;
			}
		}
	}

	private static boolean rowAllTrue(boolean[][] mask, int u, int v, int w) {
		for (int i = 0; i < w; i++) {
			if (!mask[u + i][v]) return false;
		}
		return true;
	}

	private static boolean inBounds(int x, int y, int z) {
		return x >= 0 && x < SECTION_SIZE && y >= 0 && y < SECTION_SIZE && z >= 0 && z < SECTION_SIZE;
	}

	private static int idx(int x, int y, int z) {
		return z * 256 + y * 16 + x;
	}
}
