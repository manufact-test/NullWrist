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
  ("PEBBLEOS_SDK_VERSION",lock["pebbleos_sdk"]["version"]),
  ("SDK_INSTALLER_SHA256",lock["pebbleos_sdk"]["installer_sha256"]),
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
    echo "$SDK_INSTALLER_SHA256  $LAB/pebbleos-sdk-installer.sh" | sha256sum --check -
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
test "$(git -C "$LAB/PebbleOS" rev-parse HEAD)" = "$PEBBLEOS_REV" || {
  echo "PebbleOS source revision differs from upstream lock" >&2; exit 1;
}
git -C "$LAB/PebbleOS" submodule update --init --recursive
if [[ "${EMERY_LEGACY_STACK_EXPERIMENT:-0}" == "1" ]]; then
    echo "=== Candidate: legacy Basalt 3.x 4KiB stack on Emery, MPU unchanged ==="
    python3 "$REPO_ROOT/time2/scripts/experiment_emery_legacy_stack.py" \
      "$LAB/PebbleOS/fw/process_management/app_manager.c" "$LOGS/legacy-stack-experiment.json"
fi
echo "=== Verify both source privilege-entry paths before any build ==="
python3 "$REPO_ROOT/time2/scripts/verify_emery_privilege_fix.py" --source "$LAB/PebbleOS" --report "$LOGS/privilege-source.json"
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
echo "=== Verify source-derived firmware ELF and disassemble both task wrappers ==="
python3 "$REPO_ROOT/time2/scripts/verify_emery_privilege_fix.py" --source "$LAB/PebbleOS" \
  --elf "$LAB/PebbleOS/build/pebbleos.elf" --report "$LOGS/privilege-elf.json" \
  --disassembly-dir "$LOGS/disassembly"
echo "=== Recording the official QEMU launch arguments ==="
pbl qemu --dry-run > "$LOGS/launch-command.txt" 2>&1
echo "=== Generating QEMU micro/SPI flash targets explicitly ==="
# pbl build alone links pebbleos.elf but does not guarantee these images.
# The integration harness requires both targets before it can boot.
pbl build qemu_image_micro qemu_image_spi 2>&1 | tee "$LOGS/flash-images-build.log"
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
echo "=== Genuine PBW execution Gate A ==="
# The six original PBWs are private and absent from this repository. Do not
# substitute unrelated apps or label launcher smoke as a passing PBW test.
printf 'NOT RUN: the three original, hash-pinned PBW files are not in public CI.\nSource: time2/app/src/main/assets/starter-pbws/index.txt\nRequired: Iridescency II, Comic Watchface, Crush Em, each 60s rendering; worker probe; persistent SPI restart.\n' > "$LOGS/PBW-GATE-A-NOT-RUN.txt"
echo "=== Running PebbleOS integration smoke tests (does NOT satisfy Gate A) ==="
# PebbleOS integration harness starts and shuts down QEMU headless.
# Success is necessary for M0 but not proof that Android/ARM64 works.
timeout 900 pbl itest \
  ui/test_navigation.py::test_launcher_opens \
  ui/test_navigation.py::test_back_returns_home \
  ui/test_touch.py::test_swipe_scrolls_menu \
  ui/test_touch.py::test_tap_opens_item \
  -q --maxfail=1 2>&1 | tee "$LOGS/itest.log"
echo "Source-fixed Emery Linux smoke complete (PBW Gate A not yet verified)"
