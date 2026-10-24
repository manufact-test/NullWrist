#!/usr/bin/env bash
# M0 gate: reproducible upstream PebbleOS qemu_emery desktop build and smoke.
# Do not run the old NullWrist project, do not alter app/, release tags or any stored Basalt flash.
set -Eeuo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOCK="${REPO_ROOT}/time2/upstream.lock.json"
LAB="${EMERY_LAB_DIR:-${REPO_ROOT}/time2/.lab}"
LOGS="${EMERY_LOGS_DIR:-${REPO_ROOT}/time2/m0-results}"
mkdir -p "$LAB" "$LOGS"

eval "$(python3 - "$LOCK" <<'PY'
import json,sys,shlex
with open(sys.argv[1]) as f:
    lock=json.load(f)
for var,value in (
  ("PEBBLEOS_REV",lock["pebbleos"]["sha"]),
  ("PEBBLEOS_SDK_VERSION","0.1.11"),
):
    print(f"{var}={shlex.quote(value)}")
PY
)"

if [[ ! -f "$HOME/pebbleos-sdk-$PEBBLEOS_SDK_VERSION/env.sh" ]]; then
    # Installer comes from a fixed release tag (not /latest). A more secure
    # production build should additionally verify the published asset hash.
    curl --fail --location --retry 3 --silent --show-error \
      "https://github.com/coredevices/PebbleOS-SDK/releases/download/v${PEBBLEOS_SDK_VERSION}/pebbleos-sdk-installer.sh" \
      -o "$LAB/pebbleos-sdk-installer.sh"
    sh "$LAB/pebbleos-sdk-installer.sh" --version "$PEBBLEOS_SDK_VERSION" --defaults
fi
# shellcheck disable=SC1091
source "$HOME/pebbleos-sdk-$PEBBLEOS_SDK_VERSION/env.sh"
cp "$HOME/pebbleos-sdk-$PEBBLEOS_SDK_VERSION/.sdk-info" "$LOGS/sdk-info.txt" || true
arm-none-eabi-gcc --version | head -2 | tee "$LOGS/toolchain.txt"

if [[ ! -d "$LAB/PebbleOS/.git" ]]; then
    git clone --filter=blob:none https://github.com/coredevices/PebbleOS.git "$LAB/PebbleOS"
fi
git -C "$LAB/PebbleOS" fetch --depth 1 origin "$PEBBLEOS_REV"
git -C "$LAB/PebbleOS" checkout --detach --force FETCH_HEAD
git -C "$LAB/PebbleOS" submodule update --init --recursive
cd "$LAB/PebbleOS"
python3 -m venv .venv
# shellcheck disable=SC1091
source .venv/bin/activate
pip install --disable-pip-version-check -r requirements.txt >"$LOGS/python-deps.log" 2>&1

echo "$PEBBLEOS_REV" > "$LOGS/pebbleos-revision.txt"
echo "=== Configuring genuine Emery QEMU board ==="
pbl configure --board qemu_emery 2>&1 | tee "$LOGS/configure.log"
echo "=== Building genuine Emery firmware ==="
pbl build 2>&1 | tee "$LOGS/build.log"
echo "=== Recording the official QEMU launch arguments ==="
pbl qemu --dry-run > "$LOGS/launch-command.txt" 2>&1
echo "=== Running PebbleOS integration smoke tests (real emulator boot) ==="
# PebbleOS integration harness starts and shuts down QEMU headless.
# Success is necessary for M0 but not proof that Android/ARM64 works.
timeout 900 pbl itest \
  ui/test_navigation.py::test_launcher_opens \
  ui/test_navigation.py::test_back_returns_home \
  ui/test_touch.py::test_swipe_scrolls_menu \
  ui/test_touch.py::test_tap_opens_item \
  -q --maxfail=1 2>&1 | tee "$LOGS/itest.log"
echo "M0 desktop smoke complete"
echo "=== Exporting independently reproducible Emery development firmware images ==="
mkdir -p "$LOGS/firmware"
for item in pebbleos.elf qemu_micro_flash.bin qemu_spi_flash.bin; do
    input="$LAB/PebbleOS/build/$item"
    test -s "$input" || { echo "Missing Emery firmware asset: $item" >&2; exit 1; }
    cp "$input" "$LOGS/firmware/$item"
done
(
  cd "$LOGS/firmware"
  sha256sum pebbleos.elf qemu_micro_flash.bin qemu_spi_flash.bin > SHA256SUMS
)
echo "Prepared development-only Emery images with per-file SHA-256"
