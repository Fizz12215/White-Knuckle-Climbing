WHITE KNUCKLE CLIMBING  -  0.3.0 beta  -  for Minecraft Java Edition 1.21.9 (Fabric)
=====================================================================================
Climb Minecraft like the White Knuckle climber: grab ledges with each hand, watch your grip run
out, swing from bars and chains, and move, sound and get hurt the way the game does.

This beta is the climbing part only. Items, the inventory and perks may come in later versions.

An unofficial fan mod. It is not made or endorsed by Dark Machine (White Knuckle's developers),
and it contains none of the game's files.


WHAT'S IN THIS ZIP
------------------
  mods/white-knuckle-climbing-0.3.0-beta.jar   the mod
  asset-extractor/                             optional: builds White Knuckle's hands and sounds
                                               from YOUR copy of the game (step 3)
  README.txt                                   this guide


WHAT YOU NEED
-------------
  - Minecraft Java Edition 1.21.9
  - Fabric Loader for 1.21.9           https://fabricmc.net/use/installer/
  - Fabric API for 1.21.9              https://modrinth.com/mod/fabric-api
                                       (on its Versions tab, pick one marked 1.21.9)
  - Optional but recommended: White Knuckle on Steam. Without it the mod still works, with simple
    drawn hands and Minecraft's sounds.


=====================================================================================
STEP 1 - INSTALL FABRIC (skip if you already play Fabric 1.21.9)
=====================================================================================
Official Minecraft launcher:
  1. Download the Fabric installer from https://fabricmc.net/use/installer/
       Windows: the .EXE.   Linux: the universal .JAR. Run it with:  java -jar fabric-installer-*.jar
  2. Pick Minecraft version 1.21.9 and click Install.
  3. Open the Minecraft launcher, pick the "fabric-loader-1.21.9" profile, press Play once and
     quit at the title screen. That creates the mods folder.

Prism Launcher / MultiMC:
  1. Add Instance > Minecraft 1.21.9 > OK.
  2. Right-click the instance > Edit > Version > Install Loader > Fabric > OK.


=====================================================================================
STEP 2 - PUT THE MODS IN
=====================================================================================
Copy TWO files into your mods folder:
  - the Fabric API .jar you downloaded
  - mods/white-knuckle-climbing-0.3.0-beta.jar from this zip

Where the mods folder is:
  Windows, official launcher:  press Win+R, type  %APPDATA%\.minecraft\mods  and press Enter
                               (create the "mods" folder if it isn't there)
  Linux, official launcher:    ~/.minecraft/mods   (a hidden folder; Ctrl+H shows hidden files)
  Prism Launcher:              right-click the instance > Folder, then open "mods"

Start Minecraft. If it reaches the title screen, the mod is installed.


=====================================================================================
STEP 3 - WHITE KNUCKLE'S HANDS AND SOUNDS (optional, needs White Knuckle on Steam)
=====================================================================================
A small tool builds a resource pack, "White Knuckle Assets.zip", from your own copy of the game.
It finds White Knuckle by itself, including Steam libraries on other drives. The first run needs
an internet connection to download its libraries; after that it takes about 15 seconds.

--- WINDOWS ---
  1. Extract this zip first: right-click it > Extract All. (Running the tool from inside the zip
     won't work.)
  2. Open the extracted "asset-extractor" folder and double-click
         Get White Knuckle Assets (Windows).bat
     If Windows says "Windows protected your PC", click More info > Run anyway. (It's a plain
     text script; open it in Notepad to read what it does.)
  3. It needs Python 3.10 - 3.14. If you don't have it, it offers to install Python 3.13: press Y,
     wait for the install, close the window and double-click the .bat again.
  4. The first run sets things up (a minute or two), then extracts. When it says "Done!",
     "White Knuckle Assets.zip" is in the asset-extractor folder.

--- LINUX (including Steam Deck in Desktop Mode) ---
  1. Extract this zip.
  2. Open a terminal in the "asset-extractor" folder (in most file managers: right-click >
     Open Terminal Here; on Steam Deck, Konsole) and run:
         bash get-white-knuckle-assets.sh
  3. It needs Python 3.10 - 3.14, which most distributions already have. If it asks for it, or
     says the "venv" module is missing, it prints the command to install it, for example on
     Ubuntu / Debian / Mint:   sudo apt install python3-venv
  4. When it says "Done!", "White Knuckle Assets.zip" is in the asset-extractor folder.

--- THEN, IN MINECRAFT (both systems) ---
  Options > Resource Packs > Open Pack Folder. Copy "White Knuckle Assets.zip" into that folder,
  go back to Minecraft, click the pack on the left to move it to the right ("Selected"), then Done.

  If the tool can't find White Knuckle, run it again with the game folder (in Steam: right-click
  White Knuckle > Manage > Browse local files shows it):
      Windows (Command Prompt in the asset-extractor folder):
          "Get White Knuckle Assets (Windows).bat" --game "D:\SteamLibrary\steamapps\common\White Knuckle"
      Linux:
          bash get-white-knuckle-assets.sh --game "/path/to/White Knuckle"

  Please don't share the pack: it's the game's own art and audio, so everyone makes their own.


=====================================================================================
PLAYING
=====================================================================================
  Left mouse / right mouse   your left / right hand. Hold a button while looking at a ledge
                             (the top edge of a block) to grab it; let go of the button to drop.
                             Keep a button held and the hand grabs the next ledge you look at.
  W / S                      climb up / down the wall; W on top of a ledge pulls you onto it
  A / D                      shuffle sideways
  Space                      jump off the wall (costs grip)
  Left Ctrl                  brace against the wall (crouch)
  Left Shift                 sprint
  R                          switch climbing mode on / off

  - Each hand has 10 seconds of grip while hanging; hanging by both hands makes it last twice as
    long. Each hand turns red as its grip runs out and lets go when it's empty. Grip refills
    when you stand on the ground.
  - Chains, iron-bar posts, end rods, lightning rods and bamboo: grab anywhere and swing (look
    where you want to go and press W). Ladders, vines and buttons can be grabbed anywhere.
  - Ice: your hand slides and slips off after 2 seconds. Icy floors are slippery.
  - White Knuckle's health rules: no hunger; you heal 5 s after the last hit; falls only hurt past
    about 40 blocks; the screen washes out, greys and reddens as you get hurt.
  - While climbing mode is on, the mouse buttons are your hands, so they don't break, place or
    attack. Press R to switch climbing off when you want to build or fight. The hotbar and hearts
    are hidden in climbing mode (the number keys still change your held item).

  The first time you start, the mod switches three keys to White Knuckle's defaults:
      Sprint = Left Shift,  Crouch = Left Ctrl,  Inventory = Tab  (and the player list loses Tab).
  You can change them back in Options > Controls > Key Binds.

  Settings file: .minecraft/config/wkclimb.properties (field of view, reach, ledges only, and
  more; see the mod's page for what each one does).


=====================================================================================
PLAYING TOGETHER
=====================================================================================
  - Everyone needs the mod. The host or server needs it too: open a single-player world to LAN,
    or put this mod and Fabric API on a Fabric 1.21.9 server.
  - On a server without the mod, climbing switches itself off (otherwise the server would kick
    you for flying).
  - For server owners: the server trusts each player's client about when they're hanging (no fall
    damage or flying kick then). Fine among friends; on a public server, a cheating client could
    misuse that.


=====================================================================================
IF SOMETHING GOES WRONG
=====================================================================================
  Minecraft crashes or won't start
      Check that it's Minecraft 1.21.9, Fabric Loader 0.17 or newer, and the Fabric API made for
      1.21.9.
  The hands are plain drawn shapes and the sounds are Minecraft's
      The White Knuckle Assets pack isn't on. Do step 3, and check it's on the right-hand
      ("Selected") side in Options > Resource Packs.
  The asset tool says Python is missing or too new
      It needs Python 3.10 - 3.14. Having Python 3.15 as well is fine; the tool picks the right one.
  I can't break or place blocks
      Climbing mode is on: press R.
  Logs (useful for bug reports)
      .minecraft/logs/latest.log  (Prism: the instance's Logs tab)

  Uninstall: delete the white-knuckle-climbing jar from the mods folder (and the pack from the
  resourcepacks folder if you like).

  Found a bug? Report it at https://github.com/Fizz12215/White-Knuckle-Climbing/issues
  with what you were doing, a screenshot or clip, and latest.log.
