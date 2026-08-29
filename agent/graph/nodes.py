"""Nos deterministicos do grafo de fundacao (sem LLM nesta fase)."""


def parse_stub(state: dict) -> dict:
    text = (state.get("text") or "").strip()
    if not text:
        raise ValueError("texto da solicitacao nao pode ser vazio")
    return {"text": " ".join(text.split()), "warnings": []}


def compile_stub(state: dict) -> dict:
    return {
        "status": "completed",
        "result": {
            "request_id": state["request_id"],
            "processed_text": state["text"],
            "summary": "Analise de fundacao executada (stub deterministico)",
        },
    }
