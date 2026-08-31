#!/usr/bin/env python3
"""Redacao de padroes sensiveis em logs de CI antes da publicacao como artefato.

Regras (case-insensitive):
- chaves como token, secret, password, api_key, authorization seguidas de
  ':' ou '=' e de um valor ate espaco/virgula/aspa tem o VALOR substituido
  por ***REDACTED*** (a chave e preservada para diagnostico);
- qualquer linha contendo essas chaves e sempre mantida, apenas o valor e
  removido; nenhuma linha e descartada (evidencia do pipeline preservada).

Uso: python3 scripts/redact_logs.py build.log test.log
"""

import re
import sys

SENSITIVE = (
    r"(token|secret|password|passwd|api[_-]?key|authorization|bearer)"
    r"(\s*[:=]\s*)"
    r"([^\s,;\"']+)"
)

PATTERN = re.compile(SENSITIVE, re.IGNORECASE)

# "Bearer <jwt>" separado por espaco (ex.: "Authorization: Bearer eyJ..." apos
# a redacao do valor "Bearer" na primeira passada).
BEARER_SPACED = re.compile(r"(bearer)(\s+)([A-Za-z0-9._-]{8,})", re.IGNORECASE)


def redact(text: str) -> str:
    # 1) "Bearer <jwt>" primeiro (antes que o valor "Bearer" seja consumido
    # pela redacao generica de "Authorization: <valor>").
    redacted = BEARER_SPACED.sub(
        lambda match: match.group(1) + match.group(2) + "***REDACTED***", text
    )
    return PATTERN.sub(
        lambda match: match.group(1) + match.group(2) + "***REDACTED***", redacted
    )


def main(paths):
    for path in paths:
        try:
            with open(path, "r", encoding="utf-8", errors="replace") as handle:
                content = handle.read()
        except OSError:
            print(f"aviso: {path} nao existe; ignorado")
            continue
        redacted = redact(content)
        if redacted != content:
            print(f"{path}: padroes sensiveis redigidos")
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(redacted)


if __name__ == "__main__":
    if len(sys.argv) < 2:
        raise SystemExit("uso: python3 scripts/redact_logs.py <arquivo.log> [...]")
    main(sys.argv[1:])
