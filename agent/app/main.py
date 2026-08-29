"""API HTTP do servico agente."""

import uuid

import structlog
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from app.logging_config import configure_logging
from graph.builder import build_graph

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


@app.post("/analyze")
def analyze(payload: AnalyzeRequest):
    state = graph.invoke({"request_id": payload.request_id, "text": payload.text})
    return {
        "request_id": state["request_id"],
        "status": state["status"],
        "result": state["result"],
    }
