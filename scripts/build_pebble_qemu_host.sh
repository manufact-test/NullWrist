#!/usr/bin/env bash
# Build a headless host QEMU used to pre-seed Basalt flash and render thumbnails in CI.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="${QEMU_HOST_WORK_DIR:-$ROOT_DIR/.host-qemu-build}"
OUTPUT_DIR="${QEMU_HOST_OUTPUT_DIR:-$ROOT_DIR/host-qemu-output}"
QEMU_COMMIT="${QEMU_COMMIT:-9ef633fa512105d8bcf16b93322f99660ce9d4ab}"
JOBS="${JOBS:-$(nproc)}"
SOURCES="$WORK_DIR/sources"
BUILD="$WORK_DIR/build"

mkdir -p "$SOURCES" "$OUTPUT_DIR"

if [[ ! -d "$SOURCES/qemu-pebble/.git" ]]; then
  git clone --filter=blob:none --no-checkout https://github.com/coredevices/qemu.git "$SOURCES/qemu-pebble"
fi

git -C "$SOURCES/qemu-pebble" fetch --depth 1 origin "$QEMU_COMMIT"
git -C "$SOURCES/qemu-pebble" checkout --detach --force FETCH_HEAD
git -C "$SOURCES/qemu-pebble" clean -ffdqx
python3 "$ROOT_DIR/scripts/patch_qemu_basalt_only.py" "$SOURCES/qemu-pebble"
python3 "$ROOT_DIR/scripts/patch_qemu_framebuffer.py" "$SOURCES/qemu-pebble"

rm -rf "$BUILD"
mkdir -p "$BUILD"
pushd "$BUILD" >/dev/null
"$SOURCES/qemu-pebble/configure" \
  --target-list=arm-softmmu \
  --enable-fdt=internal \
  --extra-cflags=-DPEBBLE_REAR_FB_EXPORT \
  --disable-werror --disable-docs --disable-tools --disable-guest-agent \
  --disable-modules --disable-plugins --disable-debug-info \
  --disable-sdl --disable-sdl-image --disable-gtk --disable-vte \
  --disable-vnc --disable-curses --disable-opengl --disable-virglrenderer \
  --disable-spice --disable-slirp --disable-curl --disable-gnutls \
  --disable-nettle --disable-gcrypt --disable-libssh --disable-libnfs \
  --disable-libusb --disable-usb-redir --disable-zstd --disable-lzo \
  --disable-snappy --disable-bzip2 --disable-seccomp --disable-kvm \
  --disable-xen --disable-hvf --disable-whpx --disable-linux-aio \
  --disable-linux-io-uring --disable-virtfs --disable-vhost-user \
  --disable-vhost-user-blk-server --disable-vhost-vdpa
ninja -j"$JOBS" qemu-system-arm
popd >/dev/null

cp "$BUILD/qemu-system-arm" "$OUTPUT_DIR/qemu-system-arm"
chmod 0755 "$OUTPUT_DIR/qemu-system-arm"
strip --strip-unneeded "$OUTPUT_DIR/qemu-system-arm" || true
"$OUTPUT_DIR/qemu-system-arm" -machine help | tee "$OUTPUT_DIR/machine-help.txt"
grep -q "pebble-snowy-bb" "$OUTPUT_DIR/machine-help.txt"
if grep -E "pebble-(silk|s4|cutts|robert|emery|chalk|diorite|flint|gabbro)" "$OUTPUT_DIR/machine-help.txt"; then
  echo "Non-Basalt Pebble machine is still registered" >&2
  exit 1
fi
sha256sum "$OUTPUT_DIR/qemu-system-arm" > "$OUTPUT_DIR/qemu-system-arm.sha256"
echo "Host Pebble Time QEMU ready: $OUTPUT_DIR/qemu-system-arm"
