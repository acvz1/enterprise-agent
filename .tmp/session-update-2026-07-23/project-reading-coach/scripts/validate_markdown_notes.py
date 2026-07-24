#!/usr/bin/env python3
"""Validate structural invariants used by project-reading study notes."""

from __future__ import annotations

import argparse
import re
import sys
from collections import Counter
from pathlib import Path


INDEX_PATTERN = re.compile(
    r"^\s*(?:[-*+]\s+)?\[(\d{3})\.\s+.+?\]\(#[^)]+\)\s*$"
)
HEADING_PATTERN = re.compile(r"^###\s+(\d{3})\.\s+.+?\s*$")
FENCE_PATTERN = re.compile(r"^\s*```")
MARKDOWN_IMAGE_PATTERN = re.compile(r"!\[[^\]]*\]\(([^)]+)\)")
HTML_IMAGE_PATTERN = re.compile(r'<img\s+[^>]*src=["\']([^"\']+)["\']', re.IGNORECASE)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("note", type=Path, help="Markdown note to validate")
    return parser.parse_args()


def duplicate_values(values: list[str]) -> list[str]:
    return sorted(value for value, count in Counter(values).items() if count > 1)


def local_image_paths(text: str) -> list[str]:
    paths = MARKDOWN_IMAGE_PATTERN.findall(text)
    paths.extend(HTML_IMAGE_PATTERN.findall(text))
    return [
        value.strip().strip("<>")
        for value in paths
        if not re.match(r"^(?:https?://|data:|#)", value.strip(), re.IGNORECASE)
    ]


def resolve_local_path(note: Path, raw_path: str) -> Path:
    normalized = raw_path.split(maxsplit=1)[0]
    candidate = Path(normalized)
    if candidate.is_absolute():
        return candidate
    return note.parent / candidate


def validate(note: Path) -> tuple[list[str], list[str]]:
    errors: list[str] = []
    warnings: list[str] = []

    if not note.is_file():
        return [f"note does not exist: {note}"], warnings

    text = note.read_text(encoding="utf-8")
    lines = text.splitlines()

    outside_fences: list[tuple[int, str]] = []
    in_fence = False
    for line_number, line in enumerate(lines, start=1):
        if FENCE_PATTERN.match(line):
            in_fence = not in_fence
            continue
        if not in_fence:
            outside_fences.append((line_number, line))

    index_numbers = [
        match.group(1)
        for _, line in outside_fences
        if (match := INDEX_PATTERN.match(line))
    ]
    heading_numbers = [
        match.group(1)
        for _, line in outside_fences
        if (match := HEADING_PATTERN.match(line))
    ]

    duplicate_indexes = duplicate_values(index_numbers)
    duplicate_headings = duplicate_values(heading_numbers)
    if duplicate_indexes:
        errors.append(f"duplicate quick-index numbers: {', '.join(duplicate_indexes)}")
    if duplicate_headings:
        errors.append(f"duplicate numbered headings: {', '.join(duplicate_headings)}")

    if index_numbers or heading_numbers:
        missing_headings = sorted(set(index_numbers) - set(heading_numbers))
        missing_indexes = sorted(set(heading_numbers) - set(index_numbers))
        if missing_headings:
            errors.append(f"index entries without headings: {', '.join(missing_headings)}")
        if missing_indexes:
            errors.append(f"headings without index entries: {', '.join(missing_indexes)}")

    fence_count = sum(bool(FENCE_PATTERN.match(line)) for line in lines)
    if fence_count % 2:
        errors.append(f"unpaired fenced-code marker count: {fence_count}")

    for line_number, line in outside_fences:
        index = line_number - 1
        if line != "---" or index == 0:
            continue
        before_blank = index > 0 and lines[index - 1] == ""
        after_blank = index + 1 < len(lines) and lines[index + 1] == ""
        if not before_blank or not after_blank:
            errors.append(f"horizontal rule at line {index + 1} needs blank lines around it")

    visible_text = "\n".join(line for _, line in outside_fences)
    for raw_path in local_image_paths(visible_text):
        resolved = resolve_local_path(note, raw_path)
        if not resolved.exists():
            warnings.append(f"local image does not exist: {raw_path}")

    if text and not text.endswith("\n"):
        warnings.append("file does not end with a newline")

    return errors, warnings


def main() -> int:
    args = parse_args()
    note = args.note.expanduser().resolve()
    errors, warnings = validate(note)

    for warning in warnings:
        print(f"WARNING: {warning}")
    for error in errors:
        print(f"ERROR: {error}")

    if errors:
        print(f"FAILED: {len(errors)} error(s), {len(warnings)} warning(s)")
        return 1

    print(f"OK: {note} ({len(warnings)} warning(s))")
    return 0


if __name__ == "__main__":
    sys.exit(main())
