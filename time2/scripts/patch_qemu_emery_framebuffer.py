#!/usr/bin/env python3
"""Export genuine Emery QEMU-rendered ARGB pixels to app-private v2 framebuffer.

Pinned upstream: coredevices/qemu @ 8a0a7c646fd80bb13d92bb5e2320427e224a2f6b
The patch FAILS CLOSED if the expected upstream source anchors change.
It does not modify or import the older Basalt-only QEMU patch.
"""
import pathlib
import sys

SOURCE = pathlib.Path("hw/display/pebble_display.c")
OSLIB = pathlib.Path("util/oslib-posix.c")
INCLUDE_MARKER = '#include "pebble_sunlight.h"\n'
FUNCTION_MARKER = "static void pbl_display_update(void *opaque)"
SURFACE_MARKER = "    dest = surface_data(surface);\n"
PIXEL_MARKER = "            /* Write pixel to host surface */\n"
END_MARKER = "    dpy_gfx_update(s->con, 0, 0, s->width, s->height);\n"

EXPORTER = r'''
/* NullWrist Time 2 independent Android Emery output ABI, v2. */
#if defined(__ANDROID__) || defined(NULLWRIST_EMERY_FB_EXPORT)
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdint.h>
#include <stdlib.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>
#define NULLWRIST_EMERY_WIDTH 200u
#define NULLWRIST_EMERY_HEIGHT 228u
#define NULLWRIST_EMERY_MAGIC 0x50424642u
#define NULLWRIST_EMERY_ABI_VERSION 2u
#define NULLWRIST_EMERY_FORMAT_ARGB8888 2u

typedef struct {
    uint32_t magic, version, width, height, stride, format, sequence;
    uint32_t reserved[9];
} NullWristEmeryHeader;
_Static_assert(sizeof(NullWristEmeryHeader) == 64, "frame header must be 64 bytes");
static void *nw_emery_map = MAP_FAILED;
static size_t nw_emery_size;
static int nw_emery_fd = -1;
static int nw_emery_signal_fd = -1;
static uint32_t nw_emery_sequence;

static bool nw_emery_begin(uint32_t w, uint32_t h)
{
    const char *path = getenv("PEBBLE_EMERY_FB_PATH");
    if (!path || !*path || w != NULLWRIST_EMERY_WIDTH
        || h != NULLWRIST_EMERY_HEIGHT) {
        return false;
    }
    if (nw_emery_map == MAP_FAILED) {
        size_t size = sizeof(NullWristEmeryHeader) + (size_t)w*h*4;
        int fd = open(path, O_CREAT | O_RDWR | O_TRUNC | O_CLOEXEC, 0600);
        if (fd < 0) return false;
        if (ftruncate(fd, (off_t)size) != 0) {
            close(fd);
            return false;
        }
        void *mapping = mmap(NULL, size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
        if (mapping == MAP_FAILED) {
            close(fd);
            return false;
        }
        nw_emery_map = mapping;
        nw_emery_size = size;
        nw_emery_fd = fd;
        NullWristEmeryHeader *hdr = (NullWristEmeryHeader *)nw_emery_map;
        memset(hdr, 0, sizeof(*hdr));
        hdr->magic = NULLWRIST_EMERY_MAGIC;
        hdr->version = NULLWRIST_EMERY_ABI_VERSION;
        hdr->width = w;
        hdr->height = h;
        hdr->stride = w;
        hdr->format = NULLWRIST_EMERY_FORMAT_ARGB8888;
        signal(SIGPIPE, SIG_IGN);
    }
    NullWristEmeryHeader *hdr = (NullWristEmeryHeader *)nw_emery_map;
    /* Sequence 1,3,5,... means native writer is currently painting. */
    nw_emery_sequence += 1u;
    __atomic_store_n(&hdr->sequence, nw_emery_sequence, __ATOMIC_RELEASE);
    return true;
}
static inline void nw_emery_pixel(uint32_t x, uint32_t y,
                                  uint8_t r, uint8_t g, uint8_t b)
{
    uint32_t *px = (uint32_t *)((uint8_t *)nw_emery_map
                                + sizeof(NullWristEmeryHeader));
    px[(size_t)y * NULLWRIST_EMERY_WIDTH + x] =
        UINT32_C(0xFF000000) | ((uint32_t)r << 16)
        | ((uint32_t)g << 8) | (uint32_t)b;
}
static void nw_emery_end(void)
{
    NullWristEmeryHeader *hdr = (NullWristEmeryHeader *)nw_emery_map;
    nw_emery_sequence += 1u;
    __atomic_store_n(&hdr->sequence, nw_emery_sequence, __ATOMIC_RELEASE);
    const char *fifo = getenv("PEBBLE_EMERY_FB_EVENT_PATH");
    if (!fifo || !*fifo) return;
    if (nw_emery_signal_fd < 0) {
        nw_emery_signal_fd = open(fifo, O_WRONLY | O_NONBLOCK | O_CLOEXEC);
        if (nw_emery_signal_fd < 0) return;
    }
    const uint8_t event = 1;
    if (write(nw_emery_signal_fd, &event, 1) < 0 &&
        errno != EAGAIN && errno != EWOULDBLOCK) {
        close(nw_emery_signal_fd);
        nw_emery_signal_fd = -1;
    }
}
#endif
'''

OSLIB_MARKER = "#include <termios.h>\n"
OSLIB_ANDROID_SHM = r'''
#ifdef __ANDROID__
/* Bionic lacks shm_open; QEMU's temporary shared page has no persistent name.
 * Use a deleted private TMPDIR file. Names are never exposed globally. */
static int shm_open(const char *name, int oflag, mode_t mode)
{
    (void)name;
    (void)oflag;
    const char *dir = getenv("TMPDIR");
    if (!dir || !*dir) return -1;
    char namebuf[512];
    int len = snprintf(namebuf, sizeof(namebuf), "%s/qemu-shm-XXXXXX", dir);
    if (len < 0 || (size_t)len >= sizeof(namebuf)) {
        errno = ENAMETOOLONG;
        return -1;
    }
    int fd = mkstemp(namebuf);
    if (fd < 0) return -1;
    unlink(namebuf);
    if (fchmod(fd, mode) != 0) {
        int olderr = errno;
        close(fd);
        errno = olderr;
        return -1;
    }
    int flags = fcntl(fd, F_GETFD);
    if (flags >= 0) fcntl(fd, F_SETFD, flags | FD_CLOEXEC);
    return fd;
}
static int shm_unlink(const char *name)
{
    (void)name;
    return 0;
}
#endif
'''


# -display none creates no GUI refresh timer; the generic display redraw
# method is ordinarily only called by QEMU's graphical frontend or screendump.
# Supply a private 12.5 Hz timer when Time2 exports the framebuffer, avoiding
# dozens of disk-bound QMP screendump requests per minute.
TIMER_FIELD_MARKER = "    QEMUTimer *vibe_timer;\n};\n"
TIMER_FUNC_MARKER = "static void pbl_display_realize(DeviceState *dev, Error **errp)\n"
TIMER_REALIZE_MARKER = "    qemu_console_resize(s->con, s->width, s->height);\n"
TIMER_FIELD_PATCH = r'''
#if defined(__ANDROID__) || defined(NULLWRIST_EMERY_FB_EXPORT)
    QEMUTimer *nw_headless_timer;
#endif
'''
TIMER_FUNCTION_PATCH = r'''
#if defined(__ANDROID__) || defined(NULLWRIST_EMERY_FB_EXPORT)
#define NULLWRIST_EMERY_REFRESH_MS 80
static void nw_emery_headless_refresh(void *opaque)
{
    PblDisplay *s = opaque;
    if (s->con && (s->ctrl & CTRL_ENABLE)) {
        graphic_hw_update(s->con);
    }
    timer_mod(s->nw_headless_timer,
              qemu_clock_get_ms(QEMU_CLOCK_VIRTUAL) + NULLWRIST_EMERY_REFRESH_MS);
}
#endif
'''
TIMER_INIT_PATCH = r'''
#if defined(__ANDROID__) || defined(NULLWRIST_EMERY_FB_EXPORT)
    if (getenv("PEBBLE_EMERY_FB_PATH")) {
        s->nw_headless_timer = timer_new_ms(QEMU_CLOCK_VIRTUAL,
                                            nw_emery_headless_refresh, s);
        timer_mod(s->nw_headless_timer,
                  qemu_clock_get_ms(QEMU_CLOCK_VIRTUAL) + NULLWRIST_EMERY_REFRESH_MS);
    }
#endif
'''

def patch(root: pathlib.Path) -> None:
    file = root / SOURCE
    text = file.read_text(encoding="utf-8")
    if "NULLWRIST_EMERY_ABI_VERSION" in text:
        print("Emery exporter already patched")
    else:
        markers = [INCLUDE_MARKER, FUNCTION_MARKER, SURFACE_MARKER,
                   PIXEL_MARKER, END_MARKER]
        if any(text.count(marker) != 1 for marker in markers):
            raise RuntimeError("QEMU display layout changed; exporter patch refused")
        text = text.replace(INCLUDE_MARKER, INCLUDE_MARKER + EXPORTER, 1)
        text = text.replace(SURFACE_MARKER,
                            SURFACE_MARKER +
                            "#if defined(__ANDROID__) || defined(NULLWRIST_EMERY_FB_EXPORT)\n"
                            "    bool nw_publishing = nw_emery_begin(s->width, s->height);\n"
                            "#endif\n", 1)
        text = text.replace(PIXEL_MARKER,
                            "#if defined(__ANDROID__) || defined(NULLWRIST_EMERY_FB_EXPORT)\n"
                            "            if (nw_publishing) nw_emery_pixel(x, y, r, g, b);\n"
                            "#endif\n" + PIXEL_MARKER, 1)
        text = text.replace(END_MARKER, END_MARKER +
                            "#if defined(__ANDROID__) || defined(NULLWRIST_EMERY_FB_EXPORT)\n"
                            "    if (nw_publishing) nw_emery_end();\n"
                            "#endif\n", 1)
        # Headless refresh support is part of the same pinned-source patch.
        timer_anchors = [TIMER_FIELD_MARKER, TIMER_FUNC_MARKER, TIMER_REALIZE_MARKER]
        if any(text.count(anchor) != 1 for anchor in timer_anchors):
            raise RuntimeError("QEMU display timer anchors changed; patch refused")
        text = text.replace(TIMER_FIELD_MARKER,
                            TIMER_FIELD_MARKER.replace("};\n", TIMER_FIELD_PATCH + "};\n"), 1)
        text = text.replace(TIMER_FUNC_MARKER,
                            TIMER_FUNCTION_PATCH + TIMER_FUNC_MARKER, 1)
        text = text.replace(TIMER_REALIZE_MARKER,
                            TIMER_REALIZE_MARKER + TIMER_INIT_PATCH, 1)
        file.write_text(text, encoding="utf-8")
    osfile = root / OSLIB
    oslib = osfile.read_text(encoding="utf-8")
    if "Bionic lacks shm_open" not in oslib:
        if oslib.count(OSLIB_MARKER) != 1:
            raise RuntimeError("QEMU oslib changed; Android shm patch refused")
        osfile.write_text(oslib.replace(OSLIB_MARKER,
                         OSLIB_MARKER + OSLIB_ANDROID_SHM, 1), encoding="utf-8")
    print("Verified isolated Emery framebuffer + bionic shim patches")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: patch_qemu_emery_framebuffer.py QEMU_ROOT")
    patch(pathlib.Path(sys.argv[1]).resolve())
