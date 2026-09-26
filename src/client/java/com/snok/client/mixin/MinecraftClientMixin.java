package com.snok.client.mixin;

import com.snok.client.VerdiumuimClient;
import com.snok.client.gl.AsyncMeshUploader;
import com.snok.config.VerdConfig;
import com.snok.log.PerfLog;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Per-frame hook on the render loop: drains finished staging slots (zero-wait
 * fence checks) and emits batch stats when debug logging is on.
 */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void verdiumuim$frameStart(CallbackInfo ci) {
		AsyncMeshUploader uploader = VerdiumuimClient.meshUploader();
		if (uploader != null && VerdConfig.get().enabled && !VerdConfig.get().glFallback) {
			uploader.drainCompleted();
		}
	}
}
