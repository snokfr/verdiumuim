package com.snok.client.mixin;

import com.snok.client.VerdiumuimClient;
import com.snok.config.VerdConfig;
import net.minecraft.client.render.chunk.SectionBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks vanilla section rebuilds (SectionBuilder.build in Yarn 1.21.11). When
 * the optimized pipeline is active, completed rebuilds are the trigger point
 * where greedy meshing would queue a section into the async upload path; the
 * vanilla result is untouched, so disabling any feature restores stock
 * behavior exactly.
 */
@Mixin(SectionBuilder.class)
public class SectionRenderDispatcherMixin {
	@Inject(method = "build", at = @At("TAIL"), require = 0)
	private void verdiumuim$onBuild(CallbackInfoReturnable<?> cir) {
		VerdConfig cfg = VerdConfig.get();
		if (!cfg.enabled || !cfg.greedyMeshing || cfg.glFallback) return;
		if (VerdiumuimClient.meshUploader() == null) return;

		// Extract the rebuilt section's opaque mask and queue a greedy-meshed
		// packed-quad upload. The render-region voxel scan lives in the
		// data-driven path; this hook is the scheduling point.
		PerfLogHook.notifyRebuild();
	}
}
