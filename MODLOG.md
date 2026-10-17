# MODLOG

## 2026-10-13: route and first slice
- **Goal:** White Knuckle's climbing system in Minecraft Java.
- **Route:** mashup Pattern 1/5 (port the mechanic). White Knuckle is not run; its rules are
  reimplemented headless (`sim/`), and Minecraft is the host and view. White Knuckle (Unity 6, Mono)
  was only read offline from the user's Steam install.
- **Sources read:** ILSpy 8.2 decompile of `Assembly-CSharp.dll` (kept in a scratch dir, not in the repo);
  UnityPy + TypeTreeGeneratorAPI for the stripped MonoBehaviour data. TimeManager fixed step 0.02 s.
- **Unit contract:** 1 WK unit = 0.8 blocks (2.25-unit capsule vs a 1.8-block player). The WK capsule
  centre is mapped to the player's position + half the bounding-box height. WK `vel` → blocks/tick =
  `vel * 175 * 0.05 * 0.8`. The simulation owns movement only while a hand is hanging, plus the rest of
  the tick in which the last hand let go. Vanilla owns movement otherwise.
- **Server contract:** C2S `wkclimb:climb_state(bool hanging)` is sent on change and every 20 ticks.
  While it is set, the server resets fall distance and `aboveGroundTickCount`.
- **Oracles:** JUnit on the pure simulation (expected values derived by hand from the decompiled code);
  Fabric client gametest in a real 1.21.9 client.
- **Verified (real client, 2026-10-13):** all gametest checks passed: two-hand grab, climbed
  2.2 blocks in 1.5 s, grip −1.00 over 2 s with two hands, climbing jump +2.2 blocks, regrab
  while held, release, one-hand fatigue after ~10 s, ice slip after ~2.9 s, toggle.
- **Not verified:** dedicated multiplayer server, feel and tuning by a human player.

## 2026-10-13: ledges, WK hands and sounds
- **Ledges only:** a hold is the top edge of a block with open space above it. The crosshair can be
  on the upper 0.35 blocks of a side face, or the outer 0.35 of the top face next to an open side.
  The hold snaps to the edge. Holds drop if a block is placed on top.
- **Assets:** `tools/extract_wk_assets.py` builds a local resource pack from the user's install:
  - hand poses from `ENT_Player.hands[0].handPoses` (HND_B1A1–A4, B1B1, 128 px at 100 ppu);
  - crosshair icons from `interactionSprites`;
  - 9 sound sets (21 clips) from the player's `UT_AudioClipHandler`.
  It needed two workarounds:
  - FMOD in UnityPy rejects FSB5 IMA ADPCM, so the extractor has its own decoder. The
    4-byte-interleaved layout was confirmed by the L/R channels correlating at 1.000.
  - One Vorbis clip only decodes in a fresh process, so failed clips are retried in a subprocess.
- **Not reproduced exactly:** the hand shader's blend (DXBC not disassembled). The red overlay uses
  the shader's inputs: 8 levels, red body and outline.
- **Verified (real client, with the extracted pack):** the gametest passed.
  - Sprites and sounds loaded.
  - The side of a wall can't be grabbed.
  - The ledge hold snapped to y = −57.0.
  - Climbed from y −60 onto the ledge top (−57).
  - Grip drained 1.00 over 2 s with two hands.
  - Jumped, released and regrabbed.
  - One-hand fatigue came after ~10 s; ice slipped at 58 ticks.
  - Screenshots show the hands at rest, on the ledge, and reddening at half and low grip.
- **Not verified:** how the sounds sound (no audio capture in the test); a dedicated server.

## 2026-10-13: the White Knuckle character (movement, camera, Chimney ice)
- **Chimney ice:** the 314 `CL_Handhold_Icy` prefabs in sharedassets1 use maxHoldTime 2,
  slipSpeed 0.3, handShakeHoldTime 1 and handShakeAmount 0.5, plus slip and shake-start sounds. The
  type-tree generator emits a stray `references` registry before the subclass fields; drop it to read
  them. Chimney floors are the "Ice Footsteps" material (friction 0.05, moveLerp 0.2, moveSpeed 0.6).
- **Movement:** the simulation now drives all on-foot movement: sprint 2.5, crouch 0.3,
  IsSprintHeld (input > 0.8, not crouching), per-material friction/lerp/speed, and FootstepAudio
  timing (0.8 / 0.6 / 1.0 over max(curSpeed, 1.2)).
- **Camera:** ported MouseLook head-bob, the Movement camSway roll, FixedUpdate FOV smoothing and
  CL_CameraControl shake. Minecraft's bobView and the hurt tilt are replaced while alive.
- **Verified (real client):** walk 4.19 b/s (WK math 4.21), sprint 6.35 (6.78; measured while still
  accelerating), sprint FOV 85 (70 + 15), jump apex 2.11 blocks, ice slide 2.57 vs grass 0.34
  blocks, Chimney ice let go at 38 ticks, all climbing checks still pass. Unit tests: 9/9.
- **Not verified:** how the camera feels and the sounds' levels in normal play (no human playtest yet).

## 2026-10-13: keys, FOV, hand return, buttons
- **Keys:** Rewired `InputManager` in level1 `_userData` keyboard and mouse maps. Applied once:
  Sprint LShift, Crouch LCtrl, Inventory Tab. Hands LMB/RMB, Jump Space and WASD were already the
  same. 1/2/3, Q/E and F/R are not mapped (hotbar clash or no Minecraft equivalent).
- **FOV:** SettingsManager.playerFOV defaults to 90 (vertical, like Minecraft's FOV); it was using
  Minecraft's 70. All hands now project with the real camera FOV, plus the invCamFov scale/height
  adjustments during sprint FOV.
- **Hand return:** released hands now ease back through camera space via the ViewSway lerp, as in
  the game, instead of a screen-space blend.
- **Buttons:** grab-anywhere blocks get the same sphere cast as ledges (closest point on the outline).
- **Verified (real client):** all checks pass, including the button grab, the keys after apply and
  sprint FOV 105 (90 + 15).

## 2026-10-13: hand icon audit, swinging rods
- **Hand icons**, checked against InteractCheck, Hand.Set/Show/HideInteractionSprite and the
  Interact_L/R Image components (raw-parsed: type Simple, preserveAspect 0):
  - stretched to fill the 64x64 box (I had kept the aspect ratio);
  - hidden during the post-release cooldown (I had dimmed it);
  - kept up for the grabForgive hold memory;
  - always shown while holding (the hover pass is skipped then);
  - colour Lerp(red, white, round(grip * 6) / 6) at full alpha;
  - InteractHands x offset 0.154; handIconScale 0.8.
- **Bars:** the "Handhold-Bar" preset is dragMult 4, climbMult 0.1. Other presets: Handhold-Rod is
  drag 35; Handhold-Chains is climbGrav -0.1. Rod-like blocks use the bar preset and grab anywhere.
- **Verified:** unit test (bar swing keeps 1.9x the amplitude of a normal hold); real client (chain
  grabbed as a bar, one 12-tick pump gives a 1.7-block swing while still hanging); all earlier
  checks pass.

## 2026-10-13: ledges on non-full blocks
- **The problem:** ledges came from the bounding box of a block's collision shape. Fences and
  walls (collision 1.5 tall) had their ledge half a block above the visible rail, and the rail
  between two posts was never caught. Stairs only had one edge, at the full top and out front.
  Small blocks (lanterns) had the same issue.
- **The fix:** ledges are now the top edges of each box in the visible (outline) shape. An edge
  point counts only if two probes 0.04 blocks away are open: one just above and inward, one just
  outside and below. Both are tested against the outline shapes of whatever blocks are there. The
  ledge stops being a hold when something is placed over the grip point.
- **Verified (real client):** fence rail between posts gripped at y -59.0 (visible top; collision
  top -58.5). Cobblestone wall middle gripped at -59.125 (the low side). Stair lower step front edge
  at -59.5. Lantern body top at -58.5625. All earlier checks pass.

## 2026-10-13: rods without collision, damage overlay
- **Rods:** `#wkclimb:bars` lose their collision for a player swinging from one, for 10 ticks after
  letting go, and while still overlapping one. A BlockStateBase.getCollisionShape mixin runs on both
  sides; the server learns the state from the extended climb_state payload.
- **Damage overlay (from level3 data):**
  - CL_UIManager "hurt" vignette: colour (0.60, 0.03, 0.03, 0.82), fadeSpeed 0.8, 400 x 200 UI
    units past the screen, set to max(0.6, current) on a hit, target 1 - health/max.
  - Four damage-direction indicators: alpha = view-space hit direction, fading 1.25/s.
  - Screen blood (FX_Player_BloodSplatter_Screen): 4x4 sheet, a random tile per splat, 2 per hit
    and 6 on death, 1-2 s life, 2-3 units at 0.548 in front of the camera within a 1.5 circle,
    alpha 0 -> 1 -> 1 (57%) -> 0, drift down up to 0.03/s.
  - damageFX: worldTint (1, 0.2, 0.2), saturation 0.1, gamma 2, weighted by damageFXCurve. Drawn as
    a red layer (approximation; no post shader).
- **Verified (real client):** the swing passed through a bar pane (body to z 61.51; a solid pane
  stops at 61.14); on foot the pane blocks at 61.86. A hit from the right gives the right indicator
  0.89, vignette 0.58 and 2 splats (screenshots). All earlier checks pass.

## 2026-10-13: White Knuckle health, HUD
- **HUD:** hotbar, health, food, armour, air, XP level, info bar and held-item tooltip are hidden
  in WK mode (HudElementRegistry.replaceElement).
- **Health (server, ENT_Player values, x4 scale):**
  - healingRate 0.25 after healDelay 5;
  - FoodData.tick and ServerPlayer.tickRegeneration (Peaceful regen) cancelled;
  - FallingDamage ported: start at vy < -3 u/s, maxFall 50 units, 0.1/s past it while falling,
    death past 200, landing damage ceil((d - 60) / 15) + 1, halved when not grounded;
  - fall-grab costs grip 3 per holding hand (S2C fall_grip payload);
  - Player.causeFallDamage cancelled;
  - breath 10 s, refill 5/s, then 0.25 per second.
  WK mode reaches the server through the climb_state payload (resent on join).
- **Verified (real client + integrated server):**
  - food stays at 20 under Hunger 100;
  - 12.0 -> 12.0 after 4 s -> 16.1 after 9 s (delay, then 1 HP/s);
  - 30-block fall: no damage (vanilla would deal 27);
  - 60-block fall: 8.4 (WK 2 x 4, plus one 0.4 tick);
  - underwater: 20 at 9 s, 16 at 14 s;
  - screenshot shows the HUD cleared;
  - all earlier checks pass.

## 2026-10-14: rods vs walls of bars
- **Bug:** grabbing an iron-bar wall let you walk through it, because every #wkclimb:bars block
  lost its collision.
- **Fix:** WKClimb.isRod. A bars block counts as a rod only if its visible shape is under 0.5 along
  at least two axes. Only rods get the Bar preset and the swing pass-through. Joined panes are
  plain grab-anywhere handholds and stay solid.
- **Verified (real client):**
  - swinging passes a vertical chain (body to z 61.51; it would stop at 61.11);
  - on foot the chain blocks at 61.89;
  - an iron-bar wall is gripped as DEFAULT with pass-through off, and pushing into it stays at
    z 70.86 (pane face);
  - all other checks pass.

## 2026-10-14: damage greyscale
- **damageFX** is now a real post effect (assets/wkclimb/shaders/post/damage.fsh): saturation
  lerped 1 -> 0.1 by damageFXCurve, evaluated as Unity's Hermite curve with the extracted tangents.
  It is 20 steps (post_effect/damage_N.json), because 1.21.9 post-effect uniforms are fixed per
  file, and switched with GameRenderer.setPostEffect (invoker). Vanilla post effects are left alone.
- **worldTint (1, 0.2, 0.2) is dropped:** the game applies it in world lighting, and as a
  final-image multiply it made the screen red rather than grey (the user reports the game goes grey).
  The red translucent layer is removed.
- **Verified (real client):** at 25% health the grade is step 7/20 (wkclimb:damage_7); the
  screenshot shows the world desaturated with the hurt vignette at the edges. It clears at full
  health.

## 2026-10-14: health / damage / grade re-check (slime report)
- **Slime trace** (Normal difficulty, Size-2 slime, every tick):
  - health 17 -> 14 -> 11 -> 8 -> 5 -> 2 -> 0 (3 per hit, every 10 ticks), server and client in step;
  - grade step 0 -> 2 -> 4 -> 7 -> 9 -> 20, only increasing;
  - no flicker in health or grade;
  - Minecraft loads post chains synchronously, so there are no effect-less frames when the step
    changes;
  - the game's FXManager.CameraFX blends from scratch every frame, with no smoothing.
- **Where colour comes back (both as in the game):** regen restarts 5 s after the last hit at
  1 HP/s, and grey only begins below ~67% health, so a short break from a bouncing slime can bring
  colour back; dying and respawning also does.
- **Real bugs found and fixed:**
  - a teleport or respawn to a lower spot was measured as a fall (fall damage on arrival);
    moves over 5 blocks in a tick now reset the fall;
  - health state is cleared on respawn;
  - onDamaged creates the state if missing.
- **Verified:** after a death the respawn is at 20 HP with grade 0; an 80-block teleport down does
  no damage; all checks pass.

## 2026-10-14: smooth damage grade (fades out as in the game)
- **Problem 1:** the 20 fixed post-effect files made the grade step and then pop off.
- **Problem 2:** the old block {vec3 Tint; float Saturation} was laid out differently by
  Minecraft's Std140Builder (float at 16) and GLSL std140 (float at 12), so the shader read
  Saturation from padding.
- **Now:** a single wkclimb:damage effect with DamageConfig {float Saturation}. Its uniform buffer
  is made writable for this mod's passes only (PostPass constructor @ModifyArg adds COPY_DST), and
  it is written every frame from damageFXCurve(health / max).
- **Verified in the real client:**
  - at 25% health, blend 0.34 and centre-screen HSV saturation 0.40 -> 0.26 (matches 1 - 0.9 x 0.34);
  - while healing the grade holds 5 s, then falls 0.34 -> 0 over about 8 s in 17 decreasing samples,
    never rising, then switches off;
  - all checks pass.

## 2026-10-14: stronger grey near death; faster in-game test
- **Grey:** saturation = 1 - 0.9 * min(1, blend / 0.53). Same damageFXCurve shape and timing, but
  normalised to the curve's 1.8%-health key (0.53), so near death it is close to greyscale. The
  plain mix had kept over half the colour there, and the user reports the game is much greyer.
  Roughly: 50% health keeps ~80% colour, 25% keeps ~40%, about to die ~15%.
- **Test speed:**
  - the in-game test went from ~3 min 23 s to ~2 min 3 s (whole run 146 s), with all 21 checks
    and measurements kept;
  - test hooks reset grip (ClimbSim.resetHands), the damage overlay (ClimbDamage.resetForTest) and
    breath (WKHealth.setBreath) instead of waiting for them to regenerate;
  - shorter but still rule-covering windows: fatigue from 3 grip (54 ticks), regen 4 s delay then
    3 s, fade 8 s, breath from 2 s, the slime trace stops at death;
  - tried `/tick rate 60` and dropped it: the integrated server fell behind the client, so
    server-side timing measured in client ticks became unreliable.

## 2026-10-14: 0.2.0-beta package for testers
- `./gradlew betaZip` -> build/beta/White-Knuckle-Climbing-0.2.0-beta.zip (~116 KB). Contents:
  BETA-README.txt, the mod jar, the extractor, requirements.txt, a Windows .bat and a Linux/macOS
  .sh. No White Knuckle files: each tester builds the pack from their own install.
- **Extractor:**
  - encodes Ogg Vorbis with `soundfile` (pip wheels on all platforms; ffmpeg only as a fallback);
  - `--zip` writes a single resource-pack zip.
- **Builds:** installMod now deletes older white-knuckle-climbing-*.jar from the mods folder.
- **Verified:**
  - zip and jar contain no png/ogg/wav or game data;
  - the .sh run from a clean unzip with a fresh venv built a 104-file pack zip with pack.mcmeta at
    the root (283 s including the first-time pip install);
  - the Windows .bat has not been run (no Windows machine here).

## 2026-10-14: beta extractor on Windows (tester report)
- **Failure:** a Windows 10 tester had Python 3.15. UnityPy, brotli and lz4 have no win_amd64
  wheels for cp315 yet, so pip tried to build them and stopped at "Microsoft Visual C++ 14.0 or
  greater is required".
- **Check (`pip download --platform win_amd64 --only-binary=:all:` for each version):** Python
  3.10-3.14 all resolve to 18 prebuilt packages; 3.15 fails on UnityPy.
- **Windows script now:**
  - picks py -3.13 / 3.12 / 3.14 / 3.11 / 3.10 even when 3.15 is the default;
  - if none is installed, offers `winget install Python.Python.3.13`, else links python.org;
  - wipes a venv that never finished setup (marker file per Python version);
  - installs with --only-binary=:all:, so it never tries to compile;
  - is saved with CRLF line endings (labels misbehave with LF in cmd).
- **Shell script:** the same version picking, marker file and only-binary install.
- **Verified:** the .sh from a clean unzip with a fresh venv (python3.14, only-binary) builds the
  104-file pack. The .bat still hasn't been run on Windows.

## 2026-10-14: handoff cleanup
- **Added:**
  - DEVELOPING.md: setup, commands, layout, tick flow, unit and network contracts, where every
    number comes from, gotchas, open issues;
  - tools/dump_component.py: the research dumper, with the type-tree and raw-Image workarounds,
    tested against ENT_Player and CL_Handhold_Icy.
- **Changes:** .gitignore covers Python caches and extractor output; README points developers to
  DEVELOPING.md; tools/__pycache__ removed.
- **Archive:** ~/White-Knuckle-Minecraft-source.zip (source only: no build output, caches,
  local.properties, game files or decompiled code). A clean unzip builds, passes 10 unit tests and
  produces the beta zip; installMod skips without local.properties.
- **Still open:** the Windows extractor failure; the new error output is needed.

## 2026-10-14: 0.2.1-beta, Windows sound extraction crash
- **Tester report (worked around locally):** Python died during sound conversion and only the
  `.partial` folder was left; the .bat still printed "Done!".
- **Root cause, reproduced on Linux with `ulimit -s 1024` (the size of Windows' main-thread
  stack):** `soundfile.write` of a whole clip into Ogg Vorbis segfaults on clips of a few seconds
  (the fatigue loop is one). Writing 4096-frame chunks works at any stack size.
- **Fix:**
  - `to_ogg` encodes in chunks on a 64 MB-stack thread and checks the output file; ffmpeg is used
    only if soundfile is unavailable, so testers don't need to install it.
  - The .bat compares `%errorlevel%` with 0 (crash codes are negative, which `if errorlevel 1`
    misses), deletes any old zip first, and only says Done if the new zip exists; the .sh also
    checks the zip.
- **Verified:**
  - at a 1 MB stack the 0.2.0 extractor segfaults leaving `.partial`, while the new one builds the
    full 104-file pack;
  - the packaged 0.2.1 .sh works at a 1 MB stack;
  - under Wine's cmd, the old errorlevel check misses a crash code and the new one catches it
    without false alarms.

## 2026-10-14: 0.2.2-beta, extraction 285 s -> 13 s, exact IMA decoding
- **Profile:** 266 of 285 s was 75 per-clip FMOD subprocesses, each re-loading sharedassets1.
  Main-process FMOD decoded 0 clips, while a fresh process decodes all 78.
- **Now:** ClipDecoder decodes every clip in one `--decode-clips` subprocess per file.
- **Found while comparing:** the old IMA decoder was off by a sample per 64-sample block (it
  didn't output the header predictor and used all 64 nibbles), so the grab/let-go sounds had
  slight glitches at block edges. Header-then-63-nibbles matches FMOD bit-for-bit. It is now the
  fallback when FMOD fails.
- **Verified:**
  - normal run, 1 MB-stack run and the FMOD-only reference: 12.7 s, all 68 sounds decode to
    identical samples;
  - `--no-fmod`: 64 sounds identical to the reference, and only the 4 FMOD-only (Vorbis) clips
    missing;
  - textures unchanged.

## 2026-10-14: 0.2.3-beta, the damage look matched to the game
- **Feedback:** after a playtest, damage "doesn't look exactly like the game".
- **References:** the user stood in the playground's green-screen rooms. I drove the game (KDE
  Wayland, XWayland window: XTEST keys via python-xlib, Spectacle screenshots) and captured each
  room at WK health 5/4/3/2/1/0.1 with `hurtplayer dontkill 1`.
- **What the game does** (from the green/white/black frames, then confirmed in the disassembled
  "Post Process WK" shader):
  1. HSV saturation x (1 - 0.9 curve), keeping the value, so pure green turns *white*. We had
     mixed toward luma, which gave dull grey.
  2. x (0.25 + 0.75 (brightness + 2 curve)): the picture brightens as you get hurt
     (damageFX.gamma 2). We had nothing.
  3. Hurt vignette: colour **and** alpha x amount (`Color.Lerp(clear, c, amount)`), so it grows
     like amount²; blended in linear light. We had alpha only, blended in sRGB.
  - With the real vignette image, a per-pixel model fits all 15 frames at rms 2.8/255. The fitted
    vignette amounts are 0.23/0.42/0.60/0.785/0.968 against 1 - health/max =
    0.2/0.4/0.6/0.8/0.98. The user's brightness setting (2.0) also came out of the fit (1.97)
    before I found it in settings.json.
- **Now:**
  - one post pass does the grade and the vignette in linear light (`wk_damage.glsl`). The
    vignette image is a fitted curve (rms 0.02 alpha), so it no longer needs the pack;
  - the hands use a custom GUI pipeline (`core/hand.fsh`, parameters in the vertex colour), so
    they get the same look per pixel, as the game's in-world hands do (red outline near death).
- **Verified:**
  - new game-test scene: white/lime/black concrete walls at health 16/12/8/4/0.4;
  - each frame matches the model applied to the full-health frame: 0.25-0.27/255 mean error;
  - the WK and Minecraft frames look alike side by side; the full game test passes.

## 2026-10-14: 0.2.4-beta, the hands' stamina red matched to the game
- **Source:** SHDR_Base_Inventory disassembled, with uniform offsets from the pass's parameter
  table. Values from Item_Hand_Material and the raw Cosmetic_Hand_Default.
- **Before (approximation):** a pre-made red copy of each sprite, faded in by floor(8a)/8, with
  outlines jumping to bright red.
- **Game:**
  - the hand body is multiplied toward red by s = round(8a)/8 + ceil(8a)/16, stepped and
    reaching full red just over 62.5% used (first written here as ~69%; corrected
    2026-10-15, see below);
  - outline-mask pixels (0.49) fade grey to red linearly;
  - then x 0.81 grey, in linear light.
- **1 vs 2 hands:**
  - grip drain was already exact: each hand drains on its own, at half rate while both hang;
  - display: the default cosmetic gives each hand its own material, so each hand shows its own
    red. The mod already did, and now has a test for it.
- **Now:** hand.fsh does it per pixel, with the amount in the vertex alpha (128 + 127a). The
  extractor no longer writes `*_stamina.png`.
- **Verified:**
  - game test sets the left hand to 4-99% used and the right to full, on the black wall;
  - left-hand pixels of all three sprite values match the formula within 2/255 at all 8 levels;
  - the right hand stays (207, 207, 207);
  - full suite passes.
- **Lesson:** a game-driving attempt went wrong. KDE's Remote Control popup took focus, so
  "setstamina" was typed into the game (the user saw the player jumping). Its measurements were
  discarded.

## 2026-10-14: 0.3.0-beta, cleanup and checks for the first public release (climbing only)
- **Safety:** on a server without the mod, climbing switches itself off at join (with a
  message), and R won't turn it back on. Before this, the client would hang in mid-air there and
  get kicked for flying, or flagged by anti-cheat. Single-player and LAN hosts always count as
  having it.
- **Dedicated server, first time verified:**
  - standalone `runServer` boots and runs cleanly with the mod;
  - new game-test step: join an in-process dedicated server, climbing stays on, both hands hang
    5 s with no kick, and the server applies WK mode.
- **Full game test** passes with and without the asset pack (21 checks).
- **Extractor:**
  - finds White Knuckle in every Steam library (`libraryfolders.vdf`, Windows registry
    `SteamPath`, Flatpak and Snap Steam);
  - better "not found" help;
  - no longer writes the unused `hurt_vignette.png`;
  - a stray `fmod_only` was removed from `Files`;
  - the "restart Minecraft" hint now only prints for `--instance` installs.
- **Scripts:**
  - Linux: install hints for missing Python or `venv` (Ubuntu/Debian/Mint, Fedora, Arch/SteamOS);
  - both: generic "report on the mod's page" messages instead of "send to Fizz"; macOS dropped
    (White Knuckle is Windows-only).
  - Checked: a fresh-user Linux run from a clean unzip (21 s first run, 13 s after); the `.bat`
    "Python missing" and failure paths under Wine cmd.
- **Docs:**
  - README.md rewritten for players (features, Windows/Linux install, controls, multiplayer and
    the trust caveat, settings, troubleshooting, credits, AI disclosure);
  - `beta/README.txt` is the step-by-step guide inside the download;
  - new CHANGELOG.md.
- **Publish lint:** the release zip and jar contents pass `um publish check` (no game files,
  keys or home paths).

## 2026-10-15: correction, hand red threshold
- The knowledge-base maintainer, merging PR #302, caught that s = round(8a)/8 + ceil(8a)/16 is
  already 1 once 8a passes 5 (5/8 + 6/16). So hands are fully red just over **62.5%** used, not
  "about 69%" as the docs said: I had checked a = 0.6875 instead of the first value that reaches 1.
- The shader and the game test always used the formula itself; only the prose was wrong.
  Corrected in CHANGELOG, DEVELOPING and the hand.fsh comment.

## 2026-10-15: 0.3.1-beta, full review against the game (same build 25612096)
Reviewed: Movement, Hang, Jump, HandStamina, InteractCheck/EnqueueInteractCast, StopInteraction,
HandAnimation, ViewSway, MouseLook, FallingDamage, LandingAudio, CL_Handhold(_Icy), Rewired input,
CharacterController, against ClimbSim and the adapter. Grip, hang pull, climb accel, jumps, ice, falls,
landings, hand sway (every ViewSway set: bobSwayOffset 0, impulseRecoveryMult 1) and camera all matched.

**Fixed, each now behaving like the game:**
1. **Ground jumps kicked the hands twice:** the sim's JUMP event and ClimbHands' "left the ground"
   check both called onJump(). The check now runs only when the sim isn't driving.
2. **Diagonal climbing was ~30% weak:**
   - Rewired: two independent axes, not normalised, and `_digitalAxisSimulation` 0, so keys are
     instant;
   - Minecraft normalises W+D to (0.71, 0.71);
   - fix: build the axes from the keys.
3. **The wall probe was 1.125 units:** the game uses `cCon.radius * 2.5`, with the unscaled 0.3,
   so 0.75 units.
4. **Wall friction used the raw sprint key:** the game uses the sprint state (key + input above 0.8
   + not crouching).
5. **Minecraft's sneak edge guard applied in WK mode:** fixed by a common mixin keyed on isWkMode,
   so the server's replay agrees.
6. **The hang anchor followed Minecraft's crouch box:** it is now the standing capsule centre
   (WK transform.position, CharacterController centre 0).
7. **Grounded-state timing around the move:**
   - the post-move clamp now uses the pre-move flag;
   - walking off an edge zeroes falling speed (`isGrounded && !cCon.isGrounded && vel.y < 0`).

   Unit tests for 4 and 7 fail on the old code and pass on the new.

**Added from the game:**
- **Eye height:**
  - Main Cam Target sits at local y 0.45 under CL_Player (scale 1.5): 1.44 blocks standing, and
    0.84 crouched (ChangeHeight: 0.45 + crouchHeight 1 - standingHeight 1.5);
  - EyeHeightMixin on Avatar.getDefaultDimensions, client only, refreshed when stale.
- **Falling wind:** windAudio = "PlayerFalling", 20.6 s, pitch 1.2. Volume max((-vy - 2) * 0.03, 0),
  lerped at fixedDt * 5; it restarts on each new fall.
- **Long-fall shake:** past 50 units, ShakeCamera(0.1 * dt), with a client-side copy of
  FallingDamage's distance (also used for the LONG_FALL hand sway).
- **Near-death loops:**
  - damageAudio "player_neardeath_heartbeat_layer1" to damageFXCurve * 0.8;
  - dyingAudio "player_neardeath_mortalwound_layer2" to dyingAudioCurve * 0.8, curve keys dumped;
  - both lerped at dt * 2, and they start and stop together because they're layers of one piece.
- **"handhold-metafungus" grab:** 4 clips, maxVolume 0.204, for SoundType FUNGUS / SHROOMLIGHT /
  NYLIUM / MOSS / FROGLIGHT.
- **Held hands at 95%:** Hand.ScaleUpdate, clamp(1 + (fov - 90) * -0.015, 0.6, 1.2) * 0.95 while
  holding, easing back at 5/s.
- **Config `exact_step_height`:** CharacterController stepOffset 0.15 x 1.5 = 0.225 units = 0.18
  blocks. Off by default (Minecraft's 0.6), as the owner chose.

**Extractor:**
- LOOP_SOURCES reads the clip from AudioSource.m_Resource (Unity 6; m_audioClip is empty).
- 85 clips, ~13 s, also checked at a 1 MB stack.

**Also fixed:** without the pack, blood splats never expired and the hit indicators never faded
(render returned before both), so the list grew all session.

**Tests:**
- 12 unit tests;
- the game test, with and without the pack, now also checks:
  - eye height 1.44 / 0.84 / 1.62 with climbing off;
  - crouch-walking off a 1-block ledge drops you, without the server pulling you back;
  - step height 0.6 / 0.18;
  - a moss grab plays handhold_metafungus;
  - the near-death loops at 2% health: 0.422 / 0.794 (game 0.423 / 0.797);
  - the 60-block fall: wind peaks at 0.77, and the camera shakes;
- six fixed ledge aims moved from -52 to -55 degrees for the lower eye (the margin to the edge is
  0.076 blocks, against 0.062 before).

**Not done, by the owner's choice:** sampling the aim every frame (Minecraft samples at 20 Hz).

## 2026-10-15: snow and inner corners (player report)
- **Report:** "there is an issue with snow layers", and "you can grab on the inner corner of 2
  blocks". Reproduced in a new game-test scene before changing anything:
  - a ledge under one snow layer: not grabbable;
  - the inner corner under an overhang: grabbed at the corner;
  - the end of a ledge against a taller block: grabbed at x 150.97, 0.03 from the neighbour;
  - the middle of that ledge: grabbed (control).
- **Causes:**
  - Snow: one layer has no collision box but a 2-pixel visual shape, and the ledge probes used visual
    shapes, so it "covered" the edge. The snow itself isn't a handhold (no collision), so snowy biomes
    had almost no ledges.
  - Corners: `isLedgePoint` only checked above-inward and below-outward, so a block's back edge under
    a block one up and one back passed, as did a ledge end pressed into a taller neighbour.
- **Fix (isLedgePoint / insideShape):**
  - blocks without a collision box don't cover ledges (also leaf litter, petals, rails, plants);
  - new "over" probe (outward, above the top) must be open: that's where the hand comes over;
  - the top must stay open 0.1 blocks either way along the edge (a hand's room).
- **After:**
  - 1 layer: grabs the block edge (-58.0); 3 layers: grabs the snow's own edge (-57.625);
  - both inner corners: nothing to grab; the ledge's middle still grabs;
  - fence rails, wall tops, stairs and lanterns still catch where they did, and the full suite passes.
- **Test helper:** `tryGrab` waits 6 ticks after aiming, so the 0.2 s grab memory of the previous
  view can't satisfy a check (the first run grabbed x 150.8 from memory).

## 2026-10-15: grabbing a flat floor at block seams (player report)
- **Report:** "you can grab a flat floor at the seam of the blocks". Reproduced: aiming at the point
  where four floor blocks meet grabbed (171.0, -60.0, 0.0). The middle of a seam between two blocks
  didn't (control).
- **Cause:** when the closest point is an edge's exact end, it lies on block boundaries. The "drop
  beside" probe then sits on the diagonal block's face, and insideShape's strict test calls that
  outside, so the corner passed as a ledge.
- **Fix:** findHandhold judges, and grabs at, a point 0.001 blocks inside the edge instead of its
  exact end.
- **After:** floor corner and seam both grab nothing; all other ledge checks are unchanged (the wall-top
  grab moves by the nudge, x 1.0 -> 1.001); full suite passes.

## 2026-10-15: screen blood vs the field of view (player report)
- **Report:** "the blood splash effect seems to conflict with fov effect".
- **Game data:** FX_Player_BloodSplatter_Screen is a child of "Overlay Camera" on layer 29, and that
  camera renders only layer 29 (cullingMask 1 << 29) at depth 5, the last camera. Its FOV is a fixed
  90: SetCameraFov changes only cam and inventoryCam, and code only ever touches the overlay
  camera's scale. So the game's blood never follows the sprint FOV (+15) or the FOV setting.
- **The mod** projected the splats with the current WK FOV, so they shrank about 23% and slid inward
  while sprinting. Now a fixed 90 (OVERLAY_CAMERA_FOV).
- **Order:** the UI canvas holding the hurt vignette is Screen Space - Overlay (renderMode 0), drawn
  after every camera, so the vignette covers the blood. The mod drew the blood (GUI) over its
  vignette (post pass). Now the blood goes through core/blood.fsh, which applies the vignette to each
  splat pixel (wk_vignette, split out of wk_damage). The vignette is a per-pixel affine blend, so
  that equals drawing it over the splat. The damage grade isn't applied, since the overlay camera
  renders after post-processing.
- **Checked:** the full suite passes (damage look still 0.25/255 from the model), the hit screenshot
  shows the splats with the vignette tint toward the edges, and blood splats are still 2 per hit.

## 2026-10-15: the in-game Build button for White Knuckle's hands and sounds
- **Request:** "is it possible to streamline the asset extracting process such that it happens
  automatically?", for Windows and Linux. The owner chose a build button in Minecraft that runs the
  existing Python extractor. A Java rewrite was ruled out: Unity 6 files with no type trees, BC
  textures, 4 FMOD-only Vorbis clips.
- **Design:**
  - the extractor and requirements.txt ride in the jar;
  - at the first title screen the mod checks the pack (missing / outdated via the new
    `wk_pack_info.json`, PACK_CONTENT_VERSION 1) and offers Build;
  - `AssetBuilder` sets up the scripts' private venv and runs the extractor into resourcepacks/;
  - the pack is switched on with no restart;
  - later version bumps rebuild quietly once the player has agreed.
  Details in DEVELOPING ("The in-game asset build").
- **Found while planning:** Prism from Flathub can't see `~/.local/share/Steam`. Its runtime's
  Python 3.13 has venv + ensurepip, and XDG_DATA_HOME is the app's own data folder. The screen
  shows the one-line `flatpak override` (needs a launcher restart).
- **Verified:**
  - 22 unit tests (vdf, registry output, game folder, .flatpak-info, Python probe and order, venv
    paths, pack version = the script's);
  - `WKCLIMB_TEST_BUILD=1 WKCLIMB_TEST_PACK=... runClientGameTest`: all climbing checks pass with
    the pack; then the real build from Steam's White Knuckle with Python 3.14:
    - venv set up in 6 s (wheels already in pip's cache), extraction 13 s, 85 clips;
    - the pack is switched on and the WK hands load with no restart;
    - a version-0 pack is rebuilt in the background with a toast;
  - each help screen screenshotted (layout OK; a toast line that ran off the screen was shortened);
  - inside the Prism Flatpak sandbox, with a one-run `--filesystem` grant: venv + pip + extractor
    in 29 s including downloads. Same 97 files as the instance's 0.3.1 pack, plus the info file;
  - the winget command line (`cmd /c start "<title>" cmd /c "... & pause"`) runs both parts under
    Wine's cmd.
- **Sizes:** first setup downloads 45 MB of wheels (Python 3.14, Linux); the venv is 184 MB.
- **Not verified:** a real Windows PC (Python search, winget install, registry Steam path); a
  real Minecraft launched from the Flatpak (the sandbox steps were run by hand); a truly fresh pip
  cache on a slow connection.

## 2026-10-16: folder picker, a confirmation before every step, Windows review, safety docs
- **Asked for:**
  - a button that replaces the Flatpak terminal command;
  - instructions that make sense on Windows;
  - telling players exactly what the mod does to their computer;
  - documentation on the dangers of this kind of access.
- **Owner's choices:** the folder button everywhere, and a confirmation before each step.
- **Design:**
  - steps CREATE_VENV, INSTALL_LIBRARIES, BUILD_PACK and ENABLE_PACK; each waits in state CONFIRM;
  - the silent background rebuild and `asset_auto_update` are gone;
  - the folder picker: the desktop portal on Linux (a pure-Java D-Bus client), tinyfd on Windows;
  - the screen leaves fullscreen before opening any other window.
- **Found:**
  - in Prism's sandbox, zenity is 3.44 (GTK3), whose dialog shows only the sandbox, and there's no
    Python `gi`. `gdbus call` exits before the portal's Response arrives. So the portal is called
    directly.
  - The earlier Flatpak command granted all of `~/.local/share/Steam`. That folder includes Steam's
    `config/` (`config.vdf` was readable from inside the sandbox). The fallback command is now limited
    to White Knuckle's folder, and the docs explain why.
  - **Windows:** a bare `python` can be Microsoft Store Python, whose AppData writes are redirected.
    Removed it, added the Program Files paths, and the builder now checks the venv's python exists.
  - pip evaluates `python_version` markers against the running interpreter, not `--python-version`, so
    the pin check resolves the markers per version with pip's own `packaging`.
- **Pinned** `tools/requirements.txt`: 18 packages; numpy 2.2.6 (3.10), 2.4.6 (3.11), 2.5.4 (3.12+).
  - All 10 combinations of Python 3.10-3.14 x Linux/Windows resolve to exactly the pinned set.
  - Downloads: 34 MB on Windows, 45 MB on Linux. A fresh venv is 183 MB and builds the same 98 files.
  - The pip-upgrade step is dropped (mod and scripts).
- **Verified:**
  - 31 unit tests: D-Bus encoding and decoding (incl. big-endian), Response parsing, bus addresses,
    chosen-folder rules, document-portal ids, the Windows candidates, the steps needed, all
    requirements pinned, pack version = the script's;
  - the portal client read `org.freedesktop.appearance color-scheme` from the desktop and from inside
    Prism's Flatpak with Prism's own Java (through the sandbox's bus proxy);
  - a gametest with an empty `XDG_DATA_HOME`:
    - clicked Continue through all 4 steps (27 s, with the wheels in pip's cache);
    - the pack is on and the WK hands load;
    - Stop at a step changes nothing;
    - an out-of-date pack brings back the offer (nothing runs by itself), and the rebuild has 2 steps;
  - the climbing suite passes;
  - screenshots of all pages, incl. long Windows-style paths (they wrap without hitting the buttons).
    Fixed after looking: lone footer buttons now centred; two sentences that didn't fit their page.
- **Owner's test in the real Flatpak Prism (00:19):**
  - the broad Steam override removed;
  - "Choose its folder..." opened the portal dialog;
  - the choice came back as `/run/user/1000/doc/lh9OK_QVZ5rSxGU2CFyb3g/White Knuckle` and was saved
    as `wk_game_dir`;
  - the steps ran from there (the libraries were reinstalled for the new pins, then the build and
    turning it on);
  - the pack (version 1) is on. The owner reports it works.
- **Not verified yet:**
  - the `document-unexport` undo;
  - on real Windows: tinyfd's dialog, winget and Store-Python detection. 0.4.0-beta goes to a
    Windows tester for that.

## 2026-10-16: Windows 11 test of 0.4.0-beta, and fixes from it
- **How:** the owner booted their Windows 11 dual boot. A Claude Code session there followed
  `windows-test/README.md` in the private testing repo and pushed `windows-test/results/` back. It
  automated the clicking with universal-modder's Windows tools.
- **Starting point:** no real Python (only the Microsoft Store alias), no Java (Temurin 21 installed
  with the owner's OK), and White Knuckle installed for the test.
- **Worked on Windows:**
  - the Python search ignored the Store alias;
  - "Install Python 3.13" opened winget in a titled Windows Terminal window, in front of Minecraft.
    It installed per user with no UAC prompt and ended at "Press any key to continue". Try again
    found `%LOCALAPPDATA%\Programs\Python\Python313\python.exe` without a restart;
  - the venv and pip with the pinned Windows wheels, and the extractor (85 FMOD clips, 0 failed);
  - the pack switched on. In a world: the hands, the crosshair, the grab pose, and the grab and
    release sounds (recorded from the game's own audio);
  - tinyfd's "Browse For Folder" opened in front of Minecraft, starting in `steamapps\common`. A wrong
    folder was rejected, and the right one went on to the steps;
  - fullscreen became a normal window while the dialog was open, then fullscreen again;
  - Stop changed nothing;
  - the beta .bat worked;
  - 31 unit tests passed, and so did the climbing gametest.
- **Bug found (not Windows-specific):** `ClimbHands.haveTextures()` and `ClimbDamage.available()`
  cache their resource lookups for 200 calls, and nothing reset them on a reload. The asset gametest
  failed `WK hand sprites loaded` when the climbing part before it had run without a pack. Linux runs
  had always set `WKCLIMB_TEST_PACK`, so they never hit it. For players: a pack switched on showed up
  only up to 200 frames later.
  - **Fix:** the Windows session's patch. A v1 `ResourceLoader` client reload listener drops both
    caches.
- **Smaller fixes from the report:**
  - **the folder spelling:** a game found through the registry `SteamPath` (`c:/program files
    (x86)/steam`) was shown in lower case. It now goes through `toRealPath()`;
  - **a wrong `wk_game_dir`:** the not-found screen blamed the Steam libraries. It now names
    `wk_game_dir` and its value;
  - **the config file:** `wk_game_dir` was saved escaped (`C\:\\...`). The config is now written by
    hand (fixed order, paths as written, `/` on Windows), and the raw line is read, so a Windows path
    typed with backslashes works. The "write / instead of \" hint is gone;
  - **disk size:** the private folder is 126 MB on Windows, against 183 MB on Linux. The page shows
    130 / 185 MB per system;
  - **wrapping:** the Python path and the log path now sit on their own lines, and each undo line
    names its folder.
- **Verified here:** 36 unit tests (new: `ClimbConfigTest` for the raw `wk_game_dir`, and a
  symlinked game folder resolving to its real path). The full gametest ran exactly like the failing
  Windows run (no `WKCLIMB_TEST_PACK`, a fresh `XDG_DATA_HOME`): `ALL CHECKS PASSED (with WK asset
  pack: false)`, then every asset check including `WK hand sprites loaded`.
- **Owner's follow-ups (same day):**
  - pages whose lines were all short (the progress pages, "Waiting for you to choose a folder",
    steps 2 and 4) sat left of centre: the scroll area keeps the full text width, and the narrower
    column hugged its left edge. A full-width spacer now keeps the column full width;
  - at the owner's request, the placeholder pixel-art hand icons drawn without the pack were
    removed. Without the pack there's now no hand HUD (Minecraft's crosshair and arm).

## 2026-10-16: port to Minecraft 26.3 (0.5.0-beta)
- **Goal:** move 0.4.0-beta to Minecraft 26.3, unchanged in play. 0.4.0-beta (tag `v0.4.0-beta`) is
  the last version for 1.21.9. Branch `port-26.3`; the working notes are in `PORT-26.3.md` until the
  port is done.
- **Method:** the port-a-mod skill. 0.4.0-beta is the source of truth (`git show v0.4.0-beta:...`),
  every 26.3 name was looked up with `javap` against the real jars, one commit per unit, and the
  gametest is the oracle on both graphics engines.
- **What changed:**
  - build: non-remapping Loom 1.18, no mappings (26.x ships Mojang's names), Java 25, Gradle 9.7.1;
  - `ResourceLocation` -> `Identifier`; payload registry renames;
  - shaders: ShaderC rules (`layout(location)`, `#include`, the separate-shader-objects extension);
  - render pipelines and GPU buffers moved to `com.mojang.renderpearl`;
  - the damage look: 26.3 rebuilds a list of post effects every frame, so a hook adds ours to it
    (it now stacks with vanilla's effects instead of giving way to them);
  - GUI: `GuiGraphicsExtractor`, screens through `mc.gui`; input: SDL3 scancodes and mouse buttons;
  - camera: the FOV hook moved to `Camera.calculateFov`; the bob hooks take the camera render state;
  - the Windows folder picker: tinyfd is gone from 26.3, so it's SDL3's `SDL_ShowOpenFolderDialog`;
  - the pack: format 97, content version 2, so 0.4.0 packs get the rebuild offer;
  - Fabric Loader 0.19.3 or newer (what Fabric API for 26.3 needs).
- **Test tooling:** `WKCLIMB_TEST_GRAPHICS=opengl|vulkan` picks the engine for the gametest (passed
  as `--graphicsBackend`, since the test framework rewrites `options.txt`). `installMod` now skips a
  Prism instance of another Minecraft version: an early port build had overwritten 0.4.0-beta in
  the owner's 1.21.9 instance (restored from the release zip).
- **Verified (this Linux PC, NVIDIA 615.78):** 36 unit tests. Gametest:
  - OpenGL, no pack: `ALL CHECKS PASSED (with WK asset pack: false)`;
  - OpenGL, with a freshly built 26.3 pack: all checks passed; damage look within 0.24-0.26/255 of
    the model at every health level, as on 1.21.9;
  - Vulkan, with the pack: all checks passed, same numbers;
  - `WKCLIMB_TEST_BUILD=1` on Vulkan and on OpenGL: the Build button, the out-of-date offer, Stop and
    the rebuild all passed.
- **Found by looking at the screenshots, not by the checks:** the setup screen showed only its title
  and buttons. 26.3's scroll area keeps whatever height it had when its maximum was set, and we set
  the maximum before laying out the text (0 tall). Fixed by laying it out first, as vanilla does.
  The test helper for the screen's pages also built its widgets twice (26.3 screens know
  `minecraft` from the start), which drew the title twice.
- **Folder picker start (owner's request):** in a Flatpak the sandbox can't see Steam, so no start
  folder was sent and the portal opened at home. It now suggests `~/.local/share/Steam/steamapps/common`
  unchecked: xdg-desktop-portal 1.22.1 passes a plain `current_folder` on unchanged, and the KDE
  (6.7.5) and GNOME backends open it (read in their sources). On Windows, SDL3 takes what follows
  the last separator as a file name, so it opened `steamapps` with "common" typed in; the path now
  ends with a separator, as tinyfd's did. Unit-tested; not yet seen on Windows or in the Flatpak.
- **Not verified yet:** Windows (above all the SDL3 folder picker), a manual play in a real 26.3
  client, the Linux portal picker and fullscreen handling outside the test.

## 2026-10-16: the low-health sound's volume (owner's report: "seems too loud")
- **Checked against the game first:**
  - `ENT_Player`'s serialized `damageAudioVolume`/`dyingAudioVolume` are 0.8, as the mod uses; the
    health curves match (0.003 at 2% health, from the gametest).
  - The three loop AudioSources (heartbeat, mortal wound, wind) are 2D and output to the
    `Main-Mixer` group "Master" (-0.19 dB). The game's other sounds reach Master through "Effects"
    (0 dB), so the mixer adds no difference. Its other groups (Announcer -3.9 dB, Radio -8.5 dB, with
    lowpass and reverb) and Master's pitch shifter don't touch these sounds.
  - The extractor's mono mix is a plain average, with no gain.
- **Measured** (loud-part RMS of the extracted clips, times their play volume): both loops reach about
  -22 dBFS each near death, like a heavy landing, but continuously. Hurt and jump are about -31,
  grabs -38 to -43, footsteps -48; Minecraft's own hit is -27.5 and a stone step about -52. The
  heartbeat starts below about 6 hearts and the mortal-wound layer below 2 hearts.
- **So the port matched the game.** It stands out because White Knuckle always has music and
  ambience under it.
- **Change (owner's choice):** a `low_health_volume` setting, 0 to 1 (clamped), **default 0.5**;
  1 = the game's level. The gametest now expects the game's values times the setting: 0.211 / 0.397 at
  2% health with the default. All checks passed with the pack (OpenGL).

## 2026-10-16: shader double check (owner's request)
- **Source:** since 0.4.0 only syntax changed in the four shader files; every input, output and
  uniform block matches the vanilla 26.3 shaders they pair with (`position_tex_color`,
  `screenquad`, `post/blit`, `globals.glsl`, `dynamictransforms.glsl`), and `DamageConfig` matches the
  buffer the mod writes. The only screen-position use (the hurt vignette) is symmetric, so Vulkan's
  flipped `gl_FragCoord` can't change it.
- **OpenGL vs Vulkan** (same pack test, 50 screenshot pairs): the post effect is identical to within
  1/255 on at most 0.009% of pixels; the hands use exactly the same colours on both, only some edge
  pixels of the scaled sprites land differently. Random blood and moving sprint frames differ as
  expected. All checks passed on both.
- **Vulkan validation layer** (Khronos 1.4.341, installed by the owner): the full test with the pack
  ran through it (`WKCLIMB_TEST_VK_VALIDATION=1`, which adds `--vulkanValidation
  --renderDebugLabels`) and it reported nothing. The loader's debug output (`VK_LOADER_DEBUG=layer`)
  confirmed the layer was in for the whole run.
- **Not done:** offline compiler warnings (no `glslc` installed).

## 2026-10-17: in-game settings menu (owner's choices)
- **Owner's choices:** open it from Mod Menu's Config button (Mod Menu 21.0.0, optional,
  `clientCompileOnly`, `suggests` in fabric.mod.json); player settings only (not
  `wk_keybinds_applied` or `wk_game_dir`); a Build... button to the asset setup screen.
- `ClimbSettingsScreen` uses vanilla `OptionsSubScreen` + `OptionInstance` (no config library).
  Sliders are whole-number `IntRange`s mapped to the settings: reach 0.5-2x and blocks per unit
  0.5-1.2 in 0.05 steps, FOV 30-110 with the lowest step "Minecraft's" (saved as 0), low-health
  volume 0-100%. Each tooltip gives the default (White Knuckle's value). Saved on close.
- The setup screen opened with an up-to-date pack now says so (`wkclimb.assets.offer.ok`) instead of
  "missing".
- Gametest: opens the screen, clicks Ledges only (OFF), clicks Build... (setup screen opens), closes,
  checks `ledges_only=false` in the file, restores it. All checks passed with the pack on OpenGL and Vulkan.
