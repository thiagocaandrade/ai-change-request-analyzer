#!/usr/bin/env python3
"""Servidor local deterministico de embeddings, compativel com a API OpenAI.

Usado SOMENTE para demonstracao/evidencia do pipeline RAG (chunking ->
embeddings -> pgvector -> similaridade) sem chave externa. Os vetores sao
deterministicos (bag-of-hashing): textos com vocabulario em comum ficam
proximos, o que torna a busca por similaridade realista. Em producao,
AI_EMBEDDING_API_KEY aponta para um provedor real e o mesmo pipeline roda
sem nenhuma alteracao.

Uso: python scripts/fake_embeddings_server.py --port 9999
"""

import argparse
import hashlib
import json
import math
from http.server import BaseHTTPRequestHandler, HTTPServer

DIM = 1536
HASHES_PER_TOKEN = 32


def embed(text):
    vector = [0.0] * DIM
    for token in text.lower().split():
        token = token.strip(".,;:()[]{}!?\"'")
        if not token:
            continue
        for i in range(HASHES_PER_TOKEN):
            digest = hashlib.sha256(f"{token}#{i}".encode()).digest()
            idx = int.from_bytes(digest[:4], "big") % DIM
            sign = 1.0 if digest[4] % 2 == 0 else -1.0
            vector[idx] += sign
    norm = math.sqrt(sum(v * v for v in vector)) or 1.0
    return [v / norm for v in vector]


class EmbeddingsHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path not in ("/v1/embeddings", "/embeddings"):
            self.send_error(404, "not found")
            return
        length = int(self.headers.get("Content-Length", 0))
        body = json.loads(self.read_body(length))
        model = body.get("model", "text-embedding-3-small")
        inputs = body.get("input", [])
        if isinstance(inputs, str):
            inputs = [inputs]
        data = [
            {"embedding": embed(item), "index": index}
            for index, item in enumerate(inputs)
        ]
        response = json.dumps(
            {
                "object": "list",
                "data": data,
                "model": model,
                "usage": {"prompt_tokens": sum(len(i.split()) for i in inputs), "total_tokens": 1},
            }
        ).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(response)))
        self.end_headers()
        self.wfile.write(response)

    def read_body(self, length):
        chunks = []
        remaining = length
        while remaining > 0:
            chunk = self.rfile.read(min(remaining, 65536))
            if not chunk:
                break
            chunks.append(chunk)
            remaining -= len(chunk)
        return b"".join(chunks).decode("utf-8")

    def log_message(self, format, *args):
        print(f"[embeddings-stub] {format % args}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=9999)
    args = parser.parse_args()
    server = HTTPServer(("127.0.0.1", args.port), EmbeddingsHandler)
    print(f"fake_embeddings_server ouvindo em http://127.0.0.1:{args.port}/v1/embeddings")
    server.serve_forever()


if __name__ == "__main__":
    main()
