#!/usr/bin/env python3
"""Run the production Java Pebble protocol and installer against host Pebble QEMU."""
from __future__ import annotations

import argparse
import base64
import shutil
import subprocess
import tempfile
from pathlib import Path

from preseed_basalt_flash import load_pbw, unused_port, wait_for_firmware


STUB_BUNDLE = r'''package com.manufacttest.pebblereardisplay.runtime;

public final class PebblePbwBundle {
    private final AppHeader header;
    private final byte[] application;
    private final byte[] resources;
    private final byte[] worker;

    public PebblePbwBundle(AppHeader header, byte[] application, byte[] resources, byte[] worker) {
        this.header = header;
        this.application = application;
        this.resources = resources;
        this.worker = worker;
    }

    public AppHeader getHeader() { return header; }
    public byte[] getApplication() { return application; }
    public byte[] getResources() { return resources; }
    public byte[] getWorker() { return worker; }
    public int getTotalTransferBytes() {
        return application.length + (resources == null ? 0 : resources.length)
                + (worker == null ? 0 : worker.length);
    }

    public static final class AppHeader {
        private final int sdkMajor;
        private final int sdkMinor;
        private final int appMajor;
        private final int appMinor;
        private final int icon;
        private final int flags;
        private final String name;
        private final java.util.UUID uuid;

        public AppHeader(int sdkMajor, int sdkMinor, int appMajor, int appMinor,
                         int icon, int flags, String name, java.util.UUID uuid) {
            this.sdkMajor = sdkMajor;
            this.sdkMinor = sdkMinor;
            this.appMajor = appMajor;
            this.appMinor = appMinor;
            this.icon = icon;
            this.flags = flags;
            this.name = name;
            this.uuid = uuid;
        }

        public int getSdkVersionMajor() { return sdkMajor; }
        public int getSdkVersionMinor() { return sdkMinor; }
        public int getAppVersionMajor() { return appMajor; }
        public int getAppVersionMinor() { return appMinor; }
        public int getIconResourceId() { return icon; }
        public int getFlags() { return flags; }
        public String getAppName() { return name; }
        public java.util.UUID getUuid() { return uuid; }
    }
}
'''

HARNESS = r'''package com.manufacttest.pebblereardisplay.runtime;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

public final class JavaInstallerSmoke {
    public static void main(String[] args) throws Exception {
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        Path manifest = Path.of(args[2]);
        try (PebbleProtocolLink link = PebbleProtocolLink.connect(host, port, 30_000)) {
            List<String> lines = Files.readAllLines(manifest, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.isBlank()) continue;
                String[] v = line.split("\\t", -1);
                String name = new String(Base64.getDecoder().decode(v[7]), StandardCharsets.UTF_8);
                PebblePbwBundle.AppHeader header = new PebblePbwBundle.AppHeader(
                        Integer.parseInt(v[1]), Integer.parseInt(v[2]),
                        Integer.parseInt(v[3]), Integer.parseInt(v[4]),
                        Integer.parseUnsignedInt(v[5]), Integer.parseUnsignedInt(v[6]),
                        name, UUID.fromString(v[0])
                );
                byte[] app = Files.readAllBytes(Path.of(v[8]));
                byte[] res = v[9].equals("-") ? null : Files.readAllBytes(Path.of(v[9]));
                byte[] worker = v[10].equals("-") ? null : Files.readAllBytes(Path.of(v[10]));
                PebblePbwBundle bundle = new PebblePbwBundle(header, app, res, worker);
                System.out.println("JAVA IMPORT START " + name + " " + header.getUuid());
                new PebbleAppInstaller(link, (message, sent, total) -> {
                    if (sent == 0 || sent == total || sent % 50000 < 1000) {
                        System.out.println(message + " " + sent + "/" + total);
                    }
                }).install(bundle);
                System.out.println("JAVA IMPORT ACTIVE " + name + " " + header.getUuid());
            }
        }
    }
}
'''


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--qemu", type=Path, required=True)
    parser.add_argument("--micro", type=Path, required=True)
    parser.add_argument("--base-spi", type=Path, required=True)
    parser.add_argument("--watchfaces", type=Path, required=True)
    parser.add_argument("--log", type=Path, required=True)
    return parser.parse_args()


def choose_bundles(watchfaces: Path):
    bundles = [load_pbw(path) for path in sorted(watchfaces.glob("*.pbw"))]
    if not bundles:
        raise RuntimeError("No PBWs available for Java smoke test")
    chosen = []
    for predicate in (
        lambda b: b.worker is not None,
        lambda b: b.worker is None and b.resources is not None,
        lambda b: True,
    ):
        matches = [b for b in bundles if predicate(b) and b not in chosen]
        if matches:
            chosen.append(max(matches, key=lambda b: len(b.application) + len(b.resources or b"") + len(b.worker or b"")))
    return chosen[:3]


def main() -> int:
    args = parse_args()
    args.log.parent.mkdir(parents=True, exist_ok=True)
    bundles = choose_bundles(args.watchfaces)

    with tempfile.TemporaryDirectory(prefix="pebble-java-smoke-") as temporary:
        root = Path(temporary)
        spi = root / "smoke-spi.bin"
        shutil.copyfile(args.base_spi, spi)

        source_root = root / "src" / "com" / "manufacttest" / "pebblereardisplay" / "runtime"
        source_root.mkdir(parents=True)
        (source_root / "PebblePbwBundle.java").write_text(STUB_BUNDLE, encoding="utf-8")
        (source_root / "JavaInstallerSmoke.java").write_text(HARNESS, encoding="utf-8")

        manifest_lines = []
        for index, bundle in enumerate(bundles):
            app_path = root / f"{index}-app.bin"
            app_path.write_bytes(bundle.application)
            res_path = root / f"{index}-res.bin" if bundle.resources is not None else None
            worker_path = root / f"{index}-worker.bin" if bundle.worker is not None else None
            if res_path is not None:
                res_path.write_bytes(bundle.resources)
            if worker_path is not None:
                worker_path.write_bytes(bundle.worker)
            h = bundle.header
            manifest_lines.append("\t".join([
                str(h.app_uuid), str(h.sdk_major), str(h.sdk_minor),
                str(h.app_major), str(h.app_minor), str(h.icon_resource_id), str(h.flags),
                base64.b64encode(h.app_name.encode("utf-8")).decode("ascii"),
                str(app_path), str(res_path) if res_path else "-",
                str(worker_path) if worker_path else "-",
            ]))
        manifest = root / "bundles.tsv"
        manifest.write_text("\n".join(manifest_lines) + "\n", encoding="utf-8")

        classes = root / "classes"
        classes.mkdir()
        subprocess.run([
            "javac", "-encoding", "UTF-8", "-d", str(classes),
            "app/src/main/java/com/manufacttest/pebblereardisplay/runtime/PebbleProtocolLink.java",
            "app/src/main/java/com/manufacttest/pebblereardisplay/runtime/PebbleAppInstaller.java",
            str(source_root / "PebblePbwBundle.java"),
            str(source_root / "JavaInstallerSmoke.java"),
        ], check=True)

        protocol_port = unused_port()
        console_port = unused_port()
        command = [
            str(args.qemu), "-rtc", "base=localtime",
            "-serial", "null",
            "-serial", f"tcp::{protocol_port},server=on,wait=off,nodelay=on",
            "-serial", f"tcp::{console_port},server=on,wait=on,nodelay=on",
            "-kernel", str(args.micro), "-monitor", "none", "-display", "none",
            "-machine", "pebble-snowy-bb", "-cpu", "cortex-m4",
            "-drive", f"if=none,id=spi-flash,file={spi},format=raw", "-no-reboot",
        ]
        with args.log.open("wb") as qemu_log:
            process = subprocess.Popen(command, stdout=qemu_log, stderr=subprocess.STDOUT)
            try:
                wait_for_firmware(console_port, process)
                subprocess.run([
                    "java", "-cp", str(classes),
                    "com.manufacttest.pebblereardisplay.runtime.JavaInstallerSmoke",
                    "127.0.0.1", str(protocol_port), str(manifest),
                ], check=True, timeout=180)
            finally:
                process.terminate()
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=5)

    print("Java production installer imported:")
    for bundle in bundles:
        print(f"  {bundle.header.app_name} {bundle.header.app_uuid}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
