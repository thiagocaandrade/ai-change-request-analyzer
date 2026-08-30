"""Construcao do grafo LangGraph completo: 13 nos, paralelizacao, branching e condicao de parada.

O client (AgentClient) e injetado nos nos via fabrica; nos testes um client
mockado substitui o client real. Topologia imutavel entre as changes.
"""

from langgraph.graph import END, START, StateGraph

from tools.client import AgentClient

from . import nodes
from .state import ChangeRequestState


def build_graph(client=None):
    if client is None:
        client = AgentClient()
    node_functions = nodes.make_nodes(client)

    graph = StateGraph(ChangeRequestState)

    graph.add_node(
        "validate_request",
        nodes.run_node("validate_request", node_functions["validate_request"]),
    )
    graph.add_node(
        "classify_request", nodes.run_node("classify_request", node_functions["classify_request"])
    )
    graph.add_node(
        "detect_untrusted_content",
        nodes.run_node("detect_untrusted_content", node_functions["detect_untrusted_content"]),
    )
    graph.add_node("analyze_code", nodes.run_node("analyze_code", node_functions["analyze_code"]))
    graph.add_node(
        "retrieve_knowledge",
        nodes.run_node("retrieve_knowledge", node_functions["retrieve_knowledge"]),
    )
    graph.add_node(
        "retrieve_history",
        nodes.run_node("retrieve_history", node_functions["retrieve_history"]),
    )
    graph.add_node("analyze_impact", nodes.run_node("analyze_impact", node_functions["analyze_impact"]))
    graph.add_node("assess_risk", nodes.run_node("assess_risk", node_functions["assess_risk"]))
    graph.add_node("approval_router", nodes.run_node("approval_router", node_functions["approval_router"]))
    graph.add_node("human_approval", nodes.run_node("human_approval", node_functions["human_approval"]))
    graph.add_node(
        "generate_test_plan",
        nodes.run_node("generate_test_plan", node_functions["generate_test_plan"]),
    )
    graph.add_node(
        "validate_final_result",
        nodes.run_node("validate_final_result", node_functions["validate_final_result"]),
    )
    graph.add_node("finalize", nodes.run_node("finalize", node_functions["finalize"]))
    graph.add_node("finalize_error", nodes.run_node("finalize_error", node_functions["finalize_error"]))

    graph.add_edge(START, "validate_request")
    graph.add_edge("validate_request", "classify_request")
    graph.add_edge("classify_request", "detect_untrusted_content")

    graph.add_edge("detect_untrusted_content", "analyze_code")
    graph.add_edge("detect_untrusted_content", "retrieve_knowledge")
    graph.add_edge("detect_untrusted_content", "retrieve_history")

    graph.add_edge("analyze_code", "analyze_impact")
    graph.add_edge("retrieve_knowledge", "analyze_impact")
    graph.add_edge("retrieve_history", "analyze_impact")

    graph.add_edge("analyze_impact", "assess_risk")
    graph.add_edge("assess_risk", "approval_router")
    graph.add_conditional_edges(
        "approval_router",
        nodes.approval_route,
        {"human_approval": "human_approval", "generate_test_plan": "generate_test_plan"},
    )
    graph.add_edge("human_approval", "generate_test_plan")
    graph.add_edge("generate_test_plan", "validate_final_result")
    graph.add_conditional_edges(
        "validate_final_result",
        nodes.final_result_router,
        {"finalize": "finalize", "retry": "generate_test_plan", "error": "finalize_error"},
    )
    graph.add_edge("finalize", END)
    graph.add_edge("finalize_error", END)

    return graph.compile()
