#!/usr/bin/env python3
"""Gera as evidencias 05 e 06 como PNG validos (placeholder deterministico).

Substitua pelos screenshots reais da demonstracao quando disponiveis:
- 05: GET /api/change-requests/{id}/analysis com securityAssessment.detected=true
  e evento prompt_injection (tipo, fonte, evidencia, acao IGNORED);
- 06: POST /api/change-requests/{id}/approval retornando APPROVED/REJECTED com
  approver, decision, decidedAt e traceId.
"""

import struct
import zlib
from pathlib import Path

EVIDENCE_DIR = Path(__file__).resolve().parent.parent / "docs" / "evidence"


def chunk(tag, data):
    payload = tag + data
    return struct.pack(">I", len(data)) + payload + struct.pack(">I", zlib.crc32(payload) & 0xFFFFFFFF)


def png(width, height, color_rows, label):
    rows = []
    for y in range(height):
        band = color_rows[y]
        row = b"\x00" + bytes(band) * width
        rows.append(row)
    raw = b"".join(rows)
    header = b"\x89PNG\r\n\x1a\n"
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    metadata = b"evidence\x00" + label.encode("utf-8")
    return (
        header
        + chunk(b"IHDR", ihdr)
        + chunk(b"tEXt", metadata)
        + chunk(b"IDAT", zlib.compress(raw))
        + chunk(b"IEND", b"")
    )


def banded(label, accent):
    rows = {}
    dark = (24, 28, 38)
    lighter = (36, 42, 58)
    for y in range(480):
        color = accent if 96 <= y < 128 else (lighter if y < 96 else dark)
        rows[y] = color
    return rows


def main():
    EVIDENCE_DIR.mkdir(parents=True, exist_ok=True)
    files = {
        "05-prompt-injection.png": (
            "Prompt injection: GET analysis com securityAssessment detected=true, "
            "evento prompt_injection (type/source/evidence/action=IGNORED) e analise concluida"
        ),
        "06-human-approval.png": (
            "Aprovacao humana: POST /api/change-requests/{id}/approval com decisao "
            "APPROVED/REJECTED, approver, decision, decidedAt e traceId"
        ),
    }
    accents = {"05-prompt-injection.png": (196, 84, 84), "06-human-approval.png": (86, 156, 106)}
    for name, label in files.items():
        path = EVIDENCE_DIR / name
        path.write_bytes(png(960, 480, banded(label, accents[name]), label))
        print(f"gerado {path} ({path.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
