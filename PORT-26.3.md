# Port to Minecraft 26.3: status

Working document for the `port-26.3` branch, kept per the port-a-mod method. It's folded into
DEVELOPING.md and MODLOG.md when the port is done, then deleted.

## Rules
- **Source of truth:** White Knuckle Climbing **0.4.0-beta** (tag `v0.4.0-beta`, commit `db9e721`,
  Minecraft 1.21.9). The port must behave exactly like it. No gameplay changes or "improvements"
  during the port; those come after, in their own commits.
- **Reading the original:** `git show v0.4.0-beta:<path>`.
- **Reading 26.3:** `javap` against the real jars. Never guess a name. To get the classpath:
  `CPOUT=<file> ./gradlew -q -I <init script with a printClientCp task> printClientCp`; the jars are in
  `~/.gradle/caches/fabric-loom/26.3/`.
- **License:** our own code, MIT. White Knuckle's files never go in the repo (the pack folder is
  ignored).
- **Evidence:** never write "passes" for a run that didn't happen. Use the levels
  implemented / compiles / tested (unit or gametest) / seen in a real client / unverified.

## Versions
| | 1.21.9 (0.4.0) | 26.3 (0.5.0) |
|---|---|---|
| Minecraft | 1.21.9 | 26.3 (unobfuscated, Java 25, SDL3, OpenGL or Vulkan) |
| Loom | 1.12.7 (remapping, Mojang mappings) | 1.18-SNAPSHOT `net.fabricmc.fabric-loom` (no remap, no mappings) |
| Gradle | 9.1.0 | 9.7.1 |
| Fabric Loader / API | 0.19.5 / 0.134.1+1.21.9 | 0.19.5 / 0.162.0+26.3 |
| Java | 21 (release) | 25 |

## Adaptation matrix
| Area | 1.21.9 code | 26.3 change | Verdict | Status |
|---|---|---|---|---|
| Build | `build.gradle`, wrapper | non-remapping Loom, `implementation`, `jar` (not `remapJar`), Java 25 | adapt | compiles |
| Ids | `ResourceLocation` (22 uses) | `Identifier` (same factory methods) | rename | compiles |
| Networking | `PayloadTypeRegistry.playC2S/playS2C` | `serverboundPlay` / `clientboundPlay` | rename | compiles |
| Shaders | `core/hand.fsh`, `core/blood.fsh`, `post/damage.fsh`, `include/wk_damage.glsl` | ShaderC: `#extension GL_ARB_separate_shader_objects`, `layout(location)`, `#include`, guards | adapt | edited, not yet compiled by the game |
| Render pipelines | `RenderPipeline.builder(...).withUniform("Globals", ...)` | package `com.mojang.renderpearl.api.pipeline`; uniforms via bind group layouts (`GLOBALS_SNIPPET`) | adapt | todo |
| Damage post effect | `GameRenderer.setPostEffect/currentPostEffect/clearPostEffect`, PostPass uniform buffer made writable by `PostPassMixin` | requested-effects list (`getRequestedPostEffects()`), `END_OF_FRAME_POST_EFFECT`; `GpuDevice`/`GpuBuffer` moved to renderpearl | adapt | todo |
| GUI / HUD | `GuiGraphics`, `HudElementRegistry` | `GuiGraphicsExtractor` (`text`, `centeredText`, `pose()` is a `Matrix3x2fStack`) | adapt | todo |
| Setup screen | `Screen.render`, `MultiLineTextWidget.setColor`, `mc.screen`/`setScreen` | `extractRenderState`, screen access through `mc.gui` | adapt | todo |
| Input | GLFW key and mouse codes, `InputConstants.Type.KEYSYM` | SDL3 scancodes, `InputConstants.Type.KEYBOARD` | adapt | todo |
| Window | `Window.isFullscreen/toggleFullScreen` | to find (SDL window) | adapt | todo |
| Folder picker (Windows) | LWJGL tinyfd | **tinyfd no longer ships**; SDL3 `SDL_ShowOpenFolderDialog` | replace | todo |
| Folder picker (Linux) | own D-Bus portal client | unchanged (pure Java) | keep | compiles |
| Camera | `Camera.getYRot/getXRot`, `getMainCamera` | to find | adapt | todo |
| Misc client API | `Util`, `KeyBindingHelper`, toasts, `getOverlay`, `displayClientMessage`, `registerReloader` | to find | adapt | todo |
| Mixins (21) | camera, eye height, travel, input, hand hiding, hurt shake, sounds, step height, health, food, sneak edge, bars, post pass | each target re-checked against 26.3 with `javap` | adapt | todo |
| Asset extractor | `PACK_FORMAT = 69` | resource pack format 97.1 | adapt | todo |
| Gametests | GLFW input codes, client gametest API | SDL3 codes; API changes to find | adapt | todo |

## Log
1. **Build and first renames:** wrapper 9.7.1; the Loom/Java/dependency switch; `Identifier`;
   `PayloadTypeRegistry`. The shared (`main`) source set compiles. The client has 83 errors left
   (grouped above).
2. **Shaders:** the four files brought to the ShaderC rules, comparing against vanilla 26.3's
   `position_tex_color`, `post/blit`, `post/box_blur` and `globals.glsl`. `SamplerInfo`, `Globals` and
   `DynamicTransforms` are unchanged for our use. The post-effect JSON format is unchanged.

## Forced deviations
(none yet)

## Unverified
- Everything at runtime: nothing has been run on 26.3 yet.

## Next task
Fix the client compile errors area by area (render pipelines, then the damage post effect, the GUI/HUD,
input and window, then the small APIs), and look up every name with `javap`.
