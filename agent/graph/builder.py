"""Construcao do grafo LangGraph completo: 13 nos, paralelizacao, branching e condicao de parada."""

from langgraph.graph import END, START, StateGraph

from . import nodes
from .state import ChangeRequestState


def build_graph():
    graph = StateGraph(ChangeRequestState)

    graph.add_node("validate_request", nodes.run_node("validate_request", nodes.validate_request))
    graph.add_node("classify_request", nodes.run_node("classify_request", nodes.classify_request))
    graph.add_node(
        "detect_untrusted_content",
        nodes.run_node("detect_untrusted_content", nodes.detect_untrusted_content),
    )
    graph.add_node("analyze_code", nodes.run_node("analyze_code", nodes.analyze_code))
    graph.add_node(
        "retrieve_knowledge",
        nodes.run_node("retrieve_knowledge", nodes.retrieve_knowledge),
    )
    graph.add_node(
        "retrieve_history",
        nodes.run_node("retrieve_history", nodes.retrieve_history),
    )
    graph.add_node("analyze_impact", nodes.run_node("analyze_impact", nodes.analyze_impact))
    graph.add_node("assess_risk", nodes.run_node("assess_risk", nodes.assess_risk))
    graph.add_node("approval_router", nodes.run_node("approval_router", nodes.approval_router))
    graph.add_node("human_approval", nodes.run_node("human_approval", nodes.human_approval))
    graph.add_node(
        "generate_test_plan",
        nodes.run_node("generate_test_plan", nodes.generate_test_plan),
    )
    graph.add_node(
        "validate_final_result",
        nodes.run_node("validate_final_result", nodes.validate_final_result),
    )
    graph.add_node("finalize", nodes.run_node("finalize", nodes.finalize))
    graph.add_node("finalize_error", nodes.run_node("finalize_error", nodes.finalize_error))

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
