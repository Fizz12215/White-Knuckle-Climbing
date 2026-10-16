# Changelog

## 0.5.0 beta: Minecraft 26.3
- **Now for Minecraft 26.3** (Fabric Loader 0.19.3 or newer, Fabric API for 26.3). **0.4.0 beta stays
  the version for Minecraft 1.21.9**; it gets no more updates.
- **Works with both of 26.3's graphics engines**, OpenGL and Vulkan (Options > Video Settings >
  Graphics API). The hands, blood and damage look are the same on both.
- **Climbing, movement and health are unchanged** from 0.4.0.
- **The low-health heartbeat is half as loud by default.** It plays at White Knuckle's own level, but
  without the game's music and ambience under it, it stood out in Minecraft. The new setting
  `low_health_volume` (0 to 1) sets it: `1` is the game's level, `0` turns it off.
- **Rebuild the White Knuckle pack once:** 26.3 uses a newer pack format. At the title screen the
  mod offers to rebuild it (two steps, about 15 seconds).
- The damage look now also shows together with Minecraft's own screen effects (for example a
  creeper's view while spectating one), instead of giving way to them. 26.3 can run several at once.
- On Windows, **Choose its folder** now opens SDL3's folder picker, since Minecraft 26.3 no longer
  ships the old one. It starts in Steam's game folder, as before.
- **Choose its folder now starts in Steam's game folder in a Flatpak launcher too** (Prism from
  Flathub, Steam Deck), instead of your home folder.

## 0.4.0 beta: White Knuckle's hands and sounds from a button in the game
- **White Knuckle's hands and sounds are built in the game:** at the title screen the mod finds your
  copy of White Knuckle and offers to build them. There's nothing to copy and no tool to run.
  - **Every step asks first.** Each step (Python folder, libraries, building the pack, turning it on)
    says exactly what it will do on your computer, with the real folders, sizes and how to undo it,
    and waits for Continue. You can Stop at any step.
  - **Choose its folder:** if White Knuckle isn't found, pick it in your system's own folder picker.
    In a Flatpak launcher (Prism from Flathub, Steam Deck) this lets the launcher read just that
    folder: no terminal command, no restart.
  - On Windows, the screen can install Python 3.13 for you (with winget), after explaining what that
    does.
  - When a mod update needs a newer pack, the screen offers to rebuild it.
  - The tool's Python libraries are now pinned to exact versions, so nothing new is downloaded by
    surprise.
  - New README sections: what the Build button does to your computer (and how to remove it), and
    what this kind of access means for your safety.
  - New settings: `asset_prompt`, `wk_game_dir`.
  - The manual tool is still in the download zip, in case the button can't run.
  - Tested on Windows 11 and on Linux, including a Flatpak launcher.
- **No more placeholder hands:** without the White Knuckle pack, the mod no longer draws its own
  simple hand icons beside the crosshair. You see Minecraft's crosshair and arm until the pack is on.
- **Switching the White Knuckle pack on or off takes effect at once.** The hands and blood used to
  catch up a few seconds later.
- **`config/wkclimb.properties` is easier to read:** settings stay in a fixed order, and folders are
  written as they are. On Windows, `wk_game_dir` can be typed with `\` or `/`.

## 0.3.1 beta: closer to the game
A full review of the climbing against White Knuckle's own code. To get the new sounds, run the
asset tool again.

Fixes, each making it behave like the game:
- **Diagonal climbing (W+A, W+D)** is full strength. White Knuckle doesn't scale diagonal input down;
  the mod did, which made diagonal climbing about 30% weaker.
- **Ground jumps** kick the hands once, not twice.
- **Crouching no longer stops you at ledges.** That was Minecraft's sneak guard; White Knuckle has none.
- **Shift alone** no longer makes you slide down walls faster. The game only counts it as sprinting
  with movement keys held.
- **The "wall in front of you" check** has the game's reach (it was 50% too long). It changes which
  way W/S pushes you near walls.
- **Bracing (crouch) while hanging** no longer shifts your body.
- **Walking off an edge** starts the fall from rest, as in the game.
- **Held hands** are drawn at the game's 95% size.
- **Snow:** ledges covered by a layer of snow can be grabbed again (one layer has no collision, but it
  used to hide the ledge). Thicker snow is held by its own edge.
- **Blood on the screen** no longer moves or shrinks when the field of view changes (sprinting, or
  your FOV setting): the game draws it with its own camera fixed at 90 degrees. The red hurt vignette
  is now laid over it, as in the game.
- **Flat floors:** you can no longer grab the floor where four blocks meet.
- **Inner corners:** you can no longer grab a concave corner. That covers the back edge of a block
  under an overhang, and the end of a ledge pressed against a taller block (a hand needs room).

New, from the game:
- **The game's camera height:** 1.44 blocks standing and 0.84 crouched (Minecraft's are 1.62 and
  1.27), so ledges sit in view where they do in White Knuckle.
- **Falling wind** that grows with fall speed, and **camera shake** on falls past the safe distance.
- **The near-death heartbeat and mortal-wound loops**, which fade in as health drops.
- **The fungus grab sound** on moss, fungus and froglight blocks.
- **New setting `exact_step_height`:** White Knuckle's 0.18-block step instead of Minecraft's 0.6, so
  slabs and stairs must be jumped onto. Off by default.

Also: without the asset pack, blood splats and hit indicators no longer pile up unseen.


## 0.3.0 beta, the climbing beta (first public release)
White Knuckle's climbing, movement and health in Minecraft 1.21.9 (Fabric). Items, the inventory
and perks are not in this version.

Changes since the private test builds:
- **Damage look matched to the game:** the screen washes out and loses colour while keeping its
  brightness, and the red edge vignette grows the way the game's does, on the hands too. It was
  measured against screenshots of White Knuckle and its shader.
- **Hand redness matched to the game:** each hand reddens in the game's steps (fully red once just
  over 62.5% of the grip is used), and its outline fades to red smoothly.
- **Servers without the mod:** climbing now switches itself off there, instead of getting you
  kicked for flying.
- **Dedicated servers:** tested; the mod runs on a Fabric server and hanging doesn't get players
  kicked.
- **Asset tool:**
  - about 17 times faster (about 15 seconds);
  - finds White Knuckle in any Steam library, including other drives;
  - fixed a crash on Windows;
  - grab and let-go sounds are now decoded exactly;
  - clearer messages when Python is missing.
- New step-by-step install guide for Windows and Linux.
