"""Demonstracao da busca RAG real (evidencia docs/evidence/03-rag.png).

Roda contra a aplicacao em execucao com embeddings ativos e imprime os
chunks recuperados com fonte, document id, chunk id e score.
"""

import json
import sys
import urllib.request

APP_URL = "http://localhost:8080"

CHANGE_TEXT = "Alterar o desconto de clientes VIP de 10% para 15%"


def post(path, payload):
    body = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        APP_URL + path,
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.loads(response.read().decode("utf-8"))


def main():
    print(f"consulta: {CHANGE_TEXT}")
    print()
    result = post("/api/agent/retrieve-knowledge", {"changeText": CHANGE_TEXT})
    print(f"degraded: {result.get('degraded')}")
    print(f"documentos retornados: {len(result.get('documents', []))}")
    print()
    for index, doc in enumerate(result.get("documents", [])):
        print(
            f"{index + 1}. source={doc.get('source')} "
            f"document_id={doc.get('documentId')} "
            f"chunk_id={doc.get('chunkId')} "
            f"score={doc.get('score')}"
        )
        content = (doc.get("content") or "").replace("\n", " ")
        print(f"   conteudo: {content[:120]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
