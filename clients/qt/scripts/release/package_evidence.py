from __future__ import annotations

import json
from pathlib import Path

from canonical_json import canonical_json


def verify_source_identity(packaged: bytes, checkout: dict[str, object]) -> None:
    if checkout["dirty"]:
        raise ValueError(
            "the checkout has changes. Commit all the changes, then build the package again"
        )
    if packaged == canonical_json(checkout):
        return
    try:
        recorded = json.loads(packaged)
    except ValueError:
        recorded = None
    if not isinstance(recorded, dict) or set(recorded) != set(checkout):
        raise ValueError("the source identity of the package is not a source identity record")
    differences = ", ".join(
        f"{key}={recorded[key]!r} (checkout: {value!r})"
        for key, value in checkout.items()
        if recorded[key] != value
    )
    raise ValueError(
        "the source identity of the package differs from the checkout: "
        + (differences or "the bytes are not canonical JSON")
    )


def verify_rust_licenses(packaged: dict[str, bytes], generated: Path) -> None:
    expected = {
        path.relative_to(generated).as_posix(): path
        for path in generated.rglob("*")
        if path.is_file()
    }
    if not expected:
        raise ValueError(f"{generated} contains no Rust license inventory")
    missing = sorted(set(expected) - set(packaged))
    extra = sorted(set(packaged) - set(expected))
    if missing or extra:
        raise ValueError(
            f"the packaged Rust licenses differ from {generated}: "
            f"{len(missing)} missing {missing[:3]}, {len(extra)} extra {extra[:3]}"
        )
    for name, path in sorted(expected.items()):
        if packaged[name] != path.read_bytes():
            raise ValueError(f"the packaged Rust license file differs from {path}")


def translation_files(i18n: Path, qt_translations: Path) -> list[str]:
    languages = sorted(
        path.stem.removeprefix("kodosi_") for path in i18n.glob("kodosi_*.ts") if path.stem != "kodosi_en"
    )
    return [
        *(f"qt_{language}.qm" for language in languages if (qt_translations / f"qtbase_{language}.qm").is_file()),
        *(f"qtwebengine_locales/{language.replace('_', '-')}.pak" for language in languages),
    ]
