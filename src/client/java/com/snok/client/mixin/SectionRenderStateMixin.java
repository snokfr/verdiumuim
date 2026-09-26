package com.snok.client.mixin;

import com.snok.client.VerdiumuimClient;
import net.minecraft.client.render.BlockRenderLayerGroup;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.render.SectionRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla terrain suppression. SectionRenderState.renderSection is the single
 * method that issues vanilla chunk draws for a block-render layer group;
 * cancelling it when the Verdiumuim pipeline is active (and holding meshes
 * for the visible world) completes the replacement: one batched instanced/MDI
 * draw instead of vanilla's per-section command stream.
 *
 * Guarded by three conditions in shouldSuppressVanillaTerrain(): config opt-in,
 * a healthy pipeline (probe tier + no Sodium), and a non-empty section store
 * so the screen never renders empty while meshes are still streaming in.
 */
@Mixin(SectionRenderState.class)
public class SectionRenderStateMixin {
	@Inject(method = "renderSection", at = @At("HEAD"), cancellable = true)
	private void verdiumuim$suppressVanillaTerrain(BlockRenderLayerGroup layerGroup, GpuSampler sampler,
	                                               CallbackInfo ci) {
		if (VerdiumuimClient.shouldSuppressVanillaTerrain()) {
			ci.cancel();
		}
	}
}
