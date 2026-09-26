package com.snok.client.mixin;

import com.snok.client.VerdiumuimClient;
import com.snok.client.gl.AsyncMeshUploader;
import com.snok.config.VerdConfig;
import net.minecraft.client.MinecraftClient;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Per-frame hook on the render loop: captures the projection matrix for the
 * MDI batch draw and drains finished staging slots (zero-wait fence checks).
 */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void verdiumuim$frameStart(CallbackInfo ci) {
		VerdConfig cfg = VerdConfig.get();
		if (!cfg.enabled || cfg.glFallback) return;

		AsyncMeshUploader uploader = VerdiumuimClient.meshUploader();
		if (uploader != null) uploader.drainCompleted();

		MinecraftClient mc = (MinecraftClient) (Object) this;
		if (mc.world != null) {
			Matrix4f proj = mc.gameRenderer.getBasicProjectionMatrix(1.0f);
			if (proj != null) VerdiumuimClient.setLastProjection(new Matrix4f(proj));
		}
	}
}
