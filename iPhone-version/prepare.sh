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
a = s.index('name = "AppNameInfoPlist"')
b = s.index('"""\n)', a)
s = s[:a] + s[a:b].replace("<string>Swiftgram</string>", "<string>Avragram</string>") + s[b:]
open(p, "w").write(s)
PY

# 3. App icon
cp "$HERE/branding/AppIcon.png" "$SG/Telegram/Telegram-iOS/DefaultAppIcon.xcassets/AppIconLLC.appiconset/Swiftgram.png"

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
