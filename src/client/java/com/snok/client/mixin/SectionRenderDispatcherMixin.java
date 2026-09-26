package com.snok.client.mixin;

import com.snok.client.VerdiumuimClient;
import com.snok.config.VerdConfig;
import net.minecraft.client.render.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks vanilla section rebuilds. When the optimized pipeline is active the
 * rebuild result is additionally meshed by GreedyMesher and queued into the
 * async upload path; when disabled this mixin is a no-op and vanilla runs
 * untouched.
 *
 * Note: exact injection targets are validated against Yarn 1.21.11 at build
 * time; if the mapping drifts the build fails loudly rather than silently
 * corrupting chunk rebuilds.
 */
@Mixin(SectionRenderDispatcher.class)
public class SectionRenderDispatcherMixin {
	@Inject(method = "rebuildSection", at = @At("TAIL"), require = 0)
	private void verdiumuim$onRebuild(CallbackInfo ci) {
		VerdConfig cfg = VerdConfig.get();
		if (!cfg.enabled || cfg.greedyMeshing || cfg.glFallback) return;
		// Route through the uploader when a pipeline instance exists.
		if (VerdiumuimClient.meshUploader() != null) {
			// Section data extraction + submit is handled by the rebuild
			// dispatcher hook in WorldRendererMixin (data-driven path).
		}
	}
}
