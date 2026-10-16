WHITE KNUCKLE CLIMBING  -  0.5.0 beta  -  for Minecraft Java Edition 26.3 (Fabric)
=====================================================================================
(On Minecraft 1.21.9, use 0.4.0 beta instead: it's the last version for 1.21.9.)
Climb Minecraft like the White Knuckle climber: grab ledges with each hand, watch your grip run
out, swing from bars and chains, and move, sound and get hurt the way the game does.

This beta is the climbing part only. Items, the inventory and perks may come in later versions.

An unofficial fan mod. It is not made or endorsed by Dark Machine (White Knuckle's developers),
and it contains none of the game's files.


WHAT'S IN THIS ZIP
------------------
  mods/white-knuckle-climbing-0.5.0-beta.jar   the mod
  asset-extractor/                             only if the in-game Build button can't run: builds
                                               White Knuckle's hands and sounds by hand (step 3)
  README.txt                                   this guide


WHAT YOU NEED
-------------
  - Minecraft Java Edition 26.3
  - Fabric Loader for 26.3             https://fabricmc.net/use/installer/
  - Fabric API for 26.3                https://modrinth.com/mod/fabric-api
                                       (on its Versions tab, pick one marked 26.3)
  - Optional but recommended: White Knuckle on Steam. Without it the mod still works, with
    Minecraft's own arm, crosshair and sounds.


=====================================================================================
STEP 1 - INSTALL FABRIC (skip if you already play Fabric 26.3)
=====================================================================================
Official Minecraft launcher:
  1. Download the Fabric installer from https://fabricmc.net/use/installer/
       Windows: the .EXE.   Linux: the universal .JAR. Run it with:  java -jar fabric-installer-*.jar
  2. Pick Minecraft version 26.3 and click Install.
  3. Open the Minecraft launcher, pick the "fabric-loader-26.3" profile, press Play once and
     quit at the title screen. That creates the mods folder.

Prism Launcher / MultiMC:
  1. Add Instance > Minecraft 26.3 > OK.
  2. Right-click the instance > Edit > Version > Install Loader > Fabric > OK.


=====================================================================================
STEP 2 - PUT THE MODS IN
=====================================================================================
Copy TWO files into your mods folder:
  - the Fabric API .jar you downloaded
  - mods/white-knuckle-climbing-0.5.0-beta.jar from this zip

Where the mods folder is:
  Windows, official launcher:  press Win+R, type  %APPDATA%\.minecraft\mods  and press Enter
                               (create the "mods" folder if it isn't there)
  Linux, official launcher:    ~/.minecraft/mods   (a hidden folder; Ctrl+H shows hidden files)
  Prism Launcher:              right-click the instance > Folder, then open "mods"

Start Minecraft. If it reaches the title screen, the mod is installed.


=====================================================================================
STEP 3 - WHITE KNUCKLE'S HANDS AND SOUNDS (optional, needs White Knuckle on Steam)
=====================================================================================
The mod builds a resource pack, "White Knuckle Assets", from YOUR copy of the game.

Start Minecraft. At the title screen the mod says it found White Knuckle and offers to build its
hands and sounds. Press Start. It then goes through up to four steps. EACH STEP FIRST SAYS EXACTLY
WHAT IT WILL DO ON YOUR COMPUTER, WHICH FOLDERS IT USES AND HOW TO UNDO IT, AND WAITS FOR YOU TO PRESS
CONTINUE. You can press Stop at any step.
  1. Make a private Python folder (first time only).
  2. Download the Python libraries the tool needs from pypi.org (first time only; about 35 MB on
     Windows, 45 MB on Linux; about 130 MB and 185 MB on disk; it can take a few minutes).
  3. Build the pack from your copy of the game (about 15 seconds; it only reads White Knuckle's
     files and never changes them).
  4. Turn the pack on. No copying, no menus.
When a later mod update needs a newer pack, the screen offers to rebuild it (two steps).

It needs Python 3.10 - 3.14:
  Windows: if Python isn't installed, the screen offers to install Python 3.13 and explains what
           that does first. It opens a window that uses winget (Windows' own app installer). When
           that window says "Press any key to continue", press a key, then press Try again in
           Minecraft (no restart needed).
  Linux:   most distributions already have it. On Ubuntu / Debian / Mint you may also need
               sudo apt install python3-venv
           (the screen tells you if so).

If White Knuckle isn't found (it's on another drive, or your launcher is a Flatpak, such as Prism
Launcher from Flathub or most Steam Deck setups, which can't see your Steam folder):
  Press "Choose its folder..." and pick the folder called White Knuckle in your system's folder
  picker. (In Steam: right-click White Knuckle > Manage > Browse local files shows where it is.)
  In a Flatpak this lets the launcher read just that one folder: no commands, no restart.

Please read the two sections below before you start, and please don't share the pack: it's the game's
own art and audio, so everyone makes their own.

--- WHAT THE BUILD BUTTON DOES TO YOUR COMPUTER ---
  A private Python folder with the tool's libraries (about 130 MB on Windows, 185 MB on Linux):
      Windows: %LOCALAPPDATA%\wkclimb-extractor
      Linux:   ~/.local/share/wkclimb-extractor
      Flatpak launcher: ~/.var/app/<launcher id>/data/wkclimb-extractor
      To remove it: delete the folder.
  The pack (about 3 MB): .minecraft/resourcepacks/White Knuckle Assets
      To remove it: delete the folder.
  Turns the pack on in Minecraft's list (.minecraft/options.txt).
      To undo: Options > Resource Packs, move it back to the left.
  If you chose White Knuckle's folder: remembers it in .minecraft/config/wkclimb.properties
  (wk_game_dir).
  A log of what it ran: .minecraft/logs/wkclimb-assets.log
  Only if you press Install Python (Windows): Python 3.13 from python.org (about 100 MB).
      To remove it: Settings > Apps > Installed apps > Python 3.13 > Uninstall.
  Only in a Flatpak, if you choose a folder: your launcher can read that folder.
      To undo: flatpak document-unexport --doc-id=<id>   (the build step shows the exact command;
      "flatpak documents <launcher id>" lists the folders it can see).
  It downloads only from pypi.org (and python.org, if you install Python). It only reads White
  Knuckle's folder and never changes it.

--- SAFETY: WHAT THIS ACCESS MEANS ---
  - It runs downloaded code with your permissions. The tool is the mod's own Python script plus
    libraries from pypi.org (UnityPy, Pillow, numpy and a few more). Like any program you install,
    they can do anything your user account can do. If one of those libraries were ever taken over
    by an attacker, its code would run on your computer too. To make that less likely, the mod fixes
    the exact version of every library: PyPI never lets anyone change the files of an existing
    version, so new code only arrives when a mod update changes that list.
  - Only get the mod from its official page:
        https://github.com/Fizz12215/White-Knuckle-Climbing/releases
    A modified copy could show the same screens and run something else. Be wary of any other mod
    showing a screen like this one.
  - Minecraft mods already have full access. Outside a Flatpak, every mod you install runs with your
    user's permissions, so the Build button can't do anything a mod couldn't already do. What it adds
    is downloading and running more code, which is why every step asks first.
  - Folder access in a Flatpak. A Flatpak launcher normally can't see your files, and that protects
    you from a bad mod in any of its instances. Every folder you let it see becomes readable by the
    launcher and every mod you run in it. Don't give it your whole Steam folder: that folder also
    holds Steam's login and settings files (config/), which a bad mod could read. Choosing a folder
    gives access to that folder only. You can take the access away again (see above).
  - Installing Python (Windows) adds a program to your PC and agrees to Python's license for you.
    Windows asks for your permission if it needs it. You can uninstall it like any other app.
  - What it never does: it never needs administrator rights (apart from Windows' own prompt when
    installing Python), never changes White Knuckle's files, never uploads anything, never uses the
    internet except pypi.org (and python.org for Python), and never runs anything without a Continue
    from you.

--- BY HAND (only if the Build button can't run) ---
The asset-extractor folder in this zip does the same job outside Minecraft and makes
"White Knuckle Assets.zip". It also needs Python 3.10 - 3.14 and an internet connection the first
time.

  WINDOWS
  1. Extract this zip first: right-click it > Extract All. (Running the tool from inside the zip
     won't work.)
  2. Open the extracted "asset-extractor" folder and double-click
         Get White Knuckle Assets (Windows).bat
     If Windows says "Windows protected your PC", click More info > Run anyway. (It's a plain
     text script; open it in Notepad to read what it does.)
  3. If Python is missing, it offers to install Python 3.13: press Y, wait for the install, close the
     window and double-click the .bat again.
  4. When it says "Done!", "White Knuckle Assets.zip" is in the asset-extractor folder.

  LINUX (including Steam Deck in Desktop Mode)
  1. Extract this zip.
  2. Open a terminal in the "asset-extractor" folder (in most file managers: right-click >
     Open Terminal Here; on Steam Deck, Konsole) and run:
         bash get-white-knuckle-assets.sh
  3. When it says "Done!", "White Knuckle Assets.zip" is in the asset-extractor folder.

  THEN, IN MINECRAFT
  Options > Resource Packs > Open Pack Folder. Copy "White Knuckle Assets.zip" into that folder,
  go back to Minecraft, click the pack on the left to move it to the right ("Selected"), then Done.

  If the tool can't find White Knuckle, run it again with the game folder:
      Windows (Command Prompt in the asset-extractor folder):
          "Get White Knuckle Assets (Windows).bat" --game "D:\SteamLibrary\steamapps\common\White Knuckle"
      Linux:
          bash get-white-knuckle-assets.sh --game "/path/to/White Knuckle"


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

  Settings file: .minecraft/config/wkclimb.properties (field of view, reach, ledges only, White
  Knuckle's exact step height, and more; see the mod's page for what each one does).

  Updating from an older version? Start Minecraft and press Start when the mod offers to rebuild
  the pack, then Continue at each step. Packs from 0.4.0 or older always need this once, because
  Minecraft 26.3 uses a newer pack format.


=====================================================================================
PLAYING TOGETHER
=====================================================================================
  - Everyone needs the mod. The host or server needs it too: open a single-player world to LAN,
    or put this mod and Fabric API on a Fabric 26.3 server.
  - On a server without the mod, climbing switches itself off (otherwise the server would kick
    you for flying).
  - For server owners: the server trusts each player's client about when they're hanging (no fall
    damage or flying kick then). Fine among friends; on a public server, a cheating client could
    misuse that.


=====================================================================================
IF SOMETHING GOES WRONG
=====================================================================================
  Minecraft crashes or won't start
      Check that it's Minecraft 26.3, Fabric Loader 0.19.3 or newer, and the Fabric API made for
      26.3.
  I see Minecraft's arm, crosshair and sounds instead of White Knuckle's
      The White Knuckle Assets pack isn't on. Do step 3, and check it's on the right-hand
      ("Selected") side in Options > Resource Packs. If you clicked "Don't ask again", set
      asset_prompt=true in .minecraft/config/wkclimb.properties and restart Minecraft.
  It says Python is missing or too new
      It needs Python 3.10 - 3.14. Having Python 3.15 as well is fine; it picks the right one.
  Building the pack fails
      The screen shows the last lines; the full log is .minecraft/logs/wkclimb-assets.log.
      Please attach that file to a bug report.
  I can't break or place blocks
      Climbing mode is on: press R.
  Logs (useful for bug reports)
      .minecraft/logs/latest.log  (Prism: the instance's Logs tab)

  Uninstall: delete the white-knuckle-climbing jar from the mods folder (and the pack from the
  resourcepacks folder if you like).

  Found a bug? Report it at https://github.com/Fizz12215/White-Knuckle-Climbing/issues
  with what you were doing, a screenshot or clip, and latest.log.
