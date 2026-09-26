package com.snok.client.render;

import com.snok.client.VerdiumuimClient;
import com.snok.config.VerdConfig;
import com.snok.log.PerfLog;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.render.state.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Frame hooks: drain meshed sections into the store, reload GPU data when it
 * changed, issue the single MDI draw after entities, and render sun beams.
 * All hooks no-op when the pipeline is disabled or GL-unsupported.
 */
public final class PipelineHooks {
	private PipelineHooks() {
	}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(PipelineHooks::onAfterEntities);
	}

	private static void onAfterEntities(WorldRenderContext ctx) {
		ChunkBatchRenderer renderer = VerdiumuimClient.batchRenderer();
		SectionStore store = VerdiumuimClient.sectionStore();
		if (renderer == null || store == null || !renderer.isEnabled()) return;

		// 1. Fold worker results into the visible store.
		store.drainUpdates();

		// 2. Reload GPU mirror when new meshes committed.
		if (store.hasNewCommits()) {
			renderer.reloadFromStore(store);
			store.clearNewCommitFlag();
		}

		// 3. Camera + matrices. The optimized batch draws slightly above the
		//    vanilla terrain (tiny Y offset) so it is visible while vanilla
		//    suppression is not yet enabled.
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

		// 4. The one draw call.
		float maxDist = VerdConfig.get().lodSinkDistance * 2.0f;
		renderer.renderBatch(view, proj, camX, camY, camZ, maxDist * maxDist);

		// 5. Sun beams.
		SunBeamRenderer beams = VerdiumuimClient.sunBeams();
		if (beams != null && VerdConfig.get().sunBeams) {
			beams.render(ctx, proj);
		}
	}

	private static Vector3f forward(org.joml.Quaternionf orientation) {
		Vector3f f = new Vector3f(0, 0, -1);
		if (orientation != null) f.rotate(orientation);
		return f;
	}
}
