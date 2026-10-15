# White Knuckle Climbing

**Climb Minecraft like the White Knuckle climber.** Grab ledges with each hand, watch your grip run
out, swing from bars and chains, and move, sound and get hurt the way the game does. The
climbing, movement and health rules are rebuilt from White Knuckle's own code and data, and checked
against the game.

Minecraft Java Edition **1.21.9**, **Fabric**. Version **0.3.1 beta**: climbing only. Items, the
inventory and perks may come later.

> An unofficial fan mod, not made or endorsed by Dark Machine, White Knuckle's developers. The mod
> contains none of the game's files: if you own White Knuckle, a small tool builds its hands and
> sounds into a resource pack from your own copy.

## What you get
- **Two-handed grip climbing.** Left mouse is your left hand, right mouse your right. Hold a button
  while looking at a ledge (a block's top edge) to grab it; keep it held and the hand catches the
  next ledge you look at. Climb with W/S, shuffle with A/D, pull yourself up, jump between holds.
- **Grip per hand.** 10 seconds each while hanging, twice as long on two hands. Each hand turns red
  as its grip runs out (exactly like the game's hand shader), then lets go.
- **Bars, chains and rods** (chains, iron-bar posts, end and lightning rods, bamboo): grab anywhere
  and swing. Ladders, vines and buttons can be grabbed anywhere too.
- **Ice:** icy ledges slip out of your hand after 2 seconds, and icy floors are slippery.
- **White Knuckle's movement and camera:** walking, sprinting, crouching (with no "sneak" guard at
  ledges), jumping, air control, head-bob, sprint camera roll, and the game's lower camera height,
  so ledges sit in view where they do in White Knuckle.
- **White Knuckle's health:** no hunger, healing 5 seconds after the last hit, falls that only hurt
  past about 40 blocks, and the game's damage look (the screen washes out, greys and reddens as you
  get hurt, with blood and hit-direction glows).
- **With the asset pack:** the game's first-person hands, crosshair, hand icons, blood, and climbing,
  footstep, landing and voice sounds, the rushing wind of a long fall, and the heartbeat that fades in
  as you get close to death.

## Install
The download zip contains `README.txt` with the same steps in more detail, for **Windows** and
**Linux** (including Steam Deck).

1. **Fabric:** install [Fabric Loader](https://fabricmc.net/use/installer/) for 1.21.9 (or, in Prism
   Launcher, make a 1.21.9 instance and add Fabric under Edit > Version > Install Loader).
2. **Mods:** put [Fabric API](https://modrinth.com/mod/fabric-api) for 1.21.9 and
   `white-knuckle-climbing-0.3.1-beta.jar` in your `mods` folder:
   - Windows: `%APPDATA%\.minecraft\mods` (press Win+R and paste it)
   - Linux: `~/.minecraft/mods`
   - Prism: right-click the instance > Folder > `mods`
3. **White Knuckle's hands and sounds (optional, needs White Knuckle on Steam):** extract the
   download zip, then in its `asset-extractor` folder:
   - **Windows:** double-click `Get White Knuckle Assets (Windows).bat`. It installs Python 3.13 for
     you if needed (press Y, then run it again).
   - **Linux:** open a terminal in that folder and run `bash get-white-knuckle-assets.sh` (needs
     Python 3.10 to 3.14; on Ubuntu/Debian/Mint also `sudo apt install python3-venv`).

   It finds White Knuckle by itself (including Steam libraries on other drives) and makes
   `White Knuckle Assets.zip`. In Minecraft: Options > Resource Packs > Open Pack Folder, copy the zip
   in, and move the pack to the right-hand side. Please don't share the pack, since it's the game's own art and
   audio.

## Controls
| Key | Action |
|---|---|
| Left / right mouse | left / right hand: hold to grab, release to let go |
| W / S | climb up / down; W on top of a ledge pulls you onto it |
| A / D | shuffle sideways |
| Space | jump off the wall (costs grip) |
| Left Ctrl | crouch; while hanging, brace against the wall |
| Left Shift | sprint |
| R | switch climbing mode on / off |

- On first launch the mod sets White Knuckle's default keys: **Sprint = Left Shift, Crouch = Left
  Ctrl, Inventory = Tab** (the player list loses Tab). Change them back in Options > Controls >
  Key Binds whenever you like.
- While climbing mode is on, the mouse buttons are your hands: they don't break, place or attack.
  Press **R** to build or fight. The hotbar and hearts are hidden in climbing mode.

## Multiplayer
- Everyone needs the mod, and so does the host or server (LAN worlds work; for a server, install
  this mod and Fabric API on a Fabric 1.21.9 server).
- On a server **without** the mod, climbing switches itself off, because the server would otherwise
  kick you for flying.
- **For server owners:** the server trusts each player's game about when they're hanging (no fall
  damage or flying kick then). That's fine among friends, but on a public server a cheating client
  could misuse it.

## Settings
`config/wkclimb.properties` (created on first launch):

| Setting | Default | Meaning |
|---|---|---|
| `fov` | `90` | field of view while climbing mode is on (White Knuckle's default); `0` uses Minecraft's |
| `reach_multiplier` | `1.0` | how far you can reach (1.0 = White Knuckle's 2 blocks) |
| `ledges_only` | `true` | only top edges can be grabbed; `false` lets you grab any face |
| `handholds_only` | `false` | `true`: only blocks in the `#wkclimb:handholds` tag can be grabbed |
| `enabled_on_start` | `true` | climbing mode on when the game starts |
| `hide_vanilla_hand` | `true` | hide Minecraft's arm and held item while the climbing hands show |
| `blocks_per_unit` | `0.8` | the world scale: Minecraft blocks per White Knuckle unit |
| `exact_step_height` | `false` | `true`: White Knuckle's step height (0.18 blocks), so slabs and stairs must be jumped onto; `false`: Minecraft's 0.6 |
| `wk_keybinds_applied` | `false` → `true` | set back to `false` to apply White Knuckle's keys again |

Datapacks can change what is climbable through the block tags `wkclimb:handholds`,
`wkclimb:unclimbable`, `wkclimb:grab_anywhere`, `wkclimb:bars`, `wkclimb:icy_handholds`,
`wkclimb:slow_handholds` and `wkclimb:chain_sound`.

## Not in this beta
Items and tools in your hands, the White Knuckle inventory, perks and buffs, the grappling hook,
moving handholds, and White Knuckle's swimming (Minecraft's is used). The number keys still change
your held item even though the hotbar is hidden.

## Troubleshooting
- **Minecraft won't start:** check it's 1.21.9, with Fabric Loader 0.17 or newer and the Fabric API
  made for 1.21.9.
- **Plain drawn hands and Minecraft sounds:** the White Knuckle Assets pack isn't on (step 3).
- **The asset tool can't find White Knuckle:** run it with
  `--game "<the White Knuckle folder>"` (Steam: right-click White Knuckle > Manage > Browse local
  files).
- **Can't break or place blocks:** climbing mode is on, so press R.
- **Bug reports:** [open an issue](https://github.com/Fizz12215/White-Knuckle-Climbing/issues) with what you were doing, a screenshot or clip, and
  `.minecraft/logs/latest.log`.

## Credits
- **White Knuckle** is by Dark Machine. This mod is a fan tribute; please buy the game.
- Built on [Fabric](https://fabricmc.net/) and Fabric API. The asset tool uses
  [UnityPy](https://github.com/K0lb3/UnityPy), Pillow and soundfile.
- **AI disclosure:** this mod was written with Claude Code (Anthropic's Claude), working from the
  game's decompiled logic and data for reference only. No White Knuckle code or assets are
  included. Every mechanic was checked against the game or with automated in-game tests.
- License: MIT for the mod's own code (see `LICENSE`). White Knuckle's art and audio belong to Dark Machine and are not included.

Developers: [DEVELOPING.md](DEVELOPING.md) has the build, the code layout, where every number comes
from, and the test setup. [MODLOG.md](MODLOG.md) is the dated history of changes.
