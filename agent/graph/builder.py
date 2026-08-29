"""Construcao do grafo LangGraph minimo: START -> parse_stub -> compile_stub -> END."""

from langgraph.graph import END, START, StateGraph

from .nodes import compile_stub, parse_stub
from .state import AnalysisState


def build_graph():
    graph = StateGraph(AnalysisState)
    graph.add_node("parse_stub", parse_stub)
    graph.add_node("compile_stub", compile_stub)
    graph.add_edge(START, "parse_stub")
    graph.add_edge("parse_stub", "compile_stub")
    graph.add_edge("compile_stub", END)
    return graph.compile()
