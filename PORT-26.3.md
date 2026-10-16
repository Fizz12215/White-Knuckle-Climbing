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
| Build | `build.gradle`, wrapper | non-remapping Loom, `implementation`, `jar` (not `remapJar`), Java 25 | adapt | tested |
| Ids | `ResourceLocation` (22 uses) | `Identifier` (same factory methods) | rename | tested |
| Networking | `PayloadTypeRegistry.playC2S/playS2C` | `serverboundPlay` / `clientboundPlay` | rename | tested (dedicated-server check) |
| Shaders | `core/hand.fsh`, `core/blood.fsh`, `post/damage.fsh`, `include/wk_damage.glsl` | ShaderC: `#extension GL_ARB_separate_shader_objects`, `layout(location)`, `#include`, guards | adapt | tested, OpenGL and Vulkan |
| Render pipelines | `RenderPipeline.builder(...).withUniform("Globals", ...)` | package `com.mojang.renderpearl.api.pipeline`; `GUI_TEXTURED_SNIPPET` already binds Globals | adapt | tested, OpenGL and Vulkan |
| Damage post effect | `GameRenderer.setPostEffect/currentPostEffect/clearPostEffect`, PostPass uniform buffer made writable by `PostPassMixin` | our id added to `requestedPostEffects` at `update` TAIL (`GameRendererMixin`); `GpuDevice`/`GpuBuffer` moved to renderpearl | adapt | tested (model error 0.24-0.26/255, as on 1.21.9), OpenGL and Vulkan |
| GUI / HUD | `GuiGraphics`, `HudElementRegistry` | `GuiGraphicsExtractor`; HUD elements are `extractRenderState(g, delta)` | adapt | tested |
| Setup screen | `MultiLineTextWidget.setColor`, `mc.screen`/`setScreen`, `Util.getPlatform().openPath`, `confirmLinkNow(String)` | colour inside the text; `mc.gui.screen()/setScreen()`; `Blaze3D.openPath`; `confirmLinkNow(URI)` | adapt | tested (screenshots of every page in the build test) |
| Input | GLFW key and mouse codes, `InputConstants.Type.KEYSYM` | SDL3 scancodes (`SDLScancode`), mouse buttons (`SDLMouse`), `InputConstants.Type.KEYBOARD` | adapt | tested (gametest presses keys and buttons) |
| Window | `Window.isFullscreen/toggleFullScreen` | the `fullscreen` option (as F11 does) | adapt | compiles; see deviations |
| Folder picker (Windows) | LWJGL tinyfd | **tinyfd no longer ships**; SDL3 `SDL_ShowOpenFolderDialog`, opened on the main thread | replace | compiles; needs the Windows test |
| Folder picker (Linux) | own D-Bus portal client | unchanged (pure Java) | keep | compiles; manual check |
| Camera | `Camera.getYRot/getXRot`, `getMainCamera`, `GameRenderer.getFov` | `yRot()/xRot()`, `mainCamera()`, `Camera.calculateFov` | adapt | tested |
| Misc client API | `Util`, `KeyBindingHelper`, toasts, `getOverlay`, `displayClientMessage`, `registerReloader` | `net.minecraft.util.Util`, `KeyMappingHelper.registerKeyMapping`, `mc.gui.toastManager()`, `mc.gui.overlay()`, `sendOverlayMessage`/`sendSystemMessage`, `registerReloadListener` | adapt | tested |
| Mixins (21) | camera, eye height, travel, input, hand hiding, hurt shake, sounds, step height, health, food, sneak edge, bars, post pass | all re-checked with `javap`; 3 changed (below) | adapt | tested |
| Asset extractor | `PACK_FORMAT = 69` | resource pack format 97 (game: 97.1); pack content version 1 -> 2 | adapt | tested (pack built and loaded) |
| Gametests | GLFW input codes, client gametest API | SDL3 codes; no `waitForChunksRender`; dedicated connection not `AutoCloseable` | adapt | tested |

## Log
1. **Build and first renames:** wrapper 9.7.1; the Loom/Java/dependency switch; `Identifier`;
   `PayloadTypeRegistry`. The shared (`main`) source set compiles. The client had 83 errors left.
2. **Shaders:** the four files brought to the ShaderC rules, comparing against vanilla 26.3's
   `position_tex_color`, `post/blit`, `post/box_blur` and `globals.glsl`. `SamplerInfo`, `Globals` and
   `DynamicTransforms` are unchanged for our use. The post-effect JSON format is unchanged.
3. **Client compiles** (commit `1f15944`): render API, damage post effect, GUI, input, folder picker,
   small APIs.
4. **Gametests compile** (`8728961`); pack format 97 and content version 2 (`b7cf27e`), so a 1.21.9
   pack gets the rebuild offer.
5. **First launch** crashed on one hook (`getFov` is gone). All 21 hooks then checked against 26.3:
   - the FOV hook moved to `Camera.calculateFov` (`CameraFovMixin`), and the hands recompute it
     through `CameraInvoker`, as they did with `GameRenderer.getFov`;
   - `bobHurt`/`bobView` take `(CameraRenderState, PoseStack)`;
   - the hand-hiding hook no longer names the parameters it never used.
6. **Gametest results** (all on this Linux PC, NVIDIA):
   - OpenGL, no pack: all checks passed.
   - OpenGL, with the pack: all checks passed; damage look within 0.24-0.26/255 of the model.
   - Vulkan, with the pack: all checks passed; same numbers.
   - Vulkan, `WKCLIMB_TEST_BUILD=1` (real private Python folder): the Build button installed the
     libraries, built and switched on the pack, the out-of-date offer, Stop and the two-step rebuild
     all passed; then the climbing checks without the pack.
   - OpenGL, `WKCLIMB_TEST_BUILD=1`: all passed (build + switch on only: the libraries were current).
7. **The setup screen showed no text** (found in the Vulkan build run's screenshots; every state
   check had passed):
   - 26.3's `ScrollableLayout.arrangeElements` keeps the box's current height (clamped), and
     `setMaxHeight` sizes it from the content's height at that moment. We set the maximum before
     the text was laid out, so the box stayed 0 tall and clipped everything. Now `repositionElements`
     calls `scroll.arrangeElements()` first, as vanilla's `RestrictionsScreen` does.
   - The test helper `showPageForTest` rebuilt "if `minecraft != null`", but 26.3 screens get
     `minecraft` in the constructor. It built a zero-width set of widgets before the screen opened,
     and opening added a second set (the title showed twice). It now checks `layout != null`.
   - Re-checked in the OpenGL build run's screenshots: every page has its text and colours.
8. **Folder picker start folder** (owner's request, after the port): a Flatpak now suggests Steam's
   usual `steamapps/common` unchecked (the portal backend opens it; the sandbox can't see it), and
   SDL3 gets the path with a trailing separator (without one it opens the parent folder with
   "common" typed in, on Windows). Not a 1.21.9 behaviour change on Windows: tinyfd got the separator.
   **Folder picker robustness:** the SDL callback and the dialog call always answer, so an exception
   can't leave the picker waiting forever.
9. **`installMod` guard:** an early port build had replaced 0.4.0-beta in the owner's 1.21.9 Prism
   instance (restored from the release zip). `installMod` now reads the instance's `mmc-pack.json`
   and skips another Minecraft version with a warning (checked: it skips the 1.21.9 instance).
   - `WKCLIMB_TEST_GRAPHICS=opengl|vulkan` added to `build.gradle`: the test framework rewrites
     `options.txt`, so the engine goes in as the `--graphicsBackend` launch argument.
10. **Shader double check** (owner's request):
    - Against 0.4.0: only syntax lines changed (the extension, `#include`, `layout(location)`, an
      include guard); no maths.
    - Against vanilla 26.3: hand/blood replace `core/position_tex_color.fsh` (the `GUI_TEXTURED_SNIPPET`
      pair) with the same locations (0 `texCoord0`, 1 `vertexColor`, out 0) and blocks; `damage.fsh`
      takes `screenquad`'s location 0 `texCoord`, and `SamplerInfo` as `post/box_blur`; `post/blit`
      still has `BlitConfig { vec4 ColorModulate }`; `Globals.ScreenSize` and
      `DynamicTransforms.ColorModulator` exist. `DamageConfig` = three std140 floats, as written.
    - `gl_FragCoord` runs the other way on Vulkan, but every use (the vignette) is symmetric about the
      screen centre.
    - OpenGL vs Vulkan, same test with the pack, 50 screenshot pairs: the post effect is identical
      (max 1/255 on at most 0.009% of pixels); the hand areas use exactly the same colours (117 of 117
      in the stamina frames, 1834 of 1834 under the near-death look), with only sprite-edge pixels
      placed differently (scaled-sprite rounding). Blood and sprint frames differ as expected (random
      splats, motion).
    - Not done: compiler warnings (no `glslc` here; the game compiled both engines without a message)
      and Vulkan validation layers (not installed).

## 26.3 API map (checked with javap)
- **Render API:** `com.mojang.blaze3d.{pipeline.RenderPipeline, shaders.UniformType, buffers.GpuBuffer,
  systems.GpuDevice}` moved to `com.mojang.renderpearl.api.{pipeline.RenderPipeline,
  pipeline.UniformType, buffers.GpuBuffer, device.GpuDevice}`; `CommandEncoder` is in
  `renderpearl.api.commands`. `Std140Builder` and `RenderSystem.getDevice()` didn't move.
- **Pipelines:** there's no `withUniform`/`withSampler`. Uniforms come from bind group layouts
  (`BindGroupLayouts.GLOBALS/PROJECTION/DYNAMIC_TRANSFORMS/SAMPLER0`).
  `RenderPipelines.GUI_TEXTURED_SNIPPET` already includes `GLOBALS_SNIPPET`, so our hand and blood
  pipelines only drop the `withUniform` line.
- **Post effects:** `currentPostEffect/setPostEffect/clearPostEffect` are gone.
  - `GameRenderer.update()` clears `requestedPostEffects`, then adds `END_OF_FRAME_POST_EFFECT`, the
    player's `getActivePostEffects()` and the spectated effect.
  - `extract()` copies the list to `GameRenderState`, and `render()` prepares and applies it.
  - **So we add our id at `update` TAIL.** Several effects now run together.
  - `ShaderManager.getPostChain(Identifier, Set<Identifier>)`, `LevelTargetBundle.MAIN_TARGETS`,
    `PostChain.passes` and `PostPass.customUniforms` are unchanged.
  - `PostPass.<init>` creates uniform buffers with
    `renderpearl/api/device/GpuDevice.createBuffer(Supplier;I;ByteBuffer)`; `GpuBuffer.USAGE_COPY_DST`
    exists.
- **Camera and FOV:**
  - `GameRenderer.getFov(Camera,float,boolean)` is gone. `Camera.update(DeltaTracker)` (called from
    `GameRenderer.update`, before `renderLevel`) stores `calculateFov(partial)` in `fov` and
    `calculateHudFov(partial)` in `hudFov`. `calculateFov` = old `getFov(..., true)`, including
    panorama 90 and the death/fluid changes; `calculateHudFov` = old `getFov(..., false)`.
  - `Camera.yRot()/xRot()`, `GameRenderer.mainCamera()`.
  - `GameRenderer.bobHurt/bobView(CameraRenderState, PoseStack)`, called from `renderLevel` and
    `renderItemInHand(CameraRenderState, PlayerRenderState, GpuTextureView)`, as before.
- **GUI:**
  - `GuiGraphicsExtractor` has `text`/`centeredText` (not `drawString`), `blit(RenderPipeline,
    Identifier, ...)` with the same argument lists, and `fill`. `pose()` returns a `Matrix3x2fStack`.
  - Fabric `HudElement.extractRenderState(GuiGraphicsExtractor, DeltaTracker)`.
  - Screens: `mc.gui.screen()`, `mc.gui.setScreen()`, `mc.gui.overlay()`, `mc.gui.toastManager()`,
    `mc.gui.hud.isHidden()` (was `options.hideGui`).
  - `MultiLineTextWidget` has no `setColor`: the colour goes in the `Component`.
  - `ConfirmLinkScreen.confirmLinkNow(Screen, URI)`; `com.mojang.blaze3d.Blaze3D.openPath(Path)` opens
    a folder (`Util.OS` can't any more).
- **Input:** `KeyMapping(String, InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_*, Category)`;
  mouse buttons `SDLMouse.SDL_BUTTON_LEFT/RIGHT`. Fabric: `KeyMappingHelper.registerKeyMapping`.
  The gametest `TestInput.holdKey/pressKey/holdMouse(int)` take the same SDL codes.
- **Window:** no `isFullscreen/toggleFullScreen`. F11 (`Minecraft.toggleFullscreen`, from the
  `keyFullscreen` binding) flips `options.fullscreen()`, whose callback calls
  `Window.setFullscreen(boolean)`, then saves. `Minecraft.fullscreenStateChanged` copies every real
  window change into the option and saves `options.txt`.
- **Messages:** `Player.sendOverlayMessage(Component)` (was `displayClientMessage(c, true)`),
  `sendSystemMessage(Component)` (was `displayClientMessage(c, false)`).
- **Resources:** Fabric `ResourceLoader.registerReloadListener(Identifier, PreparableReloadListener)`.
  Resource pack format 97.1 (`version.json` `pack_version.resource_major/minor`).
- **Graphics engine:** option `preferredGraphicsBackend` (`default`/`opengl`/`vulkan`); launch
  argument `--graphicsBackend <same>` wins over it.
- **Networking:** `PayloadTypeRegistry.serverboundPlay()/clientboundPlay()`.
- **LWJGL 3.4.3 modules:** no GLFW and no tinyfd; SDL3 (`lwjgl-sdl`), shaderc, spvc, vulkan, vma.
  `SDLDialog.SDL_ShowOpenFolderDialog(callback, userdata, window, defaultLocation, allowMany)`;
  the callback gets a NULL list on error, an empty list on cancel.
- **Gametest API:** no `waitForChunksRender` (we wait 60 ticks); `TestDedicatedServerContext.connect()`
  returns a `TestDedicatedServerConnection` that has `close()` but isn't `AutoCloseable`.

## Forced deviations
- **The damage look now stacks with vanilla post effects** (creeper/spider vision while spectating,
  `/posteffect`). Before, it gave way to them, because 1.21.9 had a single post-effect slot.
- **Leaving fullscreen for the folder picker is written to `options.txt`** (and written back when the
  picker closes): 26.3 saves every real window change. Only a quit while the picker is open would
  leave the game windowed next time. 1.21.9's `toggleFullScreen` didn't touch the option.

## Seen in the logs, not ours
- `Requested post effect does not exist: minecraft:end_of_frame`: 26.3 always requests its
  `end_of_frame` slot, which only a resource pack fills.
- `Illegal option value 0 for Anisotropic Filtering`: the test run folder's `options.txt`.

## Unverified
- The Windows folder picker on SDL3, and the whole mod on Windows (needs the Windows test).
- The Linux portal picker and the fullscreen handling in a real (non-test) client.
- A manual play in a 26.3 Prism instance.

## Next task
- The owner made the Prism instance `whiteKnuckle` (26.3, Fabric Loader 0.19.5, Fabric API
  0.163.0+26.3); `installMod` copies the jar there. The 1.21.9 instance is now `WhiteKnucklelegacy`.
  Next: a manual play there (the mod offers to rebuild the pack at the title screen).
- **Windows testing waits** (owner, 2026-10-16) until new features are added on 26.3; that round
  covers the port too, above all SDL3's folder dialog.
- Knowledge base: the 26.3 lessons are in the Fabric 26.x technique note (*Porting a 1.21.x mod to
  26.3*, Gotchas 8-12) and the White Knuckle note, committed locally on the PR #318 branch.
- Later: fold this file into DEVELOPING/MODLOG, merge to main and release 0.5.0-beta (each push only
  with the owner's OK).
