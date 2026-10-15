#!/usr/bin/env python3
"""
Build a private Minecraft resource pack with White Knuckle's own hand sprites and climbing sounds,
read from YOUR White Knuckle install. Nothing from the game ships with the mod; run this once.

    pip install -r tools/requirements.txt
    python3 tools/extract_wk_assets.py --instance ~/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances/1.21.9 --enable

What it reads (all found through the ENT_Player component in the level3 scene):
  * hands[0].handPoses      -> the five first-person hand sprites (normal/open/grab/crouch/sprint)
  * interactionSprites      -> the crosshair hand icons (hand-open / hand-grabbed)
  * clipHandler (UT_AudioClipHandler) -> the climbing, fatigue and jump sound sets
  * windAudio / damageAudio / dyingAudio (AudioSources) -> the falling wind and the near-death loops
"""
import argparse
import struct
import io
import json
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

try:
    import UnityPy
    from UnityPy.helpers.TypeTreeGenerator import TypeTreeGenerator
    from PIL import Image
except ImportError:
    sys.exit("Missing dependencies: pip install UnityPy TypeTreeGeneratorAPI")

# Where Steam itself usually lives; every Steam library it knows about is then read from
# steamapps/libraryfolders.vdf, so installs on other drives are found too.
STEAM_ROOTS = [
    "~/.local/share/Steam",
    "~/.steam/steam",
    "~/.steam/root",
    "~/.var/app/com.valvesoftware.Steam/.local/share/Steam",  # Flatpak Steam
    "~/snap/steam/common/.local/share/Steam",                  # Snap Steam
    "C:/Program Files (x86)/Steam",
    "C:/Program Files/Steam",
]
PACK_NAME = "White Knuckle Assets"
PACK_FORMAT = 69  # Minecraft 1.21.9

# (WK group, WK set) -> Minecraft sound event path under the wkclimb namespace
SOUND_SETS = {
    ("climbing", "handhold"): "climbing.handhold",
    ("climbing", "handhold-chain"): "climbing.handhold_chain",
    ("climbing", "handhold-metafungus"): "climbing.handhold_metafungus",
    ("climbing", "drop"): "climbing.drop",
    ("fatigue", "drop"): "fatigue.drop",
    ("fatigue", "level"): "fatigue.level",
    ("movement", "jump"): "movement.jump",
    ("movement", "mantle"): "movement.mantle",
    ("movement", "land-soft"): "movement.land_soft",
    ("movement", "land-medium"): "movement.land_medium",
    ("movement", "land-heavy"): "movement.land_heavy",
    ("player", "hurt"): "player.hurt",
    ("player", "hurt-falling"): "player.hurt_falling",
    ("player", "hurt-heat"): "player.hurt_heat",
    ("player", "death"): "player.death",
}
# Looping AudioSources on ENT_Player: field -> sound event. The game sets their volume itself every frame
# (wind: fall speed; heartbeat / mortal wound: damageFXCurve / dyingAudioCurve of health), so the mod does too.
LOOP_SOURCES = {
    "windAudio": "movement.wind",
    "damageAudio": "player.heartbeat",
    "dyingAudio": "player.mortal_wound",
}
HAND_POSES = ["normal", "open", "grab", "crouch", "sprint"]
UI_ICONS = {"hand-open": "icon_open", "hand-grabbed": "icon_grabbed"}


IMA_STEPS = [7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45, 50, 55, 60, 66, 73, 80,
             88, 97, 107, 118, 130, 143, 157, 173, 190, 209, 230, 253, 279, 307, 337, 371, 408, 449, 494, 544, 598,
             658, 724, 796, 876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066, 2272, 2499, 2749, 3024, 3327,
             3660, 4026, 4428, 4871, 5358, 5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899, 15289,
             16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767]
IMA_INDEX = [-1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8]


def fsb5_ima_to_wav(fsb, channels, frequency, total_samples):
    """FSB5 codec 7 (Xbox-style IMA ADPCM), decoded exactly as FMOD does (verified bit-identical
    against FMOD's output): per 0x24-byte frame and channel, a 4-byte header (predictor, step index)
    whose predictor is the frame's first sample, then 63 samples from the first 63 of the 64 data
    nibbles (4-byte interleaved between channels, low nibble first); the last nibble is unused.
    UnityPy's FMOD only decodes these in a fresh process, so decoding them here is far faster."""
    import struct
    import wave
    if fsb[:4] != b"FSB5":
        raise ValueError("not FSB5")
    version, _, shs, nts, _, mode = struct.unpack_from("<IIIIII", fsb, 4)
    if mode != 7:
        raise ValueError(f"FSB5 codec {mode} not handled")
    data = fsb[(0x3C if version == 1 else 0x40) + shs + nts:]
    block = 0x24 * channels
    out = [[] for _ in range(channels)]
    for b in range(len(data) // block):
        frame = data[b * block:(b + 1) * block]
        body = frame[4 * channels:]
        for c in range(channels):
            pred, step_idx = struct.unpack_from("<hB", frame, 4 * c)
            sample, idx = pred, min(max(step_idx, 0), 88)
            dst = out[c]
            dst.append(sample)
            nibbles = []
            for chunk in range(len(body) // (4 * channels)):
                for byte in body[(chunk * channels + c) * 4:(chunk * channels + c) * 4 + 4]:
                    nibbles.append(byte & 0x0F)
                    nibbles.append(byte >> 4)
            for nib in nibbles[:-1]:
                step = IMA_STEPS[idx]
                diff = step >> 3
                if nib & 1: diff += step >> 2
                if nib & 2: diff += step >> 1
                if nib & 4: diff += step
                if nib & 8: diff = -diff
                sample = max(-32768, min(32767, sample + diff))
                idx = max(0, min(88, idx + IMA_INDEX[nib]))
                dst.append(sample)
    n = min(total_samples, min(len(ch) for ch in out))
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        w.setnchannels(channels)
        w.setsampwidth(2)
        w.setframerate(frequency)
        w.writeframes(b"".join(struct.pack("<" + "h" * channels, *(out[c][i] for c in range(channels))) for i in range(n)))
    return buf.getvalue()


def steam_roots():
    roots = [Path(os.path.expanduser(r)) for r in STEAM_ROOTS]
    if sys.platform == "win32":
        try:
            import winreg
            with winreg.OpenKey(winreg.HKEY_CURRENT_USER, r"Software\Valve\Steam") as key:
                roots.insert(0, Path(winreg.QueryValueEx(key, "SteamPath")[0]))
        except OSError:
            pass
    return roots


def steam_libraries():
    """Every Steam library folder: the Steam folders above plus the paths in libraryfolders.vdf."""
    libraries = []
    for root in steam_roots():
        if not root.is_dir():
            continue
        libraries.append(root)
        vdf = root / "steamapps" / "libraryfolders.vdf"
        try:
            text = vdf.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        for line in text.splitlines():
            parts = line.strip().split('"')
            # lines look like:  "path"    "D:\\SteamLibrary"
            if len(parts) >= 4 and parts[1] == "path":
                libraries.append(Path(parts[3].replace("\\\\", "\\")))
    unique = []
    for lib in libraries:
        if lib not in unique:
            unique.append(lib)
    return unique


def find_game_dir(arg):
    if arg:
        candidates = [Path(os.path.expanduser(arg))]
    else:
        candidates = [lib / "steamapps" / "common" / "White Knuckle" for lib in steam_libraries()]
    for p in candidates:
        if (p / "White Knuckle_Data" / "level3").exists():
            return p / "White Knuckle_Data"
        if (p / "level3").exists() and p.name == "White Knuckle_Data":  # the data folder itself was given
            return p
    looked = "\n  ".join(str(c) for c in candidates) or "(no Steam folder found)"
    sys.exit("Couldn't find White Knuckle. Looked in:\n  " + looked +
             "\nRun again with --game \"<the White Knuckle folder>\" (in Steam: right-click White Knuckle >"
             " Manage > Browse local files shows it).")


class Files:
    """Loads serialized files one at a time (loading the whole folder at once can crash UnityPy)."""

    def __init__(self, data_dir):
        self.data_dir = data_dir
        self.cache = {}

    def objects(self, name):
        if name not in self.cache:
            env = UnityPy.load(str(self.data_dir / name))
            sf = list(env.files.values())[0]
            # keep env alive: streamed audio/texture data is read through it
            self.cache[name] = (sf, {o.path_id: o for o in env.objects}, env)
        return self.cache[name][:2]

    def resolve(self, from_file, pptr):
        sf, objs = self.objects(from_file)
        if pptr["m_FileID"] == 0:
            return from_file, objs[pptr["m_PathID"]]
        ext = Path(sf.externals[pptr["m_FileID"] - 1].path).name
        return ext, self.objects(ext)[1][pptr["m_PathID"]]


def find_player(files):
    for name in ("level3", "level2"):
        _, objs = files.objects(name)
        for obj in objs.values():
            if obj.type.name != "MonoBehaviour":
                continue
            try:
                if obj.read(check_read=False).m_Script.read().m_ClassName == "ENT_Player":
                    return name, obj
            except Exception:
                continue
    sys.exit("ENT_Player not found in the game's scenes")


# libsndfile's Vorbis encoder keeps buffers on the stack in proportion to how many frames are
# written in one call. Windows gives the main thread only 1 MB of stack, so writing a whole
# multi-second clip at once overflows and kills Python outright (no exception). Writing in small
# chunks avoids it; the encode also runs on a thread with a large stack as extra headroom.
OGG_CHUNK_FRAMES = 4096
ENCODER_STACK_BYTES = 64 * 1024 * 1024


def _encode_ogg_soundfile(wav_bytes, out_path):
    import soundfile as sf
    data, rate = sf.read(io.BytesIO(wav_bytes), always_2d=True)
    mono = data.mean(axis=1)
    with sf.SoundFile(str(out_path), "w", rate, 1, format="OGG", subtype="VORBIS") as f:
        for i in range(0, len(mono), OGG_CHUNK_FRAMES):
            f.write(mono[i:i + OGG_CHUNK_FRAMES])


def _run_with_big_stack(fn, *args):
    import threading
    result = {}

    def target():
        try:
            fn(*args)
        except BaseException as e:  # re-raised on the calling thread
            result["error"] = e

    old = threading.stack_size()
    try:
        threading.stack_size(ENCODER_STACK_BYTES)
        t = threading.Thread(target=target, name="ogg-encoder")
        t.start()
    finally:
        threading.stack_size(old)
    t.join()
    if "error" in result:
        raise result["error"]


def to_ogg(wav_bytes, out_path):
    """Mono Ogg Vorbis for Minecraft: soundfile (pip wheels on Windows/Mac/Linux), else ffmpeg."""
    try:
        import soundfile  # noqa: F401
        have_soundfile = True
    except ImportError:
        have_soundfile = False
    if have_soundfile:
        _run_with_big_stack(_encode_ogg_soundfile, wav_bytes, out_path)
    else:
        with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
            tmp.write(wav_bytes)
            tmp_path = tmp.name
        try:
            subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", tmp_path, "-ac", "1",
                            "-c:a", "libvorbis", "-q:a", "5", str(out_path)], check=True)
        finally:
            os.unlink(tmp_path)
    if not Path(out_path).is_file() or Path(out_path).stat().st_size == 0:
        raise RuntimeError(f"sound conversion produced no file: {out_path}")


def image_sprite(files, scene, image_ptr):
    """The sprite of a UnityEngine.UI.Image, read from its raw data (the type-tree generator can't
    build UI types): MonoBehaviour header, Graphic fields, then m_Sprite."""
    import struct
    file, obj = files.resolve(scene, image_ptr)
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
    rd("<iq"); rd("<4f"); rd("<B"); align(); rd("<4f"); rd("<B"); align()
    if rd("<i")[0] != 0:
        return None
    file_id, path_id = rd("<iq")
    if path_id == 0:
        return None
    _, sprite = files.resolve(file, {"m_FileID": file_id, "m_PathID": path_id})
    return sprite.read().image


def find_component(files, scene, class_name, gen):
    _, objs = files.objects(scene)
    for obj in objs.values():
        if obj.type.name != "MonoBehaviour":
            continue
        try:
            if obj.read(check_read=False).m_Script.read().m_ClassName == class_name:
                return obj.read_typetree(gen.get_nodes_up("Assembly-CSharp", class_name))
        except Exception:
            continue
    print(f"  ! {class_name} not found")
    return None


def find_icy_handhold(files, data_dir):
    """First CL_Handhold_Icy in the shared assets (the Chimney ice), read with a fixed type tree:
    the generator emits a stray ManagedReferencesRegistry before the subclass fields."""
    name = "sharedassets1.assets"
    sf, objs = files.objects(name)
    gen = TypeTreeGenerator(sf.unity_version)
    gen.load_local_dll_folder(str(data_dir / "Managed"))
    nodes = gen.get_nodes_up("Assembly-CSharp", "CL_Handhold_Icy")
    refs = [i for i, ch in enumerate(nodes.m_Children) if ch.m_Name == "references"]
    if len(refs) > 1:
        del nodes.m_Children[refs[0]]
    for obj in objs.values():
        if obj.type.name != "MonoBehaviour":
            continue
        try:
            if obj.read(check_read=False).m_Script.read().m_ClassName != "CL_Handhold_Icy":
                continue
            return name, obj.read_typetree(nodes)
        except Exception:
            continue
    print("  ! no CL_Handhold_Icy found")
    return None


class ClipDecoder:
    """Turns AudioClips into WAV bytes, fast and the same on Windows and Linux.

    All clips are decoded together by ONE subprocess per game file (`--decode-clips`), using
    UnityPy's FMOD in a fresh process. FMOD in the main process refuses these clips (it fails on
    the IMA ADPCM ones and then on later ones), and the old approach of one subprocess per clip
    re-read the ~1 GB asset file each time: 285 s against ~13 s for the whole pack now.
    If FMOD can't decode a clip, IMA ADPCM clips (FSB5 codec 7) fall back to fsb5_ima_to_wav,
    which is verified bit-identical to FMOD on every clip the pack uses.
    Results are delivered through callbacks once flush() has run.
    """

    def __init__(self, data_dir, fmod_only=False, no_fmod=False):
        self.data_dir = data_dir
        self.fmod_only = fmod_only  # --fmod-only: no fallback (for checking FMOD on its own)
        self.no_fmod = no_fmod  # --no-fmod: skip FMOD (for checking the fallback on its own)
        self.pending = []  # (file name, clip object, callback)
        self.stats = {"FMOD": 0, "own IMA decoder (fallback)": 0, "failed": 0}

    def decode(self, clip_file, clip_obj, on_wav):
        self.pending.append((clip_file, clip_obj, on_wav))

    def _raw(self, clip):
        res = clip.m_Resource
        if not res.m_Source:
            return None
        try:
            with open(self.data_dir / Path(res.m_Source).name, "rb") as fh:
                fh.seek(res.m_Offset)
                return fh.read(res.m_Size)
        except OSError:
            return None

    def flush(self):
        by_file = {}
        for item in self.pending:
            by_file.setdefault(item[0], []).append(item)
        self.pending = []
        for file_name, items in by_file.items():
            with tempfile.TemporaryDirectory() as tmp:
                ids = [str(obj.path_id) for _, obj, _ in items]
                if not self.no_fmod:
                    subprocess.run([sys.executable, "-I", __file__, "--decode-clips", str(self.data_dir / file_name), tmp, *ids],
                                   capture_output=True)
                for _, obj, on_wav in items:
                    out = Path(tmp) / f"{obj.path_id}.wav"
                    if out.exists() and out.stat().st_size > 44:
                        on_wav(out.read_bytes())
                        self.stats["FMOD"] += 1
                        continue
                    clip = obj.read()
                    raw = None if self.fmod_only else self._raw(clip)
                    if raw is not None and raw[:4] == b"FSB5" and struct.unpack_from("<I", raw, 24)[0] == 7:
                        total = int(round(clip.m_Length * clip.m_Frequency))
                        on_wav(fsb5_ima_to_wav(raw, clip.m_Channels, clip.m_Frequency, total))
                        self.stats["own IMA decoder (fallback)"] += 1
                    else:
                        print(f"  ! could not decode {clip.m_Name}")
                        self.stats["failed"] += 1


def decode_clips_main(file_path, out_dir, *path_ids):
    """Subprocess worker: decode several clips from one game file with a fresh FMOD."""
    wanted = {int(i) for i in path_ids}
    env = UnityPy.load(file_path)
    for o in env.objects:
        if o.path_id in wanted:
            try:
                for _, wav in o.read().samples.items():
                    (Path(out_dir) / f"{o.path_id}.wav").write_bytes(wav)
                    break
            except Exception:
                pass


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--game", help="White Knuckle install folder")
    ap.add_argument("--instance", help="Minecraft instance folder (its resourcepacks/ gets the pack)")
    ap.add_argument("--out", help="write the pack folder here instead")
    ap.add_argument("--enable", action="store_true", help="also turn the pack on in the instance's options.txt")
    ap.add_argument("--zip", action="store_true", help="also write the pack as a single .zip next to the folder")
    ap.add_argument("--fmod-only", action="store_true", help=argparse.SUPPRESS)  # decoder check: no fallback
    ap.add_argument("--no-fmod", action="store_true", help=argparse.SUPPRESS)  # decoder check: fallback only
    args = ap.parse_args()
    try:
        import soundfile  # noqa: F401
    except ImportError:
        if not shutil.which("ffmpeg"):
            sys.exit("Install the requirements (pip install -r requirements.txt) or ffmpeg to convert the sounds")

    data_dir = find_game_dir(args.game)
    mc_dir = None
    if args.out:
        pack = Path(args.out).expanduser()
    elif args.instance:
        inst = Path(args.instance).expanduser()
        mc_dir = inst / "minecraft" if (inst / "minecraft").is_dir() or (inst / "instance.cfg").exists() else inst
        pack = mc_dir / "resourcepacks" / PACK_NAME
    else:
        sys.exit("pass --instance <minecraft instance> or --out <folder>")

    files = Files(data_dir)
    scene, player_obj = find_player(files)
    sf, _ = files.objects(scene)
    gen = TypeTreeGenerator(sf.unity_version)
    gen.load_local_dll_folder(str(data_dir / "Managed"))
    player = player_obj.read_typetree(gen.get_nodes_up("Assembly-CSharp", "ENT_Player"))
    print(f"ENT_Player found in {scene}")

    # Build next to the destination and move it into place at the end, so a running Minecraft never
    # sees a half-written pack (it skips folders without pack.mcmeta until the next restart).
    final_pack = pack
    pack = final_pack.parent / (final_pack.name + ".partial")
    final_pack.parent.mkdir(parents=True, exist_ok=True)
    if pack.exists():
        shutil.rmtree(pack)
    tex_dir = pack / "assets/wkclimb/textures/gui/hands"
    snd_dir = pack / "assets/wkclimb/sounds/wk"
    tex_dir.mkdir(parents=True)
    snd_dir.mkdir(parents=True)

    # Hand sprites
    poses = {p["id"]: p["sprite"] for p in player["hands"][0]["handPoses"]}
    for pose in HAND_POSES:
        if pose not in poses:
            print(f"  ! pose {pose} missing")
            continue
        _, obj = files.resolve(scene, poses[pose])
        sprite = obj.read()
        img = sprite.image.convert("RGBA")
        img.save(tex_dir / f"{pose}.png")
        print(f"  hand {pose}: {sprite.m_Name} {img.size[0]}x{img.size[1]}")
    # Crosshair: UI_CrosshairController "base" (crosshairSprites[settings.crosshair = 0]) and "focus"
    xh = find_component(files, scene, "UI_CrosshairController", gen)
    if xh:
        for c in xh["crosshairs"]:
            _, obj = files.resolve(scene, c["sprite"])
            img = obj.read().image.convert("RGBA")
            img.save(tex_dir.parent / f"crosshair_{c['id']}.png")
            # Pre-filtered smaller copies (Minecraft GUI textures have no mipmaps; the game's do)
            for size in (256, 128, 64, 32):
                if size < img.size[0]:
                    img.resize((size, size), Image.LANCZOS).save(tex_dir.parent / f"crosshair_{c['id']}_{size}.png")
            print(f"  crosshair {c['id']}: {img.size[0]}x{img.size[1]}, size {c['size']}")
    # Damage overlay: CL_UIManager's damage-direction indicators and the player's screen blood
    # (bloodEffect particle system -> renderer material _MainTex, a 4x4 sheet). The hurt vignette is
    # drawn by the mod's shader, so its image isn't needed.
    dmg_dir = tex_dir.parent / "damage"
    dmg_dir.mkdir(exist_ok=True)
    ui = find_component(files, scene, "CL_UIManager", gen)
    if ui:
        for i, name in ((0, "direction_side"), (1, "direction_top")):
            spr = image_sprite(files, scene, ui["damageDirectionIndicators"][i])
            if spr:
                spr.convert("RGBA").save(dmg_dir / f"{name}.png")
                print(f"  {name}: {spr.size[0]}x{spr.size[1]}")
    blood_file, blood = files.resolve(scene, player["bloodEffect"])
    _, blood_objs = files.objects(blood_file)
    go = blood_objs[blood.read_typetree()["m_GameObject"]["m_PathID"]].read_typetree()
    for c in go["m_Component"]:
        comp = blood_objs[c["component"]["m_PathID"]]
        if comp.type.name != "ParticleSystemRenderer":
            continue
        mat_file, mat = files.resolve(blood_file, comp.read_typetree()["m_Materials"][0])
        for prop, te in mat.read_typetree()["m_SavedProperties"]["m_TexEnvs"]:
            if prop == "_MainTex" and te["m_Texture"]["m_PathID"]:
                _, tex = files.resolve(mat_file, te["m_Texture"])
                img = tex.read().image.convert("RGBA")
                img.resize((1024, 1024), Image.LANCZOS).save(dmg_dir / "blood_splatter.png")
                print(f"  screen blood sheet: {img.size[0]}x{img.size[1]} (saved at 1024)")

    for entry in player["interactionSprites"]:
        if entry["name"] in UI_ICONS:
            _, obj = files.resolve(scene, entry["sprite"])
            img = obj.read().image.convert("RGBA")
            img.save(tex_dir / f"{UI_ICONS[entry['name']]}.png")
            print(f"  icon {entry['name']}: {img.size[0]}x{img.size[1]}")

    # Sounds
    _, handler_obj = files.resolve(scene, player["clipHandler"])
    handler = handler_obj.read_typetree(gen.get_nodes_up("Assembly-CSharp", "UT_AudioClipHandler"))
    sounds_json, meta = {}, {}
    decoder = ClipDecoder(data_dir, fmod_only=args.fmod_only, no_fmod=args.no_fmod)

    def ogg_writer(clip_obj, entries, volume):
        def write(wav):
            name = clip_obj.read().m_Name.lower().replace(" ", "_")
            to_ogg(wav, snd_dir / f"{name}.ogg")
            entries.append({"name": f"wkclimb:wk/{name}", "volume": round(volume, 3)})
        return write

    for group in handler["groups"]:
        for s in group["audioSets"]:
            key = (group["name"], s["name"])
            if key not in SOUND_SETS:
                continue
            event = SOUND_SETS[key]
            entries, seen = [], set()
            for clip_ptr in s["clips"]:
                if clip_ptr["m_PathID"] in seen:
                    continue
                seen.add(clip_ptr["m_PathID"])
                clip_file, clip_obj = files.resolve(scene, clip_ptr)
                decoder.decode(clip_file, clip_obj, ogg_writer(clip_obj, entries, s["maxVolume"]))
            sounds_json[event] = {"sounds": entries}
            meta[event] = {"minPitch": s["minPitch"], "maxPitch": s["maxPitch"], "loop": bool(s["loop"])}
            print(f"  sound set {group['name']}:{s['name']} -> wkclimb:{event}")
    def add_clips(event, clip_ptrs, volume, from_file=scene):
        entries, seen = [], set()
        sounds_json[event] = {"sounds": entries}
        for ptr in clip_ptrs:
            if not ptr or ptr["m_PathID"] == 0 or ptr["m_PathID"] in seen:
                continue
            seen.add(ptr["m_PathID"])
            clip_file, clip_obj = files.resolve(from_file, ptr)
            decoder.decode(clip_file, clip_obj, ogg_writer(clip_obj, entries, volume))
        print(f"  sound set wkclimb:{event}")

    # Footsteps: ENT_Player.footsteps, one list of clips per floor material
    for fs in player["footsteps"]:
        key = fs["name"].lower().replace(" footsteps", "").replace(" ", "_")
        add_clips(f"footstep.{key}", fs["audio"], fs["volume"])
        meta[f"footstep.{key}"] = {"minPitch": fs["minPitch"], "maxPitch": fs["maxPitch"], "friction": fs["friction"],
                                   "moveLerp": fs["moveLerp"], "moveSpeed": fs["moveSpeed"]}

    # Loops: the clip on each AudioSource. Unity 6 stores it in m_Resource (an AudioResource); the older
    # m_audioClip field is still there but empty, so take whichever reference is set.
    for field, event in LOOP_SOURCES.items():
        ptr = player.get(field)
        if not ptr or not ptr["m_PathID"]:
            print(f"  ! {field} not found")
            continue
        src_file, src_obj = files.resolve(scene, ptr)
        src = src_obj.read_typetree()
        clip_ptr = next((c for c in (src.get("m_Resource"), src.get("m_audioClip")) if c and c["m_PathID"]), None)
        if clip_ptr is None:
            print(f"  ! {field} has no clip")
            continue
        add_clips(event, [clip_ptr], 1.0, src_file)
        meta[event] = {"loop": True}

    # Icy handholds (Chimney): slipSound / shakeStartSound on CL_Handhold_Icy
    icy = find_icy_handhold(files, data_dir)
    if icy:
        icy_file, icy_tt = icy
        add_clips("icy.slip", [icy_tt["slipSound"]], 1.0, icy_file)
        add_clips("icy.shake", [icy_tt["shakeStartSound"]], 1.0, icy_file)

    decoder.flush()
    sounds_json = {k: v for k, v in sounds_json.items() if v["sounds"]}
    print("  clips decoded: " + ", ".join(f"{n} {k}" for k, n in decoder.stats.items()))
    (pack / "assets/wkclimb/sounds.json").write_text(json.dumps(sounds_json, indent=1))
    (pack / "assets/wkclimb/wk_sound_meta.json").write_text(json.dumps(meta, indent=1))
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {
        "description": "White Knuckle hands and sounds, extracted from your own copy of the game",
        "pack_format": PACK_FORMAT, "min_format": PACK_FORMAT, "max_format": PACK_FORMAT}}, indent=1))
    if final_pack.exists():
        shutil.rmtree(final_pack)
    pack.rename(final_pack)
    pack = final_pack
    print(f"Pack written to {pack}")
    if args.zip:
        zip_path = shutil.make_archive(str(pack), "zip", root_dir=str(pack))
        print(f"Zipped pack: {zip_path}")
    if args.instance:  # the pack went straight into an instance's resourcepacks folder
        print("Restart Minecraft (or press F3+T) to load it.")

    if args.enable and mc_dir:
        opts = mc_dir / "options.txt"
        entry = f'"file/{PACK_NAME}"'
        lines = opts.read_text().splitlines() if opts.exists() else []
        found = False
        for i, line in enumerate(lines):
            if line.startswith("resourcePacks:"):
                found = True
                packs = json.loads(line.split(":", 1)[1] or "[]")
                if f"file/{PACK_NAME}" not in packs:
                    packs.append(f"file/{PACK_NAME}")
                lines[i] = "resourcePacks:" + json.dumps(packs)
        if not found:
            lines.append("resourcePacks:" + json.dumps(["vanilla", "fabric", f"file/{PACK_NAME}"]))
        opts.write_text("\n".join(lines) + "\n")
        print(f"Enabled in {opts} ({entry})")


if __name__ == "__main__":
    if len(sys.argv) >= 4 and sys.argv[1] == "--decode-clips":
        decode_clips_main(*sys.argv[2:])
    else:
        main()
