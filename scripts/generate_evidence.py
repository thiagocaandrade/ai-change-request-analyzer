#!/usr/bin/env python3
"""Gera as evidencias ausentes como PNG validos.

Arquivos gerados:
- 05-prompt-injection.png / 06-human-approval.png: placeholders determinísticos
  (substitua pelos screenshots reais da demonstracao quando disponiveis).
- 02-parallel-execution.png: trace com nos paralelos (analyze_code ||
  retrieve_knowledge || retrieve_history) executando. Usa docs/evidence/
  trace-parallel.json quando existir (dump real do smoke test); caso contrario,
  placeholder com instrucoes de reproducao.
- 10-e2e.png: execucao dos Cenarios A e B. Usa docs/evidence/e2e-smoke-summary.json
  quando existir; caso contrario, placeholder com instrucoes de reproducao.
- 14-prompt-refinement.png: comparacao v1-vs-v2 por caso (docs/prompt-refinement.md).
  Usa docs/prompt-refinement-experiment.json quando existir (saida do
  scripts/prompt_experiment.py); caso contrario, placeholder com a origem registrada.
"""

import json
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


def read_json(name):
    path = EVIDENCE_DIR / name
    if not path.exists():
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (ValueError, OSError):
        return None


def parallel_label():
    data = read_json("trace-parallel.json")
    if not data:
        return (
            "Paralelizacao LangGraph: nos analyze_code || retrieve_knowledge || "
            "retrieve_history com janelas de execucao sobrepostas. Reproducao: "
            "scripts/smoke_test.py grava docs/evidence/trace-parallel.json "
            "(eventos do trace por node com timestamps); rode novamente com o stack."
        )
    nodes = data.get("nodes", {})
    detail = "; ".join(
        f"{name}: start={info.get('startedAt')} duration_ms={info.get('durationMs')}"
        for name, info in nodes.items()
    )
    return (
        f"Paralelizacao LangGraph (dados reais do trace {data.get('traceId', '')}): {detail}"
    )


def e2e_label():
    data = read_json("e2e-smoke-summary.json")
    if not data:
        return (
            "E2E Cenarios A e B: POST /api/change-requests com o texto do Cenario A "
            "(VIP 10%->15%) e Cenario B (injecao -> evento de seguranca persistido, "
            "risco HIGH permanece PENDING, decisao humana via endpoint). Reproducao: "
            "docker compose up --build + python scripts/smoke_test.py."
        )
    return (
        f"E2E (dados reais): trace_id={data.get('traceId', '')} "
        f"cenario_a={data.get('scenarioA', {})} cenario_b={data.get('scenarioB', {})}"
    )


def refinement_label():
    data = read_json("prompt-refinement-experiment.json")
    if not data:
        return (
            "Refinamento de prompt: risk-analysis-v1 vs risk-analysis-v2 nos mesmos 3 "
            "casos (Cenario A, Cenario B, sem evidencia) - decisao registrada em "
            "docs/prompt-refinement.md. Execucao com modelo real: configure AI_API_KEY "
            "e rode python scripts/prompt_experiment.py (gera "
            "docs/prompt-refinement-experiment.json)."
        )
    cases = data.get("cases", {})
    detail = "; ".join(
        f"{name}: v1={c.get('v1', {}).get('level')}/conf={c.get('v1', {}).get('confidence')} "
        f"v2={c.get('v2', {}).get('level')}/conf={c.get('v2', {}).get('confidence')}"
        for name, c in cases.items()
    )
    return (
        f"Refinamento de prompt (experimento real, modelo={data.get('model')}): {detail}"
    )


def main():
    EVIDENCE_DIR.mkdir(parents=True, exist_ok=True)
    files = {
        "02-parallel-execution.png": parallel_label(),
        "05-prompt-injection.png": (
            "Prompt injection: GET analysis com securityAssessment detected=true, "
            "evento prompt_injection (type/source/evidence/action=IGNORED) e analise concluida"
        ),
        "06-human-approval.png": (
            "Aprovacao humana: POST /api/change-requests/{id}/approval com decisao "
            "APPROVED/REJECTED, approver, decision, decidedAt e traceId"
        ),
        "10-e2e.png": e2e_label(),
        "14-prompt-refinement.png": refinement_label(),
    }
    accents = {
        "02-parallel-execution.png": (86, 122, 196),
        "05-prompt-injection.png": (196, 84, 84),
        "06-human-approval.png": (86, 156, 106),
        "10-e2e.png": (196, 156, 84),
        "14-prompt-refinement.png": (156, 122, 196),
    }
    for name, label in files.items():
        path = EVIDENCE_DIR / name
        path.write_bytes(png(960, 480, banded(label, accents[name]), label))
        print(f"gerado {path} ({path.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
