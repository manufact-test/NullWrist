#!/usr/bin/env python3
"""Patch Core Devices QEMU for Android execution and framebuffer export."""
from __future__ import annotations

import pathlib
import sys

DISPLAY_SOURCE = pathlib.Path("hw/display/pebble_snowy_display.c")
OSLIB_SOURCE = pathlib.Path("util/oslib-posix.c")

DISPLAY_INCLUDE_MARKER = '#include "pebble_snowy_display_overlays.h"\n'
DISPLAY_INCLUDE_BLOCK = r'''
#include "qemu/error-report.h"

#ifdef __ANDROID__
#include <errno.h>
#include <fcntl.h>
#include <stdint.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

#define PEBBLE_ANDROID_FB_MAGIC 0x50424642u /* PBFB */
#define PEBBLE_ANDROID_FB_VERSION 1u
#define PEBBLE_ANDROID_FB_FORMAT_COLOR_2BIT 1u

typedef struct {
    uint32_t magic;
    uint32_t version;
    uint32_t width;
    uint32_t height;
    uint32_t stride;
    uint32_t pixel_format;
    uint32_t sequence;
    uint32_t reserved[9];
} PebbleAndroidFramebufferHeader;

static int s_android_fb_fd = -1;
static void *s_android_fb_mapping = MAP_FAILED;
static size_t s_android_fb_mapping_size = 0;
static uint32_t s_android_fb_width = 0;
static uint32_t s_android_fb_height = 0;

static void pebble_android_fb_close(void)
{
    if (s_android_fb_mapping != MAP_FAILED) {
        munmap(s_android_fb_mapping, s_android_fb_mapping_size);
        s_android_fb_mapping = MAP_FAILED;
    }
    if (s_android_fb_fd >= 0) {
        close(s_android_fb_fd);
        s_android_fb_fd = -1;
    }
    s_android_fb_mapping_size = 0;
    s_android_fb_width = 0;
    s_android_fb_height = 0;
}

static bool pebble_android_fb_open(uint32_t width, uint32_t height)
{
    const char *path = getenv("PEBBLE_FB_PATH");
    if (!path || !path[0]) {
        return false;
    }

    if (s_android_fb_mapping != MAP_FAILED &&
        s_android_fb_width == width && s_android_fb_height == height) {
        return true;
    }

    pebble_android_fb_close();

    const size_t pixel_bytes = (size_t)width * height;
    const size_t mapping_size = sizeof(PebbleAndroidFramebufferHeader) + pixel_bytes;
    const int fd = open(path, O_RDWR | O_CREAT | O_TRUNC | O_CLOEXEC, 0600);
    if (fd < 0) {
        error_report("Pebble framebuffer open failed for %s: %s", path, strerror(errno));
        return false;
    }
    if (ftruncate(fd, (off_t)mapping_size) != 0) {
        error_report("Pebble framebuffer resize failed: %s", strerror(errno));
        close(fd);
        return false;
    }

    void *mapping = mmap(NULL, mapping_size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    if (mapping == MAP_FAILED) {
        error_report("Pebble framebuffer mmap failed: %s", strerror(errno));
        close(fd);
        return false;
    }

    s_android_fb_fd = fd;
    s_android_fb_mapping = mapping;
    s_android_fb_mapping_size = mapping_size;
    s_android_fb_width = width;
    s_android_fb_height = height;

    PebbleAndroidFramebufferHeader *header = mapping;
    memset(header, 0, sizeof(*header));
    header->magic = PEBBLE_ANDROID_FB_MAGIC;
    header->version = PEBBLE_ANDROID_FB_VERSION;
    header->width = width;
    header->height = height;
    header->stride = width;
    header->pixel_format = PEBBLE_ANDROID_FB_FORMAT_COLOR_2BIT;
    __atomic_store_n(&header->sequence, 0u, __ATOMIC_RELEASE);
    return true;
}

static void pebble_android_fb_publish(const uint8_t *source,
                                      uint32_t source_stride,
                                      uint32_t border_x,
                                      uint32_t border_y,
                                      uint32_t width,
                                      uint32_t height)
{
    if (!pebble_android_fb_open(width, height)) {
        return;
    }

    PebbleAndroidFramebufferHeader *header = s_android_fb_mapping;
    uint8_t *destination = (uint8_t *)(header + 1);
    for (uint32_t y = 0; y < height; ++y) {
        const uint8_t *source_row = source + (y + border_y) * source_stride + border_x;
        memcpy(destination + (size_t)y * width, source_row, width);
    }

    const uint32_t next = __atomic_load_n(&header->sequence, __ATOMIC_RELAXED) + 1u;
    __atomic_store_n(&header->sequence, next, __ATOMIC_RELEASE);
}
#endif
'''

REDRAW_OLD = '''static void ps_set_redraw(PSDisplayGlobals *s) {
    s->redraw = true;
    memmove(s->framebuffer_copy, s->framebuffer, s->bytes_per_frame);
}
'''

REDRAW_NEW = '''static void ps_set_redraw(PSDisplayGlobals *s) {
    s->redraw = true;
    memmove(s->framebuffer_copy, s->framebuffer, s->bytes_per_frame);
#ifdef __ANDROID__
    const uint32_t visible_width = s->num_cols - 2 * s->num_border_cols;
    const uint32_t visible_height = s->num_rows - 2 * s->num_border_rows;
    pebble_android_fb_publish(
            s->framebuffer_copy,
            s->bytes_per_row,
            s->num_border_cols,
            s->num_border_rows,
            visible_width,
            visible_height);
#endif
}
'''

OSLIB_INCLUDE_MARKER = "#include <termios.h>\n"
OSLIB_ANDROID_SHM = r'''

#ifdef __ANDROID__
/* Android's bionic libc does not expose POSIX shm_open()/shm_unlink().
 * QEMU only needs an anonymous shared file descriptor here, so back it with
 * an unlinked temporary file in the app-provided TMPDIR. */
static int shm_open(const char *name, int oflag, mode_t mode)
{
    (void)name;
    (void)oflag;
    const char *tmpdir = getenv("TMPDIR");
    if (!tmpdir || !tmpdir[0]) {
        tmpdir = "/data/local/tmp";
    }

    char path[512];
    int written = snprintf(path, sizeof(path), "%s/qemu-shm-XXXXXX", tmpdir);
    if (written < 0 || (size_t)written >= sizeof(path)) {
        errno = ENAMETOOLONG;
        return -1;
    }

    int fd = mkstemp(path);
    if (fd < 0) {
        return -1;
    }
    unlink(path);
    if (fchmod(fd, mode) != 0) {
        int saved_errno = errno;
        close(fd);
        errno = saved_errno;
        return -1;
    }
    int descriptor_flags = fcntl(fd, F_GETFD);
    if (descriptor_flags >= 0) {
        fcntl(fd, F_SETFD, descriptor_flags | FD_CLOEXEC);
    }
    return fd;
}

static int shm_unlink(const char *name)
{
    (void)name;
    return 0;
}
#endif
'''


def patch_display(root: pathlib.Path) -> None:
    source_path = root / DISPLAY_SOURCE
    text = source_path.read_text(encoding="utf-8")

    if "PEBBLE_ANDROID_FB_MAGIC" not in text:
        if DISPLAY_INCLUDE_MARKER not in text:
            raise SystemExit(f"include marker not found in {source_path}")
        text = text.replace(
            DISPLAY_INCLUDE_MARKER,
            DISPLAY_INCLUDE_MARKER + DISPLAY_INCLUDE_BLOCK,
            1,
        )

    if "pebble_android_fb_publish(" not in text[text.find("static void ps_set_redraw"):]:
        if REDRAW_OLD not in text:
            raise SystemExit(f"redraw marker not found in {source_path}")
        text = text.replace(REDRAW_OLD, REDRAW_NEW, 1)

    source_path.write_text(text, encoding="utf-8")
    print(f"Patched {source_path}")


def patch_android_shm(root: pathlib.Path) -> None:
    source_path = root / OSLIB_SOURCE
    text = source_path.read_text(encoding="utf-8")
    if "Android's bionic libc does not expose POSIX shm_open" not in text:
        if OSLIB_INCLUDE_MARKER not in text:
            raise SystemExit(f"termios include marker not found in {source_path}")
        text = text.replace(
            OSLIB_INCLUDE_MARKER,
            OSLIB_INCLUDE_MARKER + OSLIB_ANDROID_SHM,
            1,
        )
        source_path.write_text(text, encoding="utf-8")
        print(f"Patched {source_path}")


def main() -> int:
    root = pathlib.Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else pathlib.Path.cwd()
    patch_display(root)
    patch_android_shm(root)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
