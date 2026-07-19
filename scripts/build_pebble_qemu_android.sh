#!/usr/bin/env bash
# Cross-build Core Devices Pebble QEMU for Android arm64.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="${QEMU_ANDROID_WORK_DIR:-$ROOT_DIR/.native-qemu-build}"
OUTPUT_DIR="${QEMU_ANDROID_OUTPUT_DIR:-$ROOT_DIR/native-qemu-output}"
ANDROID_API="${ANDROID_API:-28}"
QEMU_COMMIT="${QEMU_COMMIT:-9ef633fa512105d8bcf16b93322f99660ce9d4ab}"
NDK_ROOT="${ANDROID_NDK_ROOT:-${ANDROID_NDK_HOME:-}}"

if [[ -z "$NDK_ROOT" || ! -d "$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64" ]]; then
  echo "ANDROID_NDK_ROOT or ANDROID_NDK_HOME must point to an installed Android NDK" >&2
  exit 2
fi

TOOLCHAIN="$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64"
SYSROOT="$TOOLCHAIN/sysroot"
TRIPLE="aarch64-linux-android"
CC="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang"
CXX="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang++"
AR="$TOOLCHAIN/bin/llvm-ar"
RANLIB="$TOOLCHAIN/bin/llvm-ranlib"
STRIP="$TOOLCHAIN/bin/llvm-strip"
NM="$TOOLCHAIN/bin/llvm-nm"
READELF="$TOOLCHAIN/bin/llvm-readelf"
PREFIX="$WORK_DIR/prefix"
SOURCES="$WORK_DIR/sources"
BUILDS="$WORK_DIR/builds"
WRAPPERS="$WORK_DIR/toolchain-bin"
JOBS="${JOBS:-$(nproc)}"

mkdir -p "$SOURCES" "$BUILDS" "$PREFIX" "$WRAPPERS" "$OUTPUT_DIR"

for tool in python3 git curl cmake meson ninja pkg-config make; do
  command -v "$tool" >/dev/null || { echo "Missing build tool: $tool" >&2; exit 2; }
done

ln -sf "$CC" "$WRAPPERS/${TRIPLE}-gcc"
ln -sf "$CXX" "$WRAPPERS/${TRIPLE}-g++"
ln -sf "$CC" "$WRAPPERS/${TRIPLE}-cc"
ln -sf "$CXX" "$WRAPPERS/${TRIPLE}-c++"
ln -sf "$AR" "$WRAPPERS/${TRIPLE}-ar"
ln -sf "$RANLIB" "$WRAPPERS/${TRIPLE}-ranlib"
ln -sf "$STRIP" "$WRAPPERS/${TRIPLE}-strip"
ln -sf "$NM" "$WRAPPERS/${TRIPLE}-nm"

export PATH="$WRAPPERS:$TOOLCHAIN/bin:$PATH"
export PKG_CONFIG_PATH="$PREFIX/lib/pkgconfig:$PREFIX/share/pkgconfig"
export PKG_CONFIG_LIBDIR="$PKG_CONFIG_PATH"
export CFLAGS="-O2 -fPIC -ffunction-sections -fdata-sections"
export CXXFLAGS="$CFLAGS"
export CPPFLAGS="-I$PREFIX/include"
export LDFLAGS="-L$PREFIX/lib -Wl,--gc-sections -Wl,-z,max-page-size=16384 -llog -ldl -lm"

fetch_tar() {
  local url="$1"
  local archive="$2"
  local directory="$3"
  if [[ ! -d "$SOURCES/$directory" ]]; then
    echo "Downloading $url"
    curl --fail --location --retry 3 "$url" -o "$WORK_DIR/$archive"
    tar -xf "$WORK_DIR/$archive" -C "$SOURCES"
  fi
}

write_cross_file() {
  cat > "$WORK_DIR/android-arm64.ini" <<EOF
[binaries]
c = '$CC'
cpp = '$CXX'
ar = '$AR'
strip = '$STRIP'
nm = '$NM'
pkg-config = 'pkg-config'

[host_machine]
system = 'android'
cpu_family = 'aarch64'
cpu = 'armv8-a'
endian = 'little'

[properties]
needs_exe_wrapper = true
sys_root = '$SYSROOT'

[built-in options]
c_args = ['-O2', '-fPIC', '-ffunction-sections', '-fdata-sections', '-I$PREFIX/include']
cpp_args = ['-O2', '-fPIC', '-ffunction-sections', '-fdata-sections', '-I$PREFIX/include']
c_link_args = ['-L$PREFIX/lib', '-Wl,--gc-sections', '-Wl,-z,max-page-size=16384', '-llog', '-ldl', '-lm']
cpp_link_args = ['-L$PREFIX/lib', '-Wl,--gc-sections', '-Wl,-z,max-page-size=16384', '-llog', '-ldl', '-lm']
EOF
}

build_zlib() {
  fetch_tar "https://github.com/madler/zlib/releases/download/v1.3.1/zlib-1.3.1.tar.gz" "zlib-1.3.1.tar.gz" "zlib-1.3.1"
  local build="$BUILDS/zlib"
  if [[ ! -f "$PREFIX/lib/libz.a" ]]; then
    rm -rf "$build"
    cmake -S "$SOURCES/zlib-1.3.1" -B "$build" -G Ninja \
      -DCMAKE_TOOLCHAIN_FILE="$NDK_ROOT/build/cmake/android.toolchain.cmake" \
      -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM="android-$ANDROID_API" \
      -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX="$PREFIX" \
      -DBUILD_SHARED_LIBS=OFF
    cmake --build "$build" --parallel "$JOBS"
    cmake --install "$build"
  fi
}

build_libffi() {
  fetch_tar "https://github.com/libffi/libffi/releases/download/v3.4.8/libffi-3.4.8.tar.gz" "libffi-3.4.8.tar.gz" "libffi-3.4.8"
  local build="$BUILDS/libffi"
  if [[ ! -f "$PREFIX/lib/libffi.a" ]]; then
    rm -rf "$build" && mkdir -p "$build"
    pushd "$build" >/dev/null
    CC="$CC" CXX="$CXX" AR="$AR" RANLIB="$RANLIB" STRIP="$STRIP" \
      "$SOURCES/libffi-3.4.8/configure" \
      --host="$TRIPLE" --prefix="$PREFIX" --disable-shared --enable-static \
      --disable-docs
    make -j"$JOBS"
    make install
    popd >/dev/null
  fi
}

build_pcre2() {
  fetch_tar "https://github.com/PCRE2Project/pcre2/releases/download/pcre2-10.46/pcre2-10.46.tar.bz2" "pcre2-10.46.tar.bz2" "pcre2-10.46"
  local build="$BUILDS/pcre2"
  if [[ ! -f "$PREFIX/lib/libpcre2-8.a" ]]; then
    rm -rf "$build"
    cmake -S "$SOURCES/pcre2-10.46" -B "$build" -G Ninja \
      -DCMAKE_TOOLCHAIN_FILE="$NDK_ROOT/build/cmake/android.toolchain.cmake" \
      -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM="android-$ANDROID_API" \
      -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX="$PREFIX" \
      -DBUILD_SHARED_LIBS=OFF -DPCRE2_BUILD_PCRE2_8=ON \
      -DPCRE2_BUILD_PCRE2_16=OFF -DPCRE2_BUILD_PCRE2_32=OFF \
      -DPCRE2_BUILD_TESTS=OFF -DPCRE2_BUILD_PCRE2GREP=OFF \
      -DPCRE2_SUPPORT_UNICODE=ON
    cmake --build "$build" --parallel "$JOBS"
    cmake --install "$build"
  fi
}

build_glib() {
  fetch_tar "https://download.gnome.org/sources/glib/2.82/glib-2.82.5.tar.xz" "glib-2.82.5.tar.xz" "glib-2.82.5"
  local build="$BUILDS/glib"
  if [[ ! -f "$PREFIX/lib/libglib-2.0.a" ]]; then
    rm -rf "$build"
    meson setup "$build" "$SOURCES/glib-2.82.5" \
      --cross-file "$WORK_DIR/android-arm64.ini" \
      --prefix "$PREFIX" --buildtype release --default-library static \
      -Dtests=false -Dinstalled_tests=false -Dintrospection=disabled \
      -Ddocumentation=false -Dman-pages=disabled -Dnls=disabled \
      -Dlibmount=disabled -Dselinux=disabled -Dsysprof=disabled \
      -Ddtrace=disabled -Dsystemtap=disabled -Dxattr=false
    meson compile -C "$build" -j "$JOBS"
    meson install -C "$build"
  fi
}

build_pixman() {
  fetch_tar "https://cairographics.org/releases/pixman-0.44.2.tar.gz" "pixman-0.44.2.tar.gz" "pixman-0.44.2"
  local build="$BUILDS/pixman"
  if [[ ! -f "$PREFIX/lib/libpixman-1.a" ]]; then
    rm -rf "$build"
    meson setup "$build" "$SOURCES/pixman-0.44.2" \
      --cross-file "$WORK_DIR/android-arm64.ini" \
      --prefix "$PREFIX" --buildtype release --default-library static \
      -Dtests=disabled -Ddemos=disabled -Dgtk=disabled -Dlibpng=disabled \
      -Dopenmp=disabled
    meson compile -C "$build" -j "$JOBS"
    meson install -C "$build"
  fi
}

checkout_qemu() {
  local qemu="$SOURCES/qemu-pebble"
  if [[ ! -d "$qemu/.git" ]]; then
    git clone --filter=blob:none --no-checkout https://github.com/coredevices/qemu.git "$qemu"
  fi
  git -C "$qemu" fetch --depth 1 origin "$QEMU_COMMIT"
  git -C "$qemu" checkout --detach --force FETCH_HEAD
  git -C "$qemu" clean -ffdqx
  python3 "$ROOT_DIR/scripts/patch_qemu_framebuffer.py" "$qemu"
  python3 "$ROOT_DIR/scripts/patch_qemu_basalt_only.py" "$qemu"
}

build_qemu() {
  checkout_qemu
  local qemu="$SOURCES/qemu-pebble"
  local build="$BUILDS/qemu"
  rm -rf "$build"
  mkdir -p "$build"

  pushd "$build" >/dev/null
  PKG_CONFIG="pkg-config --static" \
  "$qemu/configure" \
    --prefix="$PREFIX" \
    --target-list=arm-softmmu \
    --cross-prefix="$WRAPPERS/${TRIPLE}-" \
    --cc="$CC" --cxx="$CXX" --host-cc=gcc \
    --python=python3 \
    --enable-tcg-interpreter \
    --enable-fdt=internal \
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
    --disable-vhost-user-blk-server --disable-vhost-vdpa \
    --extra-cflags="$CFLAGS $CPPFLAGS" \
    --extra-ldflags="$LDFLAGS"
  ninja -j"$JOBS" qemu-system-arm
  popd >/dev/null

  cp "$build/qemu-system-arm" "$OUTPUT_DIR/libpebble_qemu_exec.so"
  chmod 0755 "$OUTPUT_DIR/libpebble_qemu_exec.so"
  "$STRIP" --strip-unneeded "$OUTPUT_DIR/libpebble_qemu_exec.so"
  file "$OUTPUT_DIR/libpebble_qemu_exec.so"
  "$READELF" -h "$OUTPUT_DIR/libpebble_qemu_exec.so"
  "$READELF" -d "$OUTPUT_DIR/libpebble_qemu_exec.so" || true
  echo "Registered Pebble machine strings retained in binary:"
  strings "$OUTPUT_DIR/libpebble_qemu_exec.so" \
    | grep -E '^pebble-[a-z0-9-]+$' \
    | sort -u || true
  sha256sum "$OUTPUT_DIR/libpebble_qemu_exec.so" > "$OUTPUT_DIR/libpebble_qemu_exec.so.sha256"
}

write_cross_file
build_zlib
build_libffi
build_pcre2
build_glib
build_pixman
build_qemu

echo "Pebble QEMU Android build complete: $OUTPUT_DIR/libpebble_qemu_exec.so"
