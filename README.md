# Verdiumuim

A high-performance rendering optimization mod for **Minecraft 1.21.11 (Fabric)** targeting Intel integrated GPUs.

## Features

- **Greedy meshing** — coplanar voxel faces merged horizontally and vertically; each merged face packs into a single 32-bit integer (position + face direction + dimensions via bit masking).
- **Single-command batching** — all visible chunk sections render in one `glMultiDrawElementsIndirect` call. Section world positions and face directions are read by the shader from SSBOs indexed via `gl_DrawID`.
- **Triangle strips + procedural vertices** — 4-vertex strips with corner generation in the vertex shader instead of 6-vertex quads.
- **Async mesh transfers** — a persistent-mapped staging ring guarded by `glFenceSync` streams chunk mesh updates on a worker thread. The render thread only zero-wait-checks fences: no main-thread stalls.
- **Dynamic LOD terrain sinking** — distant terrain uses 2x-scaled meshes; near the high-quality boundary LOD sections sink underground with an eased offset, so transitions never show transparency or pop.
- **2D volumetric sun beams** — beam planes at shadow edges; opacity from 16 sunlight samples per beam, temporally averaged through an SSBO accumulator so foliage flicker doesn't flicker the beams.

## Requirements

| Dependency | Required? |
|---|---|
| Minecraft 1.21.11 + Fabric Loader ≥ 0.19.5 | yes |
| Fabric API | yes |
| Java 21 | yes |
| OpenGL 4.3+ | yes (otherwise the mod falls back to vanilla rendering) |
| Cloth Config | optional (config GUI) |
| Mod Menu | optional (config button in the Mods list) |

## Configuration

Open **Mods → Verdiumuim → Configure** (Mod Menu) for the tabbed config:

- **Terrain Pipeline** — master enable, greedy meshing, MDI batching, triangle strips
- **Buffers** — staging ring size, staging buffer size
- **Shaders** — GL_KHR_debug toggle
- **LOD** — enable, start distance, sink distance
- **Volumetrics** — sun beams enable, intensity
- **Debug Logging** — per-category toggles: renderer batch stats, GPU buffer allocations, fence wait times, shader compile steps, LOD rebuilds, volumetric samples

Config persists to `config/verdiumuim.json`.

## Fallback behavior

If the GPU/driver lacks OpenGL 4.3 (MDI + SSBO support) or pipeline initialization fails, Verdiumuim latches into a permanent vanilla-rendering fallback for the session and logs the reason. Every feature also has an individual toggle.

## Building

```bash
./gradlew build
```

JARs land in `build/libs/`.

## Releases

Pushing a semver tag publishes a GitHub Release with the built JARs automatically:

```bash
git tag v1.0.0
git push origin v1.0.0
```

The workflow at `.github/workflows/release.yml` builds on JDK 21 and attaches mod + sources JARs to the release.

## License

CC0 1.0 — see [LICENSE](LICENSE).
