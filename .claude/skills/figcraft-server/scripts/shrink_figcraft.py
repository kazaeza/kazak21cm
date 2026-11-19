#!/usr/bin/env python3
"""Make a .figcraft file smaller by downscaling its images, so it fits a server's limits.

    python3 shrink_figcraft.py --cli /path/to/figcraft-cli IN.figcraft OUT.figcraft \
        [--max-side 2048] [--quality 85] [--work DIR]

1. Opens IN in a headless `figcraft-cli mcp` and finds every layer with an image paint.
2. Downscales each image to --max-side (JPEG at --quality; PNG when it has transparency); an
   image that wouldn't get smaller is left as it is.
3. Puts the new bytes in with FigCraft's own `image.replace` (keeps fill mode, crop and position)
   and saves a temporary file.
4. FigCraft keeps replaced images in the file, so the last step rewrites the archive without
   images nothing references (and drops them from `imageIndex`).

IN is never changed. Open OUT in FigCraft and look at it before you use it.
Needs Pillow (python3 -m pip install pillow).
"""
import argparse
import io
import json
import os
import re
import sys
import tempfile
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from figcraft_live import McpClient, Runner, PROTOCOL  # noqa: E402

HASH = re.compile(r"[0-9a-f]{64}")


def image_layers(tree):
    found = []

    def walk(n):
        if isinstance(n, dict):
            for i, f in enumerate(n.get("fills") or []):
                if isinstance(f, dict) and f.get("type") == "image" and isinstance(n.get("id"), int):
                    found.append((n["id"], i, f.get("image")))
            for v in n.values():
                walk(v)
        elif isinstance(n, list):
            for v in n:
                walk(v)
    walk(tree)
    return found


def downscale(data, max_side, quality):
    from PIL import Image
    im = Image.open(io.BytesIO(data))
    alpha = im.mode in ("RGBA", "LA") or (im.mode == "P" and "transparency" in im.info)
    if alpha and im.mode != "RGBA":
        im = im.convert("RGBA")
    if alpha and im.getchannel("A").getextrema() == (255, 255):
        alpha = False  # an alpha channel that is opaque everywhere: store as JPEG
    if not alpha:
        im = im.convert("RGB")
    im.thumbnail((max_side, max_side))
    out = io.BytesIO()
    if alpha:
        im.save(out, "PNG", optimize=True)
    else:
        im.save(out, "JPEG", quality=quality, optimize=True)
    return out.getvalue(), ("png" if alpha else "jpg")


def prune(src, dst):
    """Copy the archive without images that no layer, style or comment references."""
    with zipfile.ZipFile(src) as z:
        doc = json.loads(z.read("document.json"))
        index = doc.get("imageIndex") or []
        rest = {k: v for k, v in doc.items() if k != "imageIndex"}
        used = set(HASH.findall(json.dumps(rest)))
        doc["imageIndex"] = [e for e in index if e.get("hash") in used]
        kept = dropped = 0
        with zipfile.ZipFile(dst, "w") as out:
            for info in z.infolist():
                if info.filename == "document.json":
                    out.writestr(info, json.dumps(doc, ensure_ascii=False), compress_type=info.compress_type)
                    continue
                if info.filename.startswith("images/") and info.filename[len("images/"):] not in used:
                    dropped += 1
                    continue
                kept += info.filename.startswith("images/")
                with z.open(info) as f:
                    out.writestr(info, f.read(), compress_type=info.compress_type)
    return kept, dropped


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("src")
    ap.add_argument("dst")
    ap.add_argument("--cli", required=True)
    ap.add_argument("--max-side", type=int, default=2048)
    ap.add_argument("--quality", type=int, default=85)
    ap.add_argument("--work", default=None, help="folder for temporary files (default: a new temp dir)")
    a = ap.parse_args()
    src, dst = os.path.abspath(a.src), os.path.abspath(a.dst)
    if src == dst:
        sys.exit("OUT must differ from IN")
    work = a.work or tempfile.mkdtemp(prefix="figcraft-shrink-")
    os.makedirs(work, exist_ok=True)

    client = McpClient([a.cli, "mcp"], os.path.join(work, "mcp.stderr.log"))
    client.request("initialize", {"protocolVersion": PROTOCOL, "capabilities": {},
                                  "clientInfo": {"name": "shrink_figcraft.py", "version": "1"}}, 60)
    client.notify("notifications/initialized")
    run = Runner(client, work)
    try:
        run.tool("open_file", {"path": src}, timeout=1800)
        tree = run.tool("inspect_document", {"allPages": True, "depth": 1000,
                                             "fields": ["id", "name", "fills"], "maxBytes": 256 * 2**20})
        if isinstance(tree, dict) and tree.get("truncated"):
            raise RuntimeError(f"layer tree was cut short: {tree['truncated']}")
        layers = image_layers(tree)
        z = zipfile.ZipFile(src)
        names = {n[len("images/"):]: n for n in z.namelist() if n.startswith("images/")}
        done, replaced, skipped = {}, 0, 0
        for lid, index, h in layers:
            if h not in names:
                skipped += 1
                continue
            if h not in done:
                data = z.read(names[h])
                new, ext = downscale(data, a.max_side, a.quality)
                if len(new) >= len(data):
                    done[h] = None
                else:
                    p = os.path.join(work, f"{h[:16]}.{ext}")
                    with open(p, "wb") as f:
                        f.write(new)
                    done[h] = p
            if done[h]:
                run.command("image.replace", {"ids": [lid], "index": index, "path": done[h]})
                replaced += 1
            else:
                skipped += 1
        tmp = os.path.join(work, "replaced.figcraft")
        run.tool("save_file", {"path": tmp}, timeout=1800)
    finally:
        client.close()

    kept, dropped = prune(tmp, dst)
    before, after = os.path.getsize(src), os.path.getsize(dst)
    print(json.dumps({"layers": len(layers), "replaced": replaced, "unchanged": skipped,
                      "images_kept": kept, "images_dropped": dropped,
                      "before_mib": round(before / 2**20, 1), "after_mib": round(after / 2**20, 1),
                      "out": dst}, ensure_ascii=False))


if __name__ == "__main__":
    main()
