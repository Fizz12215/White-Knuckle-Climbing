# Developing White Knuckle Climbing

Handoff notes for whoever picks this up. `README.md` is for players; `MODLOG.md` is the dated
history of every change with what was measured. This file is the map.

## What it is

A Fabric mod for **Minecraft 1.21.9** that plays like White Knuckle (Dark Machine, Unity 6 / Mono):
two-handed grip climbing on ledges and rods, White Knuckle's on-foot movement, camera, hands, HUD,
sounds and health rules. Gameplay is reimplemented from the game's decompiled code and serialized
data. **No game files ship with the mod.** Art and audio come from a resource pack each player
builds from their own install (`tools/extract_wk_assets.py`).

Status: 0.3.0-beta, the first public release (climbing only). See *Open issues* at the end.

## Setup

- JDK 21 or newer (the build compiles with `--release 21`; it has been built with JDK 25).
- The Gradle wrapper (Gradle 9.1), Fabric Loom 1.12.7 with **Mojang mappings**, Fabric Loader
  0.19.5 and Fabric API 0.134.1+1.21.9 (`gradle.properties`).
- Optional: `local.properties` with `modsDir=/path/to/instance/minecraft/mods`. If present, every
  `./gradlew build` copies the jar there and deletes older versions of it. The file isn't committed.
- To work on the assets or research: Python 3.10-3.14 with `pip install -r tools/requirements.txt`,
  and a White Knuckle install.

| Command | What it does |
|---|---|
| `./gradlew build` | compile, run unit tests, build `build/libs/*.jar` (and install it, see above) |
| `./gradlew test` | `ClimbSimTest`: headless checks of the ported physics against hand-derived numbers |
| `./gradlew runClient` | dev client |
| `./gradlew runClientGameTest` | real client + flat world; scripted checks of almost everything (~2.5 min). Set `WKCLIMB_TEST_PACK=<extracted pack folder>` to test with the real sprites and sounds. Screenshots: `build/run/clientGameTest/screenshots/` |
| `./gradlew betaZip` | release download: `build/beta/White-Knuckle-Climbing-<version>.zip` (jar, extractor, scripts, `beta/README.txt` = the player guide) |
| `python tools/extract_wk_assets.py --out <dir> [--zip]` | build the "White Knuckle Assets" resource pack from your install (`--instance <prism instance> --enable` installs it) |
| `python tools/dump_component.py <Class> [--file ...] [--keys ...]` | dump a game component's serialized values (for checking or porting numbers) |

## Layout

```
src/main/java/dev/fizz/wkclimb/          common (both sides)
  sim/ClimbSim.java       THE CORE: port of ENT_Player.Movement/Hang/Jump/HandStamina + CL_Handhold_Icy.
                          Pure Java, White Knuckle units, 50 Hz. No Minecraft imports (unit-testable).
  sim/WK.java             all ported constants, with where each came from
  sim/Handhold.java       handhold presets (default, slow, Bar, Chimney ice)
  sim/V3.java             tiny vector type mirroring Unity's Vector3 helpers
  WKClimb.java            mod init, payload handling, tags, isRod(), per-player server flags
  WKHealth.java           White Knuckle health on the server (regen, falls, breath), x4 scale
  net/                    ClimbStatePayload (C2S: hanging, throughBars, wkMode), FallGripPayload (S2C)
  mixin/                  rods without collision, no hunger/vanilla fall damage/peaceful regen,
                          heal-delay hook, anti-fly-kick accessor
src/client/java/dev/fizz/wkclimb/client/   client only
  ClimbController.java    the adapter: input, hand catching (sphere cast vs ledge edges / rod outlines),
                          runs ClimbSim 2-3 steps per tick through Body (Minecraft collision), sounds, sync
  ClimbHands.java         first-person hand sprites (camera-rig placement, ViewSway); drawn by
                          shaders/core/hand.fsh (the game's stamina red + the damage look)
  HandSway.java           port of ViewSway + its parameter sets
  ClimbHud.java           crosshair hand icons (and a drawn fallback without the pack)
  ClimbCrosshair.java     the game's crosshair and focus ring, sized like its UI canvas
  ClimbCamera.java        head-bob, sprint roll, FOV, camera shake (replaces vanilla bobbing)
  ClimbDamage.java        blood splats, hit-direction glow; damage grade + hurt vignette (post shader)
  ClimbSounds.java / ClimbFootsteps.java   game sound sets, floor materials
  ClimbKeybinds.java      applies the game's default keys once
  mixin/                  travel hook, input, camera, sounds, hide vanilla hand, post-pass access
src/client/resources/assets/wkclimb/    lang, the damage post effect + shaders (our own files;
                                        the maths is in shaders/include/wk_damage.glsl)
src/main/resources/data/wkclimb/tags/   block tags: handholds, grab_anywhere, bars, icy, slow, ...
src/test/  src/gametest/                unit tests / in-game tests
tools/                                  extractor, research dump tool, requirements
beta/                                   tester readme + one-click extractor scripts
```

### How a tick works (client)

`LivingEntityTravelMixin` -> `ClimbController.travel()`. The accumulator runs 2 or 3 `ClimbSim`
steps of 0.02 s per 0.05 s tick. Each step:
1. `handleHands` (catch / let go);
2. `sim.updateHands` (grip, cooldowns, ice);
3. `sim.movement` (physics), which calls `Body.move` -> `Entity.move` for collision and returns
   the hit normal.

While White Knuckle mode is on, the sim owns all on-foot movement; vanilla keeps swimming, flying,
elytra and riding. Velocity is converted at the hand-over points.

### Unit contract

- 1 White Knuckle unit = **0.8 blocks**: the game's capsule is 2.25 units tall, Steve is 1.8 blocks
  (`ClimbConfig.blocksPerUnit`).
- The game's `vel` isn't m/s. Displacement per step is `vel * 0.02 * 175`, so in blocks per tick:
  `vel * 175 * 0.05 * 0.8`.
- Health is x4: White Knuckle has 5, Minecraft 20.

### Client <-> server

The client sends `climb_state` on change and every 20 ticks while active, and again after
joining. The server uses it to:
- stop fall distance and the fly-kick while hanging;
- let a player through rods while swinging;
- run `WKHealth` for players in White Knuckle mode.

The server sends `fall_grip` when a fall ends by catching a hold (grip penalty + blood).
**The mod must be installed on servers.**

## Where the numbers come from

Everything ported is commented with its source class/field. Sources:
- **Code:** a decompile of `White Knuckle_Data/Managed/Assembly-CSharp.dll`. Main classes:
  - `ENT_Player`: Movement, Hang, Jump, HandStamina, InteractCheck, HandAnimation, MouseLook,
    FallingDamage, Damage, LandingAudio;
  - `CL_Handhold`, `CL_Handhold_Icy`, `ViewSway`, `CL_CameraControl`, `FXManager`,
    `UI_CrosshairController`, `CL_UIManager`, `UI_CanvasScaler`.

  ilspycmd **8.2.0.7535** on .NET 8 works (`DOTNET_ROLL_FORWARD=Major`); newer ilspycmd needs a
  newer runtime. Don't commit decompiled code.
- **Data:** prefab and scene values via UnityPy + TypeTreeGeneratorAPI (`tools/dump_component.py`).
  The player is `ENT_Player` in `level3`; handhold presets, icy handholds, sprites and audio are in
  `sharedassets1.assets`. Many prefab values differ from the C# field defaults (gravity -0.2 not
  -9.81, drag 8 not 0.1, interact distance 2.5, ...). Always check the data, not just the code.

## Gotchas (each cost real time)

- **Assets:**
  - UnityPy's FMOD fails in the main process (first on the FSB5 IMA ADPCM clips, then on the rest),
    but a fresh process decodes every clip. `ClipDecoder` sends all clips to one `--decode-clips`
    subprocess per game file (~13 s for the whole pack). The old one-subprocess-per-clip approach
    re-read the ~1 GB asset file each time and took ~285 s.
  - Fallback: `fsb5_ima_to_wav` decodes IMA ADPCM bit-identically to FMOD. Per 0x24-byte frame and
    channel: the header predictor is the first sample, then 63 samples from the first 63 of the 64
    nibbles (4-byte interleaved, low nibble first). `--fmod-only` / `--no-fmod` (hidden) exercise
    each path on its own for comparison.
  - Keep the UnityPy `Environment` alive while reading streamed data.
  - **TypeTreeGenerator** emits a stray `references` node before subclass fields for classes with
    `[SerializeReference]` lists; drop the first one or reads fail (`find_icy_handhold`,
    `dump_component.py`). UI types (`Image`) can't be generated, so parse them raw.
- **The game's damage look** (measured 2026-10-14, see MODLOG):
  - It is the "Hidden/Dark Machine/Post Process WK" shader, not URP volumes. Shaders are
    D3D11 bytecode in an LZ4 `compressedBlob`. Decompress per platform with UnityPy, cut out the
    `DXBC` containers, and disassemble them with `vkd3d-compiler.exe -x dxbc-tpf -b d3d-asm` under
    Wine (Fedora's `mingw64-vkd3d`). Uniform names are in each pass's `m_NameIndices`.
  - It multiplies by `0.25 + 0.75 * _GAMMA`, where `_GAMMA` is the player's brightness setting
    plus damageFX.gamma. Screenshots depend on that setting (`settings.json` in the Proton prefix
    under `AppData/LocalLow/Dark Machine/White Knuckle`). The mod assumes the default, 1.
  - UI vignettes use `Color.Lerp(clear, colour, amount)`, so colour and opacity both scale with
    the amount, and Unity blends them in linear light.
  - To capture references: the playground's green-screen rooms (pure white/green/black), the
    console (Shift+\`, `cheats`, `hurtplayer dontkill 1`), and screenshots ~2.5 s after each hit
    (healing starts at 5 s). The game test does the same with concrete walls and checks the
    shader against the model (`lookError`).
- **The hands' stamina red** (SHDR_Base_Inventory + Item_Hand_Material + Cosmetic_Hand_Default):
  - Each hand has its own material copy, because even the "default" hand is a cosmetic
    (`Cosmetic_HandItem` makes temp materials), so each hand shows its own grip. Without a
    cosmetic the material would be shared and both hands would show the same amount.
  - The default cosmetic's `outlineMaskColor` (0.49 grey) turns on the outline branch: sprite
    pixels of value 0.49 (125) fade grey to red linearly. Everything else is multiplied toward red
    by s = round(8a)/8 + ceil(8a)/16 (`_StaminaMaskRepeat` 8 with the default black mask), so it
    is fully red from about 69% grip used.
  - The Cosmetic_HandItem type tree doesn't read with TypeTreeGenerator (EOF). The fields were
    read raw, by position, from the class layout.
  - Driving the game: each new XTEST client on KDE Wayland triggers a "Remote Control" permission
    popup that steals focus, so the keystrokes land in the game instead of the console. Check
    for the popup (screenshot) before typing, or ask the user to tick "Always allow".
- **Post effects:**
  - In 1.21.9 post-effect uniforms are fixed per JSON file. The damage grade makes its own
    pass's uniform buffer writable (`PostPassMixin` adds `COPY_DST`) and writes it every frame.
  - Keep uniform blocks to plain floats: Minecraft's `Std140Builder` pads `vec3` to 16 bytes, but
    GLSL std140 packs a following float at offset 12, so the shader read padding.
- **Minecraft and Fabric hooks:**
  - Hook `Player.getHurtSound` / `getDeathSound`; `LivingEntity`'s versions are overridden.
  - Peaceful regen is `ServerPlayer.tickRegeneration`; `Player`'s is empty.
  - Minecraft calls `bobView` only with view bobbing on; the camera replacement hooks `bobHurt`,
    which always runs.
  - Folder resource packs must be complete when Minecraft scans them, so the extractor builds in
    `*.partial` and renames.
- **In-game tests:**
  - The gametest framework restores default options at test start (re-apply keybinds in tests).
  - `/tick rate` makes the integrated server fall behind the client; don't use it in gametests.
- **Windows extractor:**
  - Use Python 3.10-3.14 (3.15 has no wheels for UnityPy/brotli/lz4 yet), and
    `--only-binary=:all:` so pip never tries to compile.
  - libsndfile's Vorbis encoder overflows Windows' 1 MB main-thread stack when a multi-second
    clip is written in one call. Python dies with no exception, leaving only `*.partial`.
    `to_ogg` writes 4096-frame chunks on a 64 MB-stack thread. Reproduce on Linux with
    `ulimit -s 1024`.
  - In `.bat` files, crashes give negative exit codes that `if errorlevel 1` misses: use
    `if not "%errorlevel%"=="0"`.
  - `.bat` files need CRLF line endings.

## Open issues / next steps

1. **Windows extractor:** fixed in 0.2.1 (Python version picking, chunked Vorbis encoding, crash
   detection). It worked for a Windows 10 tester with their local workaround. Since then, the
   scripts have been checked on Linux with a 1 MB stack and under Wine's cmd, but not yet on a
   real Windows PC; the same applies to the Steam library search (`libraryfolders.vdf`, registry
   `SteamPath`), which was tested with a Windows-style vdf on Linux.
2. **Approximations, not exact ports:**
   - damage look: the vignette image is a fitted curve (within 0.02 opacity); blood splats and
     hit-direction glows are blended the GUI way, not in linear light like the game;
     damageFX.worldTint (red light on lit surfaces in the game's world shader) is left out.
3. **Not ported:** perks/buffs, items in hands, the game's inventory (the hotbar is hidden but
   number keys still switch slots), grappling, swimming, moving handholds, the ground-jump sound
   on vanilla jumps, the Focus mechanic (key is unbound; only the crosshair ring is shown).
4. **Verified in 0.3.0:**
   - a dedicated server: a standalone `runServer` boots and runs cleanly, and the game test joins an
     in-process dedicated server, climbs, and hangs 5 s with no kick;
   - the whole game test with and without the asset pack.

   **Not verified:** the guard for servers without the mod (`WKClimbClient`: checks
   `ClientPlayNetworking.canSend` on JOIN) against a real vanilla server. The positive case is in
   the test. Also, how it feels to play, beyond a few testers.
5. **Known trust issue:** the server takes the client's "hanging" flag on trust (no fall damage or
   fly kick while set). Documented for server owners in the README.
6. **Publishing:**
   - The repository is https://github.com/Fizz12215/White-Knuckle-Climbing (MIT, public).
   - Releases are normal GitHub releases (marked Latest, so they show in the repo sidebar; not
     pre-releases, even for betas) with the `betaZip` file attached. 0.3.0-beta was the first.
   - Commits use the owner's no-reply address (set in the repo's local git config).
   - Always ask the owner before pushing or publishing.
   - The knowledge-base note is in rehan-remade/universal-modder PR #302.
