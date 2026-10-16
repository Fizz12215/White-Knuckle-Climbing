#!/usr/bin/env bash
# Builds the White Knuckle hands, sounds and effects pack from YOUR copy of White Knuckle (Linux).
# Extra arguments are passed to the extractor, e.g. --game "/path/to/White Knuckle"
set -e
cd "$(dirname "$0")"

# The extractor's libraries ship ready-made for Python 3.10 - 3.14 (not yet 3.15).
PY=""
for v in 3.13 3.12 3.14 3.11 3.10; do
  if command -v "python$v" >/dev/null 2>&1; then PY="python$v"; break; fi
done
if [ -z "$PY" ] && command -v python3 >/dev/null 2>&1; then
  case "$(python3 -c 'import sys; print("%d.%d" % sys.version_info[:2])')" in
    3.10|3.11|3.12|3.13|3.14) PY=python3 ;;
  esac
fi
if [ -z "$PY" ]; then
  echo "This needs Python 3.10 to 3.14 (3.15 is too new for the extractor's libraries)."
  echo "Install it with your package manager, for example:"
  echo "  Ubuntu / Debian / Mint:  sudo apt install python3 python3-venv"
  echo "  Fedora:                  sudo dnf install python3"
  echo "  Arch / SteamOS:          sudo pacman -S python"
  exit 1
fi

VENV="${XDG_DATA_HOME:-$HOME/.local/share}/wkclimb-extractor"
VER="$("$PY" -c 'import sys; print("%d.%d" % sys.version_info[:2])')"
MARK="$VENV/setup-ok-$VER"
if [ ! -f "$MARK" ]; then
  rm -rf "$VENV"
  echo "First run: setting up the extractor with $PY (a minute or two)..."
  if ! "$PY" -m venv "$VENV"; then
    rm -rf "$VENV"
    echo
    echo "Python can't create its private library folder (the 'venv' module is missing)."
    echo "On Ubuntu / Debian / Mint run:  sudo apt install python3-venv   then run this script again."
    exit 1
  fi
  # exact versions from requirements.txt, ready-made only (never compiles anything)
  "$VENV/bin/python" -m pip install --quiet --only-binary=:all: -r requirements.txt
  touch "$MARK"
fi
echo "Extracting (about 15 seconds)..."
rm -f "White Knuckle Assets.zip"
"$VENV/bin/python" extract_wk_assets.py --out "$PWD/White Knuckle Assets" --zip "$@"
if [ ! -f "White Knuckle Assets.zip" ]; then
  echo "The pack was not created. Please report it at https://github.com/Fizz12215/White-Knuckle-Climbing/issues with everything this window printed."
  exit 1
fi
echo
echo "Done! \"White Knuckle Assets.zip\" is in this folder:"
echo "  $PWD"
echo 'In Minecraft: Options > Resource Packs > Open Pack Folder, copy the zip there, then move it to the right-hand side.'
