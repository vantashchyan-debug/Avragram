#!/usr/bin/env bash
# Turns a Swiftgram checkout into Avragram for iPhone.
#   prepare.sh <swiftgram dir>
# Needs AVRA_API_ID and AVRA_API_HASH in the environment (GitHub secrets in CI; never committed).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
SG="$1"
: "${AVRA_API_ID:?AVRA_API_ID is not set}"
: "${AVRA_API_HASH:?AVRA_API_HASH is not set}"

# 1. Build configuration with our own api_id / api_hash
python3 - "$SG" <<'PY'
import json, os, sys
sg = sys.argv[1]
cfg = json.load(open(os.path.join(sg, "build-system/appstore-configuration.json")))
cfg["api_id"] = os.environ["AVRA_API_ID"]
cfg["api_hash"] = os.environ["AVRA_API_HASH"]
cfg.setdefault("sg_config", "")  # Swiftgram's own server settings: none for Avragram
json.dump(cfg, open(os.path.join(sg, "build-system/avragram-configuration.json"), "w"), indent=2)
PY

# 2. Name on the home screen
python3 - "$SG" <<'PY'
import os, sys
p = os.path.join(sys.argv[1], "Telegram/BUILD")
s = open(p).read()
for fragment in ("AppNameInfoPlist", "TelegramInfoPlist"):
    a = s.index('name = "%s"' % fragment)
    b = s.index('"""', s.index('"""', a) + 3)
    part = s[a:b]
    part = part.replace("<key>CFBundleDisplayName</key>\n    <string>Swiftgram</string>", "<key>CFBundleDisplayName</key>\n    <string>Avragram</string>")
    part = part.replace("<key>CFBundleName</key>\n    <string>Swiftgram</string>", "<key>CFBundleName</key>\n    <string>Avragram</string>")
    s = s[:a] + part + s[b:]
open(p, "w").write(s)
PY

# 2b. "Swiftgram" in the welcome screen and in texts that name the app
for f in "$SG"/Telegram/Telegram-iOS/*.lproj/Localizable.strings; do
  sed -i '' -e 's/^"Tour.Title1" = "Swiftgram";/"Tour.Title1" = "Avragram";/' -e 's/^"Application.Name" = "Swiftgram";/"Application.Name" = "Avragram";/' "$f" 2>/dev/null \
    || sed -i -e 's/^"Tour.Title1" = "Swiftgram";/"Tour.Title1" = "Avragram";/' -e 's/^"Application.Name" = "Swiftgram";/"Application.Name" = "Avragram";/' "$f"
done

# 3. App icon
cp "$HERE/branding/AppIcon.png" "$SG/Telegram/Telegram-iOS/DefaultAppIcon.xcassets/AppIconLLC.appiconset/Swiftgram.png"
# iOS 26+ takes the home screen icon from the Icon Composer package (glass layers)
rm -rf "$SG/Telegram/Telegram-iOS/Swiftgram.icon/Assets"
mkdir -p "$SG/Telegram/Telegram-iOS/Swiftgram.icon/Assets"
cp "$HERE/branding/icon/icon.json" "$SG/Telegram/Telegram-iOS/Swiftgram.icon/icon.json"
cp "$HERE/branding/icon/Assets/"* "$SG/Telegram/Telegram-iOS/Swiftgram.icon/Assets/"
# the default alternate icon (Settings → Appearance → App Icon) is ours too
for f in "$SG"/Telegram/Telegram-iOS/SGDefault.alticon/*.png; do
  python3 - "$HERE/branding/AppIcon.png" "$f" <<'PY' || cp "$HERE/branding/AppIcon.png" "$f"
import sys, subprocess
src, dst = sys.argv[1], sys.argv[2]
size = subprocess.check_output(["sips", "-g", "pixelWidth", dst]).decode().split()[-1]
subprocess.check_call(["sips", "-z", size, size, src, "--out", dst], stdout=subprocess.DEVNULL)
PY
done

# 3b. Test signing profiles: the ones in Swiftgram/Telegram expired on 2026-10-30; ours are the same
#     profiles re-signed with the same self-signed test certificate, valid until 2030-10-01
cp "$HERE"/codesigning/profiles/*.mobileprovision "$SG/build-system/fake-codesigning/profiles/"

# 4. Avragram sources and resources
if [ -d "$HERE/overlay" ] && [ -n "$(ls -A "$HERE/overlay")" ]; then
  cp -R "$HERE/overlay/." "$SG/"
fi

# 5. Patches to Swiftgram / Telegram code
shopt -s nullglob
for p in "$HERE"/patches/*.patch; do
  echo "applying $(basename "$p")"
  git -C "$SG" apply --whitespace=nowarn "$p"
done
echo "Avragram iOS prepared in $SG"
