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
