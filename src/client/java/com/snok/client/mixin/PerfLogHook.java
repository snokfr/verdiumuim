package com.snok.client.mixin;

import com.snok.log.PerfLog;

/**
 * Tiny indirection so the section mixin logs through PerfLog without the
 * mixin class loading the render subsystems eagerly.
 */
final class PerfLogHook {
	private PerfLogHook() {
	}

	static void notifyRebuild() {
		PerfLog.info(PerfLog.Cat.RENDERER, "section rebuild observed; greedy mesh queue notified");
	}
}
