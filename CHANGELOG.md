# Changelog

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
