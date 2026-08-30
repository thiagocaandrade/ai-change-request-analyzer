"""API HTTP do servico agente."""

import uuid

import structlog
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from app.logging_config import configure_logging
from graph.builder import build_graph
from graph.state import initial_state

configure_logging()

app = FastAPI(title="AI Change Request Analyzer - Agent")
graph = build_graph()


class AnalyzeRequest(BaseModel):
    request_id: str = Field(min_length=1)
    text: str = Field(min_length=1)


@app.middleware("http")
async def trace_id_middleware(request: Request, call_next):
    trace_id = request.headers.get("X-Trace-Id") or str(uuid.uuid4())
    structlog.contextvars.bind_contextvars(trace_id=trace_id)
    logger = structlog.get_logger()
    logger.info("request_started", method=request.method, path=request.url.path)
    response = await call_next(request)
    response.headers["X-Trace-Id"] = trace_id
    logger.info("request_finished", status_code=response.status_code)
    structlog.contextvars.clear_contextvars()
    return response


@app.exception_handler(RequestValidationError)
async def handle_validation_error(request: Request, exc: RequestValidationError):
    return JSONResponse(
        status_code=400,
        content={
            "error": "invalid_request",
            "detail": "request_id e text sao obrigatorios e nao podem ser vazios",
        },
    )


@app.get("/health")
def health():
    return {"status": "ok"}


def to_response(state):
    change_request = state.get("change_request") or {}
    final_result = state.get("final_result") or {}
    result = {
        "processed_text": change_request.get("text"),
        "summary": final_result.get("summary"),
        "classification": final_result.get("classification"),
        "risk": final_result.get("risk"),
        "confidence": final_result.get("confidence"),
        "rationale": final_result.get("rationale"),
        "findings": final_result.get("findings"),
        "test_plan": final_result.get("test_plan"),
        "security_assessment": final_result.get("security_assessment"),
        "approval": final_result.get("approval"),
    }
    errors = state.get("errors") or []
    if errors:
        result["errors"] = errors
    return {
        "request_id": change_request.get("request_id"),
        "status": state.get("status"),
        "result": result,
        "qa": final_result.get("qa"),
    }


@app.post("/analyze")
def analyze(payload: AnalyzeRequest):
    context = structlog.contextvars.get_contextvars()
    trace_id = context.get("trace_id") or str(uuid.uuid4())
    state = graph.invoke(
        initial_state(request_id=payload.request_id, text=payload.text, trace_id=trace_id)
    )
    return to_response(state)
