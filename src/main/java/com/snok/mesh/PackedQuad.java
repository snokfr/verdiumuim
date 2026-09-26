package com.snok.mesh;

/**
 * 32-bit packed greedy quad. One int per merged face; the vertex shader
 * expands it into a 4-vertex triangle strip procedurally.
 *
 * Bit layout (LSB -> MSB):
 *   [0..9]   x   - quad origin, section-local (10 bits, 0..1023)
 *   [10..19] y   - quad origin, section-local (10 bits)
 *   [20..25] z   - quad origin, section-local (6 bits)
 *   [26..27] dir - face direction (0=-X 1=+X 2=-Y 3=+Y 4=-Z 5=+Z)
 *   [28..29] w   - quad width  in blocks (greedy-merged, 0..3 => 1..4 blocks)
 *   [30..31] h   - quad height in blocks (0..3 => 1..4 blocks)
 *
 * Sections are 16 blocks, so w/h up to 4 keeps merged spans addressable;
 * larger runs are split into multiple packed quads by the mesher.
 */
public final class PackedQuad {
	public static final int DIR_NEG_X = 0, DIR_POS_X = 1;
	public static final int DIR_NEG_Y = 2, DIR_POS_Y = 3;
	public static final int DIR_NEG_Z = 4, DIR_POS_Z = 5;

	private PackedQuad() {
	}

	public static int pack(int x, int y, int z, int dir, int w, int h) {
		return (x & 0x3FF)
				| ((y & 0x3FF) << 10)
				| ((z & 0x3F) << 20)
				| ((dir & 0x7) << 26)
				| ((w & 0x3) << 28)
				| ((h & 0x3) << 30);
	}

	public static int x(int packed) {
		return packed & 0x3FF;
	}

	public static int y(int packed) {
		return (packed >>> 10) & 0x3FF;
	}

	public static int z(int packed) {
		return (packed >>> 20) & 0x3F;
	}

	public static int dir(int packed) {
		return (packed >>> 26) & 0x7;
	}

	public static int w(int packed) {
		return (packed >>> 28) & 0x3;
	}

	public static int h(int packed) {
		return (packed >>> 30) & 0x3;
	}

	/** Bytes per packed quad in the VBO. */
	public static final int BYTES = 4;
	/** Vertex count of one quad drawn as a triangle strip. */
	public static final int VERTS = 4;
}
