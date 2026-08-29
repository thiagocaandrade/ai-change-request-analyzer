"""Estado compartilhado e tipado do grafo de analise."""

from typing import TypedDict


class AnalysisState(TypedDict, total=False):
    request_id: str
    text: str
    status: str
    result: dict
    warnings: list[str]
