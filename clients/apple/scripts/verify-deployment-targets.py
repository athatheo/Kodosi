#!/usr/bin/env python3

from __future__ import annotations

import re
import sys
from pathlib import Path

REPOSITORY = Path(__file__).resolve().parents[3]
MACOS_TARGET = "clients/macos/Config/macOSDeploymentTarget"
IOS_TARGET = "clients/apple/Config/iOSDeploymentTarget"
APPLE_PACKAGE = "clients/apple/Package.swift"
GHOSTTY_PACKAGE = "terminal/ghostty/Package.swift"
NATIVE_BUILD = "terminal/ghostty/NativeBuild.env"
VERSION = r"[0-9]+\.[0-9]+(?:\.[0-9]+)?"


def fail(message: str) -> None:
    raise SystemExit(f"[!] deployment targets: {message}")


def read(relative: str) -> str:
    try:
        return (REPOSITORY / relative).read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError) as error:
        fail(f"cannot read {relative}: {error}")


def record(relative: str) -> str:
    value = read(relative).removesuffix("\n")
    if not re.fullmatch(VERSION, value):
        fail(f"{relative} must contain one version")
    return value


def package_version(relative: str, platform: str) -> str:
    versions = re.findall(rf'\.{platform}\("([^"]*)"\)', read(relative))
    if len(versions) != 1:
        fail(f"{relative} must give one {platform} version as a string")
    return versions[0]


def version_key(value: str) -> tuple[int, ...]:
    parts = [int(part) for part in value.split(".")]
    return tuple(parts + [0] * (3 - len(parts)))


def ghostty_version(key: str, system: str) -> str:
    targets = [line.partition("=")[2] for line in read(NATIVE_BUILD).splitlines() if line.startswith(f"{key}=")]
    match = re.fullmatch(rf"aarch64-{system}\.({VERSION})", targets[0]) if len(targets) == 1 else None
    if match is None:
        fail(f"{NATIVE_BUILD} must give one {key} with a version")
    return match.group(1)


def main() -> None:
    if len(sys.argv) != 1:
        fail("this verifier accepts no arguments")
    macos = record(MACOS_TARGET)
    ios = record(IOS_TARGET)
    ghostty = {}
    for platform, expected, source, key, system in (
        ("macOS", macos, MACOS_TARGET, "GHOSTTY_BUILD_MACOS_TARGET", "macos"),
        ("iOS", ios, IOS_TARGET, "GHOSTTY_BUILD_IOS_TARGET", "ios"),
    ):
        actual = package_version(APPLE_PACKAGE, platform)
        if actual != expected:
            fail(f"{APPLE_PACKAGE} gives {platform} {actual}, but {source} gives {expected}")
        ghostty[platform] = ghostty_version(key, system)
        if version_key(ghostty[platform]) > version_key(expected):
            fail(
                f"{NATIVE_BUILD} builds Ghostty for {platform} {ghostty[platform]}, "
                f"which is higher than {expected} in {source}"
            )
        actual = package_version(GHOSTTY_PACKAGE, platform)
        if actual != ghostty[platform]:
            fail(
                f"{GHOSTTY_PACKAGE} gives {platform} {actual}, "
                f"but {NATIVE_BUILD} builds Ghostty for {platform} {ghostty[platform]}"
            )

    print(
        f"[*] the Swift packages agree with macOS {macos} and iOS {ios}, "
        f"and Ghostty builds for macOS {ghostty['macOS']} and iOS {ghostty['iOS']}"
    )


if __name__ == "__main__":
    main()
