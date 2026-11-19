#!/usr/bin/env bash
# Download FigCraft for Linux x86_64 from the official GitHub release, check it against the
# release's SHA256SUMS.txt and unpack it. Prints the absolute paths of the binaries.
#
#   get_figcraft.sh DEST_DIR [--server] [--version 0.18.1]
#
# --server also fetches figcraft-server. Nothing is installed system-wide and nothing is run.
set -euo pipefail

dest=""; want_server=0; version=""
while [ $# -gt 0 ]; do
  case "$1" in
    --server) want_server=1 ;;
    --version) version="${2:?--version needs a value}"; shift ;;
    -*) echo "unknown option: $1" >&2; exit 2 ;;
    *) dest="$1" ;;
  esac
  shift
done
[ -n "$dest" ] || { echo "usage: $0 DEST_DIR [--server] [--version X.Y.Z]" >&2; exit 2; }
[ "$(uname -m)" = "x86_64" ] || { echo "this script fetches the x86_64 build; this machine is $(uname -m)" >&2; exit 2; }

mkdir -p "$dest"; dest="$(cd "$dest" && pwd)"
repo="https://github.com/storytold/figcraft/releases"
if [ -n "$version" ]; then base="$repo/download/v$version"; else base="$repo/latest/download"; fi

curl -fsSL -m 60 -o "$dest/SHA256SUMS.txt" "$base/SHA256SUMS.txt"
app=$(grep -oE 'figcraft-[0-9][0-9.]*-linux-x86_64\.tar\.gz$' "$dest/SHA256SUMS.txt" | head -1)
[ -n "$app" ] || { echo "no Linux x86_64 build in this release" >&2; exit 1; }
version=${app#figcraft-}; version=${version%-linux-x86_64.tar.gz}
files=("$app")
[ "$want_server" = 1 ] && files+=("figcraft-server-$version-linux-x86_64.tar.gz")

for f in "${files[@]}"; do
  if [ ! -f "$dest/$f" ]; then
    curl -fsSL -m 900 -o "$dest/$f.part" "$repo/download/v$version/$f"
    mv "$dest/$f.part" "$dest/$f"
  fi
  (cd "$dest" && grep -E "  $f\$" SHA256SUMS.txt | sha256sum -c --quiet -) \
    || { echo "checksum mismatch for $f: deleted" >&2; rm -f "$dest/$f"; exit 1; }
  tar -xzf "$dest/$f" -C "$dest"
done

echo "version=$version"
echo "figcraft_cli=$dest/figcraft-$version-linux-x86_64/bin/figcraft-cli"
[ "$want_server" = 1 ] && echo "figcraft_server=$dest/figcraft-server-$version-linux-x86_64/figcraft-server"
exit 0
