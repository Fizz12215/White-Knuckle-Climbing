# Changelog

## 0.3.0 beta, the climbing beta (first public release)
White Knuckle's climbing, movement and health in Minecraft 1.21.9 (Fabric). Items, the inventory
and perks are not in this version.

Changes since the private test builds:
- **Damage look matched to the game:** the screen washes out and loses colour while keeping its
  brightness, and the red edge vignette grows the way the game's does, on the hands too. It was
  measured against screenshots of White Knuckle and its shader.
- **Hand redness matched to the game:** each hand reddens in the game's steps (fully red at about
  69% grip used), and its outline fades to red smoothly.
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
