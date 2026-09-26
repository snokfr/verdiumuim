# Yarn 1.21.11 Mapping Reference

Verified against the resolved mappings for this project:
`net.fabricmc.yarn.1_21_11` / `1.21.11+build.6` (see `gradle.properties`).

Everything that compiles is compiler-verified; this document exists for
**mixin targets and runtime members**, which the compiler cannot check.

Last verified: 2026-09-26 (build green at commit `b380efc`).

## Client classes (verified via javap on the loom-cache JAR)

| Intent | Yarn 1.21.11 name | Notes |
|---|---|---|
| Section terrain meshing | `net.minecraft.client.render.chunk.SectionBuilder` | **Not** `SectionRenderDispatcher` (that name is gone in 1.21.11). Entry: `build(ChunkSectionPos, ChunkRendererRegion, VertexSorter, BlockBufferAllocatorStorage) -> RenderData` |
| Per-frame render hook | `net.minecraft.client.MinecraftClient#render(boolean)` | Private; mixin target `render` with `require = 0` |
| World renderer | `net.minecraft.client.render.WorldRenderer` | Frame-graph based now: `renderMain(FrameGraphBuilder, Frustum, Matrix4f, GpuBufferSlice, boolean, WorldRenderState, RenderTickCounter, Profiler)` |
| Completed section count | `WorldRenderer#getCompletedChunkCount()` | Useful for batch stats |

## Mixin targets actually used in this mod

| Mixin | Target | Signature |
|---|---|---|
| `SectionRenderDispatcherMixin` | `SectionBuilder#build` | `build(...)` → `CallbackInfoReturnable<RenderData>`, `require = 0` |
| `MinecraftClientMixin` | `MinecraftClient#render` | `render(Z)V` HEAD, `require = 0` |

All injections use `require = 0` so a future mapping drift degrades to the
vanilla path instead of crashing the game. The debug categories in the config
GUI make it visible: if `Renderer batch stats` logs nothing during play, the
hooks are not firing.

## Removed / renamed classes to watch for (1.21.4 → 1.21.11 era)

| Old (≤1.21.4) | Current (1.21.11) |
|---|---|
| `SectionRenderDispatcher` | split into `SectionBuilder` + `ChunkBuilder` |
| `SectionRenderDispatcher$RenderRegion` | `ChunkRendererRegion` |
| `BuiltChunk` / `ChunkBuilder$BuiltChunk` | `RenderedChunk`, `Buffers` |
| `WorldRenderer#getCompletedChunkCount` unchanged | — |

## Dependency coordinates (verified resolvable)

| Dependency | Version | Maven host |
|---|---|---|
| Cloth Config (fabric) | `21.11.153` | `https://maven.shedaniel.me/` — **no `+fabric` suffix on the maven version** (the Modrinth version string `21.11.153+fabric` is not on the maven) |
| Mod Menu | `17.0.1` | `https://maven.terraformersmc.com/releases/` |
| Fabric API | `0.141.6+1.21.11` | Fabric maven |

## Build environment notes

- `fabric-loom 1.18.x` (the `fabric-loom-remap` plugin) requires **JVM 25+ to run Gradle**, even though the mod itself compiles with `options.release = 21`. Run `JAVA_HOME=<JDK 25+> ./gradlew build`.
- The machine-local valid JDKs: `C:/Program Files/Java/jdk-26.0.2.1` (Gradle runtime), `C:/Program Files/Eclipse Adoptium/jdk-21.0.8.9-hotspot` (javap reference).
