package com.snok.client.render;

import com.snok.log.PerfLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Registry of meshed sections, keyed by packed section coordinate. Worker
 * threads put finished meshes here; the render thread drains updates into
 * the batch renderer and queries visible sections each frame.
 */
public final class SectionStore {
	/** Packed section key: x/z in 22 bits (signed offset), y in 20 bits. */
	public static long key(int sectionX, int sectionY, int sectionZ) {
		return ((long) (sectionX & 0x3FFFFF) << 42)
				| ((long) (sectionZ & 0x3FFFFF) << 20)
				| (long) (sectionY & 0xFFFFF);
	}

	public static int sectionX(long key) {
		return (int) (key >> 42) << 42 >> 42; // sign-extend 22 bits
	}

	public static int sectionY(long key) {
		return (int) (key << 44) >> 44; // sign-extend 20 bits
	}

	public static int sectionZ(long key) {
		return (int) (key << 2) >> 22;
	}

	public record SectionMesh(int[] quads, int originX, int originY, int originZ,
	                          long version, float sinkOffset) {
	}

	private final Map<Long, SectionMesh> meshes = new ConcurrentHashMap<>();
	private final Map<Long, SectionMesh> pendingUpdates = new ConcurrentHashMap<>();
	private volatile boolean newCommits = false;

	/** Worker thread: publish a freshly meshed section. */
	public void put(long key, SectionMesh mesh) {
		pendingUpdates.put(key, mesh);
	}

	/** Render thread: fold pending worker updates into the visible map. */
	public void drainUpdates() {
		if (pendingUpdates.isEmpty()) return;
		for (Map.Entry<Long, SectionMesh> e : pendingUpdates.entrySet()) {
			meshes.put(e.getKey(), e.getValue());
		}
		pendingUpdates.clear();
		newCommits = true;
		PerfLog.info(PerfLog.Cat.RENDERER, "section store drain: %d total meshed sections", meshes.size());
	}

	public boolean hasNewCommits() {
		return newCommits;
	}

	public void clearNewCommitFlag() {
		newCommits = false;
	}

	public int approxQuadCount() {
		int total = 0;
		for (SectionMesh m : meshes.values()) total += m.quads().length;
		return total;
	}

	/** Render thread: deterministic-order iteration over all committed meshes. */
	public void forEachMesh(java.util.function.BiConsumer<Long, SectionMesh> consumer) {
		meshes.entrySet().stream()
				.sorted(Map.Entry.comparingByKey())
				.forEach(e -> consumer.accept(e.getKey(), e.getValue()));
	}

	/** Render thread: iterate sections in view distance with frustum-ish culling. */
	public void forEachVisible(float camX, float camY, float camZ, float maxDistSq,
	                           Consumer<SectionMesh> consumer) {
		for (SectionMesh mesh : meshes.values()) {
			float dx = mesh.originX() + 8 - camX;
			float dy = mesh.originY() + 8 - camY;
			float dz = mesh.originZ() + 8 - camZ;
			if (dx * dx + dy * dy + dz * dz <= maxDistSq) {
				consumer.accept(mesh);
			}
		}
	}

	public void remove(long key) {
		pendingUpdates.remove(key);
		meshes.remove(key);
	}

	public int size() {
		return meshes.size();
	}

	public void clear() {
		meshes.clear();
		pendingUpdates.clear();
	}
}
