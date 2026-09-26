package com.snok.client.render;

import com.snok.client.VerdiumuimClient;
import com.snok.client.gl.GlProbe;
import com.snok.config.VerdConfig;
import com.snok.log.PerfLog;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.render.state.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Frame hooks: drain meshed sections into the store, reload GPU data when it
 * changed, and issue the batch draw after entities. Dispatches to the MODERN
 * (MDI) renderer or the LEGACY (GL 3.3 instanced) renderer by GL tier.
 */
public final class PipelineHooks {
	private PipelineHooks() {
	}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(PipelineHooks::onAfterEntities);
	}

	private static void onAfterEntities(WorldRenderContext ctx) {
		SectionStore store = VerdiumuimClient.sectionStore();
		if (store == null) return;

		store.drainUpdates();

		CameraRenderState cam = ctx.worldState().cameraRenderState;
		if (cam == null || cam.pos == null) return;

		float camX = (float) cam.pos.x;
		float camY = (float) cam.pos.y + 0.02f;
		float camZ = (float) cam.pos.z;
		Vector3f fwd = forward(cam.orientation);

		Matrix4f view = new Matrix4f().setLookAt(camX, camY, camZ,
				camX + fwd.x, camY + fwd.y, camZ + fwd.z, 0, 1, 0);
		Matrix4f proj = VerdiumuimClient.lastProjection();
		if (proj == null) return;

		if (GlProbe.modernPipeline()) {
			ChunkBatchRenderer renderer = VerdiumuimClient.batchRenderer();
			if (renderer == null || !renderer.isEnabled()) return;
			if (store.hasNewCommits()) {
				renderer.reloadFromStore(store);
				store.clearNewCommitFlag();
			}
			renderer.renderBatch(view, proj, camX, camY, camZ,
					VerdConfig.get().lodSinkDistance * 2.0f);
		} else {
			LegacyBatchRenderer legacy = VerdiumuimClient.legacyRenderer();
			if (legacy == null || !legacy.isEnabled()) return;
			if (store.hasNewCommits()) {
				legacy.reloadFromStore(store);
				store.clearNewCommitFlag();
			}
			legacy.renderBatch(view, proj);
		}
	}

	private static Vector3f forward(org.joml.Quaternionf orientation) {
		Vector3f f = new Vector3f(0, 0, -1);
		if (orientation != null) f.rotate(orientation);
		return f;
	}
}
