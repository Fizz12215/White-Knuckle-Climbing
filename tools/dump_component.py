#!/usr/bin/env python3
"""
Research helper: dump White Knuckle components (MonoBehaviours) as JSON, to look up the values the
mod ports (player settings, handhold presets, UI layout...). Reads your own install; prints to stdout.

    python tools/dump_component.py ENT_Player                       # first ENT_Player in level3
    python tools/dump_component.py CL_Handhold_Icy --file sharedassets1.assets --all --keys maxHoldTime,slipSpeed
    python tools/dump_component.py UI_CanvasScaler --game "D:/SteamLibrary/steamapps/common/White Knuckle"

Needs the same packages as the extractor (pip install -r tools/requirements.txt).
Workarounds built in (see DEVELOPING.md):
  * MonoBehaviour type trees are stripped from the build; they are regenerated from Managed/*.dll.
  * The generator emits a stray ManagedReferencesRegistry ("references") before subclass fields of
    classes with [SerializeReference] lists (e.g. CL_Handhold subclasses); the first one is dropped.
  * UnityEngine.UI types can't be generated; use --image to read an Image's colour/sprite/type raw.
"""
import argparse
import json
import struct
import sys
from pathlib import Path

try:
    import UnityPy
    from UnityPy.helpers.TypeTreeGenerator import TypeTreeGenerator
except ImportError:
    sys.exit("pip install -r tools/requirements.txt")

sys.path.insert(0, str(Path(__file__).parent))
from extract_wk_assets import find_game_dir  # noqa: E402


def nodes_for(gen, cls):
    nodes = gen.get_nodes_up("Assembly-CSharp", cls)
    refs = [i for i, ch in enumerate(nodes.m_Children) if ch.m_Name == "references"]
    if len(refs) > 1:
        del nodes.m_Children[refs[0]]
    return nodes


def read_image(obj):
    """UnityEngine.UI.Image fields after the MonoBehaviour header and Graphic fields."""
    raw = obj.get_raw_data()
    off = 0

    def rd(fmt):
        nonlocal off
        v = struct.unpack_from(fmt, raw, off)
        off += struct.calcsize(fmt)
        return v

    def align():
        nonlocal off
        off = (off + 3) & ~3

    rd("<iq"); rd("<B"); align(); rd("<iq")
    n = rd("<i")[0]; off += n; align()
    material = rd("<iq"); color = rd("<4f"); rd("<B"); align(); rd("<4f"); rd("<B"); align()
    if rd("<i")[0] != 0:
        return {"material": material, "color": color, "note": "has persistent calls; sprite not parsed"}
    sprite = rd("<iq"); img_type = rd("<i")[0]; preserve = rd("<B")[0]
    return {"material": material, "color": color, "sprite": sprite, "type": img_type, "preserveAspect": preserve}


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("cls", help="MonoBehaviour class name, e.g. ENT_Player, CL_Handhold, UI_CrosshairController")
    ap.add_argument("--game", help="White Knuckle install folder")
    ap.add_argument("--file", default="level3", help="serialized file inside White Knuckle_Data (default level3)")
    ap.add_argument("--all", action="store_true", help="dump every instance (default: the first)")
    ap.add_argument("--keys", help="comma-separated fields to keep (default: all)")
    ap.add_argument("--image", type=int, metavar="PATH_ID", help="instead, read a UI Image component raw")
    args = ap.parse_args()

    data_dir = find_game_dir(args.game)
    env = UnityPy.load(str(data_dir / args.file))
    sf = list(env.files.values())[0]
    objs = {o.path_id: o for o in env.objects}

    if args.image is not None:
        print(json.dumps(read_image(objs[args.image]), indent=1))
        return

    gen = TypeTreeGenerator(sf.unity_version)
    gen.load_local_dll_folder(str(data_dir / "Managed"))
    nodes = nodes_for(gen, args.cls)
    keys = args.keys.split(",") if args.keys else None
    found = 0
    for obj in objs.values():
        if obj.type.name != "MonoBehaviour":
            continue
        try:
            if obj.read(check_read=False).m_Script.read().m_ClassName != args.cls:
                continue
            tree = obj.read_typetree(nodes)
        except Exception:
            continue
        if keys:
            tree = {k: tree.get(k) for k in keys}
        print(json.dumps({"path_id": obj.path_id, **tree}, default=str, indent=1))
        found += 1
        if not args.all:
            break
    if not found:
        sys.exit(f"no {args.cls} found in {args.file}")


if __name__ == "__main__":
    main()
