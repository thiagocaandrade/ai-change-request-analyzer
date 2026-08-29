"""Estado compartilhado e tipado do grafo de analise."""

import operator
from typing import Annotated, TypedDict


class ChangeRequestState(TypedDict, total=False):
    trace_id: str
    change_request: dict
    classification: dict
    retrieved_documents: list[dict]
    code_findings: list[dict]
    historical_findings: list[dict]
    impact_findings: list[dict]
    risk_assessment: dict
    security_assessment: dict
    test_plan: list[dict]
    approval_required: bool
    approval_status: str | None
    final_result: dict
    errors: Annotated[list[dict], operator.add]
    iteration_count: int
    status: str


def initial_state(
    request_id: str | None = None,
    text: str | None = None,
    trace_id: str | None = None,
) -> dict:
    """Fabrica o estado inicial com defaults seguros para todos os campos."""
    return {
        "trace_id": trace_id,
        "change_request": {
            "request_id": request_id,
            "text": (text or "").strip(),
        },
        "classification": {},
        "retrieved_documents": [],
        "code_findings": [],
        "historical_findings": [],
        "impact_findings": [],
        "risk_assessment": {},
        "security_assessment": {"detected": False, "events": []},
        "test_plan": [],
        "approval_required": False,
        "approval_status": None,
        "final_result": {},
        "errors": [],
        "iteration_count": 0,
    }
