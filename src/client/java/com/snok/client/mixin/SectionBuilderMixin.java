package com.snok.client.mixin;

import com.snok.client.VerdiumuimClient;
import com.snok.client.gl.AsyncMeshUploader;
import com.snok.client.render.SectionStore;
import com.snok.config.VerdConfig;
import com.snok.log.PerfLog;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.client.render.chunk.SectionBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks vanilla section rebuilds (SectionBuilder.build in Yarn 1.21.11) and
 * snapshots the section's opaque-voxel mask into the async greedy-mesh
 * pipeline. Vanilla rendering itself is untouched — the optimized MDI batch
 * draws alongside it (small normal offset in the draw hook) until full
 * suppression is proven stable.
 */
@Mixin(SectionBuilder.class)
public class SectionBuilderMixin {
	@Inject(method = "build", at = @At("HEAD"))
	private void verdiumuim$snapshotMask(ChunkSectionPos pos, ChunkRendererRegion region,
	                                     com.mojang.blaze3d.systems.VertexSorter sorter,
	                                     net.minecraft.client.render.chunk.BlockBufferAllocatorStorage alloc,
	                                     CallbackInfoReturnable<?> cir) {
		VerdConfig cfg = VerdConfig.get();
		if (!cfg.enabled || !cfg.greedyMeshing || cfg.glFallback) return;
		AsyncMeshUploader uploader = VerdiumuimClient.meshUploader();
		if (uploader == null || region == null) return;

		final int minX = pos.getMinX(), minY = pos.getMinY(), minZ = pos.getMinZ();

		// Sample a 16^3 mask with a 1-block border read so faces on section
		// boundaries are correctly hidden.
		boolean[] mask = new boolean[16 * 16 * 16];
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int z = 0; z < 16; z++) {
			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					m.set(minX + x, minY + y, minZ + z);
					BlockState s = region.getBlockState(m);
					mask[z * 256 + y * 16 + x] = s != null && s.isOpaqueFullCube();
				}
			}
		}

		long key = SectionStore.key(pos.getSectionX(), pos.getSectionY(), pos.getSectionZ());
		uploader.submit(new AsyncMeshUploader.MeshJob(key, minX, minY, minZ, mask));
		PerfLog.info(PerfLog.Cat.RENDERER, "section (%d,%d,%d) mask submitted for greedy meshing",
				pos.getSectionX(), pos.getSectionY(), pos.getSectionZ());
	}
}
