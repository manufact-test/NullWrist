from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEXT_SUFFIXES = {
    ".c", ".cc", ".cmake", ".cpp", ".h", ".hpp", ".java", ".json", ".kt", ".kts",
    ".md", ".properties", ".py", ".sh", ".txt", ".xml", ".yaml", ".yml",
}
SKIP_DIRS = {".git", ".gradle", "build", ".idea"}
REPLACEMENTS = (
    ("PebbleHertz", "NullWrist"),
    ("Pebblehertz", "NullWrist"),
    ("PEBBLEHERTZ", "NULLWRIST"),
    ("pebblehertz", "nullwrist"),
)


def is_text_file(path: Path) -> bool:
    if any(part in SKIP_DIRS for part in path.parts):
        return False
    return path.suffix.lower() in TEXT_SUFFIXES or path.name in {
        "CMakeLists.txt", "gradlew", "gradlew.bat",
    }


def replace_brand_everywhere() -> None:
    for path in ROOT.rglob("*"):
        if not path.is_file() or not is_text_file(path):
            continue
        try:
            original = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        updated = original
        for old, new in REPLACEMENTS:
            updated = updated.replace(old, new)
        if updated != original:
            path.write_text(updated, encoding="utf-8")


def rename_application_class() -> None:
    package_dir = ROOT / "app/src/main/java/com/manufacttest/pebblereardisplay"
    old_path = package_dir / "PebblehertzApplication.java"
    new_path = package_dir / "NullWristApplication.java"
    if old_path.exists():
        new_path.write_text(
            old_path.read_text(encoding="utf-8").replace(
                "PebblehertzApplication", "NullWristApplication"
            ),
            encoding="utf-8",
        )
        old_path.unlink()


def remove_obsolete_files() -> None:
    for relative in (
        ".github/workflows/publish-pebblehertz-0.8.9.yml",
        ".github/workflows/apply-nullwrist-rebrand.yml",
        "scripts/rebrand_nullwrist.py",
    ):
        path = ROOT / relative
        if path.exists():
            path.unlink()


def normalize_version() -> None:
    path = ROOT / "app/build.gradle.kts"
    text = path.read_text(encoding="utf-8")
    text = text.replace("versionCode = 27", "versionCode = 28")
    text = text.replace('versionName = "0.8.10"', 'versionName = "0.8.11"')
    path.write_text(text, encoding="utf-8")


def validate() -> None:
    forbidden = ("Pebblehertz", "PebbleHertz", "PEBBLEHERTZ", "pebblehertz")
    hits: list[str] = []
    for path in ROOT.rglob("*"):
        if not path.is_file() or not is_text_file(path):
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        relative = path.relative_to(ROOT).as_posix()
        for token in forbidden:
            if token in text or token in relative:
                hits.append(f"{relative}: {token}")
    if hits:
        raise RuntimeError("Old project name remains:\n" + "\n".join(hits))

    build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    if 'applicationId = "com.manufacttest.pebblereardisplay"' not in build:
        raise RuntimeError("Android applicationId changed unexpectedly")
    if 'versionCode = 28' not in build or 'versionName = "0.8.11"' not in build:
        raise RuntimeError("Release version is not 0.8.11 / 28")

    strings = (ROOT / "app/src/main/res/values/strings.xml").read_text(encoding="utf-8")
    if '<string name="app_name">NullWrist</string>' not in strings:
        raise RuntimeError("Android application label is not NullWrist")

    manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
    if 'android:name=".NullWristApplication"' not in manifest:
        raise RuntimeError("Manifest does not reference NullWristApplication")


def main() -> None:
    replace_brand_everywhere()
    rename_application_class()
    normalize_version()
    validate()
    remove_obsolete_files()


if __name__ == "__main__":
    main()
